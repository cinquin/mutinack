package uk.org.cinquin.mutinack.benchmarking;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import gnu.trove.map.TMap;
import gnu.trove.map.hash.TCustomHashMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.strategy.HashingStrategy;
import uk.org.cinquin.mutinack.misc_util.collections.trove_dups.THashMap0;

/*
Benchmark                                       (loadFactor)  (size)  Mode  Cnt    Score    Error  Units
benchmarkMonomorphic                                     0.5   10000  avgt  100   90.193 ±  5.843  ns/op
benchmarkMonomorphic                                       1   10000  avgt  100  284.079 ± 29.905  ns/op
benchmarkPolymorphic                                     0.5   10000  avgt  100   98.288 ±  5.381  ns/op
benchmarkPolymorphic                                       1   10000  avgt  100  270.164 ± 36.660  ns/op
benchmarkPolymorphicOnly1ImplementationCalledA           0.5   10000  avgt  100   95.910 ±  4.749  ns/op
benchmarkPolymorphicOnly1ImplementationCalledA             1   10000  avgt  100  268.945 ± 44.154  ns/op
benchmarkPolymorphicOnly1ImplementationCalledB           0.5   10000  avgt  100   93.755 ±  5.288  ns/op
benchmarkPolymorphicOnly1ImplementationCalledB             1   10000  avgt  100  255.665 ± 30.007  ns/op
*/

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5)
public class TestPolymorphicTCustomCost {

	private final String MAX_MEM = "-Xmx300M";
	private final String PARALLEL_GC_1 = "-XX:+UseParallelGC";
	private final String PARALLEL_GC_2 = "-XX:+UseParallelOldGC";

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 20)
	public int benchmarkMonomorphic(Data d) {
		int result = 0;
		result += d.mapMono.put(12345, 12345);
		result += d.customMapPoly.put(12345, 12345);
		return result;
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 20)
	public int benchmarkPolymorphic(Data d) {
		int result = 0;
		result += d.mapPoly.put(12345, 12345);
		result += d.customMapPoly.put(12345, 12345);
		return result;
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 20)
	public int benchmarkPolymorphicOnly1ImplementationCalledA(Data d) {
		int result = 0;
		result += d.mapPoly.put(12345, 12345);
		result += d.mapPoly.put(12346, 12346);
		return result;
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 20)
	public int benchmarkPolymorphicOnly1ImplementationCalledB(Data d) {
		int result = 0;
		result += d.customMapPoly.put(12345, 12345);
		result += d.customMapPoly.put(12346, 12346);
		return result;
	}

	@State(Scope.Benchmark)
	public static class Data {

		@Param({"10000"})
		int size;

		@Param({"0.5", "1"})
		float loadFactor;

		THashMap0<Integer, Integer> mapMono;

		THashMap<Integer, Integer> mapPoly;
		TCustomHashMap<Integer, Integer> customMapPoly;

		private void fill (TMap<Integer, Integer> map) {
			for (int i = 0; i < size; i++) {
				Integer integer = Integer.valueOf((int) (Math.random() * Integer.MAX_VALUE));
				map.put(integer, integer);
			}
			map.put(12345, 12345);
			map.put(12346, 12346);
		}

		static final HashingStrategy<Integer> strategy = new HashingStrategy<Integer>() {

			private static final long serialVersionUID = -7097091077964878968L;

			@Override
			public int computeHashCode(Integer arg0) {
				return Integer.hashCode(arg0) + 1;
			}

			@Override
			public boolean equals(Integer arg0, Integer arg1) {
				return arg0 == Integer.MAX_VALUE || (arg0.equals(arg1));
			}
		};

		@Setup
		public void setup() {
			mapMono = new THashMap0<>(1, loadFactor);
			fill(mapMono);
			mapPoly = new THashMap<>(1, loadFactor);
			fill(mapPoly);
			customMapPoly = new TCustomHashMap<>(strategy, 1, loadFactor);
			fill(customMapPoly);
		}
	}

}
