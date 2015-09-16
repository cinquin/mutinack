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

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.WrappedParameter;
import com.beust.jcommander.converters.BaseConverter;

import uk.org.cinquin.mutinack.misc_util.Handle;

public class Parameters implements Serializable {

	public static final long serialVersionUID = 1L;
	private static final boolean hideInProgressParameters = true;
	private static final boolean hideComplicatedParameters = true;
	
	@Parameter(names = {"-help", "--help"}, help = true, description = "Display this message and return")
	@HideInToString
	public boolean help;
	
	@Parameter(names = {"-version", "--version"}, help = true, description = "Display version information and return")
	@HideInToString
	public boolean version;
	
	@Parameter(names = "-noStatusMessages", description = "Do not output any status information on stderr or stdout", required = false)
	@HideInToString
	public boolean noStatusMessages = false;
	
	@Parameter(names = "-skipVersionCheck", description = "Do not check whether update is available for download", required = false)
	@HideInToString
	public boolean skipVersionCheck = false;

	@Parameter(names = "-verbosity", description = "0: main mutation detection results only; 3: open the firehose", required = false)
	public int verbosity = 0;
	
	@Parameter(names = "-outputDuplexDetails", description = "For each reported mutation, give list of its reads and duplexes", required = false)
	public boolean outputDuplexDetails = false;
		
	@Parameter(names = "-parallelizationFactor", description = "Number of chunks into which to split each contig for parallel processing; setting this value too high can be highly counter-productive", required = false)
	public int parallelizationFactor = 1;
	
	@Parameter(names = "-terminateImmediatelyUponError", description = "If true, any error causes immediate termination of the run", required = false)
	public boolean terminateImmediatelyUponError = true;

	@Parameter(names = "-terminateUponOutputFileError", description = "If true, any error in writing auxiliary output files causes termination of the run", required = false)
	public boolean terminateUponOutputFileError = true;
	
	@Parameter(names = "-processingChunk", description = "Size of sliding windows used to synchronize analysis in different samples", required = false)
	public int processingChunk = 160;
	
	@Parameter(names = "-inputReads", description = "Input BAM read file, sorted and with an index; repeat as many times as there are samples", required = true)
	public List<@NonNull String> inputReads = new ArrayList<>();
	
	@Parameter(names = "-lenientSamValidation",description = "Passed to Picard; seems at least sometimes necessary for"
			+ " alignments produced by BWA", required = false)
	public boolean lenientSamValidation = true;
	
	@Parameter(names = "-originalReadFile1", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile1 = new ArrayList<>();

	@Parameter(names = "-originalReadFile2", description = "Fastq-formatted raw read data", required = false, hidden = true)
	public List<@NonNull String> originalReadFile2 = new ArrayList<>();
	
	@Parameter(names = "-nRecordsToProcess", description = "Only process first N reads", required = false)
	public long nRecordsToProcess = Long.MAX_VALUE;

	@Parameter(names = "-dropReadProbability", description = "Reads will be randomly ignored with a probability given by this number")
	public float dropReadProbability = 0;
	
	@Parameter(names = "-intersectAlignment", description = "List of BAM files with which alignments in inputReads must agree; each file must be sorted", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> intersectAlignment = new ArrayList<>();
	
	@Parameter(names = "-minMappingQIntersect", description = "Minimum mapping quality for reads in intersection files", required = false, hidden = hideInProgressParameters)
	public List<Integer> minMappingQIntersect = new ArrayList<>();

	@Parameter(names = "-referenceGenome", description = "Reference genome in FASTA format; index file must be present and for now contigs must appear in alphabetical order",
			required = true)
	public String referenceGenome = "";
	
	@SuppressWarnings("null")
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
	
	@Parameter(names = "-promoteNQ1Duplexes", description = "NOT YET FUNCTIONAL - Promote candidate that has at least this many Q1 duplexes to Q2", required = false, hidden = hideComplicatedParameters)
	public int promoteNQ1Duplexes = Integer.MAX_VALUE;
	
	@Parameter(names = "-promoteNSingleStrands", description = "NOT YET FUNCTIONAL - Promote duplex that has just 1 original strand but at least this many reads to Q1", required = false, hidden = hideComplicatedParameters)
	public int promoteNSingleStrands = Integer.MAX_VALUE;
	
	@Parameter(names = "-promoteFractionReads", description = "Promote candidate supported by at least this fraction of reads to Q2", required = false, hidden = hideComplicatedParameters)
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
	
	@Parameter(names = "-minMedianPhredQualityAtLocus", description = "Loci whose median Phred quality score is below this threshold are not used to propose mutation candidates"
			, required = false)
	public int minMedianPhredQualityAtLocus = 0;
	
	@Parameter(names = "-maxFractionWrongPairsAtLocus", description = "Loci are not used to propose mutation candidates if the fraction of reads covering the locus that have an unmapped mate or a mate that forms a wrong pair orientiation (RF, Tandem) is above this threshold"
			, required = false)
	public float maxFractionWrongPairsAtLocus = 1.0f;
	
	@Parameter(names = "-maxAverageBasesClipped", description = "Duplexes whose mean number of clipped bases is above this threshold are not used to propose mutation candidates"
			, required = false)
	public int maxAverageBasesClipped = 15;
	
	@Parameter(names = "-maxAverageClippingOfAllCoveringDuplexes", description = "Loci whose average covering duplex average number of clipped bases is above this threshold are not used to propose mutation candidates"
			, required = false)
	public int maxAverageClippingOfAllCoveringDuplexes = 999;
	
	@Parameter(names = "-maxNDuplexes", description = "Loci whose number of Q1 or Q2 duplexes is above this threshold are ignored when computing mutation rates"
			, required = false)
	public List<Integer> maxNDuplexes = new ArrayList<>();
	
	@Parameter(names = "-maxInsertSize", description = "Inserts above this size are not used to propose Q2 mutation candidates, and will most of the time be ignored when identifying Q1 candidates", required = false)
	public int maxInsertSize = 1_000;

	@Parameter(names = "-minInsertSize", description = "Inserts below this size are not used to propose mutation candidates", required = false)
	public int minInsertSize = 0;
	
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

	@Parameter(names = "-saveFilteredReadsTo", description = "Not implemented; write raw reads that were kept for analysis to specified files", required = false, hidden = hideInProgressParameters)
	public List<@NonNull String> saveFilteredReadsTo = new ArrayList<>();
	
	@Parameter(names = "-collapseFilteredReads", description = "Only write one (randomly-chosen) read per duplex", required = false, hidden = false)
	public boolean collapseFilteredReads = false;
	
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
	
	@Parameter(names = "-forceOutputAtPositionsFile", description = "Detailed information is reported for all positions listed in the file", required = false)
	public String forceOutputAtPositionsFile = null;

	@Parameter(names = "-outputAlignmentFile", description = "Write BAM output with duplex information provided in custom tags; " +
			"note that a read may be omitted from the output, e.g. if it falls below a Q1 threshold, if predicted insert size is 0, "
			+ "and under some circumstances if the mate mapping quality is below Q1 threshold, if the mate is mapped further away "
			+ "than possible according to maximum insert size, or if it falls outside of the specified processing window (it is "
			+ "relatively rare but possible for a read to be omitted even though it counts toward coverage).", required = false)
	public String outputAlignmentFile = null;

	@Parameter(names = "-discardedReadFile", description = "Write discarded reads to BAM file specified by parameter", required = false, hidden = hideInProgressParameters)
	public String discardedReadFile = null;

	@Parameter(names = "-logReadIssuesInOutputBam", description = "Use custom fields in output BAM to give reasons why duplexes as a whole or individual bases did not reach maximum quality", required = false, arity = 1)
	public boolean logReadIssuesInOutputBam = true;
	
	@Parameter(names = "-sortOutputAlignmentFile", description = "Sort BAM file; can require a large amount of memory", required = false, arity = 1)
	public boolean sortOutputAlignmentFile = false;
	
	@Parameter(names = "-outputTopBottomDisagreementBED", description = "Output to file specified by option -topBottomDisagreementFileBaseName", required = false, arity = 1)
	public boolean outputTopBottomDisagreementBED = true;
	
	@Parameter(names = "-reportStatsForBED", description = "Report number of observations that fall within " +
			"the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForBED = new ArrayList<>();
	
	@Parameter(names = "-reportStatsForNotBED", description = "Report number of observations that do *not* fall within " +
			"the union of regions listed by BED file whose path follows", required = false)
	public List<@NonNull String> reportStatsForNotBED = new ArrayList<>();
	
	@Parameter(names = "-excludeRegionsInBED", description = "Loci covered by this BED file will be completely ignored in the analysis", required = false)
	public List<@NonNull String> excludeRegionsInBED = new ArrayList<>();
	
	@Parameter(names = "-repetiveRegionBED", description = "If specified, used for stats (mutant|wt)Q2CandidateQ1Q2DCoverage[Non]Repetitive", required = false)
	public List<@NonNull String> repetiveRegionBED = new ArrayList<>();
	
	@Parameter(names = "-bedDisagreementOrienter", description = "Gene orientation read from this file "
			+ "is used to orient top/bottom strand disagreements with respect to transcribed strand", required = false)
	public String bedDisagreementOrienter = null;
		
	@Parameter(names = "-reportBreakdownForBED", description = "Report number of observations that fall within " +
			"each of the regions defined by BED file whose path follows", required = false)
	public List<@NonNull String> reportBreakdownForBED = new ArrayList<>();
	
	@Parameter(names = "-saveBEDBreakdownTo", description = "Path for saving of BED region counts; argument " +
			"list must match that given to -reportBreakdownForBED", required = false)
	public List<@NonNull String> saveBEDBreakdownTo = new ArrayList<>();
	
	@Parameter(names = "-bedFeatureSuppInfoFile", description = "Read genome annotation supplementary info, used in output of counter with BED feature breakdown")
	public String bedFeatureSuppInfoFile = null;

	@Parameter(names = "-refSeqToOfficialGeneName", description = "Tab separated text file with RefSeq ID, tab, and official gene name and any other useful info; " +
			"counts will be reported both by RefSeq ID and official gene name")
	public String refSeqToOfficialGeneName = null;
	
	@Parameter(names = "-auxOutputFileBaseName", description = "Base name of files to which to record mutations, disagreements between top and bottom strands, etc.", required = false)
	public String auxOutputFileBaseName = null;
	
	@Parameter(names = "-rnaSeq", description = "Ignore deletions and turn off checks that do not make sense for RNAseq data", required = false)
	public boolean rnaSeq = false;
	
	@Retention(RetentionPolicy.RUNTIME)
	/**
	 * Used to mark parameters that it is not useful to print in toString method.
	 * @author olivier
	 *
	 */
	public @interface HideInToString {}
	
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
	private static final Parameters defaultValues = new Parameters();
	
	@Override
	public String toString() {
		String defaultValuesString = "";
		String nonDefaultValuesString = "";
		for (Field field: Parameters.class.getDeclaredFields()) {
			try {
				if (field.getAnnotation(HideInToString.class) != null)
					continue;
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

}

