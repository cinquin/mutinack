package uk.org.cinquin.mutinack;

import static contrib.uk.org.lidalia.slf4jext.Level.TRACE;
import static uk.org.cinquin.mutinack.misc_util.DebugControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.Util.getRecordNameWithPairSuffix;

import java.io.File;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Phaser;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.picard.reference.ReferenceSequenceFile;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import contrib.net.sf.samtools.SAMFileReader.QueryInterval;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.sequence_IO.IteratorPrefetcher;

public class ReadLoader {
	
	private final static boolean IGNORE_SECONDARY_MAPPINGS = true;
	
	private final static Logger logger = LoggerFactory.getLogger("ReadLoader");
	private final static Logger statusLogger = LoggerFactory.getLogger("ReadLoaderStatus");
	
	@SuppressWarnings("resource")
	public static void load (Mutinack analyzer, SubAnalyzer subAnalyzer, AnalysisChunk analysisChunk,
			Parameters argValues, List<AnalysisChunk> analysisChunks,
			final int PROCESSING_CHUNK, @NonNull List<@NonNull String> contigs, final int contigIndex,
			SAMFileWriter alignmentWriter, Map<String, ReferenceSequence> refMap,
			ReferenceSequenceFile refFile) {

		final SettableInteger lastProcessable = subAnalyzer.lastProcessablePosition;
		final int startAt = analysisChunk.startAtPosition;
		final SettableInteger pauseAt = analysisChunk.pauseAtPosition;
		final SettableInteger lastProcessedPosition = analysisChunk.lastProcessedPosition;
		
		lastProcessable.set(startAt - 1);
		lastProcessedPosition.set(startAt - 1);
		pauseAt.set(lastProcessable.get() + PROCESSING_CHUNK);


		final Phaser phaser = analysisChunk.phaser;
		String lastReferenceName = null;

		try {
			final String contig = contigs.get(contigIndex);
			final int truncateAtPosition = analysisChunk.terminateAtPosition;

			final Set<String> droppedReads = analyzer.dropReadProbability > 0 ? new HashSet<>(1_000_000) : null;
			final Set<String> keptReads = analyzer.dropReadProbability > 0 ?  new HashSet<>(1_000_000) : null;

			final SequenceLocation contigLocation = new SequenceLocation(contigIndex, 0);

			BiConsumer<PrintStream, Integer> info = (stream, userRequestNumber) -> {
				NumberFormat formatter = NumberFormat.getInstance();
				stream.println("Analyzer " + analyzer.name +
						" contig " + contig + 
						" range: " + (analysisChunk.startAtPosition + "-" + analysisChunk.terminateAtPosition) +
						"; pauseAtPosition: " + formatter.format(pauseAt.get()) +
						"; lastProcessedPosition: " + formatter.format(lastProcessedPosition.get()) + "; " +
						formatter.format(100f * (lastProcessedPosition.get() - analysisChunk.startAtPosition) / 
								(analysisChunk.terminateAtPosition - analysisChunk.startAtPosition)) + "% done"
						);
			};
			synchronized(Mutinack.statusUpdateTasks) {
				Mutinack.statusUpdateTasks.add(info);
			}

			final SAMFileReader bamReader = analyzer.readerPool.getObj();
			SAMRecordIterator it0 = null;
			try {

				if (contigs.get(0).equals(contig)) {
					analyzer.stats.forEach(s -> s.nRecordsInFile.add(contigLocation, Util.getTotalReadCount(bamReader)));
				}

				int furthestPositionReadInContig = 0;
				final int maxInsertSize = analyzer.maxInsertSize;
				final QueryInterval[] bamContig = new QueryInterval[] {
						bamReader.makeQueryInterval(contig, Math.max(1, startAt - maxInsertSize + 1))};
				analyzer.timeStartProcessing = System.nanoTime();
				final Map<String, Pair<ExtendedSAMRecord, @NonNull ReferenceSequence>> readsToProcess =
						new HashMap<>(5_000);

				subAnalyzer.truncateProcessingAt = truncateAtPosition;
				subAnalyzer.startProcessingAt = startAt;

				final List<Iterator<SAMRecord>> intersectionIterators = new ArrayList<>();

				for (File f: analyzer.intersectAlignmentFiles) {
					SAMFileReader reader = new SAMFileReader(f);
					intersectionIterators.add(new IteratorPrefetcher<>(reader.queryOverlapping(bamContig), 100, reader, e -> {}));
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

				boolean firstRun = true;
				subAnalyzer.stats = subAnalyzer.analyzer.stats.get(0);

				try (IteratorPrefetcher<SAMRecord> iterator = new IteratorPrefetcher<>(it0, 100, it0,
						e -> {e.eagerDecode(); e.getUnclippedEnd(); e.getUnclippedStart();})) {
					while (iterator.hasNext() && !phaser.isTerminated() && !Mutinack.terminateAnalysis) {

						final SAMRecord samRecord = iterator.next();

						if (alignmentWriter != null) {
							samRecord.setAttribute("DS", null); //If not output read will not be written
							//if reading from an already-annotated BAM file
						}

						final SequenceLocation location = new SequenceLocation(samRecord);

						final int current5p = samRecord.getAlignmentStart();
						if (current5p < previous5p) {
							throw new IllegalArgumentException("Unsorted input");
						}
						if (nIntersect > 0 && current5p > previous5p) {
							final Iterator<Entry<Pair<String, Integer>, SettableInteger>> esit = intersectReads.entrySet().iterator();
							while (esit.hasNext()) {
								if (esit.next().getKey().snd < current5p - 6) {
									analyzer.stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
									esit.remove();
								}
							}
							final Set<Entry<Pair<String, Integer>, SettableInteger>> readAheadStashEntries =
									new HashSet<>(readAheadStash.entrySet());
							for (Entry<Pair<String, Integer>, SettableInteger> e: readAheadStashEntries) {
								if (e.getKey().getSnd() < current5p - 6) {
									analyzer.stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
									readAheadStash.removeAll(e.getKey());
								} else if (e.getKey().getSnd() <= current5p + 6) {
									readAheadStash.removeAll(e.getKey());
									intersectReads.put(e.getKey(), e.getValue().get());
								}
							}

							for (int i = 0; i < nIntersect; i++) {
								if (current5p + 6 < intersectionWaitUntil.get(i)) {
									continue;
								}
								Iterator<SAMRecord> it = intersectionIterators.get(i);
								while (it.hasNext()) {
									SAMRecord ir = it.next();
									if (ir.getMappingQuality() < argValues.minMappingQIntersect.get(i)) {
										analyzer.stats.forEach(s -> s.nTooLowMapQIntersect.accept(location));
										continue;
									}
									int ir5p = ir.getAlignmentStart();
									final Pair<String, Integer> pair = new Pair<>(getRecordNameWithPairSuffix(ir), ir5p);
									if (ir5p < current5p - 6) {
										analyzer.stats.forEach(s -> s.nRecordsNotInIntersection1.accept(location));
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
								intersectReads.get(new Pair<>(getRecordNameWithPairSuffix(samRecord), 
										current5p)) < nIntersect) {
							analyzer.stats.forEach(s -> s.nRecordsNotInIntersection2.accept(location));
							continue;
						}

						if (analyzer.dropReadProbability > 0) {
							Objects.requireNonNull(droppedReads);
							Objects.requireNonNull(keptReads);
							String readName = ExtendedSAMRecord.getReadFullName(samRecord);
							boolean mateAlreadyDropped = droppedReads.contains(readName);
							if (mateAlreadyDropped) {
								droppedReads.remove(readName);
								continue;
							}
							if (keptReads.contains(readName)) {
								keptReads.remove(readName);
							} else if (Math.random() < analyzer.dropReadProbability) {
								//If mate is in a different contig, do not store a reference
								//to the read name in droppedReads since this thread will never
								//encounter the mate
								if (Integer.compare(samRecord.getMateReferenceIndex(), samRecord.getReferenceIndex()) == 0)
									droppedReads.add(readName);
								continue;
							} else {//Keep the pair
								//If mate is in a different contig, do not store a reference
								//to the read name in keptReads since this thread will never
								//encounter the mate
								if (Integer.compare(samRecord.getMateReferenceIndex(), samRecord.getReferenceIndex()) == 0)
									keptReads.add(readName);
							}
						}

						analyzer.stats.forEach(s -> s.nRecordsProcessed.increment(location));

						if (contigs.size() < 100 && !argValues.rnaSeq) {
							//Summing below is a bottleneck when there are a large
							//number of contigs (tens of thousands)
							if (analyzer.stats.get(0).nRecordsProcessed.sum() > argValues.nRecordsToProcess) {
								statusLogger.info("Analysis of contig " + contig + " stopping "
										+ "because it processed over " + argValues.nRecordsToProcess + " records");
								break;
							}
						}

						final String refName = samRecord.getReferenceName();
						if ("*".equals(refName)) {
							analyzer.stats.forEach(s -> s.nRecordsUnmapped.increment(location));
							continue;
						}

						if (IGNORE_SECONDARY_MAPPINGS && samRecord.getNotPrimaryAlignmentFlag()) {
							analyzer.stats.forEach(s -> s.nRecordsIgnoredBecauseSecondary.increment(location));
							continue;
						}

						int mappingQuality = samRecord.getMappingQuality();
						analyzer.stats.forEach(s -> s.mappingQualityAllRecords.insert(mappingQuality));
						if (mappingQuality < analyzer.minMappingQualityQ1) {
							analyzer.stats.forEach(s -> s.nRecordsBelowMappingQualityThreshold.increment(location));
							continue;
						}

						if (lastReferenceName != null && !refName.equals(lastReferenceName)) {
							furthestPositionReadInContig = 0;
						}
						lastReferenceName = refName;

						lastProcessable.set(samRecord.getAlignmentStart() - 2);

						final boolean finishUp;
						if (lastProcessable.get() >= truncateAtPosition + maxInsertSize) {
							statusLogger.debug("Analysis of contig " + contig + " stopping "
									+ "because it went past " + truncateAtPosition);
							finishUp = true;
						} else {
							finishUp = false;
						}

						ReferenceSequence ref = refMap.get(refName);
						if (ref == null) {
							try {
								ref = refFile.getSequence(refName);
								if (ref.getBases()[0] == 0) {
									throw new RuntimeException("Found null byte in " + refName +
											"; contig might not exist in reference file");
								}
							} catch (Exception e) {
								throw new RuntimeException("Problem reading reference file " + argValues.referenceGenome, e);
							}
							refMap.put(refName, ref);
						}

						//Put samRecord into the cache
						ExtendedSAMRecord extended = subAnalyzer.getExtended(samRecord, location);
						//Put samRecord into map of reads to possibly be processed in next batch
						if (readsToProcess.put(extended.getFullName(), new Pair<>(extended, ref)) != null) {
							throw new RuntimeException("Read " + extended.getFullName() + " read twice from " +
									analyzer.inputBam.getAbsolutePath());
						}
						furthestPositionReadInContig = Math.max(furthestPositionReadInContig,
								samRecord.getAlignmentEnd() - 1);

						//Use phaser here and go by specific positions rather than
						//actually processed records
						if (finishUp || lastProcessable.get() >= pauseAt.get() + maxInsertSize) {
							if (ENABLE_TRACE && shouldLog(TRACE)) {
								logger.trace("Member of phaser " + phaser + " reached " + lastProcessable +
										"; pauseAtPosition is " + pauseAt.get());
							}

							/**
							 * Meat of the processing is done here by calling method processRead.
							 * Only process the reads that will contribute to mutation candidates to be analyzed in the
							 * next round.
							 */									
							Iterator<Pair<ExtendedSAMRecord, @NonNull ReferenceSequence>> it = 
									readsToProcess.values().iterator();
							final int localPauseAt = pauseAt.get();
							while (it.hasNext()) {
								Pair<ExtendedSAMRecord, @NonNull ReferenceSequence> rec = it.next();
								final ExtendedSAMRecord read = rec.fst;
								if (firstRun && read.getAlignmentStartNoBarcode() + maxInsertSize < startAt) {
									//The purpose of this was to make runs reproducible irrespective of
									//the way contigs are broken up for parallelization; probably largely redundant now
									it.remove();
									continue;
								}
								if (read.getAlignmentStart() - 1 <= localPauseAt ||
										read.getMateAlignmentStart() - 1 <= localPauseAt) {
									subAnalyzer.processRead(read.record, rec.snd);
									it.remove();
								}
							}
							firstRun = false;
							phaser.arriveAndAwaitAdvance();	
						}
						if (finishUp) {
							break;
						}
					}//End samRecord loop
				}//End iterator try block

				logger.trace("Member of phaser " + phaser + " reached final " + lastProcessable +
						"; pauseAtPosition is " + pauseAt.get());

				if (lastProcessedPosition.get() < truncateAtPosition) {
					readsToProcess.forEach((k, v) -> {
						subAnalyzer.processRead(v.fst.record, v.snd);
					});
				}

				if (truncateAtPosition == Integer.MAX_VALUE) {
					lastProcessable.set(furthestPositionReadInContig);
				} else {
					lastProcessable.set(truncateAtPosition + 1);
				}

				while (!phaser.isTerminated() && !Mutinack.terminateAnalysis) {
					phaser.arriveAndAwaitAdvance();
				}

				logger.debug("Done processing contig " + contig + " of file " + analyzer.inputBam);
			} finally {
				if (it0 != null) {
					it0.close();
				}
				analyzer.readerPool.returnObj(bamReader);
			}
		} catch (Throwable t) {
			Util.printUserMustSeeMessage("Exception " + t.getMessage() + " " +
					(t.getCause() != null ? (t.getCause() + " ") : "") + 
					"on thread " + Thread.currentThread());
			if (argValues.terminateImmediatelyUponError) {
				t.printStackTrace();
				System.exit(1);
			}
			phaser.forceTermination();
			throw new RuntimeException("Exception while processing contig " + lastReferenceName + 
					" of file " + analyzer.inputBam.getAbsolutePath(), t);
		} finally {
			//phaser.arriveAndDeregister();
		}
		//Ensure that there is no memory leak (references are kept to subAnalyzers,
		//at least through the status update handlers; XXX not clear if the maps
		//should already have been completely cleared as part of locus examination.
		analysisChunk.subAnalyzers.forEach(sa -> {
			sa.extSAMCache.clear();
			sa.candidateSequences.clear();
			if (sa.analyzedDuplexes != null) {
				sa.analyzedDuplexes.clear();
			}
		});
		//TODO Clear subAnalyzers list, but *only after* all analyzers have completed
		//that chunk
		//analysisChunk.subAnalyzers.clear();
	}
	
	static final boolean shouldLog(Level level) {
		return logger.isEnabled(level);
	}

}
