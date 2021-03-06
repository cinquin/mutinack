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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.jdt.annotation.NonNull;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.WrappedParameter;
import com.beust.jcommander.converters.BaseConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.FieldIteration;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel;

@SuppressWarnings({"DefaultAnnotationParam", "StringConcatenationInLoop"})
@PersistenceCapable
public final class Parameters implements Serializable, Cloneable {

	public void automaticAdjustments() {
		if (outputAlignmentFile.isEmpty()) {
			logReadIssuesInOutputBam = false;
		}
		if (lookForRearrangements) {
			fetchDistantMates = true;
		}
		ignoreContigsContaining = ignoreContigsContaining.stream().
			map(String::toUpperCase).collect(Collectors.toList());
	}

	public void validate() {
		try {
			validate1();
		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Problem while validating arguments " + commandLine, e);
		}
	}

	private void validate1() {
		if (parallelizationFactor != 1 && !contigByContigParallelization.isEmpty()) {
			throw new IllegalArgumentException("Cannot use parallelizationFactor and "
				+ "contigByContigParallelization at the same time");
		}

		if (ignoreFirstNBasesQ2 < ignoreFirstNBasesQ1) {
			throw new IllegalArgumentException("Parameter ignoreFirstNBasesQ2 must be greater than ignoreFirstNBasesQ1");
		}

		final int nMaxDupArg = maxNDuplexes.size();
		if (nMaxDupArg > 1 && nMaxDupArg < inputReads.size()) {
			throw new IllegalArgumentException("maxNDuplexes must be specified once, once for each input file, or not at all");
		}

		final OutputLevel[] d = OutputLevel.values();
		if (verbosity < 0 || verbosity >= d.length) {
			throw new IllegalArgumentException("Invalid verbosity " + verbosity + "; must be >= 0 and < " + d.length);
		}

		if (inputReads.isEmpty() && startServer == null && startWorker == null && !help && !version) {
			throw new IllegalArgumentException("No input reads specified");
		}

		for (String ir: inputReads) {
			if (ir.endsWith(".bai")) {
				throw new IllegalArgumentException("Unexpected .bai extension in input read path " + ir);
			}
		}

		if (clipPairOverlap && !collapseFilteredReads) {
			throw new IllegalArgumentException("-clipPairOverlap requires -collapseFilteredReads");
		}

		switch(candidateQ2Criterion) {
			case "1Q2Duplex":
				String baseErrorMessage = " only valid when candidateQ2Criterion==NQ1Duplexes";
				throwIAEIfFalse(minQ1Duplexes == Integer.MAX_VALUE, "Option minQ1Duplexes" + baseErrorMessage);
				throwIAEIfFalse(minTotalReadsForNQ1Duplexes == Integer.MAX_VALUE, "Option minTotalReadsForNQ1Duplexes" + baseErrorMessage);
				break;
			case "NQ1Duplexes":
				String baseErrorMessage1 = " must be set when candidateQ2Criterion==NQ1Duplexes";
				throwIAEIfFalse(minQ1Duplexes != Integer.MAX_VALUE, "Option minQ1Duplexes " + baseErrorMessage1);
				throwIAEIfFalse(minTotalReadsForNQ1Duplexes != Integer.MAX_VALUE, "Option minTotalReadsForNQ1Duplexes " + baseErrorMessage1);

				throwIAEIfFalse(minQ2DuplexesToCallMutation == 1, "Option minQ2DuplexesToCallMutation only valid when candidateQ2Criterion==1Q2Duplex");
				break;
			default:
				throw new RuntimeException("Option candidateQ2Criterion must be one of 1Q2Duplex or NQ1Duplexes, not "
					+ candidateQ2Criterion);
		}

		checkNoDuplicates();

		for (String p: exploreParameters) {
			String [] split = p.split(":");
			if (split.length != 4 && split.length != 3 && split.length != 1) {
				throw new IllegalArgumentException("exploreParameters argument should be formatted as " +
					"name:min:max[:n_steps] or name, but " + (split.length - 1) + " columns found in " + p);
			}
			final String paramName = split[0];
			final Field f;
			try {
				f = Parameters.class.getDeclaredField(paramName);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException("Unknown parameter " + paramName, e);
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			}
			if (f.getAnnotation(OnlyUsedAfterDuplexGrouping.class) == null &&
					f.getAnnotation(UsedAtDuplexGrouping.class) == null) {
				throw new IllegalArgumentException("Parameter " + paramName + " does not explicitly support exploration");
			}
			if (computeRawMismatches && f.getAnnotation(ExplorationIncompatibleWithRawMismatches.class) != null) {
				throw new IllegalArgumentException("Please turn computeRawMismatches off to explore " + paramName);
			}
			final Object value;
			try {
				value = f.get(this);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			if (!(value instanceof Integer) && !(value instanceof Float) && !(value instanceof Boolean)) {
				throw new IllegalArgumentException("Parameter " + paramName + " is not a number or boolean");
			}
		}

		if (minMappingQIntersect.size() != intersectAlignment.size()) {
			throw new IllegalArgumentException(
				"Lists given in minMappingQIntersect and intersectAlignment must have same length");
		}

		if (reportBreakdownForBED.size() != saveBEDBreakdownToPathPrefix.size()) {
			throw new IllegalArgumentException("Arguments -reportBreakdownForBED and " +
				"-saveBEDBreakdownToPathPrefix must appear same number of times");
		}

		checkValues();

		if (!ignoreExtraneousParameters) {
			checkOnlyValidIfAnnotations();
		}
	}

	private void checkOnlyValidIfAnnotations() {
		FieldIteration.iterateFields((field, obj) -> {
			OnlyValidWithNonEmpty checkAnnotation = field.getAnnotation(OnlyValidWithNonEmpty.class);
			if (checkAnnotation == null || obj == null) {
				return;
			}
			if (obj.equals(field.get(defaultValues))) {
				return;
			}
			try {
				final Field referredTo = Parameters.class.getField(checkAnnotation.nonEmptyList());
				referredTo.setAccessible(true);
				if (((List<?>) referredTo.get(this)).isEmpty()) {
					throw new IllegalArgumentException("Parameter " + field.getName() +
						" is set to " + obj.toString() +
						" but should only be set in conjunction with " + referredTo.getName());
				}
			} catch (NoSuchFieldException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}, this);
	}

	private void checkValues() {
		FieldIteration.iterateFields((field, obj) -> {
			CheckValues checkAnnotation = field.getAnnotation(CheckValues.class);
			if (checkAnnotation == null || obj == null) {
				return;
			}
			String [] strings = checkAnnotation.permissibleStrings();
			if (strings.length > 0) {
				Assert.isTrue(obj instanceof String);
				List<String> stringsList = Arrays.asList(strings);
				if (!stringsList.contains(obj)) {
					throw new IllegalArgumentException("Parameter " + field.getName() +
						" must be one of " + stringsList + " but found " + obj);
				}
			}
			Float min = checkAnnotation.min();
			if (!Float.isNaN(min)) {
				Assert.isTrue(obj instanceof Float);
				if ((Float) obj < min) {
					throw new IllegalArgumentException("Parameter " + field.getName() +
						" must be at least " + min + " but found " + obj);
				}
			}
			Float max = checkAnnotation.max();
			if (!Float.isNaN(max)) {
				Assert.isTrue(obj instanceof Float);
				if ((Float) obj > max) {
					throw new IllegalArgumentException("Parameter " + field.getName() +
						" must be at most " + max + " but found " + obj);
				}
			}
		}, this);
	}

	private static void throwIAEIfFalse(boolean b, String message) {
		if (!b) {
			throw new IllegalArgumentException(message);
		}
	}

	private void checkNoDuplicates() {
		FieldIteration.iterateFields((field, obj) -> {
			if (field.getAnnotation(NoDuplicates.class) == null || obj == null) {
				return;
			}
			@SuppressWarnings("unchecked")
			Collection<Object> col = (Collection<Object>) obj;
			final Set<Object> set = new HashSet<>();
			for (Object o: col) {
				if (!set.add(o)) {
					throw new IllegalArgumentException("Can specify each argument at most once for " + field.getName() +
						" but " + o + " is specified more than once");
				}
			}
		}, this);
	}

	public static boolean isUsedAtDuplexGrouping(String key) {
		try {
			Field f = Parameters.class.getDeclaredField(key);
			return f.getAnnotation(UsedAtDuplexGrouping.class) != null;
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

	@HideInToString
	@JsonIgnore
	@IgnoreInHashcodeEquals
	public String commandLine;

	@HideInToString
	@JsonIgnore
	@IgnoreInHashcodeEquals
	public transient MutinackGroup group;

	@IgnoreInHashcodeEquals
	public Map<String, Object> distinctParameters = new HashMap<>();

	public static final long serialVersionUID = 1L;
	@JsonIgnore
	private static final boolean hideInProgressParameters = true;
	@JsonIgnore
	private static final boolean hideAdvancedParameters = true;

	@Parameter(names = {"-help", "--help"}, help = true, description = "Display this message and return")
	@HideInToString
	@JsonIgnore
	public boolean help;

	@Parameter(names = {"-version", "--version"}, help = true, description = "Display version information and return")
	@HideInToString
	@JsonIgnore
	public boolean version;

	@Parameter(names = "-noStatusMessages", description = "Do not output any status information on stderr or stdout", required = false)
	@HideInToString
	@IgnoreInHashcodeEquals
	public boolean noStatusMessages = false;

	@Parameter(names = "-skipVersionCheck", description = "Do not check whether update is available for download", required = false)
	@HideInToString
	@JsonIgnore
	@IgnoreInHashcodeEquals
	public boolean skipVersionCheck = false;

	@Parameter(names = "-ignoreExtraneousParameters", description = "When false, presence of parameters that are not expected to be specified generates an error", required = false, arity = 1)
	@HideInToString
	@JsonIgnore
	@IgnoreInHashcodeEquals
	public boolean ignoreExtraneousParameters = true;

	@Parameter(names = "-verbosity", description = "0: main mutation detection results only; 3: open the firehose", required = false)
	public int verbosity = 0;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-suppressStderrOutput", description = "Only partially implemented so far", required = false)
	public boolean suppressStderrOutput = false;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-runBatchName", description = "User-defined string to identify batch to which this run belongs",
		required = false)
	public String runBatchName = "";

	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputToDatabaseURL", description = "Formatted e.g. as jdbc:postgresql://localhost/mutinack_test_db",
		required = false)
	public String outputToDatabaseURL = "jdbc:postgresql://localhost/mutinack_test_db";

	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputToDatabaseUserName", description = "",
		required = false)
	public String outputToDatabaseUserName = "testuser3";

	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputToDatabaseUserPassword", description = "",
		required = false)
	public @NotPersistent String outputToDatabaseUserPassword = "testpassword34";

	@FilePath
	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputJSONTo", description = "Path to which JSON-formatted output should be written",
		required = false)
	public @Column(length = 1_000) String outputJSONTo = "";

	@FilePath
	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputSerializedTo", description = "Path to which serialized Java object output should be written",
		required = false)
	public @Column(length = 1_000) String outputSerializedTo = "";

	@Parameter(names = "-outputDuplexDetails", description = "For each reported mutation, give list of its reads and duplexes", required = false)
	public boolean outputDuplexDetails = false;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-parallelizationFactor", description = "Number of chunks into which to split each contig for parallel processing; setting this value too high can be highly counter-productive", required = false)
	public int parallelizationFactor = 1;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-noParallelizationOfContigsBelow", description = "Processing of contigs below this size will not be parallelized", required = false)
	public int noParallelizationOfContigsBelow = 200_000;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-contigByContigParallelization", description = "Contig-by-contig list of number of chunks into which to split contig for parallel processing; setting this value too high can be highly counter-productive; last value in the list applies to all contigs whose index falls outside of the list", required = false)
	public List<Integer> contigByContigParallelization = new ArrayList<>();

	@IgnoreInHashcodeEquals
	@Parameter(names = "-maxThreadsPerPool", description = "Maximum number of threads per pool;" +
		" for now, to avoid deadlocks this number should be kept higher than number of inputs *" +
		" number of contigs * parallelization factor", required = false)
	public int maxThreadsPerPool = 64;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-maxParallelContigs", description = "JVM-wide maximum number of concurrently analyzed contigs; first call sets final value", required = false)
	public int maxParallelContigs = 30;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-hashMapLoadFactor", description = "TODO", required = false)
	public float hashMapLoadFactor = 0.5f;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-terminateImmediatelyUponError", description = "If true, any error causes immediate termination of the run", required = false)
	public boolean terminateImmediatelyUponError = true;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-terminateUponOutputFileError", description = "If true, any error in writing auxiliary output files causes termination of the run", required = false)
	public boolean terminateUponOutputFileError = true;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-processingChunk", description = "Size of sliding windows used to synchronize analysis in different samples", required = false)
	public int processingChunk = 160;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-inputReads", description = "Input BAM read file, sorted and with an index; repeat as many times as there are samples", required = true)
	public List<@NonNull String> inputReads = new ArrayList<>();

	@Parameter(names = "-computeHashForBAMSmallerThanInGB", description = "A simple hash will be computed for all input BAM files whose size is below specified threshold (in GB)", required = false)
	public float computeHashForBAMSmallerThanInGB = 0.5f;

	@Parameter(names = "-lenientSamValidation", description = "NOW IGNORED; use -samValidation instead. Passed to Picard; seems at least sometimes necessary for" +
		" alignments produced by BWA", required = false, hidden = true)
	public boolean lenientSamValidation = true;

	@CheckValues(permissibleStrings = {"none", "warning", "error"})
	@Parameter(names = "-samValidation", description = "Possible values: none, warning, or error; passed to Picard; errors on some" +
		" alignments produced by BWA", required = false)
	public String samValidation = "warning";

	@Parameter(names = "-allowMissingSupplementaryFlag", description = "", required = false)
	public boolean allowMissingSupplementaryFlag = false;

	@Parameter(names = "-ignoreMultipleAlignments", description = "Ignore multiple alignments of the same read that start at the same position (TODO: process them correctly)", required = false)
	public boolean ignoreMultipleAlignments = false;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-originalReadFile1", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile1 = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-originalReadFile2", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile2 = new ArrayList<>();

	@Parameter(names = "-nRecordsToProcess", description = "Only process first N reads", required = false)
	public long nRecordsToProcess = Long.MAX_VALUE;

	@Parameter(names = "-dropReadProbability", description = "Reads will be randomly ignored with a probability given by this number")
	public float dropReadProbability = 0;

	@Parameter(names = "-randomizeMates", description = "Randomize first/second of pair; WARNING: this will lead to incorrect top/bottom strand grouping")
	public boolean randomizeMates = false;

	@UsedAtDuplexGrouping
	@Parameter(names = "-randomizeStrand", description = "Randomize read mapping to top or bottom strand, preserving for each duplex" +
		" the number in the top strand and the number in the bottom strand; WARNING: this will lead to incorrect mutation and disagreement detection")
	public boolean randomizeStrand = false;

	@UsedAtDuplexGrouping
	@Parameter(names = "-forceDuplexGroupingByBame", required = false, arity = 1, description = "Reads with the same name are forced into the same"
		+ " duplex; this can be useful for dealing with split reads, but has a performance impact")
	public boolean forceDuplexGroupingByBame = true;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-intersectAlignment", description = "List of BAM files with which alignments in inputReads must agree; each file must be sorted", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> intersectAlignment = new ArrayList<>();

	@Parameter(names = "-minMappingQIntersect", description = "Minimum mapping quality for reads in intersection files", required = false, hidden = hideInProgressParameters)
	public List<Integer> minMappingQIntersect = new ArrayList<>();

	@FilePath
	@Parameter(names = "-referenceGenome", description = "Reference genome in FASTA format; index file must be present",
		required = true)
	public @NonNull @Column(length = 1_000) String referenceGenome = "";

	@Parameter(names = "-referenceGenomeShortName", description = "e.g. ce10, hg19, etc.", required = true)
	public @NonNull String referenceGenomeShortName = "";

	@Parameter(names = "-contigNamesToProcess", description =
			"Reads not mapped to any of these contigs will be ignored")
	@NoDuplicates
	@NonNull List<@NonNull String> contigNamesToProcess = new ArrayList<>();

	@Parameter(names = "-ignoreContigsContaining", description =
		"Contigs whose name contains one of these strings will be ignored (unless explicitly specified with -contigNamesToProcess); " +
			"matching is case insensitive",
		required = false)
	public @NonNull List<@NonNull String> ignoreContigsContaining = Arrays.asList("decoy", "_alt", "_random", "HLA-", "chrUn_");

	@Parameter(names = "-startAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
			converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	public List<@NonNull String> startAtPositions = new ArrayList<>();

	@Parameter(names = "-stopAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
			converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	public List<@NonNull String> stopAtPositions = new ArrayList<>();

	@Parameter(names = "-readContigsFromFile", description = "OBSOLETE; kept for backward compatibility", hidden = true)
	public boolean readContigsFromFile = false;

	@Parameter(names = "-traceField", description = "Output each position at which" +
		" specified statistic is incremented; formatted as sampleName:statisticName", required = false)
	@NoDuplicates
	public List<String> traceFields = new ArrayList<>();

	@Parameter(names = "-tracePositions", description = "Log details of mutations read at specified positions", required = false,
		converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	@NoDuplicates
	public List<@NonNull String> tracePositions = new ArrayList<>();
	public final List<SequenceLocation> parsedTracePositions = new ArrayList<>();

	@Parameter(names = "-contigStatsBinLength", description = "Length of bin to use for statistics that" +
		" are broken down more finely than contig by contig", required = false)
	public int contigStatsBinLength = 2_000_000;

	@Parameter(names = "-reportCoverageAtAllPositions", description = "Report key coverage statistics at every position analyzed; do not use when analyzing large regions!", arity = 1)
	public boolean reportCoverageAtAllPositions = false;

	@Parameter(names = "-minMappingQualityQ1", description = "Reads whose mapping quality is below this" +
		" threshold are discarded (best to keep this relatively low to allow non-unique mutation candidates to be identified in all samples)", required = false)
	public int minMappingQualityQ1 = 20;

	@Parameter(names = "-minMappingQualityQ2", description = "Reads whose mapping quality is below this" +
		" threshold are not used to propose Q2 mutation candidates", required = false)
	@OnlyUsedAfterDuplexGrouping
	@ExplorationIncompatibleWithRawMismatches
	public int minMappingQualityQ2 = 50;

	@Parameter(names = "-minReadsPerStrandQ1", description = "Duplexes that have fewer reads for the" +
		" original top and bottom strands are ignored when calling substitutions or indels", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minReadsPerStrandQ1 = 0;

	@Parameter(names = "-minReadsPerStrandQ2", description = "Only duplexes that have at least this number of reads" +
		" for original top and bottom strands can contribute Q2 candidates", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minReadsPerStrandQ2 = 3;

	@Parameter(names = "-minReadsPerDuplexQ2", description = "Only duplexes that have at least this total number of reads" +
		" (irrespective of whether they come from the original top and bottom strands) can contribute Q2 candidates", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minReadsPerDuplexQ2 = 3;

	@Parameter(names = "-candidateQ2Criterion", description = "Must be one of 1Q2Duplex, NQ1Duplexes", required = false)
	@OnlyUsedAfterDuplexGrouping
	public String candidateQ2Criterion = "1Q2Duplex";

	@Parameter(names = "-minQ1Duplexes", description = "If candidateQ2Criterion is set to NQ1Duplexes, allow mutation candidate to be Q2 if it has at least this many Q1 duplexes", required = false, hidden = true)
	@OnlyUsedAfterDuplexGrouping
	public int minQ1Duplexes = Integer.MAX_VALUE;

	@Parameter(names = "-minTotalReadsForNQ1Duplexes", description = "If candidateQ2Criterion is set to NQ1Duplexes, allow mutation candidate to be Q2 only if it has at least this many supporting reads", required = false, hidden = true)
	@OnlyUsedAfterDuplexGrouping
	public int minTotalReadsForNQ1Duplexes = Integer.MAX_VALUE;

	/*@Parameter(names = "-promoteNSingleStrands", description = "Not yet functional, and probably never will be - Promote duplex that has just 1 original strand but at least this many reads to Q1", required = false, hidden = true)
	public int promoteNSingleStrands = Integer.MAX_VALUE;

	@Parameter(names = "-promoteFractionReads", description = "Promote candidate supported by at least this fraction of reads to Q2", required = false, hidden = hideAdvancedParameters)
	public float promoteFractionReads = Float.MAX_VALUE;*/

	@Parameter(names = "-minConsensusThresholdQ1", description = "Lenient value for minimum fraction of reads from the same" +
		" original strand that define a consensus (must be > 0.5)", required = false)
	@OnlyUsedAfterDuplexGrouping
	public float minConsensusThresholdQ1 = 0.51f;

	@Parameter(names = "-minConsensusThresholdQ2", description = "Strict value for minimum fraction of reads from the same" +
		" original strand that define a consensus (must be > 0.5)", required = false)
	@OnlyUsedAfterDuplexGrouping
	public float minConsensusThresholdQ2 = 0.95f;

	@Parameter(names = "-disagreementConsensusThreshold", description = "NOT YET IMPLEMENTED; Disagreements are only reported if for each strand" +
		" consensus is above this threshold, in addition to being above minConsensusThresholdQ2", required = false, hidden = true)
	public float disagreementConsensusThreshold = 0.0f;

	@Parameter(names = "-minReadsPerStrandForDisagreement", description = "Minimal number of reads" +
		" for original top and bottom strands to examine duplex for disagreement between these strands", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minReadsPerStrandForDisagreement = 0;

	@Parameter(names = "-Q2DisagCapsMatchingMutationQuality", description = "Q2 disagreement in the same sample or in sister sample caps to Q1 the quality of matching, same-position mutations from other duplexes", required = false, arity = 1)
	@OnlyUsedAfterDuplexGrouping
	public boolean Q2DisagCapsMatchingMutationQuality = true;

	@CheckValues(min = 0, max = 1)
	@Parameter(names = "-maxMutFreqForDisag", description = "Disagreements are marked as Q0 if the frequency of matching mutation candidates at the same position is greater than this threshold; " +
		"note that this parameter does NOT affect coverage", required = false, arity = 1)
	@OnlyUsedAfterDuplexGrouping
	public float maxMutFreqForDisag = 1.0f;

	@Parameter(names = "-computeRawMismatches", description = "Compute mismatches between raw reads and reference sequence", arity = 1, required = false)
	public boolean computeRawMismatches = true;

	@Parameter(names = "-minTopAlleleFrequencyForDisagreement", description = "Disagreements will only be reported at positions where the top allele is present at least at this frequency", arity = 1, required = false)
	public float minTopAlleleFrequencyForDisagreement = 0f;

	@Parameter(names = "-rawMismatchesOnlyAtWtPos", description = "Obsolete and ignored", required = false, arity = 1)
	public boolean rawMismatchesOnlyAtWtPos;

	@Parameter(names = "-computeIntraStrandMismatches", description = "Compute mismatches between reads that belong to the same duplex strand", arity = 1, required = false)
	public boolean computeIntraStrandMismatches = false;

	@Parameter(names = "-intraStrandMismatchReportingThreshold", description = "If top candidate is less than this fraction of the reads of a duplex, repeated intra-strand mismatches are recorded", arity = 1, required = false)
	@OnlyUsedAfterDuplexGrouping
	public float intraStrandMismatchReportingThreshold = 1.0f;

	@CheckValues(min = 0, max = 1)
	@Parameter(names = "-topAlleleFreqReport", description = "Sites at which the top allele frequency is below this value are reported and marked with a % sign", required = false)
	public float topAlleleFreqReport = 0.3f;

	@CheckValues(min = 0, max = 1)
	@Parameter(names = "-minTopAlleleFreqQ2", description = "Only positions where the frequency of the top allele is at least this high can contribute Q2 candidates", required = false)
	public float minTopAlleleFreqQ2 = 0;

	@CheckValues(min = 0, max = 1)
	@Parameter(names = "-maxTopAlleleFreqQ2", description = "Only positions where the frequency of the top allele is at least this low can contribute Q2 candidates", required = false)
	public float maxTopAlleleFreqQ2 = 1;

	@Parameter(names = "-minBasePhredScoreQ1", description = "Bases whose Phred score is below this threshold" +
		" are discarded (keeping this relatively low helps identify problematic reads)", required = false)
	public int minBasePhredScoreQ1 = 20;

	@Parameter(names = "-minBasePhredScoreQ2", description = "Bases whose Phred score is below this threshold are not used to propose Q2 mutation candidates",
		required = false)
	@OnlyUsedAfterDuplexGrouping
	@ExplorationIncompatibleWithRawMismatches
	public int minBasePhredScoreQ2 = 30;

	@Parameter(names = "-ignoreFirstNBasesQ1", description = "Bases that occur within this many bases of read start are discarded", required = false)
	public int ignoreFirstNBasesQ1 = 4;

	@Parameter(names = "-ignoreFirstNBasesQ2", description = "Bases that occur within this many bases of read start are not used to propose Q2 mutation candidates", required = false)
	@OnlyUsedAfterDuplexGrouping
	@ExplorationIncompatibleWithRawMismatches
	public int ignoreFirstNBasesQ2 = 35;

	@Parameter(names = "-ignoreLastNBases", description = "Potential mutations that occur within this many bases of read end are ignored", required = false)
	public int ignoreLastNBases = 4;

	@Parameter(names = "-minReadMedianPhredScore", description = "Reads whose median Phred score is below this threshold are discarded", required = false)
	public int minReadMedianPhredScore = 0;

	@Parameter(names = {"-minMedianPhredScoreAtPosition", "-minMedianPhredQualityAtPosition"}, description = "Positions whose median Phred score is below this threshold are not used to propose Q2 mutation candidates", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minMedianPhredScoreAtPosition = 0;

	@Parameter(names = "-minCandidateMedianPhredScore", description = "Mutation candidates for which the median Phred score of supporting reads at the corresponding position is below this threshold are capped at Q1", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minCandidateMedianPhredScore = 20;

	@Parameter(names = "-maxFractionWrongPairsAtPosition", description = "Positions are not used to propose Q2 mutation candidates if the fraction of reads covering the position that have an unmapped mate or a mate that forms a wrong pair orientation (RF, Tandem) is above this threshold", required = false)
	public float maxFractionWrongPairsAtPosition = 1.0f;

	@Parameter(names = "-maxAverageBasesClipped", description = "Duplexes whose mean number of clipped bases is above this threshold are not used to propose Q2 mutation candidates",
		required = false)
	@UsedAtDuplexGrouping
	@ExplorationIncompatibleWithRawMismatches
	public int maxAverageBasesClipped = 15;

	@Parameter(names = "-maxAverageClippingOfAllCoveringDuplexes", description = "Positions whose average covering duplex average number of clipped bases is above this threshold are not used to propose Q2 mutation candidates",
		required = false)
	@OnlyUsedAfterDuplexGrouping
	public int maxAverageClippingOfAllCoveringDuplexes = 999;

	@Parameter(names = "-maxConcurringDuplexClipping", description = "Duplexes for which all reads have at least this many clipped bases are not counted as concurring with a mutation",
		required = false)
	@OnlyUsedAfterDuplexGrouping
	public int maxConcurringDuplexClipping = maxAverageBasesClipped;

	@Parameter(names = "-minConcurringDuplexReads", description = "Only duplexes that have at least this many reads are counted as concurring with a mutation",
		required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minConcurringDuplexReads = 2;

	@Parameter(names = "-maxNDuplexes", description = "Positions whose number of Q1 or Q2 duplexes is above this threshold are ignored when computing mutation rates",
		required = false)
	public List<Integer> maxNDuplexes = new ArrayList<>();

	@Parameter(names = "-maxInsertSize", description = "Inserts above this size are not used to propose Q2 mutation candidates, and will most of the time be ignored when identifying Q1 candidates", required = false)
	public int maxInsertSize = 1_000;

	@Parameter(names = "-minInsertSize", description = "Inserts below this size are not used to propose Q2 mutation candidates", required = false)
	public int minInsertSize = 0;

	@Parameter(names = "-ignoreZeroInsertSizeReads", description = "Reads 0 or undefined insert size are thrown out at the onset (and thus cannot contribute to exclusion of mutation candidates found in multiple samples)", required = false)
	public boolean ignoreZeroInsertSizeReads = false;

	@Parameter(names = "-ignoreSizeOutOfRangeInserts", description = "Reads with insert size out of range are thrown out at the onset (and thus cannot contribute to exclusion of mutation candidates found in multiple samples)", required = false)
	public boolean ignoreSizeOutOfRangeInserts = false;

	@Parameter(names = "-ignoreTandemRFPairs", description = "Read pairs that form tandem or RF are thrown out at the onset", required = false)
	public boolean ignoreTandemRFPairs = false;

	@Parameter(names = "-filterOpticalDuplicates", description = "", required = false)
	public boolean filterOpticalDuplicates = false;

	@Parameter(names = "-opticalDuplicateDistance", description = "", required = false)
	public int opticalDuplicateDistance = 100;

	@Parameter(names = "-computeAllReadDistances", description = "higher computational cost", required = false)
	public boolean computeAllReadDistances = false;

	@Parameter(names = {"-minNumberDuplexesSisterArm", "-minNumberDuplexesSisterSamples"}, description = "Min number of duplexes in sister arm to call a candidate mutation unique; adjust this number to deal with heterozygous mutations", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minNumberDuplexesSisterSamples = 10;

	@Parameter(names = "-minQ2DuplexesToCallMutation", description = "Min number of Q2 duplexes to call mutation (condition set by minQ1Q2DuplexesToCallMutation must also be met)", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minQ2DuplexesToCallMutation = 1;

	@Parameter(names = "-minQ1Q2DuplexesToCallMutation", description = "Min number of Q1 or Q2 duplexes to call mutation (condition set by minQ2DuplexesToCallMutation must also be met)", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minQ1Q2DuplexesToCallMutation = 1;

	@Parameter(names = "-minQ2DuplexesToCallRearrangement", description = "Min number of Q2 duplexes to call mutation (condition set by minQ1Q2DuplexesToCallMutation must also be met)", required = false)
	@OnlyUsedAfterDuplexGrouping
	public int minQ2DuplexesToCallRearrangement = 2;

	@Parameter(names = "-lookForRearrangements", description = "higher computational cost", required = false)
	public boolean lookForRearrangements = false;

	@Parameter(names = "-fetchDistantMates", description = "high computational cost", required = false)
	public boolean fetchDistantMates = false;

	@Parameter(names = "-acceptNInBarCode", description = "If true, an N read within the barcode is" +
		" considered a match", required = false)
	public boolean acceptNInBarCode = true;

	@Parameter(names = "-variableBarcodeLength", description = "Length of variable barcode, irrespective of whether it has been removed from the aligned sequences", required = false)
	public int variableBarcodeLength = 3;

	@Parameter(names = "-constantBarcode", description = "Used to only analyze reads whose constant barcode matches expected value", required = false)
	public @NonNull String constantBarcode = "TCT";

	@Parameter(names = "-nVariableBarcodeMismatchesAllowed", description = "Used for variable barcode matching", required = false)
	public int nVariableBarcodeMismatchesAllowed = 1;

	@Parameter(names = "-nConstantBarcodeMismatchesAllowed", description = "Used for constant barcode matching", required = false)
	public int nConstantBarcodeMismatchesAllowed = 3;

	@Parameter(names = "-alignmentPositionMismatchAllowed", description = "Reads assigned to same duplex must have alignment positions match within this tolerance (see also parameter requireMatchInAlignmentEnd)", required = false)
	public int alignmentPositionMismatchAllowed = 0;

	@Parameter(names = "-requireMatchInAlignmentEnd", description = "Used while grouping reads into duplexes; turn off if alignments were aggregated from sequencing runs with different read lengths", required = false)
	public boolean requireMatchInAlignmentEnd = false;

	@Parameter(names = "-computeDuplexGroupingStats", description = "Off by default for higher performance", required = false)
	public boolean computeDuplexGroupingStats = false;

	@Parameter(names = "-computeDuplexDistances", description = "Compute pairwise distances between duplexes", arity = 1, required = false)
	public boolean computeDuplexDistances = false;

	@Parameter(names = "-computeShiftedDuplexStats", description = "TODO", arity = 1, required = false)
	public boolean computeShiftedDuplexStats = false;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-saveFilteredReadsTo", description = "Not implemented; write raw reads that were kept for analysis to specified files", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> saveFilteredReadsTo = new ArrayList<>();

	@Parameter(names = "-collapseFilteredReads", description = "Only write one (randomly-chosen) read pair per duplex strand", required = false)
	public boolean collapseFilteredReads = false;

	@Parameter(names = "-writeBothStrands", description = "Used in conjunction with -collapseFilteredReads; write read pairs from both the top and the bottom strand, when available", required = false, arity = 1)
	public boolean writeBothStrands = true;

	@OnlyValidWithNonEmpty(nonEmptyList = "outputAlignmentFile")
	@Parameter(names = "-maxSubQ2DuplexesForBAMOutput", description = "Only this many sub-Q2 duplexes will be chosen to be written out at any position (actual coverage with sub-Q2 duplexes may end up being substantially higher because, for now, different duplexes are chosen at nearby positions)", required = false)
	public int maxSubQ2DuplexesForBAMOutput = Integer.MAX_VALUE;

	@Parameter(names = "-clipPairOverlap", description = "Hard clip overlap between read pairs (currently does not add an H block to the Cigar); requires -collapseFilteredReads", required = false)
	public boolean clipPairOverlap = false;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-bamReadsWithBarcodeField", description = "Unimplemented; BAM/SAM file saved from previous run with barcodes stored as attributes", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> bamReadsWithBarcodeField = new ArrayList<>();

	@Parameter(names = "-saveRawReadsDB", description = "Not functional at present", required = false, arity = 1, hidden = hideInProgressParameters)
	public boolean saveRawReadsDB = false;

	@Parameter(names = "-saveRawReadsMVDB", description = "Not functional at present", required = false, arity = 1, hidden = hideInProgressParameters)
	public boolean saveRawReadsMVDB = false;

	@Parameter(names = "-outputCoverageBed", description = "Output bed file that gives number of duplexes covering each position in the reference sequence;" +
		" note that this is a highly-inefficient format that creates a huge file", required = false)
	public boolean outputCoverageBed = false;

	@Parameter(names = "-outputCoverageProto", description = "Output protobuf file that gives number of duplexes covering each position in the reference sequence",
		required = false)
	public boolean outputCoverageProto = false;

	/**
	 * Output section
	 */

	@NoDuplicates
	@Parameter(names = "-sampleName", description = "Used to name samples in output file; can be repeated as many times as there are inputReads", required = false)
	public List<@NonNull String> sampleNames = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = {"-forceOutputAtPositionsTextFile", "-forceOutputAtPositionsFile"}, description = "Detailed information is reported for all positions listed in the file", required = false)
	public List<@NonNull String> forceOutputAtPositionsTextFile = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-forceOutputAtPositionsBinFile", description = "Detailed information is reported for all positions listed in the file", required = false)
	public List<@NonNull String> forceOutputAtPositionsBinFile = new ArrayList<>();

	@Parameter(names = "-forceOutputAtPositions", description = "Detailed information is reported for positions given as ranges", required = false,
		converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	public @NoDuplicates List<@NonNull String> forceOutputAtPositions = new ArrayList<>();

	@FilePath
	@Parameter(names = "-annotateMutationsInFile", description = "TODO", required = false)
	public @Column(length = 1_000) String annotateMutationsInFile = null;

	@FilePath
	@Parameter(names = "-annotateMutationsOutputFile", description = "TODO", required = false)
	public @Column(length = 1_000) String annotateMutationsOutputFile = null;

	@Parameter(names = "-randomOutputRate", description = "Randomly choose genome positions at this rate to include in output", required = false)
	public float randomOutputRate = 0;

	@FilePathList
	@IgnoreInHashcodeEquals
	@Parameter(names = "-outputAlignmentFile", description = "Write BAM output with duplex information provided in custom tags;" +
		" note that a read may be omitted from the output, e.g. if it falls below a Q1 threshold (it is" +
		" relatively rare but possible for a read to be omitted even though it counts toward coverage). Specify" +
		" parameter once for all reads to go to same output file (with different AI tags), or as many times" +
		" as there are input files", required = false)
	public @NonNull @Column(length = 1_000) List<String> outputAlignmentFile = new ArrayList<>();

	@FilePath
	@Parameter(names = "-discardedReadFile", description = "Write discarded reads to BAM file specified by parameter", required = false, hidden = hideInProgressParameters)
	public @Column(length = 1_000) String discardedReadFile = null;

	//@OnlyValidWithNonEmpty(nonEmptyList = "outputAlignmentFile")
	@Parameter(names = "-logReadIssuesInOutputBam", description = "Use custom fields in output BAM to give reasons why duplexes as a whole or individual bases did not reach maximum quality", required = false, arity = 1)
	public boolean logReadIssuesInOutputBam = true;

	@OnlyValidWithNonEmpty(nonEmptyList = "outputAlignmentFile")
	@Parameter(names = "-sortOutputAlignmentFile", description = "Sort BAM file; can require a large amount of memory", required = false, arity = 1)
	public boolean sortOutputAlignmentFile = false;

	@Parameter(names = "-outputTopBottomDisagreementBED", description = "Output to file specified by option -topBottomDisagreementFileBaseName", required = false, arity = 1)
	public boolean outputTopBottomDisagreementBED = true;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-reportStatsForBED", description = "Report number of observations that fall within" +
		" the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForBED = new ArrayList<>();

	@Parameter(names = "-bedContigNameColumn", description = "", required = false, arity = 1)
	public String bedContigNameColumn = "contigName";

	@Parameter(names = "-bedEntryNameColumn", description = "", required = false, arity = 1)
	public String bedEntryNameColumn = "geneName";

	@Parameter(names = "-bedEntryStartColumn", description = "", required = false, arity = 1)
	public String bedEntryStartColumn = "XXXXXX";

	@Parameter(names = "-bedBlockLengthsColumn", description = "", required = false, arity = 1)
	public String bedBlockLengthsColumn = "XXXXXX";

	@Parameter(names = "-bedEntryEndColumn", description = "", required = false, arity = 1)
	public String bedEntryEndColumn = "XXXXXX";

	@Parameter(names = "-bedEntryOrientationColumn", description = "", required = false, arity = 1)
	public String bedEntryOrientationColumn = "geneName";

	@Parameter(names = "-bedEntryScoreColumn", description = "", required = false, arity = 1)
	public String bedEntryScoreColumn = "XXXXXX";

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-reportStatsForNotBED", description = "Report number of observations that do *not* fall within" +
		" the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForNotBED = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-excludeRegionsInBED", description = "Positions covered by this BED file will be completely ignored in the analysis", required = false)
	public List<@NonNull String> excludeRegionsInBED = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-repetiveRegionBED", description = "If specified, used for stats (mutant|wt)Q2CandidateQ1Q2DCoverage[Non]Repetitive", required = false)
	public List<@NonNull String> repetiveRegionBED = new ArrayList<>();

	@FilePath
	@Parameter(names = "-bedDisagreementOrienter", description = "Gene orientation read from this file" +
		" is used to orient top/bottom strand disagreements with respect to transcribed strand", required = false)
	public @Column(length = 1_000) String bedDisagreementOrienter = null;

	@FilePathList
	@NoDuplicates
	@Parameter(names = "-reportBreakdownForBED", description = "Report number of observations that fall within" +
		" each of the regions defined by BED file whose path follows", required = false)
	public List<@NonNull String> reportBreakdownForBED = new ArrayList<>();

	@FilePathList
	@NoDuplicates
	@Parameter(names = {"-saveBEDBreakdownToPathPrefix", "-saveBEDBreakdownTo"}, description = "Path prefix for saving of BED region counts; argument " +
		" list must match that given to -reportBreakdownForBED", required = false)
	public List<@NonNull String> saveBEDBreakdownToPathPrefix = new ArrayList<>();

	@FilePath
	@Parameter(names = "-bedFeatureSuppInfoFile", description = "Read genome annotation supplementary info, used in output of counter with BED feature breakdown")
	public @Column(length = 1_000) String bedFeatureSuppInfoFile = null;

	@FilePath
	@Parameter(names = "-refSeqToOfficialGeneName", description = "Tab separated text file with RefSeq ID, tab, and official gene name and any other useful info; " +
		"counts will be reported both by RefSeq ID and official gene name")
	public @Column(length = 1_000) String refSeqToOfficialGeneName = null;

	@FilePath
	@Parameter(names = "-auxOutputFileBaseName", description = "Base name of files to which to record mutations, disagreements between top and bottom strands, etc.", required = false)
	public @Column(length = 1_000) String auxOutputFileBaseName = null;

	public String jsonFilePathExtraPrefix = "";

	@Parameter(names = "-rnaSeq", description = "Ignore deletions and turn off checks that do not make sense for RNAseq data", required = false)
	public boolean rnaSeq = false;

	@Parameter(names = "-submitToServer", description = "RMI address", required = false, hidden = hideInProgressParameters)
	public String submitToServer = null;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-writePIDPath", description = "Write PID to this file when ready", required = false, hidden = hideInProgressParameters)
	public String writePIDPath = null;

	@Parameter(names = "-startServer", help = true, description = "RMI address", required = false, hidden = hideInProgressParameters)
	public String startServer = null;

	@Parameter(names = "-startWorker", help = true, description = "RMI server address", required = false, hidden = hideInProgressParameters)
	public String startWorker = null;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-timeoutSeconds", help = true, description = "If this many seconds elapse without ping from worker, worker is considered dead", required = false, hidden = hideInProgressParameters)
	public int timeoutSeconds = 0;

	@FilePath
	@Parameter(names = "-workingDirectory", help = true, description = "Evaluate parameter file paths using specified directory as workind directory", required = false, hidden = hideAdvancedParameters)
	public @Column(length = 1_000) String workingDirectory = null;

	@FilePath
	@Parameter(names = "-referenceOutput", description = "Path to reference output to be used for functional tests", required = false, hidden = hideAdvancedParameters)
	public @Column(length = 1_000) String referenceOutput = null;

	@FilePath
	@Parameter(names = "-recordRunsTo", description = "Get server to output a record of all runs it processed, to be replayed for functional tests", required = false, hidden = hideAdvancedParameters)
	public @Column(length = 1_000) String recordRunsTo = null;

	@Parameter(names = "-runName", description = "Name of run to be used in conjunction with -recordRunsTo", required = false, hidden = hideAdvancedParameters)
	public String runName = null;

	@Parameter(names = "-enableCostlyAssertions", description = "Enable internal sanity checks that significantly slow down execution", required = false, arity = 1)
	public boolean enableCostlyAssertions = true;

	@Parameter(names = "-jiggle", description = "Internally jiggle the data in a way that should not change the important outputs; use in combination with random seed to get reproducible jiggling", required = false)
	public boolean jiggle = false;

	@Parameter(names = "-randomSeed", description = "TODO", required = false, hidden = hideAdvancedParameters)
	public long randomSeed = new SecureRandom().nextLong();

	@IgnoreInHashcodeEquals
	@Parameter(names = "-keysFile", description = "Location of .jks file for RMI SSL encryption", required = false, hidden = hideAdvancedParameters)
	public String keysFile = "mutinack_public_selfsigned.jks";

	@IgnoreInHashcodeEquals
	@Parameter(names = "-exploreParameter", description = "Perform mutation detection separately for each parameter value specified as name:min:max[:n_steps]", required = false, hidden = true)
	public @NoDuplicates List<String> exploreParameters = new ArrayList<>();

	@IgnoreInHashcodeEquals
	@Parameter(names = "-cartesianProductOfExploredParameters", description = "", required = false, hidden = true, arity = 1)
	public boolean cartesianProductOfExploredParameters = true;

	@IgnoreInHashcodeEquals
	@Parameter(names = "-includeInsertionsInParamExploration", description = "", required = false, hidden = true, arity = 1)
	public boolean includeInsertionsInParamExploration = false;

	@Retention(RetentionPolicy.RUNTIME)
	/**
	 * Used to mark parameters that it is not useful to print in toString method.
	 * @author olivier
	 *
	 */
	public @interface HideInToString {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface IgnoreInHashcodeEquals {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface OnlyUsedAfterDuplexGrouping {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface ExplorationIncompatibleWithRawMismatches {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface UsedAtDuplexGrouping {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface NoDuplicates {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface OnlyValidWithNonEmpty {
		String nonEmptyList();
	}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface FilePath {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface FilePathList {}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface CheckValues {
		String [] permissibleStrings() default {};
		float min() default Float.NaN;
		float max() default Float.NaN;
	}

	public void canonifyFilePaths() {
			transformFilePaths(s -> {
				try {
					File f = new File(s);
					String canonical = f.getCanonicalPath();
					if (f.isDirectory()) {
						return canonical + '/';
					} else {
						return canonical;
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
	}

	public void canonifyFilePaths(String wd) {
		transformFilePaths(s -> {
			try {
				final String s2 = s.startsWith("/") ? s : wd + "/" + s;
				File f = new File(s2);
				String canonical = f.getCanonicalPath();
				if (f.isDirectory()) {
					return canonical + '/';
				} else {
					return canonical;
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}


	private void transformFilePaths(Function<String, String> transformer) {
		FieldIteration.iterateFields((field, fieldValue) -> {
			if (fieldValue == null) {
				return;
			}
			if (field.getAnnotation(FilePath.class) != null) {
				Assert.isTrue(fieldValue instanceof String, "Field %s not string", field);
				String path = (String) fieldValue;
				if (path.isEmpty()) {
					return;
				}
				String transformed = transformer.apply(path);
				field.set(this, transformed);
			} else if (field.getAnnotation(FilePathList.class) != null) {
				Assert.isTrue(fieldValue instanceof List, "Field %s not list", field);
				@SuppressWarnings("unchecked")
				List<String> paths = (List<String>) fieldValue;
				for (int i = 0; i < paths.size(); i++) {
					String path = paths.get(i);
					if (path.isEmpty()) {
						return;
					}
					String transformed = transformer.apply(path);
					paths.set(i, transformed);
				}
			}
		}, this);
	}

	/**
	 * Used to make JCommander ignore commas in genome locations.
	 * @author olivier
	 *
	 */
	public static class SwallowCommasConverter extends BaseConverter<String> implements IStringConverter<String> {
		public SwallowCommasConverter(String optionName) {
			super(optionName);
		}

		@Override
		public String convert(String value) {
			return value.replaceAll(",", "");
		}
	}

	@HideInToString
	@JsonIgnore
	@IgnoreInHashcodeEquals
	private static final Parameters defaultValues = new Parameters();

	private static final Set<String> fieldsToIgnore = new HashSet<>();
	static {
		fieldsToIgnore.add("$jacocoData");
		fieldsToIgnore.add("dnFieldFlags");
		fieldsToIgnore.add("dnFieldTypes");
		fieldsToIgnore.add("dnFieldNames");
	}

	@Override
	public String toString() {
		String defaultValuesString = "";
		String nonDefaultValuesString = "";
		for (Field field: Parameters.class.getDeclaredFields()) {
			try {
				field.setAccessible(true);
				if (field.getAnnotation(HideInToString.class) != null)
					continue;
				if (fieldsToIgnore.contains(field.getName())) {
					continue;
				}
				Object fieldValue = field.get(this);
				Assert.isFalse(fieldValue instanceof Parameters);//Avoid infinite
				//recursion and StackOverflowError during mutation testing
				Object fieldDefaultValue = field.get(defaultValues);
				String stringValue;
				if (fieldValue == null)
					stringValue = field.getName() + " = null";
				else {
					Method toStringMethod = fieldValue.getClass().getMethod("toString");
					toStringMethod.setAccessible(true);
					stringValue = field.getName() + " = " + toStringMethod.invoke
						(fieldValue);
				}
				final boolean fieldHasDefaultValue;
				if (fieldValue == null) {
					fieldHasDefaultValue = fieldDefaultValue == null;
				} else {
					Method equalsMethod = fieldValue.getClass().getMethod("equals", Object.class);
					equalsMethod.setAccessible(true);
					fieldHasDefaultValue = (Boolean) equalsMethod.invoke(fieldValue, fieldDefaultValue);
				}
				if (fieldHasDefaultValue) {
					defaultValuesString += "Default parameter value: " + stringValue + '\n';
				} else {
					nonDefaultValuesString += "Non-default parameter value: " + stringValue + '\n';
				}
			} catch (IllegalArgumentException | IllegalAccessException |
					InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		return "Working directory: " + System.getProperty("user.dir") + '\n' +
				nonDefaultValuesString + '\n' + defaultValuesString + '\n';
	}

	Object getFieldValue(String name) {
		try {
			Field f = Parameters.class.getDeclaredField(name);
			return f.get(this);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException |
				IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	boolean isParameterInstanceOf(String name, Class<?> clazz) {
		Field f;
		try {
			f = Parameters.class.getDeclaredField(name);
			return clazz.isInstance(f.get(this));
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	void setFieldValue(String name, Object value) {
		try {
			Field f = Parameters.class.getDeclaredField(name);
			if (f.get(this) instanceof Integer) {
				f.set(this, ((Number) value).intValue());
			} else if (f.get(this) instanceof Float) {
				f.set(this, ((Number) value).floatValue());
			} else if (f.get(this) instanceof Boolean) {
				f.set(this, value);
			} else
				throw new IllegalArgumentException("Field " + name + " is not Integer, Float, or Boolean");
		} catch (ClassCastException e) {
			throw new RuntimeException("Class of " + " value " + " does not match field " + name, e);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException |
				IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> differingFields(Parameters obj1, Parameters obj2) {
		List<String> result = new ArrayList<>();
		for (Field field: Parameters.class.getDeclaredFields()) {
			try {
				if (field.getAnnotation(HideInToString.class) != null)
					continue;
				if (field.getName().equals("$jacocoData")) {
					continue;
				}

				if (!Objects.equals(field.get(obj1), field.get(obj2))) {
					result.add(field.getName());
				}
			} catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}

	@SuppressWarnings("unused")
	private static Set<String> differingFields(List<Parameters> list) {
		Set<String> result = new HashSet<>();
		for (int i = list.size() - 1; i >= 0; i--) {
			for (int j = 0; j < i; j++) {
				result.addAll(differingFields(list.get(i), list.get(j)));
			}
		}
		return result;
	}

	/** Fixed from https://groups.google.com/forum/#!topic/jcommander/EwabBYieP88
	 * Obtains JCommander arguments in order of appearance rather than sorted
	 * by name (which is the case with a plain usage() call in JCommander 1.18).
	 * @param jc JCommander object.
	 * @param paramClass class with JCommander parameters (and nothing else!).
	 * @param out string builder to write to.
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	static void getUnsortedUsage(JCommander jc, Class<?> paramClass, StringBuilder out)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		List<@NonNull Field> fields = Arrays.asList(paramClass.getDeclaredFields());
		// Special treatment of main parameter.
		ParameterDescription mainParam = jc.getMainParameter();
		if (mainParam != null) {
			out.append("Required parameters:\n");
			out.append("     ").append(mainParam.getDescription()).append('\n');
		}

		@SuppressWarnings("null")
		String requiredParams = fields.stream().map(f -> f.getAnnotation(Parameter.class)).
				filter(Objects::nonNull).
				filter(a -> (!a.hidden()) && a.required()).map(a -> a.names()[0]).
				collect(Collectors.joining(", "));

		if (! "".equals(requiredParams)) {
			out.append("Required parameters: " + requiredParams + " (see explanations marked with *** below)\n");
		}

		out.append("Options:\n");
		List<ParameterDescription> params = jc.getParameters();
		final Field getWrapperParameter = ParameterDescription.class.getDeclaredField("m_wrappedParameter");
		getWrapperParameter.setAccessible(true);
		for (Field f: fields) {
			boolean required = false;
			Parameter annotation = f.getAnnotation(Parameter.class);
			if (annotation != null) {
				if (annotation.hidden()) {
					continue;
				}
				if (annotation.required()) {
					required = true;
				}
			} else {
				continue;
			}
			int nIt = 0;
			Handle<String> suffix = new Handle<>("");
			outer:
			while (true) {
				if (nIt == 1) {
					suffix.set("s");
				} else if (nIt == 2) {
					throw new RuntimeException("Could not find field annotation for " + f.getName());
				}
				nIt++;
				for (ParameterDescription p: params) {
					List<String> names = Arrays.stream(((WrappedParameter) getWrapperParameter.get(p)).names()).map(
							s -> s.substring(1) + suffix.get()).collect(Collectors.toList());
					if (names.contains(f.getName())) {
						out.append(p.getNames()).append('\n');
						String def = (required ? "\nRequired parameter" : (p.getDefault() == null ? "" : ("\nDefault: " +
								p.getDefault().toString().trim() + '.'))) + '\n';
						String desc = wordWrap(p.getDescription(), 75) + def;
						desc = "     " + desc;
						desc = desc.replaceAll("\n", "\n     ") + '\n';
						desc = desc.replaceAll("     Required parameter", "**** Required parameter");
						out.append(desc);
						break outer;
					}
				}
			}
		}
	}

	/**
	 * Copied from StackOverflow
	 * @param s String without pre-existing line breaks
	 * @param nColumns
	 * @return
	 */
	private static String wordWrap(String s, int nColumns) {
		StringBuilder sb = new StringBuilder(s);
		int i = 0;
		while (i + nColumns < sb.length() && (i = sb.lastIndexOf(" ", i + nColumns)) != -1) {
			sb.replace(i, i + 1, "\n");
		}
		return sb.toString();
	}

	@Override
	public Parameters clone() {
		try {
			return (Parameters) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		SettableInteger hashCode = new SettableInteger(0);
		FieldIteration.iterateFields((f, value) -> {
			if (f.getAnnotation(IgnoreInHashcodeEquals.class) == null) {
				hashCode.set(hashCode.get() * 31 + Objects.hashCode(value));
			}
		}, this);
		return hashCode.get();
	}

	@Override
	public boolean equals(Object other0) {
		Handle<Boolean> notEqual = new Handle<>(false);
		Parameters other = (Parameters) other0;
		FieldIteration.iterateFields((f, value) -> {
			if (notEqual.get()) {
				return;
			}
			if (f.getAnnotation(IgnoreInHashcodeEquals.class) == null) {
				if (!Objects.equals(value, f.get(other))) {
					notEqual.set(true);
				}
			}
		}, this);
		return !notEqual.get();
	}

}

