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
import static uk.org.cinquin.mutinack.misc_util.DebugControl.NONTRIVIAL_ASSERTIONS;
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;
import static uk.org.cinquin.mutinack.misc_util.collections.TroveSetCollector.uniqueValueCollector;

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
import java.util.stream.StreamSupport;

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
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.IdentityHashSet;
import uk.org.cinquin.mutinack.misc_util.collections.TIntListCollector;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

final class SubAnalyzer {
	private final static Logger logger = LoggerFactory.getLogger(SubAnalyzer.class);
	
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
	
	private static final @NonNull TByteObjectMap<@NonNull String> byteMap;
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
		
	SubAnalyzer(@NonNull Mutinack analyzer) {
		this.analyzer = analyzer;
		useHashMap = analyzer.alignmentPositionMismatchAllowed == 0;
	}
	
	private boolean meetsQ2Thresholds(@NonNull ExtendedSAMRecord extendedRec) {
		return !extendedRec.formsWrongPair() &&
				extendedRec.getnClipped() <= analyzer.maxAverageBasesClipped &&
				extendedRec.getMappingQuality() >= analyzer.minMappingQualityQ2 &&
				Math.abs(extendedRec.getInsertSizeNoBarcodes(true)) <= analyzer.maxInsertSize;
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
			if (!candidates.add(candidate)) {
				throw new AssertionFailedException();
			}
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
		if (NONTRIVIAL_ASSERTIONS) {
			if (threadCount.incrementAndGet() > 1) {
				throw new AssertionFailedException();
			}
		}
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
				if (!v.isEmpty() && (!v.iterator().next().getLocation().equals(k))) {
					throw new AssertionFailedException("Mimatched locations");
				}

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
			
			if (nLeftBehind.get() > 0) {
				throw new AssertionFailedException();
			}
		}
	}

	private final boolean useHashMap;

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

		final boolean useIntervalTree;
		
		final @NonNull DuplexKeeper duplexKeeper;
		
		if (useHashMap) {
			useIntervalTree = false;
			duplexKeeper = new DuplexHashMapKeeper();
		} else {
			useIntervalTree = extSAMCache.size() > 5_000;
			if (useIntervalTree) {
				duplexKeeper = new DuplexITKeeper();
			} else {
				duplexKeeper = new DuplexArrayListKeeper(5_000);
			}
		}
		
		final AlignmentExtremetiesDistance ed = new AlignmentExtremetiesDistance();
				
		for (final @NonNull ExtendedSAMRecord rExtended: extSAMCache.values()) {

			final @NonNull SequenceLocation location = new SequenceLocation(rExtended.record);

			final byte @NonNull[] barcode = rExtended.variableBarcode;
			final byte @NonNull[] mateBarcode = rExtended.getMateVariableBarcode();
			final @NonNull SAMRecord r = rExtended.record;
			
			if (rExtended.getMate() == null) {
				stats.nMateOutOfReach.add(location, 1);	
			}
			
			/**
			 * Reads with 0 insert size sometimes throw off duplex
			 * grouping, likely at least in part because there is no
			 * mate to provide better discrimination between duplexes.
			 * Therefore, 0 insert size reads are ignored at this step.
			 * If in the future it is desirable to keep these reads to
			 * collect statistics on the locus, that should be done in
			 * a way that they are not considered at the duplex
			 * grouping step.
			 */
			if (r.getInferredInsertSize() == 0) {
				continue;
			}

			boolean foundDuplexRead = false;
			final boolean matchToLeft = r.getInferredInsertSize() > 0;
			
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
							if (NONTRIVIAL_ASSERTIONS && duplexRead.topStrandRecords.contains(rExtended)) {
								throw new AssertionFailedException();
							}
							duplexRead.topStrandRecords.add(rExtended);
						} else {
							if (NONTRIVIAL_ASSERTIONS && duplexRead.bottomStrandRecords.contains(rExtended)) {
								throw new AssertionFailedException();
							}
							duplexRead.bottomStrandRecords.add(rExtended);
						}
					} else {
						if (r.getFirstOfPairFlag()) {
							if (NONTRIVIAL_ASSERTIONS && duplexRead.bottomStrandRecords.contains(rExtended)) {
								throw new AssertionFailedException();
							}
							duplexRead.bottomStrandRecords.add(rExtended);
						} else {
							if (NONTRIVIAL_ASSERTIONS && duplexRead.topStrandRecords.contains(rExtended)) {
								throw new AssertionFailedException();
							}
							duplexRead.topStrandRecords.add(rExtended);
						}
					}
					duplexRead.assertAllBarcodesEqual();
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
						new DuplexRead(barcode, mateBarcode, !r.getReadNegativeStrandFlag(), r.getReadNegativeStrandFlag()) :
						new DuplexRead(mateBarcode, barcode, r.getReadNegativeStrandFlag(), !r.getReadNegativeStrandFlag());
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
					if (NONTRIVIAL_ASSERTIONS && !r.getReferenceIndex().equals(r.getMateReferenceIndex())) {
						throw new AssertionFailedException();
					}

					if (rExtended.getMateAlignmentStartNoBarcode() == rExtended.getAlignmentStartNoBarcode()) {
						//Reads that completely overlap because of short insert size
						stats.nLociDuplexCompletePairOverlap.increment(location);
					}

					//Arbitrarily choose top strand as the one associated with
					//first of pair that maps to the lowest position in the contig
					if (!r.getFirstOfPairFlag()) {
						duplexRead.topStrandRecords.add(rExtended);
					}
					else {
						duplexRead.bottomStrandRecords.add(rExtended);
					}
					
					duplexRead.assertAllBarcodesEqual();

					duplexRead.rightAlignmentStart = new SequenceLocation(r.getReferenceName(), 
							rExtended.getUnclippedStart());
					duplexRead.rightAlignmentEnd =  new SequenceLocation(r.getReferenceName(), 
							rExtended.getUnclippedEnd());
					duplexRead.leftAlignmentStart = new SequenceLocation(r.getReferenceName(), 
							rExtended.getMateUnclippedStart());
					duplexRead.leftAlignmentEnd = new SequenceLocation(r.getReferenceName(),
							rExtended.getMateUnclippedEnd());					
				} else {//Read on positive strand

					if (rExtended.getMateAlignmentStartNoBarcode() == rExtended.getAlignmentStartNoBarcode()) {
						//Reads that completely overlap because of short insert size?
						stats.nLociDuplexCompletePairOverlap.increment(location);
					} 

					//Arbitrarily choose top strand as the one associated with
					//first of pair that maps to the lowest position in the contig
					if (r.getFirstOfPairFlag()) {
						duplexRead.topStrandRecords.add(rExtended);
					} else {
						duplexRead.bottomStrandRecords.add(rExtended);
					}
					
					duplexRead.assertAllBarcodesEqual();

					duplexRead.leftAlignmentStart = new SequenceLocation(r.getReferenceName(), 
							rExtended.getUnclippedStart());
					duplexRead.leftAlignmentEnd = new SequenceLocation(r.getReferenceName(), 
							rExtended.getUnclippedEnd());
					duplexRead.rightAlignmentStart = new SequenceLocation(r.getReferenceName(), 
							rExtended.getMateUnclippedStart());
					duplexRead.rightAlignmentEnd = new SequenceLocation(r.getReferenceName(), 
							rExtended.getMateUnclippedEnd());
				}
				if (duplexRead.leftAlignmentEnd.compareTo(duplexRead.leftAlignmentStart) < 0) {
					throw new AssertionFailedException("Misordered duplex: " + duplexRead.leftAlignmentStart + " -- " +
							duplexRead.leftAlignmentEnd + " " + duplexRead.toString() + " " + rExtended.getFullName());
					//duplexRead.leftAlignmentStart = duplexRead.leftAlignmentEnd;
				}
			}//End new duplex creation
		}//End loop over reads

		final boolean allReadsSameBarcode = analyzer.alignmentPositionMismatchAllowed == 0;
		for (DuplexRead duplex: duplexKeeper.getIterable()) {
			duplex.computeConsensus(allReadsSameBarcode, analyzer.variableBarcodeLength);
		}

		//Group duplexes that have alignment positions that differ by at most
		//analyzer.alignmentPositionMismatchAllowed
		//and left/right consensus that differ by at most
		//analyzer.nVariableBarcodeMismatchesAllowed

		final DuplexKeeper cleanedUpDuplexes;
		
		if (useHashMap) {
			cleanedUpDuplexes = new DuplexHashMapKeeper();
		} else if (useIntervalTree) {
			cleanedUpDuplexes = new DuplexITKeeper();
		} else {
			cleanedUpDuplexes = new DuplexArrayListKeeper(5_000);
		}

		StreamSupport.stream(duplexKeeper.getIterable().spliterator(), false).
			sorted((d1, d2) -> Integer.compare(d2.totalNRecords, d1.totalNRecords)).forEach(duplex1 -> {
			boolean mergedDuplex = false;
			
			if (NONTRIVIAL_ASSERTIONS && duplex1.getInterval() == null) {
				throw new AssertionFailedException();
			}
			
			List<DuplexRead> overlapping = new ArrayList<>(30);
			for (DuplexRead dr: cleanedUpDuplexes.getOverlapping(duplex1)) {
				overlapping.add(dr);
			}
			Collections.sort(overlapping, (d1, d2) -> Integer.compare(d2.totalNRecords, d1.totalNRecords));
			
			for (DuplexRead duplex2: overlapping) {
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
								duplex1.leftBarcodeNegativeStrand, duplex2.leftBarcodeNegativeStrand);
					}
					if (rightMismatches > 0) {
						registerMismatches(Objects.requireNonNull(duplex1.roughLocation), rightMismatches,
								duplex1.rightBarcode, duplex2.rightBarcode,
								duplex1.rightBarcodeNegativeStrand, duplex2.rightBarcodeNegativeStrand);
					}
					
					if (leftMismatches <= analyzer.nVariableBarcodeMismatchesAllowed &&
							rightMismatches <= analyzer.nVariableBarcodeMismatchesAllowed) {
						duplex2.bottomStrandRecords.addAll(duplex1.bottomStrandRecords);
						duplex2.topStrandRecords.addAll(duplex1.topStrandRecords);

						duplex2.computeConsensus(false, analyzer.variableBarcodeLength);

						for (ExtendedSAMRecord rec: duplex1.bottomStrandRecords) {
							rec.duplexRead = duplex2;
						}
						for (ExtendedSAMRecord rec: duplex1.topStrandRecords) {
							rec.duplexRead = duplex2;
						}
						duplex1.invalid = true;
						mergedDuplex = true;
						break;
					}//End merge duplexes
				}//End duplex alignment distance test
			}//End loop over overlapping duplexes

			if (!mergedDuplex) {
				cleanedUpDuplexes.add(duplex1);
			}
		});//End duplex grouping

		for (DuplexRead duplexRead: cleanedUpDuplexes.getIterable()) {
			if (duplexRead.invalid) {
				throw new AssertionFailedException();
			}
			final @NonNull SequenceLocation roughLocation;
			if (duplexRead.roughLocation != null) {
				roughLocation = duplexRead.roughLocation;
			} else {
				throw new AssertionFailedException();
			}
			if (Arrays.equals(duplexRead.leftBarcode, duplexRead.rightBarcode)) {
				duplexRead.issues.add("BREQ");
				stats.nLociDuplexRescuedFromLeftRightBarcodeEquality.increment(roughLocation);
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
				stats.duplexinsertSize.insert(Math.abs(allDuplexRecords.iterator().next().getInsertSizeNoBarcodes(true)));
			}
			
			int i = 0;
			double sumDisagreementRates = 0d;
			int sumNClipped = 0;
			for (ExtendedSAMRecord r: allDuplexRecords) {
				if (r.getnClipped() < 0) {
					throw new AssertionFailedException();
				}
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
			
			final int inferredSize = Math.abs(allDuplexRecords.iterator().next().getInsertSizeNoBarcodes(true));
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
	
	private void registerMismatches(@NonNull SequenceLocation location, int nMismatches,
			byte @NonNull [] barcode1, byte @NonNull [] barcode2, boolean negativeStrand1,
			boolean negativeStrand2) {
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
						new ComparablePair<>(byteMap.get(first), byteMap.get(second)) :
						new ComparablePair<>(byteMap.get(second), byteMap.get(first));
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

	@SuppressWarnings({ "null"})
	/**
	 * This method is *NOT* thread-safe (it modifies DuplexReads associated with location retrieved
	 * from field candidateSequences)
	 * @param location
	 * @return
	 */
	@NonNull LocationExaminationResults examineLocation(final @NonNull SequenceLocation location) {
		if (NONTRIVIAL_ASSERTIONS && threadCount.incrementAndGet() > 1) {
			throw new AssertionFailedException();
		}

		try {
			final LocationExaminationResults result = new LocationExaminationResults();

			final Set<CandidateSequence> candidateSet = candidateSequences.get(location);
			if (candidateSet == null) {
				stats.nLociUncovered.increment(location);
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
						topCounter, bottomCounter, duplexRead.topStrandRecords,
						duplexRead.bottomStrandRecords, analyzer, stats);
			}
			averageClippingOfCoveringDuplexes /= duplexReads.size();
			
			if (averageClippingOfCoveringDuplexes > analyzer.maxAverageClippingOfAllCoveringDuplexes) {
				for (DuplexRead duplexRead: duplexReads) {
					duplexRead.localQuality.addUnique(
							MAX_AVERAGE_CLIPPING_ALL_COVERING_DUPLEXES, DUBIOUS);
				}
			}

			if (NONTRIVIAL_ASSERTIONS) {
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
					Set<DuplexRead> duplexes = c.getNonMutableConcurringReads().keySet().stream().
							map(r -> {
								DuplexRead d = r.duplexRead;
								if (NONTRIVIAL_ASSERTIONS && d.invalid) {
									throw new AssertionFailedException();
								}
								return d;
							}).
							collect(uniqueValueCollector());//Collect *unique* duplexes
					for (CandidateSequence c2: candidateSet) {
						if (c2 == c) {
							continue;
						}
						c2.getNonMutableConcurringReads().keySet().
						forEach(r -> {
							DuplexRead d = r.duplexRead;
								if (duplexes.contains(d)) {
									throw new AssertionFailedException("Duplex " + d +
											" associated with candidates " + c + " and " + c2);
								}
							}
						);
					}
				}
			}//End assertion block

			if (NONTRIVIAL_ASSERTIONS) {
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
			}

			Quality maxQuality = MINIMUM;

			byte wildtypeBase = 'X';

			final int totalReadsAtPosition = candidateSet.stream().
					mapToInt(c -> c.getNonMutableConcurringReads().size()).sum();

			int totalGoodDuplexes = 0, totalGoodOrDubiousDuplexes = 0,
					totalGoodDuplexesIgnoringDisag = 0, totalAllDuplexes = 0;

			final TByteArrayList allPhredQualitiesAtLocus = new TByteArrayList(500);
			int nWrongPairsAtLocus = 0;
			int nPairsAtLocus = 0;
			
			for (CandidateSequence candidate: candidateSet) {
				candidate.addPhredQualitiesToList(allPhredQualitiesAtLocus);
				nPairsAtLocus += candidate.getNonMutableConcurringReads().size();
				candidate.setnWrongPairs((int) candidate.getNonMutableConcurringReads().keySet().stream().
						filter(ExtendedSAMRecord::formsWrongPair).count());
				nWrongPairsAtLocus += candidate.getnWrongPairs();
			}
			
			Quality maxQForAllDuplexes = MAXIMUM;
			
			final int nPhredQualities = allPhredQualitiesAtLocus.size();
			allPhredQualitiesAtLocus.sort();
			final byte locusMedianPhred = nPhredQualities == 0 ? 127 :
				allPhredQualitiesAtLocus.get(nPhredQualities / 2); 
			if (locusMedianPhred < analyzer.minMedianPhredQualityAtLocus) {
				maxQForAllDuplexes = DUBIOUS;
				stats.nMedianPhredAtLocusTooLow.increment(location);
			}
			stats.medianLocusPhredQuality.insert(locusMedianPhred);

			if (nWrongPairsAtLocus / ((float) nPairsAtLocus) > analyzer.maxFractionWrongPairsAtLocus) {
				maxQForAllDuplexes = DUBIOUS;
				stats.nFractionWrongPairsAtLocusTooHigh.increment(location);
			}
			
			if (maxQForAllDuplexes.compareTo(GOOD) < 0) {
				result.disagreements.clear();
			} else {
				candidateSet.stream().
						flatMap(c -> c.getRawMismatchesQ2().stream()).
						forEach(result.rawMismatchesQ2::add);
				candidateSet.stream().flatMap(c -> c.getRawInsertionsQ2().stream()).
						forEach(result.rawInsertionsQ2::add);
				candidateSet.stream().flatMap(c -> c.getRawDeletionsQ2().stream()).
						forEach(result.rawDeletionsQ2::add);
			}
			
			for (CandidateSequence candidate: candidateSet) {
				candidate.getQuality().reset();
				
				candidate.setMedianPhredAtLocus(locusMedianPhred);
				
				candidate.setDuplexes(candidate.getNonMutableConcurringReads().keySet().stream().
						map(r -> {
								DuplexRead d = r.duplexRead;
								if (NONTRIVIAL_ASSERTIONS && d.invalid) {
									throw new AssertionFailedException();
								}
								return d;
							}
						).collect(uniqueValueCollector()));//Collect *unique* duplexes

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
					candidate.setDistancesToLigSite(candidate.getDuplexes().stream().map(DuplexRead::getDistanceToLigSite).
						collect(TIntListCollector.tIntListCollector()));
					candidate.getNonMutableConcurringReads().forEachKey(r -> {
						final int refPosition = location.position;
						final int readPosition = r.referencePositionToReadPosition(refPosition);
						final int distance = r.tooCloseToBarcode(refPosition, readPosition, 0);
						if (Math.abs(distance) > 150 && !candidate.getMutationType().isWildtype()) {
							throw new AssertionFailedException("Distance problem with candidate " + candidate +
									" read at read position " + readPosition + " and refPosition " +
									refPosition + " " + r.toString() + " in analyzer" +
									analyzer.inputBam.getAbsolutePath() + "; distance is " + distance);
						}
						if (distance >= 0) {
							stats.Q2CandidateDistanceToLigationSite.insert(distance);
						} else {
							stats.Q2CandidateDistanceToLigationSiteN.insert(-distance);
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
							return other + ":" + er.getMateAlignmentStartNoBarcode();
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
						getInsertSizeNoBarcodes(true))).summaryStatistics();
					final int localMaxInsertSize = insertSizeStats.getMax();
					final int localMinInsertSize = insertSizeStats.getMin();

					candidate.setMinInsertSize(localMinInsertSize);
					candidate.setMaxInsertSize(localMaxInsertSize);

					if (localMaxInsertSize < analyzer.minInsertSize || localMinInsertSize > analyzer.maxInsertSize) {
						candidate.setHasFunnyInserts(true);
						candidate.getQuality().add(INSERT_SIZE, DUBIOUS);
					}
					
					final boolean has0PredictedInsertSize = localMinInsertSize == 0;

					final boolean hasNoMate = candidate.getNonMutableConcurringReads().keySet().stream().map(er -> er.record.
							getMateReferenceName() == null).reduce(false, Boolean::logicalOr);

					if (localMaxInsertSize > analyzer.maxInsertSize) {
						supplementalMessage.append("one predicted insert size is " + 
								NumberFormat.getInstance().format(localMaxInsertSize)).append("; ");
					}

					if (localMinInsertSize < analyzer.minInsertSize) {
						supplementalMessage.append("one predicted insert size is " + 
								NumberFormat.getInstance().format(localMinInsertSize)).append("; ");
					}

					candidate.setAverageMappingQuality((int) candidate.getNonMutableConcurringReads().keySet().stream().
							mapToInt(r -> r.record.getMappingQuality()).summaryStatistics().getAverage());

					if (!"".equals(hasMateOnOtherChromosome)) {
						supplementalMessage.append ("pair elements map to other chromosomes: " + hasMateOnOtherChromosome).append("; ");
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
				stats.nLociQualityPoor.increment(location);
				switch (wildtypeBase) {
					case 'A' : stats.nLociQualityPoorA.increment(location); break;
					case 'T' : stats.nLociQualityPoorT.increment(location); break;
					case 'G' : stats.nLociQualityPoorG.increment(location); break;
					case 'C' : stats.nLociQualityPoorC.increment(location); break;
					case 'X' : break; //Ignore because we do not have a record of wildtype sequence
					default : throw new AssertionFailedException();
				}
			} else if (maxQuality == DUBIOUS) {
				stats.nLociQualityQ1.increment(location);
			} else if (maxQuality == GOOD) {
				stats.nLociQualityQ2.increment(location);
			} else { 
				throw new AssertionFailedException();
			}
			result.analyzedCandidateSequences = candidateSet;
			return result;
		} finally {
			threadCount.decrementAndGet();
		}
	}//End examineLocation

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
		@NonNull final String readFullName = ExtendedSAMRecord.getReadFullName(record);
		return extSAMCache.computeIfAbsent(readFullName, s ->
			new ExtendedSAMRecord(record, readFullName, analyzer, location, extSAMCache));
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
		if (extendedRec.processed) {
			throw new AssertionFailedException("Double processing of record " + extendedRec.getFullName());
		}
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
		
		final Integer insertSizeNoBarcode = extendedRec.getInsertSizeNoBarcodes(false);
		final int insertSize = insertSizeNoBarcode != null ? insertSizeNoBarcode : rec.getInferredInsertSize();
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

		int localIgnoreLastNBases = analyzer.ignoreLastNBases;
		
		int adjustedLength = rec.getReadLength() - analyzer.unclippedBarcodeLength + analyzer.ignoreFirstNBasesQ1;
		if (insertSizeAbs > 0 && insertSizeAbs < adjustedLength) {
			//Reads ran into adapter ligated at the other end
			//Update ignoreLastNBases to stay sufficiently away from ligation site
			localIgnoreLastNBases = Math.max(adjustedLength - insertSizeAbs,
					analyzer.ignoreLastNBases);
		}

		int refEndOfPreviousAlignment = -1;
		int readEndOfPreviousAlignment = -1;

		int returnValue = -1;

	if (insertSize == 0) {
		stats.nReadsInsertNoSize.increment(location);
		return -1;
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
				if (Mutinack.shouldLog(TRACE)) {
					logger.trace("Ignoring indel too close to end " + readPosition + (readOnNegativeStrand ? " neg strand " : " pos strand ") + readPosition + " " + (rec.getReadLength() - 1) + " " + extendedRec.getFullName());
				}
				stats.nCandidateIndelAfterLastNBases.increment(location);
			} else {
				if (insertion) {
					//Insertion
					stats.nCandidateInsertions.increment(location);
					if (Mutinack.shouldLog(TRACE)) {
						logger.trace("Insertion at position " + readPosition + " for read " + rec.getReadName() +
							" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
							"; insert size: " + insertSize + "; localIgnoreLastNBases: " + localIgnoreLastNBases + ")");
					}
					location = new SequenceLocation(ref.getContigIndex(), refEndOfPreviousAlignment, true);
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

						candidate.acceptLigSiteDistance(distance);

						candidate.insertSize = insertSize;
						candidate.localIgnoreLastNBases = localIgnoreLastNBases;
						candidate.positionInRead = readPosition;
						candidate.readEL = effectiveReadLength;
						candidate.readName = extendedRec.getFullName();
						candidate.readAlignmentStart = extendedRec.getRefAlignmentStartNoBarcode();
						candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStartNoBarcode();
						candidate.readAlignmentEnd = extendedRec.getRefAlignmentEndNoBarcode();
						candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEndNoBarcode();
						candidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
						candidate.insertSizeNoBarcodeAccounting = insertSizeNoBarcode == null;
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
											distance > analyzer.ignoreFirstNBasesQ2) {
										candidate.getMutableRawInsertionsQ2().add(mutationPair);
									}
						}

						if (Mutinack.shouldLog(TRACE)) {
							logger.trace("Insertion of " + new String(candidate.getSequence()) + " at ref " + refPosition + " and read position " + readPosition + " for read " + extendedRec.getFullName());
						}
						readLocalCandidates.add(candidate, location);
						forceCandidateInsertion = true;
						if (Mutinack.shouldLog(TRACE)) {
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
						if (Mutinack.shouldLog(TRACE)) {
							logger.trace("Deletion at position " + readPosition + " for read " + rec.getReadName() +
									" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
									"; insert size: " + insertSize + "; localIgnoreLastNBases: " + localIgnoreLastNBases + ")");
						}

						final int deletionLength = refPosition - (refEndOfPreviousAlignment + 1);
						location = new SequenceLocation(ref.getContigIndex(), refEndOfPreviousAlignment + 1);
						final @NonNull SequenceLocation deletionEnd = new SequenceLocation(ref.getContigIndex(), location.position + deletionLength);

						final byte @Nullable[] deletedSequence = notRnaSeq 	? Arrays.copyOfRange(ref.getBases(),
								refEndOfPreviousAlignment + 1, refPosition)
								: null	;

						//Add hidden mutations to all locations covered by deletion
						//So disagreements between deletions that have only overlapping
						//spans are detected.
						for (int i = 1; i < deletionLength; i++) {
							SequenceLocation location2 = new SequenceLocation(ref.getContigIndex(), refEndOfPreviousAlignment + 1 + i);
							CandidateSequence hiddenCandidate = new CandidateDeletion(analyzer.idx, location2, extendedRec, Integer.MAX_VALUE,
									location, deletionEnd);
							hiddenCandidate.setHidden(true);
							hiddenCandidate.insertSize = insertSize;
							hiddenCandidate.insertSizeNoBarcodeAccounting = insertSizeNoBarcode == null;
							hiddenCandidate.localIgnoreLastNBases = localIgnoreLastNBases;
							hiddenCandidate.positionInRead = readPosition;
							hiddenCandidate.readEL = effectiveReadLength;
							hiddenCandidate.readName = extendedRec.getFullName();
							hiddenCandidate.readAlignmentStart = extendedRec.getRefAlignmentStartNoBarcode();
							hiddenCandidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStartNoBarcode();
							hiddenCandidate.readAlignmentEnd = extendedRec.getRefAlignmentEndNoBarcode();
							hiddenCandidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEndNoBarcode();
							hiddenCandidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
							hiddenCandidate.setSequence(deletedSequence);
							readLocalCandidates.add(hiddenCandidate, location2);
						}

						final CandidateSequence candidate = new CandidateDeletion(analyzer.idx, location, extendedRec, distance,
								location, new SequenceLocation(ref.getContigIndex(), refPosition));

						candidate.acceptLigSiteDistance(distance);

						candidate.insertSize = insertSize;
						candidate.insertSizeNoBarcodeAccounting = insertSizeNoBarcode == null;
						candidate.localIgnoreLastNBases = localIgnoreLastNBases;
						candidate.positionInRead = readPosition;
						candidate.readEL = effectiveReadLength;
						candidate.readName = extendedRec.getFullName();
						candidate.readAlignmentStart = extendedRec.getRefAlignmentStartNoBarcode();
						candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStartNoBarcode();
						candidate.readAlignmentEnd = extendedRec.getRefAlignmentEndNoBarcode();
						candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEndNoBarcode();
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
											distance > analyzer.ignoreFirstNBasesQ2) {
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
				new SequenceLocation(ref.getContigIndex(), refPosition, true)
				: null;
			/*final boolean endOfRead = readOnNegativeStrand ? readPosition == 0 :
				readPosition == effectiveReadLength - 1;*/

			location = new SequenceLocation(ref.getContigIndex(), refPosition);
			
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
					if (Mutinack.shouldLog(TRACE)) {
						logger.trace("Ignoring subst too close to barcode for read " + rec.getReadName());
					}
					insertCandidateAtRegularPosition = false;
				} else if (tooLate && insertCandidateAtRegularPosition) {
					stats.nCandidateSubstitutionsAfterLastNBases.increment(location);
					if (Mutinack.shouldLog(TRACE)) {
						logger.trace("Ignoring subst too close to read end for read " + rec.getReadName());
					}
					insertCandidateAtRegularPosition = false;
				}
				if (goodToInsert || forceCandidateInsertion) {
					if (Mutinack.shouldLog(TRACE)) {
						logger.trace("Substitution at position " + readPosition + " for read " + rec.getReadName() +
							" (effective length: " + effectiveReadLength + "; reversed:" + readOnNegativeStrand + 
							"; insert size: " + insertSize + "; localIgnoreLastNBases: " + localIgnoreLastNBases + ")");
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

					candidate.acceptLigSiteDistance(distance);
					
					candidate.insertSize = insertSize;
					candidate.insertSizeNoBarcodeAccounting = insertSizeNoBarcode == null;
					candidate.localIgnoreLastNBases = localIgnoreLastNBases;
					candidate.positionInRead = readPosition;
					candidate.readEL = effectiveReadLength;
					candidate.readName = extendedRec.getFullName();
					candidate.readAlignmentStart = extendedRec.getRefAlignmentStartNoBarcode();
					candidate.mateReadAlignmentStart = extendedRec.getMateRefAlignmentStartNoBarcode();
					candidate.readAlignmentEnd = extendedRec.getRefAlignmentEndNoBarcode();
					candidate.mateReadAlignmentEnd = extendedRec.getMateRefAlignmentEndNoBarcode();
					candidate.refPositionOfMateLigationSite = extendedRec.getRefPositionOfMateLigationSite();
					final byte[] mutSequence = new byte[] {readBases[readPosition]};
					candidate.setSequence(mutSequence);
					candidate.setWildtypeSequence(wildType);
					if (insertCandidateAtRegularPosition) {
						readLocalCandidates.add(candidate, location);
					}
					if (locationPH != null) {
						final CandidateSequence candidate2 = new CandidateSequence(analyzer.idx, 
								WILDTYPE, locationPH, extendedRec, distance);
						candidate2.acceptLigSiteDistance(distance);
						candidate2.setWildtypeSequence(wildType);
						candidate2.setSequence(mutSequence);
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
							distance > analyzer.ignoreFirstNBasesQ2) {
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
					if (distance < -150) {
						throw new AssertionFailedException("Distance problem 1 at read position " + readPosition +
								" and refPosition " + refPosition + " " + extendedRec.toString() +
								" in analyzer" + analyzer.inputBam.getAbsolutePath() +
								"; distance is " + distance + "");
					}
					distance = extendedRec.tooCloseToBarcode(refPosition, readPosition, 0);
					if (insertCandidateAtRegularPosition) {
						 stats.wtAcceptedBaseDistanceToLigationSite.insert(-distance);
					}
				}
								
				if (((!readOnNegativeStrand && readPosition > readBases.length - 1 - localIgnoreLastNBases) || 
						(readOnNegativeStrand && readPosition < localIgnoreLastNBases))) {
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
				candidate.acceptLigSiteDistance(-distance);
				candidate.setWildtypeSequence(StringUtil.toUpperCase(refBases[refPosition]));
				if (insertCandidateAtRegularPosition) {
					readLocalCandidates.add(candidate, location);
				}
				if (locationPH != null) {
					final CandidateSequence candidate2 = new CandidateSequence(analyzer.idx, 
							WILDTYPE, locationPH, extendedRec, -distance);
					candidate2.acceptLigSiteDistance(-distance);
					candidate2.setWildtypeSequence(StringUtil.toUpperCase(refBases[refPosition]));
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
