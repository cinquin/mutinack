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
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jdo.annotations.Extension;
import javax.jdo.annotations.Join;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.MutationHistogramMap;
import uk.org.cinquin.mutinack.output.LocationAnalysis;
import uk.org.cinquin.mutinack.qualities.Quality;
import uk.org.cinquin.mutinack.statistics.Actualizable;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocOnly;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocOnlyReportAll;
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

@PersistenceCapable
public class AnalysisStats implements Serializable, Actualizable {

	private @Final @Persistent @NonNull String name;
	OutputLevel outputLevel;
	final MutinackGroup groupSettings;
	@Final @Persistent Set<String> mutinackVersions = new HashSet<>();
	@Final @Persistent
	public Map<@NonNull String, @NonNull String> inputBAMHashes = new HashMap<>();
	@Final @Persistent @NonNull Parameters analysisParameters;
	@Final @Persistent boolean forInsertions;
	//Changed to Map instead of ConcurrentMap to please datanucleus
	@Final @Persistent @Join Map<SequenceLocation, LocationAnalysis> detections = new ConcurrentHashMap<>();
	public transient PrintStream detectionOutputStream;
	public transient OutputStreamWriter annotationOutputStream;
	public transient @Nullable OutputStreamWriter topBottomDisagreementWriter, noWtDisagreementWriter,
		mutationBEDWriter, coverageBEDWriter;
	public boolean canSkipDuplexLoading = false;

	public AnalysisStats(@NonNull String name,
			@NonNull Parameters param,
			boolean forInsertions,
			@NonNull MutinackGroup groupSettings,
			boolean reportCoverageAtAllPositions) {
		this.name = name;
		this.analysisParameters = param;
		this.forInsertions = forInsertions;
		this.groupSettings = groupSettings;

		if (reportCoverageAtAllPositions) {
			nPosDuplexCandidatesForDisagreementQ2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
			nPosDuplexCandidatesForDisagreementQ1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
			nPosDuplexQualityQ2OthersQ1Q2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
			nPosDuplexTooFewReadsPerStrand1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
			nPosDuplexTooFewReadsPerStrand2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
			nPosDuplexTooFewReadsAboveQ2Phred = new MultiCounter<>(null, () -> new CounterWithSeqLocOnlyReportAll(false, groupSettings));
		} else {
			nPosDuplexCandidatesForDisagreementQ2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
			nPosDuplexCandidatesForDisagreementQ1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
			nPosDuplexQualityQ2OthersQ1Q2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
			nPosDuplexTooFewReadsPerStrand1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
			nPosDuplexTooFewReadsPerStrand2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
			nPosDuplexTooFewReadsAboveQ2Phred = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		}

		nRecordsNotInIntersection1 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nRecordsNotInIntersection2 = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nTooLowMapQIntersect = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplex = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexBothStrandsPresent = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosIgnoredBecauseTooHighCoverage = new StatsCollector();
		nReadMedianPhredBelowThreshold = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		phredAndLigSiteDistance = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null, false);
		nDuplexesTooMuchClipping = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nDuplexesNoStats = new DoubleAdder();
		nDuplexesWithStats = new DoubleAdder();
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
		alleleFrequencies = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		topBottomDelDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null);
		topBottomIntronDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null);
		topBottomInsDisagreementsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, groupSettings), null);
		codingStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		templateStrandSubstQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(false, groupSettings), null);
		codingStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		templateStrandDelQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		codingStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		templateStrandInsQ2 = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		topBottomDisagreementsQ2TooHighCoverage = new MultiCounter<>(() -> new CounterWithSeqLocation<>(groupSettings), null);
		nPosDuplexCandidatesForDisagreementQ2TooHighCoverage = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosDuplexWithLackOfStrandConsensus1 = new StatsCollector();
		nPosDuplexWithLackOfStrandConsensus2 = new StatsCollector();
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
		nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));
		nPosCandidatesForUniqueMutation = new MultiCounter<>(null, () -> new CounterWithSeqLocOnly(false, groupSettings));

		nReadsInPrefetchQueue = new Histogram(1_000);

		{	//Force output of fields annotated with AddChromosomeBins to be broken down by
			//bins for each contig (bin size as defined by CounterWithSeqLocation.BIN_SIZE, for now)
			List<String> contigNames = groupSettings.getContigNames();
			for (Field field : AnalysisStats.class.getDeclaredFields()) {
				@Nullable AddChromosomeBins annotation = field.getAnnotation(AddChromosomeBins.class);
				if (annotation != null) {
					for (int contig = 0; contig < contigNames.size(); contig++) {
						int contigCopy = contig;
						for (int c = 0; c < Objects.requireNonNull(groupSettings.getContigSizes().get(
								contigNames.get(contig))) / groupSettings.BIN_SIZE; c++) {
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
								counter.accept(new SequenceLocation(contig,
									Objects.requireNonNull(contigNames.get(contig)), c * groupSettings.BIN_SIZE), 0);
							} catch (IllegalArgumentException | IllegalAccessException e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}
		}


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

				List<String> contigNames = groupSettings.getContigNames();
				for (int contig = 0; contig < contigNames.size(); contig++) {
					for (int c = 0; c < Objects.requireNonNull(groupSettings.getContigSizes().get(
							contigNames.get(contig))) / groupSettings.BIN_SIZE; c++) {
						SequenceLocation location = new SequenceLocation(contig,
							Objects.requireNonNull(contigNames.get(contig)), c * groupSettings.BIN_SIZE);
						topBottomSubstDisagreementsQ2.accept(location, new DuplexDisagreement(wtM, to, true, Quality.GOOD), 0);
						codingStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
						templateStrandSubstQ2.accept(location, new ComparablePair<>(wtM, to), 0);
					}
				}
			}
		}

		//Assert that annotations have not been discarded
		//This does not need to be done on every instance, but exceptions are better
		//handled in a non-static context
		try {
			Assert.isNonNull(AnalysisStats.class.getDeclaredField("duplexGroupingDepth").
				getAnnotation(PrintInStatus.class));
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public @NonNull String getName() {
		return name;
	}

	private static final long serialVersionUID = -7786797851357308577L;

	public double processingThroughput(long timeStartProcessing) {
		return (nRecordsProcessed.sum()) /
			((System.nanoTime() - timeStartProcessing) / 1_000_000_000d);
		//TODO Store start and stop times in AnalysisStats
	}

	@Retention(RetentionPolicy.RUNTIME)
	private @interface AddChromosomeBins {};

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized="true")
	@Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexGroupingDepth = new Histogram(100);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram minTopCandFreqQ2PosTopAlleleFreqOK = new Histogram(11);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram minTopCandFreqQ2PosTopAlleleFreqKO = new Histogram(11);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexTotalRecords = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rejectedIndelDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rejectedSubstDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram wtRejectedDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram wtAcceptedBaseDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram singleAnalyzerQ2CandidateDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram crossAnalyzerQ2CandidateDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	ConcurrentMap<Mutation, Histogram> disagMutConsensus = new MutationHistogramMap();

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	ConcurrentMap<Mutation, Histogram> disagWtConsensus = new MutationHistogramMap();

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram substDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram insDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram delDisagDistanceToLigationSite = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram disagDelSize = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram Q2CandidateDistanceToLigationSiteN = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram wtQ2CandidateQ1Q2Coverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram wtQ2CandidateQ1Q2CoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram wtQ2CandidateQ1Q2CoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram mutantQ2CandidateQ1Q2Coverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram mutantQ2CandidateQ1Q2DCoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram mutantQ2CandidateQ1Q2DCoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram uniqueMutantQ2CandidateQ1Q2DCoverage = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive = new Histogram(200);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosExcluded = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent StatsCollector nRecordsProcessed = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent StatsCollector ignoredUnpairedReads = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nRecordsInFile = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nRecordsUnmapped = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nRecordsBelowMappingQualityThreshold = new StatsCollector();

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram mappingQualityKeptRecords = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram mappingQualityAllRecords = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram averageReadPhredQuality0 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram averageReadPhredQuality1 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	MultiCounter<ComparablePair<Integer, Integer>> phredAndLigSiteDistance;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram medianReadPhredQuality = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram medianPositionPhredQuality = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram averageDuplexReferenceDisagreementRate = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexinsertSize = new Histogram(1000);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	double[] approximateReadInsertSize = null;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	double[] approximateReadInsertSizeRaw = null;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexAverageNClipped = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexInsert130_180averageNClipped = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexInsert100_130AverageNClipped = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram disagreementOrientationProportions1 = new Histogram(10);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram disagreementOrientationProportions2 = new Histogram(10);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector disagreementMatesSameOrientation = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nComplexDisagreementsQ2 = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nRecordsIgnoredBecauseSecondary = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nRecordsNotInIntersection1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nRecordsNotInIntersection2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nTooLowMapQIntersect;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplex;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexBothStrandsPresent;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nReadMedianPhredBelowThreshold;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nDuplexesTooMuchClipping;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent DoubleAdder nDuplexesNoStats;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent DoubleAdder nDuplexesWithStats;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexTooFewReadsPerStrand1;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexTooFewReadsPerStrand2;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexTooFewReadsAboveQ2Phred;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent StatsCollector nPosIgnoredBecauseTooHighCoverage;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexWithTopBottomDuplexDisagreementNoWT;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexWithTopBottomDuplexDisagreementNotASub;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawMismatchesQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> vBarcodeMismatches1M;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> vBarcodeMismatches2M;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> vBarcodeMismatches3OrMore;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawDeletionsQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rawDeletionLengthQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawInsertionsQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rawInsertionLengthQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawMismatchesQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawDeletionsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rawDeletionLengthQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<String, String>> rawInsertionsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram rawInsertionLengthQ2;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<DuplexDisagreement> topBottomSubstDisagreementsQ2;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<DuplexDisagreement> topBottomDelDisagreementsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<DuplexDisagreement> topBottomIntronDisagreementsQ2;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<List<Integer>> alleleFrequencies;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<DuplexDisagreement> topBottomInsDisagreementsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandSubstQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandSubstQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandDelQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandDelQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> codingStrandInsQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<ComparablePair<Mutation, Mutation>> templateStrandInsQ2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<DuplexDisagreement> topBottomDisagreementsQ2TooHighCoverage;

	@PrintInStatus(outputLevel = TERSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexCandidatesForDisagreementQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	@AddChromosomeBins
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexCandidatesForDisagreementQ1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexCandidatesForDisagreementQ2TooHighCoverage;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosDuplexWithLackOfStrandConsensus1;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosDuplexWithLackOfStrandConsensus2;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosDuplexCompletePairOverlap;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosUncovered;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nQ2PromotionsBasedOnFractionReads;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityPoor;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityPoorA;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityPoorT;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityPoorG;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityPoorC;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nConsensusQ1NotMet;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nMedianPhredAtPositionTooLow;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nFractionWrongPairsAtPositionTooHigh;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityQ1;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityQ2;

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent StatsCollector nPosQualityQ2OthersQ1Q2;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexQualityQ2OthersQ1Q2;

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate;

	@PrintInStatus(color = "greenBackground", outputLevel = TERSE)
	public @Final @Persistent(serialized = "true") MultiCounter<?> nPosCandidatesForUniqueMutation;

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram nReadsAtPosQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram nReadsAtPosWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexDistance = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram concurringMutationDuplexDistance = new Histogram(2_000);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram concurringDuplexDistance = new Histogram(2_000);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexLocalGroupSize = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexLocalShiftedGroupSize = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram nQ1Q2AtPosQualityQ2OthersQ1Q2 = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram nQ1Q2AtPosWithSomeCandidateForQ2UniqueMutation = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE, description = "Q1 or Q2 duplex coverage histogram")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram Q1Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERBOSE, description = "Q2 duplex coverage histogram")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram Q2DuplexCoverage = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Missing strands for positions that have no usable duplex")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram missingStrandsWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Top/bottom coverage imbalance for positions that have no usable duplex")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram strandCoverageImbalanceWhenNoUsableDuplex = new Histogram(500);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Histogram of copy number for duplex bottom strands")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram copyNumberOfDuplexBottomStrands = new Histogram(500) {
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
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram copyNumberOfDuplexTopStrands = new Histogram(500) {
		private static final long serialVersionUID = -8213283701959613589L;
		private final NumberFormat formatter = new DecimalFormat("0.###E0");

		@Override
		public String toString() {
			final double nPosDuplexf = nPosDuplex.sum();
			return stream().map(a -> formatter.format((float) (a.sum() / nPosDuplexf))).
				collect(Collectors.toList()).toString();
		}
	};

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Duplex collision probability")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexCollisionProbability = new Histogram(100);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Duplex collision probability at Q2 sites")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexCollisionProbabilityAtQ2 = new Histogram(100);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Duplex collision probability when both strands represented")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexCollisionProbabilityWhen2Strands = new Histogram(100);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Duplex collision probability at disagreement")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexCollisionProbabilityAtDisag = new Histogram(100);

	@PrintInStatus(outputLevel = VERY_VERBOSE, description = "Average duplex collision probability for duplexes covering genome position of disagreement")
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram duplexCollisionProbabilityLocalAvAtDisag = new Histogram(100);

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
	public @Final @Persistent StatsCollector nBasesBelowPhredScore = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nConstantBarcodeMissing = new StatsCollector(),
	nConstantBarcodeDodgy = new StatsCollector(),
	nConstantBarcodeDodgyNStrand = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nReadsConstantBarcodeOK = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsConsidered = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsToA = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsToT = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsToG = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsToC = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nNs = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent StatsCollector nCandidateInsertions = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	public @Final @Persistent StatsCollector nCandidateDeletions = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateWildtypeAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateSubstitutionsBeforeFirstNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateIndelAfterLastNBases = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nCandidateIndelBeforeFirstNBases = new StatsCollector();

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
	public @Final @Persistent StatsCollector nReadsOpticalDuplicates = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram readDistance = new Histogram(50);

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nReadsInsertNoSize = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public @Final @Persistent StatsCollector nReadsPairRF = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public @Final @Persistent StatsCollector nReadsPairTandem = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public @Final @Persistent StatsCollector nReadsInsertSizeAboveMaximum = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@DivideByTwo public @Final @Persistent StatsCollector nReadsInsertSizeBelowMinimum = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public @Final @Persistent StatsCollector nMateOutOfReach = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	@Final @Persistent StatsCollector nProcessedBases = new StatsCollector();

	@PrintInStatus(outputLevel = TERSE)
	boolean analysisTruncated;

	@PrintInStatus(outputLevel = VERBOSE)
	@Final @Persistent(serialized = "true") @Extension(vendorName = "datanucleus", key = "is-second-class", value="false")
	Histogram nReadsInPrefetchQueue;

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nProcessedFirst6BasesFirstOfPair = new StatsCollector();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final StatsCollector nProcessedFirst6BasesSecondOfPair = new StatsCollector();*/

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	@Final @Persistent DoubleAdder phredSumProcessedbases = new DoubleAdder();

	/*
	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder phredSumFirst6basesFirstOfPair = new DoubleAdder();

	@PrintInStatus(outputLevel = VERY_VERBOSE)
	public final DoubleAdder phredSumFirst6basesSecondOfPair = new DoubleAdder();*/

	public @Persistent Map<String, int[]> positionByPositionCoverage;
	transient Builder positionByPositionCoverageProtobuilder;

	@SuppressWarnings("null")
	public void print(PrintStream stream, boolean colorize) {
		stream.println();
		NumberFormat formatter = DoubleAdderFormatter.nf.get();
		ConcurrentModificationException cme = null;
		for (Field field: AnalysisStats.class.getDeclaredFields()) {
			PrintInStatus annotation = field.getAnnotation(PrintInStatus.class);
			if ((annotation != null && annotation.outputLevel().compareTo(outputLevel) <= 0) ||
					(annotation == null &&
					(field.getType().equals(LongAdderFormatter.class) ||
							field.getType().equals(StatsCollector.class) ||
							field.getType().equals(Histogram.class)))) {
				try {
					final Object fieldValue = field.get(this);
					if (fieldValue == null) {
						continue;
					}
					if (annotation != null && annotation.color().equals("greenBackground")) {
						stream.print(greenB(colorize));
					}
					long divisor;
					if (field.getAnnotation(DivideByTwo.class) != null)
						divisor = 2;
					else
						divisor = 1;
					boolean hasDescription = annotation != null && !annotation.description().isEmpty();
					stream.print(blueF(colorize) + (hasDescription ? annotation.description() : field.getName()) +
						": " + reset(colorize));
					if (field.getType().equals(LongAdderFormatter.class)) {
						stream.println(formatter.format(((Long) longAdderFormatterSum.
								invoke(fieldValue)) / divisor));
					} else if (field.getType().equals(StatsCollector.class)) {
						Function<Long, Long> transformer = l-> l / divisor;
						stream.println((String) statsCollectorToString.invoke(fieldValue, transformer));
					} else {
						stream.println(fieldValue.toString());
					}
				} catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
					throw new RuntimeException(e);
				} catch (ConcurrentModificationException e) {
					cme = e;
				} finally {
					if (annotation != null && annotation.color().equals("greenBackground")) {
						stream.print(reset(colorize));
					}
				}
			}
		}
		if (cme != null) {
			throw cme;
		}
	}

	private static final Method longAdderFormatterSum, statsCollectorToString, turnOnMethod, turnOffMethod;
	static {
		try {
			longAdderFormatterSum = LongAdder.class.getDeclaredMethod("sum");
			statsCollectorToString = StatsCollector.class.getDeclaredMethod("toString", Function.class);
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
			@Nullable PrintInStatus annotation = field.getAnnotation(PrintInStatus.class);
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
					if (field.get(this) != null) {
						((Actualizable) field.get(this)).actualize();
					}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			};
		}
	}

}
