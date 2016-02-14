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

import static contrib.uk.org.lidalia.slf4jext.Level.TRACE;
import static uk.org.cinquin.mutinack.Assay.DISAGREEMENT;
import static uk.org.cinquin.mutinack.Assay.INSERT_SIZE;
import static uk.org.cinquin.mutinack.Assay.MAX_AVERAGE_CLIPPING_ALL_COVERING_DUPLEXES;
import static uk.org.cinquin.mutinack.Assay.MAX_DPLX_Q_IGNORING_DISAG;
import static uk.org.cinquin.mutinack.Assay.MAX_Q_FOR_ALL_DUPLEXES;
import static uk.org.cinquin.mutinack.Assay.NO_DUPLEXES;
import static uk.org.cinquin.mutinack.MutationType.INSERTION;
import static uk.org.cinquin.mutinack.MutationType.SUBSTITUTION;
import static uk.org.cinquin.mutinack.MutationType.WILDTYPE;
import static uk.org.cinquin.mutinack.Quality.ATROCIOUS;
import static uk.org.cinquin.mutinack.Quality.DUBIOUS;
import static uk.org.cinquin.mutinack.Quality.GOOD;
import static uk.org.cinquin.mutinack.Quality.MAXIMUM;
import static uk.org.cinquin.mutinack.Quality.MINIMUM;
import static uk.org.cinquin.mutinack.Quality.POOR;
import static uk.org.cinquin.mutinack.Quality.max;
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.misc_util.collections.TroveSetCollector.uniqueValueCollector;

import java.io.PrintStream;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.samtools.AlignmentBlock;
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
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateBuilder;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateCounter;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateDeletion;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.collections.IdentityHashSet;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public final class SubAnalyzer {
	private static final Logger logger = LoggerFactory.getLogger(SubAnalyzer.class);
	
	final @NonNull Mutinack analyzer;
	AnalysisStats stats;
	final @NonNull SettableInteger lastProcessablePosition = new SettableInteger(-1);	
	final @NonNull Map<SequenceLocation, THashSet<CandidateSequence>> candidateSequences =
			new THashMap<>(50_000);	
	int truncateProcessingAt = Integer.MAX_VALUE;
	int startProcessingAt = 0;
	List<@NonNull DuplexRead> analyzedDuplexes;
	final @NonNull Map<String, @NonNull ExtendedSAMRecord> extSAMCache = 
			new THashMap<>(50_000, 0.1f);
	private final AtomicInteger threadCount = new AtomicInteger();

	@SuppressWarnings("null")
	private static final @NonNull Set<@NonNull Assay> assaysToIgnoreForDisagreementQuality 
		= Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(DISAGREEMENT)));
	
	static final @NonNull TByteObjectMap<@NonNull String> byteMap;
	static {
		byteMap = new TByteObjectHashMap<>();
		byteMap.put((byte) 'A', "A");
		byteMap.put((byte) 'a', "A");
		byteMap.put((byte) 'T', "T");
		byteMap.put((byte) 't', "T");
		byteMap.put((byte) 'G', "G");
		byteMap.put((byte) 'g', "G");
		byteMap.put((byte) 'C', "C");
		byteMap.put((byte) 'c', "C");
		byteMap.put((byte) 'N', "N");
		byteMap.put((byte) 'n', "N");
	}
		
	SubAnalyzer(@NonNull Mutinack analyzer, PrintStream out) {
		this.analyzer = analyzer;
		useHashMap = analyzer.alignmentPositionMismatchAllowed == 0;
	}
	
	private boolean meetsQ2Thresholds(@NonNull ExtendedSAMRecord extendedRec) {
		return !extendedRec.formsWrongPair() &&
				extendedRec.getnClipped() <= analyzer.maxAverageBasesClipped &&
				extendedRec.getMappingQuality() >= analyzer.minMappingQualityQ2 &&
				Math.abs(extendedRec.getInsertSize()) <= analyzer.maxInsertSize;
	}

	/**
	 * Make sure that concurring reads get associated with a unique candidate.
	 * We should *not* insert candidates directly into candidateSequences.
	 * Get interning as a small, beneficial side effect.
	 * @param candidate
	 * @param location
	 * @return
	 */
	private CandidateSequence insertCandidateAtPosition(@NonNull CandidateSequence candidate, 
			@NonNull SequenceLocation location) {

		THashSet<CandidateSequence> candidates = candidateSequences.get(location);
		if (candidates == null) {//No need for synchronization since we should not be
			//concurrently inserting two candidates in the same position
			candidates = new THashSet<>();
			candidateSequences.put(location, candidates);
		}
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
	 */
	void load() {
		Assert.isFalse(threadCount.incrementAndGet() > 1);
		try {
			final List<@NonNull DuplexRead> resultDuplexes = new ArrayList<>(10_000);
			loadAll(resultDuplexes);
			analyzedDuplexes = resultDuplexes;
		} finally {
			if (NONTRIVIAL_ASSERTIONS) {
				threadCount.decrementAndGet();
			}
		}
	}
	
	public void checkAllDone() {
		if (!candidateSequences.isEmpty()) {
			final SettableInteger nLeftBehind = new SettableInteger(-1);
			candidateSequences.forEach((k,v) -> {
				Assert.isTrue(v.isEmpty() || (v.iterator().next().getLocation().equals(k)),
						"Mimatched locations");

				String s = v.stream().
						filter(c -> c.getLocation().position > truncateProcessingAt + analyzer.maxInsertSize &&
								c.getLocation().position < startProcessingAt - analyzer.maxInsertSize).
						flatMap(v0 -> v0.getNonMutableConcurringReads().keySet().stream()).
						map(read -> {
							if (nLeftBehind.incrementAndGet() == 0) {
								logger.error("Sequences left behind before " + truncateProcessingAt);
							}
							return Integer.toString(read.record.getAlignmentStart()) + "-" +
								Integer.toString(read.record.getAlignmentEnd()); })
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
	 * @param finalResult
	 */
	private void loadAll(@NonNull List<DuplexRead> finalResult) {

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
				
		final AlignmentExtremetiesDistance ed = new AlignmentExtremetiesDistance(
				analyzer.groupSettings);
		
		int nReadsExcludedFromDuplexes = 0;
		
		for (final @NonNull ExtendedSAMRecord rExtended: extSAMCache.values()) {

			final @NonNull SequenceLocation location = new SequenceLocation(rExtended.record);

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
				nReadsExcludedFromDuplexes++;
				continue;
			}

			boolean foundDuplexRead = false;
			final boolean matchToLeft = rExtended.duplexLeft();
			
			ed.set(rExtended);

			for (final DuplexRead duplexRead: duplexKeeper.getOverlapping(ed.temp)) {
				//stats.nVariableBarcodeCandidateExaminations.increment(location);

				ed.set(duplexRead);

				if (ed.getMaxDistance() > analyzer.alignmentPositionMismatchAllowed) {
					continue;
				}

				final boolean barcodeMatch;
				//During first pass, do not allow any barcode mismatches
				if (matchToLeft) {
					barcodeMatch = basesEqual(duplexRead.leftBarcode, barcode, 
							analyzer.acceptNInBarCode, 0) &&
							basesEqual(duplexRead.rightBarcode, mateBarcode, 
									analyzer.acceptNInBarCode, 0);
				} else {
					barcodeMatch = basesEqual(duplexRead.leftBarcode, mateBarcode, 
							analyzer.acceptNInBarCode, 0) &&
							basesEqual(duplexRead.rightBarcode, barcode, 
									analyzer.acceptNInBarCode, 0);
				}

				if (barcodeMatch) {
					if (r.getInferredInsertSize() >= 0) {
						if (r.getFirstOfPairFlag()) {
							Assert.isFalse(duplexRead.topStrandRecords.contains(rExtended));
							duplexRead.topStrandRecords.add(rExtended);
						} else {
							Assert.isFalse(duplexRead.bottomStrandRecords.contains(rExtended));
							duplexRead.bottomStrandRecords.add(rExtended);
						}
					} else {
						if (r.getFirstOfPairFlag()) {
							Assert.isFalse(duplexRead.bottomStrandRecords.contains(rExtended));
							duplexRead.bottomStrandRecords.add(rExtended);
						} else {
							Assert.isFalse(duplexRead.topStrandRecords.contains(rExtended));
							duplexRead.topStrandRecords.add(rExtended);
						}
					}
					Assert.noException(duplexRead::assertAllBarcodesEqual);
					rExtended.duplexRead = duplexRead;
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
				final DuplexRead duplexRead = matchToLeft ?
						new DuplexRead(analyzer.groupSettings, barcode, mateBarcode, !r.getReadNegativeStrandFlag(), r.getReadNegativeStrandFlag()) :
						new DuplexRead(analyzer.groupSettings, mateBarcode, barcode, r.getReadNegativeStrandFlag(), !r.getReadNegativeStrandFlag());
				if (matchToLeft) {
					duplexRead.setPositions(
							rExtended.getUnclippedStart(),
							rExtended.getMateUnclippedEnd());
				} else {
					duplexRead.setPositions(
							rExtended.getMateUnclippedStart(),
							rExtended.getUnclippedEnd());
				}
				
				duplexKeeper.add(duplexRead);

				duplexRead.roughLocation = location;
				rExtended.duplexRead = duplexRead;

				if (!matchToLeft) {

					if (rExtended.getMateAlignmentStart() == rExtended.getAlignmentStart()) {
						//Reads that completely overlap because of short insert size
						stats.nPosDuplexCompletePairOverlap.increment(location);
					}

					//Arbitrarily choose top strand as the one associated with
					//first of pair that maps to the lowest position in the contig
					if (!r.getFirstOfPairFlag()) {
						duplexRead.topStrandRecords.add(rExtended);
					} else {
						duplexRead.bottomStrandRecords.add(rExtended);
					}
					
					Assert.noException(duplexRead::assertAllBarcodesEqual);

					duplexRead.rightAlignmentStart = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getUnclippedStart());
					duplexRead.rightAlignmentEnd =  new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getUnclippedEnd());
					duplexRead.leftAlignmentStart = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getMateUnclippedStart());
					duplexRead.leftAlignmentEnd = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getMateUnclippedEnd());					
				} else {//Read on positive strand

					if (rExtended.getMateAlignmentStart() == rExtended.getAlignmentStart()) {
						//Reads that completely overlap because of short insert size?
						stats.nPosDuplexCompletePairOverlap.increment(location);
					} 

					//Arbitrarily choose top strand as the one associated with
					//first of pair that maps to the lowest position in the contig
					if (r.getFirstOfPairFlag()) {
						duplexRead.topStrandRecords.add(rExtended);
					} else {
						duplexRead.bottomStrandRecords.add(rExtended);
					}
					
					Assert.noException(duplexRead::assertAllBarcodesEqual);

					duplexRead.leftAlignmentStart = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getUnclippedStart());
					duplexRead.leftAlignmentEnd = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getUnclippedEnd());
					duplexRead.rightAlignmentStart = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getMateUnclippedStart());
					duplexRead.rightAlignmentEnd = new SequenceLocation(r.getReferenceIndex(),
							r.getReferenceName(), rExtended.getMateUnclippedEnd());
				}

				Assert.isFalse(
					duplexRead.leftAlignmentEnd.compareTo(duplexRead.leftAlignmentStart) < 0,
					"Misordered duplex: %s -- %s %s %s", duplexRead.leftAlignmentStart,
					duplexRead.leftAlignmentEnd, duplexRead.toString(), rExtended.getFullName());
			}//End new duplex creation
		}//End loop over reads
		
		Pair<DuplexRead, DuplexRead> pair;
		if (DebugLogControl.COSTLY_ASSERTIONS && 
				(pair = 
				DuplexRead.checkNoEqualDuplexes(duplexKeeper.getIterable()))
				!= null) {
			throw new AssertionFailedException("Equal duplexes: " +
				pair.fst + " and " + pair.snd);
		}

		if (DebugLogControl.COSTLY_ASSERTIONS) {
			Assert.isTrue(checkReadsOccurOnceInDuplexes(extSAMCache.values(),
				duplexKeeper.getIterable(), nReadsExcludedFromDuplexes));
		}

		final boolean allReadsSameBarcode = analyzer.alignmentPositionMismatchAllowed == 0;
		
		//Group duplexes that have alignment positions that differ by at most
		//analyzer.alignmentPositionMismatchAllowed
		//and left/right consensus that differ by at most
		//analyzer.nVariableBarcodeMismatchesAllowed

		final DuplexKeeper cleanedUpDuplexes =
				DuplexRead.groupDuplexes(
						duplexKeeper,
						duplex -> duplex.computeConsensus(allReadsSameBarcode, analyzer.variableBarcodeLength),
						() -> getDuplexKeeper(fallBackOnIntervalTree),
						analyzer,
						stats,
						0);
		
		for (DuplexRead duplexRead: cleanedUpDuplexes.getIterable()) {
			Assert.isFalse(duplexRead.invalid);
			final @NonNull SequenceLocation roughLocation;
			Objects.requireNonNull(duplexRead.roughLocation);
			roughLocation = duplexRead.roughLocation;
			
			stats.duplexTotalRecords.insert(duplexRead.totalNRecords);

			if (Arrays.equals(duplexRead.leftBarcode, duplexRead.rightBarcode)) {
				duplexRead.issues.add("BREQ");
				stats.nPosDuplexRescuedFromLeftRightBarcodeEquality.increment(roughLocation);
			}

			Collection<ExtendedSAMRecord> allDuplexRecords = 
					new ArrayList<>(duplexRead.topStrandRecords.size() +
							duplexRead.bottomStrandRecords.size());
			allDuplexRecords.addAll(duplexRead.topStrandRecords);
			allDuplexRecords.addAll(duplexRead.bottomStrandRecords);
			
			final boolean alreadyVisitedForStats = allDuplexRecords.stream().anyMatch(
					r -> r.duplexAlreadyVisitedForStats);
			
			if (!alreadyVisitedForStats) {
				allDuplexRecords.forEach(r -> r.duplexAlreadyVisitedForStats = true);
				stats.duplexinsertSize.insert(Math.abs(allDuplexRecords.iterator().next().getInsertSize()));
			}
			
			int i = 0;
			double sumDisagreementRates = 0d;
			int sumNClipped = 0;
			for (ExtendedSAMRecord r: allDuplexRecords) {
				Assert.isFalse(r.getnClipped() < 0);
				sumNClipped += r.getnClipped();
				i++;
				sumDisagreementRates += (r.nReferenceDisagreements / ((float) (r.effectiveLength)));
			}
			
			if (i == 0) {
				stats.nDuplexesNoStats.add(1);
			} else {
				stats.nDuplexesWithStats.add(1);
				duplexRead.referenceDisagreementRate = (float) (sumDisagreementRates / i);
				stats.averageDuplexReferenceDisagreementRate.insert((int) (1000 * duplexRead.referenceDisagreementRate));
				duplexRead.averageNClipped = sumNClipped / i;
				stats.duplexAverageNClipped.insert(duplexRead.averageNClipped);
			}
			
			if (duplexRead.averageNClipped > analyzer.maxAverageBasesClipped) {
				duplexRead.issues.add("TMCLP" + duplexRead.averageNClipped);
				stats.nDuplexesTooMuchClipping.accept(roughLocation);
			}
			
			final int inferredSize = Math.abs(allDuplexRecords.iterator().next().getInsertSize());
			if (inferredSize < 130) {
				stats.duplexInsert100_130AverageNClipped.insert(duplexRead.averageNClipped);
			} else if (inferredSize < 180) {
				stats.duplexInsert130_180averageNClipped.insert(duplexRead.averageNClipped);
			}
		}//End duplex analysis
		
		for (DuplexRead dr: cleanedUpDuplexes.getIterable()) {
			finalResult.add(dr);
		}
	}//End loadAll

	private static boolean checkReadsOccurOnceInDuplexes(
			Collection<@NonNull ExtendedSAMRecord> reads,
			@NonNull Iterable<DuplexRead> duplexes,
			int nReadsExcludedFromDuplexes) {
		ExtendedSAMRecord lostRead = null;
		int nUnfoundReads = 0;
		for (final @NonNull ExtendedSAMRecord rExtended: reads) {
			boolean found = false;
			for (DuplexRead dr: duplexes) {
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
	
	@SuppressWarnings({ "null"})
	/**
	 * This method is *NOT* thread-safe (it modifies DuplexReads associated with location retrieved
	 * from field candidateSequences)
	 * @param location
	 * @return
	 */
	@NonNull LocationExaminationResults examineLocation0(final @NonNull SequenceLocation location) {
		final LocationExaminationResults result = new LocationExaminationResults();

		final Set<CandidateSequence> candidateSet = candidateSequences.get(location);
		if (candidateSet == null) {
			stats.nPosUncovered.increment(location);
			result.analyzedCandidateSequences = Collections.emptyList();
			return result;
		}

		//Retrieve relevant duplex reads
		//It is necessary not to duplicate the duplex reads, hence the use of a set
		//Identity should be good enough (and is faster) because no two different duplex read
		//objects we use in this method should be equal according to the equals() method
		//(although when grouping duplexes we don't check equality for the inner ends of
		//the reads since they depend on read length)
		final IdentityHashSet<DuplexRead> duplexReads = new IdentityHashSet<>();

		boolean hasHiddenCandidate = false;
		for (CandidateSequence candidate: candidateSet) {
			if (candidate.isHidden()) {
				hasHiddenCandidate = true;
			}
			IdentityHashSet<DuplexRead> candidateDuplexReads = new IdentityHashSet<>();
			candidate.getNonMutableConcurringReads().forEachEntry((r, c) -> {
				@Nullable DuplexRead d = r.duplexRead;
				if (d != null) {
					candidateDuplexReads.add(d);
				} else {
					//throw new AssertionFailedException("Read without a duplex :" + r);
				}
				return true;
			});
			candidate.getDuplexes().addAll(candidateDuplexReads);//XXX Not necessary?
			duplexReads.addAll(candidateDuplexReads);
		}
		
		float averageClippingOfCoveringDuplexes = 0;
		//Allocate here to avoid repeated allocation in following loop
		final CandidateCounter topCounter = new CandidateCounter(candidateSet, location);
		final CandidateCounter bottomCounter = new CandidateCounter(candidateSet, location);

		for (DuplexRead duplexRead: duplexReads) {
			averageClippingOfCoveringDuplexes += duplexRead.averageNClipped;
			duplexRead.examineAtLoc(location, result, candidateSet,
				assaysToIgnoreForDisagreementQuality, hasHiddenCandidate,
				topCounter, bottomCounter, analyzer, stats);
		}
		if (DebugLogControl.COSTLY_ASSERTIONS) {
			Assert.noException(() -> checkDuplexAndCandidates(duplexReads, candidateSet));
		}

		averageClippingOfCoveringDuplexes /= duplexReads.size();

		if (averageClippingOfCoveringDuplexes > analyzer.maxAverageClippingOfAllCoveringDuplexes) {
			for (DuplexRead duplexRead: duplexReads) {
				duplexRead.localQuality.addUnique(
					MAX_AVERAGE_CLIPPING_ALL_COVERING_DUPLEXES, DUBIOUS);
			}
		}

		Assert.noException(() -> {
			for (DuplexRead duplexRead: duplexReads) {
				for (ExtendedSAMRecord r: duplexRead.bottomStrandRecords) {
					if (r.duplexRead != duplexRead) {
						throw new AssertionFailedException();
					}
					if (duplexRead.topStrandRecords.contains(r)) {
						throw new AssertionFailedException();							
					}
				}
				for (ExtendedSAMRecord r: duplexRead.topStrandRecords) {
					if (r.duplexRead != duplexRead) {
						throw new AssertionFailedException();
					}
					if (duplexRead.bottomStrandRecords.contains(r)) {
						throw new AssertionFailedException();							
					}
				}
			}
		});

		Quality maxQuality = MINIMUM;

		byte wildtypeBase = 'X';

		final int totalReadsAtPosition = candidateSet.stream().
			mapToInt(c -> c.getNonMutableConcurringReads().size()).sum();

		int totalGoodDuplexes = 0, totalGoodOrDubiousDuplexes = 0,
			totalGoodDuplexesIgnoringDisag = 0, totalAllDuplexes = 0;

		final TByteArrayList allPhredQualitiesAtPosition = new TByteArrayList(500);
		int nWrongPairsAtPosition = 0;
		int nPairsAtPosition = 0;

		for (CandidateSequence candidate: candidateSet) {
			candidate.addPhredQualitiesToList(allPhredQualitiesAtPosition);
			nPairsAtPosition += candidate.getNonMutableConcurringReads().size();
			candidate.setnWrongPairs((int) candidate.getNonMutableConcurringReads().keySet().stream().
				filter(ExtendedSAMRecord::formsWrongPair).count());
			nWrongPairsAtPosition += candidate.getnWrongPairs();
		}

		Quality maxQForAllDuplexes = MAXIMUM;

		final int nPhredQualities = allPhredQualitiesAtPosition.size();
		allPhredQualitiesAtPosition.sort();
		final byte positionMedianPhred = nPhredQualities == 0 ? 127 :
			allPhredQualitiesAtPosition.get(nPhredQualities / 2); 
		if (positionMedianPhred < analyzer.minMedianPhredQualityAtPosition) {
			maxQForAllDuplexes = DUBIOUS;
			stats.nMedianPhredAtPositionTooLow.increment(location);
		}
		stats.medianPositionPhredQuality.insert(positionMedianPhred);

		if (nWrongPairsAtPosition / ((float) nPairsAtPosition) > analyzer.maxFractionWrongPairsAtPosition) {
			maxQForAllDuplexes = DUBIOUS;
			stats.nFractionWrongPairsAtPositionTooHigh.increment(location);
		}

		if (maxQForAllDuplexes.compareTo(GOOD) < 0) {
			result.disagreements.clear();
		} else {
			candidateSet.stream().flatMap(c -> c.getRawMismatchesQ2().stream()).
			forEach(result.rawMismatchesQ2::add);
			candidateSet.stream().flatMap(c -> c.getRawInsertionsQ2().stream()).
			forEach(result.rawInsertionsQ2::add);
			candidateSet.stream().flatMap(c -> c.getRawDeletionsQ2().stream()).
			forEach(result.rawDeletionsQ2::add);
		}

		for (CandidateSequence candidate: candidateSet) {
			candidate.getQuality().reset();

			candidate.setMedianPhredAtPosition(positionMedianPhred);

			candidate.setDuplexes(candidate.getNonMutableConcurringReads().keySet().stream().
				map(r -> r.duplexRead).filter(d -> {
					boolean nonNull = d != null;
					if (nonNull && d.invalid) {
						throw new AssertionFailedException();
					}
					return nonNull;
				}).
				collect(uniqueValueCollector()));//Collect *unique* duplexes

			candidate.setnDuplexes(candidate.getDuplexes().size());

			totalAllDuplexes += candidate.getnDuplexes();

			if (candidate.getnDuplexes() == 0) {
				candidate.getQuality().addUnique(NO_DUPLEXES, ATROCIOUS);
				continue;
			}

			candidate.getDuplexes().forEach(d -> candidate.getIssues().put(d, d.localQuality));

			Quality maxQ = maxQForAllDuplexes;
			final Quality maxDuplexQ = candidate.getDuplexes().stream().
				map(dr -> {
					dr.localQuality.addUnique(MAX_Q_FOR_ALL_DUPLEXES, maxQ);
					return dr.localQuality.getMin();
				}).
				max(Quality::compareTo).orElse(ATROCIOUS);
			candidate.getQuality().addUnique(MAX_Q_FOR_ALL_DUPLEXES, maxDuplexQ);
			candidate.getQuality().addUnique(MAX_DPLX_Q_IGNORING_DISAG, candidate.getDuplexes().stream().
				map(dr -> dr.localQuality.getMinIgnoring(assaysToIgnoreForDisagreementQuality)).
				max(Quality::compareTo).orElse(ATROCIOUS));

			if (maxDuplexQ.compareTo(GOOD) >= 0) {
				candidate.resetLigSiteDistances();
				candidate.getDuplexes().stream().filter(dr -> dr.localQuality.getMin().compareTo(GOOD) >= 0).
				forEach(d -> candidate.acceptLigSiteDistance(d.getDistanceToLigSite()));
			}

			if (analyzer.computeSupplQuality && candidate.getQuality().getMin() == DUBIOUS &&
				averageClippingOfCoveringDuplexes <= analyzer.maxAverageClippingOfAllCoveringDuplexes) {
				//See if we should promote to Q2, but only if there is not too much clipping
				final long countQ1Duplexes = candidate.getNonMutableConcurringReads().keySet().stream().map(c -> c.duplexRead).
					filter(d -> d != null && d.localQuality.getMin().compareTo(DUBIOUS) >= 0).
					collect(uniqueValueCollector()).
					size();
				final Stream<ExtendedSAMRecord> highMapQReads = candidate.getNonMutableConcurringReads().keySet().stream().
					filter(r -> r.record.getMappingQuality() >= analyzer.minMappingQualityQ2);
				if (countQ1Duplexes >= analyzer.promoteNQ1Duplexes)
					candidate.setSupplQuality(GOOD);
				else if (candidate.getNonMutableConcurringReads().size() >= analyzer.promoteFractionReads * totalReadsAtPosition &&
					highMapQReads.count() >= 10) { //TODO Make 10 a parameter
					candidate.setSupplQuality(GOOD);
					stats.nQ2PromotionsBasedOnFractionReads.add(location, 1);
				} else {
					final int maxNStrands = candidate.getNonMutableConcurringReads().keySet().stream().map(c -> c.duplexRead).
						filter(d -> d != null && d.localQuality.getMin().compareTo(POOR) >= 0).
						mapToInt(d -> d.topStrandRecords.size() + d.bottomStrandRecords.size()).max().
						orElse(0);
					if (maxNStrands >= analyzer.promoteNSingleStrands) {
						candidate.setSupplQuality(GOOD);
					}
				}
			}//End quality promotion

			SettableInteger count = new SettableInteger(0);
			candidate.getDuplexes().forEach(dr -> {
				if (dr.localQuality.getMin().compareTo(GOOD) >= 0) {
					count.incrementAndGet();
				}
			});
			candidate.setnGoodDuplexes(count.get());

			if (!analyzer.rnaSeq) {
				candidate.getNonMutableConcurringReads().forEachKey(r -> {
					final int refPosition = location.position;
					final int readPosition = r.referencePositionToReadPosition(refPosition);
					if (!r.formsWrongPair()) {
						final int distance = r.tooCloseToBarcode(refPosition, readPosition, 0);
						if (Math.abs(distance) > 150 && !candidate.getMutationType().isWildtype()) {
							throw new AssertionFailedException("Distance problem with candidate " + candidate +
								" read at read position " + readPosition + " and refPosition " +
								refPosition + " " + r.toString() + " in analyzer" +
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

			candidate.setnGoodDuplexesIgnoringDisag(candidate.getDuplexes().stream().
				filter(dr -> dr.localQuality.getMinIgnoring(assaysToIgnoreForDisagreementQuality).compareTo(GOOD) >= 0).
				collect(uniqueValueCollector()).size());

			totalGoodDuplexes += candidate.getnGoodDuplexes();

			candidate.setnGoodOrDubiousDuplexes(candidate.getDuplexes().stream().
				filter(dr -> dr.localQuality.getMin().compareTo(DUBIOUS) >= 0).
				collect(uniqueValueCollector()).size());

			totalGoodOrDubiousDuplexes += candidate.getnGoodOrDubiousDuplexes();
			totalGoodDuplexesIgnoringDisag += candidate.getnGoodDuplexesIgnoringDisag();

			if (candidate.getMutationType().isWildtype()) {
				candidate.setSupplementalMessage(null);
				wildtypeBase = candidate.getWildtypeSequence();
			} else if (candidate.getQuality().getMin().compareTo(POOR) > 0) {

				final StringBuilder supplementalMessage = new StringBuilder();
				final Map<String, Integer> stringCounts = new HashMap<>(100);

				candidate.getNonMutableConcurringReads().keySet().stream().map(er -> {
					String other = er.record.getMateReferenceName();
					if (er.record.getReferenceName().equals(other))
						return "";
					else
						return other + ":" + er.getMateAlignmentStart();
				}).forEach(s -> {
					if ("".equals(s))
						return;
					Integer found = stringCounts.get(s);
					if (found == null){
						stringCounts.put(s,1);
					} else {
						stringCounts.put(s, found + 1);
					}
				});

				final Optional<String> mates = stringCounts.entrySet().stream().map(entry -> entry.getKey() + 
					((entry.getValue() == 1) ? "" : (" (" + entry.getValue() + " repeats)"))
					+ "; ").sorted().reduce(String::concat);

				final String hasMateOnOtherChromosome = mates.isPresent() ? mates.get() : "";

				final IntSummaryStatistics insertSizeStats = candidate.getNonMutableConcurringReads().keySet().stream().mapToInt(er -> Math.abs(er.
					getInsertSize())).summaryStatistics();
				final int localMaxInsertSize = insertSizeStats.getMax();
				final int localMinInsertSize = insertSizeStats.getMin();

				candidate.setMinInsertSize(localMinInsertSize);
				candidate.setMaxInsertSize(localMaxInsertSize);

				if (localMaxInsertSize < analyzer.minInsertSize || localMinInsertSize > analyzer.maxInsertSize) {
					candidate.getQuality().add(INSERT_SIZE, DUBIOUS);
				}

				final boolean has0PredictedInsertSize = localMinInsertSize == 0;

				final NumberFormat nf = DoubleAdderFormatter.nf.get();

				final boolean hasNoMate = candidate.getNonMutableConcurringReads().keySet().stream().map(er -> er.record.
					getMateReferenceName() == null).reduce(false, Boolean::logicalOr);

				if (localMaxInsertSize > analyzer.maxInsertSize) {
					supplementalMessage.append("one predicted insert size is " + 
						nf.format(localMaxInsertSize)).append("; ");
				}

				if (localMinInsertSize < analyzer.minInsertSize) {
					supplementalMessage.append("one predicted insert size is " + 
						nf.format(localMinInsertSize)).append("; ");
				}

				candidate.setAverageMappingQuality((int) candidate.getNonMutableConcurringReads().keySet().stream().
					mapToInt(r -> r.record.getMappingQuality()).summaryStatistics().getAverage());

				if (!"".equals(hasMateOnOtherChromosome)) {
					supplementalMessage.append("pair elements map to other chromosomes: " + hasMateOnOtherChromosome).append("; ");
				}

				if (hasNoMate) {
					supplementalMessage.append("at least one read has no mate; ");
				}

				if ("".equals(hasMateOnOtherChromosome) && !hasNoMate && has0PredictedInsertSize) {
					supplementalMessage.append("at least one insert has 0 predicted size; ");
				}

				if (candidate.getnWrongPairs() > 0) {
					supplementalMessage.append(candidate.getnWrongPairs() + " wrong pairs; ");
				}

				candidate.setSupplementalMessage(supplementalMessage);
			}
			maxQuality = max(maxQuality, candidate.getQuality().getMin());
		}//End loop over candidates

		for (CandidateSequence candidate: candidateSet) {
			candidate.setTotalAllDuplexes(totalAllDuplexes);
			candidate.setTotalGoodDuplexes(totalGoodDuplexes);
			candidate.setTotalGoodOrDubiousDuplexes(totalGoodOrDubiousDuplexes);
			candidate.setTotalReadsAtPosition(totalReadsAtPosition);
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

		if (maxQuality.compareTo(POOR) <= 0) {
			stats.nPosQualityPoor.increment(location);
			switch (wildtypeBase) {
				case 'A' : stats.nPosQualityPoorA.increment(location); break;
				case 'T' : stats.nPosQualityPoorT.increment(location); break;
				case 'G' : stats.nPosQualityPoorG.increment(location); break;
				case 'C' : stats.nPosQualityPoorC.increment(location); break;
				case 'X' : break; //Ignore because we do not have a record of wildtype sequence
				default : throw new AssertionFailedException();
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
	
	private static Runnable checkDuplexAndCandidates(IdentityHashSet<DuplexRead> duplexReads,
			Set<CandidateSequence> candidateSet) {
		for (DuplexRead duplexRead: duplexReads) {
			for (ExtendedSAMRecord r: duplexRead.bottomStrandRecords) {
				if (r.duplexRead != duplexRead) {
					throw new AssertionFailedException("Read " + r + " associated with duplexes " +
						r.duplexRead + " and " + duplexRead);
				}
			}
			for (ExtendedSAMRecord r: duplexRead.topStrandRecords) {
				if (r.duplexRead != duplexRead) {
					throw new AssertionFailedException("Read " + r + " associated with duplexes " +
						r.duplexRead + " and " + duplexRead);
				}
			}
		}

		for (CandidateSequence c: candidateSet) {
			Assert.isTrue(c.getNonMutableConcurringReads().keySet().equals(
				c.getMutableConcurringReads().keySet()));
			Set<DuplexRead> duplexesSupportingC = c.getNonMutableConcurringReads().keySet().stream().
				map(r -> {
					DuplexRead d = r.duplexRead;
					if (d != null && d.invalid) {
						throw new AssertionFailedException();
					}
					return d;
				}).filter(d -> d != null).collect(uniqueValueCollector());//Collect *unique* duplexes
			for (CandidateSequence c2: candidateSet) {
				Assert.isTrue(c.getNonMutableConcurringReads().keySet().equals(
					c.getMutableConcurringReads().keySet()));
				if (c2 == c) {
					continue;
				}
				if (c2.equals(c)) {
					throw new AssertionFailedException();
				}
				c2.getNonMutableConcurringReads().keySet().forEach(r -> {
					DuplexRead d = r.duplexRead;
					if (d != null && duplexesSupportingC.contains(d)) {
						boolean disowned = !d.topStrandRecords.contains(r) && !d.bottomStrandRecords.contains(r);
						
						throw new AssertionFailedException(disowned + " Duplex " + d +
							" associated with candidates " + c + " and " + c2);
					}
				});
			}
		}
		return null;
	}

	private boolean checkConstantBarcode(byte[] bases, boolean allowN, int nAllowableMismatches) {
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

	@SuppressWarnings("null")
	ExtendedSAMRecord getExtended(@NonNull SAMRecord record, @NonNull SequenceLocation location) {
		final @NonNull String readFullName = ExtendedSAMRecord.getReadFullName(record);
		return extSAMCache.computeIfAbsent(readFullName, s ->
			new ExtendedSAMRecord(record, readFullName, analyzer.groupSettings, analyzer, location, extSAMCache));
	}

	/**
	 * 
	 * @param rec
	 * @param ref
	 * @return the furthest position in the contig covered by the read
	 */
	int processRead(final @NonNull SAMRecord rec, final @NonNull ReferenceSequence ref) {

		@NonNull SequenceLocation location = new SequenceLocation(rec);

		final ExtendedSAMRecord extendedRec = getExtended(rec, location);
		Assert.isFalse(extendedRec.processed, "Double processing of record %s", extendedRec.getFullName());
		
		extendedRec.processed = true;
		
		final boolean readOnNegativeStrand = rec.getReadNegativeStrandFlag();
		final byte[] readBases = rec.getReadBases();
		final byte[] refBases = ref.getBases();
		final byte[] baseQualities = rec.getBaseQualities();
		final int effectiveReadLength = extendedRec.effectiveLength;
		if (effectiveReadLength == 0) {
			return -1;
		}
					
		final CandidateBuilder readLocalCandidates = new CandidateBuilder(rec.getReadNegativeStrandFlag());
		
		final int insertSize = extendedRec.getInsertSize();
		final int insertSizeAbs = Math.abs(insertSize);

		if (insertSizeAbs > analyzer.maxInsertSize) {
			stats.nReadsInsertSizeAboveMaximum.increment(location);
			if (analyzer.ignoreSizeOutOfRangeInserts) {
				return -1;
			}
		}

		if (insertSizeAbs < analyzer.minInsertSize) {
			stats.nReadsInsertSizeBelowMinimum.increment(location);
			if (analyzer.ignoreSizeOutOfRangeInserts) {
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
			if (analyzer.ignoreTandemRFPairs) {
				return -1;
			}
		}
		
		final boolean reversed = rec.getReadNegativeStrandFlag();

		if (!checkConstantBarcode(extendedRec.constantBarcode, false, analyzer.nConstantBarcodeMismatchesAllowed)) {
			if (checkConstantBarcode(extendedRec.constantBarcode, true, analyzer.nConstantBarcodeMismatchesAllowed)) {
				if (reversed)
					stats.nConstantBarcodeDodgyNStrand.increment(location);
				else
					stats.nConstantBarcodeDodgy.increment(location);
				if (!analyzer.acceptNInBarCode)
					return -1;
			} else {
				stats.nConstantBarcodeMissing.increment(location);
				return -1;
			}
		}
		stats.nReadsConstantBarcodeOK.increment(location);
		
		if (extendedRec.medianPhred < analyzer.minReadMedianPhredScore) {
			stats.nReadMedianPhredBelowThreshold.accept(location);
			return -1;
		}

		stats.mappingQualityKeptRecords.insert(rec.getMappingQuality());

		/*int localIgnoreLastNBases = analyzer.ignoreLastNBases;
		
		int adjustedLength = rec.getReadLength() - analyzer.unclippedBarcodeLength + analyzer.ignoreFirstNBasesQ1;
		if (insertSizeAbs > 0 && insertSizeAbs < adjustedLength) {
			//Reads ran into adapter ligated at the other end
			//Update ignoreLastNBases to stay sufficiently away from ligation site
			localIgnoreLastNBases = Math.max(adjustedLength - insertSizeAbs,
					analyzer.ignoreLastNBases);
		}*/

		int refEndOfPreviousAlignment = -1;
		int readEndOfPreviousAlignment = -1;

		int returnValue = -1;

	if (insertSize == 0) {
		stats.nReadsInsertNoSize.increment(location);
		if (analyzer.ignoreZeroInsertSizeReads) {
			return -1;
		}
	}
	
	final boolean notRnaSeq = !analyzer.rnaSeq;

	for (AlignmentBlock block: rec.getAlignmentBlocks()) {
		int refPosition = block.getReferenceStart() - 1;
		int readPosition = block.getReadStart() - 1;
		final int nBlockBases = block.getLength();

		returnValue = Math.max(returnValue, refPosition + nBlockBases);

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
					(insertion ? readPosition <= analyzer.ignoreLastNBases : readPosition < analyzer.ignoreLastNBases) :
				readPosition > rec.getReadLength() - analyzer.ignoreLastNBases) && notRnaSeq;

			if (tooLate) {
				if (DebugLogControl.shouldLog(TRACE, logger)) {
					logger.trace("Ignoring indel too close to end " + readPosition + (readOnNegativeStrand ? " neg strand " : " pos strand ") + readPosition + " " + (rec.getReadLength() - 1) + " " + extendedRec.getFullName());
				}
				stats.nCandidateIndelAfterLastNBases.increment(location);
			} else {
				if (insertion) {
					//Insertion
					stats.nCandidateInsertions.increment(location);
					if (DebugLogControl.shouldLog(TRACE, logger)) {
						logger.trace("Insertion at position " + readPosition + " for read " + rec.getReadName() +
							" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
							"; insert size: " + insertSize + ")");
					}
					location = new SequenceLocation(ref.getContigIndex(), ref.getName(), refEndOfPreviousAlignment, true);
					int distance0 = extendedRec.tooCloseToBarcode(refPosition, readEndOfPreviousAlignment, analyzer.ignoreFirstNBasesQ1);
					int distance1 = extendedRec.tooCloseToBarcode(refPosition, readPosition, analyzer.ignoreFirstNBasesQ1);
					int distance = Math.max(distance0, distance1);
					distance = -distance + 1;
					
					final boolean Q1reject = distance < 0;
					if (Q1reject) {
						if (!extendedRec.formsWrongPair()) {
							stats.rejectedIndelDistanceToLigationSite.insert(-distance);
							stats.nCandidateIndelBeforeFirstNBases.increment(location);
						}
						logger.trace("Ignoring insertion " + readEndOfPreviousAlignment + analyzer.ignoreFirstNBasesQ1 + " " + extendedRec.getFullName());
					} else {
						distance0 = extendedRec.tooCloseToBarcode(refPosition, readEndOfPreviousAlignment, 0);
						distance1 = extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);
						distance = Math.max(distance0, distance1);
						distance = -distance + 1;

						final CandidateSequence candidate = new CandidateSequence(analyzer.idx, INSERTION, location,
								extendedRec, distance);

						final byte [] insertedSequence = Arrays.copyOfRange(readBases,
								readEndOfPreviousAlignment + 1, readPosition);

						if (!extendedRec.formsWrongPair()) {
							candidate.acceptLigSiteDistance(distance);
						}

						candidate.insertSize = insertSize;
						candidate.positionInRead = readPosition;
						candidate.readEL = effectiveReadLength;
						candidate.readName = extendedRec.getFullName();
						candidate.readAlignmentStart = extendedRec.getRefAlignmentStart();
						candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStart();
						candidate.readAlignmentEnd = extendedRec.getRefAlignmentEnd();
						candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEnd();
						candidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
						candidate.insertSizeNoBarcodeAccounting = false;
						candidate.setSequence(insertedSequence);

						if (analyzer.computeRawDisagreements) {
							final byte wildType = readBases[readEndOfPreviousAlignment];
							@SuppressWarnings("null")
							final ComparablePair<String, String> mutationPair = readOnNegativeStrand ? 
									new ComparablePair<>(byteMap.get(Mutation.complement(wildType)),
											new String(new Mutation(candidate).reverseComplement().mutationSequence).toUpperCase())
									:
									new ComparablePair<>(byteMap.get(wildType),
											new String(insertedSequence).toUpperCase());
									stats.rawInsertionsQ1.accept(location, mutationPair);
									stats.rawInsertionLengthQ1.insert(insertedSequence.length);

									if (meetsQ2Thresholds(extendedRec) &&
											baseQualities[readPosition] >= analyzer.minBasePhredScoreQ2 &&
											!extendedRec.formsWrongPair() && distance > analyzer.ignoreFirstNBasesQ2) {
										candidate.getMutableRawInsertionsQ2().add(mutationPair);
									}
						}

						if (DebugLogControl.shouldLog(TRACE, logger)) {
							logger.trace("Insertion of " + new String(candidate.getSequence()) + " at ref " + refPosition + " and read position " + readPosition + " for read " + extendedRec.getFullName());
						}
						readLocalCandidates.add(candidate, location);
						forceCandidateInsertion = true;
						if (DebugLogControl.shouldLog(TRACE, logger)) {
							logger.trace("Added candidate at " + location + "; readLocalCandidates now " + readLocalCandidates.build());
						}
						extendedRec.nReferenceDisagreements++;
					}
				}//End of insertion case
				else if (refPosition < refEndOfPreviousAlignment + 1) {
					throw new AssertionFailedException("Alignment block misordering");
				} else {
					//Deletion or skipped region ("N" in Cigar)
					if (refPosition > refBases.length - 1) {
						logger.warn("Ignoring rest of read after base mapped at " + refPosition + 
								", beyond the end of " + ref.getName());
						continue;
					}
					
					int distance0 = -extendedRec.tooCloseToBarcode(refPosition - 1, readPosition - 1, analyzer.ignoreFirstNBasesQ1);
					int distance1 = -extendedRec.tooCloseToBarcode(refEndOfPreviousAlignment + 1, readEndOfPreviousAlignment + 1, analyzer.ignoreFirstNBasesQ1);
					int distance = Math.min(distance0, distance1) + 1;

					final boolean Q1reject = distance < 0;
					
					if (Q1reject) {
						if (!extendedRec.formsWrongPair()) {
							stats.rejectedIndelDistanceToLigationSite.insert(-distance);
							stats.nCandidateIndelBeforeFirstNBases.increment(location);
						}
						logger.trace("Ignoring deletion " + readEndOfPreviousAlignment + analyzer.ignoreFirstNBasesQ1 + " " + extendedRec.getFullName());
					} else {
						distance0 = -extendedRec.tooCloseToBarcode(refPosition - 1, readPosition - 1, 0);
						distance1 = -extendedRec.tooCloseToBarcode(refEndOfPreviousAlignment + 1, readEndOfPreviousAlignment + 1, 0);
						distance = Math.min(distance0, distance1) + 1;

						stats.nCandidateDeletions.increment(location);
						if (DebugLogControl.shouldLog(TRACE, logger)) {
							logger.trace("Deletion at position " + readPosition + " for read " + rec.getReadName() +
									" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
									"; insert size: " + insertSize + ")");
						}

						final int deletionLength = refPosition - (refEndOfPreviousAlignment + 1);
						location = new SequenceLocation(ref.getContigIndex(), ref.getName(), refEndOfPreviousAlignment + 1);
						final @NonNull SequenceLocation deletionEnd = new SequenceLocation(ref.getContigIndex(),
								ref.getName(), location.position + deletionLength);

						final byte @Nullable[] deletedSequence = notRnaSeq 	? Arrays.copyOfRange(ref.getBases(),
								refEndOfPreviousAlignment + 1, refPosition)
								: null	;

						//Add hidden mutations to all locations covered by deletion
						//So disagreements between deletions that have only overlapping
						//spans are detected.
						for (int i = 1; i < deletionLength; i++) {
							SequenceLocation location2 = new SequenceLocation(ref.getContigIndex(),
									ref.getName(), refEndOfPreviousAlignment + 1 + i);
							CandidateSequence hiddenCandidate = new CandidateDeletion(analyzer.idx, location2, extendedRec, Integer.MAX_VALUE,
									location, deletionEnd);
							hiddenCandidate.setHidden(true);
							hiddenCandidate.insertSize = insertSize;
							hiddenCandidate.insertSizeNoBarcodeAccounting = false;
							hiddenCandidate.positionInRead = readPosition;
							hiddenCandidate.readEL = effectiveReadLength;
							hiddenCandidate.readName = extendedRec.getFullName();
							hiddenCandidate.readAlignmentStart = extendedRec.getRefAlignmentStart();
							hiddenCandidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStart();
							hiddenCandidate.readAlignmentEnd = extendedRec.getRefAlignmentEnd();
							hiddenCandidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEnd();
							hiddenCandidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
							hiddenCandidate.setSequence(deletedSequence);
							readLocalCandidates.add(hiddenCandidate, location2);
						}

						final CandidateSequence candidate = new CandidateDeletion(analyzer.idx, location, extendedRec, distance,
								location, new SequenceLocation(ref.getContigIndex(), ref.getName(), refPosition));

						if (!extendedRec.formsWrongPair()) {
							candidate.acceptLigSiteDistance(distance);
						}

						candidate.insertSize = insertSize;
						candidate.insertSizeNoBarcodeAccounting = false;
						candidate.positionInRead = readPosition;
						candidate.readEL = effectiveReadLength;
						candidate.readName = extendedRec.getFullName();
						candidate.readAlignmentStart = extendedRec.getRefAlignmentStart();
						candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStart();
						candidate.readAlignmentEnd = extendedRec.getRefAlignmentEnd();
						candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEnd();
						candidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
						candidate.setSequence(deletedSequence);
						readLocalCandidates.add(candidate, location);
						extendedRec.nReferenceDisagreements++;

						if (notRnaSeq && analyzer.computeRawDisagreements) {
							@SuppressWarnings("null")
							final ComparablePair<String, String> mutationPair = readOnNegativeStrand ? 
									new ComparablePair<>(byteMap.get(Mutation.complement(deletedSequence[0])),
											new String(new Mutation(candidate).reverseComplement().mutationSequence).toUpperCase())
									:
										new ComparablePair<>(byteMap.get(deletedSequence[0]),
												new String(deletedSequence).toUpperCase());
									stats.rawDeletionsQ1.accept(location, mutationPair);
									stats.rawDeletionLengthQ1.insert(deletedSequence.length);
									if (meetsQ2Thresholds(extendedRec) &&
											baseQualities[readPosition] >= analyzer.minBasePhredScoreQ2 &&
											!extendedRec.formsWrongPair() && distance > analyzer.ignoreFirstNBasesQ2) {
										candidate.getMutableRawDeletionsQ2().add(mutationPair);
									}
						}
					}
				}//End of deletion case
			}//End of case with accepted indel
		}//End of case where there was a previous alignment block

		refEndOfPreviousAlignment = refPosition + (nBlockBases - 1);
		readEndOfPreviousAlignment = readPosition + (nBlockBases - 1);

		for (int i = 0; i < nBlockBases; i++, readPosition++, refPosition++) {
			if (i == 1) {
				forceCandidateInsertion = false;
			}
			boolean insertCandidateAtRegularPosition = true;
			final SequenceLocation locationPH = notRnaSeq && i < nBlockBases - 1 ? //No insertion or deletion; make a note of it
				new SequenceLocation(ref.getContigIndex(), ref.getName(), refPosition, true)
				: null;
			/*final boolean endOfRead = readOnNegativeStrand ? readPosition == 0 :
				readPosition == effectiveReadLength - 1;*/

			location = new SequenceLocation(ref.getContigIndex(), ref.getName(), refPosition);
			
			if (baseQualities[readPosition] < analyzer.minBasePhredScoreQ1) {
				stats.nBasesBelowPhredScore.increment(location);
				if (forceCandidateInsertion) {
					insertCandidateAtRegularPosition = false;
				} else {
					continue;
				}
			}
			if (refPosition > refBases.length - 1) {
				logger.warn("Ignoring base mapped at " + refPosition + ", beyond the end of " + ref.getName());
				continue;
			}
			stats.nCandidateSubstitutionsConsidered.increment(location);
			if (readBases[readPosition] != StringUtil.toUpperCase(refBases[refPosition]) /*Mismatch*/) {
				
				final boolean tooLate = readOnNegativeStrand ? readPosition < analyzer.ignoreLastNBases :
					readPosition > (rec.getReadLength() - 1) - analyzer.ignoreLastNBases;

				int distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, analyzer.ignoreFirstNBasesQ1);

				boolean goodToInsert = distance < 0 && !tooLate;
				
				if (distance >= 0) {
					if (!extendedRec.formsWrongPair()) {
						distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);
						if (distance <= 0 && insertCandidateAtRegularPosition) {
							stats.rejectedSubstDistanceToLigationSite.insert(-distance);
							stats.nCandidateSubstitutionsBeforeFirstNBases.increment(location);
						}
					}
					if (DebugLogControl.shouldLog(TRACE, logger)) {
						logger.trace("Ignoring subst too close to barcode for read " + rec.getReadName());
					}
					insertCandidateAtRegularPosition = false;
				} else if (tooLate && insertCandidateAtRegularPosition) {
					stats.nCandidateSubstitutionsAfterLastNBases.increment(location);
					if (DebugLogControl.shouldLog(TRACE, logger)) {
						logger.trace("Ignoring subst too close to read end for read " + rec.getReadName());
					}
					insertCandidateAtRegularPosition = false;
				}
				if (goodToInsert || forceCandidateInsertion) {
					if (DebugLogControl.shouldLog(TRACE, logger)) {
						logger.trace("Substitution at position " + readPosition + " for read " + rec.getReadName() +
							" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
							"; insert size: " + insertSize + ")");
					}
					final byte wildType = StringUtil.toUpperCase(refBases[refPosition]);
					final byte mutation = StringUtil.toUpperCase(readBases[readPosition]);
					switch (mutation) {
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
								continue;
							} else {
								insertCandidateAtRegularPosition = false;
							}
							break;
						default:
							throw new AssertionFailedException("Unexpected letter: " + StringUtil.toUpperCase(readBases[readPosition]));
					}
										
					distance = -extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);

					final CandidateSequence candidate = new CandidateSequence(analyzer.idx, SUBSTITUTION, location,
							extendedRec, distance);

					if (!extendedRec.formsWrongPair()) {
						candidate.acceptLigSiteDistance(distance);
					}
					candidate.insertSize = insertSize;
					candidate.insertSizeNoBarcodeAccounting = false;
					candidate.positionInRead = readPosition;
					candidate.readEL = effectiveReadLength;
					candidate.readName = extendedRec.getFullName();
					candidate.readAlignmentStart = extendedRec.getRefAlignmentStart();
					candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStart();
					candidate.readAlignmentEnd = extendedRec.getRefAlignmentEnd();
					candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEnd();
					candidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
					final byte[] mutSequence = {readBases[readPosition]};
					candidate.setSequence(mutSequence);
					candidate.setWildtypeSequence(wildType);
					if (insertCandidateAtRegularPosition) {
						readLocalCandidates.add(candidate, location);
					}
					if (locationPH != null) {
						final CandidateSequence candidate2 = new CandidateSequence(analyzer.idx, 
								WILDTYPE, locationPH, extendedRec, distance);
						if (!extendedRec.formsWrongPair()) {
							candidate2.acceptLigSiteDistance(distance);
						}
						candidate2.setWildtypeSequence(wildType);
						candidate2.setSequence(null);
						readLocalCandidates.add(candidate2, locationPH);
					}
					candidate.addBasePhredQualityScore(baseQualities[readPosition]);
					extendedRec.nReferenceDisagreements++;
					if (extendedRec.basePhredScores.put(location, baseQualities[readPosition]) != null) {
						logger.warn("Recording Phred score multiple times at same position " + location);
					}
					if (analyzer.computeRawDisagreements && insertCandidateAtRegularPosition) {
						final ComparablePair<String, String> mutationPair = readOnNegativeStrand ? 
								new ComparablePair<>(byteMap.get(Mutation.complement(wildType)),
										byteMap.get(Mutation.complement(mutation))) :
								new ComparablePair<>(byteMap.get(wildType),
										byteMap.get(mutation));
						stats.rawMismatchesQ1.accept(location, mutationPair);
						if (meetsQ2Thresholds(extendedRec) &&
							baseQualities[readPosition] >= analyzer.minBasePhredScoreQ2 &&
							!extendedRec.formsWrongPair() && distance > analyzer.ignoreFirstNBasesQ2) {
								candidate.getMutableRawMismatchesQ2().add(mutationPair);
						}
					}
				}//End of mismatched read case
			} else {
				//Wildtype read
				int distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, analyzer.ignoreFirstNBasesQ1);
				if (distance >= 0) {
					if (!extendedRec.formsWrongPair()) {
						distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);
						if (distance <= 0 && insertCandidateAtRegularPosition) {
							stats.wtRejectedDistanceToLigationSite.insert(-distance);
						}
					}
					if (!forceCandidateInsertion) {
						continue;
					} else {
						insertCandidateAtRegularPosition = false;
					}
				} else {
					if (!extendedRec.formsWrongPair() && distance < -150) {
						throw new AssertionFailedException("Distance problem 1 at read position " + readPosition +
								" and refPosition " + refPosition + " " + extendedRec.toString() +
								" in analyzer" + analyzer.inputBam.getAbsolutePath() +
								"; distance is " + distance + "");
					}
					distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);
					if (!extendedRec.formsWrongPair() && insertCandidateAtRegularPosition) {
						 stats.wtAcceptedBaseDistanceToLigationSite.insert(-distance);
					}
				}
								
				if (((!readOnNegativeStrand && readPosition > readBases.length - 1 - analyzer.ignoreLastNBases) || 
						(readOnNegativeStrand && readPosition < analyzer.ignoreLastNBases))) {
					if (insertCandidateAtRegularPosition) {
						stats.nCandidateWildtypeAfterLastNBases.increment(location);
					}
					if (!forceCandidateInsertion) {
						continue;
					} else {
						insertCandidateAtRegularPosition = false;
					}
				}
				final CandidateSequence candidate = new CandidateSequence(analyzer.idx, 
						WILDTYPE, location, extendedRec, -distance);
				if (!extendedRec.formsWrongPair()) {
					candidate.acceptLigSiteDistance(-distance);
				}
				candidate.setWildtypeSequence(StringUtil.toUpperCase(refBases[refPosition]));
				if (insertCandidateAtRegularPosition) {
					readLocalCandidates.add(candidate, location);
				}
				if (locationPH != null) {
					final CandidateSequence candidate2 = new CandidateSequence(analyzer.idx, 
							WILDTYPE, locationPH, extendedRec, -distance);
					if (!extendedRec.formsWrongPair()) {
						candidate2.acceptLigSiteDistance(-distance);
					}
					candidate2.setWildtypeSequence(StringUtil.toUpperCase(refBases[refPosition]));
					candidate2.setSequence(null);
					readLocalCandidates.add(candidate2, locationPH);
				}
				candidate.addBasePhredQualityScore(baseQualities[readPosition]);
				if (extendedRec.basePhredScores.put(location, baseQualities[readPosition]) != null) {
					logger.warn("Recording Phred score multiple times at same position " + location);
				}
			}//End of wildtype case
		}//End of loop over alignment bases
	}//End alignment block loop

	readLocalCandidates.build().forEach((k,v) -> insertCandidateAtPosition(v, k));

	return returnValue;
	}
	
	public @NonNull Mutinack getAnalyzer() {
		return analyzer;
	}

}
