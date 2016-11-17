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

	@Benchmark
	/*Result "benchmarkCurrentImplementation":
	595.127 ±(99.9%) 7.785 ns/op [Average]
	(min, avg, max) = (515.100, 595.127, 653.144), stdev = 32.964
	CI (99.9%): [587.341, 602.912] (assumes normal distribution)
	*/
	public byte[] benchmarkCurrentImplementation() {
		return SimpleCounter.getBarcodeConsensus(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementation":
	1707.446 ±(99.9%) 11.587 ns/op [Average]
	(min, avg, max) = (1555.896, 1707.446, 1764.175), stdev = 49.060
	CI (99.9%): [1695.859, 1719.033] (assumes normal distribution)
	*/
	public byte[] benchmarkOriginalImplementation() {
		return getBarcodeConsensusOriginal(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1":
	678.178 ±(99.9%) 3.429 ns/op [Average]
	(min, avg, max) = (617.256, 678.178, 707.253), stdev = 14.518
	CI (99.9%): [674.749, 681.607] (assumes normal distribution)
	*/
	public byte[] benchmarkImplementation1() {
		return getBarcodeConsensus1(barcodes.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2":
	665.795 ±(99.9%) 4.457 ns/op [Average]
	(min, avg, max) = (614.965, 665.795, 706.989), stdev = 18.873
	CI (99.9%): [661.338, 670.252] (assumes normal distribution)
	*/
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
	public byte[] benchmarkCurrentImplementationLongList() {
		return SimpleCounter.getBarcodeConsensus(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkOriginalImplementationLongList":
	17528.284 ±(99.9%) 133.802 ns/op [Average]
	(min, avg, max) = (15105.780, 17528.284, 18530.509), stdev = 566.525
	CI (99.9%): [17394.482, 17662.086] (assumes normal distribution)
	*/
	public byte[] benchmarkOriginalImplementationLongList() {
		return getBarcodeConsensusOriginal(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation1LongList":
	2147.665 ±(99.9%) 4.078 ns/op [Average]
	(min, avg, max) = (2100.270, 2147.665, 2193.302), stdev = 17.266
	CI (99.9%): [2143.588, 2151.743] (assumes normal distribution)
	*/
	public byte[] benchmarkImplementation1LongList() {
		return getBarcodeConsensus1(longerBarcodeList.stream(), 4);
	}

	@Benchmark
	/*Result "benchmarkImplementation2LongList":
	2027.644 ±(99.9%) 4.747 ns/op [Average]
	(min, avg, max) = (1968.910, 2027.644, 2080.428), stdev = 20.101
	CI (99.9%): [2022.896, 2032.391] (assumes normal distribution)
	*/
	public byte[] benchmarkImplementation2LongList() {
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

	public byte[] BASES = new byte[] {'A', 'C', 'G', 'N', 'T', 'a', 'c', 'g', 'n', 't'};
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
