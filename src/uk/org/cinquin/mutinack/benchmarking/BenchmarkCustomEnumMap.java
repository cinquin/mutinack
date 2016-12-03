package uk.org.cinquin.mutinack.benchmarking;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.candidate_sequences.Quality;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.collections.CustomEnumMap;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 50, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkCustomEnumMap {

	private final String MAX_MEM = "-Xmx300M";
	private final String G1 = "-XX:+UseG1GC";
	@SuppressWarnings("unused")
	private final String PARALLEL_GC_1 = "-XX:+UseParallelGC";
	@SuppressWarnings("unused")
	private final String PARALLEL_GC_2 = "-XX:+UseParallelOldGC";

	Map<PositionAssay, Quality> mapJDK, mapCustom;

	@Setup
	public void init() {
		mapJDK = new EnumMap<>(PositionAssay.class);
		mapCustom = new CustomEnumMap<>(PositionAssay.class);

		fillMap(mapJDK);
		fillMap(mapCustom);
	}

	private static void fillMap(Map<PositionAssay, Quality> map) {
		for (PositionAssay a: PositionAssay.values()) {
			map.put(a, Quality.DUBIOUS);
		}
		map.put(PositionAssay.INSERT_SIZE, Quality.POOR);
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	/* Result "benchmarkJDKImplementation":
	404.973 ±(99.9%) 1.134 ns/op [Average]
	(min, avg, max) = (396.532, 404.973, 423.267), stdev = 4.802
	CI (99.9%): [403.839, 406.107] (assumes normal distribution)
	*/
	public Quality benchmarkJDKImplementation() {
		return getMin(mapJDK);
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	/* Result "benchmarkCustomImplementation":
	163.892 ±(99.9%) 1.257 ns/op [Average]
	(min, avg, max) = (158.934, 163.892, 181.983), stdev = 5.324
	CI (99.9%): [162.635, 165.149] (assumes normal distribution)
	*/
	public Quality benchmarkCustomImplementation() {
		return getMin(mapCustom);
	}

	@SuppressWarnings("null")
	private static Quality getMin(Map<PositionAssay, Quality> map) {
		final Handle<@NonNull Quality> min1 = new Handle<>(Quality.MAXIMUM);
		map.forEach((k, v) -> {
			min1.set(Quality.min(min1.get(), v));
		});
		return min1.get();
	}
}
