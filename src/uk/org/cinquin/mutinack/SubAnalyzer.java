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
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.MutationType.WILDTYPE;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.DISAGREEMENT;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.MISSING_STRAND;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.N_READS_PER_STRAND;
import static uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay.QUALITY_AT_POSITION;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.FRACTION_WRONG_PAIRS_AT_POS;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MAX_AVERAGE_CLIPPING_OF_DUPLEX_AT_POS;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MAX_DPLX_Q_IGNORING_DISAG;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MAX_Q_FOR_ALL_DUPLEXES;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MEDIAN_CANDIDATE_PHRED;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.MEDIAN_PHRED_AT_POS;
import static uk.org.cinquin.mutinack.candidate_sequences.PositionAssay.NO_DUPLEXES;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.qualities.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.qualities.Quality.DUBIOUS;
import static uk.org.cinquin.mutinack.qualities.Quality.GOOD;
import static uk.org.cinquin.mutinack.qualities.Quality.MINIMUM;
import static uk.org.cinquin.mutinack.qualities.Quality.POOR;
import static uk.org.cinquin.mutinack.qualities.Quality.max;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.set.MutableSetMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.factory.SortedSets;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.samtools.CigarOperator;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SamPairUtil;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import contrib.net.sf.samtools.util.StringUtil;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TByteObjectMap;
import gnu.trove.map.hash.TByteObjectHashMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.TCustomHashSet;
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateBuilder;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateCounter;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateDeletion;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.candidate_sequences.ExtendedAlignmentBlock;
import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableDouble;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.HashingStrategies;
import uk.org.cinquin.mutinack.misc_util.collections.InterningSet;
import uk.org.cinquin.mutinack.misc_util.collections.duplex_keeper.DuplexArrayListKeeper;
import uk.org.cinquin.mutinack.misc_util.collections.duplex_keeper.DuplexHashMapKeeper;
import uk.org.cinquin.mutinack.misc_util.collections.duplex_keeper.DuplexKeeper;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.LocationExaminationResults;
import uk.org.cinquin.mutinack.qualities.DetailedPositionQualities;
import uk.org.cinquin.mutinack.qualities.DetailedQualities;
import uk.org.cinquin.mutinack.qualities.Quality;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.MultiCounter;

public final class SubAnalyzer {
	private static final Logger logger = LoggerFactory.getLogger(SubAnalyzer.class);

	//For assertion and debugging purposes
	public boolean incrementednPosDuplexQualityQ2OthersQ1Q2, processed, c1, c2, c3, c4;

	public final @NonNull Mutinack analyzer;
	@NonNull Parameters param;
	@NonNull public AnalysisStats stats;//Will in fact be null until set in SubAnalyzerPhaser but that's OK
	final @NonNull SettableInteger lastProcessablePosition = new SettableInteger(-1);
	final @NonNull THashMap<SequenceLocation, THashSet<CandidateSequence>> candidateSequences =
			new THashMap<>(1_000);
	int truncateProcessingAt = Integer.MAX_VALUE;
	int startProcessingAt = 0;
	MutableList<@NonNull Duplex> analyzedDuplexes;
	float[] averageClipping;
	int averageClippingOffset = Integer.MAX_VALUE;
	final @NonNull THashMap<String, @NonNull ExtendedSAMRecord> extSAMCache =
			new THashMap<>(10_000, 0.5f);
	private final AtomicInteger threadCount = new AtomicInteger();
	@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
	@NonNull Map<@NonNull ExtendedSAMRecord, @NonNull SAMRecord> readsToWrite
		= new THashMap<>();
	private final Random random;

	public static final @NonNull Set<@NonNull DuplexAssay>
		ASSAYS_TO_IGNORE_FOR_DISAGREEMENT_QUALITY
		= Collections.unmodifiableSet(EnumSet.of(DISAGREEMENT)),

		ASSAYS_TO_IGNORE_FOR_DUPLEX_NSTRANDS
		= Collections.unmodifiableSet(EnumSet.of(N_READS_PER_STRAND, MISSING_STRAND, DuplexAssay.TOTAL_N_READS_Q2));

	static final @NonNull TByteObjectMap<@NonNull String> byteMap;
	static {
		byteMap = new TByteObjectHashMap<>();
		Assert.isNull(byteMap.put((byte) 'A', "A"));
		Assert.isNull(byteMap.put((byte) 'a', "A"));
		Assert.isNull(byteMap.put((byte) 'T', "T"));
		Assert.isNull(byteMap.put((byte) 't', "T"));
		Assert.isNull(byteMap.put((byte) 'G', "G"));
		Assert.isNull(byteMap.put((byte) 'g', "G"));
		Assert.isNull(byteMap.put((byte) 'C', "C"));
		Assert.isNull(byteMap.put((byte) 'c', "C"));
		Assert.isNull(byteMap.put((byte) 'N', "N"));
		Assert.isNull(byteMap.put((byte) 'n', "N"));
		Assert.isNull(byteMap.put((byte) 'W', "W"));
		Assert.isNull(byteMap.put((byte) 'w', "W"));
		Assert.isNull(byteMap.put((byte) 'S', "S"));
		Assert.isNull(byteMap.put((byte) 's', "S"));
		Assert.isNull(byteMap.put((byte) 'Y', "Y"));
		Assert.isNull(byteMap.put((byte) 'y', "Y"));
		Assert.isNull(byteMap.put((byte) 'R', "R"));
		Assert.isNull(byteMap.put((byte) 'r', "R"));
		Assert.isNull(byteMap.put((byte) 'B', "B"));
		Assert.isNull(byteMap.put((byte) 'b', "B"));
		Assert.isNull(byteMap.put((byte) 'D', "D"));
		Assert.isNull(byteMap.put((byte) 'd', "D"));
		Assert.isNull(byteMap.put((byte) 'H', "H"));
		Assert.isNull(byteMap.put((byte) 'h', "H"));
		Assert.isNull(byteMap.put((byte) 'K', "K"));
		Assert.isNull(byteMap.put((byte) 'k', "K"));
		Assert.isNull(byteMap.put((byte) 'M', "M"));
		Assert.isNull(byteMap.put((byte) 'm', "M"));
		Assert.isNull(byteMap.put((byte) 'V', "V"));
		Assert.isNull(byteMap.put((byte) 'v', "V"));
	}

	static final @NonNull TByteObjectMap<byte @NonNull[]> byteArrayMap;
	static {
		byteArrayMap = new TByteObjectHashMap<>();
		Assert.isNull(byteArrayMap.put((byte) 'A', new byte[] {'A'}));
		Assert.isNull(byteArrayMap.put((byte) 'a', new byte[] {'a'}));
		Assert.isNull(byteArrayMap.put((byte) 'T', new byte[] {'T'}));
		Assert.isNull(byteArrayMap.put((byte) 't', new byte[] {'t'}));
		Assert.isNull(byteArrayMap.put((byte) 'G', new byte[] {'G'}));
		Assert.isNull(byteArrayMap.put((byte) 'g', new byte[] {'g'}));
		Assert.isNull(byteArrayMap.put((byte) 'C', new byte[] {'C'}));
		Assert.isNull(byteArrayMap.put((byte) 'c', new byte[] {'c'}));
		Assert.isNull(byteArrayMap.put((byte) 'N', new byte[] {'N'}));
		Assert.isNull(byteArrayMap.put((byte) 'n', new byte[] {'n'}));
		Assert.isNull(byteArrayMap.put((byte) 'W', new byte[] {'W'}));
		Assert.isNull(byteArrayMap.put((byte) 'w', new byte[] {'w'}));
		Assert.isNull(byteArrayMap.put((byte) 'S', new byte[] {'S'}));
		Assert.isNull(byteArrayMap.put((byte) 's', new byte[] {'s'}));
		Assert.isNull(byteArrayMap.put((byte) 'Y', new byte[] {'Y'}));
		Assert.isNull(byteArrayMap.put((byte) 'y', new byte[] {'y'}));
		Assert.isNull(byteArrayMap.put((byte) 'R', new byte[] {'R'}));
		Assert.isNull(byteArrayMap.put((byte) 'r', new byte[] {'r'}));
		Assert.isNull(byteArrayMap.put((byte) 'B', new byte[] {'B'}));
		Assert.isNull(byteArrayMap.put((byte) 'D', new byte[] {'D'}));
		Assert.isNull(byteArrayMap.put((byte) 'H', new byte[] {'H'}));
		Assert.isNull(byteArrayMap.put((byte) 'K', new byte[] {'K'}));
		Assert.isNull(byteArrayMap.put((byte) 'M', new byte[] {'M'}));
		Assert.isNull(byteArrayMap.put((byte) 'V', new byte[] {'V'}));
		Assert.isNull(byteArrayMap.put((byte) 'b', new byte[] {'b'}));
		Assert.isNull(byteArrayMap.put((byte) 'd', new byte[] {'d'}));
		Assert.isNull(byteArrayMap.put((byte) 'h', new byte[] {'h'}));
		Assert.isNull(byteArrayMap.put((byte) 'k', new byte[] {'k'}));
		Assert.isNull(byteArrayMap.put((byte) 'm', new byte[] {'m'}));
		Assert.isNull(byteArrayMap.put((byte) 'v', new byte[] {'v'}));
	}

	private volatile boolean writing = false;

	synchronized void queueOutputRead(@NonNull ExtendedSAMRecord e, @NonNull SAMRecord r, boolean mayAlreadyBeQueued) {
		Assert.isFalse(writing);
		final SAMRecord previous;
		if ((previous = readsToWrite.put(e, r)) != null && !mayAlreadyBeQueued) {
			throw new IllegalStateException("Read " + e.getFullName() + " already queued for writing; new: " +
				r.toString() + "; previous: " + previous.toString());
		}
	}

	void writeOutputReads() {
		Assert.isFalse(writing);
		writing = true;
		try {
			synchronized(analyzer.outputAlignmentWriter) {
				for (SAMRecord samRecord: readsToWrite.values()) {
					Objects.requireNonNull(analyzer.outputAlignmentWriter).addAlignment(samRecord);
				}
			}
			readsToWrite.clear();
		} finally {
			writing = false;
		}
	}

	@SuppressWarnings("null")//Stats not initialized straight away
	SubAnalyzer(@NonNull Mutinack analyzer) {
		this.analyzer = analyzer;
		this.param = analyzer.param;
		useHashMap = param.alignmentPositionMismatchAllowed == 0;
		random = new Random(param.randomSeed);
	}

	private boolean meetsQ2Thresholds(@NonNull ExtendedSAMRecord extendedRec) {
		return !extendedRec.formsWrongPair() &&
				extendedRec.getnClipped() <= param.maxAverageBasesClipped &&
				extendedRec.getMappingQuality() >= param.minMappingQualityQ2 &&
				Math.abs(extendedRec.getInsertSize()) <= param.maxInsertSize;
	}

	/**
	 * Make sure that concurring reads get associated with a unique candidate.
	 * We should *not* insert candidates directly into candidateSequences.
	 * Get interning as a small, beneficial side effect.
	 * @param candidate
	 * @param location
	 * @return
	 */
	private @NonNull CandidateSequence insertCandidateAtPosition(@NonNull CandidateSequence candidate,
			@NonNull SequenceLocation location) {

		//No need for synchronization since we should not be
		//concurrently inserting two candidates at the same position
		THashSet<CandidateSequence> candidates = candidateSequences.computeIfAbsent(location, k -> new THashSet<>(2, 0.2f));
		CandidateSequence candidateMapValue = candidates.get(candidate);
		if (candidateMapValue == null) {
			boolean added = candidates.add(candidate);
			Assert.isTrue(added);
		} else {
			candidateMapValue.mergeWith(candidate);
			candidate = candidateMapValue;
		}
		return candidate;
	}

	/**
	 * Load all reads currently referred to by candidate sequences and group them into DuplexReads
	 * @param toPosition
	 * @param fromPosition
	 */
	void load(int fromPosition, int toPosition) {
		Assert.isFalse(threadCount.incrementAndGet() > 1);
		try {
			if (toPosition < fromPosition) {
				throw new IllegalArgumentException("Going from " + fromPosition + " to " + toPosition);
			}
			final MutableList<@NonNull Duplex> resultDuplexes = new FastList<>(3_000);
			loadAll(fromPosition, toPosition, resultDuplexes);
			analyzedDuplexes = resultDuplexes;
		} finally {
			if (NONTRIVIAL_ASSERTIONS) {
				threadCount.decrementAndGet();
			}
		}
	}

	void checkAllDone() {
		if (!candidateSequences.isEmpty()) {
			final SettableInteger nLeftBehind = new SettableInteger(-1);
			candidateSequences.forEach((k,v) -> {
				Assert.isTrue(v.isEmpty() || (v.iterator().next().getLocation().equals(k)),
						"Mismatched locations");

				String s = v.stream().
						filter(c -> c.getLocation().position > truncateProcessingAt + param.maxInsertSize &&
								c.getLocation().position < startProcessingAt - param.maxInsertSize).
						flatMap(v0 -> v0.getNonMutableConcurringReads().keySet().stream()).
						map(read -> {
							if (nLeftBehind.incrementAndGet() == 0) {
								logger.error("Sequences left behind before " + truncateProcessingAt);
							}
							return Integer.toString(read.getAlignmentStart()) + '-' +
								Integer.toString(read.getAlignmentEnd()); })
						.collect(Collectors.joining("; "));
				if (!s.equals("")) {
					logger.error(s);
				}
			});

			Assert.isFalse(nLeftBehind.get() > 0);
		}
	}

	private final boolean useHashMap;
	private @NonNull DuplexKeeper getDuplexKeeper(boolean fallBackOnIntervalTree) {
		final @NonNull DuplexKeeper result;
		if (MutinackGroup.forceKeeperType != null) {
			switch(MutinackGroup.forceKeeperType) {
				case "DuplexHashMapKeeper":
					if (useHashMap) {
						result = new DuplexHashMapKeeper();
					} else {
						result = new DuplexArrayListKeeper(5_000);
					}
					break;
				//case "DuplexITKeeper":
				//	result = new DuplexITKeeper();
				//	break;
				case "DuplexArrayListKeeper":
					result = new DuplexArrayListKeeper(5_000);
					break;
				default:
					throw new AssertionFailedException();
			}
		} else {
			if (useHashMap) {
				result = new DuplexHashMapKeeper();
			} else /*if (fallBackOnIntervalTree) {
				result = new DuplexITKeeper();
			} else */{
				result = new DuplexArrayListKeeper(5_000);
			}
		}
		return result;
	}

	/**
	 * Group reads into duplexes.
	 * @param toPosition
	 * @param fromPosition
	 * @param finalResult
	 */
	private void loadAll(
			final int fromPosition,
			final int toPosition,
			final @NonNull List<Duplex> finalResult) {

		/**
		 * Use a custom hash map type to keep track of duplexes when
		 * alignmentPositionMismatchAllowed is 0.
		 * This provides by far the highest performance.
		 * When alignmentPositionMismatchAllowed is greater than 0, use
		 * either an interval tree or a plain list. The use of an interval
		 * tree instead of a plain list provides a speed benefit only when
		 * there is large number of local duplexes, so switch dynamically
		 * based on that number. The threshold was optimized empirically
		 * and at a gross level.
		 */

		final boolean fallBackOnIntervalTree = extSAMCache.size() > 5_000;
		@NonNull DuplexKeeper duplexKeeper =
				getDuplexKeeper(fallBackOnIntervalTree);

		InterningSet<SequenceLocation> sequenceLocationCache =
			new InterningSet<>(500);

		final AlignmentExtremetiesDistance ed = new AlignmentExtremetiesDistance(
				analyzer.groupSettings, param);

		final SettableInteger nReadsExcludedFromDuplexes = new SettableInteger(0);

		final TObjectProcedure<@NonNull ExtendedSAMRecord> callLoadRead = rExtended -> {
			loadRead(rExtended, duplexKeeper, ed, sequenceLocationCache, nReadsExcludedFromDuplexes);
			return true;
		};

		if (param.jiggle) {
			List<@NonNull ExtendedSAMRecord> reorderedReads = new ArrayList<>(extSAMCache.values());
			Collections.shuffle(reorderedReads, random);
			reorderedReads.forEach(callLoadRead::execute);
		} else {
			extSAMCache.forEachValue(callLoadRead);
		}

		sequenceLocationCache.clear();//Not strictly necessary, but might as well release the
		//memory now

		if (param.randomizeStrand) {
			duplexKeeper.forEach(dr -> dr.randomizeStrands(random));
		}

		duplexKeeper.forEach(Duplex::computeGlobalProperties);

		Pair<Duplex, Duplex> pair;
		if (param.enableCostlyAssertions &&
				(pair =
					Duplex.checkNoEqualDuplexes(duplexKeeper)) != null) {
			throw new AssertionFailedException("Equal duplexes: " +
				pair.fst + " and " + pair.snd);
		}

		if (param.enableCostlyAssertions) {
			Assert.isTrue(checkReadsOccurOnceInDuplexes(extSAMCache.values(),
				duplexKeeper, nReadsExcludedFromDuplexes.get()));
		}

		//Group duplexes that have alignment positions that differ by at most
		//param.alignmentPositionMismatchAllowed
		//and left/right consensus that differ by at most
		//param.nVariableBarcodeMismatchesAllowed

		final DuplexKeeper cleanedUpDuplexes;
		if (param.nVariableBarcodeMismatchesAllowed > 0 /*&& duplexKeeper.size() < analyzer.maxNDuplexes*/) {
			cleanedUpDuplexes = Duplex.groupDuplexes(
					duplexKeeper,
					duplex -> duplex.computeConsensus(false, param.variableBarcodeLength),
					() -> getDuplexKeeper(fallBackOnIntervalTree),
					param,
					stats,
					0);
		} else {
			cleanedUpDuplexes = duplexKeeper;
			cleanedUpDuplexes.forEach(Duplex::computeGlobalProperties);
		}

		if (param.nVariableBarcodeMismatchesAllowed == 0) {
			cleanedUpDuplexes.forEach(d -> d.computeConsensus(true,
				param.variableBarcodeLength));
		}

		if (param.variableBarcodeLength == 0) {
			//Group duplexes by alignment start (or equivalent)
			TIntObjectHashMap<List<Duplex>> duplexPositions = new TIntObjectHashMap<>
				(1_000, 0.5f, -999);
			cleanedUpDuplexes.forEach(dr -> {
				List<Duplex> list = duplexPositions.computeIfAbsent(dr.leftAlignmentStart.position,
					(Supplier<List<Duplex>>) ArrayList::new);
				list.add(dr);
			});
			final double @NonNull[] insertSizeProb =
				Objects.requireNonNull(analyzer.insertSizeProbSmooth);
			duplexPositions.forEachValue(list -> {
				for (Duplex dr: list) {
					double sizeP = insertSizeProb[
					  Math.min(insertSizeProb.length - 1, dr.maxInsertSize)];
					Assert.isTrue(Double.isNaN(sizeP) || sizeP >= 0,
						() -> "Insert size problem: " + Arrays.toString(insertSizeProb));
					dr.probAtLeastOneCollision = 1 - Math.pow(1 - sizeP, list.size());
				}
				return true;
			});
		}

		cleanedUpDuplexes.forEach(duplexRead -> duplexRead.analyzeForStats(param, stats));

		cleanedUpDuplexes.forEach(finalResult::add);

		averageClippingOffset = fromPosition;
		final int arrayLength = toPosition - fromPosition + 1;
		averageClipping = new float[arrayLength];
		int[] duplexNumber = new int[arrayLength];

		cleanedUpDuplexes.forEach(duplexRead -> {
			int start = duplexRead.getUnclippedAlignmentStart() - fromPosition;
			int stop = duplexRead.getUnclippedAlignmentEnd() - fromPosition;
			start = Math.min(Math.max(0, start), toPosition - fromPosition);
			stop = Math.min(Math.max(0, stop), toPosition - fromPosition);

			for (int i = start; i <= stop; i++) {
				averageClipping[i] += duplexRead.averageNClipped;
				duplexNumber[i]++;
			}
		});

		insertDuplexGroupSizeStats(cleanedUpDuplexes, 0, stats.duplexLocalGroupSize);
		insertDuplexGroupSizeStats(cleanedUpDuplexes, 15, stats.duplexLocalShiftedGroupSize);

		if (param.computeDuplexDistances && cleanedUpDuplexes.size() < analyzer.maxNDuplexes) {
			cleanedUpDuplexes.forEach(d1 -> cleanedUpDuplexes.forEach(d2 ->
				stats.duplexDistance.insert(d1.euclideanDistanceTo(d2))));
		}

		for (int i = 0; i < averageClipping.length; i++) {
			int n = duplexNumber[i];
			if (n == 0) {
				Assert.isTrue(averageClipping[i] == 0);
			} else {
				averageClipping[i] /= n;
			}
		}
	}//End loadAll

	private static void insertDuplexGroupSizeStats(DuplexKeeper keeper, int offset, Histogram stats) {
		keeper.forEach(duplexRead -> {
			duplexRead.assignedToLocalGroup = duplexRead.leftAlignmentStart.position == ExtendedSAMRecord.NO_MATE_POSITION ||
				duplexRead.rightAlignmentEnd.position == ExtendedSAMRecord.NO_MATE_POSITION;//Ignore duplexes from reads that
			//have a missing mate
		});
		keeper.forEach(duplexRead -> {
			if (!duplexRead.assignedToLocalGroup) {
				int groupSize = countDuplexesInWindow(duplexRead, keeper, offset, 5);
				stats.insert(groupSize);
				duplexRead.assignedToLocalGroup = true;
			}
		});
	}

	private static int countDuplexesInWindow(Duplex d, DuplexKeeper dk, int offset, int windowWidth) {
		SettableInteger result = new SettableInteger(0);
		dk.getOverlappingWithSlop(d, offset, windowWidth).forEach(od -> {
			if (od.assignedToLocalGroup) {
				return;
			}
			int distance1 = od.rightAlignmentEnd.position - (d.rightAlignmentEnd.position + offset);
			if (Math.abs(distance1) <= windowWidth) {
				Assert.isTrue(Math.abs(distance1) <= windowWidth + Math.abs(offset));
				int distance2 = od.leftAlignmentStart.position - (d.leftAlignmentStart.position + offset);
				if (Math.abs(distance2) <= windowWidth) {
					od.assignedToLocalGroup = true;
					if (distance1 != 0 && distance2 != 0) {
						result.incrementAndGet();
					}
				}
			}
		});
		return result.get();
	}

	private void loadRead(@NonNull ExtendedSAMRecord rExtended, @NonNull DuplexKeeper duplexKeeper,
			AlignmentExtremetiesDistance ed, InterningSet<SequenceLocation> sequenceLocationCache,
			SettableInteger nReadsExcludedFromDuplexes) {

		final @NonNull SequenceLocation location = rExtended.getLocation();

		final byte @NonNull[] barcode = rExtended.variableBarcode;
		final byte @NonNull[] mateBarcode = rExtended.getMateVariableBarcode();
		final @NonNull SAMRecord r = rExtended.record;

		if (rExtended.getMate() == null) {
			stats.nMateOutOfReach.add(location, 1);
		}

		if (r.getMateUnmappedFlag()) {
			//It is not trivial to tell whether the mate is unmapped because
			//e.g. it did not sequence properly, or because all of the reads
			//from the duplex just do not map to the genome. To avoid
			//artificially inflating local duplex number, do not allow the
			//read to contribute to computed duplexes. Note that the rest of
			//the duplex-handling code could technically handle an unmapped
			//mate, and that the read will still be able to contribute to
			//local statistics such as average clipping.
			nReadsExcludedFromDuplexes.incrementAndGet();
			return;
		}

		boolean foundDuplexRead = false;
		final boolean matchToLeft = rExtended.duplexLeft();

		ed.set(rExtended);

		for (final Duplex duplex: duplexKeeper.getOverlapping(ed.temp)) {
			//stats.nVariableBarcodeCandidateExaminations.increment(location);

			boolean forceGrouping = false;
			if (Util.nullableify(duplex.allRecords.detect(drRec -> drRec.record.getReadName().equals(r.getReadName())))
					!= null) {
				forceGrouping = true;
			}

			ed.set(duplex);

			if (!forceGrouping && ed.getMaxDistance() > param.alignmentPositionMismatchAllowed) {
				continue;
			}

			final boolean barcodeMatch;
			//During first pass, do not allow any barcode mismatches
			if (forceGrouping) {
				barcodeMatch = true;
			} else if (matchToLeft) {
				barcodeMatch = basesEqual(duplex.leftBarcode, barcode,
					param.acceptNInBarCode) &&
					basesEqual(duplex.rightBarcode, mateBarcode,
						param.acceptNInBarCode);
			} else {
				barcodeMatch = basesEqual(duplex.leftBarcode, mateBarcode,
					param.acceptNInBarCode) &&
					basesEqual(duplex.rightBarcode, barcode,
						param.acceptNInBarCode);
			}

			//noinspection StatementWithEmptyBody
			if (forceGrouping || barcodeMatch) {
				if (r.getInferredInsertSize() >= 0) {
					if (r.getFirstOfPairFlag()) {
						if (param.enableCostlyAssertions) {
							Assert.isFalse(duplex.topStrandRecords.contains(rExtended));
						}
						duplex.topStrandRecords.add(rExtended);
					} else {
						if (param.enableCostlyAssertions) {
							Assert.isFalse(duplex.bottomStrandRecords.contains(rExtended));
						}
						duplex.bottomStrandRecords.add(rExtended);
					}
				} else {
					if (r.getFirstOfPairFlag()) {
						if (param.enableCostlyAssertions) {
							Assert.isFalse(duplex.bottomStrandRecords.contains(rExtended));
						}
						duplex.bottomStrandRecords.add(rExtended);
					} else {
						if (param.enableCostlyAssertions) {
							Assert.isFalse(duplex.topStrandRecords.contains(rExtended));
						}
						duplex.topStrandRecords.add(rExtended);
					}
				}
				if (param.enableCostlyAssertions) {//XXX May fail if there was a barcode
					//read error and duplex grouping was forced for mate
					Assert.noException(duplex::assertAllBarcodesEqual);
				}
				rExtended.duplex = duplex;
				//stats.nVariableBarcodeMatchAfterPositionCheck.increment(location);
				foundDuplexRead = true;
				break;
			} else {//left and/or right barcodes do not match
				/*
				leftEqual = basesEqual(duplexRead.leftBarcode, barcode, true, 1);
				rightEqual = basesEqual(duplexRead.rightBarcode, barcode, true, 1);
				if (leftEqual || rightEqual) {
					stats.nVariableBarcodesCloseMisses.increment(location);
				}*/
			}
		}//End loop over duplexReads

		if (!foundDuplexRead) {
			final Duplex duplex = matchToLeft ?
					new Duplex(analyzer.groupSettings, barcode, mateBarcode, !r.getReadNegativeStrandFlag(), r.getReadNegativeStrandFlag()) :
					new Duplex(analyzer.groupSettings, mateBarcode, barcode, r.getReadNegativeStrandFlag(), !r.getReadNegativeStrandFlag());

			duplex.roughLocation = location;
			rExtended.duplex = duplex;

			if (!matchToLeft) {

				if (rExtended.getMateAlignmentStart() == rExtended.getAlignmentStart()) {
					//Reads that completely overlap because of short insert size
					stats.nPosDuplexCompletePairOverlap.increment(location);
				}

				//Arbitrarily choose top strand as the one associated with
				//first of pair that maps to the lowest position in the contig
				if (!r.getFirstOfPairFlag()) {
					duplex.topStrandRecords.add(rExtended);
				} else {
					duplex.bottomStrandRecords.add(rExtended);
				}

				if (param.enableCostlyAssertions) {
					Assert.noException(duplex::assertAllBarcodesEqual);
				}

				duplex.rightAlignmentStart = sequenceLocationCache.intern(
					rExtended.getOffsetUnclippedStartLoc());
				duplex.rightAlignmentEnd = sequenceLocationCache.intern(
					rExtended.getOffsetUnclippedEndLoc());
				duplex.leftAlignmentStart = sequenceLocationCache.intern(
					rExtended.getMateOffsetUnclippedStartLoc());
				duplex.leftAlignmentEnd = sequenceLocationCache.intern(
					rExtended.getMateOffsetUnclippedEndLoc());
			} else {//Read on positive strand

				if (rExtended.getMateAlignmentStart() == rExtended.getAlignmentStart()) {
					//Reads that completely overlap because of short insert size?
					stats.nPosDuplexCompletePairOverlap.increment(location);
				}

				//Arbitrarily choose top strand as the one associated with
				//first of pair that maps to the lowest position in the contig
				if (r.getFirstOfPairFlag()) {
					duplex.topStrandRecords.add(rExtended);
				} else {
					duplex.bottomStrandRecords.add(rExtended);
				}

				if (param.enableCostlyAssertions) {
					Assert.noException(duplex::assertAllBarcodesEqual);
				}

				duplex.leftAlignmentStart = sequenceLocationCache.intern(
					rExtended.getOffsetUnclippedStartLoc());
				duplex.leftAlignmentEnd = sequenceLocationCache.intern(
					rExtended.getOffsetUnclippedEndLoc());
				duplex.rightAlignmentStart = sequenceLocationCache.intern(
					rExtended.getMateOffsetUnclippedStartLoc());
				duplex.rightAlignmentEnd = sequenceLocationCache.intern(
					rExtended.getMateOffsetUnclippedEndLoc());
			}

			duplexKeeper.add(duplex);

			if (false) //noinspection RedundantCast
				Assert.isFalse( /* There are funny alignments that can trigger this assert;
			but it should not hurt for alignment start and end to be switched, since it should happen
			in the same fashion for all reads  in a duplex  */
				duplex.leftAlignmentEnd.contigIndex == duplex.leftAlignmentStart.contigIndex &&
					duplex.leftAlignmentEnd.compareTo(duplex.leftAlignmentStart) < 0,
				(Supplier<Object>) duplex.leftAlignmentStart::toString,
				(Supplier<Object>) duplex.leftAlignmentEnd::toString,
				(Supplier<Object>) duplex::toString,
				(Supplier<Object>) rExtended::getFullName,
				"Misordered duplex: %s -- %s %s %s");
		}//End new duplex creation

		if (param.enableCostlyAssertions) {
			Duplex.checkNoEqualDuplexes(duplexKeeper);
		}

	}

	private static boolean checkReadsOccurOnceInDuplexes(
			Collection<@NonNull ExtendedSAMRecord> reads,
			@NonNull Iterable<Duplex> duplexes,
			int nReadsExcludedFromDuplexes) {
		ExtendedSAMRecord lostRead = null;
		int nUnfoundReads = 0;
		for (final @NonNull ExtendedSAMRecord rExtended: reads) {
			boolean found = false;
			for (Duplex dr: duplexes) {
				if (dr.topStrandRecords.contains(rExtended)) {
					if (found) {
						throw new AssertionFailedException("Two occurrences of read " + rExtended);
					}
					found = true;
				}
				if (dr.bottomStrandRecords.contains(rExtended)) {
					if (found) {
						throw new AssertionFailedException("Two occurrences of read " + rExtended);
					}
					found = true;
				}
			}
			if (!found) {
				nUnfoundReads++;
				lostRead = rExtended;
			}
		}
		if (nUnfoundReads != nReadsExcludedFromDuplexes) {
			throw new AssertionFailedException("Lost " + nUnfoundReads +
				" but expected " + nReadsExcludedFromDuplexes + "; see perhaps " + lostRead);
		}
		return true;
	}

	@NonNull LocationExaminationResults examineLocation(final @NonNull SequenceLocation location) {
		Assert.isFalse(threadCount.incrementAndGet() > 1);
		try {
			return examineLocation0(location);
		} finally {
			threadCount.decrementAndGet();
		}
	}

	@SuppressWarnings({"null", "ReferenceEquality"})
	/**
	 * This method is *NOT* thread-safe (it modifies DuplexReads associated with location retrieved
	 * from field candidateSequences)
	 * @param location
	 * @return
	 */
	@NonNull
	private LocationExaminationResults examineLocation0(final @NonNull SequenceLocation location) {
		final LocationExaminationResults result = new LocationExaminationResults(param);

		final THashSet<CandidateSequence> candidateSet0 = candidateSequences.get(location);
		if (candidateSet0 == null) {
			stats.nPosUncovered.increment(location);
			result.analyzedCandidateSequences = SortedSets.immutable.empty();
			return result;
		}
		MutableSortedSet<CandidateSequence> candidateSet =
				SortedSets.mutable.ofAll(CandidateSequence.reverseFrequencyComparator, candidateSet0);

		//Retrieve relevant duplex reads
		//It is necessary not to duplicate the duplex reads, hence the use of a set
		//Identity should be good enough (and is faster) because no two different duplex read
		//objects we use in this method should be equal according to the equals() method
		//(although when grouping duplexes we don't check equality for the inner ends of
		//the reads since they depend on read length)
		final TCustomHashSet<Duplex> duplexes =
			new TCustomHashSet<>(HashingStrategies.identityHashingStrategy, 200);

		candidateSet.forEach(candidate -> {
			candidate.reset();
			final Set<Duplex> candidateDuplexReads =
				new TCustomHashSet<>(HashingStrategies.identityHashingStrategy, 200);
			List<ExtendedSAMRecord> discarded = Lists.mutable.empty();
			candidate.getMutableConcurringReads().retainEntries((r, c) -> {
				if (r.discarded) {
					discarded.add(r);
					return false;
				}
				if (param.filterOpticalDuplicates) {
					if (r.isOpticalDuplicate()) {
						return false;
					}
				}
				@Nullable Duplex d = r.duplex;
				//noinspection StatementWithEmptyBody
				if (d != null) {
					candidateDuplexReads.add(d);
				} else {
					//throw new AssertionFailedException("Read without a duplex :" + r);
				}
				return true;
			});
			discarded.forEach(candidate.getMutableConcurringReads()::remove);
			if (param.enableCostlyAssertions) {
				checkDuplexes(candidateDuplexReads);
			}
			duplexes.addAll(candidateDuplexReads);
		});

		//Allocate here to avoid repeated allocation in DuplexRead::examineAtLoc
		final CandidateCounter topCounter = new CandidateCounter(candidateSet, location);
		final CandidateCounter bottomCounter = new CandidateCounter(candidateSet, location);

		int[] insertSizes = new int [duplexes.size()];
		SettableDouble averageCollisionProbS = new SettableDouble(0d);
		SettableInteger index = new SettableInteger(0);
		duplexes.forEach(duplexRead -> {
			Assert.isFalse(duplexRead.invalid);
			Assert.isTrue(duplexRead.averageNClipped >= 0, () -> duplexRead.toString());
			Assert.isTrue(param.variableBarcodeLength > 0 ||
				Double.isNaN(duplexRead.probAtLeastOneCollision) ||
				duplexRead.probAtLeastOneCollision >= 0);
			duplexRead.examineAtLoc(
				location,
				result,
				candidateSet,
				topCounter,
				bottomCounter,
				analyzer,
				param,
				stats);
			if (index.get() < insertSizes.length) {
				//Check in case array size was capped (for future use; it is
				//never capped currently)
				insertSizes[index.get()] = duplexRead.maxInsertSize;
				index.incrementAndGet();
			}

			averageCollisionProbS.addAndGet(duplexRead.probAtLeastOneCollision);
			if (param.variableBarcodeLength == 0 && !duplexRead.missingStrand) {
				stats.duplexCollisionProbabilityWhen2Strands.insert((int)
					(1_000f * duplexRead.probAtLeastOneCollision));
			}
		});

		if (param.enableCostlyAssertions) {
			Assert.noException(() -> checkDuplexAndCandidates(duplexes, candidateSet));
			candidateSet.forEach(candidate -> checkCandidateDupNoQ(candidate, location));
		}

		if (index.get() > 0) {
			Arrays.parallelSort(insertSizes, 0, index.get());
			result.duplexInsertSize10thP = insertSizes[(int) (index.get() * 0.1f)];
			result.duplexInsertSize90thP = insertSizes[(int) (index.get() * 0.9f)];
		}

		double averageCollisionProb = averageCollisionProbS.get();
		averageCollisionProb /= duplexes.size();
		if (param.variableBarcodeLength == 0) {
			stats.duplexCollisionProbability.insert((int) (1_000d * averageCollisionProb));
		}
		result.probAtLeastOneCollision = averageCollisionProb;

		final DetailedQualities<PositionAssay> positionQualities = new DetailedPositionQualities();

		if (averageClipping[location.position - averageClippingOffset] >
				param.maxAverageClippingOfAllCoveringDuplexes) {
			positionQualities.addUnique(
					MAX_AVERAGE_CLIPPING_OF_DUPLEX_AT_POS, DUBIOUS);
		}

		if (param.enableCostlyAssertions) {
			Assert.noException(() -> duplexes.forEach((Consumer<Duplex>) Duplex::checkReadOwnership));
		}

		final TByteArrayList allPhredQualitiesAtPosition = new TByteArrayList(500);
		final SettableInteger nWrongPairsAtPosition = new SettableInteger(0);
		final SettableInteger nPairsAtPosition = new SettableInteger(0);

		candidateSet.forEach(candidate -> {
			candidate.addPhredScoresToList(allPhredQualitiesAtPosition);
			nPairsAtPosition.addAndGet(candidate.getNonMutableConcurringReads().size());
			SettableInteger count = new SettableInteger(0);
			candidate.getNonMutableConcurringReads().forEachKey(r -> {
				if (r.formsWrongPair()) {
					count.incrementAndGet();
				}
				return true;
			});
			candidate.setnWrongPairs(count.get());
			nWrongPairsAtPosition.addAndGet(candidate.getnWrongPairs());
		});

		final int nPhredQualities = allPhredQualitiesAtPosition.size();
		allPhredQualitiesAtPosition.sort();
		final byte positionMedianPhred = nPhredQualities == 0 ? 127 :
			allPhredQualitiesAtPosition.get(nPhredQualities / 2);
		if (positionMedianPhred < param.minMedianPhredScoreAtPosition) {
			positionQualities.addUnique(MEDIAN_PHRED_AT_POS, DUBIOUS);
			stats.nMedianPhredAtPositionTooLow.increment(location);
		}
		stats.medianPositionPhredQuality.insert(positionMedianPhred);

		if (nWrongPairsAtPosition.get() / ((float) nPairsAtPosition.get()) > param.maxFractionWrongPairsAtPosition) {
			positionQualities.addUnique(FRACTION_WRONG_PAIRS_AT_POS, DUBIOUS);
			stats.nFractionWrongPairsAtPositionTooHigh.increment(location);
		}

		Handle<Byte> wildtypeBase = new Handle<>((byte) 'X');

		candidateSet.forEach(candidate -> {
			candidate.getQuality().addAllUnique(positionQualities);
			processCandidateQualityStep1(candidate, result, positionMedianPhred, positionQualities);
			if (candidate.getMutationType().isWildtype()) {
				wildtypeBase.set(candidate.getWildtypeSequence());
			}
		});

		Quality maxQuality;
		int totalGoodDuplexes, totalGoodOrDubiousDuplexes,
			totalGoodDuplexesIgnoringDisag, totalAllDuplexes;
		boolean leave = false;
		final boolean qualityOKBeforeTopAllele =
			Quality.nullableMax(positionQualities.getValue(true), GOOD).atLeast(GOOD);
		Optional<Quality> topAlleleQuality = Optional.empty();
		//noinspection UnnecessaryBoxing
		do {
			maxQuality = MINIMUM;
			totalAllDuplexes = 0;
			totalGoodDuplexes = 0;
			totalGoodOrDubiousDuplexes = 0;
			totalGoodDuplexesIgnoringDisag = 0;
			for (CandidateSequence candidate: candidateSet) {
				if (leave) {
					candidate.getQuality().addUnique(PositionAssay.TOP_ALLELE_FREQUENCY, DUBIOUS);
				}
				@NonNull MutableSetMultimap<Quality, Duplex> map = candidate.getDuplexes().
					groupBy(dr -> candidate.filterQuality(dr.localAndGlobalQuality));
				if (param.enableCostlyAssertions) {
					map.forEachKeyMultiValues((k, v) -> Assert.isTrue(Util.getDuplicates(v).isEmpty()));
					Assert.isTrue(map.multiValuesView().collectInt(RichIterable::size).sum() == candidate.getDuplexes().size());
				}
				@Nullable MutableSet<Duplex> gd = map.get(GOOD);
				candidate.setnGoodDuplexes(gd == null ? 0 : gd.size());
				@Nullable MutableSet<Duplex> db = map.get(DUBIOUS);
				candidate.setnGoodOrDubiousDuplexes(candidate.getnGoodDuplexes() + (db == null ? 0 : db.size()));
				candidate.setnGoodDuplexesIgnoringDisag(candidate.getDuplexes().
					count(dr -> dr.localAndGlobalQuality.getValueIgnoring(ASSAYS_TO_IGNORE_FOR_DISAGREEMENT_QUALITY).atLeast(GOOD)));

				maxQuality = max(candidate.getQuality().getValue(), maxQuality);
				totalAllDuplexes += candidate.getnDuplexes();
				totalGoodDuplexes += candidate.getnGoodDuplexes();
				totalGoodOrDubiousDuplexes += candidate.getnGoodOrDubiousDuplexes();
				totalGoodDuplexesIgnoringDisag += candidate.getnGoodDuplexesIgnoringDisag();

				processCandidateQualityStep2(candidate, location);
			}
			if (leave) {
				break;
			} else {
				result.nGoodOrDubiousDuplexes = totalGoodOrDubiousDuplexes;
				Assert.isFalse(topAlleleQuality.isPresent());
				topAlleleQuality = alleleFrequencyQuality(candidateSet, totalGoodOrDubiousDuplexes, param);
				if (!topAlleleQuality.isPresent()) {
					break;
				}
				Assert.isTrue(topAlleleQuality.get() == DUBIOUS);
				topAlleleQuality.ifPresent(q -> positionQualities.addUnique(PositionAssay.TOP_ALLELE_FREQUENCY, q));
				leave = true;//Just one more iteration
				//noinspection UnnecessaryContinue
				continue;
			}
		} while (true);

		if (qualityOKBeforeTopAllele) {
			registerDuplexMinFracTopCandidate(duplexes,
				topAlleleQuality == null ?
					stats.minTopCandFreqQ2PosTopAlleleFreqOK
				:
					stats.minTopCandFreqQ2PosTopAlleleFreqKO
				);
		}

		if (positionQualities.getValue(true) != null && positionQualities.getValue(true).lowerThan(GOOD)) {
			result.disagreements.clear();
			result.ignoreDisagreements = true;
		} else {
			if (param.maxMutFreqForDisag < 1f) {
				final int finalTotalGoodOrDubiousDuplexes = totalGoodOrDubiousDuplexes;
				result.disagreements.forEachKey(disag -> {
					Mutation m = disag.getSnd();
					if (!(lowMutFreq(m, candidateSet, finalTotalGoodOrDubiousDuplexes))) {
						disag.quality = Quality.min(disag.quality, POOR);
					}
					m = disag.getFst();
					if (m != null && m.mutationType != WILDTYPE) {
						if (!(lowMutFreq(m, candidateSet, finalTotalGoodOrDubiousDuplexes))) {
							disag.quality = Quality.min(disag.quality, POOR);
						}
					}
					return true;
				});
			}

			stats.nPosDuplexCandidatesForDisagreementQ2.acceptSkip0(location, result.disagQ2Coverage);
			stats.nPosDuplexCandidatesForDisagreementQ1.acceptSkip0(location, result.disagOneStrandedCoverage);
			if (param.computeRawMismatches) {
				candidateSet.forEach(c -> result.rawMismatchesQ2.addAllIterable(c.getRawMismatchesQ2()));
				candidateSet.forEach(c -> result.rawInsertionsQ2.addAllIterable(c.getRawInsertionsQ2()));
				candidateSet.forEach(c -> result.rawDeletionsQ2.addAllIterable(c.getRawDeletionsQ2()));
			}
		}

		final int totalReadsAtPosition = (int) candidateSet.sumOfInt(
			c -> c.getNonMutableConcurringReads().size());

		for (CandidateSequence candidate: candidateSet) {
			candidate.setTotalAllDuplexes(totalAllDuplexes);
			candidate.setTotalGoodDuplexes(totalGoodDuplexes);
			candidate.setTotalGoodOrDubiousDuplexes(totalGoodOrDubiousDuplexes);
			candidate.setTotalReadsAtPosition(totalReadsAtPosition);
			candidate.computeNBottomTopStrandReads();
		}

		result.nGoodOrDubiousDuplexes = totalGoodOrDubiousDuplexes;
		result.nGoodDuplexes = totalGoodDuplexes;
		result.nGoodDuplexesIgnoringDisag = totalGoodDuplexesIgnoringDisag;

		stats.Q1Q2DuplexCoverage.insert(result.nGoodOrDubiousDuplexes);
		stats.Q2DuplexCoverage.insert(result.nGoodDuplexes);

		if (result.nGoodOrDubiousDuplexes == 0) {
			stats.missingStrandsWhenNoUsableDuplex.insert(result.nMissingStrands);
			stats.strandCoverageImbalanceWhenNoUsableDuplex.insert(result.strandCoverageImbalance);
		}

		if (maxQuality.atMost(POOR)) {
			stats.nPosQualityPoor.increment(location);
			switch (wildtypeBase.get()) {
				case 'A' : stats.nPosQualityPoorA.increment(location); break;
				case 'T' : stats.nPosQualityPoorT.increment(location); break;
				case 'G' : stats.nPosQualityPoorG.increment(location); break;
				case 'C' : stats.nPosQualityPoorC.increment(location); break;
				default :
					break;//Ignore to keep things simple
			}
		} else if (maxQuality == DUBIOUS) {
			stats.nPosQualityQ1.increment(location);
		} else if (maxQuality == GOOD) {
			stats.nPosQualityQ2.increment(location);
		} else {
			throw new AssertionFailedException();
		}
		result.analyzedCandidateSequences = candidateSet;
		return result;
	}//End examineLocation

	private static void registerDuplexMinFracTopCandidate(TCustomHashSet<Duplex> duplexes, Histogram hist) {
		duplexes.forEach(dr -> {
			if (dr.allRecords.size() < 2 || dr.minFracTopCandidate == Float.MAX_VALUE) {
				return true;
			}
			hist.insert((int) (dr.minFracTopCandidate * 10));
			return true;
		});
	}

	private boolean lowMutFreq(Mutation mut, SetIterable<CandidateSequence> candidateSet, int nGOrDDuplexes) {
		Objects.requireNonNull(mut);
		Handle<Boolean> result = new Handle<>(true);
		candidateSet.detect((CandidateSequence c) -> {
			Mutation cMut = c.getMutation();
			if (cMut.equals(mut)) {
				if (c.getnGoodOrDubiousDuplexes() > param.maxMutFreqForDisag * nGOrDDuplexes) {
					result.set(false);
				}
				return true;
			}
			return false;
		});
		return result.get();
	}

	private static float divideWithNanToZero(float f1, float f2) {
		return f2 == 0f ? 0 : f1 / f2;
	}

	private final static Optional<Quality> OPTIONAL_DUBIOUS = Optional.of(DUBIOUS);

	private static Optional<Quality> alleleFrequencyQuality(
			MutableSortedSet<CandidateSequence> candidateSet,
			final int nGoodOrDubiousDuplexes,
			Parameters param) {
		candidateSet.forEach(c -> c.setFrequencyAtPosition(
				divideWithNanToZero(c.getnGoodOrDubiousDuplexes(), nGoodOrDubiousDuplexes)));

		//Make sure candidateSet is sorted after modification of
		//frequencyAtPosition above
		List<CandidateSequence> tempHolder = Arrays.asList(
			candidateSet.toArray(new CandidateSequence [candidateSet.size()]));
		candidateSet.clear();
		candidateSet.addAll(tempHolder);

		return LocationExaminationResults.getTopAlleleFrequency(candidateSet).flatMap(topFreq ->
			!(topFreq >= param.minTopAlleleFreqQ2 && topFreq <= param.maxTopAlleleFreqQ2) ?
					OPTIONAL_DUBIOUS
				:
					Optional.empty()
		);
	}

	private static void checkCandidateDupNoQ(CandidateSequence candidate, SequenceLocation location) {
		candidate.getNonMutableConcurringReads().forEachKey(r -> {
			Duplex dr = r.duplex;
			if (dr != null) {
				if (dr.lastExaminedPosition != location.position) {
					throw new AssertionFailedException("Last examined position is " + dr.lastExaminedPosition +
						" instead of " + location.position + " for duplex " + dr);
				}
				if (r.discarded) {
					throw new AssertionFailedException("Discarded read " + r + " in duplex " + dr);
				}
				Assert.isTrue(dr.localAndGlobalQuality.getQuality(QUALITY_AT_POSITION) == null);
				Assert.isFalse(dr.invalid);
			}
			return true;
		});
	}

	@SuppressWarnings("null")
	private void processCandidateQualityStep1(
			final CandidateSequence candidate,
			final LocationExaminationResults result,
			final byte positionMedianPhred,
			final @NonNull DetailedQualities<PositionAssay> positionQualities) {

		candidate.setMedianPhredAtPosition(positionMedianPhred);
		final byte candidateMedianPhred = candidate.getMedianPhredScore();//Will be -1 for insertions and deletions
		if (candidateMedianPhred != -1 && candidateMedianPhred < param.minCandidateMedianPhredScore) {
			candidate.getQuality().addUnique(MEDIAN_CANDIDATE_PHRED, DUBIOUS);
		}
		//TODO Should report min rather than average collision probability?
		candidate.setProbCollision((float) result.probAtLeastOneCollision);
		candidate.setInsertSizeAtPos10thP(result.duplexInsertSize10thP);
		candidate.setInsertSizeAtPos90thP(result.duplexInsertSize90thP);

		final MutableSet<Duplex> candidateDuplexes = candidate.computeSupportingDuplexes();
		candidate.setDuplexes(candidateDuplexes);
		candidate.setnDuplexes(candidateDuplexes.size());

		if (candidate.getnDuplexes() == 0) {
			candidate.getQuality().addUnique(NO_DUPLEXES, ATROCIOUS);
		}

		if (param.verbosity > 2) {
			candidateDuplexes.forEach(d -> candidate.getIssues().put(d, d.localAndGlobalQuality.toLong()));
		}

		final Quality posQMin =	positionQualities.getValue(true);
		Handle<Quality> maxDuplexQHandle = new Handle<>(ATROCIOUS);
		candidateDuplexes.each(dr -> {
			if (posQMin != null) {
				dr.localAndGlobalQuality.addUnique(QUALITY_AT_POSITION, posQMin);
			}
			maxDuplexQHandle.set(max(maxDuplexQHandle.get(),
				candidate.filterQuality(dr.localAndGlobalQuality)));
			});
		final @NonNull Quality maxDuplexQ = maxDuplexQHandle.get();

		candidate.updateQualities(param);

		switch(param.candidateQ2Criterion) {
			case "1Q2Duplex":
				candidate.getQuality().addUnique(MAX_Q_FOR_ALL_DUPLEXES, maxDuplexQ);
				break;
			case "NQ1Duplexes":
				int duplexCount = candidateDuplexes.count(d ->
					d.localAndGlobalQuality.getValueIgnoring(ASSAYS_TO_IGNORE_FOR_DUPLEX_NSTRANDS).atLeast(GOOD) &&
						d.allRecords.size() > 1 * 2);
				setNQ1DupQuality(candidate, duplexCount, param.minQ1Duplexes, param.minTotalReadsForNQ1Duplexes);
				break;
			default:
				throw new AssertionFailedException();
		}

		if (PositionAssay.COMPUTE_MAX_DPLX_Q_IGNORING_DISAG) {
			candidateDuplexes.stream().
				map(dr -> dr.localAndGlobalQuality.getValueIgnoring(ASSAYS_TO_IGNORE_FOR_DISAGREEMENT_QUALITY, true)).
				max(Quality::compareTo).ifPresent(q -> candidate.getQuality().addUnique(MAX_DPLX_Q_IGNORING_DISAG, q));
		}

		if (maxDuplexQ.atLeast(DUBIOUS)) {
			candidateDuplexes.forEach(dr -> {
				if (dr.localAndGlobalQuality.getValue().atLeast(maxDuplexQ)) {
					candidate.acceptLigSiteDistance(dr.getMaxDistanceToLigSite());
				}
			});
		}

	}

	private static void setNQ1DupQuality(CandidateSequence candidate, int duplexCount,
			int minQ1Duplexes, int minTotalReads) {
		boolean good = duplexCount >= minQ1Duplexes &&
			candidate.getNonMutableConcurringReads().size() >= minTotalReads;
		candidate.getQuality().addUnique(PositionAssay.N_Q1_DUPLEXES, good ? GOOD : DUBIOUS);
	}

	private void processCandidateQualityStep2(
			final CandidateSequence candidate,
			final @NonNull SequenceLocation location
		) {

		if (false && !param.rnaSeq) {
			candidate.getNonMutableConcurringReads().forEachKey(r -> {
				final int refPosition = location.position;
				final int readPosition = r.referencePositionToReadPosition(refPosition);
				if (!r.formsWrongPair()) {
					final int distance = r.tooCloseToBarcode(readPosition, 0);
					if (Math.abs(distance) > 160) {
						throw new AssertionFailedException("Distance problem with candidate " + candidate +
							" read at read position " + readPosition + " and refPosition " +
							refPosition + ' ' + r.toString() + " in analyzer" +
							analyzer.inputBam.getAbsolutePath() + "; distance is " + distance);
					}
					if (distance >= 0) {
						stats.singleAnalyzerQ2CandidateDistanceToLigationSite.insert(distance);
					} else {
						stats.Q2CandidateDistanceToLigationSiteN.insert(-distance);
					}
				}
				return true;
			});
		}

		if (candidate.getMutationType().isWildtype()) {
			candidate.setSupplementalMessage(null);
		} else if (candidate.getQuality().getNonNullValue().greaterThan(POOR)) {

			final StringBuilder supplementalMessage = new StringBuilder();
			final Map<String, Integer> stringCounts = new HashMap<>(100);

			candidate.getNonMutableConcurringReads().forEachKey(er -> {
				String other = er.record.getMateReferenceName();
				if (!er.record.getReferenceName().equals(other)) {
					String s = other + ':' + er.getMateAlignmentStart();
					int found = stringCounts.getOrDefault(s, 0);
					stringCounts.put(s, found + 1);
				}
				return true;
			});

			final Optional<String> mates = stringCounts.entrySet().stream().map(entry -> entry.getKey() +
				((entry.getValue() == 1) ? "" : (" (" + entry.getValue() + " repeats)")) + "; ").
				sorted().reduce(String::concat);

			final String hasMateOnOtherChromosome = mates.orElse("");

			IntSummaryStatistics insertSizeStats = candidate.getConcurringReadSummaryStatistics(er ->
				Math.abs(er.getInsertSize()));
			final int localMaxInsertSize = insertSizeStats.getMax();
			final int localMinInsertSize = insertSizeStats.getMin();

			candidate.setMinInsertSize(insertSizeStats.getMin());
			candidate.setMaxInsertSize(insertSizeStats.getMax());

			if (localMaxInsertSize < param.minInsertSize || localMinInsertSize > param.maxInsertSize) {
				candidate.getQuality().add(PositionAssay.INSERT_SIZE, DUBIOUS);
			}

			final NumberFormat nf = DoubleAdderFormatter.nf.get();

			@SuppressWarnings("null")
			final boolean hasNoMate = candidate.getNonMutableConcurringReads().forEachKey(
				er -> er.record.getMateReferenceName() != null);

			if (localMaxInsertSize > param.maxInsertSize) {
				supplementalMessage.append("one predicted insert size is " +
					nf.format(localMaxInsertSize)).append("; ");
			}

			if (localMinInsertSize < param.minInsertSize) {
				supplementalMessage.append("one predicted insert size is " +
					nf.format(localMinInsertSize)).append("; ");
			}

			IntSummaryStatistics mappingQualities = candidate.getConcurringReadSummaryStatistics(er ->
				er.record.getMappingQuality());
			candidate.setAverageMappingQuality((int) mappingQualities.getAverage());

			if (!"".equals(hasMateOnOtherChromosome)) {
				supplementalMessage.append("pair elements map to other chromosomes: " + hasMateOnOtherChromosome).append("; ");
			}

			if (hasNoMate) {
				supplementalMessage.append("at least one read has no mate nearby; ");
			}

			if ("".equals(hasMateOnOtherChromosome) && !hasNoMate && localMinInsertSize == 0) {
				supplementalMessage.append("at least one insert has 0 predicted size; ");
			}

			if (candidate.getnWrongPairs() > 0) {
				supplementalMessage.append(candidate.getnWrongPairs() + " wrong pairs; ");
			}

			candidate.setSupplementalMessage(supplementalMessage);
		}
	}

	@SuppressWarnings("ReferenceEquality")
	private static void checkDuplexes(Iterable<Duplex> duplexes) {
		for (Duplex duplex: duplexes) {
			duplex.allRecords.each(r -> {
				//noinspection ObjectEquality
				if (r.duplex != duplex) {
					throw new AssertionFailedException("Read " + r + " associated with duplexes " +
						r.duplex + " and " + duplex);
				}
			});
		}
		@NonNull Set<Duplex> duplicates = Util.getDuplicates(duplexes);
		if (!duplicates.isEmpty()) {
			throw new AssertionFailedException("Duplicate duplexes: " + duplicates);
		}
	}

	@SuppressWarnings("ReferenceEquality")
	private static void checkDuplexAndCandidates(Set<Duplex> duplexes,
			SetIterable<CandidateSequence> candidateSet) {

		checkDuplexes(duplexes);

		candidateSet.each(c -> {
			Assert.isTrue(c.getNonMutableConcurringReads().keySet().equals(
				c.getMutableConcurringReads().keySet()));

			Set<Duplex> duplexesSupportingC = c.computeSupportingDuplexes();
			candidateSet.each(c2 -> {
				Assert.isTrue(c.getNonMutableConcurringReads().keySet().equals(
					c.getMutableConcurringReads().keySet()));
				//noinspection ObjectEquality
				if (c2 == c) {
					return;
				}
				if (c2.equals(c)) {
					throw new AssertionFailedException();
				}
				c2.getNonMutableConcurringReads().keySet().forEach(r -> {
					//if (r.isOpticalDuplicate()) {
					//	return;
					//}
					Assert.isFalse(r.discarded);
					Duplex d = r.duplex;
					if (d != null && duplexesSupportingC.contains(d)) {
						boolean disowned = !d.allRecords.contains(r);

						throw new AssertionFailedException(disowned + " Duplex " + d +
							" associated with candidates " + c + " and " + c2);
					}
				});
			});
		});
	}

	private boolean checkConstantBarcode(byte[] bases, boolean allowN, int nAllowableMismatches) {
		if (nAllowableMismatches == 0 && !allowN) {
			//noinspection ArrayEquality
			return bases == analyzer.constantBarcode;//OK because of interning
		}
		int nMismatches = 0;
		for (int i = 0; i < analyzer.constantBarcode.length; i++) {
			if (!basesEqual(analyzer.constantBarcode[i], bases[i], allowN)) {
				nMismatches ++;
				if (nMismatches > nAllowableMismatches)
					return false;
			}
		}
		return true;
	}

	@NonNull ExtendedSAMRecord getExtended(@NonNull SAMRecord record, @NonNull SequenceLocation location) {
		final @NonNull String readFullName = ExtendedSAMRecord.getReadFullName(record, false);
		return extSAMCache.computeIfAbsent(readFullName, s ->
			new ExtendedSAMRecord(record, readFullName, analyzer.stats, analyzer, location, extSAMCache,
				param.filterOpticalDuplicates));
	}

	@SuppressWarnings("null")
	public static @NonNull ExtendedSAMRecord getExtendedNoCaching(@NonNull SAMRecord record,
			@NonNull SequenceLocation location, Mutinack analyzer, boolean parseReadNameForPosition) {
		final @NonNull String readFullName = ExtendedSAMRecord.getReadFullName(record, false);
		return
			new ExtendedSAMRecord(record, readFullName, Collections.emptyList(), analyzer, location, null,
				parseReadNameForPosition);
	}

	/**
	 *
	 * @return the furthest position in the contig covered by the read
	 */
	int processRead(
			final @NonNull SequenceLocation location,
			final @NonNull InterningSet<SequenceLocation> locationInterningSet,
			final @NonNull ExtendedSAMRecord extendedRec,
			final @NonNull ReferenceSequence ref) {

		Assert.isFalse(extendedRec.processed, "Double processing of record %s"/*,
		 extendedRec.getFullName()*/);
		extendedRec.processed = true;

		final SAMRecord rec = extendedRec.record;

		final int effectiveReadLength = extendedRec.effectiveLength;
		if (effectiveReadLength == 0) {
			return -1;
		}

		final CandidateBuilder readLocalCandidates = new CandidateBuilder(rec.getReadNegativeStrandFlag(),
			analyzer.codingStrandTester,
			param.enableCostlyAssertions ? null : (k, v) -> insertCandidateAtPosition(v, k));

		final int insertSize = extendedRec.getInsertSize();
		final int insertSizeAbs = Math.abs(insertSize);

		if (insertSizeAbs > param.maxInsertSize) {
			stats.nReadsInsertSizeAboveMaximum.increment(location);
			if (param.ignoreSizeOutOfRangeInserts) {
				return -1;
			}
		}

		if (insertSizeAbs < param.minInsertSize) {
			stats.nReadsInsertSizeBelowMinimum.increment(location);
			if (param.ignoreSizeOutOfRangeInserts) {
				return -1;
			}
		}

		final PairOrientation pairOrientation;

		if (rec.getReadPairedFlag() && !rec.getReadUnmappedFlag() && !rec.getMateUnmappedFlag() &&
			rec.getReferenceIndex().equals(rec.getMateReferenceIndex()) &&
			((pairOrientation = SamPairUtil.getPairOrientation(rec)) == PairOrientation.TANDEM ||
			pairOrientation == PairOrientation.RF)) {
				if (pairOrientation == PairOrientation.TANDEM) {
					stats.nReadsPairTandem.increment(location);
				} else if (pairOrientation == PairOrientation.RF) {
					stats.nReadsPairRF.increment(location);
				}
				if (param.ignoreTandemRFPairs) {
					return -1;
				}
		}

		final boolean readOnNegativeStrand = rec.getReadNegativeStrandFlag();

		if (!checkConstantBarcode(extendedRec.constantBarcode, false, param.nConstantBarcodeMismatchesAllowed)) {
			if (checkConstantBarcode(extendedRec.constantBarcode, true, param.nConstantBarcodeMismatchesAllowed)) {
				if (readOnNegativeStrand)
					stats.nConstantBarcodeDodgyNStrand.increment(location);
				else
					stats.nConstantBarcodeDodgy.increment(location);
				if (!param.acceptNInBarCode)
					return -1;
			} else {
				stats.nConstantBarcodeMissing.increment(location);
				return -1;
			}
		}
		stats.nReadsConstantBarcodeOK.increment(location);

		if (extendedRec.medianPhred < param.minReadMedianPhredScore) {
			stats.nReadMedianPhredBelowThreshold.accept(location);
			return -1;
		}

		stats.mappingQualityKeptRecords.insert(rec.getMappingQuality());

		SettableInteger refEndOfPreviousAlignment = new SettableInteger(-1);
		SettableInteger readEndOfPreviousAlignment = new SettableInteger(-1);
		SettableInteger returnValue = new SettableInteger(-1);

		if (insertSize == 0) {
			stats.nReadsInsertNoSize.increment(location);
			if (param.ignoreZeroInsertSizeReads) {
				return -1;
			}
		}

		if (rec.getMappingQuality() < param.minMappingQualityQ1) {
			return -1;
		}

		for (ExtendedAlignmentBlock block: extendedRec.getAlignmentBlocks()) {
			processAlignmentBlock(
				location,
				locationInterningSet,
				readLocalCandidates,
				ref,
				block,
				extendedRec,
				rec,
				readOnNegativeStrand,
				effectiveReadLength,
				refEndOfPreviousAlignment,
				readEndOfPreviousAlignment,
				returnValue);
		}

		if (param.enableCostlyAssertions) {
			readLocalCandidates.build().forEach((k, v) -> insertCandidateAtPosition(v, k));
		}

		return returnValue.get();
	}

	private void processAlignmentBlock(
			@NonNull SequenceLocation location,
			InterningSet<@NonNull SequenceLocation> locationInterningSet,
			final CandidateBuilder readLocalCandidates,
			final @NonNull ReferenceSequence ref,
			final ExtendedAlignmentBlock block,
			final @NonNull ExtendedSAMRecord extendedRec,
			final SAMRecord rec,
			final boolean readOnNegativeStrand,
			final int effectiveReadLength,
			final SettableInteger refEndOfPreviousAlignment0,
			final SettableInteger readEndOfPreviousAlignment0,
			final SettableInteger returnValue) {

		@SuppressWarnings("AnonymousInnerClassMayBeStatic")
		final CandidateFiller fillInCandidateInfo = new CandidateFiller() {
			@Override
			public void accept(CandidateSequence candidate) {
				candidate.setInsertSize(extendedRec.getInsertSize());
				candidate.setReadEL(effectiveReadLength);
				candidate.setReadName(extendedRec.getFullName());
				candidate.setReadAlignmentStart(extendedRec.getRefAlignmentStart());
				candidate.setMateReadAlignmentStart(extendedRec.getMateRefAlignmentStart());
				candidate.setReadAlignmentEnd(extendedRec.getRefAlignmentEnd());
				candidate.setMateReadAlignmentEnd(extendedRec.getMateRefAlignmentEnd());
				candidate.setRefPositionOfMateLigationSite(extendedRec.getRefPositionOfMateLigationSite());
			}

			@Override
			public void fillInPhred(CandidateSequence candidate, SequenceLocation location1, int readPosition) {
				final byte quality = rec.getBaseQualities()[readPosition];
				candidate.addBasePhredScore(quality);
				if (extendedRec.basePhredScores.put(location1, quality) !=
						ExtendedSAMRecord.PHRED_NO_ENTRY) {
					logger.warn("Recording Phred score multiple times at same position " + location1);
				}
			}
		};

		int refPosition = block.getReferenceStart() - 1;
		int readPosition = block.getReadStart() - 1;
		final int nBlockBases = block.getLength();

		final int refEndOfPreviousAlignment = refEndOfPreviousAlignment0.get();
		final int readEndOfPreviousAlignment = readEndOfPreviousAlignment0.get();

		returnValue.set(Math.max(returnValue.get(), refPosition + nBlockBases));

		/**
		 * When adding an insertion candidate, make sure that a wildtype or
		 * mismatch candidate is also inserted at the same position, even
		 * if it normally would not have been (for example because of low Phred
		 * quality). This should avoid awkward comparisons between e.g. an
		 * insertion candidate and a combo insertion + wildtype candidate.
		 */
		boolean forceCandidateInsertion = false;

		if (refEndOfPreviousAlignment != -1) {

			final boolean insertion = refPosition == refEndOfPreviousAlignment + 1;

			final boolean tooLate = (readOnNegativeStrand ?
				(insertion ? readPosition <= param.ignoreLastNBases : readPosition < param.ignoreLastNBases) :
				readPosition > rec.getReadLength() - param.ignoreLastNBases) && !param.rnaSeq;

			if (tooLate) {
				if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
					logger.info("Ignoring indel too close to end " + readPosition + (readOnNegativeStrand ? " neg strand " : " pos strand ") + readPosition + ' ' + (rec.getReadLength() - 1) + ' ' + extendedRec.getFullName());
				}
				stats.nCandidateIndelAfterLastNBases.increment(location);
			} else {
				if (insertion) {
					stats.nCandidateInsertions.increment(location);
					if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
						logger.info("Insertion at position " + readPosition + " for read " + rec.getReadName() +
							" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand);
					}
					forceCandidateInsertion = processInsertion(
						fillInCandidateInfo,
						readPosition,
						refPosition,
						readEndOfPreviousAlignment,
						refEndOfPreviousAlignment,
						locationInterningSet,
						readLocalCandidates,
						extendedRec,
						readOnNegativeStrand);
				}
				else if (refPosition < refEndOfPreviousAlignment + 1) {
					throw new AssertionFailedException("Alignment block misordering");
				} else {
					processDeletion(
						fillInCandidateInfo,
						location,
						ref,
						block,
						readPosition,
						refPosition,
						readEndOfPreviousAlignment,
						refEndOfPreviousAlignment,
						locationInterningSet,
						readLocalCandidates,
						extendedRec);
				}//End of deletion case
			}//End of case with accepted indel
		}//End of case where there was a previous alignment block

		refEndOfPreviousAlignment0.set(refPosition + (nBlockBases - 1));
		readEndOfPreviousAlignment0.set(readPosition + (nBlockBases - 1));

		final byte [] readBases = rec.getReadBases();
		final byte [] baseQualities = rec.getBaseQualities();

		for (int i = 0; i < nBlockBases; i++, readPosition++, refPosition++) {
			if (i == 1) {
				forceCandidateInsertion = false;
			}
			Handle<Boolean> insertCandidateAtRegularPosition = new Handle<>(true);
			final SequenceLocation locationPH =
				i < nBlockBases - 1 ? //No insertion or deletion; make a note of it
					SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
						param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refPosition, true)
				:
					null;

			location = SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
				param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refPosition);

			if (baseQualities[readPosition] < param.minBasePhredScoreQ1) {
				stats.nBasesBelowPhredScore.increment(location);
				if (forceCandidateInsertion) {
					insertCandidateAtRegularPosition.set(false);
				} else {
					continue;
				}
			}
			if (refPosition > ref.length() - 1) {
				logger.warn("Ignoring base mapped at " + refPosition + ", beyond the end of " + ref.getName());
				continue;
			}
			stats.nCandidateSubstitutionsConsidered.increment(location);
			byte wildType = StringUtil.toUpperCase(ref.getBases()[refPosition]);
			if (isMutation(wildType, readBases[readPosition])) {/*Mismatch*/

				final boolean tooLate = readOnNegativeStrand ? readPosition < param.ignoreLastNBases :
					readPosition > (rec.getReadLength() - 1) - param.ignoreLastNBases;

				boolean goodToInsert = checkSubstDistance(
						readPosition,
						location,
						tooLate,
						insertCandidateAtRegularPosition,
						extendedRec)
					&& !tooLate;

				if (goodToInsert || forceCandidateInsertion) {
					processSubstitution(
						fillInCandidateInfo,
						location,
						locationPH,
						readPosition,
						readLocalCandidates,
						extendedRec,
						readOnNegativeStrand,
						wildType,
						effectiveReadLength,
						forceCandidateInsertion,
						insertCandidateAtRegularPosition);
				}//End of mismatched read case
			} else {
				processWildtypeBase(
					fillInCandidateInfo,
					location,
					locationPH,
					readPosition,
					refPosition,
					wildType,
					readLocalCandidates,
					extendedRec,
					readOnNegativeStrand,
					forceCandidateInsertion,
					insertCandidateAtRegularPosition);
			}//End of wildtype case
		}//End of loop over alignment bases
	}

	private static boolean isMutation(byte ucReferenceBase, byte ucReadBase) {
		Assert.isTrue(ucReferenceBase < 91);//Assert base is upper case
		Assert.isTrue(ucReadBase < 91);
		switch (ucReferenceBase) {
			case 'Y': return ucReadBase != 'C' && ucReadBase != 'T';
			case 'R': return ucReadBase != 'G' && ucReadBase != 'A';
			case 'W': return ucReadBase != 'A' && ucReadBase != 'T';
			default: return ucReadBase != ucReferenceBase;
		}
	}

	private interface CandidateFiller {
		void accept(CandidateSequence candidate);
		void fillInPhred(CandidateSequence candidate, SequenceLocation location, int readPosition);
	}

	private boolean checkSubstDistance(
			int readPosition,
			@NonNull SequenceLocation location,
			boolean tooLate,
			Handle<Boolean> insertCandidateAtRegularPosition,
			ExtendedSAMRecord extendedRec) {
		int distance = extendedRec.tooCloseToBarcode(readPosition, param.ignoreFirstNBasesQ1);
		if (distance >= 0) {
			if (!extendedRec.formsWrongPair()) {
				distance = extendedRec.tooCloseToBarcode(readPosition, 0);
				if (distance <= 0 && insertCandidateAtRegularPosition.get()) {
					stats.rejectedSubstDistanceToLigationSite.insert(-distance);
					stats.nCandidateSubstitutionsBeforeFirstNBases.increment(location);
				}
			}
			if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
				logger.info("Ignoring subst too close to barcode for read " + extendedRec.getFullName());
			}
			insertCandidateAtRegularPosition.set(false);
		} else if (tooLate && insertCandidateAtRegularPosition.get()) {
			stats.nCandidateSubstitutionsAfterLastNBases.increment(location);
			if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
				logger.info("Ignoring subst too close to read end for read " + extendedRec.getFullName());
			}
			insertCandidateAtRegularPosition.set(false);
		}
		return distance < 0;
	}

	private void processWildtypeBase(
		final CandidateFiller candidateFiller,
		final @NonNull SequenceLocation location,
		final @Nullable SequenceLocation locationPH,
		final int readPosition,
		final int refPosition,
		byte wildType,
		final CandidateBuilder readLocalCandidates,
		final @NonNull ExtendedSAMRecord extendedRec,
		final boolean readOnNegativeStrand,
		final boolean forceCandidateInsertion,
		final Handle<Boolean> insertCandidateAtRegularPosition) {

		int distance = extendedRec.tooCloseToBarcode(readPosition, param.ignoreFirstNBasesQ1);
		if (distance >= 0) {
			if (!extendedRec.formsWrongPair()) {
				distance = extendedRec.tooCloseToBarcode(readPosition, 0);
				if (distance <= 0 && insertCandidateAtRegularPosition.get()) {
					stats.wtRejectedDistanceToLigationSite.insert(-distance);
				}
			}
			if (!forceCandidateInsertion) {
				return;
			} else {
				insertCandidateAtRegularPosition.set(false);
			}
		} else {
			if (!extendedRec.formsWrongPair() && distance < -150) {
				throw new AssertionFailedException("Distance problem 1 at read position " + readPosition +
					" and refPosition " + refPosition + ' ' + extendedRec.toString() +
					" in analyzer" + analyzer.inputBam.getAbsolutePath() +
					"; distance is " + distance + "");
			}
			distance = extendedRec.tooCloseToBarcode(readPosition, 0);
			if (!extendedRec.formsWrongPair() && insertCandidateAtRegularPosition.get()) {
				stats.wtAcceptedBaseDistanceToLigationSite.insert(-distance);
			}
		}

		if (((!readOnNegativeStrand && readPosition > extendedRec.record.getReadLength() - 1 - param.ignoreLastNBases) ||
			(readOnNegativeStrand && readPosition < param.ignoreLastNBases))) {
			if (insertCandidateAtRegularPosition.get()) {
				stats.nCandidateWildtypeAfterLastNBases.increment(location);
			}
			if (!forceCandidateInsertion) {
				return;
			} else {
				insertCandidateAtRegularPosition.set(false);
			}
		}
		CandidateSequence candidate = new CandidateSequence(this,
			WILDTYPE, null, location, extendedRec, -distance);
		if (!extendedRec.formsWrongPair()) {
			candidate.acceptLigSiteDistance(-distance);
		}
		candidate.setWildtypeSequence(wildType);
		candidateFiller.fillInPhred(candidate, location, readPosition);
		if (insertCandidateAtRegularPosition.get()) {
			//noinspection UnusedAssignment
			candidate = readLocalCandidates.add(candidate, location);
			//noinspection UnusedAssignment
			candidate = null;
		}
		if (locationPH != null) {
			CandidateSequence candidate2 = new CandidateSequence(this,
				WILDTYPE, null, locationPH, extendedRec, -distance);
			if (!extendedRec.formsWrongPair()) {
				candidate2.acceptLigSiteDistance(-distance);
			}
			candidate2.setWildtypeSequence(wildType);
			//noinspection UnusedAssignment
			candidate2 = readLocalCandidates.add(candidate2, locationPH);
			//noinspection UnusedAssignment
			candidate2 = null;
		}
	}

	private void processSubstitution(
			final CandidateFiller candidateFiller,
			final @NonNull SequenceLocation location,
			final @Nullable SequenceLocation locationPH,
			final int readPosition,
			final CandidateBuilder readLocalCandidates,
			final @NonNull ExtendedSAMRecord extendedRec,
			final boolean readOnNegativeStrand,
			final byte wildType,
			final int effectiveReadLength,
			final boolean forceCandidateInsertion,
			final Handle<Boolean> insertCandidateAtRegularPosition) {

		if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
			logger.info("Substitution at position " + readPosition + " for read " + extendedRec.record.getReadName() +
				" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand +
				"; insert size: " + extendedRec.getInsertSize() + ')');
		}

		final byte mutation = extendedRec.record.getReadBases()[readPosition];
		final byte mutationUC = StringUtil.toUpperCase(mutation);
		switch (mutationUC) {
			case 'A':
				stats.nCandidateSubstitutionsToA.increment(location); break;
			case 'T':
				stats.nCandidateSubstitutionsToT.increment(location); break;
			case 'G':
				stats.nCandidateSubstitutionsToG.increment(location); break;
			case 'C':
				stats.nCandidateSubstitutionsToC.increment(location); break;
			case 'N':
				stats.nNs.increment(location);
				if (!forceCandidateInsertion) {
					return;
				} else {
					insertCandidateAtRegularPosition.set(false);
				}
				break;
			default:
				throw new AssertionFailedException("Unexpected letter: " + mutationUC);
		}

		final int distance = -extendedRec.tooCloseToBarcode(readPosition, 0);

		CandidateSequence candidate = new CandidateSequence(this,
			SUBSTITUTION, byteArrayMap.get(mutation), location, extendedRec, distance);

		if (!extendedRec.formsWrongPair()) {
			candidate.acceptLigSiteDistance(distance);
		}
		candidateFiller.accept(candidate);
		candidate.setPositionInRead(readPosition);
		candidate.setWildtypeSequence(wildType);
		candidateFiller.fillInPhred(candidate, location, readPosition);
		extendedRec.nReferenceDisagreements++;
		if (param.computeRawMismatches && insertCandidateAtRegularPosition.get()) {
			registerRawMismatch(location, extendedRec, readPosition,
				distance, candidate.getMutableRawMismatchesQ2(), stats.rawMismatchesQ1,
				getFromByteMap(wildType, readOnNegativeStrand), getFromByteMap(mutationUC, readOnNegativeStrand));
		}
		if (insertCandidateAtRegularPosition.get()) {
			//noinspection UnusedAssignment
			candidate = readLocalCandidates.add(candidate, location);
			//noinspection UnusedAssignment
			candidate = null;
		}
		if (locationPH != null) {
			CandidateSequence candidate2 = new CandidateSequence(this,
				WILDTYPE, null, locationPH, extendedRec, distance);
			if (!extendedRec.formsWrongPair()) {
				candidate2.acceptLigSiteDistance(distance);
			}
			candidate2.setWildtypeSequence(wildType);
			//noinspection UnusedAssignment
			candidate2 = readLocalCandidates.add(candidate2, locationPH);
			//noinspection UnusedAssignment
			candidate2 = null;
		}
	}

	public static @NonNull String getFromByteMap(byte b, boolean reverseComplement) {
		String result = reverseComplement ? byteMap.get(Mutation.complement(b)) : byteMap.get(b);
		if (Util.nullableify(result) == null) {
			throw new AssertionFailedException("Could not find " + new String(new byte[] {b}) + " " +
				reverseComplement);
		}
		return result;
	}

	private void registerRawMismatch(
			final @NonNull SequenceLocation location,
			final @NonNull ExtendedSAMRecord extendedRec,
			final int readPosition,
			final int distance,
			final Collection<ComparablePair<String, String>> mismatches,
			final MultiCounter<ComparablePair<String, String>> q1Stats,
			final @NonNull String wildType,
			final @NonNull String mutation) {

		final ComparablePair<String, String> mutationPair = new ComparablePair<>(wildType, mutation);
		q1Stats.accept(location, mutationPair);
		if (meetsQ2Thresholds(extendedRec) &&
			extendedRec.record.getBaseQualities()[readPosition] >= param.minBasePhredScoreQ2 &&
				!extendedRec.formsWrongPair() && distance > param.ignoreFirstNBasesQ2) {
			mismatches.add(mutationPair);
		}

	}

	private static @NonNull String toUpperCase(byte @NonNull [] deletedSequence, boolean readOnNegativeStrand) {
		@NonNull String s = new String(deletedSequence).toUpperCase();
		return readOnNegativeStrand ? Mutation.reverseComplement(s) : s;
	}

	//Deletion or skipped region ("N" in Cigar)
	private void processDeletion(
		final CandidateFiller candidateFiller,
		final @NonNull SequenceLocation location,
		final @NonNull ReferenceSequence ref,
		final ExtendedAlignmentBlock block,
		final int readPosition,
		final int refPosition,
		final int readEndOfPreviousAlignment,
		final int refEndOfPreviousAlignment,
		final InterningSet<@NonNull SequenceLocation> locationInterningSet,
		final CandidateBuilder readLocalCandidates,
		final @NonNull ExtendedSAMRecord extendedRec) {

		if (refPosition > ref.length() - 1) {
			logger.warn("Ignoring rest of read after base mapped at " + refPosition +
				", beyond the end of " + ref.getName());
			return;
		}

		int distance0 = -extendedRec.tooCloseToBarcode(readPosition - 1, param.ignoreFirstNBasesQ1);
		int distance1 = -extendedRec.tooCloseToBarcode(readEndOfPreviousAlignment + 1, param.ignoreFirstNBasesQ1);
		int distance = Math.min(distance0, distance1) + 1;

		final boolean isIntron = block.previousCigarOperator == CigarOperator.N;

		final boolean Q1reject = distance < 0;

		if (!isIntron && Q1reject) {
			if (!extendedRec.formsWrongPair()) {
				stats.rejectedIndelDistanceToLigationSite.insert(-distance);
				stats.nCandidateIndelBeforeFirstNBases.increment(location);
			}
			logger.trace("Ignoring deletion " + readEndOfPreviousAlignment + param.ignoreFirstNBasesQ1 + ' ' + extendedRec.getFullName());
		} else {
			distance0 = -extendedRec.tooCloseToBarcode(readPosition - 1, 0);
			distance1 = -extendedRec.tooCloseToBarcode(readEndOfPreviousAlignment + 1, 0);
			distance = Math.min(distance0, distance1) + 1;

			if (!isIntron) stats.nCandidateDeletions.increment(location);
			if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
				logger.info("Deletion or intron at position " + readPosition + " for read " + extendedRec.record.getReadName());
			}

			final int deletionLength = refPosition - (refEndOfPreviousAlignment + 1);
			final @NonNull SequenceLocation newLocation =
				SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
					param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refEndOfPreviousAlignment + 1);
			final @NonNull SequenceLocation deletionEnd = SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
				param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), newLocation.position + deletionLength);

			final byte @Nullable[] deletedSequence = isIntron ? null :
				Arrays.copyOfRange(ref.getBases(), refEndOfPreviousAlignment + 1, refPosition);

			//Add hidden mutations to all locations covered by deletion
			//So disagreements between deletions that have only overlapping
			//spans are detected.
			if (!isIntron) {
				for (int i = 1; i < deletionLength; i++) {
					SequenceLocation location2 = SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
						param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refEndOfPreviousAlignment + 1 + i);
					CandidateSequence hiddenCandidate = new CandidateDeletion(
						this, deletedSequence, location2, extendedRec, Integer.MAX_VALUE, MutationType.DELETION,
						newLocation, deletionEnd);
					candidateFiller.accept(hiddenCandidate);
					hiddenCandidate.setHidden(true);
					hiddenCandidate.setPositionInRead(readPosition);
					//noinspection UnusedAssignment
					hiddenCandidate = readLocalCandidates.add(hiddenCandidate, location2);
					//noinspection UnusedAssignment
					hiddenCandidate = null;
				}
			}

			CandidateSequence candidate = new CandidateDeletion(this,
				deletedSequence, newLocation, extendedRec, distance, isIntron ? MutationType.INTRON : MutationType.DELETION,
				newLocation, SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
					param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refPosition));

			if (!extendedRec.formsWrongPair()) {
				candidate.acceptLigSiteDistance(distance);
			}

			candidateFiller.accept(candidate);
			candidate.setPositionInRead(readPosition);
			if (!isIntron) extendedRec.nReferenceDisagreements++;

			if (!isIntron && param.computeRawMismatches) {
				Objects.requireNonNull(deletedSequence);
				stats.rawDeletionLengthQ1.insert(deletedSequence.length);

				final boolean negativeStrand = extendedRec.getReadNegativeStrandFlag();
				registerRawMismatch(newLocation, extendedRec, readPosition,
					distance, candidate.getMutableRawDeletionsQ2(), stats.rawDeletionsQ1,
					getFromByteMap(deletedSequence[0], negativeStrand),
					toUpperCase(deletedSequence, negativeStrand));

			}
			//noinspection UnusedAssignment
			candidate = readLocalCandidates.add(candidate, newLocation);
			//noinspection UnusedAssignment
			candidate = null;
		}
	}

	private boolean processInsertion(
			final CandidateFiller candidateFiller,
			final int readPosition,
			final int refPosition,
			final int readEndOfPreviousAlignment,
			final int refEndOfPreviousAlignment,
			final InterningSet<@NonNull SequenceLocation> locationInterningSet,
			final CandidateBuilder readLocalCandidates,
			final @NonNull ExtendedSAMRecord extendedRec,
			final boolean readOnNegativeStrand) {

		final @NonNull SequenceLocation location = SequenceLocation.get(locationInterningSet, extendedRec.getLocation().contigIndex,
			param.referenceGenomeShortName, extendedRec.getLocation().getContigName(), refEndOfPreviousAlignment, true);
		int distance0 = extendedRec.tooCloseToBarcode(readEndOfPreviousAlignment, param.ignoreFirstNBasesQ1);
		int distance1 = extendedRec.tooCloseToBarcode(readPosition, param.ignoreFirstNBasesQ1);
		int distance = Math.max(distance0, distance1);
		distance = -distance + 1;

		final boolean Q1reject = distance < 0;
		if (Q1reject) {
			if (!extendedRec.formsWrongPair()) {
				stats.rejectedIndelDistanceToLigationSite.insert(-distance);
				stats.nCandidateIndelBeforeFirstNBases.increment(location);
			}
			logger.trace("Ignoring insertion " + readEndOfPreviousAlignment + param.ignoreFirstNBasesQ1 + ' ' + extendedRec.getFullName());
			return false;
		}
		distance0 = extendedRec.tooCloseToBarcode(readEndOfPreviousAlignment, 0);
		distance1 = extendedRec.tooCloseToBarcode(readPosition, 0);
		distance = Math.max(distance0, distance1);
		distance = -distance + 1;

		final byte [] readBases = extendedRec.record.getReadBases();
		final byte @NonNull [] insertedSequence = Arrays.copyOfRange(readBases,
			readEndOfPreviousAlignment + 1, readPosition);

		CandidateSequence candidate = new CandidateSequence(
			this,
			INSERTION,
			insertedSequence,
			location,
			extendedRec,
			distance);

		if (!extendedRec.formsWrongPair()) {
			candidate.acceptLigSiteDistance(distance);
		}

		candidateFiller.accept(candidate);
		candidate.setPositionInRead(readPosition);

		if (param.computeRawMismatches) {
			stats.rawInsertionLengthQ1.insert(insertedSequence.length);
			registerRawMismatch(location, extendedRec, readPosition,
				distance, candidate.getMutableRawInsertionsQ2(), stats.rawInsertionsQ1,
				getFromByteMap(readBases[readEndOfPreviousAlignment], readOnNegativeStrand),
				toUpperCase(insertedSequence, readOnNegativeStrand));
		}

		if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
			logger.info("Insertion of " + new String(candidate.getSequence()) + " at ref " + refPosition + " and read position " + readPosition + " for read " + extendedRec.getFullName());
		}
		//noinspection UnusedAssignment
		candidate = readLocalCandidates.add(candidate, location);
		//noinspection UnusedAssignment
		candidate = null;
		if (DebugLogControl.shouldLog(TRACE, logger, param, location)) {
			logger.info("Added candidate at " + location /*+ "; readLocalCandidates now " + readLocalCandidates.build()*/);
		}
		extendedRec.nReferenceDisagreements++;

		return true;
	}

	public @NonNull Mutinack getAnalyzer() {
		return analyzer;
	}

}
