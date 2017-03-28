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
benchmarkForEachField	0.1	10	avgt	25	240	5.563	ns/op
benchmarkForEachSI	0.1	10	avgt	25	270	2.43	ns/op
benchmarkValueIterator	0.1	10	avgt	25	757	20.123	ns/op
benchmarkForEachField	0.1	1000	avgt	25	21286	66.292	ns/op
benchmarkForEachSI	0.1	1000	avgt	25	23940	104.072	ns/op
benchmarkValueIterator	0.1	1000	avgt	25	58812	770.675	ns/op
benchmarkForEachField	0.1	10000	avgt	25	200857	1552.652	ns/op
benchmarkForEachSI	0.1	10000	avgt	25	192235	2484.291	ns/op
benchmarkValueIterator	0.1	10000	avgt	25	503114	4994.448	ns/op
benchmarkForEachField	0.1	100000	avgt	25	6918232	364723.145	ns/op
benchmarkForEachSI	0.1	100000	avgt	25	7262913	200485.354	ns/op
benchmarkValueIterator	0.1	100000	avgt	25	10725694	258669.104	ns/op
benchmarkForEachField	0.5	10	avgt	25	71	9.758	ns/op
benchmarkForEachSI	0.5	10	avgt	25	75	5.096	ns/op
benchmarkValueIterator	0.5	10	avgt	25	208	24.408	ns/op
benchmarkForEachField	0.5	1000	avgt	25	12508	506.594	ns/op
benchmarkForEachSI	0.5	1000	avgt	25	13470	240.119	ns/op
benchmarkValueIterator	0.5	1000	avgt	25	23950	464.369	ns/op
benchmarkForEachField	0.5	10000	avgt	25	129856	3436.038	ns/op
benchmarkForEachSI	0.5	10000	avgt	25	127234	6447.128	ns/op
benchmarkValueIterator	0.5	10000	avgt	25	204195	2709.944	ns/op
benchmarkForEachField	0.5	100000	avgt	25	1707445	48166.318	ns/op
benchmarkForEachSI	0.5	100000	avgt	25	1692523	77554.189	ns/op
benchmarkValueIterator	0.5	100000	avgt	25	3164156	26230.788	ns/op
benchmarkForEachField	1	10	avgt	25	40	0.436	ns/op
benchmarkForEachSI	1	10	avgt	25	50	3.298	ns/op
benchmarkValueIterator	1	10	avgt	25	113	8.452	ns/op
benchmarkForEachField	1	1000	avgt	25	6152	86.29	ns/op
benchmarkForEachSI	1	1000	avgt	25	6271	123.85	ns/op
benchmarkValueIterator	1	1000	avgt	25	10715	259.92	ns/op
benchmarkForEachField	1	10000	avgt	25	47990	380.083	ns/op
benchmarkForEachSI	1	10000	avgt	25	47629	313.682	ns/op
benchmarkValueIterator	1	10000	avgt	25	83967	2300.032	ns/op
benchmarkForEachField	1	100000	avgt	25	1092429	33523.706	ns/op
benchmarkForEachSI	1	100000	avgt	25	1075268	64230.716	ns/op
benchmarkValueIterator	1	100000	avgt	25	1719783	14563.157	ns/op
*/

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5)
public class BenchmarkIteration {

	private final String MAX_MEM = "-Xmx300M";
	private final String PARALLEL_GC_1 = "-XX:+UseParallelGC";
	private final String PARALLEL_GC_2 = "-XX:+UseParallelOldGC";

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 5)
	public int benchmarkValueIterator(Data d) {
		int sum = 0;
		for (Integer i: d.map.values()) {
			sum += i;
		}
		return sum;
	}

	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 5)
	public int benchmarkForEachSI(Data d) {
		SettableInteger sum = new SettableInteger(0);
		d.map.forEachValue(v -> {
			sum.addAndGet(v);
			return true;
		});
		return sum.get();
	}

	int sumField = 0;
	@Benchmark
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2}, value = 5)
	public int benchmarkForEachField(Data d) {
		sumField = 0;
		d.map.forEachValue(v -> {
			sumField += v;
			return true;
		});
		return sumField;
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
