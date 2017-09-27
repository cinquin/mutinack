package uk.org.cinquin.mutinack.misc_util.sequence_preprocessing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.IntMinMax;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Util.FourLines;
import uk.org.cinquin.mutinack.misc_util.exceptions.IllegalInputException;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public class DemultiplexDualIndex {

	private final static int N_CONSUMER_THREADS = Runtime.getRuntime().availableProcessors();

	private volatile boolean done = false;
	private volatile boolean abort = false;
	private final Object semaphore = new Object();

	private List<Sample> samples;
	private final ConcurrentMap<String, OutputStream> outputs = new ConcurrentHashMap<>();
	private final List<Thread> threads = new ArrayList<>();

	public static void main(String[] args) {
		new DemultiplexDualIndex().run(args);
	}

	private void abort() {
		abort = true;
		threads.forEach(Thread::interrupt);
		synchronized(semaphore) {
			semaphore.notifyAll();
		}
	}

	public void run(String[] args) {
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				abort();
				while (!done) {
					synchronized (semaphore) {
						if (!done) {
							try {
								semaphore.wait();
							} catch (InterruptedException e) {
								throw new RuntimeException(e);
							}
						}
					}
				}
			}));
			DemultiplexDualIndex instance = new DemultiplexDualIndex();
			instance.readSamples(new File(args[0]));
			instance.processFourLineChunks(
				new File(args[1]),
				new File(args[2]),
				instance.demultiplexer);
		} catch (Exception e) {
			abort();
			throw new RuntimeException(e);
		}
	}

	private static class Sample {
		private final @NonNull String name;
		private final @NonNull String i5Barcode;
		private final @NonNull String i7Barcode;

		Sample(@NonNull String name, @NonNull String i5Barcode,
				@NonNull String i7Barcode) {
			this.name = name;
			this.i5Barcode = i5Barcode;
			this.i7Barcode = i7Barcode;
		}
	}

	private void readSamples(File f) throws IOException {
		SettableInteger counter = new SettableInteger(0);
		try(Stream<String> lines = Files.lines(Paths.get(f.getAbsolutePath()))) {
			samples = lines.map(l -> {
				if (counter.getAndIncrement() == 0) {
					System.err.println(l);
					return Optional.<Sample>empty();
				}
				@NonNull String [] split = l.split("\t");
				if (split.length != 3) {
					throw new ParseRTException("Expected 3 fields in " + Arrays.toString(split));
				}
				@NonNull String name = split[0].replaceAll("\\s",""),
					i7 = split[1].replaceAll("\\s",""),
					i5 = split[2].replaceAll("\\s","");
				try {
					checkSimpleBases(i7);
					checkSimpleBases(i5);
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem validating " + l, e);
				}
				return Optional.of(new Sample(name, i5, i7));
			}).filter(Optional::isPresent).map(Optional::get).
			collect(Collectors.toList());
		}

	}

	private static final Set<Character> PERMISSIBLE_CHARS = new HashSet<>(Arrays.asList('A', 'T',
		'G', 'C', 'N'));

	private static void checkSimpleBases(String s) {
		s.chars().map(Character::toUpperCase).forEach(c -> {
			if (!PERMISSIBLE_CHARS.contains((char) c)) {
				throw new ParseRTException(new String(new char[] {(char) c}) + " in sequence " + s + " is not a valid base");
			}
		});
	}

	private OutputStream getOutput(Sample sample, String readNumber) {
		return outputs.computeIfAbsent(sample.name + readNumber, newSampleType -> {
			try {
				return new FileOutputStream(newSampleType.substring(0, newSampleType.length() - 1) +
					"-READ" + readNumber + "-Sequences.txt", false);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@SuppressWarnings("resource")
	private void outputReads(FourLines fl1, FourLines fl2, Sample s) {

		final OutputStream os1 = getOutput(s, "1");
		final OutputStream os2 = getOutput(s, "2");

		synchronized(os1) {
			fl1.write(os1);
			fl2.write(os2);
		}
	}

	private final static Sample LOUSY = new Sample("LOUSY", "XXX", "XXX");

	public final BiConsumer<FourLines, FourLines> demultiplexer = (fl1, fl2) -> {
		final @NonNull String read1 = Objects.requireNonNull(fl1.lines.get(0));
		final @NonNull String read2 = Objects.requireNonNull(fl2.lines.get(0));

		final Pair<String, String> barcodes1 = extract_i7_i5_barcodes(read1);
		final Pair<String, String> barcodes2 = extract_i7_i5_barcodes(read2);

		if (!barcodes1.equals(barcodes2)) {
			throw new IllegalInputException("Barcodes should be equal but found " + barcodes1 + " vs " + barcodes2);
		}

		final String i7 = barcodes1.fst;
		final String i5 = barcodes1.snd;

		IntMinMax<Sample> im = new IntMinMax<>();
		im.acceptMax(samples, s -> scoreSampleMatch((Sample) s, i7, i5));
		final int score = im.getMax();

		final Sample s = score < 9 ? LOUSY : im.getKeyMax();

		outputReads(fl1, fl2,  s);

	};

	private static Pair<String, String> extract_i7_i5_barcodes(String line1) {
		final int plusPosition = line1.indexOf('+');
		if (plusPosition == -1 || line1.indexOf('+', plusPosition + 1) != -1) {
			throw new ParseRTException("Expected 1 occurence of '+' in " + line1);
		}
		int colonBeforePlus = -1;
		while(true) {
			int next = line1.indexOf(':', colonBeforePlus + 1);
			if (next == -1) {
				break;
			}
			if (next > plusPosition) {
				throw new ParseRTException("Found ':' past '+' in substring " + line1.substring(colonBeforePlus) + " from " + line1);
			}
			colonBeforePlus = next;
		}
		if (colonBeforePlus == -1) {
			throw new ParseRTException("Expected ':' in substring " + line1.substring(colonBeforePlus) + " from " + line1);
		}
		return new Pair<>(line1.substring(colonBeforePlus + 1, plusPosition), line1.substring(plusPosition + 1));
	}

	private static int scoreSequenceMatch(String s1, String s2) {
		if (s1.length() != s2.length()) {
			throw new IllegalInputException();
		}
		int score = 0;
		for (int i = 0; i < s1.length(); i++) {
			char c1 = Character.toUpperCase(s1.charAt(i));
			char c2 = Character.toUpperCase(s2.charAt(i));
			if (c1 == c2) {
				score++;
			} else if (c1 == 'N' || c2 == 'N') {
				score += 0;
			} else {
				score--;
			}
		}
		return score;
	}

	private static int scoreSampleMatch(Sample s, String i7, String i5) {
		return scoreSequenceMatch(s.i7Barcode, i7) + scoreSequenceMatch(s.i5Barcode, i5);
	}

	public void processFourLineChunks(
			File inputFile1,
			File inputFile2,
			BiConsumer<FourLines, FourLines> consumer) throws IOException {
		final @NonNull AtomicInteger nChunks = new AtomicInteger();

		Signals.SignalProcessor infoSignalHandler = signal ->
			System.err.println("Currently reading records from file " + inputFile1.getAbsolutePath() + "; " +
			DoubleAdderFormatter.nf.get().format(nChunks) + " total so far");
		Signals.registerSignalProcessor("INFO", infoSignalHandler);

		try (Stream<String> lines1 = Files.lines(Paths.get(inputFile1.getAbsolutePath()));
				Stream<String> lines2 = Files.lines(Paths.get(inputFile2.getAbsolutePath()))) {
			consumeFourLineChunks(lines1, lines2, consumer, nChunks);
		} finally {
			Signals.removeSignalProcessor("INFO", infoSignalHandler);
			done = true;
			synchronized (semaphore) {
				semaphore.notifyAll();
			}
		}
	}

	private static final FourLines END_MARKER = new FourLines();
	static {
		END_MARKER.lines.add("END_MARKER");
	}

	private void consumeFourLineChunks(
			Stream<String> lines1,
			Stream<String> lines2,
			BiConsumer<FourLines, FourLines> consumer,
			AtomicInteger nChunks) {

		final BlockingQueue<FourLines> queue1 = new ArrayBlockingQueue<>(10_000),
			queue2 = new ArrayBlockingQueue<>(10_000);

		final Thread readLines1, readLines2;
		try {

			final LineProcessor processor1 = new LineProcessor(queue1, nChunks);
			final LineProcessor processor2 = new LineProcessor(queue2, nChunks);

			readLines1 = new Thread(() -> {
				lines1.forEachOrdered(processor1);
				try {
					queue1.offer(END_MARKER, Long.MAX_VALUE, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			});
			readLines2 = new Thread(() -> {
				lines2.forEachOrdered(processor2);
				try {
					queue2.offer(END_MARKER, Long.MAX_VALUE, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			});

			readLines1.setDaemon(true);
			readLines2.setDaemon(true);
			readLines1.start();
			readLines2.start();

		} catch (Exception e) {
			throw new RuntimeException("Problem reading from stream", e);
		}

		final Handle<Throwable> exception = new Handle<>();
		threads.clear();
		Runnable r = () ->
		{
			try {
				while (!abort) {
					final FourLines localFourLines1, localFourLines2;
					try {
						synchronized(END_MARKER) {
							localFourLines1 = queue1.poll(Long.MAX_VALUE, TimeUnit.DAYS);
							localFourLines2 = queue2.poll(Long.MAX_VALUE, TimeUnit.DAYS);
						}
					} catch (InterruptedException e) {
						break;
					}

					if (localFourLines1 == END_MARKER) {
						if (!(localFourLines2 == END_MARKER)) {
							throw new RuntimeException("Read 1 stream shorter than read 2 stream; extra read " +
								localFourLines2);
						}
						queue1.add(END_MARKER);
						queue2.add(END_MARKER);
						break;
					}

					if (localFourLines2 == END_MARKER) {
						throw new RuntimeException("Read 2 stream shorter than read 1 stream; extra read " +
							localFourLines1);
					}

					consumer.accept(localFourLines1, localFourLines2);

				}
			} catch (Throwable e0) {
				abort = true;
				e0.printStackTrace();
				if (exception.get() == null) {
					exception.set(e0);
				}
				readLines1.interrupt();
				readLines2.interrupt();
			}
		};

		for (int i = 0; i < N_CONSUMER_THREADS; i++) {
			Thread t = new Thread(r, "Read " + i);
			threads.add(t);
			t.start();
		}

		for (Thread t: threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		if (exception.get() != null) {
			throw new RuntimeException(exception.get());
		}

	}

	private static class LineProcessor implements Consumer<String> {

		private final BlockingQueue<FourLines> queue;
		private final SettableInteger lineModulo = new SettableInteger(0);
		private final Handle<FourLines> fourLines = new Handle<>();
		private final AtomicInteger nChunks;

		LineProcessor(BlockingQueue<FourLines> queue, AtomicInteger nChunks){
			this.queue = queue;
			this.nChunks = nChunks;
			fourLines.set(new FourLines());
		}

		@Override
		public void accept(String l) {

				int line = lineModulo.incrementAndGet();
				FourLines fl = fourLines.get();
				if (line < 3) {
					fl.lines.add(l);
				} else if (line == 3) {
					if (!"+".equals(l)) {
						throw new ParseRTException("Expected + but got " + l);
					}
					fl.lines.add("+");
				} else if (line == 4) {
					fl.lines.add(l);
					try {
						queue.offer(fl, Long.MAX_VALUE, TimeUnit.DAYS);
						nChunks.incrementAndGet();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					fourLines.set(new FourLines());
					lineModulo.set(0);
				}
		}

	}
}
