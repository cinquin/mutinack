package uk.org.cinquin.mutinack;

import static contrib.uk.org.lidalia.slf4jext.Level.TRACE;
import static uk.org.cinquin.mutinack.MutationType.DELETION;
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.Quality.GOOD;
import static uk.org.cinquin.mutinack.Quality.POOR;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.mediumLengthFloatFormatter;
import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.NumberFormat;
import java.util.Collection;
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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMRecord;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.parfor.ParFor;

public class SubAnalyzerPhaser extends Phaser {
	
	private static final Logger logger = LoggerFactory.getLogger("SubAnalyzerPhaser");
	private static final boolean[] falseTrue = new boolean[] {false, true};

	private final AnalysisChunk analysisChunk;
	private final MutinackGroup groupSettings;
	private final SettableInteger lastProcessedPosition;
	private final SettableInteger pauseAt;
	private final SettableInteger previousLastProcessable;
	private final Map<SequenceLocation, Boolean> forceOutputAtLocations;
	private final Histogram dubiousOrGoodDuplexCovInAllInputs;
	private final Histogram goodDuplexCovInAllInputs;
	private final Parameters argValues;
	private final List<GenomeFeatureTester> excludeBEDs;
	private final List<BedReader> repetitiveBEDs;
	private final List<Mutinack> analyzers;
	private final List<@NonNull LocationExaminationResults> analyzerCandidateLists;
	private final int contigIndex;
	private final String contigName;
	private final int PROCESSING_CHUNK;
	
	private final ConcurrentHashMap<String, SAMRecord> readsToWrite;
	private final SAMFileWriter outputAlignment;
	private final int nSubAnalyzers;

	private int nIterations = 0;	
	private final AtomicInteger dn = new AtomicInteger(0);
	
	public SubAnalyzerPhaser(Parameters argValues, AnalysisChunk analysisChunk, 
			List<Mutinack> analyzers, List<@NonNull LocationExaminationResults> analyzerCandidateLists,
			SAMFileWriter alignmentWriter,
			Map<SequenceLocation, Boolean> forceOutputAtLocations,
			Histogram dubiousOrGoodDuplexCovInAllInputs, Histogram goodDuplexCovInAllInputs,
			String contigName,
			int contigIndex,
			List<GenomeFeatureTester> excludeBEDs,
			List<BedReader> repetitiveBEDs,
			int PROCESSING_CHUNK) {
		this.argValues = argValues;
		this.analysisChunk = analysisChunk;
		this.groupSettings = analysisChunk.groupSettings;
		this.analyzers = analyzers;
		this.analyzerCandidateLists = analyzerCandidateLists;
		lastProcessedPosition = analysisChunk.lastProcessedPosition;
		pauseAt = analysisChunk.pauseAtPosition;
		previousLastProcessable = new SettableInteger(-1);
		
		readsToWrite = alignmentWriter == null ?
			null
			: new ConcurrentHashMap<>(100_000);
		outputAlignment = alignmentWriter;
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
			dn.set(0);//Reset duplex number written in output BAM to 0
		}
		
		try {
			final ParFor pf0 = new ParFor("Load " + contigName + " " + lastProcessedPosition.get() +
					"-" + pauseAt.get(), 0, nSubAnalyzers - 1 , null, true);
			for (int worker = 0; worker < nSubAnalyzers; worker++) {
				pf0.addLoopWorker((loopIndex, j) -> {
					SubAnalyzer sub = analysisChunk.subAnalyzers.get(loopIndex);
					if (NONTRIVIAL_ASSERTIONS && nIterations > 1 && sub.candidateSequences.containsKey(
							new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get()))) {
						throw new AssertionFailedException();
					}
					sub.load();
					return null;
				}
				);
			}
			pf0.run(true);

			final int saveLastProcessedPosition = lastProcessedPosition.get();
			for (int position = saveLastProcessedPosition + 1; position <= pauseAt.get() &&
					!groupSettings.terminateAnalysis; position ++) {
			outer:
			for (boolean plusHalf: falseTrue) {
				
				final int statsIndex = plusHalf ? 1 : 0;
				for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
					subAnalyzer.stats = subAnalyzer.analyzer.stats.get(statsIndex);
				}

				if (position > analysisChunk.terminateAtPosition) {
					break outer;
				}
				
				final @NonNull SequenceLocation location = new SequenceLocation(contigIndex, 
						contigName, position, plusHalf);

				for (GenomeFeatureTester tester: excludeBEDs) {
					if (tester.test(location)) {
						analysisChunk.subAnalyzers.parallelStream().
							forEach(sa -> {sa.candidateSequences.remove(location);
									sa.stats.nPosExcluded.add(location, 1);});
						lastProcessedPosition.set(position);
						continue outer;
					}
				}

				if (!argValues.rnaSeq) {
					ParFor pf = new ParFor("Examination ", 0, nSubAnalyzers -1 , null, true);
					for (int worker = 0; worker < analyzers.size(); worker++) {
						pf.addLoopWorker((loopIndex,j) -> {
							analyzerCandidateLists.set(loopIndex, analysisChunk.subAnalyzers.get(loopIndex)
									.examineLocation(location));
							return null;});
					}
					pf.run(true);

					final int dubiousOrGoodInAllInputsAtPos = analyzerCandidateLists.stream().
							mapToInt(ler -> ler.nGoodOrDubiousDuplexes).min().getAsInt();

					final int goodDuplexCovInAllInputsAtPos = analyzerCandidateLists.stream()
							.mapToInt(ler -> ler.nGoodDuplexes).min().getAsInt();

					dubiousOrGoodDuplexCovInAllInputs.insert(dubiousOrGoodInAllInputsAtPos);
					goodDuplexCovInAllInputs.insert(goodDuplexCovInAllInputsAtPos);

					final Handle<Boolean> tooHighCoverage = new Handle<>(false);

					analyzers.parallelStream().forEach(a -> {
						final @NonNull AnalysisStats stats = a.stats.get(statsIndex);
						final @NonNull LocationExaminationResults examResults = analyzerCandidateLists.get(a.idx);
						final @Nullable OutputStreamWriter cbw = stats.coverageBEDWriter;
						if (cbw != null) {
							try {
								cbw.append(	location.getContigName() + "\t" + 
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

						for (CandidateSequence c: examResults.analyzedCandidateSequences) {
							if (c.getQuality().getMin().compareTo(GOOD) >= 0) {
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

							for (ComparablePair<Mutation, Mutation> d: examResults.disagreements) {
								Mutation mutant = d.getSnd();
								if (mutant.mutationType == SUBSTITUTION) {
									stats.topBottomSubstDisagreementsQ2.accept(location, d);
									mutant.getTemplateStrand().ifPresent(b -> {
										if (b)
											stats.templateStrandSubstQ2.accept(location, d);
										else 
											stats.codingStrandSubstQ2.accept(location, d);});
								} else if (mutant.mutationType == DELETION) {
									stats.topBottomDelDisagreementsQ2.accept(location, d);
									mutant.getTemplateStrand().ifPresent(b -> {
										if (b) 
											stats.templateStrandDelQ2.accept(location, d);
										else 
											stats.codingStrandDelQ2.accept(location, d);});
								} else if (mutant.mutationType == INSERTION) {
									stats.topBottomInsDisagreementsQ2.accept(location, d);
									mutant.getTemplateStrand().ifPresent(b -> {
										if (b) 
											stats.templateStrandInsQ2.accept(location, d);
										else stats.codingStrandInsQ2.accept(location, d);
									});
								}

								byte[] fstSeq = d.getFst().getSequence();
								if (fstSeq == null) {
									fstSeq = new byte[] {'?'};
								}
								byte[] sndSeq = d.getSnd().getSequence();
								if (sndSeq == null) {
									sndSeq = new byte[] {'?'};
								}

								try {
									@Nullable
									OutputStreamWriter tpdw = stats.topBottomDisagreementWriter;
									if (tpdw != null) {
										tpdw.append(location.getContigName() + "\t" +
												(location.position + 1) + "\t" + (location.position + 1) + "\t" +
												(mutant.mutationType == SUBSTITUTION ?
														(new String(fstSeq) + "" + new String(sndSeq)) 
														:
															new String (sndSeq))
												+ "\t" +
												mutant.mutationType + "\n");
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
						} else {
							stats.nPosDuplexCandidatesForDisagreementQ2TooHighCoverage.accept(location, examResults.nGoodDuplexesIgnoringDisag);
							for (ComparablePair<Mutation, Mutation> d: examResults.disagreements) {
								stats.topBottomDisagreementsQ2TooHighCoverage.accept(location, d);
							}
						}

						if (	(!localTooHighCoverage) &&
								examResults.nGoodDuplexes >= argValues.minQ2DuplexesToCallMutation && 
								examResults.nGoodOrDubiousDuplexes >= argValues.minQ1Q2DuplexesToCallMutation &&
								analyzers.stream().filter(b -> b != a).mapToInt(b -> 
								analyzerCandidateLists.get(b.idx).nGoodOrDubiousDuplexes).sum()
								>= a.minNumberDuplexesSisterArm
								) {

							examResults.analyzedCandidateSequences.stream().filter(c -> !c.isHidden()).flatMap(c -> c.getDuplexes().stream()).
							filter(dr -> dr.localQuality.getMin().compareTo(GOOD) >= 0).map(DuplexRead::getDistanceToLigSite).
							forEach(i -> {if (i != Integer.MIN_VALUE && i != Integer.MAX_VALUE)
								stats.crossAnalyzerQ2CandidateDistanceToLigationSite.insert(i);});

							stats.nPosDuplexQualityQ2OthersQ1Q2.accept(location, examResults.nGoodDuplexes);
							stats.nPosQualityQ2OthersQ1Q2.increment(location);
							//XXX The following includes all candidates at *all* positions considered in
							//processing chunk 
							stats.nReadsAtPosQualityQ2OthersQ1Q2.insert(
									examResults.analyzedCandidateSequences.
									stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
							stats.nQ1Q2AtPosQualityQ2OthersQ1Q2.insert(
									examResults.analyzedCandidateSequences.
									stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());
						}
					});//End parallel loop over analyzers to deal with coverage

					//Filter candidates to identify non-wildtype versions
					List<CandidateSequence> mutantCandidates = analyzerCandidateLists.stream().
							map(c -> c.analyzedCandidateSequences).
							flatMap(Collection::stream).filter(c -> {
								boolean isMutant = !c.getMutationType().isWildtype();
								return isMutant;
							}).
							collect(Collectors.toList());

					final Quality maxCandMutQuality = mutantCandidates.stream().
							map(c -> c.getQuality().getMin()).max(Quality::compareTo).orElse(ATROCIOUS);

					final boolean forceReporting = forceOutputAtLocations.get(location) != null;
					@SuppressWarnings("null")
					final boolean randomlySelected = forceReporting &&
						forceOutputAtLocations.get(location);

					//Only report details when at least one mutant candidate is of Q2 quality
					if (forceReporting || (maxCandMutQuality.compareTo(GOOD) >= 0)) {
						if (NONTRIVIAL_ASSERTIONS) for (GenomeFeatureTester t: excludeBEDs) {
							Assert.isFalse(t.test(location), "%s excluded by %s", location, t);
						}

						//Refilter also allowing Q1 candidates to compare output of different
						//analyzers
						final List<CandidateSequence> allQ1Q2Candidates = analyzerCandidateLists.stream().
								map(l -> l.analyzedCandidateSequences).
								flatMap(Collection::stream).
								filter(c -> {
									Assert.isFalse(c.getLocation().distanceOnSameContig(location) > 0);
									return c.getQuality().getMin().compareTo(POOR) > 0;}).
								filter(c -> !c.isHidden()).
								sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
								collect(Collectors.toList());

						final List<CandidateSequence> allCandidatesIncludingDisag = analyzerCandidateLists.stream().
								map(l -> l.analyzedCandidateSequences).
								flatMap(Collection::stream).
								filter(c -> c.getLocation().equals(location) && c.getQuality().getQuality(Assay.MAX_DPLX_Q_IGNORING_DISAG).compareTo(POOR) > 0).
								sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
								collect(Collectors.toList());

						final List<CandidateSequence> distinctQ1Q2Candidates = allQ1Q2Candidates.stream().distinct().
								//Sorting might not be necessary
								sorted((a,b) -> a.getMutationType().compareTo(b.getMutationType())).
								collect(Collectors.toList());

						for (CandidateSequence candidate: distinctQ1Q2Candidates) {
							String baseOutput0 = "";

							if (randomlySelected) {
								baseOutput0 += "+";
							}
							
							if (!candidate.getMutationType().isWildtype() &&
									allCandidatesIncludingDisag.stream().filter(c -> c.equals(candidate) &&
											(c.getQuality().getMin().compareTo(GOOD) >= 0 ||
											(c.getSupplQuality() != null && nonNullify(c.getSupplQuality()).compareTo(GOOD) >= 0))).count() >= 2) {
								baseOutput0 += "!";
							} else if (allCandidatesIncludingDisag.stream().filter(c -> c.equals(candidate)).count() == 1 &&
									//Mutant candidate shows up only once (and therefore in only 1 analyzer)
									!candidate.getMutationType().isWildtype() &&
									candidate.getQuality().getMin().compareTo(GOOD) >= 0 &&
									(candidate.getnGoodDuplexes() >= argValues.minQ2DuplexesToCallMutation) &&
									candidate.getnGoodOrDubiousDuplexes() >= argValues.minQ1Q2DuplexesToCallMutation &&
									(!tooHighCoverage.get()) &&
									allQ1Q2Candidates.stream().filter(c -> c.getOwningAnalyzer() != candidate.getOwningAnalyzer()).
									mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum() >= argValues.minNumberDuplexesSisterArm
									) {
								//Then highlight the output with a *

								baseOutput0 += "*";

								candidate.nDuplexesSisterArm = allQ1Q2Candidates.stream().filter(c -> c.getOwningAnalyzer() != candidate.getOwningAnalyzer()).
										mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum();

								baseOutput0 += candidate.getnGoodOrDubiousDuplexes();

								analyzers.forEach(a -> {
									final @NonNull AnalysisStats stats = a.stats.get(statsIndex);
									CandidateSequence c = analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
											stream().filter(cd -> cd.equals(candidate)).findAny().orElse(null);

									if (c != null) {
										stats.nPosCandidatesForUniqueMutation.accept(location, c.getnGoodDuplexes());
										stats.uniqueMutantQ2CandidateQ1Q2DCoverage.insert((int) c.getTotalGoodOrDubiousDuplexes());
										if (!repetitiveBEDs.isEmpty()) {
											boolean repetitive = false;
											for (GenomeFeatureTester t: repetitiveBEDs) {
												if (t.test(location)) {
													repetitive = true;
													break;
												}
											}
											if (repetitive) {
												stats.uniqueMutantQ2CandidateQ1Q2DCoverageRepetitive.insert((int) c.getTotalGoodOrDubiousDuplexes());
											} else {
												stats.uniqueMutantQ2CandidateQ1Q2DCoverageNonRepetitive.insert((int) c.getTotalGoodOrDubiousDuplexes());																
											}
										}
									}
								});
								analyzers.stream().filter(a -> analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
										contains(candidate)).forEach(a -> {
											a.stats.get(statsIndex).nReadsAtPosWithSomeCandidateForQ2UniqueMutation.insert(
													analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
													stream().mapToInt(c -> c.getNonMutableConcurringReads().size()).sum());
										});
								analyzers.stream().filter(a -> analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
										contains(candidate)).forEach(a -> {
											a.stats.get(statsIndex).nQ1Q2AtPosWithSomeCandidateForQ2UniqueMutation.insert(
													analyzerCandidateLists.get(a.idx).analyzedCandidateSequences.
													stream().mapToInt(CandidateSequence::getnGoodOrDubiousDuplexes).sum());
										});
							}

							if (!allQ1Q2Candidates.stream().anyMatch(c -> c.getOwningAnalyzer() == candidate.getOwningAnalyzer() &&
									c.getMutationType().isWildtype())) {
								//At least one analyzer does not have a wildtype candidate
								baseOutput0 += "|";
							} 

							if (!distinctQ1Q2Candidates.stream().anyMatch(c -> c.getMutationType().isWildtype())) {
								//No wildtype candidate at this position
								baseOutput0 += "_";
							}

							String baseOutput = baseOutput0 + "\t" + location + "\t" + candidate.getKind() + "\t" +
									(!candidate.getMutationType().isWildtype() ? 
											candidate.getChange() : "");

							//Now output information for each analyzer
							for (Mutinack analyzer: analyzers) {
								final List<CandidateSequence> l = analyzerCandidateLists.get(analyzer.idx).analyzedCandidateSequences.
										stream().filter(c -> c.equals(candidate)).collect(Collectors.toList());

								String line = baseOutput + "\t" + analyzer.name + "\t";

								final CandidateSequence c;
								int nCandidates = l.size();
								if (nCandidates > 1) {
									throw new AssertionFailedException();
								} else if (nCandidates == 0) {
									//Analyzer does not have matching candidate (i.e. it did not get
									//any reads matching the mutation)
									continue;
								} else {//1 candidate
									c = l.get(0);
									NumberFormat formatter = mediumLengthFloatFormatter.get();
									/**
									 * Make sure this stays in sync with Mutinack.outputHeader
									 */
									line += c.getnGoodDuplexes() + "\t" + 
											c.getnGoodOrDubiousDuplexes() + "\t" +
											c.getnDuplexes() + "\t" +
											c.getNonMutableConcurringReads().size() + "\t" +
											formatter.format((c.getnGoodDuplexes() / c.getTotalGoodDuplexes())) + "\t" +
											formatter.format((c.getnGoodOrDubiousDuplexes() / c.getTotalGoodOrDubiousDuplexes())) + "\t" +
											formatter.format((c.getnDuplexes() / c.getTotalAllDuplexes())) + "\t" +
											formatter.format((c.getNonMutableConcurringReads().size() / c.getTotalReadsAtPosition())) + "\t" +
											(c.getAverageMappingQuality() == -1 ? "?" : c.getAverageMappingQuality()) + "\t" +
											c.nDuplexesSisterArm + "\t" +
											c.insertSize + "\t" +
											c.minDistanceToLigSite + "\t" +
											c.maxDistanceToLigSite + "\t" +
											c.positionInRead + "\t" +
											c.readEL + "\t" +
											c.readName + "\t" +
											c.readAlignmentStart  + "\t" +
											c.mateReadAlignmentStart  + "\t" +
											c.readAlignmentEnd + "\t" +
											c.mateReadAlignmentEnd + "\t" +
											c.refPositionOfMateLigationSite + "\t" +
											(argValues.outputDuplexDetails ?
													c.getIssues() : "") + "\t" +
													c.getMedianPhredAtPosition() + "\t" +
													(c.getMinInsertSize() == -1 ? "?" : c.getMinInsertSize()) + "\t" +
													(c.getMaxInsertSize() == -1 ? "?" : c.getMaxInsertSize()) + "\t" +
													(c.getSupplementalMessage() != null ? c.getSupplementalMessage() : "") +
													"\t";

									boolean needComma = false;
									for (Entry<String, GenomeFeatureTester> a: analyzer.filtersForCandidateReporting.entrySet()) {
										if (!(a.getValue() instanceof BedComplement) && a.getValue().test(location)) {
											if (needComma) {
												line += ", ";
											}
											needComma = true;
											Object val = a.getValue().apply(location);
											line += a.getKey() + (val != null ? (": " + val) : "");
										}
									}
								}

								try {
									final @Nullable OutputStreamWriter ambw = analyzer.stats.get(statsIndex).mutationBEDWriter;
									if (ambw != null) {
										ambw.append(location.getContigName() + "\t" + 
												(location.position + 1) + "\t" + (location.position + 1) + "\t" +
												candidate.getKind() + "\t" + baseOutput0 + "\t" +
												c.getnGoodDuplexes() +
												"\n");
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}

								analysisChunk.out.println(line);
							}//End loop over analyzers
						}//End loop over mutation candidates
					}//End of candidate reporting
				}//End !rnaSeq
				lastProcessedPosition.set(position);
				
				if (readsToWrite != null) {
					analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {

						Mutinack analyzer = subAnalyzer.getAnalyzer();

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
							final Quality minDuplexQualityCopy = duplexRead.minQuality;
							final Quality maxDuplexQualityCopy = duplexRead.maxQuality;
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
									samRecord.setAttribute("DS", Integer.valueOf(nReads));
									samRecord.setAttribute("DT", Integer.valueOf(duplexRead.topStrandRecords.size()));
									samRecord.setAttribute("DB", Integer.valueOf(duplexRead.bottomStrandRecords.size()));
									String info = topOrBottom.get() + " Q" +
											minDuplexQualityCopy.toShortString() + "->" + maxDuplexQualityCopy.toShortString() + " P" + duplexRead.getMinMedianPhred() +
											" D" + mediumLengthFloatFormatter.get().format(duplexRead.referenceDisagreementRate);
									samRecord.setAttribute("DI", info);
									samRecord.setAttribute("DN", randomIndexForDuplexName + "--" + System.identityHashCode(duplexRead));
									samRecord.setAttribute("VB", new String(e.variableBarcode));
									samRecord.setAttribute("VM", new String(e.getMateVariableBarcode()));
									samRecord.setAttribute("DE", duplexRead.leftAlignmentStart + "-" + duplexRead.leftAlignmentEnd + " --- " +
											duplexRead.rightAlignmentStart + "-" + duplexRead.rightAlignmentEnd);
									if (duplexRead.issues.size() > 0) {
										samRecord.setAttribute("IS", duplexRead.issues.toString());
									} else {
										samRecord.setAttribute("IS", null);
									}
									samRecord.setAttribute("AI", Integer.valueOf(analyzer.idx));
									readsToWrite.put(e.getFullName(), samRecord);
								}
							};
							if (argValues.collapseFilteredReads) {
								final ExtendedSAMRecord r;
								topOrBottom.set("U");
								if (duplexRead.topStrandRecords.size() > 0) {
									r = duplexRead.topStrandRecords.get(0);
								} else if (duplexRead.bottomStrandRecords.size() > 0) {
									r = duplexRead.bottomStrandRecords.get(0);
								} else {
									//TODO Should that be an error?
									r = null;
								}
								if (r != null) {
									queueWrite.accept(r);
									if (r.getMate() != null) {
										queueWrite.accept(r.getMate());
									}
								}
							} else {
								topOrBottom.set("T");
								duplexRead.topStrandRecords.forEach(queueWrite);
								topOrBottom.set("B");
								duplexRead.bottomStrandRecords.forEach(queueWrite);
							}
						}//End writing
					});
				}//End readsToWrite != null
			}}//End loop over positions
			
			if (ENABLE_TRACE && shouldLog(TRACE)) {
				logger.trace("Going from " + saveLastProcessedPosition + " to " + lastProcessedPosition.get() + " for chunk " + analysisChunk);
			}

			analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
				Mutinack analyzer = subAnalyzer.getAnalyzer();

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
				final int maxInsertSize = analyzer.maxInsertSize;
				if (analyzer.rnaSeq) {
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
				if (subAnalyzer.extSAMCache instanceof THashMap) {
					((THashMap<?,?>) subAnalyzer.extSAMCache).compact();
				}
			});//End parallel loop over subAnalyzers

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
				analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
					Iterator<Entry<SequenceLocation, THashSet<CandidateSequence>>> it =
							subAnalyzer.candidateSequences.entrySet().iterator();
					final SequenceLocation lowerBound = new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get());
					while (it.hasNext()) {
						Entry<SequenceLocation, THashSet<CandidateSequence>> v = it.next();
						Assert.isFalse(v.getKey().contigIndex != contigIndex,
								"Problem with contig indices, " +
									"possibly because contigs not alphabetically sorted in reference genome .fa file");
						if (v.getKey().compareTo(lowerBound) < 0) {
							it.remove();
						}
					}
				});
			}

			Assert.noException(() -> {
				//Check no sequences have been left behind
				analysisChunk.subAnalyzers.parallelStream().forEach(subAnalyzer -> {
					final SequenceLocation lowerBound = new SequenceLocation(contigIndex, contigName, lastProcessedPosition.get());
					subAnalyzer.candidateSequences.keySet().parallelStream().forEach(
							e -> {
								Assert.isFalse(e.contigIndex != contigIndex);
								Assert.isFalse(e.compareTo(lowerBound) < 0,
										"pauseAt: %s; lastProcessedPosition: %s but found: %s for chunk %s",
											pauseAt.get(), lastProcessedPosition, e, analysisChunk);});
				});
			});

			final int maxLastProcessable = analysisChunk.subAnalyzers.stream().mapToInt(a -> a.lastProcessablePosition.get()).
					max().getAsInt();

			if (maxLastProcessable == previousLastProcessable.get()) {
				logger.debug("Phaser " + this + " will terminate");
				return true;
			}

			previousLastProcessable.set(maxLastProcessable);
			pauseAt.set(maxLastProcessable + PROCESSING_CHUNK);
			
			return false;
		} catch (Exception e) {
			forceTermination();
			throw new RuntimeException(e);
		} finally {
			for (SubAnalyzer subAnalyzer: analysisChunk.subAnalyzers) {
				subAnalyzer.stats = subAnalyzer.analyzer.stats.get(0);
			}
		}
	}//End onAdvance
	
	static final boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}

}
