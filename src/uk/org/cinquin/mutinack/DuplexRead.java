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
import static uk.org.cinquin.mutinack.Assay.*;
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
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.misc_util.Util.shortLengthFloatFormatter;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.stanford.nlp.util.HasInterval;
import contrib.edu.stanford.nlp.util.Interval;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.hash.TObjectIntHashMap;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateCounter;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * Equality and hashcode ignore list of reads assigned to duplex, quality, and roughLocation,
 * among other things.
 * @author olivier
 *
 */
public final class DuplexRead implements HasInterval<Integer> {
	
	private static final Logger logger = LoggerFactory.getLogger(DuplexRead.class);
	
	private final MutinackGroup groupSettings;
	public byte @NonNull[] leftBarcode, rightBarcode;
	public SequenceLocation leftAlignmentStart, rightAlignmentStart, leftAlignmentEnd, rightAlignmentEnd;
	public final @NonNull List<@NonNull ExtendedSAMRecord> topStrandRecords = new ArrayList<>(100), 
			bottomStrandRecords = new ArrayList<>(100);
	public int totalNRecords = -1;
	public final @NonNull List<String> issues = new ArrayList<>(10);
	private @Nullable Interval<Integer> interval;
	//Only used for debugging
	boolean invalid = false;
	public int nReadsWrongPair = 0;
	public int maxInsertSize;
	
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
	
	public DuplexRead(MutinackGroup groupSettings, byte @NonNull[] leftBarcode, byte @NonNull[] rightBarcode,
			boolean leftBarcodeNegativeStrand, boolean rightBarcodeNegativeStrand) {
		this.groupSettings = groupSettings;
		this.leftBarcode = leftBarcode;
		this.rightBarcode = rightBarcode;
		this.leftBarcodeNegativeStrand = leftBarcodeNegativeStrand;
		this.rightBarcodeNegativeStrand = rightBarcodeNegativeStrand;
	}
	
	void assertAllBarcodesEqual() {
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS) {
			final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords =
					new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
			allDuplexRecords.addAll(topStrandRecords);
			allDuplexRecords.addAll(bottomStrandRecords);
			allDuplexRecords.stream().forEach(r -> {
				if (!r.duplexLeft()) {
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
	/**
	 * 
	 * @param allReadsSameBarcode
	 * @param barcodeLength
	 * @return True if the barcodes have changed
	 */
	boolean computeConsensus(boolean allReadsSameBarcode, int barcodeLength) {
		final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords = 
				new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
		allDuplexRecords.addAll(topStrandRecords);
		allDuplexRecords.addAll(bottomStrandRecords);
		totalNRecords = allDuplexRecords.size();
		final byte @NonNull[] newLeft, newRight;
		if (allReadsSameBarcode) {
			newLeft = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					findAny().map(r -> r.variableBarcode).orElse(groupSettings.getNs()));
			newRight = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					findAny().map(r -> r.variableBarcode).orElse(groupSettings.getNs()));
		} else {
			newLeft = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					collect(Collectors.toList()), barcodeLength);
			newRight = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					collect(Collectors.toList()), barcodeLength);
		}
		
		//OK to do identity checks because of interning
		final boolean changed = (newLeft != leftBarcode) || (newRight != rightBarcode);
		leftBarcode = newLeft;
		rightBarcode = newRight;
		return changed;
	}
	
	private void resetMaxDistanceToLigSite() {
		maxDistanceToLig = Integer.MIN_VALUE;
	}
	
	private void acceptDistanceToLigSite(int d) {
		if (d > maxDistanceToLig) {
			maxDistanceToLig = d;
		}
	}
	
	int getDistanceToLigSite() {
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
			new String(rightBarcode) + ", " + " Q" + minQuality +
			(localQuality == null ? "" : (" " + localQuality.getQualities().entrySet().stream().
				min((e1, e2) -> e1.getValue().compareTo(e2.getValue())).map(Entry::getKey).map(
					Enum::toString).orElse("") + " ")) +
			"->" + maxQuality + topStrandRecords.toString() + " " +
			bottomStrandRecords.toString();
	}

	@Override
	public Interval<Integer> getInterval() {
		if (interval == null) {
			interval = Interval.toInterval(leftAlignmentStart != null ? (leftAlignmentStart.position - groupSettings.INTERVAL_SLOP) : 0,
					leftAlignmentEnd != null ? leftAlignmentEnd.position + groupSettings.INTERVAL_SLOP : Integer.MAX_VALUE);
			Assert.isNonNull(interval);
		}
		return interval;
	}
	
	private static void registerMismatches(@NonNull SequenceLocation location, int nMismatches,
			byte @NonNull [] barcode1, byte @NonNull [] barcode2, boolean negativeStrand1,
			boolean negativeStrand2, AnalysisStats stats) {
		if (nMismatches ==0) {
			return;
		}
		for (int i = 0; i < barcode1.length; i++) {
			if (barcode1[i] != barcode2[i]) {
				byte first = negativeStrand1 ? Mutation.complement(barcode1[i]) :
					barcode1[i];
				byte second = negativeStrand2 ? Mutation.complement(barcode2[i]) :
					barcode2[i];
				
				final ComparablePair<String, String> p = first < second ?
					new ComparablePair<>(SubAnalyzer.byteMap.get(first),
						SubAnalyzer.byteMap.get(second)) :
					new ComparablePair<>(SubAnalyzer.byteMap.get(second),
						SubAnalyzer.byteMap.get(first));
				if (nMismatches == 1) {
					stats.vBarcodeMismatches1M.accept(location, p);
				} else if (nMismatches == 2) {
					stats.vBarcodeMismatches2M.accept(location, p);
				} else {
					stats.vBarcodeMismatches3OrMore.accept(location, p);
				}
			}
		}
	}
	
	/**
	 * 
	 * @param mergeInto; will receive the reads associated with duplex1
	 * @param otherDuplex; will be marked invalid
	 */
	private static void mergeDuplexes(DuplexRead mergeInto, DuplexRead otherDuplex) {
		Assert.isFalse(mergeInto.invalid);
		Assert.isFalse(otherDuplex.invalid);
		mergeInto.bottomStrandRecords.addAll(otherDuplex.bottomStrandRecords);
		mergeInto.topStrandRecords.addAll(otherDuplex.topStrandRecords);

		for (ExtendedSAMRecord rec: otherDuplex.bottomStrandRecords) {
			rec.duplexRead = mergeInto;
		}
		for (ExtendedSAMRecord rec: otherDuplex.topStrandRecords) {
			rec.duplexRead = mergeInto;
		}
		
		otherDuplex.invalid = true;
	}
	
	private static final Comparator<DuplexRead> duplexCountQualComparator = 
		(d1, d2) -> {
			int compResult;
			compResult = Integer.compare(d2.totalNRecords, d1.totalNRecords);
			if (compResult != 0) {
				return compResult;
			}
			//TODO The qualities have not yet been computed by the time the
			//comparison below is made, so at this point it is ineffective
			compResult = d2.minQuality.compareTo(d1.minQuality);
			if (compResult != 0) {
				return compResult;
			}
			compResult = Integer.compare(Math.min(d2.topStrandRecords.size(), 
				d2.bottomStrandRecords.size()), Math.min(d1.topStrandRecords.size(), 
					d1.bottomStrandRecords.size()));
			if (compResult != 0) {
				return compResult;
			}
			return 0;
		};
		
	
	static DuplexKeeper groupDuplexes(
			DuplexKeeper duplexes,
			Consumer<DuplexRead> preliminaryOp,
			Supplier<DuplexKeeper> factory, Mutinack analyzer, AnalysisStats stats,
			int callDepth) {
		
		Handle<DuplexKeeper> result = new Handle<>(factory.get());
		
		StreamSupport.stream(duplexes.getIterable().spliterator(), false).
				sorted(duplexCountQualComparator).forEach(duplex1 -> {
			
			preliminaryOp.accept(duplex1);
			
			boolean mergedDuplex = false;

			Assert.isNonNull(duplex1.getInterval());

			List<DuplexRead> overlapping = new ArrayList<>(30);
			for (DuplexRead dr: result.get().getOverlapping(duplex1)) {
				overlapping.add(dr);
			}
			Collections.sort(overlapping, duplexCountQualComparator);

			for (DuplexRead duplex2: overlapping) {
				
				preliminaryOp.accept(duplex2);
				
				final int distance1 = duplex1.leftAlignmentStart.position - duplex2.leftAlignmentStart.position;
				final int distance2 = analyzer.requireMatchInAlignmentEnd && duplex1.leftAlignmentEnd != null && duplex2.leftAlignmentEnd != null ?
						duplex1.leftAlignmentEnd.position - duplex2.leftAlignmentEnd.position : 0;
				final int distance3 = duplex1.rightAlignmentEnd.position - duplex2.rightAlignmentEnd.position;
				final int distance4 = analyzer.requireMatchInAlignmentEnd && duplex1.rightAlignmentStart != null && duplex2.rightAlignmentStart != null ?
						duplex1.rightAlignmentStart.position - duplex2.rightAlignmentStart.position : 0;

				if (	duplex1.distanceTo(duplex2) <= analyzer.alignmentPositionMismatchAllowed &&
						Math.abs(distance1) <= analyzer.alignmentPositionMismatchAllowed &&
						Math.abs(distance2) <= analyzer.alignmentPositionMismatchAllowed &&
						Math.abs(distance3) <= analyzer.alignmentPositionMismatchAllowed &&
						Math.abs(distance4) <= analyzer.alignmentPositionMismatchAllowed) {

					final int leftMismatches = Util.nMismatches(duplex1.leftBarcode, duplex2.leftBarcode, true);
					final int rightMismatches = Util.nMismatches(duplex1.rightBarcode, duplex2.rightBarcode, true);

					if (leftMismatches > 0) {
						registerMismatches(Objects.requireNonNull(duplex1.roughLocation), leftMismatches,
								duplex1.leftBarcode, duplex2.leftBarcode,
								duplex1.leftBarcodeNegativeStrand, duplex2.leftBarcodeNegativeStrand,
								stats);
					}
					if (rightMismatches > 0) {
						registerMismatches(Objects.requireNonNull(duplex1.roughLocation), rightMismatches,
								duplex1.rightBarcode, duplex2.rightBarcode,
								duplex1.rightBarcodeNegativeStrand, duplex2.rightBarcodeNegativeStrand,
								stats);
					}

					if (leftMismatches <= analyzer.nVariableBarcodeMismatchesAllowed &&
							rightMismatches <= analyzer.nVariableBarcodeMismatchesAllowed) {

						mergeDuplexes(duplex2, duplex1);

						boolean changed = 
								duplex2.computeConsensus(false, analyzer.variableBarcodeLength);
						
						if (changed) {
							result.set(groupDuplexes(result.get(), d -> {},
								factory, analyzer, stats, callDepth + 1));
						} else {
							stats.duplexGroupingDepth.insert(callDepth);
						}

						mergedDuplex = true;
						break;
					}//End merge duplexes
				}//End duplex alignment distance test
			}//End loop over overlapping duplexes

			if (!mergedDuplex) {
				result.get().add(duplex1);
			}
		});//End duplex grouping
		
		DuplexKeeper keeper = result.get();
		if (DebugLogControl.COSTLY_ASSERTIONS) {
			checkNoEqualDuplexes(keeper.getIterable());
		}
		
		return result.get();
	}
	
	static Pair<DuplexRead, DuplexRead> checkNoEqualDuplexes(Iterable<DuplexRead> it) {
		for (DuplexRead r: it) {
			for (DuplexRead r2: it) {
				if (r != r2 && r.equals(r2)) {
					//return new Pair<>(r, r2);
					throw new AssertionFailedException("Duplexes " + r + " and " + r2 +
						" are equal");
				}
			}
		}
		return null;
	}

	private static final Set<Assay> ignorePhred = 
			Collections.singleton(N_STRAND_READS_ABOVE_MIN_PHRED);
	
	@SuppressWarnings("null")
	public void examineAtLoc(@NonNull SequenceLocation location,
			LocationExaminationResults result,
			@NonNull Set<@NonNull CandidateSequence> candidateSet,
			@NonNull Set<@NonNull Assay> assaysToIgnoreForDisagreementQuality,
			boolean hasHiddenCandidate,
			@NonNull CandidateCounter topCounter,
			@NonNull CandidateCounter bottomCounter,
			Mutinack analyzer, AnalysisStats stats) {
		
		topCounter.reset();
		bottomCounter.reset();
		topCounter.minBasePhredScore = 0;
		bottomCounter.minBasePhredScore = 0;
		resetMaxDistanceToLigSite();
		final @NonNull List<@NonNull DuplexDisagreement> duplexDisagreements = 
				new ArrayList<>();
		stats.nPosDuplex.accept(location);
		final Entry<CandidateSequence, SettableInteger> bottom, top;
		boolean disagreement = false;

		//Find if there is a clear candidate with which duplexRead is
		//associated; if not, discard it
		//The same reads can be associated with two or three candidates in case
		//there is an insertion or deletion (since the wildtype base or a
		//substitution might be present at the same position).
		//Therefore we count the number of unique records associated with
		//this position using Sets.
		
		topCounter.setRecords(topStrandRecords);
		topCounter.compute();
		
		bottomCounter.setRecords(bottomStrandRecords);
		bottomCounter.compute();
		
		{//Make sure we do not leak bottom0 or top0
			final Entry<CandidateSequence, SettableInteger> bottom0 = bottomCounter.candidateCounts.entrySet().stream().
					max((a,b) -> Integer.compare(a.getValue().get(), b.getValue().get())).
					orElse(null);
			bottom = bottom0 == null ? null :
				new AbstractMap.SimpleImmutableEntry<>(bottom0.getKey(), bottom0.getValue());

			final Entry<CandidateSequence, SettableInteger> top0 = topCounter.candidateCounts.entrySet().stream().
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
			dq.addUnique(N_READS_PER_STRAND, GOOD);
		} else if (nBottomStrandsWithCandidate >= analyzer.minReadsPerStrandQ1 &&
				nTopStrandsWithCandidate >= analyzer.minReadsPerStrandQ1) {
			dq.addUnique(N_READS_PER_STRAND, DUBIOUS);
			stats.nPosDuplexTooFewReadsPerStrand2.increment(location);
			result.strandCoverageImbalance = Math.max(result.strandCoverageImbalance,
					Math.abs(bottomStrandRecords.size() - topStrandRecords.size()));
			if (analyzer.logReadIssuesInOutputBam) {
				if (nBottomStrandsWithCandidate < analyzer.minReadsPerStrandQ2)
					issues.add(location + "TFR1B");
				if (nTopStrandsWithCandidate < analyzer.minReadsPerStrandQ2)
					issues.add(location + "TFR1T");
			}
		} else {
			dq.addUnique(N_READS_PER_STRAND, POOR);
			stats.nPosDuplexTooFewReadsPerStrand1.increment(location);
			if (bottomStrandRecords.isEmpty() || topStrandRecords.isEmpty()) {
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
			//Phred quality scores that do not meet Q2 threshold
			
			topCounter.minBasePhredScore = analyzer.minBasePhredScoreQ2;
			topCounter.compute();
			bottomCounter.minBasePhredScore = analyzer.minBasePhredScoreQ2;
			bottomCounter.compute();
			if (topCounter.keptRecords.size() < analyzer.minReadsPerStrandQ2 ||
					bottomCounter.keptRecords.size() < analyzer.minReadsPerStrandQ2) {
				dq.addUnique(N_STRAND_READS_ABOVE_MIN_PHRED, DUBIOUS);
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
			stats.nConsensusQ1NotMet.increment(location);
			if (analyzer.logReadIssuesInOutputBam) {
				issues.add(location + " CS0Y_" + (top != null ? top.getValue().get() : "x") +
						"_" + nTopStrandsWithCandidate + "_" +
						(bottom != null ? bottom.getValue().get() : "x") + 
						"_" + nBottomStrandsWithCandidate);
			}
		}

		//TODO compute consensus insert size instead of extremes
		final IntSummaryStatistics insertSizeStats = Stream.concat(bottomStrandRecords.stream(), topStrandRecords.stream()).
				mapToInt(r -> Math.abs(r.getInsertSize())).summaryStatistics();

		maxInsertSize = insertSizeStats.getMax();
		final int localMinInsertSize = insertSizeStats.getMin();
		
		if (maxInsertSize == 0) {
			dq.addUnique(INSERT_SIZE, POOR);
		} else if (maxInsertSize < analyzer.minInsertSize || localMinInsertSize > analyzer.maxInsertSize) {
			dq.addUnique(INSERT_SIZE, DUBIOUS);
		}
		
		Assert.isFalse(invalid);
		
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
				stats.nPosDuplexWithLackOfStrandConsensus2.increment(location);
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
				stats.nPosDuplexWithLackOfStrandConsensus1.increment(location);
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

		final boolean enoughReadsForQ2Disag = bottom != null && 
				bottom.getValue().get() >= analyzer.minReadsPerStrandForDisagreement
				&&
				top != null &&
				top.getValue().get() >= analyzer.minReadsPerStrandForDisagreement;

		final boolean highEnoughQualForQ2Disagreement =
			dq.getMin().compareTo(GOOD) >= 0 &&
			enoughReadsForQ2Disag &&
			!hasHiddenCandidate;
		
		if (bothStrandsPresent && highEnoughQualForQ2Disagreement) {
			stats.nPosDuplexCandidatesForDisagreementQ2.accept(location);
		}

		if (bothStrandsPresent &&
				(!Arrays.equals(bottom.getKey().getSequence(), top.getKey().getSequence()) ||
						!bottom.getKey().getMutationType().equals(top.getKey().getMutationType()))) {

			dq.addUnique(DISAGREEMENT, ATROCIOUS);
			issues.add(location + " DSG");
			disagreement = true;

			if (highEnoughQualForQ2Disagreement) {
				final Mutation m1 = new Mutation(top.getKey());
				final Mutation m2 = new Mutation(bottom.getKey());

				if (!m1.mutationType.isWildtype() && !m2.mutationType.isWildtype()) {
					stats.nPosDuplexWithTopBottomDuplexDisagreementNoWT.accept(location);
					if (getDistanceToLigSite() > analyzer.ignoreFirstNBasesQ2) {
						DuplexDisagreement disag = new DuplexDisagreement(m1, m2, false);
						duplexDisagreements.add(disag);
					}
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
						if (!topStrandRecords.contains(er) && 
							!bottomStrandRecords.contains(er)) {
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

					if (actualMutant.mutationType != SUBSTITUTION) {
						//This case is relatively infrequent, so separate it
						//But it would simpler not to have a special case for substitutions
						
						hasDeletion = mutantCandidate.getMutationType() == DELETION;
						hasInsertion = mutantCandidate.getMutationType() == INSERTION;
						hasSubstitution = mutantCandidate.getMutationType() == SUBSTITUTION;								
						
						stats.nPosDuplexWithTopBottomDuplexDisagreementNotASub.accept(location);

						actualMutant.setTemplateStrand(actualMutant.isTemplateStrand());
						if (getDistanceToLigSite() > analyzer.ignoreFirstNBasesQ2) {
							DuplexDisagreement disag = negativeStrand ? 
								new DuplexDisagreement(wildtype.reverseComplement(), actualMutant.reverseComplement(), true) :
								new DuplexDisagreement(wildtype, actualMutant, true);
							duplexDisagreements.add(disag);
						}
					} else {
						hasSubstitution = true;
						hasDeletion = false;
						hasInsertion = false;
						if (getDistanceToLigSite() > analyzer.ignoreFirstNBasesQ2) {//TODO Check
							//redundant with what was done earlier
							DuplexDisagreement disag = negativeStrand ? 
								new DuplexDisagreement(wildtype.reverseComplement(), actualMutant.reverseComplement(), true) :
								new DuplexDisagreement(wildtype, actualMutant, true);
							duplexDisagreements.add(disag);
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
								if (bottomStrandRecords.contains(read) ||
										topStrandRecords.contains(read)) {
									Assert.isFalse(dist == Integer.MAX_VALUE || dist == Integer.MIN_VALUE,
											"%s distance for mutation %s read %s file %s" ,
												dist, actualMutant, read, analyzer.inputBam.getAbsolutePath());
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
						//This can happen in case of low Phred qualities that lead to the
						//reads being discarded
					} else if (minDist.get() < 0) {
						logger.warn("Min dist = " + minDist.get() +
								" at " + location + " " + topStrandRecords.
								iterator().next() + " VS " + bottomStrandRecords.
								iterator().next() + " " + analyzer.inputBam.getAbsolutePath());
					} else if (minDist.get() > analyzer.ignoreFirstNBasesQ2) {
						//Why not just use getDistanceToLigSite() <= analyzer.ignoreFirstNBasesQ2 ?
						if (hasSubstitution) {
							stats.substDisagDistanceToLigationSite.insert(minDist.get());
						} else if (hasDeletion) {
							stats.delDisagDistanceToLigationSite.insert(minDist.get());
							stats.disagDelSize.insert(actualMutant.mutationSequence.length);
						} else if (hasInsertion) {
							stats.insDisagDistanceToLigationSite.insert(minDist.get());
						} else {
							//Too close to ligation site; could collect statistics here
						}
					}//End distance to ligation site cases
				}//End case with one wildtype candidate
			}//End highEnoughQualForQ2Disagreement
		}//End candidate for disagreement

		localQuality = dq;
		
		int nRemoved = 0;

		//Now remove support given to non-consensus candidate mutations by this duplex
		for (CandidateSequence candidate: candidateSet) {
			Assert.isFalse(top == null && bottom == null);
			if (!disagreement && (bottom == null || candidate.equals(bottom.getKey())) &&
					(top == null || candidate.equals(top.getKey()))) {
				continue;
			}
			
			@NonNull TObjectIntHashMap<ExtendedSAMRecord> reads = candidate.getMutableConcurringReads();
			
			final int noEntryValue = reads.getNoEntryValue();
			
			for (ExtendedSAMRecord r: bottomStrandRecords) {
				if (reads.remove(r) != noEntryValue) {
					nRemoved++;
				}
				Assert.isFalse(reads.contains(r));
				Assert.isFalse(candidate.getNonMutableConcurringReads().containsKey(r));
			}
			for (ExtendedSAMRecord r: topStrandRecords) {
				if (reads.remove(r) != noEntryValue) {
					nRemoved++;
				}
				Assert.isFalse(reads.contains(r));
				Assert.isFalse(candidate.getNonMutableConcurringReads().containsKey(r));
			}

			if (disagreement && nRemoved > 0) {
				//Mark presence of at least one duplex with disagreement with DISAGREEMENT Assay,
				//but only set corresponding quality to "DUBIOUS" if the disagreement is of sufficiently high
				//quality, so that a single low-quality duplex cannot force downgrading of mutation
				//(which may be undesirable if other high-quality duplexes support presence of the mutation).
				candidate.getQuality().add(DISAGREEMENT, highEnoughQualForQ2Disagreement ? DUBIOUS : GOOD);
				if (!enoughReadsForQ2Disag) {
					candidate.getQuality().add(N_STRANDS_DISAGREEMENT, GOOD);
				}
			}
		}
				
		if (dq.getMinIgnoring(ignorePhred).compareTo(GOOD) >= 0) {
			long totalNPhreds = topCounter.nPhreds + bottomCounter.nPhreds;
			if (totalNPhreds > 0) {
				long sumPhreds = topCounter.sumPhreds + bottomCounter.sumPhreds;
				stats.phredAndLigSiteDistance.accept(location, 
					new ComparablePair<>(distanceToLigSite,
						(int) (sumPhreds / totalNPhreds)));
			}
		}
		
		//Used to report global stats on duplex (including all locations), not
		//to compute quality of candidates at this location
		maxQuality = max(maxQuality, Objects.requireNonNull(dq.getMin()));
		minQuality = min(minQuality, Objects.requireNonNull(dq.getMin()));
		
		Assert.isFalse(!duplexDisagreements.isEmpty() &&
				dq.getMinIgnoring(assaysToIgnoreForDisagreementQuality).compareTo(GOOD) < 0,
			() -> dq.getQualities().toString());
		
		if (!duplexDisagreements.isEmpty()) {
			result.disagreements.addAll(duplexDisagreements);
		}
	}
}
