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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.WrappedParameter;
import com.beust.jcommander.converters.BaseConverter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Handle;

public final class Parameters implements Serializable {

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
	public boolean noStatusMessages = false;
	
	@Parameter(names = "-skipVersionCheck", description = "Do not check whether update is available for download", required = false)
	@HideInToString
	@JsonIgnore
	public boolean skipVersionCheck = false;

	@Parameter(names = "-verbosity", description = "0: main mutation detection results only; 3: open the firehose", required = false)
	public int verbosity = 0;
	
	@FilePath
	@Parameter(names = "-outputJSONTo", description = "Path to which JSON-formatted output should be written",
			required = false)
	public String outputJSONTo = "";
	
	@Parameter(names = "-outputDuplexDetails", description = "For each reported mutation, give list of its reads and duplexes", required = false)
	public boolean outputDuplexDetails = false;
		
	@Parameter(names = "-parallelizationFactor", description = "Number of chunks into which to split each contig for parallel processing; setting this value too high can be highly counter-productive", required = false)
	public int parallelizationFactor = 1;
	
	@Parameter(names = "-maxThreadsPerPool", description = "Maximum number of threads per pool; "
			+ "for now, to avoid deadlocks this number should be kept higher than number of inputs *"
			+ " number of contigs * parallelization factor", required = false)
	public int maxThreadsPerPool = 64;
	
	@Parameter(names = "-terminateImmediatelyUponError", description = "If true, any error causes immediate termination of the run", required = false)
	public boolean terminateImmediatelyUponError = true;

	@Parameter(names = "-terminateUponOutputFileError", description = "If true, any error in writing auxiliary output files causes termination of the run", required = false)
	public boolean terminateUponOutputFileError = true;
	
	@Parameter(names = "-processingChunk", description = "Size of sliding windows used to synchronize analysis in different samples", required = false)
	public int processingChunk = 160;
	
	@FilePathList
	@Parameter(names = "-inputReads", description = "Input BAM read file, sorted and with an index; repeat as many times as there are samples", required = true)
	public List<@NonNull String> inputReads = new ArrayList<>();
	
	@Parameter(names = "-lenientSamValidation",description = "Passed to Picard; seems at least sometimes necessary for"
			+ " alignments produced by BWA", required = false)
	public boolean lenientSamValidation = true;
	
	@FilePathList
	@Parameter(names = "-originalReadFile1", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile1 = new ArrayList<>();

	@FilePathList
	@Parameter(names = "-originalReadFile2", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile2 = new ArrayList<>();
	
	@Parameter(names = "-nRecordsToProcess", description = "Only process first N reads", required = false)
	public long nRecordsToProcess = Long.MAX_VALUE;

	@Parameter(names = "-dropReadProbability", description = "Reads will be randomly ignored with a probability given by this number")
	public float dropReadProbability = 0;
	
	@FilePathList
	@Parameter(names = "-intersectAlignment", description = "List of BAM files with which alignments in inputReads must agree; each file must be sorted", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> intersectAlignment = new ArrayList<>();
	
	@Parameter(names = "-minMappingQIntersect", description = "Minimum mapping quality for reads in intersection files", required = false, hidden = hideInProgressParameters)
	public List<Integer> minMappingQIntersect = new ArrayList<>();

	@FilePath
	@Parameter(names = "-referenceGenome", description = "Reference genome in FASTA format; index file must be present and for now contigs must appear in alphabetical order",
			required = true)
	public String referenceGenome = "";
	
	@Parameter(names = "-contigNamesToProcess", description =
			"Reads not mapped to any of these contigs will be ignored")
	@NonNull List<@NonNull String> contigNamesToProcess = 
		Arrays.asList("chrI", "chrII", "chrIII", "chrIV", "chrV", "chrX", "chrM");
	
	{
		Collections.sort(contigNamesToProcess);
	}
	
	@Parameter(names = "-startAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
			converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	public List<@NonNull String> startAtPositions = new ArrayList<>();

	@Parameter(names = "-stopAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
			converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
	public List<@NonNull String> stopAtPositions = new ArrayList<>();
	
	@Parameter(names = "-readContigsFromFile", description = "Read contig names from reference genome file")
	public boolean readContigsFromFile = false;
	
	public static final List<@NonNull String> defaultTruncateContigNames = Arrays.asList(
			"chrI", "chrII", "chrIII", "chrIV", "chrM", "chrV", "chrX");

	public static final List<@NonNull Integer> defaultTruncateContigPositions = Arrays.asList(
			15_072_423, 15_279_345, 13_783_700, 17_493_793, 13_794, 20_924_149, 17_718_866);
	
	static final List<@NonNull Integer> defaultStartContigPositions = 
			Arrays.asList(1, 1, 1, 1, 1, 1, 1);
	
	@Parameter(names = "-traceField", description = "Output each position at which "
			+ "specified statistic is implemented; formatted as sampleName:statisticName", required = false)
	public List<String> traceFields = new ArrayList<>();

	@Parameter(names = "-contigStatsBinLength", description = "Length of bin to use for statistics that"
			+ " are broken down more finely than contig by contig", required = false)
	public int contigStatsBinLength = 2_000_000;

	@Parameter(names = "-minMappingQualityQ1", description = "Reads whose mapping quality is below this"
			+ " threshold are discarded (best to keep this relatively low to allow non-unique mutation candidates to be identified in all samples)", required = false)
	public int minMappingQualityQ1 = 20;

	@Parameter(names = "-minMappingQualityQ2", description = "Reads whose mapping quality is below this"
			+ " threshold are not used to propose mutation candidates", required = false)
	public int minMappingQualityQ2 = 50;

	@Parameter(names = "-minReadsPerStrandQ1", description = "Duplexes that have fewer reads for the "
			+ "original top and bottom strands are ignored when calling substitutions or indels", required = false)
	public int minReadsPerStrandQ1 = 0;
	
	@Parameter(names = "-minReadsPerStrandQ2", description = "Duplexes that have at least this number of reads "
			+ "for original top and bottom strands can contribute candidates for substitutions or indels", required = false)
	public int minReadsPerStrandQ2 = 3;
	
	@Parameter(names = "-promoteNQ1Duplexes", description = "Not yet functional, and probably never will be - Promote candidate that has at least this many Q1 duplexes to Q2", required = false, hidden = true)
	public int promoteNQ1Duplexes = Integer.MAX_VALUE;
	
	@Parameter(names = "-promoteNSingleStrands", description = "Not yet functional, and probably never will be - Promote duplex that has just 1 original strand but at least this many reads to Q1", required = false, hidden = true)
	public int promoteNSingleStrands = Integer.MAX_VALUE;
	
	@Parameter(names = "-promoteFractionReads", description = "Promote candidate supported by at least this fraction of reads to Q2", required = false, hidden = hideAdvancedParameters)
	public float promoteFractionReads = Float.MAX_VALUE;
	
	@Parameter(names = "-minConsensusThresholdQ1", description = "Lenient value for minimum fraction of reads from the same"
			+ " original strand that define a consensus (must be > 0.5)", required = false)
	public float minConsensusThresholdQ1 = 0.51f;
	
	@Parameter(names = "-minConsensusThresholdQ2", description = "Strict value for minimum fraction of reads from the same"
			+ " original strand that define a consensus (must be > 0.5)", required = false)
	public float minConsensusThresholdQ2 = 0.95f;
	
	@Parameter(names = "-disagreementConsensusThreshold", description = "Disagreements are only reported if for each strand"
			+ " consensus is above this threshold, in addition to being above minConsensusThresholdQ2", required = false)
	public float disagreementConsensusThreshold = 0.0f;
	
	@Parameter(names = "-minReadsPerStrandForDisagreement", description = "Minimal number of reads "
			+ "for original top and bottom strands to examine duplex for disagreement between these strands", required = false)
	public int minReadsPerStrandForDisagreement = 0;
	
	@Parameter(names = "-computeRawDisagreements", description = "Compute disagreements between raw reads and reference sequence", required = false)
	public boolean computeRawDisagreements = true;

	@Parameter(names = "-minBasePhredScoreQ1", description = "Bases whose Phred quality score is below this threshold"
			+ " are discarded (keeping this relatively low helps identify problematic reads)", required = false)
	public int minBasePhredScoreQ1 = 20;
	
	@Parameter(names = "-minBasePhredScoreQ2", description = "Bases whose Phred quality score is below this threshold are not used to propose mutation candidates"
			, required = false)
	public int minBasePhredScoreQ2 = 30;
	
	@Parameter(names = "-ignoreFirstNBasesQ1", description = "Bases that occur within this many bases of read start are discarded", required = false)
	public int ignoreFirstNBasesQ1 = 4;

	@Parameter(names = "-ignoreFirstNBasesQ2", description = "Bases that occur within this many bases of read start are not used to propose mutation candidates", required = false)
	public int ignoreFirstNBasesQ2 = 35;

	@Parameter(names = "-ignoreLastNBases", description = "Potential mutations that occur within this many bases of read end are ignored", required = false)
	public int ignoreLastNBases = 4;

	@Parameter(names = "-minReadMedianPhredScore", description = "Reads whose median Phred quality score is below this threshold are discarded"
			, required = false)
	public int minReadMedianPhredScore = 0;
	
	@Parameter(names = "-minMedianPhredQualityAtPosition", description = "Positions whose median Phred quality score is below this threshold are not used to propose mutation candidates"
			, required = false)
	public int minMedianPhredQualityAtPosition = 0;
	
	@Parameter(names = "-maxFractionWrongPairsAtPosition", description = "Positions are not used to propose mutation candidates if the fraction of reads covering the position that have an unmapped mate or a mate that forms a wrong pair orientiation (RF, Tandem) is above this threshold"
			, required = false)
	public float maxFractionWrongPairsAtPosition = 1.0f;
	
	@Parameter(names = "-maxAverageBasesClipped", description = "Duplexes whose mean number of clipped bases is above this threshold are not used to propose mutation candidates"
			, required = false)
	public int maxAverageBasesClipped = 15;
	
	@Parameter(names = "-maxAverageClippingOfAllCoveringDuplexes", description = "Positions whose average covering duplex average number of clipped bases is above this threshold are not used to propose mutation candidates"
			, required = false)
	public int maxAverageClippingOfAllCoveringDuplexes = 999;
	
	@Parameter(names = "-maxNDuplexes", description = "Positions whose number of Q1 or Q2 duplexes is above this threshold are ignored when computing mutation rates"
			, required = false)
	public List<Integer> maxNDuplexes = new ArrayList<>();
	
	@Parameter(names = "-maxInsertSize", description = "Inserts above this size are not used to propose Q2 mutation candidates, and will most of the time be ignored when identifying Q1 candidates", required = false)
	public int maxInsertSize = 1_000;

	@Parameter(names = "-minInsertSize", description = "Inserts below this size are not used to propose mutation candidates", required = false)
	public int minInsertSize = 0;
	
	@Parameter(names = "-ignoreZeroInsertSizeReads", description = "Reads 0 or undefined insert size are thrown out at the onset (and thus cannot contribute to exclusion of mutation candidates found in multiple samples)", required = false)
	public boolean ignoreZeroInsertSizeReads = false;

	@Parameter(names = "-ignoreSizeOutOfRangeInserts", description = "Reads with insert size out of range are thrown out at the onset (and thus cannot contribute to exclusion of mutation candidates found in multiple samples)", required = false)
	public boolean ignoreSizeOutOfRangeInserts = false;
	
	@Parameter(names = "-ignoreTandemRFPairs", description = "Read pairs that form tandem or RF are thrown out at the onset", required = false)
	public boolean ignoreTandemRFPairs = false;
	
	@Parameter(names = "-minNumberDuplexesSisterArm", description = "Min number of duplexes in sister arm to call a candidate mutation unique; adjust this number to deal with heterozygous mutations", required = false)
	public int minNumberDuplexesSisterArm = 10;

	@Parameter(names = "-minQ2DuplexesToCallMutation", description = "Min number of Q2 duplexes to call mutation (condition set by minQ1Q2DuplexesToCallMutation must also be met)", required = false)
	public int minQ2DuplexesToCallMutation = 1;

	@Parameter(names = "-minQ1Q2DuplexesToCallMutation", description = "Min number of Q1 or Q2 duplexes to call mutation (condition set by minQ2DuplexesToCallMutation must also be met)", required = false)
	public int minQ1Q2DuplexesToCallMutation = 1;
	
	@Parameter(names = "-acceptNInBarCode", description = "If true, an N read within the barcode is"
			+ " considered a match", required = false)
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

	@FilePathList
	@Parameter(names = "-saveFilteredReadsTo", description = "Not implemented; write raw reads that were kept for analysis to specified files", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> saveFilteredReadsTo = new ArrayList<>();
	
	@Parameter(names = "-collapseFilteredReads", description = "Only write one (randomly-chosen) read per duplex", required = false, hidden = false)
	public boolean collapseFilteredReads = false;
	
	@FilePathList
	@Parameter(names = "-bamReadsWithBarcodeField", description = "BAM/SAM file saved from previous run with barcodes stored as attributes", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> bamReadsWithBarcodeField = new ArrayList<>();

	@Parameter(names = "-saveRawReadsDB", description = "Not functional at present", required = false, arity = 1, hidden = hideInProgressParameters)
	public boolean saveRawReadsDB = false;
	
	@Parameter(names = "-saveRawReadsMVDB", description = "Not functional at present", required = false, arity = 1, hidden = hideInProgressParameters)
	public boolean saveRawReadsMVDB = false;
	
	@Parameter(names = "-outputCoverageBed", description = "Output bed file that gives number of duplexes covering each position in the reference sequence; " +
			"note that this is a highly-inefficient format that creates a huge file", required = false)
	public boolean outputCoverageBed = false;
	
	@Parameter(names = "-outputCoverageProto", description = "Output protobuf file that gives number of duplexes covering each position in the reference sequence",
			required = false)
	public boolean outputCoverageProto = false;

	/**
	 * Output section 
	 */
	
	@Parameter(names = "-sampleName", description = "Used to name samples in output file; can be repeated as many times as there are inputReads", required = false)
	List<@NonNull String> sampleNames = new ArrayList <> ();
	
	@FilePathList
	@Parameter(names = "-forceOutputAtPositionsFile", description = "Detailed information is reported for all positions listed in the file", required = false)
	public List<@NonNull String> forceOutputAtPositionsFile = new ArrayList<>();
	
	@Parameter(names = "-randomOutputRate", description = "Randomly choose genome positions at this rate to include in output", required = false)
	public float randomOutputRate = 0;

	@FilePath
	@Parameter(names = "-outputAlignmentFile", description = "Write BAM output with duplex information provided in custom tags; " +
			"note that a read may be omitted from the output, e.g. if it falls below a Q1 threshold, if predicted insert size is 0, "
			+ "and under some circumstances if the mate mapping quality is below Q1 threshold, if the mate is mapped further away "
			+ "than possible according to maximum insert size, or if it falls outside of the specified processing window (it is "
			+ "relatively rare but possible for a read to be omitted even though it counts toward coverage).", required = false)
	public String outputAlignmentFile = null;

	@FilePath
	@Parameter(names = "-discardedReadFile", description = "Write discarded reads to BAM file specified by parameter", required = false, hidden = hideInProgressParameters)
	public String discardedReadFile = null;

	@Parameter(names = "-logReadIssuesInOutputBam", description = "Use custom fields in output BAM to give reasons why duplexes as a whole or individual bases did not reach maximum quality", required = false, arity = 1)
	public boolean logReadIssuesInOutputBam = true;
	
	@Parameter(names = "-sortOutputAlignmentFile", description = "Sort BAM file; can require a large amount of memory", required = false, arity = 1)
	public boolean sortOutputAlignmentFile = false;
	
	@Parameter(names = "-outputTopBottomDisagreementBED", description = "Output to file specified by option -topBottomDisagreementFileBaseName", required = false, arity = 1)
	public boolean outputTopBottomDisagreementBED = true;
	
	@FilePathList
	@Parameter(names = "-reportStatsForBED", description = "Report number of observations that fall within " +
			"the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForBED = new ArrayList<>();
	
	@FilePathList
	@Parameter(names = "-reportStatsForNotBED", description = "Report number of observations that do *not* fall within " +
			"the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForNotBED = new ArrayList<>();
	
	@FilePathList
	@Parameter(names = "-excludeRegionsInBED", description = "Positions covered by this BED file will be completely ignored in the analysis", required = false)
	public List<@NonNull String> excludeRegionsInBED = new ArrayList<>();
	
	@FilePathList
	@Parameter(names = "-repetiveRegionBED", description = "If specified, used for stats (mutant|wt)Q2CandidateQ1Q2DCoverage[Non]Repetitive", required = false)
	public List<@NonNull String> repetiveRegionBED = new ArrayList<>();
	
	@FilePath
	@Parameter(names = "-bedDisagreementOrienter", description = "Gene orientation read from this file "
			+ "is used to orient top/bottom strand disagreements with respect to transcribed strand", required = false)
	public String bedDisagreementOrienter = null;
	
	@FilePathList
	@Parameter(names = "-reportBreakdownForBED", description = "Report number of observations that fall within " +
			"each of the regions defined by BED file whose path follows", required = false)
	public List<@NonNull String> reportBreakdownForBED = new ArrayList<>();
	
	@FilePathList
	@Parameter(names = "-saveBEDBreakdownTo", description = "Path for saving of BED region counts; argument " +
			"list must match that given to -reportBreakdownForBED", required = false)
	public List<@NonNull String> saveBEDBreakdownTo = new ArrayList<>();
	
	@FilePath
	@Parameter(names = "-bedFeatureSuppInfoFile", description = "Read genome annotation supplementary info, used in output of counter with BED feature breakdown")
	public String bedFeatureSuppInfoFile = null;

	@FilePath
	@Parameter(names = "-refSeqToOfficialGeneName", description = "Tab separated text file with RefSeq ID, tab, and official gene name and any other useful info; " +
			"counts will be reported both by RefSeq ID and official gene name")
	public String refSeqToOfficialGeneName = null;
	
	@FilePath
	@Parameter(names = "-auxOutputFileBaseName", description = "Base name of files to which to record mutations, disagreements between top and bottom strands, etc.", required = false)
	public String auxOutputFileBaseName = null;
	
	@Parameter(names = "-rnaSeq", description = "Ignore deletions and turn off checks that do not make sense for RNAseq data", required = false)
	public boolean rnaSeq = false;
	
	@Parameter(names = "-submitToServer", description = "RMI address", required = false, hidden = hideInProgressParameters)
	public String submitToServer = null;
	
	@Parameter(names = "-startServer", help = true, description = "RMI address", required = false, hidden = hideInProgressParameters)
	public String startServer = null;
	
	@Parameter(names = "-startWorker", help = true, description = "RMI server address", required = false, hidden = hideInProgressParameters)
	public String startWorker = null;
	
	@FilePath
	@Parameter(names = "-workingDirectory", help = true, description = "Evaluate parameter file paths using specified directory as workind directory", required = false, hidden = hideAdvancedParameters)
	public String workingDirectory = null;
	
	@FilePath
	@Parameter(names = "-referenceOutput", description = "Path to reference output to be used for functional tests", required = false, hidden = hideAdvancedParameters)
	public String referenceOutput = null;
	
	@FilePath
	@Parameter(names = "-recordRunsTo", description = "Get server to output a record of all runs it processed, to be replayed for functional tests", required = false, hidden = hideAdvancedParameters)
	public String recordRunsTo = null;

	@Parameter(names = "-runName", description = "Name of run to be used in conjunction with -recordRunsTo", required = false, hidden = hideAdvancedParameters)
	public String runName = null;
	
	@Parameter(names = "-enableCostlyAssertions", description = "Enable internal sanity checks that significantly slow down execution", required = false, hidden = hideAdvancedParameters, arity = 1)
	public boolean enableCostlyAssertions = true;
	
	@Retention(RetentionPolicy.RUNTIME)
	/**
	 * Used to mark parameters that it is not useful to print in toString method.
	 * @author olivier
	 *
	 */
	public @interface HideInToString {}
	
	@Retention(RetentionPolicy.RUNTIME)
	public @interface FilePath {}

	@Retention(RetentionPolicy.RUNTIME)
	public @interface FilePathList {}
	
	public void canonifyFilePaths() {
			transformFilePaths(s -> {
				try {
					File f = new File(s);
					String canonical = f.getCanonicalPath();
					if (f.isDirectory()) {
						return canonical + "/";
					} else {
						return canonical;
					}
				} 	catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
	}
	
	public void transformFilePaths(Function<String, String> transformer) {
		for (Field field: Parameters.class.getDeclaredFields()) {
			try {
				if (field.getAnnotation(FilePath.class) != null) {
					Object fieldValue = field.get(this);
					if (fieldValue == null) {
						continue;
					}
					Assert.isTrue(fieldValue instanceof String, "Field %s not string", field);
					String path = (String) fieldValue;
					String transformed = transformer.apply(path);
					field.set(this, transformed);
				} else if (field.getAnnotation(FilePathList.class) != null) {
					Object fieldValue = field.get(this);
					if (fieldValue == null) {
						continue;
					}
					Assert.isTrue(fieldValue instanceof List, "Field %s not list", field);
					@SuppressWarnings("unchecked")
					List<String> paths = (List<String>) fieldValue;
					for (int i = 0; i < paths.size(); i++) {
						String path = paths.get(i);
						String transformed = transformer.apply(path);
						paths.set(i, transformed);
					}
				} else {
					continue;
				}
			} catch (IllegalArgumentException | IllegalAccessException | 
					SecurityException e) {
				throw new RuntimeException(e);
			} 
		}
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
	private static final Parameters defaultValues = new Parameters();
	
	@Override
	public String toString() {
		String defaultValuesString = "";
		String nonDefaultValuesString = "";
		for (Field field: Parameters.class.getDeclaredFields()) {
			try {
				if (field.getAnnotation(HideInToString.class) != null)
					continue;
				if (field.getName().equals("$jacocoData")) {
					continue;
				}
				Object fieldValue = field.get(this);
				Object fieldDefaultValue = field.get(defaultValues);
				String stringValue;
				if (fieldValue == null)
					stringValue = field.getName()+ " = null";
				else
					stringValue = field.getName() + " = " + fieldValue.getClass().getMethod("toString").invoke
						(fieldValue);
				boolean fieldHasDefaultValue = fieldValue == null ? fieldDefaultValue == null :
					Boolean.TRUE.equals(fieldValue.getClass().getMethod("equals",Object.class).invoke
						(fieldValue, fieldDefaultValue));
				if (fieldHasDefaultValue) {
					defaultValuesString += "Default parameter value: " + stringValue + "\n";
				} else {
					nonDefaultValuesString += "Non-default parameter value: " + stringValue + "\n";
				}
			} catch (IllegalArgumentException | IllegalAccessException | 
					InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}
		return "Working directory: " + System.getProperty("user.dir") + "\n" +
				nonDefaultValuesString + "\n" + defaultValuesString + "\n";
	}
	
	/** Fixed from https://groups.google.com/forum/#!topic/jcommander/EwabBYieP88
	 * Obtains JCommander arguments in order of appearance rather than sorted
	 * by name (which is the case with a plain usage() call in JCommander 1.18).
	 * @param jc JCommander object.
	 * @param jcParams class with JCommander parameters (and nothing else!).
	 * @param out string builder to write to.
	 * @throws SecurityException 
	 * @throws NoSuchFieldException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 */
	public static void getUnsortedUsage(JCommander jc, Class<?> paramClass, StringBuilder out)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		List<Field> fields = Arrays.asList(paramClass.getDeclaredFields());
		// Special treatment of main parameter.
		ParameterDescription mainParam = jc.getMainParameter();
		if (mainParam != null) {
			out.append("Required parameters:\n");
			out.append("     ").append(mainParam.getDescription()).append('\n');
		}
		
		String requiredParams = fields.stream().map(f -> f.getAnnotation(Parameter.class)).
				filter(a -> a != null).
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
					List<String> names = Arrays.asList(((WrappedParameter) getWrapperParameter.get(p)).names()).stream().map(
							s -> s.substring(1) + suffix.get()).collect(Collectors.toList());
					if (names.contains(f.getName())) {
						out.append(p.getNames()).append('\n');
						String def = (required ? "\nRequired parameter" : (p.getDefault() == null ? "" : ("\nDefault: " +
								p.getDefault().toString().trim() + '.'))) + "\n";
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
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (acceptNInBarCode ? 1231 : 1237);
		result = prime * result + alignmentPositionMismatchAllowed;
		result = prime * result + ((auxOutputFileBaseName == null) ? 0 : auxOutputFileBaseName.hashCode());
		result = prime * result + ((bamReadsWithBarcodeField == null) ? 0 : bamReadsWithBarcodeField.hashCode());
		result = prime * result + ((bedDisagreementOrienter == null) ? 0 : bedDisagreementOrienter.hashCode());
		result = prime * result + ((bedFeatureSuppInfoFile == null) ? 0 : bedFeatureSuppInfoFile.hashCode());
		result = prime * result + (collapseFilteredReads ? 1231 : 1237);
		result = prime * result + (computeRawDisagreements ? 1231 : 1237);
		result = prime * result + constantBarcode.hashCode();
		result = prime * result + contigNamesToProcess.hashCode();
		result = prime * result + contigStatsBinLength;
		result = prime * result + Float.floatToIntBits(disagreementConsensusThreshold);
		result = prime * result + ((discardedReadFile == null) ? 0 : discardedReadFile.hashCode());
		result = prime * result + Float.floatToIntBits(dropReadProbability);
		result = prime * result + ((excludeRegionsInBED == null) ? 0 : excludeRegionsInBED.hashCode());
		result = prime * result + ((forceOutputAtPositionsFile == null) ? 0 : forceOutputAtPositionsFile.hashCode());
		result = prime * result + (help ? 1231 : 1237);
		result = prime * result + ignoreFirstNBasesQ1;
		result = prime * result + ignoreFirstNBasesQ2;
		result = prime * result + ignoreLastNBases;
		result = prime * result + (ignoreSizeOutOfRangeInserts ? 1231 : 1237);
		result = prime * result + (ignoreTandemRFPairs ? 1231 : 1237);
		result = prime * result + ((inputReads == null) ? 0 : inputReads.hashCode());
		result = prime * result + ((intersectAlignment == null) ? 0 : intersectAlignment.hashCode());
		result = prime * result + (lenientSamValidation ? 1231 : 1237);
		result = prime * result + (logReadIssuesInOutputBam ? 1231 : 1237);
		result = prime * result + maxAverageBasesClipped;
		result = prime * result + maxAverageClippingOfAllCoveringDuplexes;
		result = prime * result + Float.floatToIntBits(maxFractionWrongPairsAtPosition);
		result = prime * result + maxInsertSize;
		result = prime * result + ((maxNDuplexes == null) ? 0 : maxNDuplexes.hashCode());
		result = prime * result + maxThreadsPerPool;
		result = prime * result + minBasePhredScoreQ1;
		result = prime * result + minBasePhredScoreQ2;
		result = prime * result + Float.floatToIntBits(minConsensusThresholdQ1);
		result = prime * result + Float.floatToIntBits(minConsensusThresholdQ2);
		result = prime * result + minInsertSize;
		result = prime * result + ((minMappingQIntersect == null) ? 0 : minMappingQIntersect.hashCode());
		result = prime * result + minMappingQualityQ1;
		result = prime * result + minMappingQualityQ2;
		result = prime * result + minMedianPhredQualityAtPosition;
		result = prime * result + minNumberDuplexesSisterArm;
		result = prime * result + minQ1Q2DuplexesToCallMutation;
		result = prime * result + minQ2DuplexesToCallMutation;
		result = prime * result + minReadMedianPhredScore;
		result = prime * result + minReadsPerStrandForDisagreement;
		result = prime * result + minReadsPerStrandQ1;
		result = prime * result + minReadsPerStrandQ2;
		result = prime * result + nConstantBarcodeMismatchesAllowed;
		result = prime * result + (int) (nRecordsToProcess ^ (nRecordsToProcess >>> 32));
		result = prime * result + nVariableBarcodeMismatchesAllowed;
		result = prime * result + (noStatusMessages ? 1231 : 1237);
		result = prime * result + ((originalReadFile1 == null) ? 0 : originalReadFile1.hashCode());
		result = prime * result + ((originalReadFile2 == null) ? 0 : originalReadFile2.hashCode());
		result = prime * result + ((outputAlignmentFile == null) ? 0 : outputAlignmentFile.hashCode());
		result = prime * result + (outputCoverageBed ? 1231 : 1237);
		result = prime * result + (outputCoverageProto ? 1231 : 1237);
		result = prime * result + (outputDuplexDetails ? 1231 : 1237);
		result = prime * result + (outputTopBottomDisagreementBED ? 1231 : 1237);
		result = prime * result + parallelizationFactor;
		result = prime * result + processingChunk;
		result = prime * result + Float.floatToIntBits(promoteFractionReads);
		result = prime * result + promoteNQ1Duplexes;
		result = prime * result + promoteNSingleStrands;
		result = prime * result + (readContigsFromFile ? 1231 : 1237);
		result = prime * result + ((refSeqToOfficialGeneName == null) ? 0 : refSeqToOfficialGeneName.hashCode());
		result = prime * result + ((referenceGenome == null) ? 0 : referenceGenome.hashCode());
		result = prime * result + ((repetiveRegionBED == null) ? 0 : repetiveRegionBED.hashCode());
		result = prime * result + ((reportBreakdownForBED == null) ? 0 : reportBreakdownForBED.hashCode());
		result = prime * result + ((reportStatsForBED == null) ? 0 : reportStatsForBED.hashCode());
		result = prime * result + ((reportStatsForNotBED == null) ? 0 : reportStatsForNotBED.hashCode());
		result = prime * result + (requireMatchInAlignmentEnd ? 1231 : 1237);
		result = prime * result + (rnaSeq ? 1231 : 1237);
		result = prime * result + ((sampleNames == null) ? 0 : sampleNames.hashCode());
		result = prime * result + ((saveBEDBreakdownTo == null) ? 0 : saveBEDBreakdownTo.hashCode());
		result = prime * result + ((saveFilteredReadsTo == null) ? 0 : saveFilteredReadsTo.hashCode());
		result = prime * result + (saveRawReadsDB ? 1231 : 1237);
		result = prime * result + (saveRawReadsMVDB ? 1231 : 1237);
		result = prime * result + (skipVersionCheck ? 1231 : 1237);
		result = prime * result + (sortOutputAlignmentFile ? 1231 : 1237);
		result = prime * result + ((startAtPositions == null) ? 0 : startAtPositions.hashCode());
		result = prime * result + ((startServer == null) ? 0 : startServer.hashCode());
		result = prime * result + ((startWorker == null) ? 0 : startWorker.hashCode());
		result = prime * result + ((stopAtPositions == null) ? 0 : stopAtPositions.hashCode());
		result = prime * result + ((submitToServer == null) ? 0 : submitToServer.hashCode());
		result = prime * result + (terminateImmediatelyUponError ? 1231 : 1237);
		result = prime * result + (terminateUponOutputFileError ? 1231 : 1237);
		result = prime * result + variableBarcodeLength;
		result = prime * result + verbosity;
		result = prime * result + (version ? 1231 : 1237);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Parameters other = (Parameters) obj;
		if (acceptNInBarCode != other.acceptNInBarCode)
			return false;
		if (alignmentPositionMismatchAllowed != other.alignmentPositionMismatchAllowed)
			return false;
		if (auxOutputFileBaseName == null) {
			if (other.auxOutputFileBaseName != null)
				return false;
		} else if (!auxOutputFileBaseName.equals(other.auxOutputFileBaseName))
			return false;
		if (bamReadsWithBarcodeField == null) {
			if (other.bamReadsWithBarcodeField != null)
				return false;
		} else if (!bamReadsWithBarcodeField.equals(other.bamReadsWithBarcodeField))
			return false;
		if (bedDisagreementOrienter == null) {
			if (other.bedDisagreementOrienter != null)
				return false;
		} else if (!bedDisagreementOrienter.equals(other.bedDisagreementOrienter))
			return false;
		if (bedFeatureSuppInfoFile == null) {
			if (other.bedFeatureSuppInfoFile != null)
				return false;
		} else if (!bedFeatureSuppInfoFile.equals(other.bedFeatureSuppInfoFile))
			return false;
		if (collapseFilteredReads != other.collapseFilteredReads)
			return false;
		if (computeRawDisagreements != other.computeRawDisagreements)
			return false;
		if (!constantBarcode.equals(other.constantBarcode))
			return false;
		if (!contigNamesToProcess.equals(other.contigNamesToProcess))
			return false;
		if (contigStatsBinLength != other.contigStatsBinLength)
			return false;
		if (Float.floatToIntBits(disagreementConsensusThreshold) != Float.floatToIntBits(
				other.disagreementConsensusThreshold))
			return false;
		if (discardedReadFile == null) {
			if (other.discardedReadFile != null)
				return false;
		} else if (!discardedReadFile.equals(other.discardedReadFile))
			return false;
		if (Float.floatToIntBits(dropReadProbability) != Float.floatToIntBits(other.dropReadProbability))
			return false;
		if (excludeRegionsInBED == null) {
			if (other.excludeRegionsInBED != null)
				return false;
		} else if (!excludeRegionsInBED.equals(other.excludeRegionsInBED))
			return false;
		if (forceOutputAtPositionsFile == null) {
			if (other.forceOutputAtPositionsFile != null)
				return false;
		} else if (!forceOutputAtPositionsFile.equals(other.forceOutputAtPositionsFile))
			return false;
		if (help != other.help)
			return false;
		if (ignoreFirstNBasesQ1 != other.ignoreFirstNBasesQ1)
			return false;
		if (ignoreFirstNBasesQ2 != other.ignoreFirstNBasesQ2)
			return false;
		if (ignoreLastNBases != other.ignoreLastNBases)
			return false;
		if (ignoreSizeOutOfRangeInserts != other.ignoreSizeOutOfRangeInserts)
			return false;
		if (ignoreTandemRFPairs != other.ignoreTandemRFPairs)
			return false;
		if (inputReads == null) {
			if (other.inputReads != null)
				return false;
		} else if (!inputReads.equals(other.inputReads))
			return false;
		if (intersectAlignment == null) {
			if (other.intersectAlignment != null)
				return false;
		} else if (!intersectAlignment.equals(other.intersectAlignment))
			return false;
		if (lenientSamValidation != other.lenientSamValidation)
			return false;
		if (logReadIssuesInOutputBam != other.logReadIssuesInOutputBam)
			return false;
		if (maxAverageBasesClipped != other.maxAverageBasesClipped)
			return false;
		if (maxAverageClippingOfAllCoveringDuplexes != other.maxAverageClippingOfAllCoveringDuplexes)
			return false;
		if (Float.floatToIntBits(maxFractionWrongPairsAtPosition) != Float.floatToIntBits(
				other.maxFractionWrongPairsAtPosition))
			return false;
		if (maxInsertSize != other.maxInsertSize)
			return false;
		if (maxNDuplexes == null) {
			if (other.maxNDuplexes != null)
				return false;
		} else if (!maxNDuplexes.equals(other.maxNDuplexes))
			return false;
		if (maxThreadsPerPool != other.maxThreadsPerPool)
			return false;
		if (minBasePhredScoreQ1 != other.minBasePhredScoreQ1)
			return false;
		if (minBasePhredScoreQ2 != other.minBasePhredScoreQ2)
			return false;
		if (Float.floatToIntBits(minConsensusThresholdQ1) != Float.floatToIntBits(other.minConsensusThresholdQ1))
			return false;
		if (Float.floatToIntBits(minConsensusThresholdQ2) != Float.floatToIntBits(other.minConsensusThresholdQ2))
			return false;
		if (minInsertSize != other.minInsertSize)
			return false;
		if (minMappingQIntersect == null) {
			if (other.minMappingQIntersect != null)
				return false;
		} else if (!minMappingQIntersect.equals(other.minMappingQIntersect))
			return false;
		if (minMappingQualityQ1 != other.minMappingQualityQ1)
			return false;
		if (minMappingQualityQ2 != other.minMappingQualityQ2)
			return false;
		if (minMedianPhredQualityAtPosition != other.minMedianPhredQualityAtPosition)
			return false;
		if (minNumberDuplexesSisterArm != other.minNumberDuplexesSisterArm)
			return false;
		if (minQ1Q2DuplexesToCallMutation != other.minQ1Q2DuplexesToCallMutation)
			return false;
		if (minQ2DuplexesToCallMutation != other.minQ2DuplexesToCallMutation)
			return false;
		if (minReadMedianPhredScore != other.minReadMedianPhredScore)
			return false;
		if (minReadsPerStrandForDisagreement != other.minReadsPerStrandForDisagreement)
			return false;
		if (minReadsPerStrandQ1 != other.minReadsPerStrandQ1)
			return false;
		if (minReadsPerStrandQ2 != other.minReadsPerStrandQ2)
			return false;
		if (nConstantBarcodeMismatchesAllowed != other.nConstantBarcodeMismatchesAllowed)
			return false;
		if (nRecordsToProcess != other.nRecordsToProcess)
			return false;
		if (nVariableBarcodeMismatchesAllowed != other.nVariableBarcodeMismatchesAllowed)
			return false;
		if (noStatusMessages != other.noStatusMessages)
			return false;
		if (originalReadFile1 == null) {
			if (other.originalReadFile1 != null)
				return false;
		} else if (!originalReadFile1.equals(other.originalReadFile1))
			return false;
		if (originalReadFile2 == null) {
			if (other.originalReadFile2 != null)
				return false;
		} else if (!originalReadFile2.equals(other.originalReadFile2))
			return false;
		if (outputAlignmentFile == null) {
			if (other.outputAlignmentFile != null)
				return false;
		} else if (!outputAlignmentFile.equals(other.outputAlignmentFile))
			return false;
		if (outputCoverageBed != other.outputCoverageBed)
			return false;
		if (outputCoverageProto != other.outputCoverageProto)
			return false;
		if (outputDuplexDetails != other.outputDuplexDetails)
			return false;
		if (outputTopBottomDisagreementBED != other.outputTopBottomDisagreementBED)
			return false;
		if (parallelizationFactor != other.parallelizationFactor)
			return false;
		if (processingChunk != other.processingChunk)
			return false;
		if (Float.floatToIntBits(promoteFractionReads) != Float.floatToIntBits(other.promoteFractionReads))
			return false;
		if (promoteNQ1Duplexes != other.promoteNQ1Duplexes)
			return false;
		if (promoteNSingleStrands != other.promoteNSingleStrands)
			return false;
		if (readContigsFromFile != other.readContigsFromFile)
			return false;
		if (refSeqToOfficialGeneName == null) {
			if (other.refSeqToOfficialGeneName != null)
				return false;
		} else if (!refSeqToOfficialGeneName.equals(other.refSeqToOfficialGeneName))
			return false;
		if (referenceGenome == null) {
			if (other.referenceGenome != null)
				return false;
		} else if (!referenceGenome.equals(other.referenceGenome))
			return false;
		if (repetiveRegionBED == null) {
			if (other.repetiveRegionBED != null)
				return false;
		} else if (!repetiveRegionBED.equals(other.repetiveRegionBED))
			return false;
		if (reportBreakdownForBED == null) {
			if (other.reportBreakdownForBED != null)
				return false;
		} else if (!reportBreakdownForBED.equals(other.reportBreakdownForBED))
			return false;
		if (reportStatsForBED == null) {
			if (other.reportStatsForBED != null)
				return false;
		} else if (!reportStatsForBED.equals(other.reportStatsForBED))
			return false;
		if (reportStatsForNotBED == null) {
			if (other.reportStatsForNotBED != null)
				return false;
		} else if (!reportStatsForNotBED.equals(other.reportStatsForNotBED))
			return false;
		if (requireMatchInAlignmentEnd != other.requireMatchInAlignmentEnd)
			return false;
		if (rnaSeq != other.rnaSeq)
			return false;
		if (sampleNames == null) {
			if (other.sampleNames != null)
				return false;
		} else if (!sampleNames.equals(other.sampleNames))
			return false;
		if (saveBEDBreakdownTo == null) {
			if (other.saveBEDBreakdownTo != null)
				return false;
		} else if (!saveBEDBreakdownTo.equals(other.saveBEDBreakdownTo))
			return false;
		if (saveFilteredReadsTo == null) {
			if (other.saveFilteredReadsTo != null)
				return false;
		} else if (!saveFilteredReadsTo.equals(other.saveFilteredReadsTo))
			return false;
		if (saveRawReadsDB != other.saveRawReadsDB)
			return false;
		if (saveRawReadsMVDB != other.saveRawReadsMVDB)
			return false;
		if (skipVersionCheck != other.skipVersionCheck)
			return false;
		if (sortOutputAlignmentFile != other.sortOutputAlignmentFile)
			return false;
		if (startAtPositions == null) {
			if (other.startAtPositions != null)
				return false;
		} else if (!startAtPositions.equals(other.startAtPositions))
			return false;
		if (startServer == null) {
			if (other.startServer != null)
				return false;
		} else if (!startServer.equals(other.startServer))
			return false;
		if (startWorker == null) {
			if (other.startWorker != null)
				return false;
		} else if (!startWorker.equals(other.startWorker))
			return false;
		if (stopAtPositions == null) {
			if (other.stopAtPositions != null)
				return false;
		} else if (!stopAtPositions.equals(other.stopAtPositions))
			return false;
		if (submitToServer == null) {
			if (other.submitToServer != null)
				return false;
		} else if (!submitToServer.equals(other.submitToServer))
			return false;
		if (terminateImmediatelyUponError != other.terminateImmediatelyUponError)
			return false;
		if (terminateUponOutputFileError != other.terminateUponOutputFileError)
			return false;
		if (variableBarcodeLength != other.variableBarcodeLength)
			return false;
		if (verbosity != other.verbosity)
			return false;
		if (version != other.version)
			return false;
		return true;
	}

}

