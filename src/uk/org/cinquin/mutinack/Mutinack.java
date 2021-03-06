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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.block.factory.Procedures;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jwetherell.algorithms.data_structures.IntervalData;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import contrib.net.sf.picard.sam.BuildBamIndex;
import contrib.net.sf.samtools.SAMFileHeader;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileReader.QueryInterval;
import contrib.net.sf.samtools.SAMFileReader.ValidationStringency;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMFileWriterFactory;
import contrib.net.sf.samtools.SAMProgramRecord;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import contrib.net.sf.samtools.SAMSequenceRecord;
import contrib.net.sf.samtools.util.RuntimeIOException;
import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolException;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObjectBase;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.TMap;
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
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.BAMUtil;
import uk.org.cinquin.mutinack.misc_util.CloseableCloser;
import uk.org.cinquin.mutinack.misc_util.CloseableListWrapper;
import uk.org.cinquin.mutinack.misc_util.CloseableWrapper;
import uk.org.cinquin.mutinack.misc_util.GetReadStats;
import uk.org.cinquin.mutinack.misc_util.GitCommitInfo;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.MultipleExceptionGatherer;
import uk.org.cinquin.mutinack.misc_util.NamedPoolThreadFactory;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
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
import uk.org.cinquin.mutinack.qualities.Quality;
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
			"averageMappingQuality",
			"fractionTopStrandReads", "fractionForwardReads", "topAndBottomStrandsPresent", "sequenceContext",
			"nDuplexesSisterArm", "insertSize", "insertSizeAtPos10thP",
			"insertSizeAtPos90thP", "minDistanceLigSite", "maxDistanceLigSite", "negativeCodingStrand",
			"meanDistanceLigSite", "probCollision", "positionInRead", "readEffectiveLength",
			"nameOfOneRead", "readAlignmentStart", "mateAlignmentStart", "readAlignmentEnd",
			"mateAlignmentEnd", "refPositionOfLigSite", "issuesList", "medianPhredAtPosition",
			"minInsertSize", "maxInsertSize", "secondHighestAlleleFrequency",
			"highestAlleleFrequency", "smallestConcurringDuplexDistance", "largestConcurringDuplexDistance",
			"supplementalMessage"
	));
	@SuppressWarnings("StaticVariableMayNotBeInitialized")
	private volatile static ExecutorService contigThreadPool;

	private final Collection<Closeable> itemsToClose = new ArrayList<>();
	private final Date startDate;

	private final @NonNull MutinackGroup groupSettings;
	private final @NonNull Parameters param;
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

	final public ObjectPool<SAMFileReader> readerPool =
		poolSettings.min(0).max(300).maxIdle(300).pool(true); 	//Need min(0) so inputBam is set before first
																												//reader is created
	final ValidationStringency samValidationStringency;
	final double @Nullable[] insertSizeProbSmooth;
	private final double @Nullable[] insertSizeProbRaw;
	final @NonNull Collection<File> intersectAlignmentFiles;
	final @NonNull public TMap<String, GenomeFeatureTester> filtersForCandidateReporting = new THashMap<>();
	@Nullable GenomeFeatureTester codingStrandTester;
	final byte @NonNull[] constantBarcode;
	final int maxNDuplexes;
	public @NonNull List<@NonNull AnalysisStats> stats = new ArrayList<>();
	private String finalOutputBaseName;
	public boolean notifiedUnpairedReads = false;
	final @Nullable SAMFileWriter outputAlignmentWriter;

	/**
	 *
	 * @param readName
	 * @param firstOfPair
	 * @param location
	 * @param avoidAlignmentStart0Based      Used to make sure we don't just retrieve the same read as the original,
	 * in the case where both alignments are close together
	 * @param windowHalfWidth
	 * @param parseReadNameForPosition
	 * @return
	 */
	public @Nullable ExtendedSAMRecord getRead(String readName, boolean firstOfPair,
			SequenceLocation location, int avoidAlignmentStart0Based, int windowHalfWidth,
			boolean parseReadNameForPosition) {

		SAMFileReader bamReader;
		try {
			//noinspection resource
			bamReader = readerPool.getObj();
		} catch (PoolException e) {
			throw new RuntimeException(e);
		}

		try {
			final QueryInterval[] bamContig = {
				bamReader.makeQueryInterval(location.contigName, Math.max(location.position + 1 - windowHalfWidth, 1),
					location.position + 1 + windowHalfWidth)};

			try (SAMRecordIterator it = bamReader.queryOverlapping(bamContig)) {
				while (it.hasNext()) {
					SAMRecord record = it.next();
					if (record.getReadName().equals(readName) && record.getFirstOfPairFlag() == firstOfPair &&
							record.getAlignmentStart() - 1 != avoidAlignmentStart0Based) {
						return SubAnalyzer.getExtendedNoCaching(record,
							new SequenceLocation(location.referenceGenome, location.contigName, groupSettings.getIndexContigNameReverseMap(),
								record.getAlignmentStart() - 1, false), this, parseReadNameForPosition);
					}
				}
				return null;
			}
		} finally {
			readerPool.returnObj(bamReader);
		}
	}

	public void addFilterForCandidateReporting(String filterName, GenomeFeatureTester filter) {
		if (filtersForCandidateReporting.put(filterName, filter) != null) {
			throw new IllegalArgumentException("Filter " + filterName + " already added");
		}
	}

	private Mutinack(
			@NonNull MutinackGroup groupSettings,
			Parameters param,
			@NonNull String name,
			@NonNull File inputBam,
			@NonNull ValidationStringency samValidationStringency,
			@NonNull PrintStream out,
			@Nullable OutputStreamWriter mutationAnnotationWriter,
			@Nullable Histogram approximateReadInsertSize,
			byte @NonNull [] constantBarcode,
			@NonNull List<File> intersectAlignmentFiles,
			@NonNull OutputLevel outputLevel,
			int maxNDuplexes,
			@Nullable SAMFileWriter outputAlignmentWriter) {

		param.validate();//This is redundant with validation performed in realMain0, but it
		//kept here in case a worker and server are out of sync
		this.groupSettings = groupSettings;
		this.param = param;
		this.name = name;
		this.inputBam = inputBam;
		this.samValidationStringency = samValidationStringency;
		this.outputAlignmentWriter = outputAlignmentWriter;
		if (approximateReadInsertSize != null) {
			insertSizeProbRaw = approximateReadInsertSize.toProbabilityArray(false);
			insertSizeProbSmooth = approximateReadInsertSize.toProbabilityArray(true);
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
				statsNames = Collections.singletonList("main_stats");
			}
			final BiConsumer<String, Parameters> consumer = (statsName, p) ->
				stats.add(createStats(statsName, p, out,
					mutationAnnotationWriter, outputLevel));
			recursiveParameterFill(consumer, 0, param.exploreParameters, statsNames, param,
				param.cartesianProductOfExploredParameters);
		}

		sortStatsListByDuplexLoadParams(stats);

		final @NonNull String inputHash = (inputBam.length() > param.computeHashForBAMSmallerThanInGB * Math.pow(1024,3)) ?
				"too big"
			:
				BAMUtil.getHash(inputBam);

		stats.forEach(stat -> {
			stat.approximateReadInsertSize = insertSizeProbSmooth;
			stat.approximateReadInsertSizeRaw = insertSizeProbRaw;
			stat.inputBAMHashes.put(Objects.requireNonNull(inputBam.getPath()), inputHash);
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
						map(e -> e.getKey() + '=' + e.getValue()).collect(Collectors.joining(", "));
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
			Objects.requireNonNull(getGroupSettings()), param1.reportCoverageAtAllPositions);
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

	public static void main(String args[]) {
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

		param.automaticAdjustments();
		param.commandLine = Arrays.toString(args);
		param.validate();

		if (param.timeoutSeconds != 0) {
			Server.PING_INTERVAL_SECONDS = 1 + param.timeoutSeconds / 3;
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

	@SuppressWarnings({"resource", "FutureReturnValueIgnored"})
	public static RunResult realMain1(Parameters param, PrintStream out, PrintStream err)
			throws InterruptedException, IOException {

		Thread.interrupted();//XXX The actual problem needs to be fixed upstream

		if (!versionChecked && !param.noStatusMessages && !param.skipVersionCheck) {
			versionChecked = true;
			StaticStuffToAvoidMutating.getExecutorService().submit(Util::versionCheck);
		}

		if (!versionChecked && !param.noStatusMessages) {
			printUserMustSeeMessage(GitCommitInfo.getGitCommit());
		}

		if (!param.saveFilteredReadsTo.isEmpty()) {
			throw new RuntimeException("Not implemented");
		}

		if (!param.noStatusMessages) {
			printUserMustSeeMessage("Analysis started on host " +
				StaticStuffToAvoidMutating.hostName +
				" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()));
		}

		out.println(param.toString());
		out.println("Non-trivial assertions " +
				(NONTRIVIAL_ASSERTIONS ?
				"on" : "off"));
		out.println("Costly assertions " +
			(param.enableCostlyAssertions ?
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

		final MutinackGroup groupSettings = new MutinackGroup(param.rnaSeq);
		groupSettings.registerInterruptSignalProcessor();
		groupSettings.PROCESSING_CHUNK = param.processingChunk;
		groupSettings.INTERVAL_SLOP = param.alignmentPositionMismatchAllowed;
		groupSettings.BIN_SIZE = param.contigStatsBinLength;
		groupSettings.terminateImmediatelyUponError =
			param.terminateImmediatelyUponError;
		groupSettings.setBarcodePositions(0, param.variableBarcodeLength - 1,
			3, 5);

		param.group = groupSettings;

		try (CloseableCloser closeableCloser = new CloseableCloser()) {
			closeableCloser.add(groupSettings);
			closeableCloser.add(new CloseableListWrapper<>(analyzers));
			realMain2(param, groupSettings, analyzers, out, err, closeableCloser);
		}

		return getRunResult(param, analyzers);
	}

	@SuppressWarnings("resource")
	private static SAMFileWriter createWriter(Parameters param, String path, CloseableCloser closeableCloser) {
		SAMFileWriterFactory factory = new SAMFileWriterFactory();
		SAMFileHeader header = new SAMFileHeader();
		factory.setCreateIndex(true);
		header.setSortOrder(param.sortOutputAlignmentFile ?
			SAMFileHeader.SortOrder.coordinate
			: SAMFileHeader.SortOrder.unsorted);
		if (param.sortOutputAlignmentFile) {
			factory.setMaxRecordsInRam(10_000);
		}
		final File inputBam = new File(param.inputReads.get(0));

		final List<SAMProgramRecord> programs;
		try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
			final SAMFileHeader inputHeader = tempReader.getFileHeader();
			header.setSequenceDictionary(inputHeader.getSequenceDictionary());
			programs = new ArrayList<>(inputHeader.getProgramRecords());
		}

		SAMProgramRecord mutinackRecord = new SAMProgramRecord("Mutinack");//TODO Is there
		//documentation somewhere of what programGroupId should be??
		mutinackRecord.setProgramName("Mutinack");
		mutinackRecord.setProgramVersion(GitCommitInfo.getGitCommit());
		mutinackRecord.setCommandLine(param.toString().replace("\n", "___"));
		programs.add(mutinackRecord);
		header.setProgramRecords(programs);

		SAMFileWriter result = factory.makeBAMWriter(header, false, new File(path), 0);
		closeableCloser.add(new CloseableWrapper<>(result, SAMFileWriter::close));

		return result;
	}

	@SuppressWarnings("resource")
	private static void realMain2(
			final Parameters param,
			final MutinackGroup groupSettings,
			final List<Mutinack> analyzers,
			final PrintStream out,
			final PrintStream err,
			final CloseableCloser closeableCloser) throws IOException, InterruptedException {

		final SAMFileWriter sharedAlignmentWriter;
		if (param.outputAlignmentFile.isEmpty()) {
			sharedAlignmentWriter = null;
		} else if (param.inputReads.size() >= 1 && param.outputAlignmentFile.size() == 1) {
			sharedAlignmentWriter = createWriter(param, param.outputAlignmentFile.get(0), closeableCloser);
		} else if (param.inputReads.size() != param.outputAlignmentFile.size()) {
			throw new IllegalArgumentException("Number of paths passed with outputAlignmentFile should be 0, 1, or " +
				"match the number of paths passed with inputReads, i.e. " + param.inputReads.size() +", not " +
				param.outputAlignmentFile.size());
		} else {
			sharedAlignmentWriter = null;
		}

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

		final @NonNull Map<@NonNull String, @NonNull Integer> contigSizes0 =
			new HashMap<>(
				StaticStuffToAvoidMutating.loadContigsFromFile(param.referenceGenome));

		try (SAMFileReader tempReader = new SAMFileReader(new File(param.inputReads.get(0)))) {
			final List<String> sequenceNames = tempReader.getFileHeader().
				getSequenceDictionary().getSequences().stream().
				map(SAMSequenceRecord::getSequenceName).collect(Collectors.toList());

			contigSizes0.entrySet().removeIf(e -> {
				if (!sequenceNames.contains(e.getKey())) {
					printUserMustSeeMessage("Reference sequence " + e.getKey() + " not present in sample header; ignoring");
					return true;
				}
				return false;
			});
		}
		contigSizes = Collections.unmodifiableMap(contigSizes0);
		List<@NonNull String> contigNames0 = new ArrayList<>(contigSizes0.keySet());
		contigNames0.sort(null);
		contigNames = Collections.unmodifiableList(contigNames0);
		for (int i = 0; i < contigNames.size(); i++) {
			groupSettings.getIndexContigNameReverseMap().put(contigNames.get(i), i);
		}
		groupSettings.setContigNames(contigNames);
		groupSettings.setContigSizes(contigSizes);

		if (param.contigNamesToProcess.isEmpty()) {
			if (!param.ignoreContigsContaining.isEmpty()) {
				contigNamesToProcess = contigNames.stream().filter(contigName ->
					param.ignoreContigsContaining.stream().noneMatch(pattern -> contigName.toUpperCase().contains(
						pattern))).
					collect(Collectors.toList());
				printUserMustSeeMessage("Processing contigs " + contigNamesToProcess);
			} else {
				contigNamesToProcess = contigNames;
			}
		}	else {
			contigNamesToProcess = new ArrayList<>(param.contigNamesToProcess);
			Util.checkContained(contigNamesToProcess, contigNames, "Error with -contigNamesToProcess: ");
			contigNamesToProcess.sort(null);
		}
		groupSettings.setContigNamesToProcess(contigNamesToProcess);

		StaticStuffToAvoidMutating.loadContigs(param.referenceGenomeShortName, param.referenceGenome,
			contigNames);

		groupSettings.forceOutputAtLocations.clear();

		Util.parseListStartStopLocations(param.referenceGenomeShortName, param.forceOutputAtPositions,
			groupSettings.getIndexContigNameReverseMap()).forEach(parsedLocation -> {
			if (groupSettings.forceOutputAtLocations.put(parsedLocation, false) != null) {
				printUserMustSeeMessage(Util.truncateString("Warning: repeated specification of " + parsedLocation +
					" in list of forced output positions"));
			}
		});

		param.tracePositions.stream().map(s -> SequenceLocation.parse(param.referenceGenomeShortName, s, groupSettings.getIndexContigNameReverseMap())).
			forEach(param.parsedTracePositions::add);

		for (String forceOutputFilePath: param.forceOutputAtPositionsTextFile) {
			try(Stream<String> lines = Files.lines(Paths.get(forceOutputFilePath))) {
				lines.forEach(l -> {
					for (String loc: l.split(" ")) {
						try {
							if (loc.isEmpty()) {
								continue;
							}
							int columnPosition = loc.indexOf(':');
							final @NonNull String contig = loc.substring(0, columnPosition);
							final String pos = loc.substring(columnPosition + 1);
							double position = NumberFormat.getNumberInstance(java.util.Locale.US).parse(pos).doubleValue() - 1;
							final int contigIndex = contigNames.indexOf(contig);
							if (contigIndex < 0) {
								throw new IllegalArgumentException("Unknown contig " + contig + "; known contigs are " +
									contigNames);
							}
							final SequenceLocation parsedLocation = new SequenceLocation(param.referenceGenomeShortName, contigIndex, contig,
								(int) Math.floor(position), position - Math.floor(position) > 0);
							if (groupSettings.forceOutputAtLocations.put(parsedLocation, false) != null) {
								printUserMustSeeMessage(Util.truncateString("Warning: repeated specification of " + parsedLocation +
									" in list of forced output positions"));
							}
						} catch (Exception e) {
							throw new RuntimeException("Error parsing " + loc + " in file " + forceOutputFilePath, e);
						}
					}
				});
			}
		}

		CompletionService<RunResult> completionService =
			new ExecutorCompletionService<>(StaticStuffToAvoidMutating.getExecutorService());
		for (String forceOutputFilePath: param.forceOutputAtPositionsBinFile) {
			completionService.submit(() ->  {
				try {
					RunResult runResult = (RunResult) Util.readObject(forceOutputFilePath);
					return runResult;
				} catch (Exception e) {
					throw new RuntimeException("Error extracting positions from binary out file " + forceOutputFilePath, e);
				}
			});
		}
		//The following two variables don't need to be atomic unless the loop below is made parallel
		AtomicInteger numberAddedPositions = new AtomicInteger();
		AtomicInteger numberConsideredPositions = new AtomicInteger();
		for (int fileNumber = 0; fileNumber < param.forceOutputAtPositionsBinFile.size(); fileNumber++) {
			try {
				completionService.take().get().extractDetections().
				filter(candidate -> {
					numberConsideredPositions.incrementAndGet();
					return candidate.getMutationType() != MutationType.WILDTYPE;}).
				filter(candidate -> candidate.getQuality().getNonNullValue().atLeast(Quality.GOOD)).
				forEach(candidate -> {
					if (groupSettings.forceOutputAtLocations.put(candidate.getLocation(), false) == null) {
						numberAddedPositions.incrementAndGet();
					}
				});
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		if (!param.forceOutputAtPositionsBinFile.isEmpty()) {
			printUserMustSeeMessage("Forcing output at " + numberAddedPositions.get() + " of " +
				numberConsideredPositions + " potential new positions read from " +
				param.forceOutputAtPositionsBinFile.size() + " binary files");
		}

		if (param.randomOutputRate != 0) {
			Random random = new Random(param.randomSeed);
			int randomLocs = 0;
			for (Entry<@NonNull String, Integer> c: contigSizes.entrySet()) {
				for (int i = 0; i < param.randomOutputRate * c.getValue(); i++) {
					@SuppressWarnings("null")
					SequenceLocation l = new SequenceLocation(
						param.referenceGenome,
						groupSettings.getIndexContigNameReverseMap().get(c.getKey()),
						c.getKey(), (int) (random.nextDouble() * (c.getValue() - 1)));
					if (groupSettings.forceOutputAtLocations.put(l, true) == null) {
						randomLocs++;
					}
				}
			}
			printUserMustSeeMessage("Added " + randomLocs + " random output positions");
		}
		@SuppressWarnings("null")
		@NonNull String forceOutputString = groupSettings.forceOutputAtLocations.toString();
		out.println("Forcing output at locations " +
			Util.truncateString(forceOutputString));

		out.println(String.join("\t", outputHeader));

		Pair<List<@NonNull String>, List<Integer>> parsedPositions =
			Util.parseListPositions(param.startAtPositions, true, "startAtPosition");
		final List<@NonNull String> startAtContigs = parsedPositions.fst;
		Util.checkContained(startAtContigs, contigNames, "Error validating startAtPosition:");
		final List<Integer> startAtPositions = parsedPositions.snd;

		Pair<List<@NonNull String>, List<Integer>> parsedPositions2 =
			Util.parseListPositions(param.stopAtPositions, true, "stopAtPosition");
		final List<@NonNull String> truncateAtContigs = parsedPositions2.fst;
		Util.checkContained(truncateAtContigs, contigNames, "Error validating stopAtPosition:");
		final List<Integer> truncateAtPositions = parsedPositions2.snd;

		Util.checkPositionsOrdering(parsedPositions, parsedPositions2);

		final @NonNull List<@NonNull GenomeFeatureTester> excludeBEDs = new ArrayList<>();
		for (String bed: param.excludeRegionsInBED) {
			try {
				final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
					groupSettings.getContigNames(), bed, param.referenceGenomeShortName, param);
				excludeBEDs.add(reader);
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + bed, e);
			}
		}

		final @NonNull List<@NonNull BedReader> repetitiveBEDs = new ArrayList<>();
		for (String bed: param.repetiveRegionBED) {
			try {
				final BedReader reader = BedReader.getCachedBedFileReader(bed, ".cached",
					groupSettings.getContigNames(), bed, param.referenceGenomeShortName, param);
				repetitiveBEDs.add(reader);
			} catch (Exception e) {
				throw new RuntimeException("Problem with BED file " + bed, e);
			}
		}

		final @Nullable OutputStreamWriter mutationWriterCopy = mutationAnnotationWriter;
		//Used to ensure that different analyzers do not use same output files
		final Set<String> sampleNames = new HashSet<>();

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
					throw new AssertionFailedException("Two or more analyzers trying to use the same name " +
						name);
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

			final int maxNDuplexes = param.maxNDuplexes.isEmpty() ? Integer.MAX_VALUE :
				(param.maxNDuplexes.size() == 1 ? param.maxNDuplexes.get(0) : param.maxNDuplexes.get(i));

			final OutputLevel[] d = OutputLevel.values();
			@SuppressWarnings("null")
			final @NonNull OutputLevel outputLevel = d[param.verbosity];

			final SAMFileWriter alignmentWriter;
			if (sharedAlignmentWriter != null) {
				alignmentWriter = sharedAlignmentWriter;
			} else if (!param.outputAlignmentFile.isEmpty()) {
				alignmentWriter = createWriter(param, param.outputAlignmentFile.get(i), closeableCloser);
			} else {
				alignmentWriter = null;
			}

			final ValidationStringency samValidationStringency;
			switch (param.samValidation) {
				case "none":
					samValidationStringency = ValidationStringency.SILENT;
					break;
				case "warning":
					samValidationStringency = ValidationStringency.LENIENT;
					break;
				case "error":
					samValidationStringency = ValidationStringency.STRICT;
					break;
				default:
					throw new AssertionFailedException();
			}
			SAMFileReader.setDefaultValidationStringency(samValidationStringency);
			final Mutinack analyzer = new Mutinack(
				groupSettings,
				param,
				name,
				inputBam,
				samValidationStringency,
				out,
				mutationWriterCopy,
				param.variableBarcodeLength == 0 ?
					GetReadStats.getApproximateReadInsertSize(inputBam, param.maxInsertSize, param.minMappingQualityQ2)
					:
					null,
				nonNullify(param.constantBarcode.getBytes()),
				intersectFiles,
				outputLevel,
				maxNDuplexes,
				alignmentWriter);
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
						Optional.ofNullable(new File(pathMain).getParentFile()).map(File::mkdirs);
						s.topBottomDisagreementWriter = new FileWriter(pathMain);
						analyzer.itemsToClose.add(s.topBottomDisagreementWriter);
					} catch (IOException e) {
						handleOutputException(pathMain, e, param);
					}
					final String pathNoWt = tbdNameNoWt + s.getName() + ".bed";
					try {
						Optional.ofNullable(new File(pathNoWt).getParentFile()).map(File::mkdirs);
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
					Optional.ofNullable(new File(path).getParentFile()).map(File::mkdirs);
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
					Builder builder = GenomeNumbers.newBuilder();
					builder.setGeneratingProgramVersion(GitCommitInfo.getGitCommit());
					builder.setGeneratingProgramArgs(param.toString());
					builder.setSampleName(analyzer.finalOutputBaseName + '_' +
						s.getName() + '_' + name + "_pos_by_pos_coverage");
					s.positionByPositionCoverageProtobuilder = builder;
				});
			}

			if (param.outputCoverageBed) {
				final String coverageName = analyzer.finalOutputBaseName + "_coverage_";
				analyzer.stats.forEach(s -> {
					final String path = coverageName + s.getName() + ".bed";
					try {
						Optional.ofNullable(new File(path).getParentFile()).map(File::mkdirs);
						s.coverageBEDWriter = new FileWriter(path);
						analyzer.itemsToClose.add(s.coverageBEDWriter);
					} catch (IOException e) {
						handleOutputException(path, e, param);
					}
				});
			}

			for (final String contigName: contigNamesToProcess) {
				final int contigIndex = Objects.requireNonNull(
					groupSettings.getIndexContigNameReverseMap().get(contigName));
				final SerializablePredicate<SequenceLocation> p =
					l -> l.contigIndex == contigIndex;
				analyzer.stats.forEach(s -> {
					s.topBottomSubstDisagreementsQ2.addPredicate(contigName, p);
					s.topBottomDelDisagreementsQ2.addPredicate(contigName, p);
					s.topBottomInsDisagreementsQ2.addPredicate(contigName, p);
					s.nPosDuplexCandidatesForDisagreementQ2.addPredicate(contigName, p);
					s.nPosDuplexCandidatesForDisagreementQ1.addPredicate(contigName, p);
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
						groupSettings.getContigNames(), "", param.referenceGenomeShortName, param);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " +
						param.bedDisagreementOrienter, e);
				}
			}

			final @NonNull Map<@NonNull String, @NonNull String> transcriptToGene;
			if (param.refSeqToOfficialGeneName == null) {
				transcriptToGene = Collections.emptyMap();
			} else {
				try (BufferedReader refSeqToOfficial = new BufferedReader(
						new FileReader(param.refSeqToOfficialGeneName))) {
					transcriptToGene = TSVMapReader.getMap(refSeqToOfficial);
				} catch (Exception e) {
					throw new RuntimeException("Problem reading refseq info from " + param.refSeqToOfficialGeneName, e);
				}
			}

			final Set<String> bedFileNames = new HashSet<>();
			for (String fileName: param.reportStatsForBED) {
				try {
					if (!bedFileNames.add(fileName)) {
						throw new AssertionFailedException();
					}
					final File f = new File(fileName);
					final String filterName = f.getName();
					final @NonNull GenomeFeatureTester filter =
						BedReader.getCachedBedFileReader(fileName, ".cached",
							groupSettings.getContigNames(), filterName, param.referenceGenomeShortName, transcriptToGene, param);
					final BedComplement notFilter = new BedComplement(filter);
					final String notFilterName = "NOT " + f.getName();
					analyzer.addFilterForCandidateReporting(filterName, filter);

					analyzer.stats.forEach(s -> {
						s.addLocationPredicate(filterName, filter);
						s.addLocationPredicate(notFilterName, notFilter);
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
						groupSettings.getContigNames(), filterName, param.referenceGenomeShortName, transcriptToGene, param);
					final BedComplement filter = new BedComplement(filter0);
					analyzer.stats.forEach(s -> s.addLocationPredicate(filterName, filter));

					analyzer.addFilterForCandidateReporting(filterName, filter);
				} catch (Exception e) {
					throw new RuntimeException("Problem with BED file " + fileName, e);
				}
			}

			final Set<String> outputPaths = new HashSet<>();
			for (final String bedPath: param.reportBreakdownForBED) {
				try {
					final File f = new File(bedPath);
					final BedReader filter = new BedReader(
						groupSettings.getContigNames(),
						new BufferedReader(new FileReader(f)),
						f.getName(),
						param.referenceGenomeShortName,
						Optional.ofNullable(param.bedFeatureSuppInfoFile).map(Functions.throwing(file ->
							new BufferedReader(new FileReader(file)))).orElse(null),
						transcriptToGene, false, param, null);
					final String filterName = f.getName();
					analyzer.addFilterForCandidateReporting(filterName, filter);
					analyzer.stats.forEach(s -> {
						CounterWithBedFeatureBreakdown counter =
							new CounterWithBedFeatureBreakdown(filter, transcriptToGene, groupSettings);
						counter.setNormalizedOutput(true);
						counter.setAnalyzerName(name);
						String outputPath = bedPath + '_' + s.getName();
						if (!outputPaths.add(outputPath)) {
							throw new AssertionFailedException();
						}
						counter.setOutputFile(new File(outputPath + "_nPosDuplex.bed"));
						s.nPosDuplex.addPredicate("breakdown_" + f.getName(), filter, counter);
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

						counter = new CounterWithBedFeatureBreakdown(filter, transcriptToGene, groupSettings);
						counter.setAnalyzerName(name);
						counter.setOutputFile(new File(bedPath + '_' + s.getName() +
							"_nPosDuplexQualityQ2OthersQ1Q2_" + name + ".bed"));
						s.nPosDuplexQualityQ2OthersQ1Q2.addPredicate("breakdown_" + f.getName(), filter, counter);
					});
				} catch (Exception e) {
					throw new RuntimeException("Problem setting up BED file " + bedPath, e);
				}
			}
		});//End parallel loop over analyzers

		groupSettings.mutationsToAnnotate.clear();
		if (param.annotateMutationsInFile != null) {
			Set<String> unknownSampleNames = new TreeSet<>();
			groupSettings.mutationsToAnnotate.putAll(MutationListReader.readMutationList(
				param.annotateMutationsInFile, param.annotateMutationsInFile, contigNames, param.referenceGenomeShortName,
				sampleNames, unknownSampleNames));
			if (!unknownSampleNames.isEmpty()) {
				printUserMustSeeMessage("Warning: unrecognized sample names in annotateMutationsInFile " +
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

		final @NonNull Histogram dubiousOrGoodDuplexCovInAllInputs = new Histogram(500);
		final @NonNull Histogram goodDuplexCovInAllInputs = new Histogram(500);

		final List<List<AnalysisChunk>> analysisChunks = new ArrayList<>();

		SignalProcessor infoSignalHandler = signal -> {
			final PrintStream printStream = (signal == null) ? out : err;
			for (Mutinack analyzer: analyzers) {
				analyzer.printStatus(printStream, signal != null);
				final int processingThroughput = analyzer.getProcessingThroughput(analysisChunks);
				if (processingThroughput != -1) {
					printStream.println("Processed at " + processingThroughput + " records / s");
				} else {
					printStream.println("Processing not yet started");
				}
			}
			printStream.flush();

			if (signal != null) {
				groupSettings.statusUpdate();
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

			final List<Future<?>> futures = new ArrayList<>();
			futures.add(StaticStuffToAvoidMutating.getExecutorService().submit(() ->
				outputJSON(param, analyzers)));
			if (!param.outputToDatabaseURL.isEmpty()) {
				futures.add(StaticStuffToAvoidMutating.getExecutorService().submit(() ->
					DatabaseOutput0.outputToDatabase(param, analyzers)));
			}
			if (!param.outputSerializedTo.isEmpty()) {
				futures.add(StaticStuffToAvoidMutating.getExecutorService().submit(() -> {
					RunResult root = getRunResult(param, analyzers);
					try (OutputStream fos = param.outputSerializedTo.equals("/dev/null") ?
							NullOutputStream.NULL_OUTPUT_STREAM
						:
							new FileOutputStream(param.outputSerializedTo)) {
						ObjectOutputStream oos = new ObjectOutputStream(fos);
						oos.writeObject(root);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}));
			}
			futures.forEach(Procedures.throwing(Future::get));
		};
		Signals.registerSignalProcessor("INFO", infoSignalHandler);
		closeableCloser.add(new CloseableWrapper<>(infoSignalHandler,
			sh -> Signals.removeSignalProcessor("INFO", infoSignalHandler)));

		statusLogger.info("Starting sequence analysis");

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

		@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
		final List<Phaser> phasers = new ArrayList<>();
		for (final String contigName: contigNamesToProcess) {
			final int contigIndex = Objects.requireNonNull(
				groupSettings.getIndexContigNameReverseMap().get(contigName));

			final int startContigAtPosition;
			final int terminateContigAtPosition;
			int idx = truncateAtContigs.indexOf(contigName);
			if (idx > -1) {
				terminateContigAtPosition = truncateAtPositions.get(idx);
			} else {
				terminateContigAtPosition =
					Objects.requireNonNull(contigSizes.get(contigName)) - 1;
			}
			idx = startAtContigs.indexOf(contigName);
			if (idx > -1) {
				startContigAtPosition = startAtPositions.get(idx);
			} else {
				startContigAtPosition = 0;
			}

			if (startContigAtPosition > terminateContigAtPosition + 1) {
				throw new IllegalArgumentException("Misordering of start and stop positions for contig " +
					contigName + ": " + startContigAtPosition + " > " + terminateContigAtPosition + " + 1");
			}

			final int contigParallelizationFactor = getContigParallelizationFactor(
				contigIndex, param, Objects.requireNonNull(contigSizes.get(contigNames.get(contigIndex))));
			List<AnalysisChunk> contigAnalysisChunks = new ArrayList<>();
			analysisChunks.add(contigAnalysisChunks);

			final int subAnalyzerSpan = (terminateContigAtPosition - startContigAtPosition + 1) /
				contigParallelizationFactor;
			for (int p = 0; p < contigParallelizationFactor; p++) {
				final AnalysisChunk analysisChunk = new AnalysisChunk(
					param.referenceGenomeShortName,
					Objects.requireNonNull(contigNames.get(contigIndex)), nParameterSets, groupSettings);
				contigAnalysisChunks.add(analysisChunk);

				final int startSubAt = startContigAtPosition + p * subAnalyzerSpan;
				final int terminateAtPosition = (p == contigParallelizationFactor - 1) ?
					terminateContigAtPosition
					: startSubAt + subAnalyzerSpan - 1;

				Assert.isTrue(terminateAtPosition >= startSubAt - 1);
				analysisChunk.contig = contigIndex;
				analysisChunk.startAtPosition = startSubAt;
				analysisChunk.lastProcessedPosition = analysisChunk.startAtPosition - 1;
				analysisChunk.terminateAtPosition = terminateAtPosition;

				analyzers.forEach(a -> {
					final SubAnalyzer subAnalyzer = new SubAnalyzer(Objects.requireNonNull(a));
					a.subAnalyzers.add(subAnalyzer);
					analysisChunk.subAnalyzers.add(subAnalyzer);
				});

				final SubAnalyzerPhaser phaser = new SubAnalyzerPhaser(
					param,
					analysisChunk,
					!param.outputAlignmentFile.isEmpty(),
					groupSettings.forceOutputAtLocations,
					dubiousOrGoodDuplexCovInAllInputs,
					goodDuplexCovInAllInputs,
					contigNames.get(contigIndex),
					contigIndex,
					excludeBEDs,
					repetitiveBEDs,
					groupSettings.PROCESSING_CHUNK);
				analysisChunk.phaser = phaser;
				phaser.bulkRegister(analyzers.size());
				phasers.add(phaser);
			}//End parallelization loop over analysisChunks
		}//End loop over contig index

		if (contigThreadPool == null) {
			synchronized(Mutinack.class) {
				if (contigThreadPool == null) {
					contigThreadPool = Executors.newFixedThreadPool(param.maxParallelContigs,
						new NamedPoolThreadFactory("main_contig_thread_"));
				}
			}
		}
		@SuppressWarnings("StaticVariableUsedBeforeInitialization")
		final ParFor parFor = new ParFor(0, contigNamesToProcess.size() - 1, null, contigThreadPool, true);
		parFor.setName("contig loop");

		for (int worker = 0; worker < parFor.getNThreads(); worker++)
			parFor.addLoopWorker((final int loopIndex, final int threadIndex) -> {

				final String contigName = contigNamesToProcess.get(loopIndex);
				final int contigIndex = contigNames.indexOf(contigName);

				final int contigParallelizationFactor = getContigParallelizationFactor(
					contigIndex, param, Objects.requireNonNull(contigSizes.get(contigName)));

				final List<Future<?>> futures = new ArrayList<>();
				int analyzerIndex = -1;

				for (Mutinack analyzer: analyzers) {
					analyzerIndex++;

					for (int p = 0; p < contigParallelizationFactor; p++) {
						final AnalysisChunk analysisChunk = analysisChunks.get(loopIndex).
							get(p);
						final SubAnalyzer subAnalyzer = analysisChunk.subAnalyzers.get(analyzerIndex);

						final String savedThreadName = Thread.currentThread().getName();
						Runnable r = () -> {
							Thread.currentThread().setName(analysisChunk.contigName + ' ' + analysisChunk.startAtPosition +
								' ' + analyzer.name);
							try {
								ReadLoader.load(analyzer, analyzer.getParam(), groupSettings,
									subAnalyzer, analysisChunk, groupSettings.PROCESSING_CHUNK,
									contigNames,
									contigIndex, sharedAlignmentWriter,
									StaticStuffToAvoidMutating::getContigSequence);
							} catch (Exception e) {
								//noinspection StatementWithEmptyBody
								if (groupSettings.errorCause == null) {
									throw e;
								} else {
									//Eat exceptions that are secondary
									//to avoid a long, useless list being reported to the user
								}
							} finally {
								Thread.currentThread().setName(savedThreadName);
							}
						};
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

				if (groupSettings.errorCause != null) {
					gatherer.add(groupSettings.errorCause);
				}

				gatherer.throwIfPresent();

				for (int p = 0; p < contigParallelizationFactor; p++) {
					final AnalysisChunk analysisChunk = analysisChunks.get(loopIndex).
						get(p);
					Assert.noException(
						() -> analysisChunk.subAnalyzers.forEach(sa -> {
							sa.checkAllDone();
							analyzers.forEach(a -> {
								boolean found = false;
								for (int i = 0; i < a.subAnalyzers.size(); i++) {
									//noinspection ObjectEquality
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
			printUserMustSeeMessage("Analysis of samples " +
				analyzers.stream().map(a -> a.name).collect(Collectors.joining(", ")) +
				" completed on host " + StaticStuffToAvoidMutating.hostName +
				" at " + new SimpleDateFormat("E dd MMM yy HH:mm:ss").format(new Date()) +
				" (elapsed time " +
				Util.shortLengthFloatFormatter.get().format((System.nanoTime() - analyzers.get(0).timeStartProcessing) /
					60_000_000_000d) + " min; " +
				"processing throughput: " + analyzers.stream().mapToInt(a -> a.getProcessingThroughput(analysisChunks)).sum() +
				" records / s)");
			ManagementFactory.getGarbageCollectorMXBeans().forEach(gc ->
				printUserMustSeeMessage(gc.getName() + " (" +
					Arrays.toString(gc.getMemoryPoolNames()) + "): " + gc.getCollectionCount() +
					' ' + gc.getCollectionTime()));
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
						groupSettings.getIndexContigNameReverseMap().get(e.getKey())));
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
						writer.write(e.getKey() + '\t' + (e.getValue() / averageCoverage) + '\t' +
							(e.getValue() / percentileCoverage95) + '\t' + (e.getValue() / percentileCoverage80) + '\n');
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

	private int getProcessingThroughput(List<List<AnalysisChunk>> analysisChunks) {
		//noinspection ObjectEquality
		List<AnalysisChunk.ProcessingStats> chunkStats =
			analysisChunks.stream().flatMap(Collection::stream).
			flatMap(ac -> ac.processingStats.entrySet().stream()).
			filter(e -> e.getKey().analyzer == this).
			map(Entry::getValue).
			collect(Collectors.toList());
		OptionalLong timeFirstStart = chunkStats.stream().
			mapToLong(s -> s.timeStarted).
			filter(time -> time > 0).min();
		long nRecords = chunkStats.stream().
			mapToLong(s -> s.nRecordsProcessed).
			sum();
		if (timeFirstStart.isPresent()) {
			long timeDelta = Math.max(1, (System.nanoTime() - timeFirstStart.getAsLong()) / 1_000_000_000L);
			return (int) (nRecords / timeDelta);
		} else {
			return -1;
		}

	}

	private static int getContigParallelizationFactor(int contigIndex, Parameters param, int contigSize) {
		List<Integer> factorList = param.contigByContigParallelization;
		final int result;
		if (!factorList.isEmpty()) {
			result = factorList.get(Math.min(factorList.size() - 1, contigIndex));
		} else {
			if (contigSize > param.noParallelizationOfContigsBelow) {
				result = param.parallelizationFactor;
			} else {
				result = 1;
			}
		}
		return result;
	}

	public static RunResult getRunResult(Parameters param, Collection<Mutinack> analyzers) {
		RunResult root = new RunResult();
		final String mutinackVersion = GitCommitInfo.getGitCommit();
		root.mutinackVersion = mutinackVersion;
		root.parameters = param;
		root.samples = analyzers.stream().map(a -> new ParedDownMutinack(a, a.startDate, new Date(),
					a.getParam().runBatchName, a.getParam().runName)).
				collect(Collectors.toList());
		analyzers.forEach(Actualizable::actualize);
		analyzers.stream().flatMap(a -> a.subAnalyzers.stream()).
			filter(Objects::nonNull).
			map(sa -> sa.stats).
			filter(Objects::nonNull).
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
				File originalOutputFile = new File(param.outputJSONTo);
				String parentDirectory = originalOutputFile.getParent();
				mapper.writerWithDefaultPrettyPrinter().writeValue(
						new File((parentDirectory == null ? "" : (parentDirectory + '/')) +
							param.jsonFilePathExtraPrefix + originalOutputFile.getName()), root);
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
		printUserMustSeeMessage(baseMessage + "; keeping going anyway");
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

	public static final ThreadLocal<NumberFormat> mutationRateFormatter
		= ThreadLocal.withInitial(() -> {
			DecimalFormat f = new DecimalFormat("0.###E0");
			DoubleAdderFormatter.setNanAndInfSymbols(f);
			return f;
		});

	@Override
	public String toString() {
		return name + " (" + inputBam.getAbsolutePath() + ')';
	}

	@Override
	public void actualize() {
		stats.forEach(Actualizable::actualize);
	}

	public @NonNull MutinackGroup getGroupSettings() {
		return groupSettings;
	}

	public Parameters getParam() {
		return param;
	}

}
