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
package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.AbstractBAMFileIndex;
import contrib.net.sf.samtools.BAMIndexMetaData;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import contrib.net.sf.samtools.util.StringUtil;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.sequence_IO.FastQRead;

public class Util {
	
	/**
	 * Utility function to improve Null annotation checking by removing spurious
	 * warnings (in particular for fields that can go from being null to being non-null,
	 * but not vice-versa, or for non-annotated methods from external libraries).
	 * Performance impact should be small or nonexistent.
	 * @param o
	 * @return
	 */
	public static<T> @NonNull T nonNullify(T o) {
		if (o == null) throw new AssertionFailedException();
		return o;
	}
	
	public static<T> @NonNull Optional<T> emptyOptional() {
		return nonNullify(Optional.empty());
	}
	
	public static<T> @Nullable T nullableify(@NonNull T o) {
		return o;
	}
		
	public static<T> @NonNull Set<T> getDuplicates(Collection<T> collection) {
		Set<T> set = new HashSet<>();
		Set<T> result = new HashSet<>();
		for (T t: collection) {
			if (!set.add(t)) {
				result.add(t);
			}
		}
		return result;
	}
	
	public static Pair<List<String>, List<Integer>> parseListLoci(List<String> l,
			boolean noContigRepeat, String errorMessagePrefix) {
		final List<String> contigNames = l.stream().map
				(s -> s.split(":")[0]).collect(Collectors.toList());
		
		Set<String> dup;
		if (noContigRepeat && (!(dup = Util.getDuplicates(contigNames)).isEmpty())) {
			throw new IllegalArgumentException(errorMessagePrefix + " may only appear once per contig; " +
					" remove extra occurrences of " + Arrays.toString(dup.toArray()));
		}

		final List<Integer> positionsInContig = l.stream().map
				(s -> Integer.valueOf(s.split(":")[1])).collect(Collectors.toList());
		
		return new Pair<>(contigNames, positionsInContig);
	}
	
	public static void checkPositionsOrdering(Pair<List<String>, List<Integer>> p,
			Pair<List<String>, List<Integer>> p2) {
		List<String> names1 = p.fst;
		List<String> names2 = p2.fst;
		for (String name: names1) {
			if (names2.contains(name)) {
				int i1 = names1.indexOf(name);
				int i2 = names2.indexOf(name);
				int start = p.snd.get(i1);
				int end = p2.snd.get(i2);
				if (end < start) {
					throw new IllegalArgumentException("Contig " + name + " ends at " +
							end + ", before it starts at " + start);
				}
			}
		}
	}
	
	public static @NonNull String truncateString(@NonNull String s) {
		String result = s.length() > 500 ?
				s.substring(0, 500) + " ..."
				: s;	
		return result;
	}
	
	public static @NonNull String greenB(boolean colorize) {
		return colorize ? "\033[1;42m" : "";
	}

	public static @NonNull String blueF(boolean colorize) {
		return colorize ? "\033[1;34m" : "";
	}

	public static @NonNull String reset(boolean colorize) {
		return colorize ? "\033[0m" : "";
	}

	public static boolean basesEqual(byte a, byte b, boolean allowN) {
		if (allowN && (a == 'N' || b == 'N')) {
			return true;
		}
		return a == b;
	}

	public static boolean basesEqual(byte @NonNull[] a, byte @NonNull[] b, boolean allowN, int nMismatchesAllowed) {
		if (DebugControl.NONTRIVIAL_ASSERTIONS && a.length != b.length) {
			throw new IllegalArgumentException();
		}
		try {
			int nMismatches = 0;
			for (int i = 0; i < a.length; i++) {
				if (!basesEqual(a[i], b[i], allowN)) {
					++nMismatches;
					if (nMismatches > nMismatchesAllowed)
						return false;
				}
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException("Index out of bounds while comparing " + new String(a) + " to " + new String(b), e);
		}
		return true;
	}
	
	private final static @NonNull AtomicInteger nRead = new AtomicInteger();
	
	public static void readFileIntoMap(File file, Map<String, FastQRead> rawReads, int pairID) {
		Signals.SignalProcessor infoSignalHandler = signal ->
				System.err.println("Currently reading records from file " + file.getAbsolutePath() + "; " +
                NumberFormat.getInstance().format(rawReads.size()) + " total so far");
		Signals.registerSignalProcessor("INFO", infoSignalHandler);

		final SettableInteger lineModulo = new SettableInteger(0);
		final String[] readNameHandle = new String[1];
		final String pairIDs = Integer.toString(pairID);
		final Handle<FourLines> fourLines = new Handle<>();
		fourLines.set(new FourLines());
		LinkedBlockingQueue<FourLines> processingQueue = new LinkedBlockingQueue<>(100);
		final Handle<Throwable> exception = new Handle<>();
		final List<Thread> threads = new ArrayList<>();
		Runnable r = () -> 
		{
			try {
			while (true) {
				FourLines localFourLines;
				try {
					localFourLines = processingQueue.poll(Long.MAX_VALUE, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					break;
				}
				String line1 = localFourLines.lines.get(0);
				String line2 = localFourLines.lines.get(1);
				//String line3 = localFourLines.lines.get(2);
				String line4 = localFourLines.lines.get(3);
				
				String name = line1.substring(1, line1.indexOf(" ")) + "--";
				if (pairID != 0) {
					name += pairIDs;
				}
				FastQRead read = new FastQRead(nonNullify(line2.substring(0, 6).getBytes()));
				
				byte[] qualities = new byte [6];
				read.qualities = qualities;
				byte[] qualityBytes = StringUtil.stringToBytes(line4);
				for (int i = 0; i < 6; i++) {
					qualities [i]=(byte) ((qualityBytes[i] & 0xFF) - 33);
				}
				
				if (rawReads.put(name, read) != null) {
					throw new RuntimeException("Read " + readNameHandle[0] +
							" was found twice in file " + file.getName());
				}
				if (processingQueue.size() == 0) { //May have reached the end point
					synchronized(rawReads) {
						rawReads.notifyAll();
					}
				}
			}
			} catch (Throwable e0) {
				if (exception.get() == null) {
					exception.set(e0);
				}
			}
		};
		for (int i = 0; i < 2; i++) {
			Thread t1 = new Thread(r, "Read 1");
			threads.add(t1);
			t1.start();
		}
		try(Stream<String> lines = Files.lines(Paths.get(file.getAbsolutePath()))) {
			lines.forEachOrdered(l -> {
				nRead.incrementAndGet();
				int line = lineModulo.incrementAndGet();
				FourLines fl = fourLines.get();
				if (line < 3) {
					fl.lines.add(l);
				} else if (line == 3) {
					fl.lines.add(null);
				} else if (line == 4) {
					fl.lines.add(l);
					try {
						processingQueue.offer(fl, Long.MAX_VALUE, TimeUnit.DAYS);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					fourLines.set(new FourLines());
					lineModulo.set(0);
				}
			});
			
			if (exception.get() != null) {
				throw new RuntimeException(exception.get());
			}
			//Waiting for all reads to have been loaded before returning
			while (nRead.get() != rawReads.size()) {
				try {
					synchronized(rawReads) {
						rawReads.wait(1000);
					}
					if (exception.get() != null) {
						throw new RuntimeException(exception.get());
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Problem while loading from file " + file.getAbsolutePath(), e);
		} finally {
			//Get map put threads to terminate
			for (Thread t: threads) {
				t.interrupt();
			}
			Signals.removeSignalProcessor("INFO", infoSignalHandler);
		}
	}
	
	private final static class FourLines {
		final @NonNull List<String> lines = new ArrayList<>(4);
	}
	
	public static String getRecordNameWithPairSuffix(SAMRecord record) {
		return (record.getReadName() + "--" + (record.getSecondOfPairFlag() ? "2" : "1"))/*.intern()*/;
	}
	
	//Keep separate maps for variable and constant barcodes so we can have stats for each
	public static final Map<ByteArray, ByteArray> internedVariableBarcodes = new ConcurrentHashMap<>(200);
	public static final Map<ByteArray, ByteArray> internedConstantBarcodes = new ConcurrentHashMap<>(200);

	//NB: Interning might not be fully effective in the early stages when the map is being
	//filled in a multi-threaded way, but that does not matter for our purposes
	private static byte @NonNull[] getInternedArray(byte @NonNull[] array, Map<ByteArray, ByteArray> map) {
		ByteArray key = new ByteArray(array);
		ByteArray intern = map.get(key);
		if (intern == null) {
			map.put(key, key);
			intern = key;
		}
		intern.nHits.increment();
		return intern.array;
	}
	
	public static byte @NonNull[] getInternedVB(byte @NonNull[] barcode) {
		return getInternedArray(barcode, internedVariableBarcodes);
	}

	public static byte @NonNull[] getInternedCB(byte @NonNull[] barcode) {
		return getInternedArray(barcode, internedConstantBarcodes);
	}
	
	public static final ThreadLocal<NumberFormat> shortLengthFloatFormatter = new ThreadLocal<NumberFormat>() {
		@Override
		protected NumberFormat initialValue(){
			return new DecimalFormat("0.00");
		}
	};

	public static final ThreadLocal<NumberFormat> mediumLengthFloatFormatter = new ThreadLocal<NumberFormat>() {
		@Override
		protected NumberFormat initialValue(){
			return new DecimalFormat("0.0000");
		}
	};

	/* getPairOrientation below adapted from net.sf.samtools
	 * The MIT License
	 *
	 * Copyright (c) 2009 The Broad Institute
	 *
	 */

	public static final class SimpleAlignmentInfo {
		public SimpleAlignmentInfo(SAMRecord rec) {
			negativeStrand = rec.getReadNegativeStrandFlag();
			alignmentStart = rec.getAlignmentStart();
		}
		
		public SimpleAlignmentInfo(boolean negativeStrand, int alignmentStart) {
			this.negativeStrand = negativeStrand;
			this.alignmentStart = alignmentStart;
		}
		
		final public boolean negativeStrand;
		final public int alignmentStart;
	}
	
	/**
	 * Computes the orientation of the two alignments.
	 * @param r1, r2
	 */
	public static PairOrientation getPairOrientation(SimpleAlignmentInfo r1, SimpleAlignmentInfo r2)
	{
		if (r1.negativeStrand == r2.negativeStrand) {
			return PairOrientation.TANDEM;
		}

		int sign = r1.negativeStrand ? -1 : +1;
		
		if ((r2.alignmentStart - r1.alignmentStart) * sign > 0) {
			return PairOrientation.FR;
		} else {
			return PairOrientation.RF;
		}
	}

	//From http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
	public static String convertStreamToString(java.io.InputStream is) {
		try (java.util.Scanner s = new java.util.Scanner(is)) {
			try (java.util.Scanner s2 = s.useDelimiter("\\A")) {
				String result = s2.hasNext() ? s2.next() : "";
				return result.trim();
			}
		}
	}
	
	//This method was adapted from http://left.subtree.org/2012/04/13/counting-the-number-of-reads-in-a-bam-file/
	public static long getTotalReadCount(SAMFileReader sam) {
		int count = 0;

		AbstractBAMFileIndex index = (AbstractBAMFileIndex) sam.getIndex();
		int nRefs = index.getNumberOfReferences();
		for (int i = 0; i < nRefs; i++) {
			BAMIndexMetaData meta = index.getMetaData(i);
			count += meta.getAlignedRecordCount() + meta.getUnalignedRecordCount();
		}

		return count + index.getNoCoordinateCount();
	}
	
	public static void printUserMustSeeMessage(String s) {
		if (System.console() == null) {
			System.err.println(s);
		}
		System.out.println(s);
	}
	
	public static void versionCheck() {
		try (Scanner scanner = new Scanner(new URL("http://cinquin.org.uk/static/mutinack/latestVersion.txt").openStream(), "UTF-8")) {
			String s = scanner.useDelimiter("\n").next();
			double version = Double.parseDouble(s);
			if (version > VersionNumber.version) {
				System.err.println("New Mutinack version " + version + " is available at http://cinquin.org.uk/static/mutinack.jar");
			} else {
				System.err.println("Mutinack is up to date");
			}
		} catch (IOException e) {
			System.err.println("Could not perform update check: " + e.getMessage());
			e.printStackTrace(System.err);
		}
	}
}
