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

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;

/*
Benchmark	(loadFactor)	(size)	Mode	Cnt	Score	Error	Units
NewImplementation	0.1	10	avgt	25	271.809	2.355	ns/op
OldImplementation	0.1	10	avgt	25	422.769	5.489	ns/op
NewImplementation	0.1	1000	avgt	25	23589.473	184.331	ns/op
OldImplementation	0.1	1000	avgt	25	30811.609	797.844	ns/op
NewImplementation	0.1	10000	avgt	25	193730.026	3851.176	ns/op
OldImplementation	0.1	10000	avgt	25	282142.241	624.453	ns/op
NewImplementation	0.1	100000	avgt	25	7127640.527	95410.535	ns/op
OldImplementation	0.1	100000	avgt	25	7858021.685	176439.986	ns/op
NewImplementation	0.5	10	avgt	25	78.295	11.245	ns/op
OldImplementation	0.5	10	avgt	25	144.905	18.086	ns/op
NewImplementation	0.5	1000	avgt	25	13599.805	120.213	ns/op
OldImplementation	0.5	1000	avgt	25	15110.348	361.405	ns/op
NewImplementation	0.5	10000	avgt	25	126834.575	6361.093	ns/op
OldImplementation	0.5	10000	avgt	25	142963.399	6058.426	ns/op
NewImplementation	0.5	100000	avgt	25	1717466.605	77217.022	ns/op
OldImplementation	0.5	100000	avgt	25	2147697.771	51002.246	ns/op
NewImplementation	1	10	avgt	25	51.393	2.439	ns/op
OldImplementation	1	10	avgt	25	59.63	2.841	ns/op
NewImplementation	1	1000	avgt	25	6177.521	123.94	ns/op
OldImplementation	1	1000	avgt	25	7193.304	107.285	ns/op
NewImplementation	1	10000	avgt	25	47716.489	265.067	ns/op
OldImplementation	1	10000	avgt	25	60377.054	5511.886	ns/op
NewImplementation	1	100000	avgt	25	1156677.972	70467.2	ns/op
OldImplementation	1	100000	avgt	25	1407750.658	47189.882	ns/op
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5)
public class BenchmarkTroveVariants {

	private final String MAX_MEM = "-Xmx300M";
	private final String PARALLEL_GC_1 = "-XX:+UseParallelGC";
	private final String PARALLEL_GC_2 = "-XX:+UseParallelOldGC";

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 5)
	public int benchmarkOldImplementation(Data d) {
		SettableInteger sum = new SettableInteger(0);
		d.map.forEachValueOld(v -> {
			sum.addAndGet(v);
			return true;
		});
		return sum.get();
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 5)
	public int benchmarkNewImplementation(Data d) {
		SettableInteger sum = new SettableInteger(0);
		d.map.forEachValue(v -> {
			sum.addAndGet(v);
			return true;
		});
		return sum.get();
	}

	@State(Scope.Benchmark)
	public static class Data {

		@Param({"10", "1000", "10000", "100000"})
		int size;

		@Param({"0.1", "0.5", "1"})
		float loadFactor;

		THashMap<Integer, Integer> map;

		@Setup
		public void setup() {
			map = new THashMap<>(1, loadFactor);
			for (int i = 0; i < size; i++) {
				Integer integer = Integer.valueOf((int) (Math.random() * Integer.MAX_VALUE));
				map.put(integer, integer);
			}
		}
	}

	/*@State(Scope.Thread)
	public static class DataCopy {
		THashMap<Integer, Integer> map;

		@Setup(Level.Invocation)
		public void setup2(Data d) {
			map = new THashMap<>(d.map);
		}
	}*/

}
