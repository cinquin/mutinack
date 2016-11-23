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
import static uk.org.cinquin.mutinack.MutationType.DELETION;
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.Quality.GOOD;
import static uk.org.cinquin.mutinack.Quality.POOR;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.mediumLengthFloatFormatter;
import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMRecord;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.IntMinMax;
import uk.org.cinquin.mutinack.misc_util.ObjMinMax;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.statistics.Histogram;

public class SubAnalyzerPhaser extends Phaser {

	private static final Logger logger = LoggerFactory.getLogger("SubAnalyzerPhaser");
	private static final byte[] QUESTION_MARK = {'?'};

	private final AnalysisChunk analysisChunk;
	private final MutinackGroup groupSettings;
	private final SettableInteger lastProcessedPosition;
	private final SettableInteger pauseAt;
	private final SettableInteger previousLastProcessable;
	private final Map<SequenceLocation, Boolean> forceOutputAtLocations;
	private final Histogram dubiousOrGoodDuplexCovInAllInputs;
	private final Histogram goodDuplexCovInAllInputs;
	private final Parameters param;
	private final List<GenomeFeatureTester> excludeBEDs;
	private final List<BedReader> repetitiveBEDs;
	private final List<@NonNull Mutinack> analyzers;
	private final int contigIndex;
	private final @NonNull String contigName;
	private final int PROCESSING_CHUNK;

	private final ConcurrentHashMap<String, SAMRecord> readsToWrite;
	private final SAMFileWriter outputAlignment;
	private final OutputStreamWriter mutationAnnotationWriter;
	private final int nSubAnalyzers;

	private int nIterations = 0;
	private final AtomicInteger dn = new AtomicInteger(0);

	public SubAnalyzerPhaser(Parameters param,
							 AnalysisChunk analysisChunk,
							 List<@NonNull Mutinack> analyzers,
							 SAMFileWriter alignmentWriter,
							 OutputStreamWriter mutationAnnotationWriter,
							 Map<SequenceLocation, Boolean> forceOutputAtLocations,
							 Histogram dubiousOrGoodDuplexCovInAllInputs,
							 Histogram goodDuplexCovInAllInputs,
							 @NonNull String contigName,
							 int contigIndex,
							 List<GenomeFeatureTester> excludeBEDs,
							 List<BedReader> repetitiveBEDs,
							 int PROCESSING_CHUNK) {
		this.param = param;
		this.analysisChunk = analysisChunk;
		this.groupSettings = analysisChunk.groupSettings;
		this.analyzers = analyzers;
		lastProcessedPosition = analysisChunk.lastProcessedPosition;
		pauseAt = analysisChunk.pauseAtPosition;
		previousLastProcessable = new SettableInteger(-1);

		readsToWrite = alignmentWriter == null ?
				null
			:
				new ConcurrentHashMap<>(100_000);
		outputAlignment = alignmentWriter;
		this.mutationAnnotationWriter = mutationAnnotationWriter;
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
	protected final boolean onAdvance(int phase, int registeredParties) {
		//This is the place to make comparisons between analyzer results

		if (dn.get() >= 1_000) {
			dn.set(0);//Reset duplex number written in output BAM to 0, just so it stays
			//within a reasonable range (at the cost of numbers not being unique across
			//the file).
		}

		final boolean returnValue;
		boolean completedNormally = false;
		try {
			for (int i = 0; i < nSubAnalyzers; i++) {
				SubAnalyzer sub = analysisChunk.subAnalyzers.get(i);
				if (NONTRIVIAL_ASSERTIONS && nIterations > 1 && sub.candidateSequences.containsKey(
						new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get()))) {
					throw new AssertionFailedException();
				}
				sub.load();
			}

			final int saveLastProcessedPosition = lastProcessedPosition.get();
			outer:
			for (int position = saveLastProcessedPosition + 1;
					position <= Math.min(pauseAt.get(), analysisChunk.terminateAtPosition)  &&
					!groupSettings.terminateAnalysis; position ++) {
				for (int statsIndex = 0; statsIndex < analysisChunk.nParameterSets; statsIndex++) {
					Boolean insertion = null;
					for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
						subAnalyzer.stats = subAnalyzer.analyzer.stats.get(statsIndex);
						if (insertion == null) {
							insertion = subAnalyzer.stats.forInsertions;
						} else {
							Assert.isTrue(subAnalyzer.stats.forInsertions == insertion);
						}
					}

					final @NonNull SequenceLocation location =
						new SequenceLocation(contigIndex, contigName, position,
							Objects.requireNonNull(insertion));

					for (GenomeFeatureTester tester: excludeBEDs) {
						if (tester.test(location)) {
							analysisChunk.subAnalyzers/*.parallelStream()*/.
								forEach(sa -> {sa.candidateSequences.remove(location);
										sa.stats.nPosExcluded.add(location, 1);});
							lastProcessedPosition.set(position);
							continue outer;
						}
					}

					try {
						if (!param.rnaSeq) {
							onAdvance1(position, location);
						}
						lastProcessedPosition.set(position);
						if (readsToWrite != null) {
							writeReads(location);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}

			if (ENABLE_TRACE && shouldLog(TRACE)) {
				logger.trace("Going from " + saveLastProcessedPosition + " to " + lastProcessedPosition.get() + " for chunk " + analysisChunk);
			}

			analysisChunk.subAnalyzers/*.parallelStream()*/.forEach(subAnalyzer -> {

				for (int position = saveLastProcessedPosition + 1; position <= lastProcessedPosition.get();
						position ++) {
					subAnalyzer.candidateSequences.remove(new SequenceLocation(contigIndex, contigName, position));
					subAnalyzer.candidateSequences.remove(new SequenceLocation(contigIndex, contigName, position, true));
				}
				if (shouldLog(TRACE)) {
					logger.trace("SubAnalyzer " + analysisChunk + " completed " + (saveLastProcessedPosition + 1) + " to " + lastProcessedPosition.get());
				}

				final Iterator<ExtendedSAMRecord> iterator = subAnalyzer.extSAMCache.values().iterator();
				final int localPauseAt = pauseAt.get();
				final int maxInsertSize = param.maxInsertSize;
				if (param.rnaSeq) {
					while (iterator.hasNext()) {
						if (iterator.next().getAlignmentEnd() + maxInsertSize <= localPauseAt) {
							iterator.remove();
						}
					}
				} else {
					while (iterator.hasNext()) {
						if (iterator.next().getAlignmentStart() + maxInsertSize <= localPauseAt) {
							iterator.remove();
						}
					}
				}
				subAnalyzer.extSAMCache.compact();
			});//End loop over subAnalyzers

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
				analysisChunk.subAnalyzers/*.parallelStream()*/.forEach(subAnalyzer -> {
					Iterator<Entry<SequenceLocation, THashSet<CandidateSequence>>> it =
							subAnalyzer.candidateSequences.entrySet().iterator();
					final SequenceLocation lowerBound = new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get());
					while (it.hasNext()) {
						Entry<SequenceLocation, THashSet<CandidateSequence>> v = it.next();
						Assert.isTrue(v.getKey().contigIndex == contigIndex,
								"Problem with contig indices, " + v.getKey() + " " + v.getKey().contigIndex +
								" " + contigIndex);
						if (v.getKey().compareTo(lowerBound) < 0) {
							it.remove();
						}
					}
				});
			}

			Assert.noException(() -> {
				//Check no sequences have been left behind
				analysisChunk.subAnalyzers/*.parallelStream()*/.forEach(subAnalyzer -> {
					final SequenceLocation lowerBound = new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get());
					subAnalyzer.candidateSequences.keySet()/*.parallelStream()*/.forEach(
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
				pauseAt.set(maxLastProcessable + PROCESSING_CHUNK);
				returnValue = false;
			}
			completedNormally = true;
		} finally {
			if (completedNormally) {
				for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
					subAnalyzer.stats = subAnalyzer.analyzer.stats.get(0);
				}
			} else {
				forceTermination();
			}
		}

		return returnValue;
	}//End onAdvance

	private void onAdvance1(final int position,
			final @NonNull SequenceLocation location) throws IOException {

		final @NonNull Map<SubAnalyzer, LocationExaminationResults> locationExamResultsMap0 =
			new IdentityHashMap<>();
		//Fill the map so that at next step values can be modified concurrently
		//without causing structural modifications, obviating the need for synchronization
		analysisChunk.subAnalyzers.forEach(sa -> locationExamResultsMap0.put(sa, null));

		analysisChunk.subAnalyzers.parallelStream().forEach(sa -> {
			LocationExaminationResults results = sa.examineLocation(location);
			if (NONTRIVIAL_ASSERTIONS) {
				for (CandidateSequence c: results.analyzedCandidateSequences) {
					Assert.isTrue(c.getOwningAnalyzer() == sa.analyzer);
				}
			}
			locationExamResultsMap0.put(sa, results);
		});

		@SuppressWarnings("null")
		final @NonNull Map<SubAnalyzer, @NonNull LocationExaminationResults> locationExamResultsMap =
			Collections.unmodifiableMap(locationExamResultsMap0);

		final Collection<@NonNull LocationExaminationResults> locationExamResults =
			locationExamResultsMap.values();

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

		final Handle<Boolean> tooHighCoverage = new Handle<>(false);
		final Handle<Boolean> mutationToAnnotate = new Handle<>(false);

		analysisChunk.subAnalyzers.parallelStream().forEach(
			sa -> registerAndAnalyzeCoverage(
				Objects.requireNonNull(locationExamResultsMap.get(sa)),
				tooHighCoverage,
				mutationToAnnotate,
				Objects.requireNonNull(sa.stats),
				location,
				locationExamResultsMap,
				sa.analyzer)
		);

		List<CandidateSequence> mutantCandidates = locationExamResults.stream().
			map(c -> c.analyzedCandidateSequences).
			flatMap(Collection::stream).filter(c -> {
				boolean isMutant = !c.getMutationType().isWildtype();
				return isMutant;
			}).
			collect(Collectors.toList());

		final Quality maxCandMutQuality = Objects.requireNonNull(new ObjMinMax<>
			(Quality.ATROCIOUS, Quality.ATROCIOUS, Quality::compareTo).
			acceptMax(mutantCandidates, c -> ((CandidateSequence) c).getQuality().getMin()).
			getMax());

		@SuppressWarnings("null")//Incorrect Eclipse auto-unboxing warning
		final int minTopAlleleFreq = new ObjMinMax<>(99, 99, Integer::compareTo).
			acceptMin(locationExamResults, cl -> {
				List<@NonNull Integer> freq =
					((LocationExaminationResults) cl).alleleFrequencies;
				if (freq != null) {
					return ((LocationExaminationResults) cl).alleleFrequencies.get(1);
				} else {
					return 99;
				}
			}).getMin();

		final boolean lowTopAlleleFreq = minTopAlleleFreq < param.topAlleleFreqReport;

		final boolean forceReporting =
			forceOutputAtLocations.get(location) != null ||
			mutationToAnnotate.get() ||
			lowTopAlleleFreq;

		@SuppressWarnings("null")
		final boolean randomlySelected = forceOutputAtLocations.get(location) != null &&
			forceOutputAtLocations.get(location);

		if (forceReporting || maxCandMutQuality.atLeast(GOOD)) {
			processAndReportCandidates(locationExamResults, locationExamResultsMap,
				location, randomlySelected, lowTopAlleleFreq, tooHighCoverage.get(), true);
		}
	}

	private void processAndReportCandidates(
		final Collection<@NonNull LocationExaminationResults> locationExamResults,
		final @NonNull Map<SubAnalyzer, @NonNull LocationExaminationResults> locationExamResultsMap,
		@NonNull SequenceLocation location,
		final boolean randomlySelected,
		final boolean lowTopAlleleFreq,
		final boolean tooHighCoverage,
		final boolean doOutput) throws IOException {

		if (NONTRIVIAL_ASSERTIONS) {
			for (GenomeFeatureTester t: excludeBEDs) {
				Assert.isFalse(t.test(location), "%s excluded by %s"/*, location, t*/);
			}
		}

		//Refilter also allowing Q1 candidates to compare output of different
		//analyzers
		final List<CandidateSequence> allQ1Q2CandidatesWithHidden = locationExamResults.stream().
			map(l -> l.analyzedCandidateSequences).
			flatMap(Collection::stream).
			filter(c -> {
				Assert.isTrue(c.getLocation().distanceOnSameContig(location) == 0);
				return c.getQuality().getMin().greaterThan(POOR);}).
			collect(Collectors.toList());

		final List<CandidateSequence> allQ1Q2Candidates = allQ1Q2CandidatesWithHidden.stream().
			filter(c -> !c.isHidden()).
			sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
			collect(Collectors.toList());

		final List<CandidateSequence> allCandidatesIncludingDisag = locationExamResults.stream().
			map(l -> l.analyzedCandidateSequences).
			flatMap(Collection::stream).
			filter(c -> c.getQuality().getMin().greaterThan(POOR) ||
				c.getQuality().getQualities().containsKey(Assay.DISAGREEMENT)).
			filter(c -> !c.isHidden()).
			sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
			collect(Collectors.toList());

		final List<CandidateSequence> distinctQ1Q2Candidates = allQ1Q2Candidates.stream().distinct().
			//Sorting might not be necessary
			sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
			collect(Collectors.toList());

		final List<CandidateSequence> distinctQ1Q2CandidatesIncludingDisag = allCandidatesIncludingDisag.
			stream().distinct().
			//Sorting might not be necessary
			sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
			collect(Collectors.toList());

		final CrossSampleLocationAnalysis csla = new CrossSampleLocationAnalysis(location);

		csla.randomlySelected = randomlySelected;
		csla.lowTopAlleleFreq = lowTopAlleleFreq;
		if (!distinctQ1Q2Candidates.stream().anyMatch(c -> c.getMutationType().isWildtype())) {
			csla.noWt = true;
		}

		final Map<SubAnalyzer, SettableInteger> nGoodOrDubiousDuplexes = new IdentityHashMap<>(8);
		locationExamResults.stream().map(l -> l.analyzedCandidateSequences).
			flatMap(Collection::stream).filter(c -> c.getQuality().getMin().greaterThan(POOR)).
			forEach(c->
				nGoodOrDubiousDuplexes.computeIfAbsent(c.getOwningSubAnalyzer(), c0 -> new SettableInteger(0)).
					addAndGet(c.getnGoodOrDubiousDuplexes())
			);

		final Map<SubAnalyzer, Integer> nGoodOrDubiousDuplexesOthers = new IdentityHashMap<>(8);
		nGoodOrDubiousDuplexes.forEach((sa, count) -> nGoodOrDubiousDuplexesOthers.put(sa,
			nGoodOrDubiousDuplexes.keySet().stream().
			filter(sa2 -> sa2 != sa).mapToInt(sa2 ->
				Objects.requireNonNull(nGoodOrDubiousDuplexes.get(sa2)).get()).sum()));

		locationExamResults.stream().map(l -> l.analyzedCandidateSequences).
			flatMap(Collection::stream).forEach(c -> {
				Integer i = nGoodOrDubiousDuplexesOthers.get(c.getOwningSubAnalyzer());
				c.nDuplexesSisterArm = (i == null) ? 0 : i;
			});

		for (final CandidateSequence candidate: distinctQ1Q2CandidatesIncludingDisag) {

			final int candidateCount = (int) allQ1Q2Candidates.stream().
				filter(c -> c.equals(candidate)).count();

			if (!candidate.getMutationType().isWildtype() &&
				allQ1Q2Candidates.stream().filter(c -> c.equals(candidate) &&
						(c.getQuality().getMin().atLeast(GOOD) || (c.getSupplQuality() != null && nonNullify(c.getSupplQuality()).atLeast(GOOD)))).count() >= 2) {
				csla.twoOrMoreSamplesWithSameQ2MutationCandidate = true;
			} else if (candidateCount == 1 &&//Mutant candidate shows up only once (and therefore in only 1 analyzer)
					!candidate.getMutationType().isWildtype() &&
					candidate.getQuality().getMin().atLeast(GOOD) &&
					candidate.getnGoodDuplexes() >= param.minQ2DuplexesToCallMutation &&
					candidate.getnGoodOrDubiousDuplexes() >= param.minQ1Q2DuplexesToCallMutation &&
					!tooHighCoverage &&
					candidate.nDuplexesSisterArm >= param.minNumberDuplexesSisterArm
					) {

				csla.nDuplexesUniqueQ2MutationCandidate.add(candidate.getnGoodOrDubiousDuplexes());

				final @NonNull AnalysisStats stats = Objects.requireNonNull(
					candidate.getOwningSubAnalyzer().stats);
				stats.nPosCandidatesForUniqueMutation.accept(location, candidate.getnGoodDuplexes());
				stats.uniqueMutantQ2CandidateQ1Q2DCoverage.insert((int) candidate.getTotalGoodOrDubiousDuplexes());
				if (!repetitiveBEDs.isEmpty()) {
					boolean repetitive = false;
					for (GenomeFeatureTester t: repetitiveBEDs) {
						if (t.test(location)) {
							repetitive = true;
							break;
						}
					}
					if (repetitive) {
						stats.uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive.insert((int) candidate.getTotalGoodOrDubiousDuplexes());
					} else {
						stats.uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert((int) candidate.getTotalGoodOrDubiousDuplexes());
					}
				}

				analysisChunk.subAnalyzers.stream().
					filter(sa -> Objects.requireNonNull(locationExamResultsMap.get(sa)).
						analyzedCandidateSequences.contains(candidate)).
					forEach(sa -> {
						final LocationExaminationResults examResults =
							Objects.requireNonNull(locationExamResultsMap.get(sa));
						final AnalysisStats stats0 = sa.stats;
						stats0.nReadsAtPosWithSomeCandidateForQ2UniqueMutation.insert(
							examResults.analyzedCandidateSequences.
							stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
						stats0.nQ1Q2AtPosWithSomeCandidateForQ2UniqueMutation.insert(
							examResults.analyzedCandidateSequences.
							stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());
					});
			}//End Q2 candidate

			boolean oneSampleNoWt = false;
			for (LocationExaminationResults results: locationExamResults) {
				if (!results.analyzedCandidateSequences.stream().
						anyMatch(c -> c.getMutationType().isWildtype() && c.getnGoodOrDubiousDuplexes() > 0)) {
					oneSampleNoWt = true;
					break;
				}
			}
			csla.oneSampleNoWt = oneSampleNoWt;

			final String baseOutput =
				csla.toString() + "\t" +
				location + "\t" +
				candidate.getKind() + "\t" +
				(!candidate.getMutationType().isWildtype() ?
							candidate.getChange() : "");

			//Now output information for the candidate for each analyzer
			//(unless no reads matched the candidate)
			for (@NonNull SubAnalyzer sa: analysisChunk.subAnalyzers) {

				final @NonNull LocationExaminationResults examResults =
					Objects.requireNonNull(locationExamResultsMap.get(sa));
				final List<CandidateSequence> l = examResults.analyzedCandidateSequences.
					stream().filter(c -> c.equals(candidate)).collect(Collectors.toList());

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

				if (doOutput) {
					outputCandidate(sa.analyzer, matchingSACandidate, location,
						sa.stats, csla.toString(), baseOutput,
						examResults);
				}
			}
		}//End loop over mutation candidates

	}

	private void outputCandidate(
			final @NonNull Mutinack analyzer,
			final @NonNull CandidateSequence candidate,
			final @NonNull SequenceLocation location,
			final AnalysisStats stats,
			final @NonNull String baseOutput0,
			final @NonNull String baseOutput,
			final @NonNull LocationExaminationResults examResults
		) throws IOException {

		String line = baseOutput + "\t" + analyzer.name + "\t" +
			candidate.toOutputString(param, examResults);

		try {
			final @Nullable OutputStreamWriter ambw = stats.mutationBEDWriter;
			if (ambw != null) {
				ambw.append(location.getContigName() + "\t" +
						(location.position + 1) + "\t" + (location.position + 1) + "\t" +
						candidate.getKind() + "\t" + baseOutput0 + "\t" +
						candidate.getnGoodDuplexes() +
						"\n");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		final Pair<SequenceLocation, String> fullLocation =
			new Pair<>(location, analyzer.name);

		@Nullable List<@NonNull Pair<@NonNull Mutation, @NonNull String>>
			toAnnotateList = groupSettings.mutationsToAnnotate.get(fullLocation);

		if (toAnnotateList != null) {
			Iterator<@NonNull Pair<@NonNull Mutation, @NonNull String>> it = toAnnotateList.iterator();
			while (it.hasNext()) {
				final Pair<@NonNull Mutation, @NonNull String> toAnnotate = it.next();
				final Mutation mut;
				if ((mut = toAnnotate.fst).mutationType.
						equals(candidate.getMutationType()) &&
						Arrays.equals(mut.mutationSequence, candidate.getSequence())) {
					mutationAnnotationWriter.append(toAnnotate.snd + "\t" + line + "\n");
					it.remove();
				}
			}
			if (toAnnotateList.isEmpty()) {
				groupSettings.mutationsToAnnotate.remove(fullLocation);
			}
		}
		analysisChunk.out.println(line);
	}

	private void registerAndAnalyzeCoverage(
			final @NonNull LocationExaminationResults examResults,
			final @NonNull Handle<Boolean> tooHighCoverage,
			final Handle<Boolean> mutationToAnnotate,
			final @NonNull AnalysisStats stats,
			final @NonNull SequenceLocation location,
			final @NonNull Map<SubAnalyzer, @NonNull LocationExaminationResults> analyzerCandidateLists,
			final Mutinack a) {

		if (groupSettings.mutationsToAnnotate.containsKey(new Pair<>(location, a.name))) {
			mutationToAnnotate.set(true);
		}

		final @Nullable OutputStreamWriter cbw = stats.coverageBEDWriter;
		if (cbw != null) {
			try {
				cbw.append(location.getContigName() + "\t" +
						(location.position + 1) + "\t" +
						(location.position + 1) + "\t" +
						examResults.nGoodOrDubiousDuplexes + "\n");
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

		if (examResults.alleleFrequencies != null) {
			stats.alleleFrequencies.accept(location, examResults.alleleFrequencies);
		}

		for (CandidateSequence c: examResults.analyzedCandidateSequences) {
			if (c.getQuality().getMin().atLeast(GOOD)) {
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
		}

		final boolean localTooHighCoverage = examResults.nGoodOrDubiousDuplexes > a.maxNDuplexes;

		if (localTooHighCoverage) {
			stats.nPosIgnoredBecauseTooHighCoverage.increment(location);
			tooHighCoverage.set(true);
		}

		if (!localTooHighCoverage) {
			//a.stats.nPosDuplexesCandidatesForDisagreementQ2.accept(location, examResults.nGoodDuplexesIgnoringDisag);
			registerDisagreements(examResults, stats, location);
		} else {
			stats.nPosDuplexCandidatesForDisagreementQ2TooHighCoverage.accept(location, examResults.nGoodDuplexesIgnoringDisag);
			for (@NonNull DuplexDisagreement d: examResults.disagreements.keys()) {
				stats.topBottomDisagreementsQ2TooHighCoverage.accept(location, d);
			}
		}

		if ((!localTooHighCoverage) &&
				examResults.nGoodDuplexes >= param.minQ2DuplexesToCallMutation &&
				examResults.nGoodOrDubiousDuplexes >= param.minQ1Q2DuplexesToCallMutation &&
				analyzerCandidateLists.entrySet().stream().filter(e -> e.getKey().analyzer != a).
				mapToInt(e -> e.getValue().nGoodOrDubiousDuplexes).sum()
				>= param.minNumberDuplexesSisterArm
			) {

			examResults.analyzedCandidateSequences.stream().filter(c -> !c.isHidden()).
				flatMap(c -> c.getDuplexes().stream()).
				filter(dr -> dr.localAndGlobalQuality.getMin().atLeast(GOOD)).
				map(DuplexRead::getMaxDistanceToLigSite).
				forEach(i -> {if (i != Integer.MIN_VALUE && i != Integer.MAX_VALUE)
					stats.crossAnalyzerQ2CandidateDistanceToLigationSite.insert(i);});

			stats.nPosDuplexQualityQ2OthersQ1Q2.accept(location, examResults.nGoodDuplexes);
			stats.nPosQualityQ2OthersQ1Q2.increment(location);
			@Nullable final GenomeFeatureTester codingStrandTester =
				analyzers.get(0).codingStrandTester;
			if (codingStrandTester != null &&
					codingStrandTester.getNegativeStrand(location).isPresent()) {
				stats.nPosDuplexQualityQ2OthersQ1Q2CodingOrTemplate.accept(location, examResults.nGoodDuplexes);
			}
			//XXX The following includes all candidates at *all* positions considered in
			//processing chunk
			stats.nReadsAtPosQualityQ2OthersQ1Q2.insert(
					examResults.analyzedCandidateSequences.
					stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
			stats.nQ1Q2AtPosQualityQ2OthersQ1Q2.insert(
					examResults.analyzedCandidateSequences.
					stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());

			if (param.variableBarcodeLength == 0) {
				stats.duplexCollisionProbabilityAtQ2.insert((int) (examResults.probAtLeastOneCollision * 1_000d));
			}
		}
	}

	private void registerDisagreements(final LocationExaminationResults examResults,
			final AnalysisStats stats,
			final @NonNull SequenceLocation location) {
		for (@NonNull ComparablePair<String, String> var: examResults.rawMismatchesQ2) {
			stats.rawMismatchesQ2.accept(location, var);
		}

		for (@NonNull ComparablePair<String, String> var: examResults.rawDeletionsQ2) {
			stats.rawDeletionsQ2.accept(location, var);
			stats.rawDeletionLengthQ2.insert(var.snd.length());
		}

		for (@NonNull ComparablePair<String, String> var: examResults.rawInsertionsQ2) {
			stats.rawInsertionsQ2.accept(location, var);
			stats.rawInsertionLengthQ2.insert(var.snd.length());
		}

		for (Entry<DuplexDisagreement, List<DuplexRead>> entry: examResults.disagreements) {

			DuplexDisagreement d = entry.getKey();

			if (param.variableBarcodeLength == 0) {
				stats.duplexCollisionProbabilityLocalAvAtDisag.insert(
					(int) (examResults.probAtLeastOneCollision * 1_000d));

				stats.duplexCollisionProbabilityAtDisag.insert(
					(int) (d.probCollision * 1_000d));
			}

			byte[] fstSeq = d.getFst().getSequence();
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
				@Nullable
				final OutputStreamWriter tpdw = d.hasAWtStrand ? stats.topBottomDisagreementWriter
					: stats.noWtDisagreementWriter;
				if (tpdw != null) {
					tpdw.append(location.getContigName() + "\t" +
							(location.position + 1) + "\t" + (location.position + 1) + "\t" +
							(mutant.mutationType == SUBSTITUTION ?
									(new String(fstSeq) + "" + new String(sndSeq))
								:
									new String (sndSeq)) + "\t" +
							mutant.mutationType + "\t" +
							(d.hasAWtStrand ? "" : (d.getFst().mutationType)) + "\t" +
							examResults.duplexInsertSize10thP + "\t" +
							examResults.duplexInsertSize90thP + "\t" +
							examResults.alleleFrequencies.get(0) + "\t" +
							examResults.alleleFrequencies.get(1) + "\t" +
							mediumLengthFloatFormatter.get().format(d.probCollision) + "\t" +
							mediumLengthFloatFormatter.get().format(examResults.probAtLeastOneCollision) + "\t" +
							entry.getValue().size() + "\t" +
							((param.verbosity < 2) ? "" :
							entry.getValue().stream().limit(20).map(dp ->
								Stream.concat(dp.topStrandRecords.stream(),
									dp.bottomStrandRecords.stream())/*.findFirst()*/.
									map(ExtendedSAMRecord::getFullName).collect(Collectors.joining(", ", "{ ", " }"))
							).collect(Collectors.joining(", ", "[ ",         " ]"))) +
							"\n");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			if (!d.hasAWtStrand) {
				continue;
			}

			if (mutant.mutationType == SUBSTITUTION) {
				stats.topBottomSubstDisagreementsQ2.accept(location, d);
				mutant.isTemplateStrand().ifPresent(b -> {
					if (b)
						stats.templateStrandSubstQ2.accept(location, d);
					else
						stats.codingStrandSubstQ2.accept(location, d);});
			} else if (mutant.mutationType == DELETION) {
				stats.topBottomDelDisagreementsQ2.accept(location, d);
				mutant.isTemplateStrand().ifPresent(b -> {
					if (b)
						stats.templateStrandDelQ2.accept(location, d);
					else
						stats.codingStrandDelQ2.accept(location, d);});
			} else if (mutant.mutationType == INSERTION) {
				stats.topBottomInsDisagreementsQ2.accept(location, d);
				mutant.isTemplateStrand().ifPresent(b -> {
					if (b)
						stats.templateStrandInsQ2.accept(location, d);
					else stats.codingStrandInsQ2.accept(location, d);
				});
			}
		}
	}

	private void writeReads(final SequenceLocation location) {
		analysisChunk.subAnalyzers/*.parallelStream()*/.forEach(subAnalyzer -> {
			//If outputting an alignment populated with fields identifying the duplexes,
			//fill in the fields here
			for (DuplexRead duplexRead: subAnalyzer.analyzedDuplexes) {
				boolean useAnyStart = duplexRead.maxInsertSize == 0 ||
					duplexRead.maxInsertSize > 10_000;
				boolean write = location.equals(duplexRead.rightAlignmentEnd) ||
					(useAnyStart && location.equals(duplexRead.leftAlignmentEnd));
				if (!write) {
					continue;
				}
				final int randomIndexForDuplexName = dn.incrementAndGet();

				final int nReads = duplexRead.bottomStrandRecords.size() + duplexRead.topStrandRecords.size();
				final Quality minDuplexQuality = duplexRead.minQuality;
				final Quality maxDuplexQuality = duplexRead.maxQuality;
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
						samRecord.setAttribute("DS", nReads);
						samRecord.setAttribute("DT", duplexRead.topStrandRecords.size());
						samRecord.setAttribute("DB", duplexRead.bottomStrandRecords.size());
						samRecord.setAttribute("DQ", minDuplexQuality.toInt());
						samRecord.setAttribute("DR", maxDuplexQuality.toInt());
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
								duplexRead.rightAlignmentStart + "-" + duplexRead.rightAlignmentEnd);
						if (!duplexRead.issues.isEmpty()) {
							samRecord.setAttribute("IS", duplexRead.issues.toString());
						} else {
							samRecord.setAttribute("IS", null);
						}
						samRecord.setAttribute("AI", subAnalyzer.getAnalyzer().name);
						readsToWrite.put(e.getFullName(), samRecord);
					}
				};
				if (param.collapseFilteredReads) {
					if (!duplexRead.topStrandRecords.isEmpty()) {
						topOrBottom.set("T");
						queueWrite.accept(duplexRead.topStrandRecords.get(0));
					}
					if (!duplexRead.bottomStrandRecords.isEmpty()) {
						topOrBottom.set("B");
						queueWrite.accept(duplexRead.bottomStrandRecords.get(0));
					}
				} else {
					topOrBottom.set("T");
					duplexRead.topStrandRecords.forEach(queueWrite);
					topOrBottom.set("B");
					duplexRead.bottomStrandRecords.forEach(queueWrite);
				}
			}
		});
	}

	private static final boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}

}
