/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2016 Olivier Cinquin
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
import static uk.org.cinquin.mutinack.misc_util.Util.printUserMustSeeMessage;
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
import java.lang.management.ManagementFactory;
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
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
//import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
//import com.google.monitoring.runtime.instrumentation.Sampler;
import com.jwetherell.algorithms.data_structures.IntervalTree.IntervalData;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import contrib.net.sf.samtools.SAMFileHeader;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMFileWriterFactory;
import contrib.net.sf.samtools.SAMProgramRecord;
import contrib.net.sf.samtools.SAMSequenceRecord;
import contrib.net.sf.samtools.util.RuntimeIOException;
import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObjectBase;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.database.DatabaseOutput0;
import uk.org.cinquin.mutinack.distributed.Server;
import uk.org.cinquin.mutinack.distributed.Submitter;
import uk.org.cinquin.mutinack.distributed.Worker;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.BAMUtil;
import uk.org.cinquin.mutinack.misc_util.CloseableCloser;
import uk.org.cinquin.mutinack.misc_util.CloseableListWrapper;
import uk.org.cinquin.mutinack.misc_util.CloseableWrapper;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.GetReadStats;
import uk.org.cinquin.mutinack.misc_util.GitCommitInfo;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.MultipleExceptionGatherer;
import uk.org.cinquin.mutinack.misc_util.NamedPoolThreadFactory;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SettableLong;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Signals.SignalProcessor;
import uk.org.cinquin.mutinack.misc_util.StaticStuffToAvoidMutating;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.collections.TSVMapReader;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.ParedDownMutinack;
import uk.org.cinquin.mutinack.output.RunResult;
import uk.org.cinquin.mutinack.statistics.Actualizable;
import uk.org.cinquin.mutinack.statistics.CounterWithBedFeatureBreakdown;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.ICounter;
import uk.org.cinquin.mutinack.statistics.ICounterSeqLoc;
import uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel;
import uk.org.cinquin.parfor.ParFor;

public class Mutinack implements Actualizable, Closeable {

	static ConcurrentHashMap<String, SettableLong> objectAllocations =
		new ConcurrentHashMap<>(1000);

	static {
		if (! new File("logback.xml").isFile()) {
			ch.qos.logback.classic.Logger root =
					(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			root.setLevel(ch.qos.logback.classic.Level.INFO);
			ConsoleAppender<ILoggingEvent> stdErrAppender = new ConsoleAppender<>();
			stdErrAppender.setTarget("System.err");
			root.addAppender(stdErrAppender);
		}


		/*AllocationRecorder.addSampler(new Sampler() {
			@Override
			public void sampleAllocation(int count, String desc, Object newObj, long size) {
				objectAllocations.computeIfAbsent(desc, key -> new SettableLong(0)).
					incrementAndGet();
			}
		});*/
	}

	public static final Logger logger = LoggerFactory.getLogger("Mutinack");
	private static final Logger statusLogger = LoggerFactory.getLogger("MutinackStatus");

	private static final List<String> outputHeader = Collections.unmodifiableList(Arrays.asList(
			"Notes", "Location", "Mutation type", "Mutation detail", "Sample", "nQ2Duplexes",
			"nQ1Q2Duplexes", "nDuplexes", "nConcurringReads", "fractionConcurringQ2Duplexes",
			"fractionConcurringQ1Q2Duplexes", "fractionConcurringDuplexes", "fractionConcurringReads",
			"averageMappingQuality", "nDuplexesSisterArm", "insertSize", "insertSizeAtPos10thP",
			"insertSizeAtPos90thP", "minDistanceLigSite", "maxDistanceLigSite",
			"meanDistanceLigSite", "probCollision", "positionInRead", "readEffectiveLength",
			"nameOfOneRead", "readAlignmentStart", "mateAlignmentStart", "readAlignmentEnd",
			"mateAlignmentEnd", "refPositionOfLigSite", "issuesList", "medianPhredAtPosition",
			"minInsertSize", "maxInsertSize", "secondHighestAlleleFrequencyX10",
			"highestAlleleFrequencyX10", "supplementalMessage"
	));
	private volatile static ExecutorService contigThreadPool;

	private final Collection<Closeable> itemsToClose = new ArrayList<>();
	private final Date startDate;

	final MutinackGroup groupSettings;
	final @NonNull Parameters param;
	public final @NonNull String name;
	long timeStartProcessing;
	private final @NonNull List<SubAnalyzer> subAnalyzers = new ArrayList<>();
	final @NonNull File inputBam;
	final PoolSettings<SAMFileReader> poolSettings = new PoolSettings<> (
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
			});

	final ObjectPool<SAMFileReader> readerPool =
		poolSettings.min(0).max(300).pool(true); 	//Need min(0) so inputBam is set before first
										//reader is created
	final double @Nullable[] insertSizeProbSmooth;
	private final double @Nullable[] insertSizeProbRaw;
	final @NonNull Collection<File> intersectAlignmentFiles;
	final @NonNull public Map<String, GenomeFeatureTester> filtersForCandidateReporting = new HashMap<>();
	@Nullable GenomeFeatureTester codingStrandTester;
	final byte @NonNull[] constantBarcode;
	final int maxNDuplexes;
	public @NonNull List<@NonNull AnalysisStats> stats = new ArrayList<>();
	private String finalOutputBaseName;
	public boolean notifiedUnpairedReads = false;

	private Mutinack(
			MutinackGroup groupSettings,
			Parameters param,
			@NonNull String name,
			@NonNull File inputBam,
			@NonNull PrintStream out,
			@Nullable OutputStreamWriter mutationAnnotationWriter,
			@Nullable Histogram approximateReadInsertSize,
			byte @NonNull [] constantBarcode,
			@NonNull List<File> intersectAlignmentFiles,
			@NonNull OutputLevel outputLevel,
			int maxNDuplexes) {

		this.groupSettings = groupSettings;
		param.validate();
		this.param = param;
		this.name = name;
		this.inputBam = inputBam;
		if (approximateReadInsertSize != null) {
			insertSizeProbRaw = approximateReadInsertSize.toProbabilityArray(false);;
			insertSizeProbSmooth = approximateReadInsertSize.toProbabilityArray(true);;
		} else {
			insertSizeProbRaw = null;
			insertSizeProbSmooth = null;
		}
		//Make sure reference tests can be substituted for equality tests;
		//the analyzer's constant barcode needs to come from the interning map
		this.constantBarcode = Util.getInternedCB(constantBarcode);

		this.intersectAlignmentFiles = intersectAlignmentFiles;

		this.maxNDuplexes = maxNDuplexes;

		List<String> statsNames = Arrays.asList("main_stats", "ins_stats");

		if (param.exploreParameters.isEmpty()) {
			for (String statsName: statsNames) {
				stats.add(createStats(statsName, param, out,
					mutationAnnotationWriter, outputLevel));
			}
		} else {
			if (!param.includeInsertionsInParamExploration) {
				statsNames = Arrays.asList("main_stats");
			}
			final BiConsumer<String, Parameters> consumer = (statsName, p) ->
				stats.add(createStats(statsName, p, out,
					mutationAnnotationWriter, outputLevel));
			recursiveParameterFill(consumer, 0, param.exploreParameters, statsNames, param,
				param.cartesianProductOfExploredParameters);
		}

		sortStatsListByDuplexLoadParams(stats);

		final String inputHash = (inputBam.length() > param.computeHashForBAMSmallerThanInGB * Math.pow(1024,3)) ?
				"too big"
			:
				BAMUtil.getHash(inputBam);

		stats.forEach(stat -> {
			stat.approximateReadInsertSize = insertSizeProbSmooth;
			stat.approximateReadInsertSizeRaw = insertSizeProbRaw;
			stat.inputBAMHashes.put(inputBam.getPath(), inputHash);
		});
		this.startDate = new Date();
	}

	private static void sortStatsListByDuplexLoadParams(List<AnalysisStats> list) {
		Map<Map<String, Object>, List<AnalysisStats>> groupedByDGParams = list.stream().
			collect(Collectors.groupingBy(stats -> stats.analysisParameters.
				distinctParameters.entrySet().stream().
					filter(e -> Parameters.isUsedAtDuplexGrouping(e.getKey())).
					collect(Collectors.toMap(Entry::getKey, Entry::getValue))));
		list.clear();
		Handle<Boolean> canSkipReload = new Handle<>(true);
		groupedByDGParams.forEach((param, l) -> {
			l.sort(Comparator.comparing(AnalysisStats::getName));
			l.forEach(stats -> {
				stats.canSkipDuplexLoading = canSkipReload.get();
				list.add(stats);
				canSkipReload.set(true);
			});
			canSkipReload.set(false);
		});
	}

	private static void recursiveParameterFill(
			BiConsumer<String, Parameters> consumer,
			int index, List<String> exploreParameters,
			List<String> statsNames,
			Parameters param,
			boolean cartesian) {
		final String s = exploreParameters.get(index);
		final String errorMessagePrefix = "Could not parse exploreParameter argument " + s;
		final String[] split = s.split(":");
		final String paramToExplore = split[0];
		final Number min, max;
		final int nValues;
		final Object val = param.getFieldValue(paramToExplore);

		final List<Object> values = new ArrayList<>();

		if (val instanceof Boolean) {
			values.add(true);
			values.add(false);
		} else {
			try {
				min = Util.parseNumber(split[1]);
				max = Util.parseNumber(split[2]);
				if (split.length == 4) {
					nValues = Integer.parseInt(split[3]);
				} else if (split.length == 3) {
					nValues = max.intValue() - min.intValue() + 1;
				} else
					throw new AssertionFailedException();
			} catch (RuntimeException e) {
				throw new RuntimeException(errorMessagePrefix, e);
			}
			if (val instanceof Integer) {
				checkInteger(min, errorMessagePrefix);
				checkInteger(max, errorMessagePrefix);
			}
			values.add(min.doubleValue());
			for (double step = 1; step < nValues; step++) {
				final double value = min.doubleValue() + (max.doubleValue() - min.doubleValue()) *
					step / (nValues - 1);
				if (val instanceof Integer || val instanceof Long) {
					Assert.isFalse(values.contains(Math.round(value)), "Inserting " + value + " twice");
				}
				values.add(value);
			}
		}

		for (Object value: values) {
			Parameters clone = param.clone();
			clone.exploreParameters = Collections.emptyList();
			clone.distinctParameters = new HashMap<>();
			clone.distinctParameters.putAll(param.distinctParameters);

			if (clone.isParameterInstanceOf(paramToExplore, Integer.class) ||
				clone.isParameterInstanceOf(paramToExplore, Long.class))
				value = (int) Math.round((Double) value);
			if (clone.distinctParameters.put(paramToExplore, value) != null) {
				throw new AssertionFailedException();
			}
			clone.setFieldValue(paramToExplore, value);
			if (index == exploreParameters.size() - 1 || !cartesian) {
				for (String stat: statsNames) {
					String extra = clone.distinctParameters.entrySet().stream().
						map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
					consumer.accept(stat + ": " + extra, clone);
				}
			} else if (cartesian) {
				recursiveParameterFill(consumer, index + 1, exploreParameters, statsNames, clone, cartesian);
			}
		}
		if (!cartesian && index < exploreParameters.size() - 1) {
			recursiveParameterFill(consumer, index + 1, exploreParameters, statsNames, param, cartesian);
		}
	}

	private static void checkInteger(Number n, String errorMessage) {
		if (!(n instanceof Integer) && !(n instanceof Long)) {
			throw new IllegalArgumentException(errorMessage + ": " + n + " not an integer but a " +
				n.getClass().getCanonicalName());
		}
	}

	private @NonNull AnalysisStats createStats(
			String statsName,
			Parameters param1,
			PrintStream out,
			OutputStreamWriter mutationAnnotationWriter,
			OutputLevel outputLevel) {
		AnalysisStats stat = new AnalysisStats(statsName, param1, statsName.contains("ins_stats"),
			Objects.requireNonNull(groupSettings), param1.reportCoverageAtAllPositions);
		stat.detectionOutputStream = out;
		stat.annotationOutputStream = mutationAnnotationWriter;
		stat.setOutputLevel(outputLevel);
		for (String s: param1.traceFields) {
			try {
				String[] split = s.split(":");
				String analyzerName = split[0];
				if (analyzerName.equals(name)) {
					stat.traceField(split[1], s + ": ");
				}
			} catch (Exception e) {
				throw new RuntimeException("Problem setting up traceField " + s, e);
			}
		}
		return stat;
	}

	public static void main(String args[]) throws InterruptedException, ExecutionException, FileNotFoundException {
		try {
			try {
				realMain0(args);
			} catch (RejectedExecutionException rejected) {
				throw new RuntimeException("Not enough threads; please adjust maxThreadsPerPool",
					rejected);
			}
		} catch (ParameterException e) {
			if (System.console() == null) {
				System.err.println(e.getMessage());
			}
			System.out.println(e.getMessage());
			//Make sure to return a non-0 value so Make sees the run failed
			System.exit(1);
		} catch (QuietException e) {
			throw e;
		} catch (Throwable t) {
			if (System.console() == null) {
				t.printStackTrace(System.err);
			}
			t.printStackTrace(System.out);
			//Make sure to return a non-0 value so Make sees the run failed
			System.exit(1);
		}
	}

	private static void realMain0(String args[]) throws InterruptedException, IOException {

		final Parameters param = new Parameters();
		JCommander commander = new JCommander();
		commander.setAcceptUnknownOptions(false);
		commander.setAllowAbbreviatedOptions(false);
		commander.addObject(param);
		commander.parse(args);

		if (param.timeoutSeconds != 0) {
			Server.PING_INTERVAL_SECONDS = 1 + param.timeoutSeconds / 3;
		}

		if (param.parallelizationFactor != 1 &&
				!param.contigByContigParallelization.isEmpty()) {
			throw new IllegalArgumentException("Cannot use parallelizationFactor and "
				+ "contigByContigParallelization at the same time");
		}

		StaticStuffToAvoidMutating.instantiateThreadPools(param.maxThreadsPerPool);

		if (param.help) {
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
		} else if (param.version) {
			System.out.println(GitCommitInfo.getGitCommit());
		} else if (param.startServer != null) {
			Server.createRegistry(param.startServer);
			@SuppressWarnings("unused")
			Server unusedVariable =
					new Server(0, param.startServer, param.recordRunsTo, param.keysFile, param.writePIDPath,
						param.noStatusMessages);
		} else if (param.submitToServer != null) {
			if (param.suppressStderrOutput) {
				try {
					Submitter.submitToServer(param);
				} catch (Exception e) {
					System.err.println("Suppressing stderr exception detail as requested");
					e.printStackTrace(System.out);
					throw new QuietException(e.getMessage());
				}
			} else {
				Submitter.submitToServer(param);
			}
		} else if (param.startWorker != null) {
			Worker.runWorker(param);
		} else {
			realMain1(param, System.out, System.err);
		}
	}

	private static class QuietException extends RuntimeException {

		private static final long serialVersionUID = -3006672561001099942L;

		public QuietException(String message) {
			super(message, null, true, false);
		}

	}

	private static boolean versionChecked = false;

	@SuppressWarnings("resource")
	public static RunResult realMain1(Parameters param, PrintStream out, PrintStream err)
			throws InterruptedException, IOException {

		Thread.interrupted();//XXX The actual problem needs to be fixed upstream

		DebugLogControl.COSTLY_ASSERTIONS = param.enableCostlyAssertions;

		if (!versionChecked && !param.noStatusMessages && !param.skipVersionCheck) {
			versionChecked = true;
			StaticStuffToAvoidMutating.getExecutorService().submit(Util::versionCheck);
		}

		if (!versionChecked && !param.noStatusMessages) {
			Util.printUserMustSeeMessage(GitCommitInfo.getGitCommit());
		}

		if (!param.saveFilteredReadsTo.isEmpty()) {
			throw new RuntimeException("Not implemented");
		}

		if (!param.noStatusMessages) {
			Util.printUserMustSeeMessage("Analysis started on host " +
				StaticStuffToAvoidMutating.hostName +
				" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
		}

		out.println(param.toString());
		out.println("Non-trivial assertions " +
				(DebugLogControl.NONTRIVIAL_ASSERTIONS ?
				"on" : "off"));

		final List<@Nullable Mutinack> analyzers = new ArrayList<>();

		for (int i = 0; i < param.inputReads.size(); i++) {
			analyzers.add(null);
		}

		for (String inputBamPath: param.inputReads) {
			final File inputBam = new File(inputBamPath);
			try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
				out.println(inputBamPath + ":\n" + tempReader.getFileHeader().getTextHeader());
			}
		}

		final MutinackGroup groupSettings = new MutinackGroup();
		groupSettings.registerInterruptSignalProcessor();
		param.group = groupSettings;

		try (CloseableCloser closeableCloser = new CloseableCloser()) {
			closeableCloser.add(groupSettings);
			closeableCloser.add(new CloseableListWrapper<>(analyzers));
			realMain2(param, groupSettings, analyzers, out, err, closeableCloser);
		}

		return getRunResult(param, analyzers);
	}

	@SuppressWarnings("resource")
	private static void realMain2(
			final Parameters param,
			final MutinackGroup groupSettings,
			final List<Mutinack> analyzers,
			final PrintStream out,
			final PrintStream err,
			final CloseableCloser closeableCloser) throws IOException, InterruptedException {

		final SAMFileWriter alignmentWriter;
		if (param.outputAlignmentFile == null) {
			alignmentWriter = null;
		} else {
			SAMFileWriterFactory factory = new SAMFileWriterFactory();
			SAMFileHeader header = new SAMFileHeader();
			factory.setCreateIndex(true);
			header.setSortOrder(param.sortOutputAlignmentFile ?
				contrib.net.sf.samtools.SAMFileHeader.SortOrder.coordinate
				: contrib.net.sf.samtools.SAMFileHeader.SortOrder.unsorted);
			if (param.sortOutputAlignmentFile) {
				factory.setMaxRecordsInRam(10_000);
			}
			final File inputBam = new File(param.inputReads.get(0));

			try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
				final SAMFileHeader inputHeader = tempReader.getFileHeader();
				header.setSequenceDictionary(inputHeader.getSequenceDictionary());
				List<SAMProgramRecord> programs = new ArrayList<>(inputHeader.getProgramRecords());
				SAMProgramRecord mutinackRecord = new SAMProgramRecord("Mutinack");//TODO Is there
				//documentation somewhere of what programGroupId should be??
				mutinackRecord.setProgramName("Mutinack");
				mutinackRecord.setProgramVersion(GitCommitInfo.getGitCommit());
				mutinackRecord.setCommandLine(param.toString());
				programs.add(mutinackRecord);
				header.setProgramRecords(programs);
			}
			alignmentWriter = factory.
				makeBAMWriter(header, false, new File(param.outputAlignmentFile), 0);
		}
		closeableCloser.add(new CloseableWrapper<>(alignmentWriter, SAMFileWriter::close));

		final OutputStreamWriter mutationAnnotationWriter;
		if (param.annotateMutationsOutputFile != null) {
			mutationAnnotationWriter = new FileWriter(param.annotateMutationsOutputFile);
		} else {
			mutationAnnotationWriter = null;
			if (param.annotateMutationsInFile != null) {
				throw new IllegalArgumentException("Annotating of mutation in file " +
					param.annotateMutationsInFile + " was requested but no output was specified " +
					"with annotateMutationsOutputFile argument");
			}
		}
		closeableCloser.add(mutationAnnotationWriter);

		final @NonNull List<@NonNull String> contigNames;
		final @NonNull Map<@NonNull String, @NonNull Integer> contigSizes;
		final @NonNull List<@NonNull String> contigNamesToProcess;

		if (param.readContigsFromFile) {
			final @NonNull Map<@NonNull String, @NonNull Integer> contigSizes0 =
				new HashMap<>(
					StaticStuffToAvoidMutating.loadContigsFromFile(param.referenceGenome));

			try (SAMFileReader tempReader = new SAMFileReader(
				new File(param.inputReads.get(0)))) {
				final List<String> sequenceNames = tempReader.getFileHeader().
					getSequenceDictionary().getSequences().stream().
					map(SAMSequenceRecord::getSequenceName).collect(Collectors.toList());

				contigSizes0.entrySet().removeIf(e -> {
						if (!sequenceNames.contains(e.getKey())) {
							printUserMustSeeMessage("Reference sequence " + e.getKey() + " not present in sample header; ignoring");
							return true;
						}
						return false;
					}
				);
			}
			contigSizes = Collections.unmodifiableMap(contigSizes0);
			List<@NonNull String> contigNames0 = new ArrayList<>(contigSizes0.keySet());
			contigNames0.sort(null);
			contigNames = Collections.unmodifiableList(contigNames0);
			contigNamesToProcess = contigNames;
		} else if (!param.contigNamesToProcess.equals(new Parameters().contigNamesToProcess)) {
			throw new IllegalArgumentException("Contig names specified both in file and as " +
				"command line argument; pick only one method");
		} else {
			contigNames = param.contigNamesToProcess;
			contigSizes = Collections.emptyMap();
			contigNamesToProcess = param.contigNamesToProcess;
		}

		for (int i = 0; i < contigNames.size(); i++) {
			groupSettings.indexContigNameReverseMap.put(contigNames.get(i), i);
		}
		groupSettings.setContigNames(contigNames);
		groupSettings.setContigSizes(contigSizes);

		StaticStuffToAvoidMutating.loadContigs(param.referenceGenome,
			contigNames);

		groupSettings.forceOutputAtLocations.clear();

		Util.parseListLocations(param.forceOutputAtPositions,
			groupSettings.indexContigNameReverseMap).forEach(parsedLocation -> {
			if (groupSettings.forceOutputAtLocations.put(parsedLocation, false) != null) {
				Util.printUserMustSeeMessage(Util.truncateString("Warning: repeated specification of " + parsedLocation +
					" in list of forced output positions"));
			}
		});

		for (String forceOutputFilePath: param.forceOutputAtPositionsFile) {
			try(Stream<String> lines = Files.lines(Paths.get(forceOutputFilePath))) {
				lines.forEach(l -> {
					for (String loc: l.split(" ")) {
						try {
							if (loc.isEmpty()) {
								continue;
							}
							int columnPosition = loc.indexOf(":");
							final @NonNull String contig = loc.substring(0, columnPosition);
							final String pos = loc.substring(columnPosition + 1);
							double position = NumberFormat.getNumberInstance(java.util.Locale.US).parse(pos).doubleValue() - 1;
							final int contigIndex = contigNames.indexOf(contig);
							if (contigIndex < 0) {
								throw new IllegalArgumentException("Unknown contig " + contig + "; known contigs are " +
									contigNames);
							}
							final SequenceLocation parsedLocation = new SequenceLocation(contigIndex, contig,
								(int) Math.floor(position), position - Math.floor(position) > 0);
							if (groupSettings.forceOutputAtLocations.put(parsedLocation, false) != null) {
								Util.printUserMustSeeMessage(Util.truncateString("Warning: repeated specification of " + parsedLocation +
									" in list of forced output positions"));
							}
						} catch (Exception e) {
							throw new RuntimeException("Error parsing " + loc + " in file " + forceOutputFilePath, e);
						}
					}
				});
			}
		}

		if (param.randomOutputRate != 0) {
			if (!param.readContigsFromFile) {
				throw new IllegalArgumentException("Option randomOutputRate "
					+ "requires readContigsFromFiles");
			}
			Random random = new Random(param.randomSeed);
			int randomLocs = 0;
			for (Entry<@NonNull String, Integer> c: contigSizes.entrySet()) {
				for (int i = 0; i < param.randomOutputRate * c.getValue(); i++) {
					@SuppressWarnings("null")
					SequenceLocation l = new SequenceLocation(
						groupSettings.indexContigNameReverseMap.get(c.getKey()),
						c.getKey(), (int) (random.nextDouble() * (c.getValue() - 1)));
					if (groupSettings.forceOutputAtLocations.put(l, true) == null) {
						randomLocs++;
					}
				}
			}
			Util.printUserMustSeeMessage("Added " + randomLocs + " random output positions");
		}
		@SuppressWarnings("null")
		@NonNull String forceOutputString = groupSettings.forceOutputAtLocations.toString();
		out.println("Forcing output at locations " +
			Util.truncateString(forceOutputString));

		out.println(String.join("\t", outputHeader));

		//Used to ensure that different analyzers do not use same output files
		final Set<String> sampleNames = new HashSet<>();

		if (param.minMappingQIntersect.size() != param.intersectAlignment.size()) {
			throw new IllegalArgumentException(
				"Lists given in minMappingQIntersect and intersectAlignment must have same length");
		}

		Pair<List<String>, List<Integer>> parsedPositions =
			Util.parseListPositions(param.startAtPositions, true, "startAtPosition");
		final List<String> startAtContigs = parsedPositions.fst;
		final List<Integer> startAtPositions = parsedPositions.snd;

		Pair<List<String>, List<Integer>> parsedPositions2 =
			Util.parseListPositions(param.stopAtPositions, true, "stopAtPosition");
		final List<String> truncateAtContigs = parsedPositions2.fst;
		final List<Integer> truncateAtPositions = parsedPositions2.snd;

		Util.checkPositionsOrdering(parsedPositions, parsedPositions2);

		final List<GenomeFeatureTester> excludeBEDs = new ArrayList<>();
		for (String bed: param.excludeRegionsInBED) {
			try {
				final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
					groupSettings.getContigNames(), bed, false);
				excludeBEDs.add(reader);
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + bed, e);
			}
		}

		final List<BedReader> repetitiveBEDs = new ArrayList<>();
		for (String bed: param.repetiveRegionBED) {
			try {
				final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
					groupSettings.getContigNames(), bed, false);
				repetitiveBEDs.add(reader);
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + bed, e);
			}
		}

		final @Nullable OutputStreamWriter mutationWriterCopy = mutationAnnotationWriter;

		IntStream.range(0, param.inputReads.size()).parallel().forEach(i -> {
			final String inputReads = param.inputReads.get(i);
			final File inputBam = new File(inputReads);

			final @NonNull String name;

			if (param.sampleNames.size() >= i + 1) {
				name = param.sampleNames.get(i);
			} else {
				name = inputBam.getName();
			}

			synchronized (sampleNames) {
				if (!sampleNames.add(name)) {
					throw new RuntimeException("Two or more analyzers trying to use the same name " +
						name + "; please give samples unique names");
				}
			}

			final List<File> intersectFiles = new ArrayList<>();
			final int nIntersectFiles = param.intersectAlignment.size();
			final int nIntersectFilesPerAnalyzer = nIntersectFiles / param.inputReads.size();
			for (int k = i * nIntersectFilesPerAnalyzer ; k < (i+1) * nIntersectFilesPerAnalyzer ; k++) {
				File f = new File(param.intersectAlignment.get(k));
				intersectFiles.add(f);
				out.println("Intersecting " + inputBam.getAbsolutePath() + " with " +
					f.getAbsolutePath());
			}

			groupSettings.PROCESSING_CHUNK = param.processingChunk;
			groupSettings.INTERVAL_SLOP = param.alignmentPositionMismatchAllowed;
			groupSettings.BIN_SIZE = param.contigStatsBinLength;
			groupSettings.terminateImmediatelyUponError =
				param.terminateImmediatelyUponError;

			groupSettings.setBarcodePositions(0, param.variableBarcodeLength - 1,
				3, 5);

			final int maxNDuplexes = param.maxNDuplexes.isEmpty() ? Integer.MAX_VALUE :
				param.maxNDuplexes.get(i);

			final OutputLevel[] d = OutputLevel.values();
			@SuppressWarnings("null")
			final @NonNull OutputLevel outputLevel = d[param.verbosity];

			final Mutinack analyzer = new Mutinack(
				groupSettings,
				param,
				name,
				inputBam,
				out,
				mutationWriterCopy,
				param.variableBarcodeLength == 0 ?
					GetReadStats.getApproximateReadInsertSize(inputBam, param.maxInsertSize, param.minMappingQualityQ2)
					:
					null,
				nonNullify(param.constantBarcode.getBytes()),
				intersectFiles,
				outputLevel,
				maxNDuplexes);
			analyzers.set(i, analyzer);

			analyzer.finalOutputBaseName = (param.auxOutputFileBaseName != null ?
				(param.auxOutputFileBaseName) : "") +
				name;

			if (param.outputTopBottomDisagreementBED) {
				final String tbdNameMain = analyzer.finalOutputBaseName + "_top_bottom_disag_";
				final String tbdNameNoWt = analyzer.finalOutputBaseName + "_top_bottom_disag_no_wt_";
				analyzer.stats.forEach(s -> {
					final String pathMain = tbdNameMain + s.getName() + ".bed";
					try {
						s.topBottomDisagreementWriter = new FileWriter(pathMain);
						analyzer.itemsToClose.add(s.topBottomDisagreementWriter);
					} catch (IOException e) {
						handleOutputException(pathMain, e, param);
					}
					final String pathNoWt = tbdNameNoWt + s.getName() + ".bed";
					try {
						s.noWtDisagreementWriter = new FileWriter(pathNoWt);
						analyzer.itemsToClose.add(s.noWtDisagreementWriter);
					} catch (IOException e) {
						handleOutputException(pathNoWt, e, param);
					}
				});
			}

			final String mutName = analyzer.finalOutputBaseName + "_mutations_";
			analyzer.stats.forEach(s -> {
				final String path = mutName + s.getName() + ".bed";
				try {
					s.mutationBEDWriter = new FileWriter(path);
					analyzer.itemsToClose.add(s.mutationBEDWriter);
				} catch (IOException e) {
					handleOutputException(path, e, param);
				}
			});

			if (param.outputCoverageProto) {
				analyzer.stats.forEach(s -> {
					s.positionByPositionCoverage = new HashMap<>();
					if (contigSizes.isEmpty()) {
						throw new IllegalArgumentException("Need contig sizes for outputCoverageProto; " +
							"set readContigsFromFile option");
					}
					contigSizes.forEach((k,v) -> s.positionByPositionCoverage.put(k, new int [v]));
					Builder builder = PosByPosNumbersPB.GenomeNumbers.newBuilder();
					builder.setGeneratingProgramVersion(GitCommitInfo.getGitCommit());
					builder.setGeneratingProgramArgs(param.toString());
					builder.setSampleName(analyzer.finalOutputBaseName + "_" +
						s.getName() + "_" + name + "_pos_by_pos_coverage");
					s.positionByPositionCoverageProtobuilder = builder;
				});
			}

			if (param.outputCoverageBed) {
				final String coverageName = analyzer.finalOutputBaseName + "_coverage_";
				analyzer.stats.forEach(s -> {
					final String path = coverageName + s.getName() + ".bed";
					try {
						s.coverageBEDWriter = new FileWriter(path);
						analyzer.itemsToClose.add(s.coverageBEDWriter);
					} catch (IOException e) {
						handleOutputException(path, e, param);
					}
				});
			}

			for (int contigIndex = 0; contigIndex < contigNamesToProcess.size(); contigIndex++) {
				final int finalContigIndex = contigIndex;
				final SerializablePredicate<SequenceLocation> p =
					l -> l.contigIndex == finalContigIndex;
				final String contigName = contigNamesToProcess.get(contigIndex);
				analyzer.stats.forEach(s -> {
					s.topBottomSubstDisagreementsQ2.addPredicate(contigName, p);
					s.topBottomDelDisagreementsQ2.addPredicate(contigName, p);
					s.topBottomInsDisagreementsQ2.addPredicate(contigName, p);
					s.nPosDuplexCandidatesForDisagreementQ2.addPredicate(contigName, p);
					s.codingStrandSubstQ2.addPredicate(contigName, p);
					s.templateStrandSubstQ2.addPredicate(contigName, p);
					s.codingStrandDelQ2.addPredicate(contigName, p);
					s.templateStrandDelQ2.addPredicate(contigName, p);
					s.codingStrandInsQ2.addPredicate(contigName, p);
					s.templateStrandInsQ2.addPredicate(contigName, p);
				});
			}

			if (param.bedDisagreementOrienter != null) {
				try {
					analyzer.codingStrandTester = BedReader.getCachedBedFileReader(param.bedDisagreementOrienter, ".cached",
						groupSettings.getContigNames(), "", false);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " +
						param.bedDisagreementOrienter, e);
				}
			}

			final Set<String> bedFileNames = new HashSet<>();

			for (String fileName: param.reportStatsForBED) {
				try {
					if (!bedFileNames.add(fileName)) {
						throw new IllegalArgumentException("Bed file " + fileName + " specified multiple times");
					}
					final File f = new File(fileName);
					final String filterName = f.getName();
					final @NonNull GenomeFeatureTester filter =
						BedReader.getCachedBedFileReader(fileName, ".cached",
							groupSettings.getContigNames(), filterName, false);
					final BedComplement notFilter = new BedComplement(filter);
					final String notFilterName = "NOT " + f.getName();
					analyzer.filtersForCandidateReporting.put(filterName, filter);

					analyzer.stats.forEach(s -> {
						s.nPosDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
						s.nPosDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
						s.nPosDuplexCandidatesForDisagreementQ2.addPredicate(filterName, filter);
						s.nPosDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
						s.nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate.addPredicate(filterName, filter);
						s.nPosCandidatesForUniqueMutation.addPredicate(filterName, filter);
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

						s.nPosDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(notFilterName, notFilter);
						s.nPosDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(notFilterName, notFilter);
						s.nPosDuplexCandidatesForDisagreementQ2.addPredicate(notFilterName, notFilter);
						s.nPosDuplexQualityQ2OthersQ1Q2.addPredicate(notFilterName, notFilter);
						s.nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate.addPredicate(notFilterName, notFilter);
						s.nPosCandidatesForUniqueMutation.addPredicate(notFilterName, notFilter);
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
						analyzer.filtersForCandidateReporting.put(notFilterName, notFilter);
					});
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + fileName, e);
				}
			}

			for (String fileName: param.reportStatsForNotBED) {
				try {
					final File f = new File(fileName);
					final String filterName = "NOT " + f.getName();
					final GenomeFeatureTester filter0 = BedReader.getCachedBedFileReader(fileName, ".cached",
						groupSettings.getContigNames(), filterName, false);
					final BedComplement filter = new BedComplement(filter0);
					analyzer.stats.forEach(s -> {
						s.nPosDuplexWithTopBottomDuplexDisagreementNoWT.addPredicate(filterName, filter);
						s.nPosDuplexWithTopBottomDuplexDisagreementNotASub.addPredicate(filterName, filter);
						s.nPosDuplexCandidatesForDisagreementQ2.addPredicate(filterName, filter);
						s.nPosDuplexQualityQ2OthersQ1Q2.addPredicate(filterName, filter);
						s.nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate.addPredicate(filterName, filter);
						s.nPosCandidatesForUniqueMutation.addPredicate(filterName, filter);
						s.topBottomSubstDisagreementsQ2.addPredicate(filterName, filter);
						s.topBottomDelDisagreementsQ2.addPredicate(filterName, filter);
						s.topBottomInsDisagreementsQ2.addPredicate(filterName, filter);
						s.topBottomDisagreementsQ2TooHighCoverage.addPredicate(filterName, filter);
					});

					analyzer.filtersForCandidateReporting.put(filterName, filter);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + fileName, e);
				}
			}

			if (param.reportBreakdownForBED.size() != param.saveBEDBreakdownTo.size()) {
				throw new IllegalArgumentException("Arguments -reportBreakdownForBED and " +
					"-saveBEDBreakdownTo must appear same number of times");
			}

			final Set<String> outputPaths = new HashSet<>();
			int index;
			for (index = 0; index < param.reportBreakdownForBED.size(); index++) {
				try {
					final File f = new File(param.reportBreakdownForBED.get(index));
					final BedReader filter = new BedReader(groupSettings.getContigNames(),
						new BufferedReader(new FileReader(f)), f.getName(),
						param.bedFeatureSuppInfoFile == null ? null :
							new BufferedReader(new FileReader(param.bedFeatureSuppInfoFile)), false);
					int index0 = index;
					analyzer.stats.forEach(s -> {
						CounterWithBedFeatureBreakdown counter;
						try {
							if (param.refSeqToOfficialGeneName == null) {
								counter = new CounterWithBedFeatureBreakdown(filter,
									null,
									groupSettings);
							} else {
								try (BufferedReader refSeqToOfficial = new BufferedReader(
									new FileReader(param.refSeqToOfficialGeneName))) {
								counter = new CounterWithBedFeatureBreakdown(filter,
									TSVMapReader.getMap(refSeqToOfficial),
									groupSettings);
								}
							}
						} catch (Exception e) {
							throw new RuntimeException("Problem setting up BED file " + f.getName(), e);
						}

						String outputPath = param.saveBEDBreakdownTo.get(index0) + s.getName();
						if (!outputPaths.add(outputPath)) {
							throw new IllegalArgumentException("saveBEDBreakdownTo " + outputPath +
								" specified multiple times");
						}
						counter.setOutputFile(new File(outputPath + "_nPosDuplex.bed"));
						s.nPosDuplex.addPredicate(f.getName(), filter, counter);
						for (List<IntervalData<GenomeInterval>> locs: filter.bedFileIntervals.values()) {
							for (IntervalData<GenomeInterval> loc: locs) {
								for (GenomeInterval interval : loc.getData()) {
									counter.accept(interval.getStartLocation(), 0);
									if (NONTRIVIAL_ASSERTIONS && !counter.getCounter().getCounts().containsKey(interval)) {
										throw new AssertionFailedException("Failed to add " + interval + "; matches were " +
											counter.getBedFeatures().apply(interval.getStartLocation()));
									}
								}
							}
						}
						counter.setNormalizedOutput(true);
						counter.setAnalyzerName(name);

						counter = new CounterWithBedFeatureBreakdown(filter, null, groupSettings);
						counter.setOutputFile(new File(param.saveBEDBreakdownTo.get(index0) + s.getName() +
							"_nPosDuplexQualityQ2OthersQ1Q2_" + name + ".bed"));
						s.nPosDuplexQualityQ2OthersQ1Q2.addPredicate(f.getName(), filter, counter);
					});
				} catch (Exception e) {
					throw new RuntimeException("Problem setting up BED file " + param.reportBreakdownForBED.get(index), e);
				}
			}
		});//End parallel loop over analyzers

		groupSettings.mutationsToAnnotate.clear();
		if (param.annotateMutationsInFile != null) {
			Set<String> unknownSampleNames = new TreeSet<>();
			groupSettings.mutationsToAnnotate.putAll(MutationListReader.readMutationList(
				param.annotateMutationsInFile, param.annotateMutationsInFile, contigNames,
				sampleNames, unknownSampleNames));
			if (!unknownSampleNames.isEmpty()) {
				Util.printUserMustSeeMessage("Warning: unrecognized sample names in annotateMutationsInFile " +
					unknownSampleNames);
			}
		}

		for (String s: param.traceFields) {
			try {
				String[] split = s.split(":");
				String analyzerName = split[0];
				if (!sampleNames.contains(analyzerName)) {
					throw new IllegalArgumentException("Unrecognized sample name " +
						analyzerName);
				}
			} catch (Exception e) {
				throw new RuntimeException("Problem with traceField " + s, e);
			}
		}

		if (param.lenientSamValidation) {
			SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.LENIENT);
		}

		final List<Phaser> phasers = new ArrayList<>();

		final Histogram dubiousOrGoodDuplexCovInAllInputs = new Histogram(500);
		final Histogram goodDuplexCovInAllInputs = new Histogram(500);

		SignalProcessor infoSignalHandler = signal -> {
			final PrintStream printStream = (signal == null) ? out : err;
			for (Mutinack analyzer: analyzers) {
				analyzer.printStatus(printStream, signal != null);
			}
			printStream.flush();

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
			printStream.println(ManagementFactory.getMemoryPoolMXBeans().
				stream().
				map(m -> m.getName() + ": " + m.getUsage().toString()).
				collect(Collectors.joining(",")));

			outputJSON(param, analyzers);
			if (!param.outputToDatabaseURL.isEmpty()) {
				DatabaseOutput0.outputToDatabase(param, analyzers);
			}
		};
		Signals.registerSignalProcessor("INFO", infoSignalHandler);
		closeableCloser.add(new CloseableWrapper<>(infoSignalHandler,
			sh -> Signals.removeSignalProcessor("INFO", infoSignalHandler)));

		statusLogger.info("Starting sequence analysis");

		final List<List<AnalysisChunk>> analysisChunks = new ArrayList<>();

		int nParameterSets = -1;
		for (Mutinack m: analyzers) {
			int n = m.stats.size();
			if (nParameterSets == -1) {
				nParameterSets = n;
			} else if (n != nParameterSets) {
				throw new AssertionFailedException("Analyzer " + m.name + " has " + n +
					" parameter sets instead of " + nParameterSets);
			}
		}

		for (int contigIndex0 = 0; contigIndex0 < contigNames.size(); contigIndex0++) {
			final int contigIndex = contigIndex0;

			final int startContigAtPosition;
			final int terminateContigAtPosition;
			{
				final String contigName = contigNames.get(contigIndex);
				int idx = truncateAtContigs.indexOf(contigName);
				if (idx > -1) {
					terminateContigAtPosition = truncateAtPositions.get(idx);
				} else {
					terminateContigAtPosition =
						Objects.requireNonNull(contigSizes.get(contigName)) - 1;
				}
				idx = startAtContigs.indexOf(contigNames.get(contigIndex));
				if (idx > -1) {
					startContigAtPosition = startAtPositions.get(idx);
				} else {
					startContigAtPosition = 0;
				}
			}

			final int contigParallelizationFactor = getContigParallelizationFactor(
				contigIndex, param);
			List<AnalysisChunk> contigAnalysisChunks = new ArrayList<>();
			analysisChunks.add(contigAnalysisChunks);

			final int subAnalyzerSpan = (terminateContigAtPosition - startContigAtPosition + 1) /
				contigParallelizationFactor;
			for (int p = 0; p < contigParallelizationFactor; p++) {
				final AnalysisChunk analysisChunk = new AnalysisChunk(
					Objects.requireNonNull(contigNames.get(contigIndex)), nParameterSets);
				contigAnalysisChunks.add(analysisChunk);

				final int startSubAt = startContigAtPosition + p * subAnalyzerSpan;
				final int terminateAtPosition = (p == contigParallelizationFactor - 1) ?
					terminateContigAtPosition
					: startSubAt + subAnalyzerSpan - 1;

				analysisChunk.contig = contigIndex;
				analysisChunk.startAtPosition = startSubAt;
				analysisChunk.terminateAtPosition = terminateAtPosition;
				analysisChunk.pauseAtPosition = new SettableInteger();
				analysisChunk.lastProcessedPosition = new SettableInteger();
				analysisChunk.groupSettings = groupSettings;

				analyzers.forEach(a -> {
					final SubAnalyzer subAnalyzer = new SubAnalyzer(Objects.requireNonNull(a));
					a.subAnalyzers.add(subAnalyzer);
					analysisChunk.subAnalyzers.add(subAnalyzer);
				});

				final SubAnalyzerPhaser phaser = new SubAnalyzerPhaser(param,
					analysisChunk,
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

		final int endIndex = contigNames.size() - 1;
		if (contigThreadPool == null) {
			synchronized(Mutinack.class) {
				if (contigThreadPool == null) {
					contigThreadPool = Executors.newFixedThreadPool(param.maxParallelContigs,
						new NamedPoolThreadFactory("main_contig_thread_"));
				}
			}
		}
		final ParFor parFor = new ParFor(0, endIndex, null, contigThreadPool, true);
		parFor.setName("contig loop");

		for (int worker = 0; worker < parFor.getNThreads(); worker++)
			parFor.addLoopWorker((final int contigIndex, final int threadIndex) -> {

				final int contigParallelizationFactor = getContigParallelizationFactor(
					contigIndex, param);

				final List<Future<?>> futures = new ArrayList<>();
				int analyzerIndex = -1;

				for (Mutinack analyzer: analyzers) {
					analyzerIndex++;

					for (int p = 0; p < contigParallelizationFactor; p++) {
						final AnalysisChunk analysisChunk = analysisChunks.get(contigIndex).
							get(p);
						final SubAnalyzer subAnalyzer = analysisChunk.subAnalyzers.get(analyzerIndex);

						Runnable r = () -> ReadLoader.load(analyzer, analyzer.param, groupSettings,
							subAnalyzer, analysisChunk, groupSettings.PROCESSING_CHUNK,
							contigNames,
							contigIndex, alignmentWriter,
							StaticStuffToAvoidMutating::getContigSequence);
						futures.add(StaticStuffToAvoidMutating.getExecutorService().submit(r));
					}//End loop over parallelization factor
				}//End loop over analyzers

				MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();

				//Catch all the exceptions because the exceptions thrown by
				//some threads may be secondary to the primary exception
				//but may still be examined before the primary exception
				for (Future<?> f: futures) {
					gatherer.tryAdd(f::get);
				}

				gatherer.throwIfPresent();

				for (int p = 0; p < contigParallelizationFactor; p++) {
					final AnalysisChunk analysisChunk = analysisChunks.get(contigIndex).
						get(p);
					Assert.noException(
						() -> analysisChunk.subAnalyzers.forEach(sa -> {
							sa.checkAllDone();
							analyzers.forEach(a -> {
								boolean found = false;
								for (int i = 0; i < a.subAnalyzers.size(); i++) {
									if (a.subAnalyzers.get(i) == sa) {
										Assert.isFalse(found);
										a.subAnalyzers.set(i, null);
										found = true;
									}
								}
							});
						}));
				}

				return null;
			});//End Parfor loop over contigs

		parFor.run(true);

		if (groupSettings.terminateAnalysis) {
			analyzers.forEach(a -> a.stats.forEach(s -> s.analysisTruncated = true));
		}

		infoSignalHandler.handle(null);
		if (!param.noStatusMessages) {
			Util.printUserMustSeeMessage("Analysis of samples " + analyzers.stream().map(a -> a.name
				+ " at " + ((int) a.processingThroughput()) + " records / s").
				collect(Collectors.joining(", ")) + " completed on host " +
				StaticStuffToAvoidMutating.hostName +
				" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()) +
				" (elapsed time " +
				(System.nanoTime() - analyzers.get(0).timeStartProcessing) / 1_000_000_000d +
				" s)");
			ManagementFactory.getGarbageCollectorMXBeans().forEach(gc ->
				Util.printUserMustSeeMessage(gc.getName() + " (" +
					Arrays.toString(gc.getMemoryPoolNames()) + "): " + gc.getCollectionCount() +
					" " + gc.getCollectionTime()));
		}

		if (param.readContigsFromFile) {//Probably reference transcriptome; TODO need to
			//devise a better way of figuring this out
			for (Mutinack analyzer: analyzers) {
				final ICounterSeqLoc counter =
					Objects.requireNonNull(
						analyzer.stats.get(0).nPosDuplex.getSeqLocCounters().get("All")).snd;
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
					sortedEntries.sort(Comparator.comparing(Entry::getKey));

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
		}//End if readContigsFromFile

		if (mutationAnnotationWriter != null) {//Unnecessary test, performed to suppress null warning
			for (@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>> leftBehindList:
				groupSettings.mutationsToAnnotate.values()) {
				for (Pair<@NonNull Mutation, @NonNull String> leftBehind: leftBehindList) {
					mutationAnnotationWriter.append(leftBehind.snd + "\tNOT FOUND\n");
				}
			}
		}
	}

	private static int getContigParallelizationFactor(int contigIndex, Parameters param) {
		List<Integer> factorList = param.contigByContigParallelization;
		final int result;
		if (!factorList.isEmpty()) {
			result = factorList.get(Math.min(factorList.size() - 1, contigIndex));
		} else {
			result = param.parallelizationFactor;
		}
		return result;
	}

	public static RunResult getRunResult(Parameters param, Collection<Mutinack> analyzers) {
		RunResult root = new RunResult();
		final String mutinackVersion = GitCommitInfo.getGitCommit();
		root.mutinackVersion = mutinackVersion;
		root.parameters = param;
		root.samples = analyzers.stream().map(a -> new ParedDownMutinack(a, a.startDate, new Date(),
					a.param.runBatchName, a.param.runName)).
				collect(Collectors.toList());
		analyzers.forEach(Actualizable::actualize);
		analyzers.stream().flatMap(a -> a.subAnalyzers.stream()).
			filter(Objects::nonNull).
			map(sa -> sa.stats).
			forEach(stats -> stats.mutinackVersions.add(mutinackVersion));
		return root;
	}

	private static void outputJSON(Parameters param, Collection<Mutinack> analyzers) {
		if (!param.outputJSONTo.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			SimpleModule module = new SimpleModule();
			mapper.registerModule(module).setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
					.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
					.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
					.withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
					.withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));
			RunResult root = getRunResult(param, analyzers);
			try {
				mapper.writerWithDefaultPrettyPrinter().writeValue(
						new File(param.outputJSONTo), root);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void close() {
		MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
		for (Closeable c: itemsToClose) {
			gatherer.tryAdd(c::close);
		}

		gatherer.tryAdd(() -> PoolSettings.removePoolSetting(poolSettings));

		stats.forEach(s -> gatherer.tryAdd(() -> {
			if (s.positionByPositionCoverage != null) {
				Builder builder = s.positionByPositionCoverageProtobuilder;
				for (Entry<String, int[]> e: s.positionByPositionCoverage.entrySet()) {
					PosByPosNumbersPB.ContigNumbers.Builder builder2 =
						PosByPosNumbersPB.ContigNumbers.newBuilder();
					builder2.setContigName(e.getKey());
					int [] numbers = e.getValue();
					builder2.ensureNumbersIsMutable(numbers.length);
					builder2.numUsedInNumbers_ = numbers.length;
					builder2.numbers_ = numbers;
					builder.addContigNumbers(builder2);
				}
				Path path = Paths.get(builder.getSampleName() + ".proto");
				builder.setSampleName(path.getFileName().toString());
				Files.write(path, builder.build().toByteArray());
			}
		}));

		gatherer.throwIfPresent();
	}

	private static void handleOutputException(String fileName, Throwable e, Parameters param) {
		String baseMessage = "Could not open file " + fileName;
		if (param.terminateUponOutputFileError) {
			throw new RuntimeException(baseMessage, e);
		}
		Util.printUserMustSeeMessage(baseMessage + "; keeping going anyway");
	}

	private double processingThroughput() {
		return (stats.
				get(0).
				nRecordsProcessed.sum()) /
				((System.nanoTime() - timeStartProcessing) / 1_000_000_000d);
	}

	private void printStatus(PrintStream stream, boolean colorize) {
		stats.forEach(s -> {
			try {
				printStats(s, stream, colorize);
			} catch (ConcurrentModificationException e) {
				stream.println(e);
			}
		});
	}

	private void printStats(AnalysisStats s, PrintStream stream, boolean colorize) {
		actualize();
		stream.println();
		stream.println(greenB(colorize) + "Statistics " + s.getName() + " for " + inputBam.getAbsolutePath() + reset(colorize));

		s.print(stream, colorize);

		stream.println(blueF(colorize) + "Average Phred score: " + reset(colorize) +
			DoubleAdderFormatter.formatDouble(s.phredSumProcessedbases.sum() / s.nProcessedBases.sum()));

		if (s.outputLevel.compareTo(OutputLevel.VERBOSE) >= 0) {
			stream.println(blueF(colorize) + "Top 100 barcode hits in cache: " + reset(colorize) +
				internedVariableBarcodes.values().stream().sorted((ba, bb) -> - Long.compare(ba.nHits.sum(), bb.nHits.sum())).
					limit(100).map(ByteArray::toString).collect(Collectors.toList()));
		}

		stream.println(blueF(colorize) + "Processing throughput: " + reset(colorize) +
			((int) processingThroughput()) + " records / s");

		if (!objectAllocations.isEmpty()) {
			stream.println(objectAllocations);
			objectAllocations.forEachValue(10, sl -> sl.set(0));
		}

		for (String counter: s.nPosCandidatesForUniqueMutation.getCounterNames()) {
			final double mutationRate = s.nPosCandidatesForUniqueMutation.getSum(counter) /
				s.nPosDuplexQualityQ2OthersQ1Q2.getSum(counter);
			final String mutationRateString;
			if (mutationRate > 1E-3 || mutationRate == 0) {
				mutationRateString = DoubleAdderFormatter.formatDouble(mutationRate);
			} else {
				NumberFormat mrFormatter = mutationRateFormatter.get();
				mutationRateString = mrFormatter.format(mutationRate);
			}
			stream.println(greenB(colorize) + "Mutation rate for " + counter + ": " + reset(colorize) +
				mutationRateString);
		}
	}

	private static final ThreadLocal<NumberFormat> mutationRateFormatter
		= ThreadLocal.withInitial(() -> {
			DecimalFormat f = new DecimalFormat("0.###E0");
			DoubleAdderFormatter.setNanAndInfSymbols(f);
			return f;
		});

	@Override
	public String toString() {
		return name + " (" + inputBam.getAbsolutePath() + ")";
	}

	@Override
	public void actualize() {
		stats.forEach(Actualizable::actualize);
	}

}
