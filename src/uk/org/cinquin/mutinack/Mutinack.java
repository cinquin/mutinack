/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package uk.org.cinquin.mutinack;

import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.blueF;
import static uk.org.cinquin.mutinack.misc_util.Util.greenB;
import static uk.org.cinquin.mutinack.misc_util.Util.internedVariableBarcodes;
import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;
import static uk.org.cinquin.mutinack.misc_util.Util.reset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.jwetherell.algorithms.data_structures.IntervalTree.IntervalData;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import contrib.net.sf.samtools.SAMFileHeader;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMFileWriterFactory;
import contrib.net.sf.samtools.util.RuntimeIOException;
import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObjectBase;
import contrib.nf.fr.eraasoft.pool.impl.PoolController;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.distributed.EvaluationResult;
import uk.org.cinquin.mutinack.distributed.Job;
import uk.org.cinquin.mutinack.distributed.RemoteMethods;
import uk.org.cinquin.mutinack.distributed.Server;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.features.LocusByLocusNumbersPB;
import uk.org.cinquin.mutinack.features.LocusByLocusNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.MultipleExceptionGatherer;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Signals.SignalProcessor;
import uk.org.cinquin.mutinack.misc_util.StaticStuffToAvoidMutating;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.VersionInfo;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.collections.TSVMapReader;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.statistics.CounterWithBedFeatureBreakdown;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.ICounter;
import uk.org.cinquin.mutinack.statistics.ICounterSeqLoc;
import uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel;
import uk.org.cinquin.parfor.ParFor;

public class Mutinack {
	
	static {
		if (! new File("logback.xml").isFile()) {
			ch.qos.logback.classic.Logger root =
					(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			root.setLevel(ch.qos.logback.classic.Level.INFO);
			ConsoleAppender<ILoggingEvent> stdErrAppender = new ConsoleAppender<>();
			stdErrAppender.setTarget("System.err");
			root.addAppender(stdErrAppender);
		}
	}
	
	public final static Logger logger = LoggerFactory.getLogger("Mutinack");
	final static Logger statusLogger = LoggerFactory.getLogger("MutinackStatus");
	
	private final static List<String> outputHeader = Collections.unmodifiableList(Arrays.asList(
			"Notes", "Location", "Mutation type", "Mutation detail", "Sample", "nQ2Duplexes",
			"nQ1Q2Duplexes", "nDuplexes", "nConcurringReads", "fractionConcurringQ2Duplexes", "fractionConcurringQ1Q2Duplexes", 
			"fractionConcurringDuplexes", "fractionConcurringReads", "averageMappingQuality", "nDuplexesSisterArm", "insertSize", "minDistanceLigSite", "maxDistanceLigSite",
			"positionInRead", "readEffectiveLength", "nameOfOneRead", "readAlignmentStart", "mateAlignmentStart",
			"readAlignmentEnd", "mateAlignmentEnd", "refPositionOfLigSite", "issuesList", "medianPhredAtLocus", "minInsertSize", "maxInsertSize", "supplementalMessage"
	));
		
	private final Collection<Closeable> itemsToClose = new ArrayList<>();
	
	final MutinackGroup groupSettings;
	final int ignoreFirstNBasesQ1, ignoreFirstNBasesQ2;
	final int ignoreLastNBases;
	final int minInsertSize;
	final int maxInsertSize;
	final int minMappingQualityQ1;
	final int minMappingQualityQ2;
	final int minReadsPerStrandQ1;
	final int minReadsPerStrandQ2;
	final int minReadsPerStrandForDisagreement;
	final int minBasePhredScoreQ1;
	final int minBasePhredScoreQ2;
	final int minReadMedianPhredScore;
	final int minMedianPhredQualityAtLocus;
	final float minConsensusThresholdQ1;
	final float minConsensusThresholdQ2;
	final float disagreementConsensusThreshold;
	final boolean acceptNInBarCode;
	final float dropReadProbability;
	final int nConstantBarcodeMismatchesAllowed;
	final int nVariableBarcodeMismatchesAllowed;
	final int alignmentPositionMismatchAllowed;
	final int promoteNQ1Duplexes;
	final int promoteNSingleStrands;
	final float promoteFractionReads;
	final int minNumberDuplexesSisterArm;
	final boolean ignoreSizeOutOfRangeInserts;
	final boolean ignoreTandemRFPairs;
	final boolean requireMatchInAlignmentEnd;
	final boolean logReadIssuesInOutputBam;
	final int maxAverageBasesClipped;
	final int maxAverageClippingOfAllCoveringDuplexes;
	final float maxFractionWrongPairsAtLocus;
	final int maxNDuplexes;
	final boolean computeSupplQuality;
	final boolean rnaSeq;
	final Collection<GenomeFeatureTester> excludeBEDs;
	final boolean computeRawDisagreements;

	final int idx;
	final String name;
	long timeStartProcessing;
	private final @NonNull List<SubAnalyzer> subAnalyzers = new ArrayList<>();
	final @NonNull File inputBam;
	final ObjectPool<SAMFileReader> readerPool = new PoolSettings<> (
			new PoolableObjectBase<SAMFileReader>() {

				@Override
				public SAMFileReader make() {
					Assert.isNonNull(inputBam, "Trying to open null inputBam");
					try {
						SAMFileReader reader = new SAMFileReader(inputBam);
						if (!reader.hasIndex()) {
							throw new IllegalArgumentException("File " + inputBam + " does not have index");
						}
						return reader;
					} catch (RuntimeIOException e) {
						try {
							throw new RuntimeException("Error opening read file with canonical path " +
									inputBam.getCanonicalPath(), e);
						} catch (IOException e1) {
							throw new RuntimeException("Error opening read file " + inputBam.getAbsolutePath(), e);
						}
					}
				}

				@Override
				public void activate(SAMFileReader t) {
					//Nothing to be done to reset
				}
				
				@Override
				public void destroy(SAMFileReader t) {
					t.close();
				}
			}).min(0).max(300).pool(true); 	//Need min(0) so inputBam is set before first
										//reader is created
	final @NonNull Collection<File> intersectAlignmentFiles;
	final @NonNull Map<String, GenomeFeatureTester> filtersForCandidateReporting = new HashMap<>();
	@Nullable GenomeFeatureTester codingStrandTester;
	final byte @NonNull[] constantBarcode;
	final int variableBarcodeLength;
	final int unclippedBarcodeLength;
	public final @NonNull List<@NonNull AnalysisStats> stats = new ArrayList<>();
	private String finalOutputBaseName;

	public Mutinack(
			MutinackGroup groupSettings,
			Parameters argValues,
			String name,
			int idx,
			@NonNull File inputBam,
			int ignoreFirstNBasesQ1, int ignoreFirstNBasesQ2, 
			int ignoreLastNBases,
			int minMappingQualityQ1, int minMappingQualityQ2,
			int minReadsPerStrandQ1, int minReadsPerStrandQ2,
			int minReadsPerStrandForDisagreement,
			int minBasePhredScoreQ1, int minBasePhredScoreQ2,
			int minReadMedianPhredScore,
			float minConsensusThresholdQ1, float minConsensusThresholdQ2,
			float disagreementConsensusThreshold,
			boolean acceptNInBarCode,
			int minInsertSize, int maxInsertSize,
			float dropReadProbability, 
			int nConstantBarcodeMismatchesAllowed, int nVariableBarcodeMismatchesAllowed, 
			int alignmentPositionMismatchAllowed,
			int promoteNPoorDuplexes,
			int promoteNSingleStrands,
			float promoteFractionReads,
			int minNumberDuplexesSisterArm,
			int variableBarcodeLength,
			byte @NonNull[] constantBarcode,
			boolean ignoreSizeOutOfRangeInserts,
			boolean ignoreTandemRFPairs,
			@NonNull List<File> intersectAlignmentFiles,
			boolean requireMatchInAlignmentEnd,
			boolean logReadIssuesInOutputBam,
			int maxAverageBasesClipped,
			int maxAverageClippingOfAllCoveringDuplexes,
			int minMedianPhredQualityAtLocus,
			float maxFractionWrongPairsAtLocus,
			int maxNDuplex,
			@NonNull OutputLevel outputLevel,
			boolean rnaSeq,
			List<GenomeFeatureTester> excludeBEDs,
			boolean computeRawDisagreements) {
		this.groupSettings = groupSettings;
		this.name = name;
		this.inputBam = inputBam;
		this.idx = idx;
		this.ignoreFirstNBasesQ1 = ignoreFirstNBasesQ1;
		this.ignoreFirstNBasesQ2 = ignoreFirstNBasesQ2;
		if (ignoreFirstNBasesQ2 < ignoreFirstNBasesQ1) {
			throw new IllegalArgumentException("Parameter ignoreFirstNBasesQ2 must be greater than ignoreFirstNBasesQ1");
		}
		this.ignoreLastNBases = ignoreLastNBases;
		this.minMappingQualityQ1 = minMappingQualityQ1;
		this.minMappingQualityQ2 = minMappingQualityQ2;
		this.minReadsPerStrandQ1 = minReadsPerStrandQ1;
		this.minReadsPerStrandQ2 = minReadsPerStrandQ2;
		this.minReadsPerStrandForDisagreement = minReadsPerStrandForDisagreement;
		this.minBasePhredScoreQ1 = minBasePhredScoreQ1;
		this.minBasePhredScoreQ2 = minBasePhredScoreQ2;
		this.minReadMedianPhredScore = minReadMedianPhredScore;
		this.minConsensusThresholdQ1 = minConsensusThresholdQ1;
		this.minConsensusThresholdQ2 = minConsensusThresholdQ2;
		this.disagreementConsensusThreshold = disagreementConsensusThreshold;
		this.acceptNInBarCode = acceptNInBarCode;
		this.minInsertSize = minInsertSize;
		this.maxInsertSize = maxInsertSize;
		this.dropReadProbability = dropReadProbability;
		this.nConstantBarcodeMismatchesAllowed = nConstantBarcodeMismatchesAllowed;
		this.nVariableBarcodeMismatchesAllowed = nVariableBarcodeMismatchesAllowed;
		this.alignmentPositionMismatchAllowed = alignmentPositionMismatchAllowed;
		this.promoteNQ1Duplexes = promoteNPoorDuplexes;
		this.promoteNSingleStrands = promoteNSingleStrands;
		this.promoteFractionReads = promoteFractionReads;
		this.minNumberDuplexesSisterArm = minNumberDuplexesSisterArm;
		this.variableBarcodeLength = variableBarcodeLength;
		this.constantBarcode = constantBarcode;
		this.unclippedBarcodeLength = 0;
				
		this.ignoreSizeOutOfRangeInserts = ignoreSizeOutOfRangeInserts;
		this.ignoreTandemRFPairs = ignoreTandemRFPairs;
		this.intersectAlignmentFiles = intersectAlignmentFiles;
		this.requireMatchInAlignmentEnd = requireMatchInAlignmentEnd;
		this.logReadIssuesInOutputBam = logReadIssuesInOutputBam;
		this.maxAverageBasesClipped = maxAverageBasesClipped;
		this.maxAverageClippingOfAllCoveringDuplexes = maxAverageClippingOfAllCoveringDuplexes;
		this.minMedianPhredQualityAtLocus = minMedianPhredQualityAtLocus;
		this.maxFractionWrongPairsAtLocus = maxFractionWrongPairsAtLocus;
		this.maxNDuplexes = maxNDuplex;
		
		this.computeSupplQuality = promoteNQ1Duplexes != Integer.MAX_VALUE ||
				promoteNSingleStrands != Integer.MAX_VALUE ||
				promoteFractionReads != Float.MAX_VALUE;
		
		this.rnaSeq = rnaSeq;
		this.excludeBEDs = excludeBEDs;
		this.computeRawDisagreements = computeRawDisagreements;
		
		stats.add(new AnalysisStats("main_stats", groupSettings));
		stats.add(new AnalysisStats("ins_stats", groupSettings));
		this.stats.forEach(s -> s.setOutputLevel(outputLevel));

	}
	
	public static void main(String args[]) throws InterruptedException, ExecutionException, FileNotFoundException {
		try {
			realMain0(args);
		} catch (ParameterException e) {
			if (System.console() == null) {
				System.err.println(e.getMessage());
			}
			System.out.println(e.getMessage());
			//Make sure to return a non-0 value so "make" sees the run failed
			System.exit(1);
		} catch (Throwable t) {
			if (System.console() == null) {
				t.printStackTrace(System.err);
			}
			t.printStackTrace(System.out);
			//Make sure to return a non-0 value so "make" sees the run failed
			System.exit(1);
		}
	}
		
	private static void realMain0(String args[]) throws InterruptedException, IOException {
		
		final Parameters argValues = new Parameters();
		JCommander commander = new JCommander();
		commander.setAcceptUnknownOptions(false);
		commander.setAllowAbbreviatedOptions(false);
		commander.addObject(argValues);
		commander.parse(args);

		if (argValues.help) {
			//commander.setProgramName("java -jar mutinack.jar");
			System.out.println("Usage: java -jar mutinack.jar ...");
			StringBuilder b = new StringBuilder();
			try {
				Parameters.getUnsortedUsage(commander, Parameters.class, b);
			} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			//commander.usage();
			System.out.println(b);
			StaticStuffToAvoidMutating.shutdown();
			return;
		} else if (argValues.startServer != null) {
			Server.createRegistry(argValues.startServer);
			@SuppressWarnings("unused")
			Server unusedVariable = 
					new Server(0, argValues.startServer, argValues.recordRunsTo);
			return;
		} else if (argValues.submitToServer != null) {
			RemoteMethods server = Server.getServer(argValues.submitToServer);
			Job job = new Job();
			argValues.submitToServer = null;
			if (argValues.workingDirectory != null) {
				synchronized(Runtime.getRuntime()) {
					String saveUserDir = System.getProperty("user.dir");
					System.setProperty("user.dir", argValues.workingDirectory);
					argValues.canonifyFilePaths();
					System.setProperty("user.dir", saveUserDir);
				}
			} else {
				argValues.canonifyFilePaths();
			}
			job.parameters = argValues;
			EvaluationResult result = server.submitJob("client", job);
			if (result.runtimeException != null) {
				throw new RuntimeException(result.runtimeException);
			} else {
				System.out.println("RESULT: \n" + result.output);
			}
			//Do not perform shutdown as a submission to the server is likely
			//handled through NGServer
			return;
		} else if (argValues.startWorker != null) {
			RemoteMethods server = Server.getServer(argValues.startWorker);
			while (true) {
				Job job = server.getMoreWork("worker");
				job.result = new EvaluationResult();
				ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				PrintStream outPS = new PrintStream(outStream);
				ByteArrayOutputStream errStream = new ByteArrayOutputStream();
				PrintStream errPS = new PrintStream(errStream);
				try {
					realMain1(job.parameters, outPS, errPS);
				} catch (Throwable t) {
					job.result.runtimeException = t;
				}
				job.result.output = outStream.toString("UTF8") + "\n---------\n" +
						errStream.toString("UTF8");
				server.submitWork("worker", job);
				Signals.clearSignalProcessors();//Should be unnecessary
			}
			//Worker never leaves this loop; it just gets killed when its services
			//are no longer required
		}
		
		if (argValues.version) {
			System.out.println(VersionInfo.gitCommit);
			StaticStuffToAvoidMutating.shutdown();
			return;
		}
		realMain1(argValues, System.out, System.err);
	}
	
	private static boolean versionChecked = false;
	
	@SuppressWarnings("resource")
	public static void realMain1(Parameters argValues, PrintStream out, PrintStream err) throws InterruptedException, IOException {
		
		StaticStuffToAvoidMutating.instantiateThreadPools(argValues.maxThreadsPerPool);

		if (!versionChecked && !argValues.noStatusMessages && !argValues.skipVersionCheck) {
			versionChecked = true;
			StaticStuffToAvoidMutating.getExecutorService().submit(() -> Util.versionCheck());
		}
		
		if (!versionChecked && !argValues.noStatusMessages) {
			Util.printUserMustSeeMessage(VersionInfo.gitCommit);
			Util.printUserMustSeeMessage("Launch time: " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
		}
		
		if (argValues.saveFilteredReadsTo.size() > 0) {
			throw new RuntimeException("Not implemented");
		}
		
		if (!argValues.noStatusMessages) {
			Util.printUserMustSeeMessage("Host: " + StaticStuffToAvoidMutating.hostName);
		}
		
		out.println(argValues.toString());
		out.println("Non-trivial assertions " +
				(DebugLogControl.NONTRIVIAL_ASSERTIONS ? 
				"on" : "off"));

		final List<Mutinack> analyzers = new ArrayList<>();

		for (int i = 0; i < argValues.inputReads.size(); i++) {
			analyzers.add(null);
		}

		SAMFileWriter alignmentWriter = null;
		SignalProcessor infoSignalHandler = null;
		final MutinackGroup groupSettings = new MutinackGroup();
		groupSettings.registerInterruptSignalProcessor();

		try {
			final SAMFileWriter alignmentWriter0;
			if (argValues.outputAlignmentFile != null) {
				SAMFileWriterFactory factory = new SAMFileWriterFactory();
				SAMFileHeader header = new SAMFileHeader();
				factory.setCreateIndex(true);
				header.setSortOrder(argValues.sortOutputAlignmentFile ? 
						contrib.net.sf.samtools.SAMFileHeader.SortOrder.coordinate
						: contrib.net.sf.samtools.SAMFileHeader.SortOrder.unsorted);
				if (argValues.sortOutputAlignmentFile) {
					factory.setMaxRecordsInRam(10_000);
				}
				final File inputBam = new File(argValues.inputReads.get(0)); 

				try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
					header.setSequenceDictionary(tempReader.getFileHeader().getSequenceDictionary());
				}
				alignmentWriter = factory.
						makeBAMWriter(header, false, new File(argValues.outputAlignmentFile), 0);
			}
			alignmentWriter0 = alignmentWriter;

			final @NonNull List<@NonNull String> contigs;
			final @NonNull Map<@NonNull String, Integer> contigSizes;

			if (argValues.readContigsFromFile) {
				contigSizes = StaticStuffToAvoidMutating.loadContigsFromFile(argValues.referenceGenome);
				contigs = new ArrayList<>();
				contigs.addAll(contigSizes.keySet());
				contigs.sort(null);
			} else if (!argValues.contigNamesToProcess.equals(new Parameters().contigNamesToProcess)) {
				throw new IllegalArgumentException("Contig names specified both in file and as " +
						"command line argument; pick only one method");
			} else {
				contigs = argValues.contigNamesToProcess;
				contigSizes = Collections.emptyMap();
			}

			@NonNull Map<Integer, @NonNull String> contigNames = new HashMap<>();
			for (int i = 0; i < contigs.size(); i++) {
				contigNames.put(i, contigs.get(i));
				groupSettings.indexContigNameReverseMap.put(contigs.get(i), i);
			}
			contigNames = Collections.unmodifiableMap(contigNames);
			groupSettings.setIndexContigNameMap(contigNames);
			
			StaticStuffToAvoidMutating.loadContigs(argValues.referenceGenome,
					contigNames);

			groupSettings.forceOutputAtLocations.clear();
			if (argValues.forceOutputAtPositionsFile != null) {
				try(Stream<String> lines = Files.lines(Paths.get(argValues.forceOutputAtPositionsFile))) {
					lines.forEach(l -> {
						for (String loc: l.split(" ")) {
							try {
								if ("".equals(loc)) {
									continue;
								}
								int columnPosition = loc.indexOf(":");
								final String contig = loc.substring(0, columnPosition);
								final String pos = loc.substring(columnPosition + 1);
								double position = NumberFormat.getNumberInstance(java.util.Locale.US).parse(pos).doubleValue() - 1;
								final SequenceLocation parsedLocation = new SequenceLocation(contigs.indexOf(contig), contig,
										(int) Math.floor(position), position - Math.floor(position) > 0);
								if (!groupSettings.forceOutputAtLocations.add(parsedLocation)) {
									Util.printUserMustSeeMessage(Util.truncateString("Warning: repeated specification of " + parsedLocation +
											" in list of forced output positions"));
								}
							} catch (Exception e) {
								throw new RuntimeException("Error parsing " + loc, e);
							}
						}
					});
				}
				@SuppressWarnings("null")
				@NonNull String forceOutputString = groupSettings.forceOutputAtLocations.toString();
				out.println("Forcing output at locations " + 
						Util.truncateString(forceOutputString));
			}

			out.println(outputHeader.stream().collect(Collectors.joining("\t")));

			//Used to ensure that different analyzers do not use same output files
			final Set<String> names = new HashSet<>();

			if (argValues.minMappingQIntersect.size() != argValues.intersectAlignment.size()) {
				throw new IllegalArgumentException("Lists given in minMappingQIntersect and intersectAlignment must have same length");
			}

			Pair<List<String>, List<Integer>> parsedPositions = 
					Util.parseListLoci(argValues.startAtPositions, true, "startAtPosition");	
			final List<String> startAtContigs = parsedPositions.fst;
			final List<Integer> startAtPositions = parsedPositions.snd;

			Pair<List<String>, List<Integer>> parsedPositions2 =
					Util.parseListLoci(argValues.stopAtPositions, true, "stopAtPosition");	
			final List<String> truncateAtContigs = parsedPositions2.fst;
			final List<Integer> truncateAtPositions = parsedPositions2.snd;

			Util.checkPositionsOrdering(parsedPositions, parsedPositions2);

			final List<GenomeFeatureTester> excludeBEDs = new ArrayList<>();
			for (String bed: argValues.excludeRegionsInBED) {
				try {
					final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
							groupSettings.getIndexContigNameMap(), bed);
					excludeBEDs.add(reader);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + bed, e);
				}
			}

			final List<BedReader> repetitiveBEDs = new ArrayList<>();
			for (String bed: argValues.repetiveRegionBED) {
				try {
					final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
							groupSettings.getIndexContigNameMap(), bed);
					repetitiveBEDs.add(reader);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + bed, e);
				}
			}

			IntStream.range(0, argValues.inputReads.size()).parallel().forEach(i -> {
				final String inputReads = argValues.inputReads.get(i);
				final File inputBam = new File(inputReads); 

				final String name;

				if (argValues.sampleNames.size() >= i + 1) {
					name = argValues.sampleNames.get(i);
				} else {
					name = inputBam.getName();
				}

				synchronized (names) {
					if (!names.add(name)) {
						throw new RuntimeException("Two or more analyzers trying to use the same name " +
								name + "; please give samples unique names");
					}
				}

				final List<File> intersectFiles = new ArrayList<>();
				final int nIntersectFiles = argValues.intersectAlignment.size();
				final int nIntersectFilesPerAnalyzer = nIntersectFiles / argValues.inputReads.size();
				for (int k = i * nIntersectFilesPerAnalyzer ; k < (i+1) * nIntersectFilesPerAnalyzer ; k++) {
					File f = new File(argValues.intersectAlignment.get(k));
					intersectFiles.add(f);
					out.println("Intersecting " + inputBam.getAbsolutePath() + " with " +
							f.getAbsolutePath());
				}

				final int nMaxDupArg = argValues.maxNDuplexes.size();
				if (nMaxDupArg > 0 && nMaxDupArg < argValues.inputReads.size()) {
					throw new IllegalArgumentException("maxNDuplexes must be specified once for each input file or not at all");
				}

				final int maxNDuplex = argValues.maxNDuplexes.size() == 0 ? Integer.MAX_VALUE :
					argValues.maxNDuplexes.get(i);

				final OutputLevel[] d = OutputLevel.values();
				if (argValues.verbosity < 0 || argValues.verbosity >= d.length) {
					throw new IllegalArgumentException("Invalid verbosity " + argValues.verbosity + "; must be >= 0 and < " + d.length);
				}

				@SuppressWarnings("null")
				final @NonNull OutputLevel outputLevel = d[argValues.verbosity];

				groupSettings.PROCESSING_CHUNK = argValues.processingChunk;
				groupSettings.INTERVAL_SLOP = argValues.alignmentPositionMismatchAllowed;
				groupSettings.BIN_SIZE = argValues.contigStatsBinLength;
				
				groupSettings.setBarcodePositions(0, argValues.variableBarcodeLength - 1,
						3, 5, 0);

				//TODO Create a constructor that just takes argValues as an input,
				//and/or use the builder pattern
				final Mutinack analyzer = new Mutinack(
						groupSettings,
						argValues,
						name,
						i,
						inputBam,
						argValues.ignoreFirstNBasesQ1,
						argValues.ignoreFirstNBasesQ2,
						argValues.ignoreLastNBases,
						argValues.minMappingQualityQ1,
						argValues.minMappingQualityQ2,
						argValues.minReadsPerStrandQ1,
						argValues.minReadsPerStrandQ2,
						argValues.minReadsPerStrandForDisagreement,
						argValues.minBasePhredScoreQ1, 
						argValues.minBasePhredScoreQ2, 
						argValues.minReadMedianPhredScore,
						argValues.minConsensusThresholdQ1, 
						argValues.minConsensusThresholdQ2,
						argValues.disagreementConsensusThreshold,
						argValues.acceptNInBarCode,
						argValues.minInsertSize,
						argValues.maxInsertSize, 
						argValues.dropReadProbability,
						argValues.nConstantBarcodeMismatchesAllowed,
						argValues.nVariableBarcodeMismatchesAllowed,
						argValues.alignmentPositionMismatchAllowed,
						argValues.promoteNQ1Duplexes,
						argValues.promoteNSingleStrands,
						argValues.promoteFractionReads,
						argValues.minNumberDuplexesSisterArm,
						argValues.variableBarcodeLength,
						nonNullify(argValues.constantBarcode.getBytes()),
						argValues.ignoreSizeOutOfRangeInserts,
						argValues.ignoreTandemRFPairs,
						intersectFiles,
						argValues.requireMatchInAlignmentEnd,
						argValues.logReadIssuesInOutputBam,
						argValues.maxAverageBasesClipped,
						argValues.maxAverageClippingOfAllCoveringDuplexes,
						argValues.minMedianPhredQualityAtLocus,
						argValues.maxFractionWrongPairsAtLocus,
						maxNDuplex,
						outputLevel,
						argValues.rnaSeq,
						excludeBEDs,
						argValues.computeRawDisagreements);
				analyzers.set(i,analyzer);

				analyzer.finalOutputBaseName = (argValues.auxOutputFileBaseName != null ?
						(argValues.auxOutputFileBaseName) : "") +
						name;

				if (argValues.outputTopBottomDisagreementBED) {
					String tbdName = analyzer.finalOutputBaseName + "_top_bottom_disag_";
					analyzer.stats.forEach(s -> {
						String path = tbdName + s.getName() + ".bed";
						try {
							s.topBottomDisagreementWriter = new FileWriter(path);
							analyzer.itemsToClose.add(s.topBottomDisagreementWriter);
						} catch (IOException e) {
							handleOutputException(path, e, argValues);
						}
					});
				}

				String mutName = analyzer.finalOutputBaseName + "_mutations_";
				analyzer.stats.forEach(s -> {
					String path = mutName + s.getName() + ".bed";
					try {
						s.mutationBEDWriter = new FileWriter(path);
						analyzer.itemsToClose.add(s.mutationBEDWriter);				
					} catch (IOException e) {
						handleOutputException(path, e, argValues);
					}
				});

				if (argValues.outputCoverageProto) {
					analyzer.stats.forEach(s -> {
						s.locusByLocusCoverage = new HashMap<>();
						if (contigSizes.isEmpty()) {
							throw new IllegalArgumentException("Need contig sizes for outputCoverageProto; " +
									"set readContigsFromFile option");
						}
						contigSizes.forEach((k,v) -> {
							s.locusByLocusCoverage.put(k, new int [v]);
						});
						Builder builder = LocusByLocusNumbersPB.GenomeNumbers.newBuilder();
						builder.setGeneratingProgramVersion(VersionInfo.gitCommit);
						builder.setGeneratingProgramArgs(argValues.toString());
						builder.setSampleName(s.getName() + name + "_locus_by_locus_coverage");
						s.locusByLocusCoverageProtobuilder = builder;
					});
				}

				if (argValues.outputCoverageBed) {
					String coverageName = analyzer.finalOutputBaseName + "_coverage_";
					analyzer.stats.forEach(s -> {
						String path = coverageName + s.getName() + ".bed";
						try {	
							s.coverageBEDWriter = new FileWriter(path);
							analyzer.itemsToClose.add(s.coverageBEDWriter);
						} catch (IOException e) {
							handleOutputException(path, e, argValues);
						}
					});
				}


				for (int contigIndex = 0; contigIndex < argValues.contigNamesToProcess.size(); contigIndex++) {
					final int finalContigIndex = contigIndex;
					final SerializablePredicate<SequenceLocation> p = 
							l -> l.contigIndex == finalContigIndex;
							String contigName = argValues.contigNamesToProcess.get(contigIndex);
							analyzer.stats.forEach(s -> {
								s.topBottomSubstDisagreementsQ2.addPredicate(contigName, p);
								s.topBottomDelDisagreementsQ2.addPredicate(contigName, p);
								s.topBottomInsDisagreementsQ2.addPredicate(contigName, p);
								s.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(contigName, p);
								s.codingStrandSubstQ2.addPredicate(contigName, p);
								s.templateStrandSubstQ2.addPredicate(contigName, p);
								s.codingStrandDelQ2.addPredicate(contigName, p);
								s.templateStrandDelQ2.addPredicate(contigName, p);
								s.codingStrandInsQ2.addPredicate(contigName, p);
								s.templateStrandInsQ2.addPredicate(contigName, p);
							});
				}

				if (argValues.bedDisagreementOrienter != null) {
					try {
						analyzer.codingStrandTester = BedReader.getCachedBedFileReader(argValues.bedDisagreementOrienter, ".cached",
								groupSettings.getIndexContigNameMap(), "");
					} catch (Exception e) {
						throw new RuntimeException("Problem with BED file " + 
								argValues.bedDisagreementOrienter, e);
					}
				}

				Set<String> bedFileNames = new HashSet<>();

				for (String fileName: argValues.reportStatsForBED) {
					try {
						if (!bedFileNames.add(fileName)) {
							throw new IllegalArgumentException("Bed file " + fileName + " specified multiple times");
						}
						final File f = new File(fileName);
						final String filterName = f.getName();
						final GenomeFeatureTester filter = BedReader.getCachedBedFileReader(fileName, ".cached",
								groupSettings.getIndexContigNameMap(), filterName);
						final BedComplement notFilter = new BedComplement(filter);
						final String notFilterName = "NOT " + f.getName();
						analyzer.filtersForCandidateReporting.put(filterName, filter);

						analyzer.stats.forEach(s -> {
							s.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
							s.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
							s.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(filterName, filter);
							s.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
							s.nLociCandidatesForUniqueMutation.addPredicate(filterName, filter);
							s.topBottomSubstDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomDelDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomInsDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomDisagreementsQ2TooHighCoverage.addPredicate(filterName, filter);
							s.codingStrandSubstQ2.addPredicate(filterName, filter);
							s.templateStrandSubstQ2.addPredicate(filterName, filter);
							s.codingStrandDelQ2.addPredicate(filterName, filter);
							s.templateStrandDelQ2.addPredicate(filterName, filter);
							s.codingStrandInsQ2.addPredicate(filterName, filter);
							s.templateStrandInsQ2.addPredicate(filterName, filter);
							s.lackOfConsensus1.addPredicate(filterName, filter);
							s.lackOfConsensus2.addPredicate(filterName, filter);

							s.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(notFilterName, notFilter);
							s.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(notFilterName, notFilter);
							s.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(notFilterName, notFilter);
							s.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(notFilterName, notFilter);
							s.nLociCandidatesForUniqueMutation.addPredicate(notFilterName, notFilter);
							s.topBottomSubstDisagreementsQ2.addPredicate(notFilterName, notFilter);
							s.topBottomDelDisagreementsQ2.addPredicate(notFilterName, notFilter);
							s.topBottomInsDisagreementsQ2.addPredicate(notFilterName, notFilter);
							s.topBottomDisagreementsQ2TooHighCoverage.addPredicate(notFilterName, notFilter);
							s.codingStrandSubstQ2.addPredicate(notFilterName, notFilter);
							s.templateStrandSubstQ2.addPredicate(notFilterName, notFilter);
							s.codingStrandDelQ2.addPredicate(notFilterName, notFilter);
							s.templateStrandDelQ2.addPredicate(notFilterName, notFilter);
							s.codingStrandInsQ2.addPredicate(notFilterName, notFilter);
							s.templateStrandInsQ2.addPredicate(notFilterName, notFilter);
							s.lackOfConsensus1.addPredicate(notFilterName, notFilter);
							s.lackOfConsensus2.addPredicate(notFilterName, notFilter);
							analyzer.filtersForCandidateReporting.put(notFilterName, notFilter);
						});
					} catch (Exception e) {
						throw new RuntimeException("Problem with BED file " + fileName, e);
					}
				}

				for (String fileName: argValues.reportStatsForNotBED) {
					try {
						final File f = new File(fileName);
						final String filterName = "NOT " + f.getName();
						final GenomeFeatureTester filter0 = BedReader.getCachedBedFileReader(fileName, ".cached",
								groupSettings.getIndexContigNameMap(), filterName);
						final BedComplement filter = new BedComplement(filter0);
						analyzer.stats.forEach(s -> {
							s.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
							s.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
							s.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(filterName, filter);
							s.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
							s.nLociCandidatesForUniqueMutation.addPredicate(filterName, filter);
							s.topBottomSubstDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomDelDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomInsDisagreementsQ2.addPredicate(filterName, filter);
							s.topBottomDisagreementsQ2TooHighCoverage.addPredicate(filterName, filter);
							s.lackOfConsensus1.addPredicate(filterName, filter);
							s.lackOfConsensus2.addPredicate(filterName, filter);
						});

						analyzer.filtersForCandidateReporting.put(filterName, filter);
					} catch (Exception e) {
						throw new RuntimeException("Problem with BED file " + fileName, e);
					}
				}

				if (argValues.reportBreakdownForBED.size() != argValues.saveBEDBreakdownTo.size()) {
					throw new IllegalArgumentException("Arguments -reportBreakdownForBED and " +
							"-saveBEDBreakdownTo must appear same number of times");
				}

				Set<String> outputPaths = new HashSet<>();
				int index = 0;
				for (index = 0; index < argValues.reportBreakdownForBED.size(); index++) {
					try {
						final File f = new File(argValues.reportBreakdownForBED.get(index));
						final BedReader filter = new BedReader(groupSettings.getIndexContigNameMap(), 
								new BufferedReader(new FileReader(f)), f.getName(),
								argValues.bedFeatureSuppInfoFile == null ? null :
									new BufferedReader(new FileReader(argValues.bedFeatureSuppInfoFile)));
						int index0 = index;
						analyzer.stats.forEach(s -> {
							CounterWithBedFeatureBreakdown counter;
							try {
								counter = new CounterWithBedFeatureBreakdown(filter,
										argValues.refSeqToOfficialGeneName == null ?
												null :
													TSVMapReader.getMap(new BufferedReader(
															new FileReader(argValues.refSeqToOfficialGeneName))),
													groupSettings);
							} catch (Exception e) {
								throw new RuntimeException("Problem setting up BED file " + f.getName(), e);
							}

							String outputPath = argValues.saveBEDBreakdownTo.get(index0) + s.getName();
							if (!outputPaths.add(outputPath)) {
								throw new IllegalArgumentException("saveBEDBreakdownTo " + outputPath +
										" specified multiple times");
							}
							counter.setOutputFile(new File(outputPath + "_nLociDuplex.bed"));
							s.nLociDuplex.addPredicate(f.getName(), filter, counter);
							for (List<IntervalData<GenomeInterval>> locs: filter.bedFileIntervals.values()) {
								for (IntervalData<GenomeInterval> loc: locs) {
									Iterator<GenomeInterval> it = loc.getData().iterator();
									while (it.hasNext()) {
										GenomeInterval interval = it.next();
										counter.accept(interval.getStartLocation(), 0);
										if (NONTRIVIAL_ASSERTIONS && !counter.getCounter().getCounts().containsKey(interval)) {
											throw new AssertionFailedException("Failed to add " + interval +"; matches were " +
													counter.getBedFeatures().apply(interval.getStartLocation()));
										}
									}
								}
							}
							counter.setNormalizedOutput(true);
							counter.setAnalyzerName(name);

							counter = new CounterWithBedFeatureBreakdown(filter, null, groupSettings);
							counter.setOutputFile(new File(argValues.saveBEDBreakdownTo.get(index0) + s.getName() +
									"_nLociDuplexQualityQ2OthersQ1Q2_" + name + ".bed"));
							s.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(f.getName(), filter, counter);
						});
					} catch (Exception e) {
						throw new RuntimeException("Problem setting up BED file " + argValues.reportBreakdownForBED.get(index), e);
					}
				}
			});//End parallel loop over analyzers

			if (argValues.lenientSamValidation) {
				SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.LENIENT);
			}

			final List<Phaser> phasers = new ArrayList<>();

			final Histogram dubiousOrGoodDuplexCovInAllInputs = new Histogram(500);
			final Histogram goodDuplexCovInAllInputs = new Histogram(500);

			infoSignalHandler = signal -> {
				PrintStream printStream = (signal == null) ? out : err;
				for (Mutinack analyzer: analyzers) {
					analyzer.printStatus(printStream, signal != null);
				}

				if (signal != null) {
					synchronized(groupSettings.statusUpdateTasks) {
						for (BiConsumer<PrintStream, Integer> update: groupSettings.statusUpdateTasks) {
							update.accept(System.err, 0);
						}
					}
				} else {
					//TODO Print short status?
				}

				printStream.println(blueF(signal != null) +
						"Minimal Q1 or Q2 duplex coverage across all inputs: " +
						reset(signal != null) + dubiousOrGoodDuplexCovInAllInputs);
				printStream.println(blueF(signal != null) +
						"Minimal Q2 duplex coverage across all inputs: " +
						reset(signal != null) + goodDuplexCovInAllInputs);
			};
			Signals.registerSignalProcessor("INFO", infoSignalHandler);

			statusLogger.info("Starting sequence analysis");

			final List<AnalysisChunk> analysisChunks = new ArrayList<>();

			for (int contigIndex0 = 0; contigIndex0 < contigs.size(); contigIndex0++) {
				final int contigIndex = contigIndex0;

				final int startContigAtPosition;
				final int terminateContigAtPosition;
				{
					final String contigName = contigNames.get(contigIndex);
					int idx = truncateAtContigs.indexOf(contigName);
					if (idx > -1) {
						terminateContigAtPosition = truncateAtPositions.get(idx);
					} else {
						idx = Parameters.defaultTruncateContigNames.indexOf(contigName);
						if (idx == -1) {
							//TODO Make this less ad-hoc
							terminateContigAtPosition = 
									Objects.requireNonNull(contigSizes.get(contigName)) - 1;
						} else {
							terminateContigAtPosition = Parameters.defaultTruncateContigPositions.get(idx) - 1;
						}
					}
					idx = startAtContigs.indexOf(contigNames.get(contigIndex));
					if (idx > -1) {
						startContigAtPosition = startAtPositions.get(idx);
					} else {
						idx = Parameters.defaultTruncateContigNames.indexOf(contigName);
						if (idx == -1) {
							//TODO Make this less ad-hoc
							startContigAtPosition = 0;
						} else {
							startContigAtPosition = Parameters.defaultStartContigPositions.get(idx);
						}
					}
				}

				final int subAnalyzerSpan = (terminateContigAtPosition - startContigAtPosition + 1) / 
						argValues.parallelizationFactor;
				for (int p = 0 ; p < argValues.parallelizationFactor; p++) {
					final AnalysisChunk analysisChunk = new AnalysisChunk();
					analysisChunk.out = out;
					analysisChunks.add(analysisChunk);

					final int startSubAt = startContigAtPosition + p * subAnalyzerSpan;
					final int terminateAtPosition = (p == argValues.parallelizationFactor - 1) ?
							terminateContigAtPosition 
							: startSubAt + subAnalyzerSpan - 1;

					analysisChunk.contig = contigIndex;
					analysisChunk.contigName = contigs.get(contigIndex);
					analysisChunk.startAtPosition = startSubAt;
					analysisChunk.terminateAtPosition = terminateAtPosition;
					analysisChunk.pauseAtPosition = new SettableInteger();
					analysisChunk.lastProcessedPosition = new SettableInteger();
					analysisChunk.groupSettings = groupSettings;

					final List<LocationExaminationResults> analyzerCandidateLists = new ArrayList<>();
					analyzers.forEach(a -> {
						analyzerCandidateLists.add(null);
						final SubAnalyzer subAnalyzer = new SubAnalyzer(Objects.requireNonNull(a));
						a.subAnalyzers.add(subAnalyzer);
						analysisChunk.subAnalyzers.add(subAnalyzer);
					});

					final SubAnalyzerPhaser phaser = new SubAnalyzerPhaser(argValues,
							analysisChunk, analyzers, analyzerCandidateLists,
							alignmentWriter, groupSettings.forceOutputAtLocations,
							dubiousOrGoodDuplexCovInAllInputs,
							goodDuplexCovInAllInputs,
							contigNames.get(contigIndex), contigIndex,
							excludeBEDs, repetitiveBEDs,
							groupSettings.PROCESSING_CHUNK);
					analysisChunk.phaser = phaser;
					phaser.bulkRegister(analyzers.size());
					phasers.add(phaser);
				}//End parallelization loop over analysisChunks
			}//End loop over contig index

			final int endIndex = contigs.size() - 1;
			ParFor parFor = new ParFor("contig loop", 0, endIndex, null, true);

			for (int worker = 0; worker < parFor.getNThreads(); worker++) 
				parFor.addLoopWorker((final int contigIndex, final int threadIndex) -> {

					final List<Future<?>> futures = new ArrayList<>();
					int analyzerIndex = -1;

					for (Mutinack analyzer: analyzers) {
						analyzerIndex++;

						for (int p = 0; p < argValues.parallelizationFactor; p++) {
							final AnalysisChunk analysisChunk = analysisChunks.get(contigIndex * argValues.parallelizationFactor + p);				
							final SubAnalyzer subAnalyzer = analysisChunk.subAnalyzers.get(analyzerIndex);

							Runnable r = () -> {
								ReadLoader.load(analyzer, subAnalyzer, analysisChunk,
										argValues, analysisChunks,
										groupSettings.PROCESSING_CHUNK, contigs, contigIndex,
										alignmentWriter0, StaticStuffToAvoidMutating::getContigSequence);
							};
							futures.add(StaticStuffToAvoidMutating.getExecutorService().submit(r));
						}//End loop over parallelization factor
					}//End loop over analyzers

					MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
					
					//Catch all the exceptions because the exceptions throw by
					//some threads may be secondary to the primary exception
					//but still be examined before the primary exception
					for (Future<?> f: futures) {
						gatherer.tryAdd(() -> {
							try {
								f.get();
							} catch (ExecutionException | InterruptedException e) {
								throw new RuntimeException(e);
							}
						});
					}

					gatherer.throwIfPresent();

					for (int p = 0 ; p < argValues.parallelizationFactor; p++) {
						for (Mutinack analyzer: analyzers) {
							int subAnalyzerIndex = contigIndex * argValues.parallelizationFactor + p;
							Assert.noException(() -> {
								analyzer.subAnalyzers.get(subAnalyzerIndex).checkAllDone();
							});
							analyzer.subAnalyzers.set(subAnalyzerIndex, null);
						}
					}

					return null;
				});//End Parfor loop over contigs

			parFor.run(true);

			infoSignalHandler.handle(null);
			if (!argValues.noStatusMessages) {
				Util.printUserMustSeeMessage("Analysis of samples " + analyzers.stream().map(a -> a.name
						+ " at " + ((int) a.processingThroughput()) + " records / s").
						collect(Collectors.joining(", ")) + " completed on host " + 
						StaticStuffToAvoidMutating.hostName +
						" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
			}

			if (argValues.readContigsFromFile) {//Probably reference transcriptome; TODO need to
				//devise a better way of figuring this out
				for (Mutinack analyzer: analyzers) {
					final ICounterSeqLoc counter = 
							Objects.requireNonNull(
									analyzer.stats.get(0).nLociDuplex.getSeqLocCounters().get("All")).
							snd;
					final @NonNull Map<Object, @NonNull Object> m = counter.getCounts();
					final Map<String, Double> allCoverage = new THashMap<>();

					for (Entry<String, Integer> e: contigSizes.entrySet()) {
						//TODO Need to design a better API to retrieve the counts
						ICounter<?> c = ((ICounter<?>) m.get(
								groupSettings.indexContigNameReverseMap.get(e.getKey())));
						final double d;
						if (c == null) {
							d = 0;	
						} else {
							Object o = c.getCounts().get(0);
							if (o == null) {
								d = 0;
							} else {
								d = ((DoubleAdder) o).sum();
							}
						}					
						allCoverage.put(e.getKey(), d / e.getValue());
					}

					final double averageCoverage = allCoverage.values().stream().mapToDouble(o -> o).average().orElse(Double.NaN);
					final List<Double> allValues = allCoverage.values().stream().sorted().collect(Collectors.toList());
					final int nElements = allValues.size();
					final double percentileCoverage80 = nElements == 0 ? Double.NaN : allValues.get((int) (nElements * 0.8));
					final double percentileCoverage95 = nElements == 0 ? Double.NaN : allValues.get((int) (nElements * 0.95));

					try (Writer writer = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream(analyzer.finalOutputBaseName + "_coverage.txt"), "utf-8"))) {
						List<Entry<String, Double>> sortedEntries = 
								allCoverage.entrySet().stream().map(entry ->
								new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue())).
								collect(Collectors.toList());
						sortedEntries.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));

						writer.write("RefseqID\t" + (analyzer.name + "_avn\t") + (analyzer.name + "_95thpn\t")
								+ (analyzer.name + "_80thpn\n"));

						for (Entry<String, Double> e: sortedEntries) {
							writer.write(e.getKey() + "\t" + (e.getValue() / averageCoverage) + "\t" +
									(e.getValue() / percentileCoverage95) + "\t" + (e.getValue() / percentileCoverage80) + "\n");
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}  
				}
			}
		} finally {
			close(alignmentWriter, analyzers, infoSignalHandler, groupSettings);
		}
	}
		
	private static void close(SAMFileWriter alignmentWriter, List<Mutinack> analyzers,
			SignalProcessor infoSignalHandler, MutinackGroup groupSettings) {
		MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
		
		if (alignmentWriter != null) {
			gatherer.tryAdd(() -> alignmentWriter.close());
		}

		for (Mutinack analyzer: analyzers) {
			if (analyzer != null) {
				gatherer.tryAdd(() -> {
					try {
						analyzer.closeOutputs();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			}
		}
		
		gatherer.tryAdd(() -> PoolController.shutdown());
		
		if (infoSignalHandler != null) {
			gatherer.tryAdd(() -> 
				Signals.removeSignalProcessor("INFO", infoSignalHandler));
		}
		
		gatherer.tryAdd(() -> groupSettings.close());
		
		gatherer.throwIfPresent();
	}

	private void closeOutputs() throws IOException {
		MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
		for (Closeable c: itemsToClose) {
			gatherer.tryAdd(() ->
			{
				try {
					c.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}

		gatherer.tryAdd(() -> {
			stats.forEach(s -> {
				if (s.locusByLocusCoverage != null) {
					Builder builder = s.locusByLocusCoverageProtobuilder;
					for (Entry<String, int[]> e: s.locusByLocusCoverage.entrySet()) {
						LocusByLocusNumbersPB.ContigNumbers.Builder builder2 =
								LocusByLocusNumbersPB.ContigNumbers.newBuilder();
						builder2.setContigName(e.getKey());
						int [] numbers = e.getValue();
						builder2.ensureNumbersIsMutable(numbers.length);
						builder2.numUsedInNumbers_ = numbers.length;
						builder2.numbers_ = numbers;
						builder.addContigNumbers(builder2);
					}
					Path path = Paths.get(builder.getSampleName() + ".proto");
					try {
						Files.write(path, builder.build().toByteArray());
					} catch (Exception e1) {
						throw new RuntimeException(e1);
					}
				}
			});
		});
		
		gatherer.throwIfPresent();
	}

	private static void handleOutputException(String fileName, Throwable e, Parameters argValues) {
		String baseMessage = "Could not open file " + fileName;
		if (argValues.terminateUponOutputFileError) {
			throw new RuntimeException(baseMessage, e);
		}
		Util.printUserMustSeeMessage(baseMessage + "; keeping going anyway");
	}

	private double processingThroughput() {
		return (stats.get(0).nRecordsProcessed.sum()) / 
				((System.nanoTime() - timeStartProcessing) / 1_000_000_000d);
	}

	private void printStatus(PrintStream stream, boolean colorize) {
		stats.forEach(s -> {
			stream.println();
			stream.println(greenB(colorize) + "Statistics " + s.getName() + " for " + inputBam.getAbsolutePath() + reset(colorize));

			s.print(stream, colorize);

			stream.println(blueF(colorize) + "Average Phred quality: " + reset(colorize) +
					DoubleAdderFormatter.formatDouble(s.phredSumProcessedbases.sum() / s.nProcessedBases.sum()));

			if (s.outputLevel.compareTo(OutputLevel.VERBOSE) >= 0) {
				stream.println(blueF(colorize) + "Top 100 barcode hits in cache: " + reset(colorize) +
						internedVariableBarcodes.values().stream().sorted((ba, bb) -> - Long.compare(ba.nHits.sum(), bb.nHits.sum())).
						limit(100).map(ByteArray::toString).collect(Collectors.toList()));
			}

			stream.println(blueF(colorize) + "Processing throughput: " + reset(colorize) + 
					((int) processingThroughput()) + " records / s");

			for (String counter: s.nLociCandidatesForUniqueMutation.getCounterNames()) {
				stream.println(greenB(colorize) + "Mutation rate for " + counter + ": " + reset(colorize) + DoubleAdderFormatter.
						formatDouble(s.nLociCandidatesForUniqueMutation.getSum(counter) / s.nLociDuplexQualityQ2OthersQ1Q2.getSum(counter)));
			}
		});
	}
	
	@Override
	public String toString() {
		return name + " (" + inputBam.getAbsolutePath() + ")";
	}

}
