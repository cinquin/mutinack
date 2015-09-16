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

import static uk.org.cinquin.mutinack.misc_util.Util.blueF;
import static uk.org.cinquin.mutinack.misc_util.Util.greenB;
import static uk.org.cinquin.mutinack.misc_util.Util.reset;
import static uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel.TERSE;
import static uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel.VERBOSE;
import static uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel.VERY_VERBOSE;

import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.features.LocusByLocusNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocOnly;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocation;
import uk.org.cinquin.mutinack.statistics.DivideByTwo;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.LongAdderFormatter;
import uk.org.cinquin.mutinack.statistics.MultiCounter;
import uk.org.cinquin.mutinack.statistics.PrintInStatus;
import uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel;
import uk.org.cinquin.mutinack.statistics.StatsCollector;
import uk.org.cinquin.mutinack.statistics.SwitchableStats;

public class AnalysisStats implements Serializable {
	
	
	private static final long serialVersionUID = -7786797851357308577L;

	@Retention(RetentionPolicy.RUNTIME)
	private @interface AddChromosomeBins {};

	OutputLevel outputLevel;

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rejectedIndelDistanceToLigationSite = new Histogram(200);	

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rejectedSubstDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtRejectedDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtAcceptedBaseDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram Q2CandidateDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram realQ2CandidateDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram substDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram insDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram delDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram disagDelSize = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram Q2CandidateDistanceToLigationSiteN = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtQ2CandidateQ1Q2Coverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtQ2CandidateQ1Q2CoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtQ2CandidateQ1Q2CoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram mutantQ2CandidateQ1Q2Coverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram mutantQ2CandidateQ1Q2DCoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram mutantQ2CandidateQ1Q2DCoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram uniqueMutantQ2CandidateQ1Q2DCoverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociExcluded = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nRecordsProcessed = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nRecordsInFile = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nRecordsUnmapped = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nRecordsBelowMappingQualityThreshold = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram mappingQualityKeptRecords = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram mappingQualityAllRecords = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram averageReadPhredQuality0 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram averageReadPhredQuality1 = new Histogram(500);	

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	final Histogram medianReadPhredQuality = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram medianLocusPhredQuality = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram averageDuplexReferenceDisagreementRate = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram duplexinsertSize = new Histogram(1000);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram duplexAverageNClipped = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram duplexInsert130_180averageNClipped = new Histogram(500);
	
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram duplexInsert100_130AverageNClipped = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram disagreementOrientationProportions1 = new Histogram(10);
	
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram disagreementOrientationProportions2 = new Histogram(10);
	
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector disagreementMatesSameOrientation = new StatsCollector();
	
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nComplexDisagreementsQ2 = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nRecordsIgnoredBecauseSecondary = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nRecordsNotInIntersection1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nRecordsNotInIntersection2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nTooLowMapQIntersect = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nLociDuplex = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nReadMedianPhredBelowThreshold = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nDuplexesTooMuchClipping = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder nDuplexesNoStats = new DoubleAdder();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder nDuplexesWithStats = new DoubleAdder();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nLociDuplexTooFewReadsPerStrand1 = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nLociDuplexTooFewReadsPerStrand2 = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nLociIgnoredBecauseTooHighCoverage = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nLociDuplexWithTopBottomDuplexDisagreementNoWT = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nLociDuplexWithTopBottomDuplexDisagreementNotASub = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawMismatchesQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawDeletionsQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawDeletionLengthQ1 = new Histogram(200);	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawInsertionsQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawInsertionLengthQ1 = new Histogram(200);	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawMismatchesQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawDeletionsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawDeletionLengthQ2 = new Histogram(200);	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawInsertionsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null, true);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawInsertionLengthQ2 = new Histogram(200);	

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomSubstDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false), null);
	
	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomDelDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null);

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomInsDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true), null);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false), null);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false), null);
	
	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomDisagreementsQ2TooHighCoverage = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> lackOfConsensus1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> lackOfConsensus2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(), null);

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<?> nLociDuplexesCandidatesForDisagreementQ2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nLociDuplexesCandidatesForDisagreementQ2TooHighCoverage = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociDuplexWithLackOfStrandConsensus1 = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociDuplexWithLackOfStrandConsensus2 = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociDuplexRescuedFromLeftRightBarcodeEquality = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociDuplexCompletePairOverlap = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociUncovered = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nQ2PromotionsBasedOnFractionReads = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociQualityPoor = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociQualityPoorA = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociQualityPoorT = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociQualityPoorG = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nLociQualityPoorC = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nMedianPhredAtLocusTooLow = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nFractionWrongPairsAtLocusTooHigh = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociQualityQ1 = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociQualityQ2 = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nLociQualityQ2OthersQ1Q2 = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nLociDuplexQualityQ2OthersQ1Q2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(color = "greenBackground", outputLevel = TERSE)
	public final MultiCounter<?> nLociCandidatesForUniqueMutation = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false));

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram nReadsAtLociQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram nReadsAtLociWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram nQ1Q2AtLociQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram nQ1Q2AtLociWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	@Nullable
	public transient OutputStreamWriter topBottomDisagreementWriter, mutationBEDWriter, coverageBEDWriter;

	@PrintInStatus(outputLevel = VERBOSE, description = "Q1 or Q2 duplex coverage histogram")
	public final Histogram Q1Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE, description = "Q2 duplex coverage histogram")
	public final Histogram Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Missing strands for loci that have no usable duplex")
	public final Histogram missingStrandsWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Top/bottom coverage imbalance for loci that have no usable duplex")
	public final Histogram strandCoverageImbalanceWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Histogram of copy number for duplex bottom strands")
	public final Histogram copyNumberOfDuplexBottomStrands = new Histogram(500) {
		private static final long serialVersionUID = 6597978073262739721L;
		private final NumberFormat formatter = new DecimalFormat("0.###E0");
		
		@Override
		public String toString() {
			final double nLociDuplexf = nLociDuplex.sum();
			return stream().map(a -> formatter.format((float) (a.sum() / nLociDuplexf))).
				collect(Collectors.toList()).toString();
		}
	};

	@PrintInStatus(outputLevel = VERY_VERBOSE,  description = "Histogram of copy number for duplex top strands")
	public final Histogram copyNumberOfDuplexTopStrands = new Histogram(500) {
		private static final long serialVersionUID = -8213283701959613589L;
		private final NumberFormat formatter = new DecimalFormat("0.###E0");

		@Override
		public String toString() {
			final double nLociDuplexf = nLociDuplex.sum();
			return stream().map(a -> formatter.format((float) (a.sum() / nLociDuplexf))).
				collect(Collectors.toList()).toString();
		}
	};

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Histogram of variable barcode mapping distance mismatch")
	public final Histogram sameBarcodeButPositionMismatch = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE, description = "Candidates with top good coverage")
	public final PriorityBlockingQueue<CandidateSequence> topQ2DuplexCoverage = new PriorityBlockingQueue<CandidateSequence>
	(100, (c1, c2) -> Integer.compare(c1.getnGoodDuplexes(),c2.getnGoodDuplexes())) {
		private static final long serialVersionUID = 8206630026701622788L;

		@Override
		public String toString() {
			return stream().collect(Collectors.toList()).toString();
		}
	};*/

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nBasesBelowPhredScore = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nConstantBarcodeMissing = new StatsCollector(), 
	nConstantBarcodeDodgy = new StatsCollector(), 
	nConstantBarcodeDodgyNStrand = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nReadsConstantBarcodeOK = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsConsidered = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsToA = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsToT = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsToG = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsToC = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nNs = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nCandidateInsertions = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nCandidateDeletions = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateWildtypeAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateSubstitutionsBeforeFirstNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateIndelAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nCandidateIndelBeforeFirstNBases = new StatsCollector();

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeCandidateExaminations = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeLeftEqual = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeRightEqual = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeMateDoesNotMatch = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeMateMatches = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodeMatchAfterPositionCheck = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nVariableBarcodesCloseMisses = new StatsCollector();*/

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nReadsInsertNoSize = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public final StatsCollector nReadsPairRF = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public final StatsCollector nReadsPairTandem = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public final StatsCollector nReadsInsertSizeAboveMaximum = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public final StatsCollector nReadsInsertSizeBelowMinimum = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nMateOutOfReach = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	final StatsCollector nProcessedBases = new StatsCollector();

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nProcessedFirst6BasesFirstOfPair = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nProcessedFirst6BasesSecondOfPair = new StatsCollector();*/

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	final DoubleAdder phredSumProcessedbases = new DoubleAdder();

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder phredSumFirst6basesFirstOfPair = new DoubleAdder();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder phredSumFirst6basesSecondOfPair = new DoubleAdder();*/
	
	public Map<String, int[]> locusByLocusCoverage;
	transient Builder locusByLocusCoverageProtobuilder;

	@SuppressWarnings("null")
	public void print(PrintStream stream, boolean colorize) {
		stream.println();
		NumberFormat formatter = NumberFormat.getInstance();
		for (Field field : AnalysisStats.class.getDeclaredFields()) {
			PrintInStatus annotation = field.getAnnotation(PrintInStatus.class);
			if ((annotation != null && annotation.outputLevel().compareTo(outputLevel) <= 0) || 
					(annotation == null && 
					(field.getType().equals(LongAdderFormatter.class) || 
							field.getType().equals(StatsCollector.class) ||
							field.getType().equals(Histogram.class)))) {
				try {
					if (annotation != null && annotation.color().equals("greenBackground")) {
						stream.print(greenB(colorize));
					}
					long divisor;
					if (field.getAnnotation(DivideByTwo.class) != null)
						divisor = 2;
					else
						divisor = 1;
					boolean hasDescription = annotation != null && ! annotation.description().equals("");
					stream.print(blueF(colorize) + (hasDescription ? annotation.description() : field.getName()) + ": " + reset(colorize));
					if (field.getType().equals(LongAdderFormatter.class)) {
						stream.println(formatter.format(((Long) longAdderFormatterSum.
								invoke(field.get(this)))/divisor));
					} else if (field.getType().equals(StatsCollector.class)) {
						Function<Long, Long> transformer = l-> l / divisor;
						stream.println((String) statsCollectorToString.invoke(field.get(this), transformer));
					} else if (field.getType().equals(MultiCounter.class)) {
						stream.println((String) multiCounterToString.invoke(field.get(this)));
					} else if (field.getType().equals(Histogram.class)) {
						stream.println((String) histogramToString.invoke(field.get(this)));
					} else if (field.getType().equals(DoubleAdder.class)) {
						stream.println((String) doubleAdderToString.invoke(field.get(this)));
					} else {
						stream.println(field.get(this).toString());
					}
				} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				} finally {
					if (annotation != null && annotation.color().equals("greenBackground")) {
						stream.print(reset(colorize));
					}
				}
			}
		}
	}

	private static final Method longAdderFormatterSum, statsCollectorToString, multiCounterToString, histogramToString,
	doubleAdderToString, turnOnMethod, turnOffMethod;
	static {
		try {
			longAdderFormatterSum = LongAdder.class.getDeclaredMethod("sum");
			statsCollectorToString = StatsCollector.class.getDeclaredMethod("toString", Function.class);
			multiCounterToString = MultiCounter.class.getDeclaredMethod("toString");
			histogramToString = Histogram.class.getDeclaredMethod("toString");
			doubleAdderToString = DoubleAdder.class.getDeclaredMethod("toString");
			turnOnMethod = SwitchableStats.class.getDeclaredMethod("turnOn");
			turnOffMethod = SwitchableStats.class.getDeclaredMethod("turnOff");
		} catch (NoSuchMethodException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public void setOutputLevel(OutputLevel level) {
		this.outputLevel = level;
		
		for (Field field : AnalysisStats.class.getDeclaredFields()) {
			if (!SwitchableStats.class.isAssignableFrom(field.getType())) {
				continue;
			}
			PrintInStatus annotation = field.getAnnotation(PrintInStatus.class);
			if (annotation == null) {
				continue;
			}

			try {
				if (annotation.outputLevel().compareTo(outputLevel) <= 0) {
					turnOnMethod.invoke(field.get(this));
				} else {
					turnOffMethod.invoke(field.get(this));				
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		} 
	}
	
	{	//Force output of fields annotated with AddChromosomeBins to be broken down by
		//bins for each contig (bin size as defined by CounterWithSeqLocation.BIN_SIZE, for now)
		//TODO Base this on the actual contigs the program is run on, not the default contigs
		List<String> contigNames = Parameters.defaultTruncateContigNames;
		for (Field field : AnalysisStats.class.getDeclaredFields()) {
			AddChromosomeBins annotation = field.getAnnotation(AddChromosomeBins.class);
			if (annotation != null) {
				for (int contig = 0; contig < contigNames.size(); contig++) {
					int contigCopy = contig;
					for (int c = 0; c < Parameters.defaultTruncateContigPositions.get(contig) / CounterWithSeqLocation.BIN_SIZE; c++) {
						int cCopy = c;
						try {
							MultiCounter <?> counter = ((MultiCounter<?>) field.get(this));
							counter.addPredicate(contigNames.get(contig) + "_bin_" + String.format("%03d", c), 
									loc -> {
										final int min = CounterWithSeqLocation.BIN_SIZE * cCopy;
										final int max = CounterWithSeqLocation.BIN_SIZE * (cCopy + 1);
										return loc.contigIndex == contigCopy &&
												loc.position >= min &&
												loc.position < max;
									});
							counter.accept(new SequenceLocation(contig, c * CounterWithSeqLocation.BIN_SIZE), 0);
						} catch (IllegalArgumentException | IllegalAccessException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}
		}
	}
	

	{
		//Force initialization of counters for all possible substitutions:
		//it is easier to put the output of multiple samples together if
		//unencountered substitutions have a 0-count entry
		for (byte mut: Arrays.asList((byte) 'A', (byte) 'T', (byte) 'G', (byte) 'C')) {
			for (byte wt: Arrays.asList((byte) 'A', (byte) 'T', (byte) 'G', (byte) 'C')) {
				if (wt == mut) {
					continue;
				}
				Mutation wtM = new Mutation(MutationType.WILDTYPE, wt, false, null, Util.emptyOptional());
				Mutation to = new Mutation(MutationType.SUBSTITUTION, wt, false, new byte [] {mut}, Util.emptyOptional());

				List<String> contigNames = Parameters.defaultTruncateContigNames;
				for (int contig = 0; contig < contigNames.size(); contig++) {
					for (int c = 0; c < Parameters.defaultTruncateContigPositions.get(contig) / CounterWithSeqLocation.BIN_SIZE; c++) {
							SequenceLocation location = new SequenceLocation(contig, c * CounterWithSeqLocation.BIN_SIZE);
							topBottomSubstDisagreementsQ2.accept(location, new ComparablePair<>(wtM, to), 0);
							codingStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
							templateStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
					}
				}
			}
		}
	}
}
