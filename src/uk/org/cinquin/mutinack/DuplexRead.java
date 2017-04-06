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
import static java.util.Objects.requireNonNull;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.AVERAGE_N_CLIPPED;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.BOTTOM_STRAND_MAP_Q2;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.CLOSE_TO_LIG;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.CONSENSUS_Q0;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.CONSENSUS_Q1;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.CONSENSUS_THRESHOLDS_1;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.DISAGREEMENT;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.INSERT_SIZE;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.MISSING_STRAND;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.N_READS_PER_STRAND;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.N_READS_WRONG_PAIR;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.N_STRAND_READS_ABOVE_Q2_PHRED;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.TOP_STRAND_MAP_Q2;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.TOTAL_N_READS_Q2;
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.misc_util.Util.shortLengthFloatFormatter;
import static uk.org.cinquin.mutinack.qualities.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.qualities.Quality.DUBIOUS;
import static uk.org.cinquin.mutinack.qualities.Quality.GOOD;
import static uk.org.cinquin.mutinack.qualities.Quality.MAXIMUM;
import static uk.org.cinquin.mutinack.qualities.Quality.MINIMUM;
import static uk.org.cinquin.mutinack.qualities.Quality.POOR;
import static uk.org.cinquin.mutinack.qualities.Quality.max;
import static uk.org.cinquin.mutinack.qualities.Quality.min;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.stanford.nlp.util.HasInterval;
import contrib.edu.stanford.nlp.util.Interval;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.TObjectIntMap;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateCounter;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateDuplexEval;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.IntMinMax;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.LocationExaminationResults;
import uk.org.cinquin.mutinack.qualities.DetailedDuplexQualities;
import uk.org.cinquin.mutinack.qualities.DetailedQualities;
import uk.org.cinquin.mutinack.qualities.Quality;
import uk.org.cinquin.mutinack.statistics.Histogram;

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
	boolean invalid = false;//Only used for debugging
	public int nReadsWrongPair = 0;
	public int maxInsertSize = -1;
	private int minInsertSize = -1;
	public double probAtLeastOneCollision = -1;
	public boolean missingStrand = false;
	float minFracTopCandidate = Float.MAX_VALUE;
	private Boolean topStrandIsNegative;//Use Boolean just to make sure an NPE is generated
	//if property is accessed before it has been computed

	/**
	 * Quality factoring in number of reads for top or bottom strand, percent consensus for
	 * reads from a given strand. Minimum and maximum across all base positions in duplex.
	*/
	@NonNull Quality minQuality = MAXIMUM, maxQuality = MINIMUM;
	DetailedQualities<DuplexAssay> localAndGlobalQuality;
	final @NonNull DetailedQualities<DuplexAssay> globalQuality = new DetailedDuplexQualities();
	SequenceLocation roughLocation;
	float referenceDisagreementRate;
	int averageNClipped = -1;
	int position0;
	private int position3;
	private int maxDistanceToLig = Integer.MIN_VALUE;
	private final boolean leftBarcodeNegativeStrand;
	private final boolean rightBarcodeNegativeStrand;

	public DuplexRead(MutinackGroup groupSettings, byte @NonNull [] leftBarcode, byte @NonNull [] rightBarcode,
					  boolean leftBarcodeNegativeStrand, boolean rightBarcodeNegativeStrand) {
		this.groupSettings = groupSettings;
		this.leftBarcode = leftBarcode;
		this.rightBarcode = rightBarcode;
		this.leftBarcodeNegativeStrand = leftBarcodeNegativeStrand;
		this.rightBarcodeNegativeStrand = rightBarcodeNegativeStrand;
	}

	void assertAllBarcodesEqual() {
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS) {
			final Collection<ExtendedSAMRecord> allDuplexRecords =
					new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
			allDuplexRecords.addAll(topStrandRecords);
			allDuplexRecords.addAll(bottomStrandRecords);
			allDuplexRecords.forEach(r -> {
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

	/**
	 *
	 * @param allReadsSameBarcode
	 * @param barcodeLength
	 * @return True if the barcodes have changed
	 */
	boolean computeConsensus(boolean allReadsSameBarcode, int barcodeLength) {
		final Collection<@NonNull ExtendedSAMRecord> allDuplexRecords =
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
					map(r -> r.variableBarcode), barcodeLength);
			newRight = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					map(r -> r.variableBarcode), barcodeLength);
		}

		computeGlobalProperties();

		//OK to do identity checks because of interning
		final boolean changed = (newLeft != leftBarcode) || (newRight != rightBarcode);
		leftBarcode = newLeft;
		rightBarcode = newRight;
		return changed;
	}

	void computeGlobalProperties() {
		//TODO compute consensus insert size instead of extremes
		final IntMinMax<ExtendedSAMRecord> insertSizeStats = new IntMinMax<ExtendedSAMRecord>().
			acceptMinMax(bottomStrandRecords,
				er -> Math.abs(((ExtendedSAMRecord) er).getInsertSize())).
			acceptMinMax(topStrandRecords,
				er -> Math.abs(((ExtendedSAMRecord) er).getInsertSize()));

		maxInsertSize = insertSizeStats.getMax();
		minInsertSize = insertSizeStats.getMin();

		int topStrandIsPositiveVotes = 0,
			topStrandIsNegativeVotes = 0;

		for (ExtendedSAMRecord e: topStrandRecords) {
			if (e.record.getFirstOfPairFlag()) {
				if (e.getReadPositiveStrand()) {
					topStrandIsPositiveVotes++;
				} else {
					topStrandIsNegativeVotes++;
				}
			} else {//Second of pair
				if (e.getReadNegativeStrandFlag()) {
					topStrandIsPositiveVotes++;
				} else {
					topStrandIsNegativeVotes++;
				}
			}
		}

		for (ExtendedSAMRecord e: bottomStrandRecords) {
			if (!e.record.getFirstOfPairFlag()) {
				if (e.getReadPositiveStrand()) {
					topStrandIsPositiveVotes++;
				} else {
					topStrandIsNegativeVotes++;
				}
			} else {//First of pair
				if (e.getReadNegativeStrandFlag()) {
					topStrandIsPositiveVotes++;
				} else {
					topStrandIsNegativeVotes++;
				}
			}
		}

		topStrandIsNegative = topStrandIsNegativeVotes >
			topStrandIsPositiveVotes;
	}

	private void resetMaxDistanceToLigSite() {
		maxDistanceToLig = Integer.MIN_VALUE;
	}

	private void acceptDistanceToLigSite(int d) {
		if (d > maxDistanceToLig) {
			maxDistanceToLig = d;
		}
	}

	int getMaxDistanceToLigSite() {
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
			leftAlignmentEnd + ", " + rightAlignmentEnd + ", " + new String(leftBarcode) + '-' +
			new String(rightBarcode) + ", " + " Q" + minQuality +
			(localAndGlobalQuality == null ? "" : (' ' + localAndGlobalQuality.getQualities().
				min(Comparator.comparing(Entry::getValue)).map(Entry::getKey).map(
					Enum::toString).orElse("") + ' ')) +
			"->" + maxQuality + "; all records: " + topStrandRecords.toString() + ' ' +
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
		if (nMismatches == 0) {
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
			Supplier<DuplexKeeper> factory,
			Parameters param,
			AnalysisStats stats,
			int callDepth) {

		DuplexKeeper result = factory.get();

		final DuplexRead[] sorted = new DuplexRead[duplexes.size()];
		{
			SettableInteger index = new SettableInteger(0);
			duplexes.forEach(d -> sorted[index.getAndIncrement()] = d);
		}
		Arrays.parallelSort(sorted, duplexCountQualComparator);

		for (int index = 0; index < sorted.length; index++) {
			final DuplexRead duplex1 = sorted[index];

			preliminaryOp.accept(duplex1);

			boolean mergedDuplex = false;

			Assert.isNonNull(duplex1.getInterval());

			List<DuplexRead> overlapping = result.getOverlapping(duplex1);
			Collections.sort(overlapping, duplexCountQualComparator);

			for (DuplexRead duplex2: overlapping) {

				preliminaryOp.accept(duplex2);

				final int distance1 = duplex1.leftAlignmentStart.position - duplex2.leftAlignmentStart.position;
				final int distance2 = param.requireMatchInAlignmentEnd && duplex1.leftAlignmentEnd != null && duplex2.leftAlignmentEnd != null ?
						duplex1.leftAlignmentEnd.position - duplex2.leftAlignmentEnd.position : 0;
				final int distance3 = duplex1.rightAlignmentEnd.position - duplex2.rightAlignmentEnd.position;
				final int distance4 = param.requireMatchInAlignmentEnd && duplex1.rightAlignmentStart != null && duplex2.rightAlignmentStart != null ?
						duplex1.rightAlignmentStart.position - duplex2.rightAlignmentStart.position : 0;

				if (duplex1.distanceTo(duplex2) <= param.alignmentPositionMismatchAllowed &&
						Math.abs(distance1) <= param.alignmentPositionMismatchAllowed &&
						Math.abs(distance2) <= param.alignmentPositionMismatchAllowed &&
						Math.abs(distance3) <= param.alignmentPositionMismatchAllowed &&
						Math.abs(distance4) <= param.alignmentPositionMismatchAllowed) {

					final int leftMismatches = Util.nMismatches(duplex1.leftBarcode, duplex2.leftBarcode, true);
					final int rightMismatches = Util.nMismatches(duplex1.rightBarcode, duplex2.rightBarcode, true);

					if (leftMismatches > 0) {
						registerMismatches(requireNonNull(duplex1.roughLocation), leftMismatches,
								duplex1.leftBarcode, duplex2.leftBarcode,
								duplex1.leftBarcodeNegativeStrand, duplex2.leftBarcodeNegativeStrand,
								stats);
					}
					if (rightMismatches > 0) {
						registerMismatches(requireNonNull(duplex1.roughLocation), rightMismatches,
								duplex1.rightBarcode, duplex2.rightBarcode,
								duplex1.rightBarcodeNegativeStrand, duplex2.rightBarcodeNegativeStrand,
								stats);
					}

					if (leftMismatches <= param.nVariableBarcodeMismatchesAllowed &&
							rightMismatches <= param.nVariableBarcodeMismatchesAllowed) {

						mergeDuplexes(duplex2, duplex1);

						boolean changed =
								duplex2.computeConsensus(false, param.variableBarcodeLength);

						if (changed) {
							result = groupDuplexes(result, d -> {}, factory, param, stats, callDepth + 1);
						} else {
							stats.duplexGroupingDepth.insert(callDepth);
						}

						mergedDuplex = true;
						break;
					}//End merge duplexes
				}//End duplex alignment distance test
			}//End loop over overlapping duplexes

			if (!mergedDuplex) {
				result.add(duplex1);
			}
		}//End duplex grouping

		if (param.enableCostlyAssertions) {
			checkNoEqualDuplexes(result.getIterable());
		}

		return result;
	}

	@SuppressWarnings("ReferenceEquality")
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

	private static final Set<DuplexAssay> ignorePhred =
			Collections.singleton(N_STRAND_READS_ABOVE_Q2_PHRED);

	public void examineAtLoc(@NonNull SequenceLocation location,
			LocationExaminationResults result,
			@NonNull ImmutableSet<@NonNull CandidateSequence> candidateSet,
			@NonNull Set<DuplexAssay> assaysToIgnoreForDisagreementQuality,
			@NonNull CandidateCounter topCounter,
			@NonNull CandidateCounter bottomCounter,
			Mutinack analyzer,
			Parameters param,
			AnalysisStats stats) {

		if (result.threadCount.incrementAndGet() != 1) {
			throw new AssertionFailedException();
		}

		try {
			examineAtLoc1(location, result, candidateSet, assaysToIgnoreForDisagreementQuality,
				topCounter, bottomCounter, analyzer, param, stats);
		} finally {
			if (result.threadCount.decrementAndGet() != 0) {
				throw new AssertionFailedException();
			}
		}
	}

	@SuppressWarnings({"ReferenceEquality"})
	private void examineAtLoc1(@NonNull SequenceLocation location,
			LocationExaminationResults result,
			@NonNull ImmutableSet<@NonNull CandidateSequence> candidateSet,
			@NonNull Set<DuplexAssay> assaysToIgnoreForDisagreementQuality,
			@NonNull CandidateCounter topCounter,
			@NonNull CandidateCounter bottomCounter,
			Mutinack analyzer,
			Parameters param,
			AnalysisStats stats) {

		topCounter.reset();
		bottomCounter.reset();
		topCounter.minBasePhredScore = 0;
		bottomCounter.minBasePhredScore = 0;
		resetMaxDistanceToLigSite();
		stats.nPosDuplex.accept(location);
		final @Nullable CandidateDuplexEval bottom, top;

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

		IntMinMax<CandidateDuplexEval> ir1 = new IntMinMax<>();
		bottomCounter.candidateCounts.forEachValue(si -> {
			ir1.acceptMax(si.count, si);
		});
		bottom = ir1.getKeyMax();

		IntMinMax<CandidateDuplexEval> ir2 = new IntMinMax<>();
		topCounter.candidateCounts.forEachValue(si -> {
			ir2.acceptMax(si.count, si);
		});
		top = ir2.getKeyMax();

		final int nTopStrandsWithCandidate = topCounter.keptRecords.size();
		final int nBottomStrandsWithCandidate = bottomCounter.keptRecords.size();

		topCounter.reset();
		bottomCounter.reset();

		stats.copyNumberOfDuplexTopStrands.insert(nTopStrandsWithCandidate);
		stats.copyNumberOfDuplexBottomStrands.insert(nBottomStrandsWithCandidate);
		if (nTopStrandsWithCandidate > 0 && nBottomStrandsWithCandidate > 0) {
			stats.nPosDuplexBothStrandsPresent.accept(location);
		}

		final @NonNull DetailedQualities<DuplexAssay> dq = new DetailedDuplexQualities();
		globalQuality.forEach(dq::add);

		if (nBottomStrandsWithCandidate >= param.minReadsPerStrandQ2 &&
				nTopStrandsWithCandidate >= param.minReadsPerStrandQ2) {
			dq.addUnique(N_READS_PER_STRAND, GOOD);
		} else if (nBottomStrandsWithCandidate >= param.minReadsPerStrandQ1 &&
				nTopStrandsWithCandidate >= param.minReadsPerStrandQ1) {
			if (bottom != null && top != null) {
				dq.addUnique(N_READS_PER_STRAND, DUBIOUS);
			}
			stats.nPosDuplexTooFewReadsPerStrand2.accept(location);
			result.strandCoverageImbalance = Math.max(result.strandCoverageImbalance,
					Math.abs(bottomStrandRecords.size() - topStrandRecords.size()));
			if (param.logReadIssuesInOutputBam) {
				if (nBottomStrandsWithCandidate < param.minReadsPerStrandQ2)
					issues.add(location + "_TFR1B");
				if (nTopStrandsWithCandidate < param.minReadsPerStrandQ2)
					issues.add(location + "_TFR1T");
			}
		} else {
			dq.addUnique(N_READS_PER_STRAND, POOR);
			stats.nPosDuplexTooFewReadsPerStrand1.accept(location);
			if (bottomStrandRecords.isEmpty() || topStrandRecords.isEmpty()) {
				missingStrand = true;
				result.nMissingStrands++;
			}
			if (param.logReadIssuesInOutputBam) {
				if (nTopStrandsWithCandidate < param.minReadsPerStrandQ1)
					issues.add(location + "_TFR0B");
				if (nBottomStrandsWithCandidate < param.minReadsPerStrandQ1)
					issues.add(location + "_TFR0T");
			}
		}

		if (nBottomStrandsWithCandidate + nTopStrandsWithCandidate <
			param.minReadsPerDuplexQ2) {
				dq.addUnique(TOTAL_N_READS_Q2, DUBIOUS);
		}

		dq.addUnique(TOP_STRAND_MAP_Q2,
			new IntMinMax<ExtendedSAMRecord>().
				defaultMax(255).
				acceptMax(topCounter.keptRecords,
					er -> ((ExtendedSAMRecord) er).getMappingQuality()).
				getMax()
			>= param.minMappingQualityQ2 ?
				MAXIMUM
			:
				DUBIOUS);

		if (dq.getNonNullValue().atLeast(GOOD)) {
			//Check if criteria are met even if ignoring bases with
			//Phred quality scores that do not meet Q2 threshold

			topCounter.minBasePhredScore = param.minBasePhredScoreQ2;
			topCounter.compute();
			bottomCounter.minBasePhredScore = param.minBasePhredScoreQ2;
			bottomCounter.compute();
			final CandidateDuplexEval topCount, bottomCount;
			boolean topFailed = false, bottomFailed = false;
			if ((top != null &&
							(((topCount = Util.nullableify(topCounter.candidateCounts.get(top.candidate))) == null) ||
						(topFailed = topCount.count < param.minReadsPerStrandQ2)))
				||
					(bottom != null &&
							(((bottomCount = Util.nullableify(bottomCounter.candidateCounts.get(bottom.candidate))) == null) ||
						(bottomFailed = bottomCount.count < param.minReadsPerStrandQ2)))) {
				dq.addUnique(N_STRAND_READS_ABOVE_Q2_PHRED, DUBIOUS);
				stats.nPosDuplexTooFewReadsAboveQ2Phred.accept(location);
				if (param.logReadIssuesInOutputBam) {
					if (bottomFailed)
						issues.add(location + "_TFR2B");
					if (topFailed)
						issues.add(location + "_TFR2T");
				}
			}
		}

		dq.addUnique(BOTTOM_STRAND_MAP_Q2,
			new IntMinMax<ExtendedSAMRecord>().defaultMax(255).
				acceptMax(bottomCounter.keptRecords,
					er -> ((ExtendedSAMRecord) er).getMappingQuality()).
			getMax()
			>= param.minMappingQualityQ2 ?
				MAXIMUM
			:
				DUBIOUS);

		final boolean thresholds2Met, thresholds1Met;

		thresholds2Met = ((top != null) && top.count >= param.minConsensusThresholdQ2 * nTopStrandsWithCandidate) &&
			(bottom != null && bottom.count >= param.minConsensusThresholdQ2 * nBottomStrandsWithCandidate);

		thresholds1Met = (top == null || top.count >= param.minConsensusThresholdQ1 * nTopStrandsWithCandidate) &&
			(bottom == null || bottom.count >= param.minConsensusThresholdQ1 * nBottomStrandsWithCandidate);

		if (top != null) {
			minFracTopCandidate = top.count / ((float) nTopStrandsWithCandidate);
		}

		if (bottom != null) {
			minFracTopCandidate = Math.min(minFracTopCandidate, bottom.count / ((float) nBottomStrandsWithCandidate));
		}

		if (!thresholds1Met) {
			//TODO Following quality assignment is redundant with CONSENSUS_Q0 below
			dq.addUnique(CONSENSUS_THRESHOLDS_1, ATROCIOUS);
			stats.nConsensusQ1NotMet.increment(location);
			if (param.logReadIssuesInOutputBam) {
				issues.add(location + " CS0Y_" + (top != null ? top.count : "x") +
					'_' + nTopStrandsWithCandidate + '_' +
						(bottom != null ? bottom.count : "x") +
					'_' + nBottomStrandsWithCandidate);
			}
		}

		if (maxInsertSize == 0) {
			dq.addUnique(INSERT_SIZE, POOR);
		} else if (maxInsertSize < param.minInsertSize || minInsertSize > param.maxInsertSize) {
			dq.addUnique(INSERT_SIZE, DUBIOUS);
		}

		Assert.isFalse(invalid);

		if (top != null) {
			acceptDistanceToLigSite(top.maxDistanceToLigSite);
		}
		if (bottom != null) {
			acceptDistanceToLigSite(bottom.maxDistanceToLigSite);
		}

		final int distanceToLigSite = getMaxDistanceToLigSite();
		if (distanceToLigSite <= param.ignoreFirstNBasesQ1) {
			dq.addUnique(CLOSE_TO_LIG, POOR);
		} else if (distanceToLigSite <= param.ignoreFirstNBasesQ2) {
			dq.addUnique(CLOSE_TO_LIG, DUBIOUS);
		}

		final boolean dqOKForOneStranded = dq.getNonNullValue().atLeast(GOOD);

		if (bottom != null && top != null) {
			if (thresholds2Met) {
				//Nothing to do
			} else if (thresholds1Met) {
				dq.addUnique(CONSENSUS_Q1, DUBIOUS);
				stats.nPosDuplexWithLackOfStrandConsensus2.increment(location);
				if (param.logReadIssuesInOutputBam) {
					if (top.count < param.minConsensusThresholdQ2 * nTopStrandsWithCandidate)
						issues.add(location + " CS1T_" + shortLengthFloatFormatter.get().format
								(((float) top.count) / nTopStrandsWithCandidate));
					if (bottom.count < param.minConsensusThresholdQ2 * nBottomStrandsWithCandidate)
						issues.add(location + " CS1B_" + shortLengthFloatFormatter.get().format
								(((float) bottom.count) / nBottomStrandsWithCandidate));
				}
			} else {
				dq.addUnique(CONSENSUS_Q0, POOR);
				stats.nPosDuplexWithLackOfStrandConsensus1.increment(location);
				if (param.logReadIssuesInOutputBam) {
					if (top.count < param.minConsensusThresholdQ1 * nTopStrandsWithCandidate)
						issues.add(location + " CS0T_" + shortLengthFloatFormatter.get().format
								(((float) top.count) / nTopStrandsWithCandidate));
					if (bottom.count < param.minConsensusThresholdQ1 * nBottomStrandsWithCandidate)
						issues.add(location + " CS0B_" + shortLengthFloatFormatter.get().format
								(((float) bottom.count) / nBottomStrandsWithCandidate));
				}
			}
		} else {//Only the top or bottom strand is represented
			CandidateDuplexEval presentStrand = top != null ? top : bottom;
			float total = nTopStrandsWithCandidate + nBottomStrandsWithCandidate; //One is 0, doesn't matter which
			if (presentStrand != null && presentStrand.count < param.minConsensusThresholdQ1 * total) {
				if (param.logReadIssuesInOutputBam) {
					issues.add(location + " CS0X_" + shortLengthFloatFormatter.get().format
							(presentStrand.count / total));
				}
			}
			dq.addUnique(MISSING_STRAND, DUBIOUS);
		}

		final boolean enoughReadsForQ2Disag =
			bottom != null &&
			top != null &&
			bottom.count >= param.minReadsPerStrandForDisagreement &&
			top.count >= param.minReadsPerStrandForDisagreement;

		final boolean highEnoughQualForQ2Disagreement =
			enoughReadsForQ2Disag &&
			dq.getNonNullValue().atLeast(GOOD);

		final boolean bothStrandsPresent = bottom != null && top != null;

		final boolean noHiddenCandidateAndBSP = bothStrandsPresent &&
			!requireNonNull(top).candidate.isHidden() &&
			!requireNonNull(bottom).candidate.isHidden();

		if (highEnoughQualForQ2Disagreement && noHiddenCandidateAndBSP) {
			result.disagQ2Coverage++;
		}

		final CandidateDuplexEval nonNullEval = bottom == null ? top : bottom;

		final boolean okForOneStrandedDisag =
			dqOKForOneStranded &&
			!requireNonNull(nonNullEval).candidate.isHidden() &&
			nonNullEval.count >= param.minReadsPerStrandForDisagreement;

		if (okForOneStrandedDisag && !bothStrandsPresent) {
			result.disagOneStrandedCoverage++;
		}

		final DuplexDisagreement duplexDisagreement;

		if (!bothStrandsPresent) {
			if (okForOneStrandedDisag && requireNonNull(nonNullEval).candidate.getMutationType() != MutationType.WILDTYPE) {
				final boolean reverseComplementDisag =
					topStrandIsNegative ?
						nonNullEval == top
					:
						nonNullEval == bottom;

				duplexDisagreement = new DuplexDisagreement(
					Mutation.UNKNOWN_STATUS,
					reverseComplementDisag ?
						nonNullEval.candidate.getMutation().reverseComplement()
					:
						requireNonNull(nonNullEval.candidate.getMutation()),
					false,
					DUBIOUS);
				duplexDisagreement.probCollision = probAtLeastOneCollision;
			} else {
				duplexDisagreement = null;
			}
		} else if (requireNonNull(bottom).candidate.equals(requireNonNull(top).candidate)) {
			duplexDisagreement = null;
		} else {
			dq.addUnique(DISAGREEMENT, ATROCIOUS);
			issues.add(location + " DSG");

			final Mutation m1 = new Mutation(top.candidate);
			final Mutation m2 = new Mutation(bottom.candidate);
			final boolean noWildtype = !m1.mutationType.isWildtype() && !m2.mutationType.isWildtype();
			final Mutation actualMutant = (!m1.mutationType.isWildtype()) ? m1 : m2;
			final Mutation wildtype = (actualMutant == m1) ? m2 : m1;

			final CandidateSequence mutantCandidate;
			final CandidateDuplexEval mutantEval;
			if (actualMutant == m1) {
				mutantCandidate = top.candidate;
				mutantEval = top;
			} else {
				mutantCandidate = bottom.candidate;
				mutantEval = bottom;
			}

			final boolean reverseComplementDisag =
				topStrandIsNegative ?
						mutantCandidate == top.candidate
					:
						mutantCandidate == bottom.candidate;

			if (analyzer.codingStrandTester != null) {
				Optional<Boolean> negativeCodingStrand =
						analyzer.codingStrandTester.getNegativeStrand(location);
				actualMutant.setTemplateStrand(negativeCodingStrand.map(
						b ->  b == reverseComplementDisag ? false : true));
			}

			if (noHiddenCandidateAndBSP && !noWildtype && mutantEval.count > 1) {
				final int mutConsensus, wtConsensus;
				if (m1.mutationType.isWildtype()) {
					wtConsensus = (int) (100f * top.count / nTopStrandsWithCandidate);
					mutConsensus = (int) (100f * bottom.count / nBottomStrandsWithCandidate);
				} else {
					mutConsensus = (int) (100f * top.count / nTopStrandsWithCandidate);
					wtConsensus = (int) (100f * bottom.count / nBottomStrandsWithCandidate);
				}
				stats.disagMutConsensus.computeIfAbsent(
					reverseComplementDisag ? actualMutant.reverseComplement() : actualMutant,
						key -> new Histogram(100)).insert(mutConsensus);
				stats.disagWtConsensus.computeIfAbsent(
					reverseComplementDisag ? wildtype : wildtype.reverseComplement(),
						key -> new Histogram(100)).insert(wtConsensus);
			}

			if (!highEnoughQualForQ2Disagreement || !noHiddenCandidateAndBSP) {
				duplexDisagreement = null;
			} else {
				if (noWildtype) {
					//If there is no natural ordering for the disagreement pair, sort
					//it by alphabetical order (instead of wt sequence, mutated sequence)
					final byte nonNullTop =
						top.candidate.getSequence() == null ?
							0
						:
							requireNonNull(requireNonNull(top).candidate.getSequence())[0];

					final byte nonNullBottom =
						bottom.candidate.getSequence() == null ?
							0
						:
							requireNonNull(requireNonNull(bottom).candidate.getSequence())[0];

					final boolean switchOrder = nonNullTop > nonNullBottom;

					stats.nPosDuplexWithTopBottomDuplexDisagreementNoWT.accept(location);

					duplexDisagreement = switchOrder ?
							new DuplexDisagreement(m1, m2, false, GOOD)
						:
							new DuplexDisagreement(m2, m1, false, GOOD);

					duplexDisagreement.probCollision = probAtLeastOneCollision;
				} else {
					duplexDisagreement = reverseComplementDisag ?
						new DuplexDisagreement(wildtype.reverseComplement(), actualMutant.reverseComplement(), true, GOOD)
					:
						new DuplexDisagreement(wildtype, actualMutant, true, GOOD);
					duplexDisagreement.probCollision = probAtLeastOneCollision;

					switch(actualMutant.mutationType) {
						case DELETION:
							stats.delDisagDistanceToLigationSite.insert(maxDistanceToLig);
							stats.nPosDuplexWithTopBottomDuplexDisagreementNotASub.accept(location);
							stats.disagDelSize.insert(actualMutant.mutationSequence.length);
							break;
						case INSERTION:
							stats.insDisagDistanceToLigationSite.insert(maxDistanceToLig);
							stats.nPosDuplexWithTopBottomDuplexDisagreementNotASub.accept(location);
							break;
						case SUBSTITUTION:
							stats.substDisagDistanceToLigationSite.insert(maxDistanceToLig);
							break;
						case WILDTYPE:
							throw new AssertionFailedException();
						default:
							throw new AssertionFailedException();
					}

				}//End case with one wildtype candidate
			}//End highEnoughQualForQ2Disagreement
		}//End candidate for disagreement

		if (duplexDisagreement != null) {
			result.disagreements.addAt(duplexDisagreement, this);
		}

		localAndGlobalQuality = dq;

		//Now remove support given to non-consensus candidate mutations by this duplex
		final boolean atrocious = dq.getNonNullValue().atMost(ATROCIOUS);
		if (!param.filterOpticalDuplicates || top != null || bottom != null)
		candidateSet.each(candidate -> {
			//Assert.isFalse(top == null && bottom == null);
			if (!atrocious &&
					(bottom == null || candidate.equals(bottom.candidate)) &&
					(top == null || candidate.equals(top.candidate))) {
				return;
			}

			final @NonNull TObjectIntMap<ExtendedSAMRecord> reads =
				candidate.getMutableConcurringReads();

			final int noEntryValue = reads.getNoEntryValue();
			@SuppressWarnings("unused")
			int nRemoved = 0;

			for (int i = bottomStrandRecords.size() - 1; i >= 0; --i) {
				ExtendedSAMRecord r = bottomStrandRecords.get(i);
				if (candidate.removeConcurringRead(r) != noEntryValue) {
					nRemoved++;
					if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
						logger.info("Removed support for " + candidate + " by read " + r);
					}
				}
				Assert.isFalse(reads.containsKey(r));
				Assert.isFalse(candidate.getNonMutableConcurringReads().containsKey(r));
			}
			for (int i = topStrandRecords.size() - 1; i >= 0; --i) {
				ExtendedSAMRecord r = topStrandRecords.get(i);
				if (candidate.removeConcurringRead(r) != noEntryValue) {
					if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
						logger.info("Removed support for " + candidate + " by read " + r);
					}
					nRemoved++;
				}
				Assert.isFalse(reads.containsKey(r));
				Assert.isFalse(candidate.getNonMutableConcurringReads().containsKey(r));
			}

			if (duplexDisagreement != null && duplexDisagreement.quality.atLeast(GOOD) &&
					param.Q2DisagCapsMatchingMutationQuality &&
					candidate.getMutationType() != MutationType.WILDTYPE &&
					(candidate.equals(requireNonNull(top).candidate) || candidate.equals(requireNonNull(bottom).candidate))) {
				//Mark presence of at least one duplex with disagreement matching the mutation
				//with DISAGREEMENT Assay, but only set corresponding quality to "DUBIOUS"
				//if the disagreement is of sufficiently high quality, so that a single
				//low-quality duplex cannot force downgrading of mutation (which may be
				//undesirable if other high-quality duplexes support presence of the mutation).
				candidate.getQuality().add(PositionAssay.AT_LEAST_ONE_DISAG,
					duplexDisagreement.quality.atLeast(GOOD) ? DUBIOUS : GOOD);
				if (!enoughReadsForQ2Disag) {
					candidate.getQuality().add(PositionAssay.DISAG_THAT_MISSED_Q2, GOOD);
				}
			}
			return;
		});

		if (dq.getValueIgnoring(ignorePhred).atLeast(GOOD)) {
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
		maxQuality = max(maxQuality, dq.getNonNullValue());
		minQuality = min(minQuality, dq.getNonNullValue());

	}

	public void randomizeStrands(Random random) {
		if (topStrandRecords.isEmpty() || bottomStrandRecords.isEmpty()) {
			return;
		}
		List<@NonNull ExtendedSAMRecord> shuffled = new ArrayList<>();
		final int topSize = topStrandRecords.size();
		final int bottomSize = bottomStrandRecords.size();
		shuffled.addAll(topStrandRecords);
		shuffled.addAll(bottomStrandRecords);
		Collections.shuffle(shuffled, random);
		int nTop = topStrandRecords.size();
		topStrandRecords.clear();
		bottomStrandRecords.clear();
		for (int i = 0; i < nTop; i++) {
			topStrandRecords.add(shuffled.get(i));
		}
		for (int i = shuffled.size() - 1; i >= nTop; i--) {
			bottomStrandRecords.add(shuffled.get(i));
		}
		Assert.isTrue(topStrandRecords.size() == topSize);
		Assert.isTrue(bottomStrandRecords.size() == bottomSize);
	}

	void analyzeForStats(Parameters param, AnalysisStats stats) {
		Assert.isFalse(invalid);

		stats.duplexTotalRecords.insert(totalNRecords);

		totalNRecords = topStrandRecords.size() + bottomStrandRecords.size();
		MutableList<ExtendedSAMRecord> allDuplexRecords = new FastList<>(totalNRecords);
		allDuplexRecords.addAll(topStrandRecords);
		allDuplexRecords.addAll(bottomStrandRecords);

		if (param.filterOpticalDuplicates) {
			//Side effect: allDuplexRecords sorted by x position
			markDuplicates(param, stats, allDuplexRecords);
		}

		final boolean alreadyVisitedForStats = allDuplexRecords.anySatisfy(
				r -> r.duplexAlreadyVisitedForStats);

		if (!alreadyVisitedForStats) {
			allDuplexRecords.each(r -> r.duplexAlreadyVisitedForStats = true);
			stats.duplexinsertSize.insert(Math.abs(allDuplexRecords.get(0).getInsertSize()));
		}

		int i = 0;
		double sumDisagreementRates = 0d;
		int sumNClipped = 0;
		int nReadsWrongPairRecomputed = 0;

		for (ExtendedSAMRecord r: allDuplexRecords) {
			if (r.formsWrongPair()) {
				nReadsWrongPairRecomputed++;
			}

			Assert.isFalse(r.getnClipped() < 0);
			sumNClipped += r.getnClipped();
			i++;
			sumDisagreementRates += (r.nReferenceDisagreements / ((float) (r.effectiveLength)));
		}

		if (nReadsWrongPairRecomputed > 0) {
			globalQuality.addUnique(N_READS_WRONG_PAIR, DUBIOUS);
		}

		if (i == 0) {
			stats.nDuplexesNoStats.add(1);
		} else {
			stats.nDuplexesWithStats.add(1);
			referenceDisagreementRate = (float) (sumDisagreementRates / i);
			stats.averageDuplexReferenceDisagreementRate.insert((int) (1000 * referenceDisagreementRate));
			averageNClipped = sumNClipped / i;
			stats.duplexAverageNClipped.insert(averageNClipped);
		}

		if (averageNClipped > param.maxAverageBasesClipped) {
			globalQuality.addUnique(AVERAGE_N_CLIPPED, DUBIOUS);
			stats.nDuplexesTooMuchClipping.accept(requireNonNull(roughLocation));
		}


		final int inferredSize = Math.abs(allDuplexRecords.get(0).getInsertSize());
		if (inferredSize < 130) {
			stats.duplexInsert100_130AverageNClipped.insert(averageNClipped);
		} else if (inferredSize < 180) {
			stats.duplexInsert130_180averageNClipped.insert(averageNClipped);
		}

	}

	public void markDuplicates(Parameters param, AnalysisStats stats, MutableList<ExtendedSAMRecord> reads) {
		reads.sort(Comparator.comparing(ExtendedSAMRecord::getxLoc));
		reads.each(r -> {
			r.visitedForOptDups = false;
			r.opticalDuplicate = false;
			r.hasOpticalDuplicates = false;
			r.tempIndex0 = -1;
			r.tempIndex1 = -1;
		});
		int maxxIndex = 0;
		SettableInteger nDuplicates = new SettableInteger(0);
		final int bandWidth = param.computeAllReadDistances ? 1_000_000_000 : param.opticalDuplicateDistance;
		for (int recordIndex = 0; recordIndex < totalNRecords; recordIndex++) {
			final ExtendedSAMRecord record = reads.get(recordIndex);
			Assert.isTrue(!((record.opticalDuplicate || record.hasOpticalDuplicates) ^ record.visitedForOptDups));
			if (record.opticalDuplicate || record.hasOpticalDuplicates) {
				continue;
			}
			final int x = record.xLoc;
			while (maxxIndex < totalNRecords - 1 && reads.get(maxxIndex + 1).xLoc < x + bandWidth) {
				maxxIndex++;
			}
			final int finalRecordIndex = recordIndex;
			final int finalMaxxIndex = maxxIndex;
			forEachDuplicate(param, stats, reads, reads.get(recordIndex), recordIndex + 1, maxxIndex,
					dupIndex -> {
						nDuplicates.addAndGet(sweepOpticalDuplicateGroup(param, stats, reads, finalRecordIndex, finalMaxxIndex));
						return false;//Stop on first duplicate because whole group has already been swept
					});
		}
		stats.nReadsOpticalDuplicates.add((
				leftAlignmentStart != null ? leftAlignmentStart : requireNonNull(rightAlignmentStart)),
			nDuplicates.get());
	}

	@SuppressWarnings("ReferenceEquality")
	private static void forEachDuplicate(
			Parameters param,
			AnalysisStats stats,
			List<ExtendedSAMRecord> reads,
			ExtendedSAMRecord read,
			final int startIndex,
			final int endIndex,
			IntPredicate predicate) {

		if (startIndex - 1 > endIndex || endIndex < 0) {
			throw new IllegalArgumentException("Start and end indices " + startIndex + " and " + endIndex);
		}
		final long squaredMinDistance = square(param.opticalDuplicateDistance);
		final String rt = read.getRunAndTile();
		final int x = read.getxLoc();
		final int y = read.getyLoc();
		for (int i = startIndex; i <= endIndex; i++) {
			ExtendedSAMRecord other = reads.get(i);
			if (other == read || other.visitedForOptDups) {
				continue;
			}
			if (rt != other.getRunAndTile()) {
				stats.readDistance.insert(50);//Arbitrary high value so that read pairs that cannot
				//be optical duplicates are accounted for
			} else {
				final long squaredDistance = square(x - other.getxLoc()) + square(y - other.getyLoc());
				if (squaredDistance == 0) {//Should only ever be true for a mate pair
					Assert.isTrue(read.record.getReadName().equals(other.record.getReadName()));
				} else {
					stats.readDistance.insert((int) Math.log(squaredDistance));
					if (squaredDistance < squaredMinDistance) {
						if (!predicate.test(i)) {
							break;
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("ReferenceEquality")
	private static int sweepOpticalDuplicateGroup(
			Parameters param,
			AnalysisStats stats,
			List<ExtendedSAMRecord> reads,
			final int readIndex,
			final int maxxIndex) {

		if (reads.get(0).tempIndex0 == -1) {
			markIndices(reads, param.computeAllReadDistances ? 1_000_000_000 : param.opticalDuplicateDistance);
		}

		Set<ExtendedSAMRecord> duplicateSet = new UnifiedSet<>();
		final ExtendedSAMRecord firstRead = reads.get(readIndex);
		duplicateSet.add(firstRead);
		List<ExtendedSAMRecord> toVisit = new ArrayList<>();
		toVisit.add(firstRead);
		while(!toVisit.isEmpty()) {
			addDuplicates(param, stats, reads, toVisit, duplicateSet, toVisit.remove(toVisit.size() - 1));
		}

		ExtendedSAMRecord bestRead = duplicateSet.stream().max(
			Comparator.comparing(ExtendedSAMRecord::getAveragePhred)).get();
		for (ExtendedSAMRecord read: duplicateSet) {
			ExtendedSAMRecord mate;
			if (read == bestRead || ((mate = read.getMate()) != null && mate == bestRead)) {
				read.hasOpticalDuplicates = true;
			} else {
				read.opticalDuplicate = true;
			}
		}

		return duplicateSet.size() - 1;
	}

	private static void markIndices(List<ExtendedSAMRecord> reads, int bandWidth) {
		int nReads = reads.size();
		int maxxIndex = 0;
		int minxIndex = 0;
		for (int readIndex = 0; readIndex < nReads; readIndex++) {
			final ExtendedSAMRecord read = reads.get(readIndex);
			Assert.isFalse(read.visitedForOptDups);
			Assert.isFalse(read.opticalDuplicate);
			Assert.isFalse(read.hasOpticalDuplicates);
			final int x = read.xLoc;
			while (maxxIndex < nReads - 1 && reads.get(maxxIndex + 1).xLoc < x + bandWidth) {
				maxxIndex++;
			}
			while (minxIndex < nReads - 1 && reads.get(minxIndex + 1).xLoc < x - bandWidth) {
				minxIndex++;
			}
			read.tempIndex0 = minxIndex;
			read.tempIndex1 = maxxIndex;
		}
	}

	private static void addDuplicates(
			Parameters param,
			AnalysisStats stats,
			List<ExtendedSAMRecord> reads,
			List<ExtendedSAMRecord> toVisit,
			Set<ExtendedSAMRecord> duplicateSet,
			ExtendedSAMRecord read) {

		read.visitedForOptDups = true;
		forEachDuplicate(param, stats, reads, read, read.tempIndex0, read.tempIndex1, i -> {
			ExtendedSAMRecord dup = reads.get(i);
			toVisit.add(dup);
			duplicateSet.add(dup);
			return true;
		});
	}

	private static long square(int n) {
		long l = n;
		return l * l;
	}

	public int getUnclippedAlignmentStart() {
		return position0;
	}

	public int getUnclippedAlignmentEnd() {
		return position3;
	}

}
