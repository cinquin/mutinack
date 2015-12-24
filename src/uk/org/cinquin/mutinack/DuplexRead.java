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
import static uk.org.cinquin.mutinack.Assay.AVERAGE_N_CLIPPED;
import static uk.org.cinquin.mutinack.Assay.BOTTOM_STRAND_MAP_Q2;
import static uk.org.cinquin.mutinack.Assay.CLOSE_TO_LIG;
import static uk.org.cinquin.mutinack.Assay.CONSENSUS_Q0;
import static uk.org.cinquin.mutinack.Assay.CONSENSUS_Q1;
import static uk.org.cinquin.mutinack.Assay.CONSENSUS_THRESHOLDS_1;
import static uk.org.cinquin.mutinack.Assay.DISAGREEMENT;
import static uk.org.cinquin.mutinack.Assay.INSERT_SIZE;
import static uk.org.cinquin.mutinack.Assay.MISSING_STRAND;
import static uk.org.cinquin.mutinack.Assay.N_READS_WRONG_PAIR;
import static uk.org.cinquin.mutinack.Assay.N_STRANDS;
import static uk.org.cinquin.mutinack.Assay.N_STRANDS_ABOVE_MIN_PHRED;
import static uk.org.cinquin.mutinack.Assay.TOP_STRAND_MAP_Q2;
import static uk.org.cinquin.mutinack.MutationType.DELETION;
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.Quality.DUBIOUS;
import static uk.org.cinquin.mutinack.Quality.GOOD;
import static uk.org.cinquin.mutinack.Quality.MAXIMUM;
import static uk.org.cinquin.mutinack.Quality.POOR;
import static uk.org.cinquin.mutinack.Quality.max;
import static uk.org.cinquin.mutinack.Quality.min;
import static uk.org.cinquin.mutinack.misc_util.DebugControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.misc_util.Util.shortLengthFloatFormatter;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.standford.nlp.util.HasInterval;
import contrib.edu.standford.nlp.util.Interval;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.TObjectIntHashMap;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateCounter;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * Equality and hashcode ignore list of reads assigned to duplex, quality, and roughLocation,
 * among other things.
 * @author olivier
 *
 */
public final class DuplexRead implements HasInterval<Integer> {
	
	final static Logger logger = LoggerFactory.getLogger(DuplexRead.class);
	
	public byte @NonNull[] leftBarcode, rightBarcode;
	public SequenceLocation leftAlignmentStart, rightAlignmentStart, leftAlignmentEnd, rightAlignmentEnd;
	public final @NonNull List<@NonNull ExtendedSAMRecord> topStrandRecords = new ArrayList<>(100), 
			bottomStrandRecords = new ArrayList<>(100);
	public int totalNRecords = -1;
	public final @NonNull List<String> issues = new ArrayList<>(10);
	private @Nullable Interval<Integer> interval;
	//Only used for debugging
	public boolean invalid = false;
	public int nReadsWrongPair = 0;
	
	public static int intervalSlop = 0;
	
	/**
	 * Quality factoring in number of reads for top or bottom strand, percent consensus for
	 * reads from a given strand. Minimum and maximum across all base positions in duplex.
	*/
	@NonNull Quality minQuality = Quality.MAXIMUM, maxQuality = Quality.MINIMUM;
	DetailedQualities localQuality;
	SequenceLocation roughLocation;
	float referenceDisagreementRate;
	int averageNClipped;
	int position0;
	private int position3;
	private int maxDistanceToLig = Integer.MIN_VALUE;
	public final boolean leftBarcodeNegativeStrand, rightBarcodeNegativeStrand;
	
	public DuplexRead(byte @NonNull[] leftBarcode, byte @NonNull[] rightBarcode,
			boolean leftBarcodeNegativeStrand, boolean rightBarcodeNegativeStrand) {
		this.leftBarcode = leftBarcode;
		this.rightBarcode = rightBarcode;
		this.leftBarcodeNegativeStrand = leftBarcodeNegativeStrand;
		this.rightBarcodeNegativeStrand = rightBarcodeNegativeStrand;
	}
	
	public void assertAllBarcodesEqual() {
		if (DebugControl.NONTRIVIAL_ASSERTIONS) {
			final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords =
					new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
			allDuplexRecords.addAll(topStrandRecords);
			allDuplexRecords.addAll(bottomStrandRecords);
			allDuplexRecords.stream().forEach(r -> {
				if (r.record.getInferredInsertSize() < 0) {
					if (!basesEqual(rightBarcode, r.variableBarcode, true, 0)) {
						throw new AssertionFailedException("Unequal barcodes: " +
								new String(rightBarcode) + " vs " + new String(r.variableBarcode) +
								"; other barcode is " + new String(leftBarcode) + " and mate is " +
								new String(r.getMateVariableBarcode()) + "; nRecords=" +
								allDuplexRecords.size() + " (1)");
					}
				} else {
					if (!basesEqual(leftBarcode, r.variableBarcode, true, 0)) {
						throw new AssertionFailedException("Unequal barcodes: " +
								new String(leftBarcode) + " vs " + new String(r.variableBarcode) +
								"; other barcode is " + new String(rightBarcode) + " and mate is " +
								new String(r.getMateVariableBarcode()) + "; nRecords=" +
								allDuplexRecords.size() + " (2)");
					}					
				}
			});
		}
	}
	
	@SuppressWarnings("null")
	public void computeConsensus(boolean allReadsSameBarcode, int barcodeLength) {
		final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords = 
				new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
		allDuplexRecords.addAll(topStrandRecords);
		allDuplexRecords.addAll(bottomStrandRecords);
		totalNRecords = allDuplexRecords.size();
		if (allReadsSameBarcode) {
			leftBarcode = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					findAny().map(r -> r.variableBarcode).orElse(ExtendedSAMRecord.getNs()));
			rightBarcode = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					findAny().map(r -> r.variableBarcode).orElse(ExtendedSAMRecord.getNs()));
		} else {
			leftBarcode = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					collect(Collectors.toList()), barcodeLength);
			rightBarcode = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					collect(Collectors.toList()), barcodeLength);
		}		
	}
	
	public void resetMaxDistanceToLigSite() {
		maxDistanceToLig = Integer.MIN_VALUE;
	}
	
	public void acceptDistanceToLigSite(int d) {
		if (d > maxDistanceToLig) {
			maxDistanceToLig = d;
		}
	}
	
	public int getDistanceToLigSite() {
		return maxDistanceToLig;
	}
		
	void setPositions(int position0, int position3) {
		this.position0 = position0;
		this.position3 = position3;
	}

	public int distanceTo(DuplexRead d2) {
		return Math.max(Math.abs(position0 - d2.position0), Math.abs(position3 - d2.position3));
	}
	
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((leftAlignmentEnd == null) ? 0 : leftAlignmentEnd.hashCode());
		result = prime * result + ((leftAlignmentStart == null) ? 0 : leftAlignmentStart.hashCode());
		result = prime * result + Arrays.hashCode(leftBarcode);
		result = prime * result + Arrays.hashCode(rightBarcode);
		result = prime * result + ((rightAlignmentEnd == null) ? 0 : rightAlignmentEnd.hashCode());
		result = prime * result + ((rightAlignmentStart == null) ? 0 : rightAlignmentStart.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof DuplexRead))
			return false;
		DuplexRead other = (DuplexRead) obj;
		if (leftAlignmentEnd == null) {
			if (other.leftAlignmentEnd != null)
				return false;
		} else if (!leftAlignmentEnd.equals(other.leftAlignmentEnd))
			return false;
		if (leftAlignmentStart == null) {
			if (other.leftAlignmentStart != null)
				return false;
		} else if (!leftAlignmentStart.equals(other.leftAlignmentStart))
			return false;
		if (!Arrays.equals(leftBarcode, other.leftBarcode))
			return false;
		if (!Arrays.equals(rightBarcode, other.rightBarcode))
			return false;
		if (rightAlignmentEnd == null) {
			if (other.rightAlignmentEnd != null)
				return false;
		} else if (!rightAlignmentEnd.equals(other.rightAlignmentEnd))
			return false;
		if (rightAlignmentStart == null) {
			if (other.rightAlignmentStart != null)
				return false;
		} else if (!rightAlignmentStart.equals(other.rightAlignmentStart))
			return false;
		return true;
	}
		
	public int getMinMedianPhred() {
		int result = Integer.MAX_VALUE;
		for (ExtendedSAMRecord r: topStrandRecords) {
			int i = r.medianPhred;
			if (i < result) {
				result = i;
			}
		}
		
		for (ExtendedSAMRecord r: bottomStrandRecords) {
			int i = r.medianPhred;
			if (i < result) {
				result = i;
			}
		}
		
		return result;
	}
		
	@Override
	public String toString() {
		return leftAlignmentStart + ", " + rightAlignmentStart + ", " + 
			leftAlignmentEnd + ", " + rightAlignmentEnd + ", " + new String(leftBarcode) + "-" + 
			new String(rightBarcode) + "," + " Q" + minQuality +
			(localQuality == null ? "" : (" " + localQuality.getQualities().entrySet().stream().
				min((e1, e2) -> e1.getValue().compareTo(e2.getValue())).map(e -> e.getKey()).map(
						i -> i.toString()).orElse("") + " ")) +
			"->" + maxQuality + topStrandRecords.toString() + " "
			+ bottomStrandRecords.toString();
	}

	@Override
	public Interval<Integer> getInterval() {
		if (interval == null) {
			interval = Interval.toInterval(leftAlignmentStart != null ? leftAlignmentStart.position - intervalSlop : 0,
					leftAlignmentEnd != null ? leftAlignmentEnd.position + intervalSlop : Integer.MAX_VALUE);
			if (DebugControl.NONTRIVIAL_ASSERTIONS && interval == null) {
				throw new AssertionFailedException();
			}
		}
		return interval;
	}
	
	public void examineAtLoc(@NonNull SequenceLocation location, LocationExaminationResults result,
			@NonNull Set<CandidateSequence> candidateSet,
			@NonNull Set<@NonNull Assay> assaysToIgnoreForDisagreementQuality,
			boolean hasHiddenCandidate,
			@NonNull CandidateCounter topCounter,
			@NonNull CandidateCounter bottomCounter,
			@NonNull List<@NonNull ExtendedSAMRecord> selectedTopStrandRecords,
			@NonNull List<@NonNull ExtendedSAMRecord> selectedBottomStrandRecords,
			Mutinack analyzer, AnalysisStats stats) {
		
		topCounter.minBasePhredScore = 0;
		bottomCounter.minBasePhredScore = 0;
		resetMaxDistanceToLigSite();
		final @NonNull List<@NonNull ComparablePair<Mutation, Mutation>> duplexDisagreements = 
				new ArrayList<>();
		stats.nLociDuplex.accept(location);
		final Entry<CandidateSequence, SettableInteger> bottom, top;
		boolean disagreement = false;

		//Find if there is a clear candidate with which duplexRead is
		//associated; if not, discard it
		//The same reads can be associated with two or three candidates in case
		//there is an insertion or deletion (since the wildtype base or a
		//substitution might be present at the same position).
		//Therefore we count the number of unique records associated with
		//this position using Sets.
		
		topCounter.setRecords(selectedTopStrandRecords);
		topCounter.compute();
		
		bottomCounter.setRecords(selectedBottomStrandRecords);
		bottomCounter.compute();
		
		{//Make sure we do not leak bottom0 or top0
			final Entry<CandidateSequence, SettableInteger> bottom0 = bottomCounter.candidateCounts.entrySet().parallelStream().
					max((a,b) -> Integer.compare(a.getValue().get(), b.getValue().get())).
					orElse(null);
			bottom = bottom0 == null ? null :
				new AbstractMap.SimpleImmutableEntry<>(bottom0.getKey(), bottom0.getValue());

			final Entry<CandidateSequence, SettableInteger> top0 = topCounter.candidateCounts.entrySet().parallelStream().
					max((a,b) -> Integer.compare(a.getValue().get(), b.getValue().get())).
					orElse(null);
			top = top0 == null ? null :
				new AbstractMap.SimpleImmutableEntry<>(top0.getKey(), top0.getValue());
		}
		
		final List<ExtendedSAMRecord> allRecords = 
				new ArrayList<>(topCounter.keptRecords.size() +
						bottomCounter.keptRecords.size());
		allRecords.addAll(topCounter.keptRecords);
		allRecords.addAll(bottomCounter.keptRecords);

		final int nTopStrandsWithCandidate = topCounter.keptRecords.size();				
		final int nBottomStrandsWithCandidate = bottomCounter.keptRecords.size();
		
		topCounter.reset();
		bottomCounter.reset();

		stats.copyNumberOfDuplexTopStrands.insert(nTopStrandsWithCandidate);
		stats.copyNumberOfDuplexBottomStrands.insert(nBottomStrandsWithCandidate);

		final @NonNull DetailedQualities dq = new DetailedQualities();
		
		if (nBottomStrandsWithCandidate >= analyzer.minReadsPerStrandQ2 &&
				nTopStrandsWithCandidate >= analyzer.minReadsPerStrandQ2) {
			dq.addUnique(N_STRANDS, GOOD);
		} else if (nBottomStrandsWithCandidate >= analyzer.minReadsPerStrandQ1 &&
				nTopStrandsWithCandidate >= analyzer.minReadsPerStrandQ1) {
			dq.addUnique(N_STRANDS, DUBIOUS);
			stats.nLociDuplexTooFewReadsPerStrand2.increment(location);
			result.strandCoverageImbalance = Math.max(result.strandCoverageImbalance,
					Math.abs(selectedBottomStrandRecords.size() - selectedTopStrandRecords.size()));
			if (analyzer.logReadIssuesInOutputBam) {
				if (nBottomStrandsWithCandidate < analyzer.minReadsPerStrandQ2)
					issues.add(location + "TFR1B");
				if (nTopStrandsWithCandidate < analyzer.minReadsPerStrandQ2)
					issues.add(location + "TFR1T");
			}
		} else {
			dq.addUnique(N_STRANDS, POOR);
			stats.nLociDuplexTooFewReadsPerStrand1.increment(location);
			if (selectedBottomStrandRecords.size() == 0 || selectedTopStrandRecords.size() == 0) {
				result.nMissingStrands++;
			}
			if (analyzer.logReadIssuesInOutputBam) {
				if (nTopStrandsWithCandidate < analyzer.minReadsPerStrandQ1)
					issues.add(location + "TFR0B");
				if (nBottomStrandsWithCandidate < analyzer.minReadsPerStrandQ1)
					issues.add(location + "TFR0T");
			}
		}
						
		nReadsWrongPair = (int) allRecords.stream().filter(ExtendedSAMRecord::formsWrongPair).
				count();
		
		if (nReadsWrongPair > 0) {
			dq.addUnique(N_READS_WRONG_PAIR, DUBIOUS);
		}
		
		if (dq.getMin().compareTo(GOOD) >= 0) {
			//Check if criteria are met even if ignoring bases with
			//Phred quality scores that do not mean Q2 threshold
			
			topCounter.minBasePhredScore = analyzer.minBasePhredScoreQ2;
			topCounter.compute();
			bottomCounter.minBasePhredScore = analyzer.minBasePhredScoreQ2;
			bottomCounter.compute();
			if (topCounter.keptRecords.size() < analyzer.minReadsPerStrandQ2 ||
					bottomCounter.keptRecords.size() < analyzer.minReadsPerStrandQ2) {
				dq.addUnique(N_STRANDS_ABOVE_MIN_PHRED, DUBIOUS);
			}
		}
		
		if (averageNClipped > analyzer.maxAverageBasesClipped) {
			dq.addUnique(AVERAGE_N_CLIPPED, DUBIOUS);
		}
		
		dq.addUnique(TOP_STRAND_MAP_Q2, topCounter.keptRecords.stream().
				mapToInt(r -> r.record.getMappingQuality()).
				max().orElse(255) >= analyzer.minMappingQualityQ2 ? MAXIMUM : DUBIOUS);
		
		dq.addUnique(BOTTOM_STRAND_MAP_Q2, bottomCounter.keptRecords.stream().
				mapToInt(r -> r.record.getMappingQuality()).
				max().orElse(255) >= analyzer.minMappingQualityQ2 ? MAXIMUM : DUBIOUS);
		
		final boolean bothStrandsPresent = bottom != null && top != null;
		final boolean thresholds2Met, thresholds1Met;

		thresholds2Met = ((top != null) ? top.getValue().get() >= analyzer.minConsensusThresholdQ2 * nTopStrandsWithCandidate : false) &&
			(bottom != null ? bottom.getValue().get() >= analyzer.minConsensusThresholdQ2 * nBottomStrandsWithCandidate : false);

		thresholds1Met = (top != null ? top.getValue().get() >= analyzer.minConsensusThresholdQ1 * nTopStrandsWithCandidate : true) &&
			(bottom != null ? bottom.getValue().get() >= analyzer.minConsensusThresholdQ1 * nBottomStrandsWithCandidate : true);
		
		if (!thresholds1Met) {
			//TODO Following quality assignment is redundant with CONSENSUS_Q0 below
			dq.addUnique(CONSENSUS_THRESHOLDS_1, ATROCIOUS);
			if (analyzer.logReadIssuesInOutputBam) {
				issues.add(location + " CS0Y_" + (top != null ? top.getValue().get() : "x") +
						"_" + nTopStrandsWithCandidate + "_" +
						(bottom != null ? bottom.getValue().get() : "x") + 
						"_" + nBottomStrandsWithCandidate);
			}
		}

		//TODO compute consensus insert size instead of extremes
		final IntSummaryStatistics insertSizeStats = Stream.concat(selectedBottomStrandRecords.stream(), selectedTopStrandRecords.stream()).
				mapToInt(r -> Math.abs(r.getInsertSizeNoBarcodes(true))).summaryStatistics();

		final int localMaxInsertSize = insertSizeStats.getMax();
		final int localMinInsertSize = insertSizeStats.getMin();

		if (localMaxInsertSize < analyzer.minInsertSize || localMinInsertSize > analyzer.maxInsertSize) {
			dq.addUnique(INSERT_SIZE, DUBIOUS);
		}
		
		if (NONTRIVIAL_ASSERTIONS && invalid) {
			throw new AssertionFailedException();
		}
		
		Handle<Boolean> seenFirstOfPair = new Handle<>();
		Handle<Boolean> seenSecondOfPair = new Handle<>();
		for (CandidateSequence candidate: candidateSet) {
			seenFirstOfPair.set(false);
			seenSecondOfPair.set(false);
			candidate.getNonMutableConcurringReads().forEachEntry((r, dist) -> {
				if (r.duplexRead == this) {
					acceptDistanceToLigSite(dist);
					if (r.record.getFirstOfPairFlag()) {
						seenFirstOfPair.set(true);
					} else {
						seenSecondOfPair.set(true);
					}
					if (seenFirstOfPair.get() && seenSecondOfPair.get()) {
						return false;
					}
				}
				return true;
			});
		}
		
		int distanceToLigSite = getDistanceToLigSite();
		if (distanceToLigSite <= analyzer.ignoreFirstNBasesQ1) {
			dq.addUnique(CLOSE_TO_LIG, POOR);
		} else if (distanceToLigSite <= analyzer.ignoreFirstNBasesQ2) {
			dq.addUnique(CLOSE_TO_LIG, DUBIOUS);
		}
		
		if (bottom != null && top != null) {
			if (thresholds2Met) {
				//localQuality = min(localQuality, GOOD);
			} else if (thresholds1Met) {
				dq.addUnique(CONSENSUS_Q1, DUBIOUS);
				stats.nLociDuplexWithLackOfStrandConsensus2.increment(location);
				if (analyzer.logReadIssuesInOutputBam) {
					if (top.getValue().get() < analyzer.minConsensusThresholdQ2 * nTopStrandsWithCandidate)
						issues.add(location + " CS1T_" + shortLengthFloatFormatter.get().format
								(((float) top.getValue().get()) / nTopStrandsWithCandidate));
					if (bottom.getValue().get() < analyzer.minConsensusThresholdQ2 * nBottomStrandsWithCandidate)
						issues.add(location + " CS1B_" + shortLengthFloatFormatter.get().format
								(((float) bottom.getValue().get()) / nBottomStrandsWithCandidate));
				}
			} else {
				dq.addUnique(CONSENSUS_Q0, POOR);
				stats.nLociDuplexWithLackOfStrandConsensus1.increment(location);
				if (analyzer.logReadIssuesInOutputBam) {
					if (top.getValue().get() < analyzer.minConsensusThresholdQ1 * nTopStrandsWithCandidate)
						issues.add(location + " CS0T_" + shortLengthFloatFormatter.get().format
								(((float) top.getValue().get()) / nTopStrandsWithCandidate));
					if (bottom.getValue().get() < analyzer.minConsensusThresholdQ1 * nBottomStrandsWithCandidate)
						issues.add(location + " CS0B_" + shortLengthFloatFormatter.get().format
								(((float) bottom.getValue().get()) / nBottomStrandsWithCandidate));
				}
			}
		} else {//Only the top or bottom strand is represented
			Entry<CandidateSequence, SettableInteger> strand = top != null ? top : bottom;
			float total = nTopStrandsWithCandidate + nBottomStrandsWithCandidate; //One is 0, doesn't matter which
			if (strand != null && strand.getValue().get() < analyzer.minConsensusThresholdQ1 * total) {
				if (analyzer.logReadIssuesInOutputBam) {
					issues.add(location + " CS0X_" + shortLengthFloatFormatter.get().format
							(strand.getValue().get() / total));
				}
			}
			dq.addUnique(MISSING_STRAND, DUBIOUS);
		}
		
		final boolean highEnoughQualForDisagreement = 
				dq.getMin().compareTo(GOOD) >= 0 &&
				bottom.getValue().get() >= analyzer.minReadsPerStrandForDisagreement &&
				top.getValue().get() >= analyzer.minReadsPerStrandForDisagreement &&
				!hasHiddenCandidate;
		
		if (bothStrandsPresent && highEnoughQualForDisagreement) {
			stats.nLociDuplexesCandidatesForDisagreementQ2.accept(location);
		}

		if (bothStrandsPresent &&
				(!Arrays.equals(bottom.getKey().getSequence(), top.getKey().getSequence()) ||
						!bottom.getKey().getMutationType().equals(top.getKey().getMutationType()))) {

			dq.addUnique(DISAGREEMENT, ATROCIOUS);
			issues.add(location + " DSG");
			disagreement = true;

			if (highEnoughQualForDisagreement) {
				final Mutation m1 = new Mutation(top.getKey());
				final Mutation m2 = new Mutation(bottom.getKey());

				if (!m1.mutationType.isWildtype() && !m2.mutationType.isWildtype()) {
					stats.nLociDuplexWithTopBottomDuplexDisagreementNoWT.accept(location);
				} else {
					final Mutation actualMutant = (!m1.mutationType.isWildtype()) ? m1 : m2;
					final Mutation wildtype = (actualMutant == m1) ? m2 : m1;

					final CandidateSequence mutantCandidate;
					if (actualMutant == m1) {
						mutantCandidate = top.getKey();
					} else {
						mutantCandidate = bottom.getKey();
					}
					
					//Use candidate concurring reads, and get a
					//majority vote of whether they have a positive or
					//negative alignment (if everything is as expected there
					//should be a perfect consensus with respect to alignment)
					final Set<ExtendedSAMRecord> concurringReads = mutantCandidate.
							getNonMutableConcurringReads().keySet();
					int pos1 = 0, pos2 = 0;
					float total1 = 0, total2 = 0;
					for (ExtendedSAMRecord er: concurringReads) {
						if (!selectedTopStrandRecords.contains(er) && 
							!selectedBottomStrandRecords.contains(er)) {
							continue;
						}
						if (er.record.getSecondOfPairFlag()) {
							total2++;
							if (er.record.getReadNegativeStrandFlag()) {
								pos2++;
							}											
						} else {
							total1++;
							if (er.record.getReadNegativeStrandFlag()) {
								pos1++;
							}
						}
					}
					//Both total1 and total2 might be >0 in case the consensus disagreement
					//is also found at a low frequency in the strand with wildtype consensus
					final boolean negativeStrand = !(total1 > total2 ? pos1 / total1 >= 0.5 :
						pos2 / total2 >= 0.5);
					
					if (analyzer.codingStrandTester != null) {
						Optional<Boolean> negativeCodingStrand = 
								analyzer.codingStrandTester.getNegativeStrand(location);
						actualMutant.setTemplateStrand(negativeCodingStrand.map(
								b ->  b == negativeStrand ? false : true));
						/*if (negativeCodingStrand != null) {
							if (negativeCodingStrand.booleanValue() == negativeStrand) {
								actualMutant.templateStrand = false;
							} else {
								actualMutant.templateStrand = true;
							}
						}*/
					}
					
					if (total1 > 0 && total2 > 0) {
						if (!(pos1 / total1 >= 0.5 ^ pos2 / total2 >= 0.5)) {
							//This could happen in case of a read being erroneously grouped in current duplex?
							stats.disagreementMatesSameOrientation.increment(location);
						}
					}
					stats.disagreementOrientationProportions1.insert((int) (10 * pos1 / total1));
					stats.disagreementOrientationProportions2.insert((int) (10 * pos2 / total2));
					
					final boolean hasDeletion;
					final boolean hasInsertion;
					final boolean hasSubstitution;
					final int nKinds;
					final Mutation simplifiedMutation;
					
					if (actualMutant.mutationType != SUBSTITUTION) {
						//This case is relatively infrequent, so separate it
						//But it would simpler not to have a special case for substitutions
						
						hasDeletion = mutantCandidate.containsMutationType(DELETION);
						hasInsertion = mutantCandidate.containsMutationType(INSERTION);
						hasSubstitution = mutantCandidate.containsMutationType(SUBSTITUTION);								
						nKinds = (hasDeletion ? 1 : 0) + (hasInsertion ? 1 : 0) + (hasSubstitution ? 1 : 0);

						if (nKinds == 1) {
							if (hasDeletion) {
								simplifiedMutation = new Mutation(mutantCandidate.getUniqueType(DELETION));
							} else if (hasInsertion) {
								simplifiedMutation = new Mutation(mutantCandidate.getUniqueType(INSERTION));
							} else if (hasSubstitution) {
								simplifiedMutation = new Mutation(mutantCandidate.getUniqueType(SUBSTITUTION));
							} else {
								throw new AssertionFailedException();
							}
						} else {
							simplifiedMutation = null;
						}
						
						stats.nLociDuplexWithTopBottomDuplexDisagreementNotASub.accept(location);
						if (nKinds > 1) {
							stats.nComplexDisagreementsQ2.increment(location);
						} else if (nKinds == 1) {
							simplifiedMutation.setTemplateStrand(actualMutant.getTemplateStrand());
							ComparablePair<Mutation, Mutation> mutationPair = negativeStrand ? 
									new ComparablePair<>(wildtype.reverseComplement(), simplifiedMutation.reverseComplement()) :
										new ComparablePair<>(wildtype, simplifiedMutation);
									if (getDistanceToLigSite() > analyzer.ignoreFirstNBasesQ2) {
										duplexDisagreements.add(mutationPair);
									}
						} else {
							throw new AssertionFailedException();
						}
					} else {
						nKinds = 1;
						hasSubstitution = true;
						hasDeletion = false;
						hasInsertion = false;
						simplifiedMutation = actualMutant;
						ComparablePair<Mutation, Mutation> mutationPair = negativeStrand ? 
								new ComparablePair<>(wildtype.reverseComplement(), actualMutant.reverseComplement()) :
								new ComparablePair<>(wildtype, actualMutant);
						if (getDistanceToLigSite() > analyzer.ignoreFirstNBasesQ2) {//TODO Check
							//redundant with what was done earlier
							duplexDisagreements.add(mutationPair);
						}
					}
					
					final SettableInteger minDist = new SettableInteger(99999);
					final Handle<ExtendedSAMRecord> minDistanceRead = new Handle<>();
					for (CandidateSequence candidate: candidateSet) {
						if (!candidate.getMutationType().equals(actualMutant.mutationType)) {
							continue;
						}
						candidate.getNonMutableConcurringReads().forEachEntry(
							(read, dist) -> {
								if (selectedBottomStrandRecords.contains(read) ||
										selectedTopStrandRecords.contains(read)) {
									if (dist == Integer.MAX_VALUE || dist == Integer.MIN_VALUE) {
										throw new AssertionFailedException(dist + " distance for mutation " +
												actualMutant + " read " + read + " file " + analyzer.inputBam.getAbsolutePath());
									}
									if (dist < minDist.get()) {
										minDist.set(dist);
										minDistanceRead.set(read);
									}
								}
								return true;
							}
						);
						break;
					}
						
					if (minDist.get() == 99999) {
						//This means that no reads are left that support the mutation candidate
						//This can happen in case of low Phred qualities that leads to the
						//reads being discarded
					} else if (minDist.get() < 0) {
						logger.warn("Min dist = " + minDist.get() +
								" at " + location + " " + selectedTopStrandRecords.
								iterator().next() + " VS " + selectedBottomStrandRecords.
								iterator().next() + " " + analyzer.inputBam.getAbsolutePath());
					} else if (minDist.get() > analyzer.ignoreFirstNBasesQ2) {
						//Why not just use getDistanceToLigSite() <= analyzer.ignoreFirstNBasesQ2 ?
						if (nKinds == 1) {
							if (hasSubstitution) {
								stats.substDisagDistanceToLigationSite.insert(minDist.get());
							} else if (hasDeletion) {
								stats.delDisagDistanceToLigationSite.insert(minDist.get());
								stats.disagDelSize.insert(simplifiedMutation.mutationSequence.length);
							} else if (hasInsertion) {
								stats.insDisagDistanceToLigationSite.insert(minDist.get());
							} else {
								//Too close to ligation site; could collect statistics here
							}
						}
					}//End distance to ligation site cases
				}//End case with one wildtype candidate
			}//End highEnoughQualForDisagreement
		}//End candidate for disagreement

		localQuality = dq;
		
		//Now remove support given to non-consensus candidate mutations by this duplex
		for (CandidateSequence candidate: candidateSet) {
			if (!disagreement && (bottom == null || candidate.equals(bottom.getKey())) &&
					(top == null || candidate.equals(top.getKey())))
				continue;

			@NonNull TObjectIntHashMap<ExtendedSAMRecord> reads = 
					candidate.getMutableConcurringReads();
			for (ExtendedSAMRecord r: selectedBottomStrandRecords) {
				reads.remove(r);
			}
			for (ExtendedSAMRecord r: selectedTopStrandRecords) {
				reads.remove(r);
			}
		}
		
		//Used to report global stats on duplex (including all locations), not
		//to compute quality of candidates at this location
		maxQuality = max(maxQuality, Objects.requireNonNull(dq.getMin()));
		minQuality = min(minQuality, Objects.requireNonNull(dq.getMin()));
		
		if (NONTRIVIAL_ASSERTIONS && !duplexDisagreements.isEmpty() &&
				dq.getMinIgnoring(assaysToIgnoreForDisagreementQuality).compareTo(GOOD) < 0) {
			throw new AssertionFailedException(dq.getQualities().toString());
		}
		
		if (!duplexDisagreements.isEmpty()) {
			result.disagreements.addAll(duplexDisagreements);
		}
	}
	
}
