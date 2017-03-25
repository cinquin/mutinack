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
import static uk.org.cinquin.mutinack.misc_util.DebugLogControl.ENABLE_TRACE;

import java.io.File;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileReader.QueryInterval;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.map.TMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.InterningSet;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.sequence_IO.IteratorPrefetcher;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public class ReadLoader {

	private static final boolean IGNORE_SECONDARY_MAPPINGS = true;

	private static final Logger logger = LoggerFactory.getLogger("ReadLoader");
	private static final Logger statusLogger = LoggerFactory.getLogger("ReadLoaderStatus");

	@SuppressWarnings("resource")
	public static void load(
			Mutinack analyzer,
			Parameters param,
			MutinackGroup groupSettings,
			SubAnalyzer subAnalyzer,
			AnalysisChunk analysisChunk,
			final int PROCESSING_CHUNK,
			@NonNull List<@NonNull String> contigs,
			final int contigIndex,
			SAMFileWriter alignmentWriter,
			Function<String, ReferenceSequence> contigSequences) {

		final @NonNull List<@NonNull AnalysisStats> stats = analyzer.stats;

		final SettableInteger lastProcessable = subAnalyzer.lastProcessablePosition;
		final int startAt = analysisChunk.startAtPosition;

		lastProcessable.set(startAt - 1);
		analysisChunk.lastProcessedPosition = startAt - 1;
		analysisChunk.pauseAtPosition = lastProcessable.get() + PROCESSING_CHUNK;
		Assert.isTrue(analysisChunk.pauseAtPosition >= analysisChunk.lastProcessedPosition);

		final boolean needRandom = param.dropReadProbability > 0 ||
			param.randomizeMates;
		final Random random = needRandom ?
				new Random(param.randomSeed)
			:
				null;

		final @NonNull Phaser phaser = Objects.requireNonNull(analysisChunk.phaser);
		String lastContigName = null;
		BiConsumer<PrintStream, Integer> info = null;
		try {
			final String contigName = contigs.get(contigIndex);
			final int truncateAtPosition = analysisChunk.terminateAtPosition;

			final Set<String> droppedReads = param.dropReadProbability > 0 ? new THashSet<>(1_000_000) : null;
			final Set<String> keptReads = param.dropReadProbability > 0 ?  new THashSet<>(1_000_000) : null;

			final Set<String> switchedMatePairs = param.randomizeMates ? new THashSet<>(1_000_000) : null;
			final Set<String> unswitchedMatePairs = param.randomizeMates ? new THashSet<>(1_000_000) : null;

			final SequenceLocation contigLocation = new SequenceLocation(contigIndex, contigName, 0);

			info = (stream, userRequestNumber) -> {
				NumberFormat formatter = DoubleAdderFormatter.nf.get();
				stream.println("Analyzer " + analyzer.name +
						" contig " + contigName +
						" range: " + (analysisChunk.startAtPosition + "-" + analysisChunk.terminateAtPosition) +
						"; pauseAtPosition: " + formatter.format(analysisChunk.pauseAtPosition) +
						"; lastProcessedPosition: " + formatter.format(analysisChunk.lastProcessedPosition) + "; " +
						formatter.format(100f * (analysisChunk.lastProcessedPosition - analysisChunk.startAtPosition) /
								(analysisChunk.terminateAtPosition - analysisChunk.startAtPosition)) + "% done"
						);
			};
			synchronized(groupSettings.statusUpdateTasks) {
				groupSettings.statusUpdateTasks.add(info);
			}

			final SAMFileReader bamReader = analyzer.readerPool.getObj();
			SAMRecordIterator it0 = null;
			try {

				if (contigs.get(0).equals(contigName)) {
					analyzer.stats.forEach(s -> s.nRecordsInFile.add(contigLocation, Util.getTotalReadCount(bamReader)));
				}

				int furthestPositionReadInContig = 0;
				final int maxInsertSize = param.maxInsertSize;
				final QueryInterval[] bamContig = {
						bamReader.makeQueryInterval(contigName, Math.max(1, startAt - maxInsertSize + 1))};
				analyzer.timeStartProcessing = System.nanoTime();
				final TMap<String, Pair<@NonNull ExtendedSAMRecord, @NonNull ReferenceSequence>>
					readsToProcess = new THashMap<>(5_000);

				subAnalyzer.truncateProcessingAt = truncateAtPosition;
				subAnalyzer.startProcessingAt = startAt;

				final List<Iterator<SAMRecord>> intersectionIterators = new ArrayList<>();

				for (File f: analyzer.intersectAlignmentFiles) {
					SAMFileReader reader = new SAMFileReader(f);
					intersectionIterators.add(new IteratorPrefetcher<>(reader.queryOverlapping(bamContig),
						100, reader, e -> {}, null));
				}
				final int nIntersect = intersectionIterators.size();
				final Map<Integer, Integer> intersectionWaitUntil = new HashMap<>();
				for (int z = 0; z < nIntersect; z++) {
					intersectionWaitUntil.put(z, -1);
				}

				int previous5p = -1;
				SimpleCounter<Pair<String, Integer>> intersectReads = new SimpleCounter<>();
				SimpleCounter<Pair<String, Integer>> readAheadStash = new SimpleCounter<>();

				try {
					it0 = bamReader.queryOverlapping(bamContig);
				} catch (Exception e) {
					throw new RuntimeException("Problem with sample " + analyzer.name, e);
				}

				Handle<Boolean> firstRun = new Handle<>(true);
				subAnalyzer.stats = subAnalyzer.analyzer.stats.get(0);

				final InterningSet<@NonNull SequenceLocation> locationInterningSet = new InterningSet<>(10_000);

				try (IteratorPrefetcher<SAMRecord> iterator = new IteratorPrefetcher<>(it0, 100, it0,
						e -> {
							//Work around BWA output problem with reads that hang off the reference end
							//See e.g. https://www.biostars.org/p/65338/
							if (e.getReadUnmappedFlag() && e.getAlignmentStart() > 0) {
								e.setReadUnmappedFlag(false);
							}
							//e.eagerDecode();
							e.getUnclippedEnd();
							e.getUnclippedStart();
						},
						subAnalyzer.stats.nReadsInPrefetchQueue))//TODO Create a sample-wide stats object
				{
					while (iterator.hasNext() && !phaser.isTerminated() && !groupSettings.terminateAnalysis) {

						final SAMRecord samRecord = iterator.next();

						if (alignmentWriter != null) {
							samRecord.setAttribute("DS", null); //If not output read will not be written
							//if reading from an already-annotated BAM file
						}

						final @NonNull SequenceLocation location =
							SequenceLocation.get(locationInterningSet, contigIndex, contigName, samRecord.getAlignmentStart());

						if (!samRecord.getReadPairedFlag()) {
							if (!analyzer.notifiedUnpairedReads) {
								Util.printUserMustSeeMessage("Ignoring unpaired reads");
								analyzer.notifiedUnpairedReads = true;
							}
							analyzer.stats.forEach(s -> s.ignoredUnpairedReads.increment(location));
							continue;
						}

						if (param.randomizeMates) {
							Objects.requireNonNull(switchedMatePairs);
							Objects.requireNonNull(unswitchedMatePairs);
							Objects.requireNonNull(random);
							if (!samRecord.getMateUnmappedFlag() &&
								samRecord.getMateReferenceIndex().equals(samRecord.getReferenceIndex())) {
								String readName = samRecord.getReadName();
								if (unswitchedMatePairs.remove(readName)) {
									//Nothing to do
								} else if (switchedMatePairs.remove(readName)) {
									switchPair(samRecord);
								} else {
									if (random.nextFloat() < 0.5) {
										switchedMatePairs.add(readName);
										switchPair(samRecord);
									} else {
										unswitchedMatePairs.add(readName);
									}
								}
							} else {
								if (random.nextFloat() < 0.5) {
									switchPair(samRecord);
								}
							}
						}

						final int current5p = samRecord.getAlignmentStart();
						if (current5p < previous5p) {
							throw new IllegalArgumentException("Unsorted input");
						}
						if (nIntersect > 0 && current5p > previous5p) {
							final Iterator<Entry<Pair<String, Integer>, SettableInteger>> esit = intersectReads.entrySet().iterator();
							while (esit.hasNext()) {
								if (esit.next().getKey().snd < current5p - 6) {
									stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
									esit.remove();
								}
							}
							final Set<Entry<Pair<String, @NonNull Integer>, SettableInteger>> readAheadStashEntries =
									new HashSet<>(readAheadStash.entrySet());
							for (Entry<Pair<String, @NonNull Integer>, SettableInteger> e: readAheadStashEntries) {
								if (e.getKey().getSnd() < current5p - 6) {
									stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
									readAheadStash.removeAll(e.getKey());
								} else if (e.getKey().getSnd() <= current5p + 6) {
									readAheadStash.removeAll(e.getKey());
									intersectReads.put(e.getKey(), e.getValue().get());
								}
							}

							for (int i = 0; i < nIntersect; i++) {
								if (current5p + 6 < Objects.requireNonNull(intersectionWaitUntil.get(i))) {
									continue;
								}
								Iterator<SAMRecord> it = intersectionIterators.get(i);
								while (it.hasNext()) {
									SAMRecord ir = it.next();
									if (ir.getMappingQuality() < param.minMappingQIntersect.get(i)) {
										stats.forEach(s -> s.nTooLowMapQIntersect.accept(location));
										continue;
									}
									int ir5p = ir.getAlignmentStart();
									final Pair<String, Integer> pair = new Pair<>(ExtendedSAMRecord.getReadFullName(ir, false), ir5p);
									if (ir5p < current5p - 6) {
										stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
									} else if (ir5p <= current5p + 6) {
										intersectReads.put(pair);
									} else {
										readAheadStash.put(pair);
										intersectionWaitUntil.put(i, ir5p);
										break;
									}
								}
							}
						}
						previous5p = current5p;

						if (nIntersect > 0 &&
								intersectReads.get(new Pair<>(ExtendedSAMRecord.getReadFullName(samRecord, false),
										current5p)) < nIntersect) {
							stats.forEach(s -> s.nRecordsNotInIntersection2.accept(location));
							continue;
						}

						if (param.dropReadProbability > 0) {
							Objects.requireNonNull(droppedReads);
							Objects.requireNonNull(keptReads);
							Objects.requireNonNull(random);
							String readName = samRecord.getReadName();
							boolean mateAlreadyDropped = droppedReads.contains(readName);
							if (mateAlreadyDropped) {
								droppedReads.remove(readName);
								continue;
							}
							if (keptReads.contains(readName)) {
								keptReads.remove(readName);
							} else if (random.nextFloat() < param.dropReadProbability) {
								//If mate is in a different contig, do not store a reference
								//to the read name in droppedReads since this thread will never
								//encounter the mate
								if (samRecord.getMateReferenceIndex().equals(samRecord.getReferenceIndex()))
									droppedReads.add(readName);
								continue;
							} else {//Keep the pair
								//If mate is in a different contig, do not store a reference
								//to the read name in keptReads since this thread will never
								//encounter the mate
								if (samRecord.getMateReferenceIndex().equals(samRecord.getReferenceIndex()))
									keptReads.add(readName);
							}
						}

						stats.forEach(s -> s.nRecordsProcessed.increment(location));

						if (contigs.size() < 100 && !param.rnaSeq) {
							//Summing below is a bottleneck when there are a large
							//number of contigs (tens of thousands)
							if (stats.get(0).nRecordsProcessed.sum() > param.nRecordsToProcess) {
								statusLogger.info("Analysis of contig " + contigName + " stopping "
										+ "because it processed over " + param.nRecordsToProcess + " records");
								phaser.forceTermination();
								break;
							}
						}

						final String samRecordContigName = samRecord.getReferenceName();
						if ("*".equals(samRecordContigName)) {
							stats.forEach(s -> s.nRecordsUnmapped.increment(location));
							continue;
						}

						if (!samRecordContigName.equals(contigName)) {
							throw new AssertionFailedException(samRecordContigName + " vs " + contigName);
						}

						if (IGNORE_SECONDARY_MAPPINGS && samRecord.getNotPrimaryAlignmentFlag()) {
							stats.forEach(s -> s.nRecordsIgnoredBecauseSecondary.increment(location));
							continue;
						}

						int mappingQuality = samRecord.getMappingQuality();
						stats.forEach(s -> s.mappingQualityAllRecords.insert(mappingQuality));
						if (mappingQuality < param.minMappingQualityQ1) {
							stats.forEach(s -> s.nRecordsBelowMappingQualityThreshold.increment(location));
							continue;
						}

						if (lastContigName != null && !contigName.equals(lastContigName)) {
							furthestPositionReadInContig = 0;
						}
						lastContigName = contigName;

						lastProcessable.set(samRecord.getAlignmentStart() - 2);

						final boolean finishUp;
						if (lastProcessable.get() >= truncateAtPosition + maxInsertSize) {
							statusLogger.debug("Analysis of contig " + contigName + " stopping "
									+ "because it went past " + truncateAtPosition);
							finishUp = true;
						} else {
							finishUp = false;
						}

						ReferenceSequence ref = Objects.requireNonNull(contigSequences.apply(contigName));

						//Put samRecord into the cache
						ExtendedSAMRecord extended = subAnalyzer.getExtended(samRecord, location);

						if (!extended.getReferenceName().equals(contigName)) {
							throw new AssertionFailedException(extended.getReferenceName() + " vs " + contigName);
						}

						if (extended.getReferenceIndex() != contigIndex) {
							throw new AssertionFailedException(extended.getReferenceIndex() + " vs " + contigIndex);
						}

						//Put samRecord into map of reads to possibly be processed in next batch
						final @Nullable Pair<@NonNull ExtendedSAMRecord, @NonNull ReferenceSequence> previous;
						if ((previous = readsToProcess.put(extended.getFullName(), new Pair<>(extended, ref))) != null) {
							if (param.allowMissingSupplementaryFlag) {
								readsToProcess.put(extended.getFullName(), previous);//Put back previous record
								if (extended.record.getSupplementaryAlignmentFlag() ||
										previous.fst.record.getSupplementaryAlignmentFlag()) {
									throw new RuntimeException();
								}
								//TODO This essentially picks one of the alignments at random to mark as supplementary
								//That does not affect anything else, but that should be improved as some point
								samRecord.setSupplementaryAlignmentFlag(true);
								extended = subAnalyzer.getExtended(samRecord, location);
								if (readsToProcess.put(extended.getFullName(), new Pair<>(extended, ref)) != null) {
									throw new RuntimeException("Read " + extended.getFullName() + " read twice from " +
										analyzer.inputBam.getAbsolutePath());
								}
							} else if (!param.randomizeMates) {//The assertion can be triggered when randomizing, probably
								//because of supplementary alignments; just suppress the assertion when randomizing:
								//the duplication could be indicative of a problem upstream, but otherwise it should
								//be harmless
								throw new RuntimeException("Read " + extended.getFullName() + " read twice from " +
									analyzer.inputBam.getAbsolutePath());
							}
						}
						furthestPositionReadInContig = Math.max(furthestPositionReadInContig,
								samRecord.getAlignmentEnd() - 1);

						//Use phaser here and go by specific positions rather than
						//actually processed records
						if (finishUp || lastProcessable.get() >= analysisChunk.pauseAtPosition + maxInsertSize) {
							if (ENABLE_TRACE && shouldLog(TRACE)) {
								logger.trace("Member of phaser " + phaser + " reached " + lastProcessable +
										"; pauseAtPosition is " + analysisChunk.pauseAtPosition);
							}

							/**
							 * Meat of the processing is done here by calling method processRead.
							 * Only process the reads that will contribute to mutation candidates to be analyzed in the
							 * next round.
							 */
							final int localPauseAt = analysisChunk.pauseAtPosition;
							readsToProcess.retainEntries((k, v) -> {
								final ExtendedSAMRecord read = v.fst;
								if (firstRun.get() && read.getAlignmentStart() + maxInsertSize < startAt) {
									//The purpose of this was to make runs reproducible irrespective of
									//the way contigs are broken up for parallelization; probably largely redundant now
									return false;
								}
								if (read.getAlignmentStart() - 1 <= localPauseAt ||
										read.getMateAlignmentStart() - 1 <= localPauseAt) {
									subAnalyzer.processRead(location, locationInterningSet, read, v.snd);
									return false;
								}
								return true;
							}
								);
							firstRun.set(false);
							locationInterningSet.clear();
							phaser.arriveAndAwaitAdvance();
						}
						if (finishUp) {
							break;
						}
					}//End samRecord loop
				}//End iterator try block

				logger.trace("Member of phaser " + phaser + " reached final " + lastProcessable +
						"; pauseAtPosition is " + analysisChunk.pauseAtPosition);

				if (analysisChunk.lastProcessedPosition < truncateAtPosition) {
					readsToProcess.forEach((k, v) -> {
						final SequenceLocation location = SequenceLocation.get(locationInterningSet, contigIndex, contigName,
							v.fst.record.getAlignmentStart());
						subAnalyzer.processRead(location, locationInterningSet, v.fst, v.snd);
					});
				}

				if (truncateAtPosition == Integer.MAX_VALUE) {
					lastProcessable.set(furthestPositionReadInContig);
				} else {
					lastProcessable.set(truncateAtPosition + 1);
				}

				while (!phaser.isTerminated() && !groupSettings.terminateAnalysis) {
					phaser.arriveAndAwaitAdvance();
				}

				logger.debug("Done processing contig " + contigName + " of file " + analyzer.inputBam);
			} finally {
				if (it0 != null) {
					it0.close();
				}
				analyzer.readerPool.returnObj(bamReader);
			}
		} catch (Throwable t) {
			Util.printUserMustSeeMessage("Exception " + t +
					" on thread " + Thread.currentThread());
			groupSettings.errorOccurred(t);
			phaser.forceTermination();
			throw new RuntimeException("Exception while processing contig " + lastContigName +
					" of file " + analyzer.inputBam.getAbsolutePath(), t);
		} finally {
			if (info != null) {
				synchronized(groupSettings.statusUpdateTasks) {
					if (!groupSettings.statusUpdateTasks.remove(info)) {
						logger.warn("Could not remove status udpate task");
					}
				}
			}
			if (groupSettings.terminateAnalysis) {
				phaser.forceTermination();
			}
		}
		//Ensure that there is no memory leak (references are kept to subAnalyzers,
		//at least through the status update handlers; XXX not clear if the maps
		//should already have been completely cleared as part of position examination).
		clearSubAnalyzers(analysisChunk);
	}

	//It's OK for clearing to occur multiple times, but not concurrently (which
	//can generate an NPE)
	private static synchronized void clearSubAnalyzers(AnalysisChunk analysisChunk) {
		analysisChunk.subAnalyzers.forEach(sa -> {
			sa.extSAMCache.clear();
			sa.extSAMCache.compact();
			sa.candidateSequences.clear();
			sa.candidateSequences.compact();
			if (sa.analyzedDuplexes != null) {
				//An exception may have occurred before analyzedDuplexes was allocated,
				//so check for null
				sa.analyzedDuplexes.clear();
				sa.analyzedDuplexes = null;
			}
			sa.averageClipping = null;
			nullReadsToWrite(sa);//Set to null to generate NPE is an attempt is made
			//to reuse it
		});
		//TODO Clear subAnalyzers list, but *only after* all analyzers have completed
		//that chunk
		//analysisChunk.subAnalyzers.clear();
	}

	@SuppressWarnings("null")
	private static void nullReadsToWrite(SubAnalyzer sa) {
		sa.readsToWrite = null;
	}

	private static void switchPair(SAMRecord r) {
		r.setFirstOfPairFlag(!r.getFirstOfPairFlag());
		r.setSecondOfPairFlag(!r.getSecondOfPairFlag());
	}

	static boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}

}
