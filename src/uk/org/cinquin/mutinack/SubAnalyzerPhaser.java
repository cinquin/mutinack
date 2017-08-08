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

import static contrib.uk.org.lidalia.slf4jext.Level.TRACE;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.AT_LEAST_ONE_DISAG;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.DISAG_THAT_MISSED_Q2;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MIN_DUPLEXES_SISTER_SAMPLE;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.TOO_HIGH_COVERAGE;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.mediumLengthFloatFormatter;
import static uk.org.cinquin.mutinack.qualities.Quality.GOOD;
import static uk.org.cinquin.mutinack.qualities.Quality.POOR;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.block.factory.Procedures;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.SAMRecord;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.IntMinMax;
import uk.org.cinquin.mutinack.misc_util.ObjMinMax;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.PositionAssayToQualityMap;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.CrossSampleLocationAnalysis;
import uk.org.cinquin.mutinack.output.LocationAnalysis;
import uk.org.cinquin.mutinack.output.LocationExaminationResults;
import uk.org.cinquin.mutinack.output.LocationExaminationResults.FloatPair;
import uk.org.cinquin.mutinack.qualities.Quality;
import uk.org.cinquin.mutinack.sequence_IO.TrimOverlappingReads;
import uk.org.cinquin.mutinack.statistics.Histogram;

public class SubAnalyzerPhaser extends Phaser {

	private static final Logger logger = LoggerFactory.getLogger("SubAnalyzerPhaser");
	private static final byte[] QUESTION_MARK = {'?'};

	private final @NonNull AnalysisChunk analysisChunk;
	private final @NonNull MutinackGroup groupSettings;
	private final @NonNull SettableInteger previousLastProcessable;
	private final @NonNull Map<SequenceLocation, Boolean> forceOutputAtLocations;
	private final @NonNull Histogram dubiousOrGoodDuplexCovInAllInputs;
	private final @NonNull Histogram goodDuplexCovInAllInputs;
	private final @NonNull Parameters param;
	private final @NonNull List<GenomeFeatureTester> excludeBEDs;
	private final @NonNull List<@NonNull BedReader> repetitiveBEDs;
	private final int contigIndex;
	private final @NonNull String contigName;
	private final int PROCESSING_CHUNK;

	private final boolean outputReads;
	private final int nSubAnalyzers;

	private int nIterations = 0;
	private final @NonNull AtomicInteger dn = new AtomicInteger(0);

	public SubAnalyzerPhaser(@NonNull Parameters param,
			@NonNull AnalysisChunk analysisChunk,
			boolean outputReads,
			@NonNull Map<SequenceLocation, Boolean> forceOutputAtLocations,
			@NonNull Histogram dubiousOrGoodDuplexCovInAllInputs,
			@NonNull Histogram goodDuplexCovInAllInputs,
			@NonNull String contigName,
			int contigIndex,
			@NonNull List<GenomeFeatureTester> excludeBEDs,
			@NonNull List<@NonNull BedReader> repetitiveBEDs,
			int PROCESSING_CHUNK) {
		this.param = param;
		this.analysisChunk = analysisChunk;
		this.groupSettings = analysisChunk.groupSettings;
		previousLastProcessable = new SettableInteger(-1);

		this.outputReads = outputReads;
		this.dubiousOrGoodDuplexCovInAllInputs = dubiousOrGoodDuplexCovInAllInputs;
		this.goodDuplexCovInAllInputs = goodDuplexCovInAllInputs;
		this.forceOutputAtLocations = forceOutputAtLocations;
		nSubAnalyzers = analysisChunk.subAnalyzers.size();

		this.contigIndex = contigIndex;
		this.contigName = contigName;

		this.excludeBEDs = excludeBEDs;
		this.repetitiveBEDs = repetitiveBEDs;
		this.PROCESSING_CHUNK = PROCESSING_CHUNK;
	}

	@Override
	protected final boolean onAdvance(final int phase, final int registeredParties) {
		//This is the place to make comparisons between analyzer results

		if (dn.get() >= 1_000) {
			dn.set(0);//Reset duplex number written in output BAM to 0, just so it stays
			//within a reasonable range (at the cost of numbers not being unique across
			//the file).
		}

		final boolean returnValue;
		boolean completedNormally = false;
		try {
			final int saveLastProcessedPosition = analysisChunk.lastProcessedPosition;

			for (int statsIndex = 0; statsIndex < analysisChunk.nParameterSets; statsIndex++) {
				Boolean insertion = null;
				for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
					subAnalyzer.stats = subAnalyzer.analyzer.stats.get(statsIndex);
					subAnalyzer.param = Objects.requireNonNull(subAnalyzer.stats.analysisParameters);
					if (insertion == null) {
						insertion = subAnalyzer.stats.forInsertions;
					} else {
						Assert.isTrue(subAnalyzer.stats.forInsertions == insertion);
					}
				}

				final int targetStopPosition =
					Math.min(analysisChunk.pauseAtPosition, analysisChunk.terminateAtPosition);

				Assert.isTrue(analysisChunk.pauseAtPosition >= saveLastProcessedPosition);

				analysisChunk.lastProcessedPosition = saveLastProcessedPosition;
				for (int i = 0; i < nSubAnalyzers; i++) {
					SubAnalyzer sub = analysisChunk.subAnalyzers.get(i);
					if (NONTRIVIAL_ASSERTIONS && nIterations > 1 && sub.candidateSequences.containsKey(
							new SequenceLocation(param.referenceGenomeShortName, contigIndex, contigName, analysisChunk.lastProcessedPosition))) {
						throw new AssertionFailedException();
					}
					if (saveLastProcessedPosition + 1 <= targetStopPosition && (statsIndex == 0 || !sub.stats.canSkipDuplexLoading)) {
						sub.load(saveLastProcessedPosition + 1, targetStopPosition);
					}
				}

				outer:
				for (int position = saveLastProcessedPosition + 1;
						position <= targetStopPosition && !groupSettings.terminateAnalysis;
						position ++) {

					final @NonNull SequenceLocation location =
						new SequenceLocation(param.referenceGenomeShortName, contigIndex, contigName, position,
							Objects.requireNonNull(insertion));

					for (GenomeFeatureTester tester: excludeBEDs) {
						if (tester.test(location)) {
							analysisChunk.subAnalyzers.
								forEach(sa -> {
									sa.candidateSequences.remove(location);
									sa.stats.nPosExcluded.add(location, 1);
								});
							analysisChunk.lastProcessedPosition = position;
							continue outer;
						}
					}

					onAdvance1(location);
					analysisChunk.lastProcessedPosition = position;
					if (outputReads && statsIndex == 0) {//Only output reads once; note
						//however that different parameter sets may lead to different duplex
						//grouping, which will not be apparent in BAM output
						final @NonNull SequenceLocation locationNoPH =
							new SequenceLocation(param.referenceGenomeShortName, contigIndex, contigName, position, false);
						prepareReadsToWrite(
							locationNoPH,
							analysisChunk,
							param.collapseFilteredReads,
							param.writeBothStrands,
							param.clipPairOverlap,
							dn);
					}

				}
			}

			if (ENABLE_TRACE && shouldLog(TRACE)) {
				logger.trace("Going from " + saveLastProcessedPosition + " to " + analysisChunk.lastProcessedPosition + " for chunk " + analysisChunk);
			}

			analysisChunk.subAnalyzersParallel.
				forEach(subAnalyzer -> {

				final int localLastProcessedPosition = analysisChunk.lastProcessedPosition;
				subAnalyzer.candidateSequences.retainEntries((key, value) ->
					key.position > localLastProcessedPosition);

				if (shouldLog(TRACE)) {
					logger.trace("SubAnalyzer " + analysisChunk + " completed " + (saveLastProcessedPosition + 1) +
						" to " + analysisChunk.lastProcessedPosition);
				}

				final int localPauseAt = analysisChunk.pauseAtPosition;
				final int maxInsertSize = param.maxInsertSize;
				subAnalyzer.extSAMCache.retainEntries((key, val) -> {
					final boolean keep = val.getAlignmentStart() + maxInsertSize > localPauseAt;
					if (!keep) {
						val.discarded = true;
					}
					return keep;
				});
				if (outputReads) {
					subAnalyzer.writeOutputReads();
				}
			});//End loop over subAnalyzers

			if (nIterations < 2) {
				nIterations++;
				final SequenceLocation lowerBound =
					new SequenceLocation(param.referenceGenomeShortName, contigIndex, contigName, analysisChunk.lastProcessedPosition);
				analysisChunk.subAnalyzers.
				forEach(subAnalyzer -> subAnalyzer.candidateSequences.retainEntries((key, val) -> {
					Assert.isTrue(key.contigIndex == contigIndex,
						"Problem with contig indices, " + key + ' ' + key.contigIndex +
							' ' + contigIndex);
					return key.compareTo(lowerBound) >= 0;
				}));
			}

			Assert.noException(() -> {
				//Check no sequences have been left behind
				analysisChunk.subAnalyzersParallel.
				forEach(subAnalyzer -> {
					final SequenceLocation lowerBound = new SequenceLocation(param.referenceGenomeShortName, contigIndex, contigName,
						analysisChunk.lastProcessedPosition);
					subAnalyzer.candidateSequences.forEach(
							e -> {
								Assert.isFalse(e.contigIndex != contigIndex);
								Assert.isFalse(e.compareTo(lowerBound) < 0/*,
										"pauseAt: %s; lastProcessedPosition: %s but found: %s for chunk %s",
											pauseAt.get(), lastProcessedPosition, e, analysisChunk*/);});
				});
			});

			final int maxLastProcessable = new IntMinMax<SubAnalyzer>().
				acceptMax(analysisChunk.subAnalyzers,
					sa -> ((SubAnalyzer) sa).lastProcessablePosition.get()).getMax();

			if (maxLastProcessable == previousLastProcessable.get()) {
				logger.debug("Phaser " + this + " will terminate");
				returnValue = true;
			} else {
				previousLastProcessable.set(maxLastProcessable);
				analysisChunk.pauseAtPosition = maxLastProcessable + PROCESSING_CHUNK;
				returnValue = false;
			}
			completedNormally = true;
		} finally {
			if (completedNormally) {
				for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
					subAnalyzer.stats = subAnalyzer.analyzer.stats.get(0);
					subAnalyzer.param = Objects.requireNonNull(subAnalyzer.stats.analysisParameters);
				}
			} else {
				forceTermination();
			}
		}

		return returnValue;
	}//End onAdvance

	private static void examineLocation(SubAnalyzer sa, @NonNull SequenceLocation location,
			MutableMap<SubAnalyzer, LocationExaminationResults> resultsMap) {
		LocationExaminationResults results = sa.examineLocation(location);
		if (NONTRIVIAL_ASSERTIONS) {
			for (CandidateSequence c: results.analyzedCandidateSequences) {
				//noinspection ObjectEquality
				Assert.isTrue(c.getOwningAnalyzer() == sa.analyzer);
			}
		}
		resultsMap.put(sa, results);
	}

	private void onAdvance1(
			final @NonNull SequenceLocation location
		) {

		final @NonNull MutableMap<SubAnalyzer, LocationExaminationResults> locationExamResultsMap0 =
			new UnifiedMap<>();
		//Fill the map so that at next step values can be modified concurrently
		//without causing structural modifications, obviating the need for synchronization
		analysisChunk.subAnalyzers.forEach(sa -> locationExamResultsMap0.put(sa, null));

		if (analysisChunk.subAnalyzers.getFirst().analyzedDuplexes.size() > 50_000) {
			analysisChunk.subAnalyzersParallel.forEach(sa -> examineLocation(sa, location, locationExamResultsMap0));
		} else {
			analysisChunk.subAnalyzers.forEach(sa -> examineLocation(sa, location, locationExamResultsMap0));
		}

		@SuppressWarnings("null")
		final @NonNull MutableMap<SubAnalyzer, @NonNull LocationExaminationResults> locationExamResultsMap =
			param.enableCostlyAssertions ?
				locationExamResultsMap0.asUnmodifiable()
			:
				locationExamResultsMap0;

		final ListIterable<@NonNull LocationExaminationResults> locationExamResults = Lists.immutable.
			withAll(locationExamResultsMap.values());

		final int dubiousOrGoodInAllInputsAtPos = new IntMinMax<LocationExaminationResults>().
			acceptMin(locationExamResults,
				ler -> ((LocationExaminationResults) ler).nGoodOrDubiousDuplexes).
			getMin();

		final int goodDuplexCovInAllInputsAtPos = new IntMinMax<LocationExaminationResults>().
			acceptMin(locationExamResults,
				ler -> ((LocationExaminationResults) ler).nGoodDuplexes).
			getMin();

		dubiousOrGoodDuplexCovInAllInputs.insert(dubiousOrGoodInAllInputsAtPos);
		goodDuplexCovInAllInputs.insert(goodDuplexCovInAllInputsAtPos);

		final Handle<Boolean> mutationToAnnotate = new Handle<>(false);

		locationExamResultsMap.forEachKeyValue((sa, ler) -> {
			SettableInteger sum = new SettableInteger(0);
			locationExamResultsMap.forEachKeyValue((k , v) -> {//noinspection ObjectEquality
				if (k != sa) sum.addAndGet(v.nGoodOrDubiousDuplexes);});
			ler.nGoodOrDubiousDuplexesSisterSamples = sum.get();
			ler.analyzedCandidateSequences.each(c -> c.setnDuplexesSisterSamples(sum.get()));
		});

		analysisChunk.subAnalyzers.forEach(
			sa -> {
				sa.incrementednPosDuplexQualityQ2OthersQ1Q2 = false;
				sa.c1 = false;
				sa.c2 = false;
				sa.c3 = false;
				sa.c4 = false;
				sa.processed = false;
				registerAndAnalyzeCoverage(
					Objects.requireNonNull(locationExamResultsMap.get(sa)),
					mutationToAnnotate,
					Objects.requireNonNull(sa.stats),
					location,
					locationExamResultsMap,
					sa.analyzer,
					groupSettings.mutationsToAnnotate,
					sa.analyzer.codingStrandTester,
					repetitiveBEDs,
					param.rawMismatchesOnlyAtWtPos);
			}
		);

		MutableList<CandidateSequence> mutantCandidates = Lists.mutable.empty();
		locationExamResults.
			each(a -> a.analyzedCandidateSequences.each(c -> {
				boolean isMutant = !c.getMutationType().isWildtype();
				if (isMutant) mutantCandidates.add(c);
			}));

		final @NonNull Quality maxCandMutQuality = Objects.requireNonNull(new ObjMinMax<>
			(Quality.ATROCIOUS, Quality.ATROCIOUS, Quality::compareTo).
			acceptMax(mutantCandidates, c -> {
				CandidateSequence c0 = (CandidateSequence) c;
				return c0.getMutationType().reportable() ? c0.getQuality().getValue() : Quality.ATROCIOUS;
			}).
			getMax());

		@SuppressWarnings("null")
		final float minTopAlleleFreq = new ObjMinMax<>(Float.MAX_VALUE, - Float.MAX_VALUE, Float::compareTo).
			acceptMin(locationExamResults, cl -> {
				Float f = ((LocationExaminationResults) cl).getTopAlleleFrequency().orElse(null);
				return (f == null || Float.isNaN(f)) ? Float.MAX_VALUE : f;
			}).getMin();

		final boolean lowTopAlleleFreq = minTopAlleleFreq != 0 &&
			minTopAlleleFreq < param.topAlleleFreqReport;

		locationExamResults.forEach(c -> c.analyzedCandidateSequences.forEach(candidate -> {
			if (candidate.getQuality().getNonNullValue().atLeast(GOOD)) {
				final @NonNull AnalysisStats stats = Objects.requireNonNull(
					candidate.getOwningSubAnalyzer().stats);
				candidate.computeNQ1PlusConcurringDuplexes(stats.concurringDuplexDistance, param);
			}
		}));

		final boolean forceReporting =
			forceOutputAtLocations.get(location) != null ||
			mutationToAnnotate.get() ||
			lowTopAlleleFreq;

		@SuppressWarnings("null")
		final boolean randomlySelected = forceOutputAtLocations.get(location) != null &&
			forceOutputAtLocations.get(location);

		if (forceReporting || maxCandMutQuality.atLeast(GOOD)) {
			if (NONTRIVIAL_ASSERTIONS) {
				for (GenomeFeatureTester t: excludeBEDs) {
					Assert.isFalse(t.test(location), "%s excluded by %s"/*, location, t*/);
				}
			}

			processAndReportCandidates(analysisChunk.subAnalyzers.get(0).stats.analysisParameters,
				locationExamResults, locationExamResultsMap,
				location, randomlySelected, lowTopAlleleFreq, true,
				repetitiveBEDs, analysisChunk, groupSettings.mutationsToAnnotate);
		}
	}

	private final static Set<PositionAssay> SISTER_SAMPLE_ASSAY_SET = Collections.singleton(
		PositionAssay.PRESENT_IN_SISTER_SAMPLE);

	private static void processAndReportCandidates(
			final @NonNull Parameters param,
			final @NonNull ListIterable<@NonNull LocationExaminationResults> locationExamResults,
			final @NonNull MutableMap<SubAnalyzer, @NonNull LocationExaminationResults> locationExamResultsMap,
			final @NonNull SequenceLocation location,
			final boolean randomlySelected,
			final boolean lowTopAlleleFreq,
			final boolean doOutput,
			final @NonNull List<@NonNull BedReader> repetitiveBEDs,
			final @NonNull AnalysisChunk analysisChunk,
			final @NonNull ConcurrentMap<Pair<SequenceLocation, String>,
			@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>> mutationsToAnnotate
		) {

		final MutableList<CandidateSequence> candidateSequences = locationExamResults.
			flatCollect(l -> l.analyzedCandidateSequences, new FastList<>()).
			sortThis(Comparator.comparing(CandidateSequence::getMutationType));

		//Refilter also allowing Q1 candidates to compare output of different
		//analyzers
		final MutableList<CandidateSequence> allQ1Q2Candidates = candidateSequences.
			select(c -> {
				Assert.isTrue(c.getLocation().distanceOnSameContig(location) == 0);
				return c.getQuality().getNonNullValue().greaterThan(POOR) && !c.isHidden();
				}, new FastList<>());

		final MutableSet<@NonNull DuplexDisagreement> allQ2DuplexDisagreements =
			Sets.mutable.empty(); //Distinct disagreements
		locationExamResults.
			each(ler -> ler.disagreements.forEachKey(d -> {
				if (d.quality.atLeast(GOOD)) allQ2DuplexDisagreements.add(d);
				return true;
			}));//Filtering is a NOP right now since all disags are Q2

		final CrossSampleLocationAnalysis csla = new CrossSampleLocationAnalysis(location);
		csla.randomlySelected = randomlySelected;
		csla.lowTopAlleleFreq = lowTopAlleleFreq;
		if (allQ1Q2Candidates.noneSatisfy(c -> c.getMutationType().isWildtype())) {
			csla.noWt = true;
		}

		MutableSet<CandidateSequence> distinctCandidates = candidateSequences.select(c -> !c.isHidden() && (c.getQuality().getNonNullValue().greaterThan(POOR) ||
			c.getQuality().qualitiesContain(DISAG_THAT_MISSED_Q2)), Sets.mutable.empty());
			//Collect into a set to get uniqueness

		PositionAssayToQualityMap extraPositionQualities = new PositionAssayToQualityMap();

		distinctCandidates.each(candidate -> {
			final int candidateCount = allQ1Q2Candidates.count(c -> c.equals(candidate));
			if (!candidate.getMutationType().isWildtype() && candidateCount > 1) {
				extraPositionQualities.put(PositionAssay.PRESENT_IN_SISTER_SAMPLE, Quality.DUBIOUS);
			}
		});

		final boolean multipleQ2Mutations =
			distinctCandidates.
			count(c -> !c.getMutationType().isWildtype() &&
				//Following doesn't do anything at this point since PRESENT_IN_SISTER_SAMPLE has not been set
				c.getQuality().getValueIgnoring(SISTER_SAMPLE_ASSAY_SET).atLeast(GOOD)) > 1;
		if (multipleQ2Mutations) {
			csla.multipleQ2MutantsAtSamePos = true;
			extraPositionQualities.put(PositionAssay.MULTIPLE_Q2_MUT_AT_POS, Quality.DUBIOUS);
		}

		if (!extraPositionQualities.isEmpty()) {
			candidateSequences.forEach(c ->
				extraPositionQualities.forEach((a, q) -> c.getQuality().addUnique(a, q)));
		}

		distinctCandidates.each(Procedures.throwing(candidate -> {//Iteration may not be parallel

			final int candidateCount = allQ1Q2Candidates.count(c -> c.equals(candidate));

			if (!candidate.getMutationType().isWildtype() &&
					allQ1Q2Candidates.count(c -> c.equals(candidate) &&
					(c.getQuality().getNonNullValue().atLeast(GOOD))) >= 2) {
				csla.twoOrMoreSamplesWithSameQ2MutationCandidate = true;
			} else if (candidateCount == 1 &&//Mutant candidate shows up only once (and therefore in only 1 analyzer)
					!candidate.getMutationType().isWildtype() &&
					candidate.getQuality().getNonNullValue().atLeast(GOOD) &&
					(!param.candidateQ2Criterion.equals("1Q2Duplex") || candidate.getnGoodDuplexes() >= param.minQ2DuplexesToCallMutation) &&
					candidate.getnGoodOrDubiousDuplexes() >= param.minQ1Q2DuplexesToCallMutation &&
					candidate.getQuality().downgradeUniqueIfFalse(TOO_HIGH_COVERAGE,
						!locationExamResultsMap.get(candidate.getOwningSubAnalyzer()).tooHighCoverage) &&
					candidate.getQuality().downgradeUniqueIfFalse(MIN_DUPLEXES_SISTER_SAMPLE,
						candidate.getnDuplexesSisterSamples() >= param.minNumberDuplexesSisterSamples) &&
					(!param.Q2DisagCapsMatchingMutationQuality ||
						//Disagreements from the same sample will have already-downgraded mutation quality,
						//if analysis parameter dictates it, but check again in case a matching disagreement is
						//present in another sample
						candidate.getQuality().downgradeIfFalse(AT_LEAST_ONE_DISAG,
								!allQ2DuplexDisagreements.anySatisfy(
									disag -> disag.snd.equals(candidate.getMutation()) ||
									disag.fst.equals(candidate.getMutation())))
					)
				) {

				final @NonNull AnalysisStats stats = Objects.requireNonNull(
					candidate.getOwningSubAnalyzer().stats);

				SubAnalyzer sa0 = candidate.getOwningSubAnalyzer();
				if (!sa0.incrementednPosDuplexQualityQ2OthersQ1Q2) {
					@NonNull LocationExaminationResults ler =
						locationExamResultsMap.get(candidate.getOwningSubAnalyzer());
					boolean c1 = ler.nGoodDuplexes >= stats.analysisParameters.minQ2DuplexesToCallMutation;
					boolean c2 = ler.nGoodOrDubiousDuplexes >= stats.analysisParameters.minQ1Q2DuplexesToCallMutation;
					boolean c3 = ler.nGoodOrDubiousDuplexesSisterSamples >= stats.analysisParameters.minNumberDuplexesSisterSamples;

					throw new AssertionFailedException(c1 + " " + c2 + " " + c3 + " --- " + sa0.processed + " " +
						sa0.c1 + " " + sa0.c2 + " " + sa0.c3 + " " + sa0.c4);
				}

				csla.nDuplexesUniqueQ2MutationCandidate.add(
					candidate.computeNQ1PlusConcurringDuplexes(stats.concurringMutationDuplexDistance, param));

				candidate.setGoodCandidateForUniqueMutation(true);

				stats.nPosCandidatesForUniqueMutation.accept(location, candidate.getnGoodDuplexes());
				stats.uniqueMutantQ2CandidateQ1Q2DCoverage.insert(candidate.getTotalGoodOrDubiousDuplexes());
				if (!repetitiveBEDs.isEmpty()) {
					boolean repetitive = false;
					for (GenomeFeatureTester t: repetitiveBEDs) {
						if (t.test(location)) {
							repetitive = true;
							break;
						}
					}
					if (repetitive) {
						stats.uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive.insert(candidate.getTotalGoodOrDubiousDuplexes());
					} else {
						stats.uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert(candidate.getTotalGoodOrDubiousDuplexes());
					}
				}

				analysisChunk.subAnalyzers.select(sa -> Objects.requireNonNull(locationExamResultsMap.get(sa)).
						analyzedCandidateSequences.contains(candidate)).
					forEach(sa -> {
						final LocationExaminationResults examResults =
							Objects.requireNonNull(locationExamResultsMap.get(sa));
						final AnalysisStats stats0 = sa.stats;
						stats0.nReadsAtPosWithSomeCandidateForQ2UniqueMutation.insert(
							(int) examResults.analyzedCandidateSequences.sumOfInt(
								c -> c.getNonMutableConcurringReads().size()));
						stats0.nQ1Q2AtPosWithSomeCandidateForQ2UniqueMutation.insert(
							(int) examResults.analyzedCandidateSequences.
							sumOfInt(CandidateSequence::getnGoodOrDubiousDuplexes));
					});
			}//End Q2 candidate

			boolean oneSampleNoWt = false;
			for (LocationExaminationResults results: locationExamResults) {
				if (results.analyzedCandidateSequences.
						noneSatisfy(c -> c.getMutationType().isWildtype() && c.getnGoodOrDubiousDuplexes() > 0)) {
					oneSampleNoWt = true;
					break;
				}
			}
			csla.oneSampleNoWt = oneSampleNoWt;

			final String baseOutput =
				csla.toString() + '\t' +
				location + '\t' +
				candidate.getKind() + '\t' +
				(!candidate.getMutationType().isWildtype() ?
							candidate.getChange() : "");

			//Now output information for the candidate for each analyzer
			//(unless no reads matched the candidate)
			for (@NonNull SubAnalyzer sa: analysisChunk.subAnalyzers) {

				final @NonNull LocationExaminationResults examResults =
					Objects.requireNonNull(locationExamResultsMap.get(sa));
				final List<CandidateSequence> l = examResults.analyzedCandidateSequences.
					select(c -> c.equals(candidate)).toList();

				final CandidateSequence matchingSACandidate;
				final int nCandidates = l.size();
				if (nCandidates > 1) {
					throw new AssertionFailedException();
				} else if (nCandidates == 0) {
					//Analyzer does not have matching candidate (i.e. it did not get
					//any reads matching the mutation)
					continue;
				} else {//1 candidate
					matchingSACandidate = l.get(0);
					matchingSACandidate.setnMatchingCandidatesOtherSamples(candidateCount);
				}

				if (!sa.stats.detections.computeIfAbsent(location, loc -> new LocationAnalysis(csla,
						Util.serializeAndDeserialize(examResults))).
					setCrossSampleLocationAnalysis(csla).candidates.add(
							Util.serializeAndDeserialize(matchingSACandidate))) {
						throw new AssertionFailedException();
				}

				final Pair<SequenceLocation, String> fullLocation =
					new Pair<>(location, sa.analyzer.name);

				@Nullable List<@NonNull Pair<@NonNull Mutation, @NonNull String>>
					toAnnotateList = mutationsToAnnotate.get(fullLocation);

				if (toAnnotateList != null) {
					for (Pair<Mutation, String> toAnnotate : toAnnotateList) {
						final Mutation mut;
						if ((mut = toAnnotate.fst).mutationType.
								equals(matchingSACandidate.getMutationType()) &&
								Arrays.equals(mut.mutationSequence, matchingSACandidate.getSequence())) {
							matchingSACandidate.setPreexistingDetection(toAnnotate.snd);
						}
					}
				}

				matchingSACandidate.recordMatchingGenomeIntervals(sa.analyzer.filtersForCandidateReporting);

				if (doOutput && (sa.stats.detectionOutputStream != null ||
							sa.stats.annotationOutputStream != null)) {
					outputCandidate(sa.analyzer, matchingSACandidate, location,
						sa.stats, csla.toString(), baseOutput,
						examResults);
				}
			}//End loop over subAnalyzers
		}));//End loop over mutation candidates
	}

	private static void outputCandidate(
			final @NonNull Mutinack analyzer,
			final @NonNull CandidateSequence candidate,
			final @NonNull SequenceLocation location,
			final @NonNull AnalysisStats stats,
			final @NonNull String baseOutput0,
			final @NonNull String baseOutput,
			final @NonNull LocationExaminationResults examResults
		) throws IOException {

		final String line = baseOutput + '\t' + analyzer.name + '\t' +
			candidate.toOutputString(stats.analysisParameters, examResults);

		if (stats.detectionOutputStream != null) {
			stats.detectionOutputStream.println(line);
		}

		try {
			final @Nullable OutputStreamWriter ambw = stats.mutationBEDWriter;
			if (ambw != null) {
				ambw.append(location.getContigName() + '\t' +
						(location.position + 1) + '\t' + (location.position + 1) + '\t' +
						candidate.getKind() + '\t' + baseOutput0 + '\t' +
						candidate.getnGoodDuplexes() +
					'\n');
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		if (candidate.getPreexistingDetection() != null) {
			stats.annotationOutputStream.append(candidate.getPreexistingDetection() + "\t" +
				line + '\n');
		}
	}

	private static void registerAndAnalyzeCoverage(
			final @NonNull LocationExaminationResults examResults,
			final @NonNull Handle<Boolean> mutationToAnnotate,
			final @NonNull AnalysisStats stats,
			final @NonNull SequenceLocation location,
			final @NonNull MutableMap<SubAnalyzer, @NonNull LocationExaminationResults> analyzerCandidateLists,
			final @NonNull Mutinack a,
			final @NonNull ConcurrentMap<Pair<SequenceLocation, String>,
				@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>> mutationsToAnnotate,
			final @Nullable GenomeFeatureTester codingStrandTester,
			final @NonNull List<BedReader> repetitiveBEDs,
			final boolean rawMismatchesOnlyAtWtPos
		) {

		if (mutationsToAnnotate.containsKey(new Pair<>(location, a.name))) {
			mutationToAnnotate.set(true);
		}

		final @Nullable OutputStreamWriter cbw = stats.coverageBEDWriter;
		if (cbw != null) {
			try {
				cbw.append(location.getContigName() + '\t' +
						(location.position + 1) + '\t' +
						(location.position + 1) + '\t' +
						examResults.nGoodOrDubiousDuplexes + '\n');
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		if (stats.positionByPositionCoverage != null) {
			int[] array = Objects.requireNonNull(stats.positionByPositionCoverage.get(
					location.getContigName()));
			if (array.length <= location.position) {
				throw new IllegalArgumentException("Position goes beyond end of contig " +
						location.getContigName() + ": " + location.position +
						" vs " + array.length);
			} else {
				array[location.position] += examResults.nGoodOrDubiousDuplexes;
			}
		}

		stats.alleleFrequencies.accept(location, examResults.getTopTwoAlleleFreqList());

		examResults.analyzedCandidateSequences.each(c -> {
			if (c.getQuality().getNonNullValue().atLeast(GOOD)) {
				if (c.getMutationType().isWildtype()) {
					stats.wtQ2CandidateQ1Q2Coverage.insert(examResults.nGoodOrDubiousDuplexes);
					if (!repetitiveBEDs.isEmpty()) {
						boolean repetitive = false;
						for (GenomeFeatureTester t: repetitiveBEDs) {
							if (t.test(location)) {
								repetitive = true;
								break;
							}
						}
						if (repetitive) {
							stats.wtQ2CandidateQ1Q2CoverageRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
						} else {
							stats.wtQ2CandidateQ1Q2CoverageNonRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
						}
					}
				} else {
					stats.mutantQ2CandidateQ1Q2Coverage.insert(examResults.nGoodOrDubiousDuplexes);
					if (!repetitiveBEDs.isEmpty()) {
						boolean repetitive = false;
						for (GenomeFeatureTester t: repetitiveBEDs) {
							if (t.test(location)) {
								repetitive = true;
								break;
							}
						}
						if (repetitive) {
							stats.mutantQ2CandidateQ1Q2DCoverageRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
						} else {
							stats.mutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert(examResults.nGoodOrDubiousDuplexes);
						}
					}
				}
			}
		});

		final boolean localTooHighCoverage = examResults.nGoodOrDubiousDuplexes > a.maxNDuplexes;
		examResults.tooHighCoverage = localTooHighCoverage;
		analyzerCandidateLists.forEachKey(sa -> {
			sa.c1 = localTooHighCoverage;
			sa.processed = true;
		});

		if (localTooHighCoverage) {
			stats.nPosIgnoredBecauseTooHighCoverage.increment(location);
		}

		if (!localTooHighCoverage) {
			//a.stats.nPosDuplexesCandidatesForDisagreementQ2.accept(location, examResults.nGoodDuplexesIgnoringDisag);
			registerOutputDisagreements(examResults, stats, location, rawMismatchesOnlyAtWtPos);
		} else {
			stats.nPosDuplexCandidatesForDisagreementQ2TooHighCoverage.accept(location, examResults.nGoodDuplexesIgnoringDisag);
			for (@NonNull DuplexDisagreement d: examResults.disagreements.keys()) {
				stats.topBottomDisagreementsQ2TooHighCoverage.accept(location, d);
			}
		}

		analyzerCandidateLists.forEachKey(sa -> {
			sa.c2 = !stats.analysisParameters.candidateQ2Criterion.equals("1Q2Duplex") ||//XXX Needs more work
				examResults.nGoodDuplexes >= stats.analysisParameters.minQ2DuplexesToCallMutation;
			sa.c3 = examResults.nGoodOrDubiousDuplexes >= stats.analysisParameters.minQ1Q2DuplexesToCallMutation;
			sa.c4 = examResults.nGoodOrDubiousDuplexesSisterSamples >= stats.analysisParameters.minNumberDuplexesSisterSamples;
		});

		if ((!localTooHighCoverage) &&
			(!stats.analysisParameters.candidateQ2Criterion.equals("1Q2Duplex") ||//XXX Needs more work
					examResults.nGoodDuplexes >= stats.analysisParameters.minQ2DuplexesToCallMutation) &&
				examResults.nGoodOrDubiousDuplexes >= stats.analysisParameters.minQ1Q2DuplexesToCallMutation &&
				examResults.nGoodOrDubiousDuplexesSisterSamples >= stats.analysisParameters.minNumberDuplexesSisterSamples
			) {

			examResults.analyzedCandidateSequences.each(c -> {
				if (c.isHidden()) return;
				c.getDuplexes().each(d -> {
					if (!d.localAndGlobalQuality.getNonNullValue().atLeast(GOOD)) return;
					int q = d.getMaxDistanceToLigSite();
					if (q != Integer.MIN_VALUE && q != Integer.MAX_VALUE)
						stats.crossAnalyzerQ2CandidateDistanceToLigationSite.insert(q);
				});
			});

			analyzerCandidateLists.forEachKey(sa -> sa.incrementednPosDuplexQualityQ2OthersQ1Q2 = true);
			stats.nPosDuplexQualityQ2OthersQ1Q2.accept(location, examResults.nGoodDuplexes);
			stats.nPosQualityQ2OthersQ1Q2.increment(location);
			if (codingStrandTester != null &&
					codingStrandTester.getNegativeStrand(location).isPresent()) {
				stats.nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate.accept(location, examResults.nGoodDuplexes);
			}
			//XXX The following includes all candidates at *all* positions considered in
			//processing chunk
			stats.nReadsAtPosQualityQ2OthersQ1Q2.insert(
					(int) examResults.analyzedCandidateSequences.sumOfInt(
						c -> c.getNonMutableConcurringReads().size()));
			stats.nQ1Q2AtPosQualityQ2OthersQ1Q2.insert(
					(int) examResults.analyzedCandidateSequences.sumOfInt(
						CandidateSequence::getnGoodOrDubiousDuplexes));

			if (stats.analysisParameters.variableBarcodeLength == 0) {
				stats.duplexCollisionProbabilityAtQ2.insert((int) (examResults.probAtLeastOneCollision * 1_000d));
			}
		}
	}

	private static void registerOutputDisagreements(
			final @NonNull LocationExaminationResults examResults,
			final @NonNull AnalysisStats stats,
			final @NonNull SequenceLocation location,
			final boolean rawMismatchesOnlyAtWtPos) {

		if (examResults.ignoreDisagreements) {
			return;
		}
		examResults.analyzedCandidateSequences.getFirstOptional().ifPresent(topCandidate -> {
			if (!rawMismatchesOnlyAtWtPos ||
					(topCandidate.getMutation().mutationType.isWildtype() && topCandidate.getFrequencyAtPosition() >= 0.7f)) {

				stats.intraStrandAndRawMismatchNPositions.increment();

				stats.rawMismatchesNReads.add(topCandidate.getTotalReadsAtPosition());
				stats.rawMismatchesQ2.accept(location, examResults.rawMismatchesQ2);
				for (@NonNull ComparablePair<String, String> var: examResults.rawDeletionsQ2) {
					stats.rawDeletionsQ2.accept(location, var);
					stats.rawDeletionLengthQ2.insert(var.snd.length());
				}
				for (@NonNull ComparablePair<String, String> var: examResults.rawInsertionsQ2) {
					stats.rawInsertionsQ2.accept(location, var);
					stats.rawInsertionLengthQ2.insert(var.snd.length());
				}

				stats.intraStrandNReads.add(examResults.intraStrandNReads);
				stats.intraStrandSubstitutions.accept(location, examResults.intraStrandSubstitutions);
				stats.intraStrandDeletions.accept(location, examResults.intraStrandDeletions);
				stats.intraStrandInsertions.accept(location, examResults.intraStrandInsertions);

			}
		});

		for (Entry<DuplexDisagreement, List<Duplex>> entry: examResults.disagreements) {

			DuplexDisagreement d = entry.getKey();

			if (!stats.detections.computeIfAbsent(location, loc -> new LocationAnalysis(null,
					Util.serializeAndDeserialize(examResults))).
				disagreements.add(d)) {
					throw new AssertionFailedException();
			}

			if (stats.analysisParameters.variableBarcodeLength == 0) {
				stats.duplexCollisionProbabilityLocalAvAtDisag.insert(
					(int) (examResults.probAtLeastOneCollision * 1_000d));

				stats.duplexCollisionProbabilityAtDisag.insert(
					(int) (d.probCollision * 1_000d));
			}

			byte[] fstSeq = d.getFst() == null ? null : d.getFst().getSequence();
			if (fstSeq == null) {
				fstSeq = QUESTION_MARK;
			}
			byte[] sndSeq = d.getSnd().getSequence();
			if (sndSeq == null) {
				sndSeq = QUESTION_MARK;
			}

			final Mutation mutant = d.getSnd();

			try {
				@SuppressWarnings("resource")
				@Nullable final OutputStreamWriter tpdw =
					d.hasAWtStrand ?
						stats.topBottomDisagreementWriter
					:
						stats.noWtDisagreementWriter;
				if (tpdw != null) {
					FloatPair alleleFreq = examResults.getTopTwoAlleleFreqFloat();
					NumberFormat formatter = mediumLengthFloatFormatter.get();
					tpdw.append(location.getContigName() + '\t' +
						(location.position + 1) + '\t' + (location.position + 1) + '\t' +
						(mutant.mutationType == SUBSTITUTION ?
								(new String(fstSeq) + "" + new String(sndSeq))
							:
								new String (sndSeq)) + '\t' +
						mutant.mutationType + '\t' +
						(d.hasAWtStrand ? "" : (d.getFst() != null ? d.getFst().mutationType : "-")) + '\t' +
						examResults.duplexInsertSize10thP + '\t' +
						examResults.duplexInsertSize90thP + '\t' +
						formatter.format(alleleFreq.f1) + '\t' +
						formatter.format(alleleFreq.f2) + '\t' +
						formatter.format(d.probCollision) + '\t' +
						formatter.format(examResults.probAtLeastOneCollision) + '\t' +
						entry.getValue().size() + '\t' +
						((stats.analysisParameters.verbosity < 2) ? "" :
						entry.getValue().stream().limit(20).map(dp ->
							Stream.concat(dp.topStrandRecords.stream(),
								dp.bottomStrandRecords.stream())/*.findFirst()*/.
								map(ExtendedSAMRecord::getFullName).collect(Collectors.joining(", ", "{ ", " }"))
						).collect(Collectors.joining(", ", "[ ",         " ]"))) +
						'\n');
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (!d.hasAWtStrand) {
				continue;
			}

			switch(mutant.mutationType) {
				case SUBSTITUTION:
					stats.topBottomSubstDisagreementsQ2.accept(location, d);
					mutant.isTemplateStrand().ifPresent(b -> {
						if (b)
							stats.templateStrandSubstQ2.accept(location, d);
						else
							stats.codingStrandSubstQ2.accept(location, d);});
					break;
				case DELETION:
					stats.topBottomDelDisagreementsQ2.accept(location, d);
					mutant.isTemplateStrand().ifPresent(b -> {
						if (b)
							stats.templateStrandDelQ2.accept(location, d);
						else
							stats.codingStrandDelQ2.accept(location, d);});
					break;
				case INTRON:
					stats.topBottomDelDisagreementsQ2.accept(location, d);
					break;
				case INSERTION:
					stats.topBottomInsDisagreementsQ2.accept(location, d);
					mutant.isTemplateStrand().ifPresent(b -> {
						if (b)
							stats.templateStrandInsQ2.accept(location, d);
						else stats.codingStrandInsQ2.accept(location, d);
					});
					break;
				case REARRANGEMENT:
					stats.topBottomRearDisagreementsQ2.accept(location, d);
					break;
				default:
					throw new AssertionFailedException();
			}
		}
	}

	private static void prepareReadsToWrite(
			final @NonNull SequenceLocation location,
			final @NonNull AnalysisChunk analysisChunk,
			final boolean collapseFilteredReads,
			final boolean writeBothStrands,
			final boolean clipPairOverlap,
			final @NonNull AtomicInteger dn
		) {
		analysisChunk.subAnalyzersParallel.forEach(subAnalyzer -> {
			//If outputting an alignment populated with fields identifying the duplexes,
			//fill in the fields here
			subAnalyzer.analyzedDuplexes.forEach(duplexRead -> {
			//for (DuplexRead duplexRead: subAnalyzer.analyzedDuplexes) {
				boolean useAnyStart = duplexRead.maxInsertSize == 0 ||
					duplexRead.maxInsertSize > 10_000;
				boolean write = location.equals(duplexRead.rightAlignmentEnd) ||
					(useAnyStart && location.equals(duplexRead.leftAlignmentEnd));
				if (!write) {
					return;
				}
				final int randomIndexForDuplexName = dn.incrementAndGet();

				final int nReads = duplexRead.allRecords.size();
				final Quality minDuplexQuality = duplexRead.minQuality;
				final Quality maxDuplexQuality = duplexRead.maxQuality;
				Handle<String> topOrBottom = new Handle<>();
				BiConsumer<ExtendedSAMRecord, SAMRecord> queueWrite = (ExtendedSAMRecord e, SAMRecord samRecord) -> {
					samRecord.setAttribute("DS", nReads);
					samRecord.setAttribute("DT", duplexRead.topStrandRecords.size());
					samRecord.setAttribute("DB", duplexRead.bottomStrandRecords.size());
					samRecord.setAttribute("DQ", minDuplexQuality.toInt());
					samRecord.setAttribute("DR", maxDuplexQuality.toInt());
					samRecord.setDuplicateReadFlag(e.isOpticalDuplicate());
					String info = topOrBottom.get() + " Q" +
						minDuplexQuality.toShortString() + "->" + maxDuplexQuality.toShortString() +
						" global Qs: " + duplexRead.globalQuality +
						" P" + duplexRead.getMinMedianPhred() +
						" D" + mediumLengthFloatFormatter.get().format(duplexRead.referenceDisagreementRate);
					samRecord.setAttribute("DI", info);
					samRecord.setAttribute("DN", randomIndexForDuplexName + "--" + System.identityHashCode(duplexRead));
					samRecord.setAttribute("VB", new String(e.variableBarcode));
					samRecord.setAttribute("VM", new String(e.getMateVariableBarcode()));
					samRecord.setAttribute("DE", duplexRead.leftAlignmentStart + "-" + duplexRead.leftAlignmentEnd + " --- " +
						duplexRead.rightAlignmentStart + '-' + duplexRead.rightAlignmentEnd);
					if (!duplexRead.issues.isEmpty()) {
						samRecord.setAttribute("IS", duplexRead.issues.toString());
					} else {
						samRecord.setAttribute("IS", null);
					}
					samRecord.setAttribute("AI", subAnalyzer.getAnalyzer().name);
					subAnalyzer.queueOutputRead(e, samRecord, useAnyStart);
				};
				Consumer<List<ExtendedSAMRecord>> writePair = (List<ExtendedSAMRecord> list) -> {
					final ExtendedSAMRecord e = list.get(0);
					final ExtendedSAMRecord mate = e.getMate();
					SAMRecord samRecord = e.record;
					if (mate != null) {
						SAMRecord mateSamRecord = mate.record;
						if (clipPairOverlap) {
							try {
								samRecord = (SAMRecord) samRecord.clone();
								mateSamRecord = (SAMRecord) mateSamRecord.clone();
							} catch (CloneNotSupportedException excp) {
								throw new RuntimeException(excp);
							}
							TrimOverlappingReads.clipForNoOverlap(samRecord, mateSamRecord, e, mate);
							TrimOverlappingReads.removeClippedBases(samRecord);
							TrimOverlappingReads.removeClippedBases(mateSamRecord);
						}
						if (!mateSamRecord.getReadUnmappedFlag()) {
							queueWrite.accept(mate, mateSamRecord);
						}
					}
					if (!samRecord.getReadUnmappedFlag()) {
						queueWrite.accept(e, samRecord);
					}
				};
				if (collapseFilteredReads) {
					final boolean topPresent = !duplexRead.topStrandRecords.isEmpty();
					if (topPresent) {
						topOrBottom.set("T");
						writePair.accept(duplexRead.topStrandRecords);
					}
					if ((writeBothStrands || !topPresent) && !duplexRead.bottomStrandRecords.isEmpty()) {
						topOrBottom.set("B");
						writePair.accept(duplexRead.bottomStrandRecords);
					}
					if (!duplexRead.topStrandRecords.isEmpty() && !duplexRead.bottomStrandRecords.isEmpty()) {
						Assert.isFalse(duplexRead.topStrandRecords.get(0).equals(duplexRead.bottomStrandRecords.get(0)));
					}
				} else {
					topOrBottom.set("T");
					duplexRead.topStrandRecords.forEach(e-> queueWrite.accept(e, e.record));
					topOrBottom.set("B");
					duplexRead.bottomStrandRecords.forEach(e-> queueWrite.accept(e, e.record));
				}
			});
		});
	}

	private static boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}

}
