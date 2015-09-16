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

import static contrib.uk.org.lidalia.slf4jext.Level.TRACE;
import static uk.org.cinquin.mutinack.MutationType.DELETION;
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.MutationType.WILDTYPE;
import static uk.org.cinquin.mutinack.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.Quality.GOOD;
import static uk.org.cinquin.mutinack.Quality.POOR;
import static uk.org.cinquin.mutinack.misc_util.DebugControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.DebugControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.blueF;
import static uk.org.cinquin.mutinack.misc_util.Util.getRecordNameWithPairSuffix;
import static uk.org.cinquin.mutinack.misc_util.Util.greenB;
import static uk.org.cinquin.mutinack.misc_util.Util.internedVariableBarcodes;
import static uk.org.cinquin.mutinack.misc_util.Util.mediumLengthFloatFormatter;
import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;
import static uk.org.cinquin.mutinack.misc_util.Util.reset;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.text.DecimalFormat;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.picard.reference.ReferenceSequenceFile;
import contrib.net.sf.picard.reference.ReferenceSequenceFileFactory;
import contrib.net.sf.samtools.SAMFileHeader;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileReader.QueryInterval;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMFileWriterFactory;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObjectBase;
import contrib.nf.fr.eraasoft.pool.impl.PoolController;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.THashMap;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.features.LocusByLocusNumbersPB;
import uk.org.cinquin.mutinack.features.LocusByLocusNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Signals.SignalProcessor;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.collections.TSVMapReader;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.VersionInfo;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.sequence_IO.IteratorPrefetcher;
import uk.org.cinquin.mutinack.statistics.CounterWithBedFeatureBreakdown;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocOnly;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocation;
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
	
	final static Logger logger = LoggerFactory.getLogger("Mutinack");
	final static Logger statusLogger = LoggerFactory.getLogger("MutinackStatus");
	
	private final static boolean IGNORE_SECONDARY_MAPPINGS = true;
	private final static List<String> outputHeader = Collections.unmodifiableList(Arrays.asList(
			"Notes", "Location", "Mutation type", "Mutation detail", "Sample", "nQ2Duplexes",
			"nQ1Q2Duplexes", "nDuplexes", "nConcurringReads", "fractionConcurringQ2Duplexes", "fractionConcurringQ1Q2Duplexes", 
			"fractionConcurringDuplexes", "fractionConcurringReads", "averageMappingQuality", "nDuplexesSisterArm", "insertSize", "minDistanceLigSite", "maxDistanceLigSite",
			"positionInRead", "readEffectiveLength", "nameOfOneRead", "readAlignmentStart", "mateAlignmentStart",
			"readAlignmentEnd", "mateAlignmentEnd", "refPositionOfLigSite", "issuesList", "medianPhredAtLocus", "minInsertSize", "maxInsertSize", "supplementalMessage"
	));

	private static final Collection<BiConsumer<PrintStream, Integer>> statusUpdateTasks = new ArrayList<>();
	static final Map<Integer, @NonNull String> indexContigNameMap = new ConcurrentHashMap<>();
	static final Map<String, @NonNull Integer> indexContigNameReverseMap = new ConcurrentHashMap<>();
	private static final Set<SequenceLocation> forceOutputAtLocations = new HashSet<>();
	private static int PROCESSING_CHUNK;
	/**
	 * Terminate analysis but finish writing output BAM file.
	 */
	private volatile static boolean terminateAnalysis = false;
	
	private final Collection<Closeable> itemsToClose = new ArrayList<>();
		
	final int ignoreFirstNBasesQ1, ignoreFirstNBasesQ2, ignoreFirstNBaseQ1mQ2;
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
	private final float dropReadProbability;
	final int nConstantBarcodeMismatchesAllowed;
	final int nVariableBarcodeMismatchesAllowed;
	final int alignmentPositionMismatchAllowed;
	final int promoteNQ1Duplexes;
	final int promoteNSingleStrands;
	final float promoteFractionReads;
	private final int minNumberDuplexesSisterArm;
	final boolean ignoreSizeOutOfRangeInserts;
	final boolean ignoreTandemRFPairs;
	final boolean requireMatchInAlignmentEnd;
	final boolean logReadIssuesInOutputBam;
	final int maxAverageBasesClipped;
	final int maxAverageClippingOfAllCoveringDuplexes;
	final float maxFractionWrongPairsAtLocus;
	private final int maxNDuplexes;
	final boolean computeSupplQuality;
	final boolean rnaSeq;
	final Collection<GenomeFeatureTester> excludeBEDs;
	final boolean computeRawDisagreements;

	final int idx;
	final String name;
	private long timeStartProcessing;
	private final @NonNull List<SubAnalyzer> subAnalyzers = new ArrayList<>();
	final @NonNull File inputBam;
	final ObjectPool<SAMFileReader> readerPool = new PoolSettings<> (
			new PoolableObjectBase<SAMFileReader>() {

				@Override
				public SAMFileReader make() {
					SAMFileReader reader = new SAMFileReader(inputBam);
					if (!reader.hasIndex()) {
						throw new IllegalArgumentException("File " + inputBam + " does not have index");
					}
					return reader;
				}

				@Override
				public void activate(SAMFileReader t) {
					//Nothing to be done to reset
				}
			}).min(0).max(300).pool(); 	//Need min(0) so inputBam is set before first
										//reader is created
	private final @Nullable Collection<File> intersectAlignmentFiles;
	private final @NonNull Map<String, GenomeFeatureTester> filtersForCandidateReporting = new HashMap<>();
	@Nullable GenomeFeatureTester codingStrandTester;
	final byte @NonNull[] constantBarcode;
	final int variableBarcodeLength;
	final int unclippedBarcodeLength;
	public final @NonNull AnalysisStats stats = new AnalysisStats();

	public Mutinack(Parameters argValues, String name,
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
			List<File> intersectAlignmentFiles,
			int processingChunk,
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
		this.name = name;
		this.inputBam = inputBam;
		this.idx = idx;
		this.ignoreFirstNBasesQ1 = ignoreFirstNBasesQ1;
		this.ignoreFirstNBasesQ2 = ignoreFirstNBasesQ2;
		ignoreFirstNBaseQ1mQ2 = ignoreFirstNBasesQ2 - ignoreFirstNBasesQ1;
		if (ignoreFirstNBaseQ1mQ2 < 0) {
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
		unclippedBarcodeLength = 0;
		
		synchronized(ExtendedSAMRecord.class) {
			if (ExtendedSAMRecord.UNCLIPPED_BARCODE_LENGTH != Integer.MAX_VALUE) {
				if (unclippedBarcodeLength != ExtendedSAMRecord.UNCLIPPED_BARCODE_LENGTH) {
					throw new IllegalArgumentException("Incompatibe total barcode lengths " + unclippedBarcodeLength +
							" and " + ExtendedSAMRecord.UNCLIPPED_BARCODE_LENGTH);
				}
			} else {
				ExtendedSAMRecord.UNCLIPPED_BARCODE_LENGTH = unclippedBarcodeLength;
			}
			
			if (ExtendedSAMRecord.VARIABLE_BARCODE_END != Integer.MAX_VALUE) {
				if (variableBarcodeLength - 1 != ExtendedSAMRecord.VARIABLE_BARCODE_END) {
					throw new IllegalArgumentException("Incompatibe variable barcode ends " + (variableBarcodeLength - 1) +
							" and " + ExtendedSAMRecord.VARIABLE_BARCODE_END);
				}
			} else {
				ExtendedSAMRecord.VARIABLE_BARCODE_END = variableBarcodeLength - 1;
				System.out.println("Setting variable barcode end to " + ExtendedSAMRecord.VARIABLE_BARCODE_END);
			}
		}
		
		this.ignoreSizeOutOfRangeInserts = ignoreSizeOutOfRangeInserts;
		this.ignoreTandemRFPairs = ignoreTandemRFPairs;
		this.intersectAlignmentFiles = intersectAlignmentFiles;
		PROCESSING_CHUNK = processingChunk;
		this.requireMatchInAlignmentEnd = requireMatchInAlignmentEnd;
		this.logReadIssuesInOutputBam = logReadIssuesInOutputBam;
		this.maxAverageBasesClipped = maxAverageBasesClipped;
		this.maxAverageClippingOfAllCoveringDuplexes = maxAverageClippingOfAllCoveringDuplexes;
		this.minMedianPhredQualityAtLocus = minMedianPhredQualityAtLocus;
		this.maxFractionWrongPairsAtLocus = maxFractionWrongPairsAtLocus;
		this.maxNDuplexes = maxNDuplex;
		this.stats.setOutputLevel(outputLevel);
		
		this.computeSupplQuality = promoteNQ1Duplexes != Integer.MAX_VALUE ||
				promoteNSingleStrands != Integer.MAX_VALUE ||
				promoteFractionReads != Float.MAX_VALUE;
		
		this.rnaSeq = rnaSeq;
		this.excludeBEDs = excludeBEDs;
		this.computeRawDisagreements = computeRawDisagreements;
	}
	
	public static void main(String args[]) throws InterruptedException, ExecutionException, FileNotFoundException {
		try {
			realMain(args);
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
		executorService.shutdown();
		ParFor.threadPool.shutdown();
	}
	
	private final static ExecutorService executorService = Executors.newFixedThreadPool(40);

	@SuppressWarnings("resource")
	private static void realMain(String args[]) throws InterruptedException, IOException {
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
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			//commander.usage();
			System.out.println(b);
			return;
		}
		
		if (argValues.version) {
			System.out.println(VersionInfo.gitCommit);
			return;
		}
		
		if (!argValues.noStatusMessages && !argValues.skipVersionCheck) {
			executorService.submit(() -> Util.versionCheck());
		}
		
		if (!argValues.noStatusMessages) {
			Util.printUserMustSeeMessage(VersionInfo.gitCommit);
			Util.printUserMustSeeMessage("Launch time: " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
		}
		
		if (argValues.saveFilteredReadsTo.size() > 0) {
			throw new RuntimeException("Not implemented");
		}
		
		String hostName;
		try {
			hostName = Util.convertStreamToString(
					Runtime.getRuntime().exec("hostname").getInputStream());
		} catch (Throwable t) {
			hostName = "Could not retrieve hostname";
			t.printStackTrace();
		}
		if (!argValues.noStatusMessages) {
			Util.printUserMustSeeMessage("Host: " + hostName);
		}
		System.out.println(argValues.toString());
		System.out.println("Non-trivial assertions " +
				(DebugControl.NONTRIVIAL_ASSERTIONS ? 
				"on" : "off"));

		final List<Mutinack> analyzers = new ArrayList<>();

		final ReferenceSequenceFile refFile = 
				ReferenceSequenceFileFactory.getReferenceSequenceFile(
						new File(argValues.referenceGenome));

		for (int i = 0; i<argValues.inputReads.size(); i++) {
			analyzers.add(null);
		}

		final SAMFileWriter alignmentWriter;

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
		} else {
			alignmentWriter = null;
		}

		final @NonNull List<@NonNull String> contigs;
		final @NonNull Map<String, Integer> contigSizes = new HashMap<>();
		
		if (argValues.readContigsFromFile) {
			contigs = new ArrayList<>();
			try(Stream<String> lines = Files.lines(Paths.get(argValues.referenceGenome))) {
				Handle<String> currentName = new Handle<>();
				SettableInteger currentLength = new SettableInteger(0);
				lines.forEachOrdered(l -> {
					if (l.startsWith(">")) {
						if (currentName.get() != null) {
							contigSizes.put(currentName.get(), currentLength.get());
							currentLength.set(0);
						}
						int endName = l.indexOf(" ");
						if (endName == -1) {
							endName = l.length();
						}
						currentName.set(l.substring(1, endName));
						contigs.add(nonNullify(currentName.get()));
					} else {
						currentLength.addAndGet(l.replaceAll("\\s","").length());
					}
				});
				contigSizes.put(currentName.get(), currentLength.get());
			}
		} else if (!argValues.contigNamesToProcess.equals(new Parameters().contigNamesToProcess)) {
			throw new IllegalArgumentException("Contig names specified both in file and as " +
					"command line argument; pick only one method");
		} else {
			contigs = argValues.contigNamesToProcess;
		}
				
		final @NonNull Map<Object, @NonNull Object> contigNames = new HashMap<>();
		for (int i = 0; i < contigs.size(); i++) {
			contigNames.put(i, contigs.get(i));
			indexContigNameMap.put(i, contigs.get(i));
			indexContigNameReverseMap.put(contigs.get(i), i);
		}
		@SuppressWarnings("null")
		final @NonNull Map<Object, @NonNull Object> contigNamesUnmod = 
				Collections.unmodifiableMap(contigNames);
		CounterWithSeqLocation.contigNames = contigNamesUnmod;
		CounterWithSeqLocOnly.contigNames = contigNamesUnmod;

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
							final SequenceLocation parsedLocation = new SequenceLocation(contigs.indexOf(contig),
									NumberFormat.getNumberInstance(java.util.Locale.US).parse(pos).intValue() - 1);
							if (!forceOutputAtLocations.add(parsedLocation)) {
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
			@NonNull String forceOutputString = forceOutputAtLocations.toString();
			System.out.println("Forcing output at locations " + 
					Util.truncateString(forceOutputString));
		}

		System.out.println(outputHeader.stream().collect(Collectors.joining("\t")));

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
				excludeBEDs.add(new BedReader(indexContigNameMap.values(), 
						new BufferedReader(new FileReader(new File(bed))), bed, null));
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + 
						bed, e);
			}
		}
		
		final List<BedReader> repetitiveBEDs = new ArrayList<>();
		for (String bed: argValues.repetiveRegionBED) {
			try {
				repetitiveBEDs.add(new BedReader(indexContigNameMap.values(), 
						new BufferedReader(new FileReader(new File(bed))), bed, null));
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + 
						bed, e);
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
				System.out.println("Intersecting " + inputBam.getAbsolutePath() + " with " +
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
			
			//TODO Create a constructor that just takes argValues as an input,
			//and/or use the builder pattern
			final Mutinack analyzer = new Mutinack(
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
					argValues.processingChunk,
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
			
			String baseName = (argValues.auxOutputFileBaseName != null ?
					argValues.auxOutputFileBaseName + "_": "") +
					name ;

			if (argValues.outputTopBottomDisagreementBED) {
				String tbdName = baseName + "_top_bottom_disag.bed";
				try {
					analyzer.stats.topBottomDisagreementWriter = new FileWriter(tbdName);
					analyzer.itemsToClose.add(analyzer.stats.topBottomDisagreementWriter);
				} catch (IOException e) {
					handleOutputException(tbdName, e, argValues);
				}
			}

			String mutName = baseName + "_mutations.bed";
			try {
				analyzer.stats.mutationBEDWriter = new FileWriter(mutName);
				analyzer.itemsToClose.add(analyzer.stats.mutationBEDWriter);
			} catch (IOException e) {
				handleOutputException(mutName, e, argValues);
			}

			if (argValues.outputCoverageProto) {
				analyzer.stats.locusByLocusCoverage = new HashMap<>();
				if (contigSizes.isEmpty()) {
					throw new IllegalArgumentException("Need contig sizes for outputCoverageProto; " +
							"set readContigsFromFile option");
				}
				contigSizes.forEach((k,v) -> {
					analyzer.stats.locusByLocusCoverage.put(k, new int [v]);
				});
				Builder builder = LocusByLocusNumbersPB.GenomeNumbers.newBuilder();
				builder.setGeneratingProgramVersion(VersionInfo.gitCommit);
				builder.setGeneratingProgramArgs(argValues.toString());
				builder.setSampleName(name + "_locus_by_locus_coverage");
				analyzer.stats.locusByLocusCoverageProtobuilder = builder;
			}
			
			if (argValues.outputCoverageBed) {
				String coverageName = baseName + "_coverage.bed";
				try {					
					analyzer.stats.coverageBEDWriter = new FileWriter(coverageName);
					analyzer.itemsToClose.add(analyzer.stats.coverageBEDWriter);
				} catch (IOException e) {
					handleOutputException(coverageName, e, argValues);
				}
			}

			
			for (int contigIndex = 0; contigIndex < argValues.contigNamesToProcess.size(); contigIndex++) {
				final int finalContigIndex = contigIndex;
				final SerializablePredicate<SequenceLocation> p = 
						l -> l.contigIndex == finalContigIndex;
				String contigName = argValues.contigNamesToProcess.get(contigIndex);
				analyzer.stats.topBottomSubstDisagreementsQ2.addPredicate(contigName, p);
				analyzer.stats.topBottomDelDisagreementsQ2.addPredicate(contigName, p);
				analyzer.stats.topBottomInsDisagreementsQ2.addPredicate(contigName, p);
				analyzer.stats.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(contigName, p);
				analyzer.stats.codingStrandSubstQ2.addPredicate(contigName, p);
				analyzer.stats.templateStrandSubstQ2.addPredicate(contigName, p);
				analyzer.stats.codingStrandDelQ2.addPredicate(contigName, p);
				analyzer.stats.templateStrandDelQ2.addPredicate(contigName, p);
				analyzer.stats.codingStrandInsQ2.addPredicate(contigName, p);
				analyzer.stats.templateStrandInsQ2.addPredicate(contigName, p);
			}
			
			if (argValues.bedDisagreementOrienter != null) {
				try {
					analyzer.codingStrandTester = new BedReader(indexContigNameMap.values(),
							new BufferedReader(new FileReader(new File(argValues.bedDisagreementOrienter))),
							"", null);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + 
							argValues.bedDisagreementOrienter, e);
				}
			}
			
			Set<String> bedFileNames = new HashSet<>();

			for (String s: argValues.reportStatsForBED) {
				try {
					if (!bedFileNames.add(s)) {
						throw new IllegalArgumentException("Bed file " + s + " specified multiple times");
					}
					final File f = new File(s);
					final String filterName = f.getName();
					final GenomeFeatureTester filter = new BedReader(indexContigNameMap.values(), 
							new BufferedReader(new FileReader(f)), filterName, null);
					final BedComplement notFilter = new BedComplement(filter);
					final String notFilterName = "NOT " + f.getName();

					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
					analyzer.stats.nLociCandidatesForUniqueMutation.addPredicate(filterName, filter);
					analyzer.stats.topBottomSubstDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomDelDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomInsDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomDisagreementsQ2TooHighCoverage.addPredicate(filterName, filter);
					analyzer.stats.codingStrandSubstQ2.addPredicate(filterName, filter);
					analyzer.stats.templateStrandSubstQ2.addPredicate(filterName, filter);
					analyzer.stats.codingStrandDelQ2.addPredicate(filterName, filter);
					analyzer.stats.templateStrandDelQ2.addPredicate(filterName, filter);
					analyzer.stats.codingStrandInsQ2.addPredicate(filterName, filter);
					analyzer.stats.templateStrandInsQ2.addPredicate(filterName, filter);
					analyzer.stats.lackOfConsensus1.addPredicate(filterName, filter);
					analyzer.stats.lackOfConsensus2.addPredicate(filterName, filter);
					analyzer.filtersForCandidateReporting.put(filterName, filter);

					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(notFilterName, notFilter);
					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(notFilterName, notFilter);
					analyzer.stats.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(notFilterName, notFilter);
					analyzer.stats.nLociCandidatesForUniqueMutation.addPredicate(notFilterName, notFilter);
					analyzer.stats.topBottomSubstDisagreementsQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.topBottomDelDisagreementsQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.topBottomInsDisagreementsQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.topBottomDisagreementsQ2TooHighCoverage.addPredicate(notFilterName, notFilter);
					analyzer.stats.codingStrandSubstQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.templateStrandSubstQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.codingStrandDelQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.templateStrandDelQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.codingStrandInsQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.templateStrandInsQ2.addPredicate(notFilterName, notFilter);
					analyzer.stats.lackOfConsensus1.addPredicate(notFilterName, notFilter);
					analyzer.stats.lackOfConsensus2.addPredicate(notFilterName, notFilter);
					analyzer.filtersForCandidateReporting.put(notFilterName, notFilter);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + s, e);
				}
			}

			for (String s: argValues.reportStatsForNotBED) {
				try {
					final File f = new File(s);
					final String filterName = "NOT " + f.getName();
					final GenomeFeatureTester filter0 = new BedReader(indexContigNameMap.values(),
							new BufferedReader(new FileReader(f)), filterName, null);
					final BedComplement filter = new BedComplement(filter0);
					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexesCandidatesForDisagreementQ2.addPredicate(filterName, filter);
					analyzer.stats.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
					analyzer.stats.nLociCandidatesForUniqueMutation.addPredicate(filterName, filter);
					analyzer.stats.topBottomSubstDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomDelDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomInsDisagreementsQ2.addPredicate(filterName, filter);
					analyzer.stats.topBottomDisagreementsQ2TooHighCoverage.addPredicate(filterName, filter);
					analyzer.stats.lackOfConsensus1.addPredicate(filterName, filter);
					analyzer.stats.lackOfConsensus2.addPredicate(filterName, filter);

					analyzer.filtersForCandidateReporting.put(filterName, filter);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + s, e);
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
					final BedReader filter = new BedReader(indexContigNameMap.values(), 
							new BufferedReader(new FileReader(f)), f.getName(),
							argValues.bedFeatureSuppInfoFile == null ? null :
							new BufferedReader(new FileReader(argValues.bedFeatureSuppInfoFile)));
					CounterWithBedFeatureBreakdown counter = new CounterWithBedFeatureBreakdown(filter,
							argValues.refSeqToOfficialGeneName == null ?
								null :
								TSVMapReader.getMap(new BufferedReader(
										new FileReader(argValues.refSeqToOfficialGeneName))));
					
					String outputPath = argValues.saveBEDBreakdownTo.get(index);
					if (!outputPaths.add(outputPath)) {
						throw new IllegalArgumentException("saveBEDBreakdownTo " + outputPath +
								" specified multiple times");
					}
					counter.setOutputFile(new File(outputPath + "_nLociDuplex_" + ".bed"));
					analyzer.stats.nLociDuplex.addPredicate(f.getName(), filter, counter);
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
					
					counter = new CounterWithBedFeatureBreakdown(filter, null);
					counter.setOutputFile(new File(argValues.saveBEDBreakdownTo.get(index) +
							"_nLociDuplexQualityQ2OthersQ1Q2_" + name + ".bed"));
					analyzer.stats.nLociDuplexQualityQ2OthersQ1Q2.addPredicate(f.getName(), filter, counter);
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

		SignalProcessor infoSignalHandler = signal -> {
            PrintStream printStream = (signal == null) ? System.out : System.err;
            for (Mutinack analyzer: analyzers) {
                analyzer.printStatus(printStream, signal != null);
            }

            if (signal != null) {
                synchronized(statusUpdateTasks) {
                    for (BiConsumer<PrintStream, Integer> update: statusUpdateTasks) {
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
			final String contigName = (String) contigNames.get(contigIndex);
			int idx = truncateAtContigs.indexOf(contigName);
			if (idx > -1) {
				terminateContigAtPosition = truncateAtPositions.get(idx);
			} else {
				idx = Parameters.defaultTruncateContigNames.indexOf(contigName);
				if (idx == -1) {
					//TODO Make this less ad-hoc
					terminateContigAtPosition = contigSizes.get(contigName) - 1;
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
			analysisChunks.add(analysisChunk);

			final int startSubAt = startContigAtPosition + p * subAnalyzerSpan;
			final int terminateAtPosition = (p == argValues.parallelizationFactor - 1) ?
					terminateContigAtPosition 
					: startSubAt + subAnalyzerSpan - 1;

			analysisChunk.contig = contigIndex;
			analysisChunk.startAtPosition = startSubAt;
			analysisChunk.terminateAtPosition = terminateAtPosition;
			analysisChunk.pauseAtPosition = new SettableInteger();
			analysisChunk.lastProcessedPosition = new SettableInteger();
			
			final SettableInteger lastProcessedPosition = analysisChunk.lastProcessedPosition;
			final SettableInteger pauseAt = analysisChunk.pauseAtPosition;
			final SettableInteger previousLastProcessable = new SettableInteger(-1);
			final List<LocationExaminationResults> analyzerCandidateLists = new ArrayList<>();
			analyzers.forEach(a -> {
				if (a == null) {
					throw new AssertionFailedException();
				}
				analyzerCandidateLists.add(null);
				final SubAnalyzer subAnalyzer = new SubAnalyzer(a);
				a.subAnalyzers.add(subAnalyzer);
				analysisChunk.subAnalyzers.add(subAnalyzer);
			});
			
			final Phaser phaser = new Phaser() {

				private final ConcurrentHashMap<String, SAMRecord> readsToWrite = alignmentWriter == null ?
						null
						: new ConcurrentHashMap<>(100_000);
				private final SAMFileWriter outputAlignment = alignmentWriter;
				
				private final int nSubAnalyzers = analysisChunk.subAnalyzers.size();
				private int nIterations = 0;
				
				final AtomicInteger dn = new AtomicInteger(0);

				@Override
				protected final boolean onAdvance(int phase, int registeredParties) {
					//This is the place to make comparisons between analyzer results

					if (dn.get() >= 1_000) {
						dn.set(0);
					}
					
					try {
						final ParFor pf0 = new ParFor("Load " + contigNames.get(analysisChunk.contig) + " " + lastProcessedPosition.get() +
								"-" + pauseAt.get(), 0, nSubAnalyzers - 1 , null, true);
						for (int worker = 0; worker < nSubAnalyzers; worker++) {
							pf0.addLoopWorker((loopIndex, j) -> {
								SubAnalyzer sub = analysisChunk.subAnalyzers.get(loopIndex);
								if (NONTRIVIAL_ASSERTIONS && nIterations > 1 && sub.candidateSequences.containsKey(
										new SequenceLocation(contigIndex, lastProcessedPosition.get()))) {
									throw new AssertionFailedException();
								}
								sub.load();
								return null;
							}
							);
						}
						pf0.run(true);

						final int saveLastProcessedPosition = lastProcessedPosition.get();
						outer:
						for (int position = saveLastProcessedPosition + 1; position <= pauseAt.get() && !terminateAnalysis;
								position ++) {

							if (position > terminateAtPosition) {
								break;
							}
							
							final @NonNull SequenceLocation location = new SequenceLocation(contigIndex, position);

							for (GenomeFeatureTester tester: excludeBEDs) {
								if (tester.test(location)) {
									analysisChunk.subAnalyzers.parallelStream().
										forEach(sa -> {sa.candidateSequences.remove(location);
												sa.stats.nLociExcluded.add(location, 1);});
									lastProcessedPosition.set(position);
									continue outer;
								}
							}

							ParFor pf = new ParFor("Examination ", 0, nSubAnalyzers -1 , null, true);
							for (int worker = 0; worker < analyzers.size(); worker++) {
								pf.addLoopWorker((loopIndex,j) -> {
									analyzerCandidateLists.set(loopIndex, analysisChunk.subAnalyzers.get(loopIndex)
											.examineLocation(location));
									return null;});
							}
							pf.run(true);

							final int dubiousOrGoodInAllInputsAtPos = analyzerCandidateLists.stream().
									mapToInt(ler -> ler.nGoodOrDubiousDuplexes).min().getAsInt();

							final int goodDuplexCovInAllInputsAtPos = analyzerCandidateLists.stream()
									.mapToInt(ler -> ler.nGoodDuplexes).min().getAsInt();

							dubiousOrGoodDuplexCovInAllInputs.insert(dubiousOrGoodInAllInputsAtPos);
							goodDuplexCovInAllInputs.insert(goodDuplexCovInAllInputsAtPos);
							
							final Handle<Boolean> tooHighCoverage = new Handle<>(false);

							analyzers.parallelStream().forEach(a -> {
								
								final LocationExaminationResults examResults = analyzerCandidateLists.get(a.idx);
								@Nullable
								OutputStreamWriter cbw = a.stats.coverageBEDWriter;
								if (cbw != null) {
									try {
										cbw.append(	location.getContigName() + "\t" + 
													(location.position + 1) + "\t" +
													(location.position + 1) + "\t" +
													examResults.nGoodOrDubiousDuplexes + "\n");
									} catch (IOException e) {
										throw new RuntimeException(e);
									}
								}
								
								if (a.stats.locusByLocusCoverage != null) {
									int[] array = a.stats.locusByLocusCoverage.get(location.getContigName());
									if (array.length <= location.position) {
										throw new IllegalArgumentException("Position goes beyond end of contig " +
												location.getContigName() + ": " + location.position +
												" vs " + array.length);
									} else {
										array[location.position] += examResults.nGoodOrDubiousDuplexes;
									}
								}
								
								for (CandidateSequence c: examResults.analyzedCandidateSequences) {
									if (c.getQuality().getMin().compareTo(GOOD) >= 0) {
										if (c.getMutationType() == WILDTYPE) {
											a.stats.wtQ2CandidateQ1Q2Coverage.insert(examResults.nGoodOrDubiousDuplexes);
											if (!repetitiveBEDs.isEmpty()) {
												boolean repetitive = false;
												for (GenomeFeatureTester t: repetitiveBEDs) {
													if (t.test(location)) {
														repetitive = true;
														break;
													}
												}
												if (repetitive) {
													a.stats.wtQ2CandidateQ1Q2CoverageRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
												} else {
													a.stats.wtQ2CandidateQ1Q2CoverageNonRepetitive.insert(examResults.nGoodOrDubiousDuplexes);																
												}
											}
										} else {
											a.stats.mutantQ2CandidateQ1Q2Coverage.insert(examResults.nGoodOrDubiousDuplexes);
											if (!repetitiveBEDs.isEmpty()) {
												boolean repetitive = false;
												for (GenomeFeatureTester t: repetitiveBEDs) {
													if (t.test(location)) {
														repetitive = true;
														break;
													}
												}
												if (repetitive) {
													a.stats.mutantQ2CandidateQ1Q2DCoverageRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
												} else {
													a.stats.mutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert(examResults.nGoodOrDubiousDuplexes);																
												}
											}
										}
									}
								}
								
								final boolean localTooHighCoverage = examResults.nGoodOrDubiousDuplexes > a.maxNDuplexes;
								
								if (localTooHighCoverage) {
									a.stats.nLociIgnoredBecauseTooHighCoverage.increment(location);
									tooHighCoverage.set(true);
								}
																		
								if (!localTooHighCoverage) {
									//a.stats.nLociDuplexesCandidatesForDisagreementQ2.accept(location, examResults.nGoodDuplexesIgnoringDisag);
				
									for (@NonNull ComparablePair<String, String> var: examResults.rawMismatchesQ2) {
										a.stats.rawMismatchesQ2.accept(location, var);
									}
									
									for (@NonNull ComparablePair<String, String> var: examResults.rawDeletionsQ2) {
										a.stats.rawDeletionsQ2.accept(location, var);
										a.stats.rawDeletionLengthQ2.insert(var.snd.length());
									}
									
									for (@NonNull ComparablePair<String, String> var: examResults.rawInsertionsQ2) {
										a.stats.rawInsertionsQ2.accept(location, var);
										a.stats.rawInsertionLengthQ2.insert(var.snd.length());
									}
									
									for (ComparablePair<Mutation, Mutation> d: examResults.disagreements) {
										Mutation mutant = d.getSnd();
										if (mutant.mutationType == SUBSTITUTION) {
											a.stats.topBottomSubstDisagreementsQ2.accept(location, d);
											mutant.getTemplateStrand().ifPresent( b ->
													{if (b) a.stats.templateStrandSubstQ2.accept(location, d);
													else a.stats.codingStrandSubstQ2.accept(location, d);});
										} else if (mutant.mutationType == DELETION) {
											a.stats.topBottomDelDisagreementsQ2.accept(location, d);
											mutant.getTemplateStrand().ifPresent( b ->
													{if (b) a.stats.templateStrandDelQ2.accept(location, d);
													else a.stats.codingStrandDelQ2.accept(location, d);});
										} else if (mutant.mutationType == INSERTION) {
											a.stats.topBottomInsDisagreementsQ2.accept(location, d);
											mutant.getTemplateStrand().ifPresent( b ->
													{if (b) a.stats.templateStrandInsQ2.accept(location, d);
													else a.stats.codingStrandInsQ2.accept(location, d);});
										}

										byte[] fstSeq = d.getFst().getSequence();
										if (fstSeq == null) {
											fstSeq = new byte[] {'?'};
										}
										byte[] sndSeq = d.getSnd().getSequence();
										if (sndSeq == null) {
											sndSeq = new byte[] {'?'};
										}
										
										try {
											@Nullable
											OutputStreamWriter tpdw = a.stats.topBottomDisagreementWriter;
											if (tpdw != null) {
												tpdw.append(location.getContigName() + "\t" +
														(location.position + 1) + "\t" + (location.position + 1) + "\t" +
														(mutant.mutationType == SUBSTITUTION ?
																(new String(fstSeq) + "" + new String(sndSeq)) 
																:
																new String (sndSeq))
														+ "\t" +
														mutant.mutationType + "\n");
											}
										} catch (IOException e) {
											throw new RuntimeException(e);
										}
									}
								} else {
									a.stats.nLociDuplexesCandidatesForDisagreementQ2TooHighCoverage.accept(location, examResults.nGoodDuplexesIgnoringDisag);
									for (ComparablePair<Mutation, Mutation> d: examResults.disagreements) {
										a.stats.topBottomDisagreementsQ2TooHighCoverage.accept(location, d);
									}
								}
																
								if (	(!localTooHighCoverage) &&
										examResults.nGoodDuplexes >= argValues.minQ2DuplexesToCallMutation && 
										examResults.nGoodOrDubiousDuplexes >= argValues.minQ1Q2DuplexesToCallMutation &&
										analyzers.stream().filter(b -> b != a).mapToInt(b -> 
												analyzerCandidateLists.get(b.idx).nGoodOrDubiousDuplexes).sum()
											>= a.minNumberDuplexesSisterArm
									) {
									
									examResults.analyzedCandidateSequences.stream().filter(c -> !c.isHidden()).flatMap(c -> c.getDuplexes().stream()).
										filter(dr -> dr.localQuality.getMin().compareTo(GOOD) >= 0).map(DuplexRead::getDistanceToLigSite).
										forEach(i -> {if (i != Integer.MIN_VALUE && i != Integer.MAX_VALUE)
											a.stats.realQ2CandidateDistanceToLigationSite.insert(i);});
									
									a.stats.nLociDuplexQualityQ2OthersQ1Q2.accept(location, examResults.nGoodDuplexes);
									a.stats.nLociQualityQ2OthersQ1Q2.increment(location);
									//XXX The following includes all candidates at *all* positions considered in
									//processing chunk 
									a.stats.nReadsAtLociQualityQ2OthersQ1Q2.insert(
											examResults.analyzedCandidateSequences.
											stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
									a.stats.nQ1Q2AtLociQualityQ2OthersQ1Q2.insert(
											examResults.analyzedCandidateSequences.
											stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());
								}
							});//End parallel loop over analyzers to deal with coverage
							
							//Filter candidates to identify non-wildtype versions
							List<CandidateSequence> mutantCandidates = analyzerCandidateLists.stream().
									map(c -> c.analyzedCandidateSequences).
									flatMap(Collection::stream).filter(c -> {
										boolean isMutant = c.getMutationType() != WILDTYPE;
										return isMutant;
									}).
									collect(Collectors.toList());

							final Quality maxCandMutQuality = mutantCandidates.stream().
									map(c -> c.getQuality().getMin()).max(Quality::compareTo).orElse(ATROCIOUS);

							final boolean forceReporting = forceOutputAtLocations.contains(location);

							//Only report details when at least one mutant candidate is of Q2 quality
							if (forceReporting || (maxCandMutQuality.compareTo(GOOD) >= 0)) {
								if (NONTRIVIAL_ASSERTIONS) for (GenomeFeatureTester t: excludeBEDs) {
									if (t.test(location)) {
										throw new AssertionFailedException(location + " excluded by " + t);
									}
								}

								//Refilter also allowing Q1 candidates to compare output of different
								//analyzers
								final List<CandidateSequence> allQ1Q2Candidates = analyzerCandidateLists.stream().
										map(l -> l.analyzedCandidateSequences).
										flatMap(Collection::stream).
										filter(c -> {if (NONTRIVIAL_ASSERTIONS && !c.getLocation().equals(location)) {throw new AssertionFailedException();};
										return c.getQuality().getMin().compareTo(POOR) > 0;}).
										filter(c -> !c.isHidden()).
										sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
										collect(Collectors.toList());
								
								final List<CandidateSequence> allCandidatesIncludingDisag = analyzerCandidateLists.stream().
										map(l -> l.analyzedCandidateSequences).
										flatMap(Collection::stream).
										filter(c -> c.getLocation().equals(location) && c.getQuality().getQuality(Assay.MAX_DPLX_Q_IGNORING_DISAG).compareTo(POOR) > 0).
										sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
										collect(Collectors.toList());

								final List<CandidateSequence> distinctQ1Q2Candidates = allQ1Q2Candidates.stream().distinct().
										//Sorting might not be necessary
										sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
										collect(Collectors.toList());

								for (CandidateSequence candidate: distinctQ1Q2Candidates) {
									String baseOutput0 = "";

									if (candidate.getMutationType() != WILDTYPE &&
											allCandidatesIncludingDisag.stream().filter(c -> c.equals(candidate) &&
													(c.getQuality().getMin().compareTo(GOOD) >= 0 ||
														(c.getSupplQuality() != null && nonNullify(c.getSupplQuality()).compareTo(GOOD) >= 0))).count() >= 2) {
										baseOutput0 += "!";
									} else if (allCandidatesIncludingDisag.stream().filter(c -> c.equals(candidate)).count() == 1 &&
											//Mutant candidate shows up only once (and therefore in only 1 analyzer)
											candidate.getMutationType() != WILDTYPE &&
											candidate.getQuality().getMin().compareTo(GOOD) >= 0 &&
											(candidate.getnGoodDuplexes() >= argValues.minQ2DuplexesToCallMutation) &&
											candidate.getnGoodOrDubiousDuplexes() >= argValues.minQ1Q2DuplexesToCallMutation &&
											(!tooHighCoverage.get()) &&
											allQ1Q2Candidates.stream().filter(c -> c.getOwningAnalyzer() != candidate.getOwningAnalyzer()).
											mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum() >= argValues.minNumberDuplexesSisterArm
											) {
										//Then highlight the output with a *
										
										baseOutput0 += "*";
										
										candidate.nDuplexesSisterArm = allQ1Q2Candidates.stream().filter(c -> c.getOwningAnalyzer() != candidate.getOwningAnalyzer()).
												mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum();
										
										baseOutput0 += candidate.getnGoodOrDubiousDuplexes();
										
										analyzers.forEach(a -> {
												CandidateSequence c = analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
														stream().filter(cd -> cd.equals(candidate)).findAny().orElse(null);
												
												if (c != null) {
														a.stats.nLociCandidatesForUniqueMutation.accept(location, c.getnGoodDuplexes());
														a.stats.uniqueMutantQ2CandidateQ1Q2DCoverage.insert((int) c.getTotalGoodOrDubiousDuplexes());
														if (!repetitiveBEDs.isEmpty()) {
															boolean repetitive = false;
															for (GenomeFeatureTester t: repetitiveBEDs) {
																if (t.test(location)) {
																	repetitive = true;
																	break;
																}
															}
															if (repetitive) {
																a.stats.uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive.insert((int) c.getTotalGoodOrDubiousDuplexes());
															} else {
																a.stats.uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert((int) c.getTotalGoodOrDubiousDuplexes());																
															}
														}
													}
											});
										analyzers.stream().filter(a -> analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
												contains(candidate)).forEach(a -> {
													a.stats.nReadsAtLociWithSomeCandidateForQ2UniqueMutation.insert(
															analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
															stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
												});
										analyzers.stream().filter(a -> analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
												contains(candidate)).forEach(a -> {
													a.stats.nQ1Q2AtLociWithSomeCandidateForQ2UniqueMutation.insert(
															analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
															stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());
												});
									}

									if (!allQ1Q2Candidates.stream().anyMatch(c -> c.getOwningAnalyzer() == candidate.getOwningAnalyzer() &&
											c.getMutationType() == WILDTYPE)) {
										//At least one analyzer does not have a wildtype candidate
										baseOutput0 += "|";
									} 

									if (!distinctQ1Q2Candidates.stream().anyMatch(c -> c.getMutationType() == WILDTYPE)) {
										//No wildtype candidate at this position
										baseOutput0 += "_";
									}

									String baseOutput = baseOutput0 + "\t" + location + "\t" + candidate.getKind() + "\t" +
											(candidate.getMutationType() != WILDTYPE ? 
													candidate.getChange() : "");

									//Now output information for each analyzer
									for (Mutinack analyzer: analyzers) {
										final List<CandidateSequence> l = analyzerCandidateLists.get(analyzer.idx).analyzedCandidateSequences.
												stream().filter(c -> c.equals(candidate)).collect(Collectors.toList());

										String line = baseOutput + "\t" + analyzer.name + "\t";

										final CandidateSequence c;
										int nCandidates = l.size();
										if (nCandidates > 1) {
											throw new AssertionFailedException();
										} else if (nCandidates == 0) {
											//Analyzer does not have matching candidate (i.e. it did not get
											//any reads matching the mutation)
											continue;
										} else {//1 candidate
											c = l.get(0);
											NumberFormat formatter = mediumLengthFloatFormatter.get();
											line += c.getnGoodDuplexes() + "\t" + 
													c.getnGoodOrDubiousDuplexes() + "\t" +
													c.getnDuplexes() + "\t" +
													c.getNonMutableConcurringReads().size() + "\t" +
													formatter.format((c.getnGoodDuplexes() / c.getTotalGoodDuplexes())) + "\t" +
													formatter.format((c.getnGoodOrDubiousDuplexes() / c.getTotalGoodOrDubiousDuplexes())) + "\t" +
													formatter.format((c.getnDuplexes() / c.getTotalAllDuplexes())) + "\t" +
													formatter.format((c.getNonMutableConcurringReads().size() / c.getTotalReadsAtPosition())) + "\t" +
													(c.getAverageMappingQuality() == -1 ? "?" : c.getAverageMappingQuality()) + "\t" +
													c.nDuplexesSisterArm + "\t" +
													c.insertSize + "\t" +
													c.minDistanceToLigSite + "\t" +
													c.maxDistanceToLigSite + "\t" +
													c.positionInRead + "\t" +
													c.readEL + "\t" +
													c.readName + "\t" +
													c.readAlignmentStart  + "\t" +
													c.mateReadAlignmentStart  + "\t" +
													c.readAlignmentEnd + "\t" +
													c.mateReadAlignmentEnd + "\t" +
													c.refPositionOfMateLigationSite + "\t" +
													(argValues.outputDuplexDetails ?
															c.getIssues() : "") + "\t" +
													c.getMedianPhredAtLocus() + "\t" +
													(c.getMinInsertSize() == -1 ? "?" : c.getMinInsertSize()) + "\t" +
													(c.getMaxInsertSize() == -1 ? "?" : c.getMaxInsertSize()) + "\t" +
													(c.getSupplementalMessage() != null ? c.getSupplementalMessage() : "") +
													"\t";

											boolean needComma = false;
											for (Entry<String, GenomeFeatureTester> a: analyzer.filtersForCandidateReporting.entrySet()) {
												if (!(a.getValue() instanceof BedComplement) && a.getValue().test(location)) {
													if (needComma) {
														line += ", ";
													}
													needComma = true;
													Object val = a.getValue().apply(location);
													line += a.getKey() + (val != null ? (": " + val) : "");
												}
											}
										}

										try {
											@Nullable
											final OutputStreamWriter ambw = analyzer.stats.mutationBEDWriter;
											if (ambw != null) {
												ambw.append(location.getContigName() + "\t" + 
														(location.position + 1) + "\t" + (location.position + 1) + "\t" +
														candidate.getKind() + "\t" + baseOutput0 + "\t" +
														c.getnGoodDuplexes() +
														"\n");
											}
										} catch (IOException e) {
											throw new RuntimeException(e);
										}

										System.out.println(line);
									}//End loop over analyzers
								}//End loop over mutation candidates
							}//End of candidate reporting
							lastProcessedPosition.set(position);
							
							if (readsToWrite != null) {
								analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {

									Mutinack analyzer = subAnalyzer.getAnalyzer();

									//If outputting an alignment populated with fields identifying the duplexes,
									//fill in the fields here
									for (DuplexRead duplexRead: subAnalyzer.analyzedDuplexes) {
										if (!duplexRead.leftAlignmentStart.equals(location)) {
											continue;
										}
										final int randomIndexForDuplexName = dn.incrementAndGet();

										final int nReads = duplexRead.bottomStrandRecords.size() + duplexRead.topStrandRecords.size();
										final Quality minDuplexQualityCopy = duplexRead.minQuality;
										final Quality maxDuplexQualityCopy = duplexRead.maxQuality;
										Handle<String> topOrBottom = new Handle<>();
										Consumer<ExtendedSAMRecord> queueWrite = (ExtendedSAMRecord e) -> {
											final SAMRecord samRecord = e.record;
											Integer dsAttr = (Integer) samRecord.getAttribute("DS");
											if (dsAttr == null || nReads > dsAttr) {
												if (dsAttr != null && readsToWrite.containsKey(e.getFullName())) {
													//Read must have been already written
													//For now, don't try to correct it with better duplex
													return;
												}
												samRecord.setAttribute("DS", Integer.valueOf(nReads));
												samRecord.setAttribute("DT", Integer.valueOf(duplexRead.topStrandRecords.size()));
												samRecord.setAttribute("DB", Integer.valueOf(duplexRead.bottomStrandRecords.size()));
												String info = topOrBottom.get() + " Q" +
														minDuplexQualityCopy.toShortString() + "->" + maxDuplexQualityCopy.toShortString() + " P" + duplexRead.getMinMedianPhred() +
														" D" + mediumLengthFloatFormatter.get().format(duplexRead.referenceDisagreementRate);
												samRecord.setAttribute("DI", info);
												samRecord.setAttribute("DN", randomIndexForDuplexName);
												samRecord.setAttribute("VB", new String(e.variableBarcode));
												samRecord.setAttribute("VM", new String(e.getMateVariableBarcode()));
												samRecord.setAttribute("DE", duplexRead.leftAlignmentStart + "-" + duplexRead.leftAlignmentEnd + " --- " +
														duplexRead.rightAlignmentStart + "-" + duplexRead.rightAlignmentEnd);
												if (duplexRead.issues.size() > 0) {
													samRecord.setAttribute("IS", duplexRead.issues.toString());
												} else {
													samRecord.setAttribute("IS", null);
												}
												samRecord.setAttribute("AI", Integer.valueOf(analyzer.idx));
												readsToWrite.put(e.getFullName(), samRecord);
											}
										};
										if (argValues.collapseFilteredReads) {
											final ExtendedSAMRecord r;
											topOrBottom.set("U");
											if (duplexRead.topStrandRecords.size() > 0) {
												r = duplexRead.topStrandRecords.get(0);
											} else if (duplexRead.bottomStrandRecords.size() > 0) {
												r = duplexRead.bottomStrandRecords.get(0);
											} else {
												//TODO Should that be an error?
												r = null;
											}
											if (r != null) {
												queueWrite.accept(r);
												if (r.getMate() != null) {
													queueWrite.accept(r.getMate());
												}
											}
										} else {
											topOrBottom.set("T");
											duplexRead.topStrandRecords.forEach(queueWrite);
											topOrBottom.set("B");
											duplexRead.bottomStrandRecords.forEach(queueWrite);
										}
									}//End writing
								});
							}//End readsToWrite != null
						}//End loop over positions
						
						if (ENABLE_TRACE && shouldLog(TRACE)) {
							logger.trace("Going from " + saveLastProcessedPosition + " to " + lastProcessedPosition.get() + " for chunk " + analysisChunk);
						}

						analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
							Mutinack analyzer = subAnalyzer.getAnalyzer();

							for (int position = saveLastProcessedPosition + 1; position <= lastProcessedPosition.get();
									position ++) {
								subAnalyzer.candidateSequences.remove(new SequenceLocation(contigIndex, position));
							}
							if (shouldLog(TRACE)) {
								logger.trace("SubAnalyzer " + analysisChunk + " completed " + (saveLastProcessedPosition + 1) + " to " + lastProcessedPosition.get());
							}
							
							final Iterator<ExtendedSAMRecord> iterator = subAnalyzer.extSAMCache.values().iterator();
							final int localPauseAt = pauseAt.get();
							final int maxInsertSize = analyzer.maxInsertSize;
							if (analyzer.rnaSeq) {
								while (iterator.hasNext()) {
									if (iterator.next().getAlignmentEndNoBarcode() + maxInsertSize <= localPauseAt) {
										iterator.remove();
									}
								}
							} else {
								while (iterator.hasNext()) {
									if (iterator.next().getAlignmentStartNoBarcode() + maxInsertSize <= localPauseAt) {
										iterator.remove();
									}
								}
							}
							if (subAnalyzer.extSAMCache instanceof THashMap) {
								((THashMap<?,?>) subAnalyzer.extSAMCache).compact();
							}
						});//End parallel loop over subAnalyzers

						if (outputAlignment != null) {
							synchronized(outputAlignment) {
								for (SAMRecord samRecord: readsToWrite.values()) {
									outputAlignment.addAlignment(samRecord);
								}
							}
							readsToWrite.clear();
						}
						

						if (nIterations < 2) {
							nIterations++;
							analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
								Iterator<Entry<SequenceLocation, Map<CandidateSequence, CandidateSequence>>> it =
										subAnalyzer.candidateSequences.entrySet().iterator();
								final SequenceLocation lowerBound = new SequenceLocation(contigIndex, lastProcessedPosition.get());
								while (it.hasNext()) {
									Entry<SequenceLocation, Map<CandidateSequence, CandidateSequence>> v = it.next();
									if (NONTRIVIAL_ASSERTIONS && v.getKey().contigIndex != contigIndex) {
										throw new AssertionFailedException("Problem with contig indices, " +
												"possibly because contigs not alphabetically sorted in reference genome .fa file");
									}
									if (v.getKey().compareTo(lowerBound) < 0) {
										it.remove();
									}
								}
							});
						}

						if (NONTRIVIAL_ASSERTIONS) {
							//Check no sequences have been left behind
							analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
								final SequenceLocation lowerBound = new SequenceLocation(contigIndex, lastProcessedPosition.get());
								subAnalyzer.candidateSequences.keySet().parallelStream().forEach(
										e -> {
											if (e.contigIndex != contigIndex) {
												throw new AssertionFailedException();
											}
											if (e.compareTo(lowerBound) < 0)
												throw new AssertionFailedException("pauseAt:" + pauseAt.get() + "; lastProcessedPosition:" + lastProcessedPosition +
														" but found: " + e + " for chunk " + analysisChunk);});
							});
						}

						final int maxLastProcessable = analysisChunk.subAnalyzers.stream().mapToInt(a -> a.lastProcessablePosition.get()).
								max().getAsInt();

						if (maxLastProcessable == previousLastProcessable.get()) {
							logger.debug("Phaser " + this + " will terminate");
							return true;
						}

						previousLastProcessable.set(maxLastProcessable);
						pauseAt.set(maxLastProcessable + PROCESSING_CHUNK);
						
						return false;
					} catch (Exception e) {
						forceTermination();
						throw new RuntimeException(e);
					}
				}//End onAdvance
			};//End local Phaser definition
			analysisChunk.phaser = phaser;
			phaser.bulkRegister(analyzers.size());
			phasers.add(phaser);
		}//End parallelization loop over analysisChunks
		}//End loop over contig index

		//TODO: Fix indentation
		final int endIndex = contigs.size() - 1;
		ParFor parFor = new ParFor("contig loop", 0, endIndex, null, true);
		parFor.setNThreads(60);//TODO Make this a parameter
		
		for (int worker = 0; worker < 60; worker++) 
		parFor.addLoopWorker((final int contigIndex, final int threadIndex) -> {
		final Map<String, ReferenceSequence> refMap = new ConcurrentHashMap<>();

		final List<Future<?>> futures = new ArrayList<>();
		final SettableInteger analyzerIndex = new SettableInteger(-1);
		final List<Throwable> exceptionList = new ArrayList<>();

		for (Mutinack analyzer: analyzers) {
			analyzerIndex.incrementAndGet();

			for (int p = 0; p < argValues.parallelizationFactor; p++) {
				final AnalysisChunk analysisChunk = analysisChunks.get(contigIndex * argValues.parallelizationFactor + p);
				
				final SubAnalyzer subAnalyzer = analysisChunk.subAnalyzers.get(analyzerIndex.get());
				final SettableInteger lastProcessable = subAnalyzer.lastProcessablePosition;
				final int startAt = analysisChunk.startAtPosition;
				final SettableInteger pauseAt = analysisChunk.pauseAtPosition;
				final SettableInteger lastProcessedPosition = analysisChunk.lastProcessedPosition;

				lastProcessable.set(startAt - 1);
				lastProcessedPosition.set(startAt - 1);
				pauseAt.set(lastProcessable.get() + PROCESSING_CHUNK);

				@SuppressWarnings({"null"})
				Runnable r = () -> {

					final Phaser phaser = analysisChunk.phaser;
					String lastReferenceName = null;

					try {
					final String contig = contigs.get(contigIndex);
					final int truncateAtPosition = analysisChunk.terminateAtPosition;

					final Set<String> droppedReads = analyzer.dropReadProbability > 0 ? new HashSet<>(1_000_000) : null;
					final Set<String> keptReads = analyzer.dropReadProbability > 0 ?  new HashSet<>(1_000_000) : null;

					final SequenceLocation contigLocation = new SequenceLocation(contigIndex,0);
					
					BiConsumer<PrintStream, Integer> info = (stream, userRequestNumber) -> {
						NumberFormat formatter = NumberFormat.getInstance();
						stream.println("Analyzer " + analyzer.name +
							" contig " + contig + 
							" range: " + (analysisChunk.startAtPosition + "-" + analysisChunk.terminateAtPosition) +
							"; pauseAtPosition: " + formatter.format(pauseAt.get()) +
							"; lastProcessedPosition: " + formatter.format(lastProcessedPosition.get()) + "; " +
							formatter.format(100f * (lastProcessedPosition.get() - analysisChunk.startAtPosition) / 
									(analysisChunk.terminateAtPosition - analysisChunk.startAtPosition)) + "% done"
							);
					};
					synchronized(statusUpdateTasks) {
						statusUpdateTasks.add(info);
					}

					final SAMFileReader bamReader = analyzer.readerPool.getObj();
					SAMRecordIterator it0 = null;
					try {

						if (contigs.get(0).equals(contig)) {
							analyzer.stats.nRecordsInFile.add(contigLocation, Util.getTotalReadCount(bamReader));
						}

						int furthestPositionReadInContig = 0;
						final int maxInsertSize = analyzer.maxInsertSize;
						final QueryInterval[] bamContig = new QueryInterval[] {
								bamReader.makeQueryInterval(contig, Math.max(1, startAt - maxInsertSize + 1))};
						analyzer.timeStartProcessing = System.nanoTime();
						final Map<String, Pair<ExtendedSAMRecord, ReferenceSequence>> readsToProcess = new HashMap<>(5_000);

						subAnalyzer.truncateProcessingAt = truncateAtPosition;
						subAnalyzer.startProcessingAt = startAt;

						final List<Iterator<SAMRecord>> intersectionIterators = new ArrayList<>();

						for (File f: analyzer.intersectAlignmentFiles) {
							SAMFileReader reader = new SAMFileReader(f);
							intersectionIterators.add(new IteratorPrefetcher<>(reader.queryOverlapping(bamContig), 100, reader, e -> {}));
						}
						final int nIntersect = intersectionIterators.size();
						final Map<Integer, Integer> intersectionWaitUntil = new HashMap<>();
						for (int z = 0; z < nIntersect; z++) {
							intersectionWaitUntil.put(z, -1);
						}

						int previous5p = -1;
						SimpleCounter<Pair<String, Integer>> intersectReads = new SimpleCounter<>();
						SimpleCounter<Pair<String, Integer>> readAheadStash = new SimpleCounter<>();

						try {
							it0 = bamReader.queryOverlapping(bamContig);
						} catch (Exception e) {
							throw new RuntimeException("Problem with sample " + analyzer.name, e);
						}

						boolean firstRun = true;
						try (IteratorPrefetcher<SAMRecord> iterator = new IteratorPrefetcher<>(it0, 100, it0,
								e -> {e.eagerDecode(); e.getUnclippedEnd(); e.getUnclippedStart();})) {
						while (iterator.hasNext() && !phaser.isTerminated() && !terminateAnalysis) {
															
							final SAMRecord samRecord = iterator.next();
															
							if (alignmentWriter != null) {
								samRecord.setAttribute("DS", null); //If not output read will not be written
								//if reading from an already-annotated BAM file
							}
							
							final SequenceLocation location = new SequenceLocation(samRecord);
							
							final int current5p = samRecord.getAlignmentStart();
							if (current5p < previous5p) {
								throw new IllegalArgumentException("Unsorted input");
							}
							if (nIntersect > 0 && current5p > previous5p) {
								final Iterator<Entry<Pair<String, Integer>, SettableInteger>> esit = intersectReads.entrySet().iterator();
								while (esit.hasNext()) {
									if (esit.next().getKey().snd < current5p - 6) {
										analyzer.stats.nRecordsNotInIntersection1.accept(location);
										esit.remove();
									}
								}
								final Set<Entry<Pair<String, Integer>, SettableInteger>> readAheadStashEntries =
										new HashSet<>(readAheadStash.entrySet());
								for (Entry<Pair<String, Integer>, SettableInteger> e: readAheadStashEntries) {
									if (e.getKey().getSnd() < current5p - 6) {
										analyzer.stats.nRecordsNotInIntersection1.accept(location);
										readAheadStash.removeAll(e.getKey());
									} else if (e.getKey().getSnd() <= current5p + 6) {
										readAheadStash.removeAll(e.getKey());
										intersectReads.put(e.getKey(), e.getValue().get());
									}
								}

								for (int i = 0; i < nIntersect; i++) {
									if (current5p + 6 < intersectionWaitUntil.get(i)) {
										continue;
									}
									Iterator<SAMRecord> it = intersectionIterators.get(i);
									while (it.hasNext()) {
										SAMRecord ir = it.next();
										if (ir.getMappingQuality() < argValues.minMappingQIntersect.get(i)) {
											analyzer.stats.nTooLowMapQIntersect.accept(location);
											continue;
										}
										int ir5p = ir.getAlignmentStart();
										final Pair<String, Integer> pair = new Pair<>(getRecordNameWithPairSuffix(ir), ir5p);
										if (ir5p < current5p - 6) {
											analyzer.stats.nRecordsNotInIntersection1.accept(location);
										} else if (ir5p <= current5p + 6) {
											intersectReads.put(pair);
										} else {
											readAheadStash.put(pair);
											intersectionWaitUntil.put(i, ir5p);
											break;
										} 
									}
								}
							}
							previous5p = current5p;

							if (nIntersect > 0 &&
									intersectReads.get(new Pair<>(getRecordNameWithPairSuffix(samRecord), 
											current5p)) < nIntersect) {
								analyzer.stats.nRecordsNotInIntersection2.accept(location);
								continue;
							}

							if (analyzer.dropReadProbability > 0) {
								String readName = ExtendedSAMRecord.getReadFullName(samRecord);
								boolean mateAlreadyDropped = droppedReads.contains(readName);
								if (mateAlreadyDropped) {
									droppedReads.remove(readName);
									continue;
								}
								if (keptReads.contains(readName)) {
									keptReads.remove(readName);
								} else if (Math.random() < analyzer.dropReadProbability) {
									//If mate is in a different contig, do not store a reference
									//to the read name in droppedReads since this thread will never
									//encounter the mate
									if (Integer.compare(samRecord.getMateReferenceIndex(), samRecord.getReferenceIndex()) == 0)
										droppedReads.add(readName);
									continue;
								} else {//Keep the pair
									//If mate is in a different contig, do not store a reference
									//to the read name in keptReads since this thread will never
									//encounter the mate
									if (Integer.compare(samRecord.getMateReferenceIndex(), samRecord.getReferenceIndex()) == 0)
										keptReads.add(readName);
								}
							}

							analyzer.stats.nRecordsProcessed.increment(location);

							if (contigs.size() < 100 && !argValues.rnaSeq) {
								//Summing below is a bottleneck when there are a large
								//number of contigs (tens of thousands)
								if (analyzer.stats.nRecordsProcessed.sum() > argValues.nRecordsToProcess) {
									statusLogger.info("Analysis of contig " + contig + " stopping "
												+ "because it processed over " + argValues.nRecordsToProcess + " records");
									break;
								}
							}

							final String refName = samRecord.getReferenceName();
							if ("*".equals(refName)) {
								analyzer.stats.nRecordsUnmapped.increment(location);
								continue;
							}

							if (IGNORE_SECONDARY_MAPPINGS && samRecord.getNotPrimaryAlignmentFlag()) {
								analyzer.stats.nRecordsIgnoredBecauseSecondary.increment(location);
								continue;
							}

							int mappingQuality = samRecord.getMappingQuality();
							analyzer.stats.mappingQualityAllRecords.insert(mappingQuality);
							if (mappingQuality < analyzer.minMappingQualityQ1) {
								analyzer.stats.nRecordsBelowMappingQualityThreshold.increment(location);
								continue;
							}

							if (lastReferenceName != null && !refName.equals(lastReferenceName)) {
								furthestPositionReadInContig = 0;
							}
							lastReferenceName = refName;

							lastProcessable.set(samRecord.getAlignmentStart() - 2);
							
							final boolean finishUp;
							if (lastProcessable.get() >= truncateAtPosition + maxInsertSize) {
								statusLogger.debug("Analysis of contig " + contig + " stopping "
										+ "because it went past " + truncateAtPosition);
								finishUp = true;
							} else {
								finishUp = false;
							}

							ReferenceSequence ref = refMap.get(refName);
							if (ref == null) {
								try {
									ref = refFile.getSequence(refName);
									if (ref.getBases()[0] == 0) {
										throw new RuntimeException("Found null byte in " + refName +
												"; contig might not exist in reference file");
									}
								} catch (Exception e) {
									throw new RuntimeException("Problem reading reference file " + argValues.referenceGenome, e);
								}
								refMap.put(refName, ref);
							}

							//Put samRecord into the cache
							ExtendedSAMRecord extended = subAnalyzer.getExtended(samRecord, location);
							//Put samRecord into map of reads to possibly be processed in next batch
							if (readsToProcess.put(extended.getFullName(), new Pair<>(extended, ref)) != null) {
								throw new RuntimeException("Read " + extended.getFullName() + " read twice from " +
										analyzer.inputBam.getAbsolutePath());
							}
							furthestPositionReadInContig = Math.max(furthestPositionReadInContig,
									samRecord.getAlignmentEnd() - 1);

							//Use phaser here and go by specific positions rather than
							//actually processed records
							if (finishUp || lastProcessable.get() >= pauseAt.get() + maxInsertSize) {
								if (ENABLE_TRACE && shouldLog(TRACE)) {
									logger.trace("Member of phaser " + phaser + " reached " + lastProcessable +
											"; pauseAtPosition is " + pauseAt.get());
								}
								
								/**
								 * Meat of the processing is done here by calling method processRead.
								 * Only process the reads that will contribute to mutation candidates to be analyzed in the
								 * next round.
								 */									
								Iterator<Pair<ExtendedSAMRecord, ReferenceSequence>> it = 
										readsToProcess.values().iterator();
								final int localPauseAt = pauseAt.get();
								while (it.hasNext()) {
									Pair<ExtendedSAMRecord, ReferenceSequence> rec = it.next();
									final ExtendedSAMRecord read = rec.fst;
									if (firstRun && read.getAlignmentStartNoBarcode() + maxInsertSize < startAt) {
										//The purpose of this was to make runs reproducible irrespective of
										//the way contigs are broken up for parallelization; probably largely redundant now
										it.remove();
										continue;
									}
									if (read.getAlignmentStart() - 1 <= localPauseAt ||
											read.getMateAlignmentStart() - 1 <= localPauseAt) {
										subAnalyzer.processRead(read.record, rec.snd);
										it.remove();
									}
								}
								firstRun = false;
								phaser.arriveAndAwaitAdvance();	
							}
							if (finishUp) {
								break;
							}
						}//End samRecord loop
						}//End iterator try block
						
						logger.trace("Member of phaser " + phaser + " reached final " + lastProcessable +
									"; pauseAtPosition is " + pauseAt.get());

						if (lastProcessedPosition.get() < truncateAtPosition) {
							readsToProcess.forEach((k, v) -> {
								subAnalyzer.processRead(v.fst.record, v.snd);
							});
						}
						
						if (truncateAtPosition == Integer.MAX_VALUE) {
							lastProcessable.set(furthestPositionReadInContig);
						} else {
							lastProcessable.set(truncateAtPosition + 1);
						}

						while (!phaser.isTerminated() && !terminateAnalysis) {
							phaser.arriveAndAwaitAdvance();
						}
						logger.debug("Done processing contig " + contig + " of file " + analyzer.inputBam);
						} finally {
							if (it0 != null) {
								it0.close();
							}
							analyzer.readerPool.returnObj(bamReader);
						}
					} catch (Throwable t) {
						Util.printUserMustSeeMessage("Exception " + t.getMessage() + " " +
								(t.getCause() != null ? (t.getCause() + " ") : "") + 
								"on thread " + Thread.currentThread());
						if (argValues.terminateImmediatelyUponError) {
							t.printStackTrace();
							System.exit(1);
						}
						phaser.forceTermination();
						throw new RuntimeException("Exception while processing contig " + lastReferenceName + 
								" of file " + analyzer.inputBam.getAbsolutePath(), t);
					} finally {
						phaser.arriveAndDeregister();
					}
					//Ensure that there is no memory leak (references are kept to subAnalyzers,
					//at least through the status update handlers; XXX not clear if the maps
					//should already have been completely cleared as part of locus examination.
					analysisChunk.subAnalyzers.forEach(sa -> {
						sa.extSAMCache.clear();
						sa.candidateSequences.clear();
						sa.analyzedDuplexes.clear();
						});
					//TODO Clear subAnalyzers list, but *only after* all analyzers have completed
					//that chunk
					//analysisChunk.subAnalyzers.clear();
				};//End Runnable
				futures.add(executorService.submit(r));
			}//End loop over parallelization factor
		}//End loop over analyzers

		for (Future<?> f: futures) {
			try {
				f.get();
			} catch (ExecutionException e) {
				synchronized(exceptionList) {
					exceptionList.add(e);
				}
			}
		}
				
		if (exceptionList.size() > 0) {
			synchronized(exceptionList) {
				throw new RuntimeException(exceptionList.get(0));
			}
		}
		
		for (int p = 0 ; p < argValues.parallelizationFactor; p++) {
			for (Mutinack analyzer: analyzers) {
				analyzer.subAnalyzers.set(contigIndex * argValues.parallelizationFactor + p, null);
			}
		}
		
		return null;
	});//End Parfor loop over contigs
		
		parFor.run(true);

		infoSignalHandler.handle(null);
		if (!argValues.noStatusMessages) {
			Util.printUserMustSeeMessage("Analysis of samples " + analyzers.stream().map(a -> a.name
					+ " at " + ((int) a.processingThroughput()) + " records / s").
					collect(Collectors.joining(", ")) + " completed on host " + hostName +
					" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
		}
		
		if (argValues.readContigsFromFile) {//Probably reference transcriptome; TODO need to
			//devise a better way of figuring this out
			for (Mutinack analyzer: analyzers) {
				final ICounterSeqLoc counter = 
						analyzer.stats.nLociDuplex.getSeqLocCounters().get("All").snd;
				final @NonNull Map<Object, @NonNull Object> m = counter.getCounts();
				final Map<String, Double> allCoverage = new THashMap<>();
				
				for (Entry<String, Integer> e: contigSizes.entrySet()) {
					//TODO Need to design a better API to retrieve the counts
					ICounter<?> c = Util.nullableify(((ICounter<?>) m.get(indexContigNameReverseMap.get(e.getKey()))));
					final double d;
					if (c == null) {
						d = 0;	
					} else {
						Object o = Util.nullableify(c.getCounts().get(0));
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
				
				try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("coverage_" + analyzer.name + ".txt"), "utf-8"))) {
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

		if (alignmentWriter != null) {
			alignmentWriter.close();
		}
		
		for (Mutinack analyzer: analyzers) {
			analyzer.subAnalyzers.stream().filter(sa -> sa != null).forEach(SubAnalyzer::checkAllDone);
			analyzer.closeOutputs();
		}
		
		PoolController.shutdown();
	}
	
	private void closeOutputs() throws IOException {
		for (Closeable c: itemsToClose) {
			c.close();
		}
		
		if (stats.locusByLocusCoverage != null) {
			Builder builder = stats.locusByLocusCoverageProtobuilder;
			for (Entry<String, int[]> e: stats.locusByLocusCoverage.entrySet()) {
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
			Files.write(path, builder.build().toByteArray());
		}
	}

	private static void handleOutputException(String fileName, IOException e, Parameters argValues) {
		String baseMessage = "Could not open file " + fileName;
		if (argValues.terminateUponOutputFileError) {
			throw new RuntimeException(baseMessage, e);
		}
		Util.printUserMustSeeMessage(baseMessage + "; keeping going anyway");
	}

	static final boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}
	
	private double processingThroughput() {
		return (stats.nRecordsProcessed.sum()) / 
				((System.nanoTime() - timeStartProcessing) / 1_000_000_000d);
	}

	private void printStatus(PrintStream stream, boolean colorize) {
		stream.println();
		stream.println(greenB(colorize) + "Statistics for " + inputBam.getAbsolutePath() + reset(colorize));

		stats.print(stream, colorize);

		NumberFormat formatter = new DecimalFormat("0.###E0");

		stream.println(blueF(colorize) + "Average Phred quality: " + reset(colorize) +
				formatter.format(stats.phredSumProcessedbases.sum() / stats.nProcessedBases.sum()));
		
		if (stats.outputLevel.compareTo(OutputLevel.VERBOSE) >= 0) {
			stream.println(blueF(colorize) + "Top 100 barcode hits in cache: " + reset(colorize) +
					internedVariableBarcodes.values().stream().sorted((ba, bb) -> - Long.compare(ba.nHits.sum(), bb.nHits.sum())).
					limit(100).map(ByteArray::toString).collect(Collectors.toList()));
		}

		stream.println(blueF(colorize) + "Processing throughput: " + reset(colorize) + 
				((int) processingThroughput()) + " records / s");

		for (String counter: stats.nLociCandidatesForUniqueMutation.getCounterNames()) {
			stream.println(greenB(colorize) + "Mutation rate for " + counter + ": " + reset(colorize) + formatter.
					format(stats.nLociCandidatesForUniqueMutation.getSum(counter) / stats.nLociDuplexQualityQ2OthersQ1Q2.getSum(counter)));			
		}
	}	
	
	@Override
	public String toString() {
		return name + " (" + inputBam.getAbsolutePath() + ")";
	}

	static {
		SignalHandler sigINTHandler = new SignalHandler() {
			@Override
			public void handle(Signal signal) {
				Util.printUserMustSeeMessage("Writing output files and terminating");
				terminateAnalysis = true;
			}
		};
		Signal.handle(new Signal("URG"), sigINTHandler);
	}
}
