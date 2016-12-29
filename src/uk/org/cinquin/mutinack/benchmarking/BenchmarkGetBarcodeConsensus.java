package uk.org.cinquin.mutinack.benchmarking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.Util;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 50, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class BenchmarkGetBarcodeConsensus {

	private List<byte[]> barcodes = Arrays.asList(
		"TGCA".getBytes(),
		"ATGC".getBytes(),
		"GGGG".getBytes(),
		"CCCC".getBytes(),
		"AAAA".getBytes(),
		"TTTT".getBytes(),
		"TGCA".getBytes());

	private final String MAX_MEM = "-Xmx300M";
	private final String G1 = "-XX:+UseG1GC";
	private final String PARALLEL_GC_1 = "-XX:+UseParallelGC";
	private final String PARALLEL_GC_2 = "-XX:+UseParallelOldGC";

	@Benchmark
	/*Result "benchmarkCurrentImplementation":
	696.179 ±(99.9%) 10.576 ns/op [Average]
	(min, avg, max) = (638.682, 696.179, 1119.323), stdev = 44.779
	CI (99.9%): [685.603, 706.754] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkCurrentImplementation() {
		return SimpleCounter.getBarcodeConsensus(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementation":
	1754.640 ±(99.9%) 14.679 ns/op [Average]
	(min, avg, max) = (1627.906, 1754.640, 1849.707), stdev = 62.154
	CI (99.9%): [1739.961, 1769.320] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkOriginalImplementation() {
		return getBarcodeConsensusOriginal(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1":
	1225.031 ±(99.9%) 23.223 ns/op [Average]
	(min, avg, max) = (1156.470, 1225.031, 2437.182), stdev = 98.326
	CI (99.9%): [1201.808, 1248.253] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkImplementation1() {
		return getBarcodeConsensus1(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2":
	1219.853 ±(99.9%) 20.397 ns/op [Average]
	(min, avg, max) = (1109.666, 1219.853, 2047.413), stdev = 86.364
	CI (99.9%): [1199.456, 1240.251] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkImplementation2() {
		return getBarcodeConsensus2(barcodes.stream(), 4);
	}

	private List<byte[]> longerBarcodeList = new ArrayList<>();
	{
		for (int i = 0; i < 30; i++) {
			longerBarcodeList.addAll(barcodes);
		}
	}

	@Benchmark
	/*Result "benchmarkCurrentImplementationLongList":
	2140.760 ±(99.9%) 3.571 ns/op [Average]
	(min, avg, max) = (2103.459, 2140.760, 2207.486), stdev = 15.118
	CI (99.9%): [2137.190, 2144.331] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkCurrentImplementationLongList() {
		return SimpleCounter.getBarcodeConsensus(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementationLongList":
	17673.306 ±(99.9%) 71.294 ns/op [Average]
	(min, avg, max) = (15895.117, 17673.306, 18165.695), stdev = 301.864
	CI (99.9%): [17602.012, 17744.600] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkOriginalImplementationLongList() {
		return getBarcodeConsensusOriginal(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1LongList":
	2079.771 ±(99.9%) 4.368 ns/op [Average]
	(min, avg, max) = (2037.663, 2079.771, 2139.046), stdev = 18.493
	CI (99.9%): [2075.403, 2084.138] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkImplementation1LongList() {
		return getBarcodeConsensus1(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2LongList":
	2062.632 ±(99.9%) 4.671 ns/op [Average]
	(min, avg, max) = (2005.671, 2062.632, 2110.788), stdev = 19.775
	CI (99.9%): [2057.962, 2067.303] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, G1})
	public byte[] benchmarkImplementation2LongList() {
		return getBarcodeConsensus2(longerBarcodeList.stream(), 4);
	}

	/**
	 * Same benchmarks as above, with parallel GC
	 */

	@Benchmark
	/*Result "benchmarkCurrentImplementationPGC":
	667.441 ±(99.9%) 9.137 ns/op [Average]
	(min, avg, max) = (594.590, 667.441, 773.722), stdev = 38.687
	CI (99.9%): [658.304, 676.578] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkCurrentImplementationPGC() {
		return SimpleCounter.getBarcodeConsensus(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementationPGC":
	1811.790 ±(99.9%) 12.351 ns/op [Average]
	(min, avg, max) = (1647.858, 1811.790, 1888.886), stdev = 52.295
	CI (99.9%): [1799.438, 1824.141] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkOriginalImplementationPGC() {
		return getBarcodeConsensusOriginal(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1PGC":
	758.192 ±(99.9%) 5.420 ns/op [Average]
	(min, avg, max) = (694.177, 758.192, 831.377), stdev = 22.949
	CI (99.9%): [752.772, 763.612] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkImplementation1PGC() {
		return getBarcodeConsensus1(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2PGC":
	754.002 ±(99.9%) 9.131 ns/op [Average]
	(min, avg, max) = (689.518, 754.002, 1205.087), stdev = 38.661
	CI (99.9%): [744.871, 763.133] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkImplementation2PGC() {
		return getBarcodeConsensus2(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkCurrentImplementationLongListPGC":
	2192.949 ±(99.9%) 3.427 ns/op [Average]
	(min, avg, max) = (2151.208, 2192.949, 2225.582), stdev = 14.511
	CI (99.9%): [2189.522, 2196.377] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkCurrentImplementationLongListPGC() {
		return SimpleCounter.getBarcodeConsensus(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementationLongListPGC":
	17775.573 ±(99.9%) 77.276 ns/op [Average]
	(min, avg, max) = (16921.565, 17775.573, 18582.101), stdev = 327.190
	CI (99.9%): [17698.298, 17852.849] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkOriginalImplementationLongListPGC() {
		return getBarcodeConsensusOriginal(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1LongListPGC":
	2209.661 ±(99.9%) 4.818 ns/op [Average]
	(min, avg, max) = (2167.050, 2209.661, 2264.874), stdev = 20.399
	CI (99.9%): [2204.843, 2214.479] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkImplementation1LongListPGC() {
		return getBarcodeConsensus1(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2LongListPGC":
	2106.950 ±(99.9%) 6.097 ns/op [Average]
	(min, avg, max) = (2020.543, 2106.950, 2168.021), stdev = 25.817
	CI (99.9%): [2100.852, 2113.047] (assumes normal distribution)
	*/
	@Fork(jvmArgsAppend = {MAX_MEM, PARALLEL_GC_1, PARALLEL_GC_2})
	public byte[] benchmarkImplementation2LongListPGC() {
		return getBarcodeConsensus2(longerBarcodeList.stream(), 4);
	}

	public static byte @NonNull[] getBarcodeConsensusOriginal(Stream<byte[]> records,
			int barcodeLength) {
		@SuppressWarnings("unchecked")
		final SimpleCounter<Byte>[] counts = new SimpleCounter[barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			counts[i] = new SimpleCounter<>();
		}
		records.forEach(barcode -> {
			for (int i = 0; i < barcodeLength; i++) {
				if (barcode[i] != 'N') {
					counts[i].put(barcode[i]);
				}
			}
		});

		final byte[] consensus = new byte [barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			@SuppressWarnings("unused")
			byte maxByte = 'Z', runnerUp = 'Z';
			int maxByteCount = 0, runnerUpCount = 0;
			for (Entry<Byte, SettableInteger> entry: counts[i].entrySet()) {
				SettableInteger v = entry.getValue();
				int count = v.get();
				if (count > maxByteCount) {
					runnerUp = maxByte;
					runnerUpCount = maxByteCount;
					maxByte = entry.getKey();
					maxByteCount = count;
				} else if (count == maxByteCount) {
					runnerUp = entry.getKey();
					runnerUpCount = count;
				}
			}

			if (runnerUpCount == maxByteCount) {
				consensus[i] = 'N';
			} else {
				consensus[i] = maxByte;
			}
		}
		return Util.getInternedVB(consensus);
	}

	public byte[] BASES = {'A', 'C', 'G', 'N', 'T', 'a', 'c', 'g', 'n', 't'};
	{
		for (int i = 0; i < BASES.length; i++) {
			BASES[i] = (byte) (BASES[i] - 'A');
		}
	}
	public int N_BASES = BASES.length;

	//Use Java array of arrays
	public byte @NonNull[] getBarcodeConsensus1(Stream<byte[]> records,
			int barcodeLength) {

		final int[][] counts = new int[barcodeLength]['t' - 'A' + 1];

		records.forEach(barcode -> {
			for (int i = 0; i < barcodeLength; i++) {
				final byte barcodeAti = barcode[i];
				if (barcodeAti != 'N') {
					counts[i][barcodeAti - 'A']++;
				}
			}
		});

		final byte[] consensus = new byte [barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			@SuppressWarnings("unused")
			byte maxByte = 'Z', runnerUp = 'Z';
			int maxCount = 0, runnerUpCount = 0;
			for (final byte b: BASES) {
				final int count = counts[i][b];
				if (count > maxCount) {
					runnerUp = maxByte;
					runnerUpCount = maxCount;
					maxByte = b;
					maxCount = count;
				} else if (count == maxCount) {
					runnerUp = b;
					runnerUpCount = count;
				}
			}

			if (runnerUpCount == maxCount) {
				consensus[i] = 'N';
			} else {
				consensus[i] = (byte) (maxByte + 'A');
			}
		}
		return Util.getInternedVB(consensus);
	}

	//Same as #1 without for-each loop construct
	public byte @NonNull[] getBarcodeConsensus2(Stream<byte[]> records,
			int barcodeLength) {

		final int[][] counts = new int[barcodeLength]['t' - 'A' + 1];

		records.forEach(barcode -> {
			for (int i = 0; i < barcodeLength; i++) {
				final byte barcodeAti = barcode[i];
				if (barcodeAti != 'N') {
					counts[i][barcodeAti - 'A']++;
				}
			}
		});

		final byte[] consensus = new byte [barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			@SuppressWarnings("unused")
			byte maxByte = 'Z', runnerUp = 'Z';
			int maxCount = 0, runnerUpCount = 0;

			for (int baseIndex = 0; baseIndex < N_BASES; baseIndex++) {
				final byte b = BASES[baseIndex];
				final int count = counts[i][b];
				if (count > maxCount) {
					runnerUp = maxByte;
					runnerUpCount = maxCount;
					maxByte = b;
					maxCount = count;
				} else if (count == maxCount) {
					runnerUp = b;
					runnerUpCount = count;
				}
			}

			if (runnerUpCount == maxCount) {
				consensus[i] = 'N';
			} else {
				consensus[i] = (byte) (maxByte + 'A');
			}
		}
		return Util.getInternedVB(consensus);
	}


}
