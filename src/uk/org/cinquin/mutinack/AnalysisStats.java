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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.statistics.Actualizable;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocOnly;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocation;
import uk.org.cinquin.mutinack.statistics.DivideByTwo;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.LongAdderFormatter;
import uk.org.cinquin.mutinack.statistics.MultiCounter;
import uk.org.cinquin.mutinack.statistics.PrintInStatus;
import uk.org.cinquin.mutinack.statistics.PrintInStatus.OutputLevel;
import uk.org.cinquin.mutinack.statistics.StatsCollector;
import uk.org.cinquin.mutinack.statistics.SwitchableStats;
import uk.org.cinquin.mutinack.statistics.Traceable;

public class AnalysisStats implements Serializable, Actualizable {
	
	private final @NonNull String name;
	OutputLevel outputLevel;
	final MutinackGroup groupSettings;
	
	public AnalysisStats(@NonNull String name, MutinackGroup groupSettings) {
		this.name = name;
		this.groupSettings = groupSettings;
		
		nRecordsNotInIntersection1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nRecordsNotInIntersection2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nTooLowMapQIntersect = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplex = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nReadMedianPhredBelowThreshold = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		phredAndLigSiteDistance = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, false);
		nDuplexesTooMuchClipping = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nDuplexesNoStats = new DoubleAdder();
		nDuplexesWithStats = new DoubleAdder();
		nPosDuplexTooFewReadsPerStrand1 = new StatsCollector();
		nPosDuplexTooFewReadsPerStrand2 = new StatsCollector();
		nPosIgnoredBecauseTooHighCoverage = new StatsCollector();
		nPosDuplexWithTopBottomDuplexDisagreementNoWT = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexWithTopBottomDuplexDisagreementNotASub = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		rawMismatchesQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		vBarcodeMismatches1M = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		vBarcodeMismatches2M = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		vBarcodeMismatches3OrMore = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawDeletionsQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawDeletionLengthQ1 = new Histogram(200);	
		rawInsertionsQ1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawInsertionLengthQ1 = new Histogram(200);	
		rawMismatchesQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawDeletionsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawDeletionLengthQ2 = new Histogram(200);	
		rawInsertionsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, true);
		rawInsertionLengthQ2 = new Histogram(200);	
		topBottomSubstDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		topBottomDelDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null);
		topBottomInsDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null);
		codingStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		templateStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		codingStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		templateStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		codingStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		templateStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		topBottomDisagreementsQ2TooHighCoverage = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		lackOfConsensus1 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		lackOfConsensus2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		nPosDuplexCandidatesForDisagreementQ2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexCandidatesForDisagreementQ2TooHighCoverage = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexWithLackOfStrandConsensus1 = new StatsCollector();
		nPosDuplexWithLackOfStrandConsensus2 = new StatsCollector();
		nPosDuplexRescuedFromLeftRightBarcodeEquality = new StatsCollector();
		nPosDuplexCompletePairOverlap = new StatsCollector();
		nPosUncovered = new StatsCollector();
		nQ2PromotionsBasedOnFractionReads = new StatsCollector();
		nPosQualityPoor = new StatsCollector();
		nPosQualityPoorA = new StatsCollector();
		nPosQualityPoorT = new StatsCollector();
		nPosQualityPoorG = new StatsCollector();
		nPosQualityPoorC = new StatsCollector();
		nConsensusQ1NotMet = new StatsCollector();
		nMedianPhredAtPositionTooLow = new StatsCollector();
		nFractionWrongPairsAtPositionTooHigh = new StatsCollector();
		nPosQualityQ1 = new StatsCollector();
		nPosQualityQ2 = new StatsCollector();
		nPosQualityQ2OthersQ1Q2 = new StatsCollector();
		nPosDuplexQualityQ2OthersQ1Q2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosCandidatesForUniqueMutation = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		
		{	//Force output of fields annotated with AddChromosomeBins to be broken down by
			//bins for each contig (bin size as defined by CounterWithSeqLocation.BIN_SIZE, for now)
			//TODO Base this on the actual contigs the program is run on, not the default contigs
			List<String> contigNames = Parameters.defaultTruncateContigNames;
			for (Field field : AnalysisStats.class.getDeclaredFields()) {
				AddChromosomeBins annotation = field.getAnnotation(AddChromosomeBins.class);
				if (annotation != null) {
					for (int contig = 0; contig < contigNames.size(); contig++) {
						int contigCopy = contig;
						for (int c = 0; c < Parameters.defaultTruncateContigPositions.get(contig) / groupSettings.BIN_SIZE; c++) {
							int cCopy = c;
							try {
								MultiCounter <?> counter = ((MultiCounter<?>) field.get(this));
								counter.addPredicate(contigNames.get(contig) + "_bin_" + String.format("%03d", c), 
										loc -> {
											final int min = groupSettings.BIN_SIZE * cCopy;
											final int max = groupSettings.BIN_SIZE * (cCopy + 1);
											return loc.contigIndex == contigCopy &&
													loc.position >= min &&
													loc.position < max;
										});
								counter.accept(new SequenceLocation(contig, contigNames.get(contig), c * groupSettings.BIN_SIZE), 0);
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
						for (int c = 0; c < Parameters.defaultTruncateContigPositions.get(contig) / groupSettings.BIN_SIZE; c++) {
							SequenceLocation location = new SequenceLocation(contig, contigNames.get(contig), c * groupSettings.BIN_SIZE);
							topBottomSubstDisagreementsQ2.accept(location, new ComparablePair<>(wtM, to), 0);
							codingStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
							templateStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
						}
					}
				}
			}
		}
	}
	
	public @NonNull String getName() {
		return name;
	}

	private static final long serialVersionUID = -7786797851357308577L;

	@Retention(RetentionPolicy.RUNTIME)
	private @interface AddChromosomeBins {};

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram duplexGroupingDepth = new Histogram(100);
	
	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram duplexTotalRecords = new Histogram(500);
	
	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rejectedIndelDistanceToLigationSite = new Histogram(200);	

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rejectedSubstDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtRejectedDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram wtAcceptedBaseDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram singleAnalyzerQ2CandidateDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram crossAnalyzerQ2CandidateDistanceToLigationSite = new Histogram(200);

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
	public final StatsCollector nPosExcluded = new StatsCollector();

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
	
	@PrintInStatus(outputLevel = VERBOSE)
	final MultiCounter<ComparablePair<Integer, Integer>> phredAndLigSiteDistance;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	final Histogram medianReadPhredQuality = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram medianPositionPhredQuality = new Histogram(500);

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
	public final MultiCounter<?> nRecordsNotInIntersection1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nRecordsNotInIntersection2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nTooLowMapQIntersect;

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nPosDuplex;

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nReadMedianPhredBelowThreshold;

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nDuplexesTooMuchClipping;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder nDuplexesNoStats;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder nDuplexesWithStats;

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nPosDuplexTooFewReadsPerStrand1;

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nPosDuplexTooFewReadsPerStrand2;

	@PrintInStatus(outputLevel = TERSE)
	public final StatsCollector nPosIgnoredBecauseTooHighCoverage;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nPosDuplexWithTopBottomDuplexDisagreementNoWT;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nPosDuplexWithTopBottomDuplexDisagreementNotASub;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawMismatchesQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> vBarcodeMismatches1M;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> vBarcodeMismatches2M;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> vBarcodeMismatches3OrMore;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawDeletionsQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawDeletionLengthQ1;	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawInsertionsQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawInsertionLengthQ1;	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawMismatchesQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawDeletionsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawDeletionLengthQ2;	

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<String, String>> rawInsertionsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	final Histogram rawInsertionLengthQ2;	

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomSubstDisagreementsQ2;
	
	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomDelDisagreementsQ2;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomInsDisagreementsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandSubstQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandSubstQ2;
	
	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandDelQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandDelQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandInsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandInsQ2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> topBottomDisagreementsQ2TooHighCoverage;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> lackOfConsensus1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<ComparablePair<Mutation, Mutation>> lackOfConsensus2;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public final MultiCounter<?> nPosDuplexCandidatesForDisagreementQ2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final MultiCounter<?> nPosDuplexCandidatesForDisagreementQ2TooHighCoverage;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosDuplexWithLackOfStrandConsensus1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosDuplexWithLackOfStrandConsensus2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosDuplexRescuedFromLeftRightBarcodeEquality;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosDuplexCompletePairOverlap;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nPosUncovered;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nQ2PromotionsBasedOnFractionReads;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nPosQualityPoor;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosQualityPoorA;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosQualityPoorT;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosQualityPoorG;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nPosQualityPoorC;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nConsensusQ1NotMet;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nMedianPhredAtPositionTooLow;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nFractionWrongPairsAtPositionTooHigh;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nPosQualityQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nPosQualityQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public final StatsCollector nPosQualityQ2OthersQ1Q2;

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nPosDuplexQualityQ2OthersQ1Q2;

	@PrintInStatus(outputLevel = TERSE)
	public final MultiCounter<?> nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate;

	@PrintInStatus(color = "greenBackground", outputLevel = TERSE)
	public final MultiCounter<?> nPosCandidatesForUniqueMutation;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram nReadsAtPosQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final Histogram nReadsAtPosWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram nQ1Q2AtPosQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public final Histogram nQ1Q2AtPosWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	public transient @Nullable OutputStreamWriter topBottomDisagreementWriter, mutationBEDWriter, coverageBEDWriter;

	@PrintInStatus(outputLevel = VERBOSE, description = "Q1 or Q2 duplex coverage histogram")
	public final Histogram Q1Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE, description = "Q2 duplex coverage histogram")
	public final Histogram Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Missing strands for positions that have no usable duplex")
	public final Histogram missingStrandsWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Top/bottom coverage imbalance for positions that have no usable duplex")
	public final Histogram strandCoverageImbalanceWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Histogram of copy number for duplex bottom strands")
	public final Histogram copyNumberOfDuplexBottomStrands = new Histogram(500) {
		private static final long serialVersionUID = 6597978073262739721L;
		private final NumberFormat formatter = new DecimalFormat("0.###E0");
		
		@Override
		public String toString() {
			final double nPosDuplexf = nPosDuplex.sum();
			return stream().map(a -> formatter.format((float) (a.sum() / nPosDuplexf))).
				collect(Collectors.toList()).toString();
		}
	};

	@PrintInStatus(outputLevel = VERY_VERBOSE,  description = "Histogram of copy number for duplex top strands")
	public final Histogram copyNumberOfDuplexTopStrands = new Histogram(500) {
		private static final long serialVersionUID = -8213283701959613589L;
		private final NumberFormat formatter = new DecimalFormat("0.###E0");

		@Override
		public String toString() {
			final double nPosDuplexf = nPosDuplex.sum();
			return stream().map(a -> formatter.format((float) (a.sum() / nPosDuplexf))).
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
	
	public Map<String, int[]> positionByPositionCoverage;
	transient Builder positionByPositionCoverageProtobuilder;

	@SuppressWarnings("null")
	public void print(PrintStream stream, boolean colorize) {
		stream.println();
		NumberFormat formatter = DoubleAdderFormatter.nf.get();
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
			e.printStackTrace();
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

	public void traceField(String fieldName, String prefix) throws NoSuchFieldException,
	SecurityException, IllegalArgumentException, IllegalAccessException {
		Object o = this.getClass().getDeclaredField(fieldName).get(this);
		if (o instanceof Traceable) {
			((Traceable) o).setPrefix(prefix);
		} else {
			throw new IllegalArgumentException("Field " + fieldName +
					" not currently traceable");
		}
	}

	@Override
	public void actualize() {
		for (Field field: AnalysisStats.class.getDeclaredFields()) {
			if (!Actualizable.class.isAssignableFrom(field.getType())) {
				continue;
			}
			try {
				((Actualizable) field.get(this)).actualize();
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			};
		}
	}
	
}
