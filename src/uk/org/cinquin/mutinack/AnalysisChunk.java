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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.stream.Collectors;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.ParallelListIterable;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.misc_util.StaticStuffToAvoidMutating;

public class AnalysisChunk {
	int contig;
	final @NonNull String contigName;
	int startAtPosition;
	int terminateAtPosition;
	int pauseAtPosition;
	int lastProcessedPosition;
	@Nullable Phaser phaser;
	final MutableList<@NonNull SubAnalyzer> subAnalyzers = Lists.mutable.empty();
	final ParallelListIterable<@NonNull SubAnalyzer> subAnalyzersParallel =
		subAnalyzers.asParallel(StaticStuffToAvoidMutating.getExecutorService(), 1);
	public @NonNull final MutinackGroup groupSettings;
	public final int nParameterSets;

	public final Map<SubAnalyzer, ProcessingStats> processingStats = new HashMap<>();

	public AnalysisChunk(@NonNull String contigName, int nParameterSets, @NonNull MutinackGroup groupSettings) {
		this.contigName = contigName;
		this.nParameterSets = nParameterSets;
		this.groupSettings = groupSettings;
	}

	@Override
	public String toString() {
		return new SequenceLocation(contig, contigName, startAtPosition) + " -> " +
			new SequenceLocation(contig, contigName, terminateAtPosition);
	}

	public static class ProcessingStats {
		public long timeStarted, timeStopped;
		public long nRecordsProcessed;

		public int throughput() {
			if (timeStarted == 0) {
				return -1;
			}
			final long timeExpended = (timeStopped > 0 ? timeStopped : System.nanoTime()) - timeStarted;
			return (int) (nRecordsProcessed / (timeExpended / 1_000_000_000d));
		}
	}

	public int getProcessingThroughput() {
		List<ProcessingStats> localStats = new ArrayList<>(processingStats.values());//Inconsequential race condition
		return getProcessingThroughput(localStats);
	}

	public static int getProcessingThroughput(Collection<ProcessingStats> pss) {
		final long nRecordsProcessed =
			pss.stream().filter(ps -> ps.timeStarted > 0).mapToLong(ps -> ps.nRecordsProcessed).sum();
		final long timeExpended =
			pss.stream().filter(ps -> ps.timeStarted > 0).mapToLong(ps ->
				(ps.timeStopped > 0 ? ps.timeStopped : System.nanoTime()) - ps.timeStarted).sum();
		return (int) (nRecordsProcessed / (timeExpended / 1_000_000_000d));
	}

	public static int getProcessingThroughput(List<List<AnalysisChunk>> l0) {
		List<ProcessingStats> processingStats =
			l0.stream().flatMap(l -> l.stream()).flatMap(c -> c.processingStats.values().stream()).
				collect(Collectors.toList());
		return getProcessingThroughput(processingStats);
	}

	public static int getProcessingThroughput(List<List<AnalysisChunk>> l0, Mutinack analyzer) {
		List<ProcessingStats> processingStats =
			l0.stream().flatMap(l -> l.stream()).flatMap(c -> c.processingStats.entrySet().stream()).
			filter(e -> e.getKey().analyzer == analyzer).map(e -> e.getValue()).
			collect(Collectors.toList());
		return getProcessingThroughput(processingStats);
	}
}
