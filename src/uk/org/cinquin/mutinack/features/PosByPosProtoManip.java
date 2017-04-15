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

package uk.org.cinquin.mutinack.features;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.jwetherell.algorithms.data_structures.IntervalTree;
import com.jwetherell.algorithms.data_structures.IntervalTree.IntervalData;

import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters.HideInToString;
import uk.org.cinquin.mutinack.Parameters.SwallowCommasConverter;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.ContigNumbers;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers;
import uk.org.cinquin.mutinack.features.PosByPosNumbersPB.GenomeNumbers.Builder;
import uk.org.cinquin.mutinack.misc_util.GitCommitInfo;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.MapOfLists;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.statistics.Counter;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.parfor.ParFor;

public class PosByPosProtoManip {

	public static class Params {
		@Parameter(names = "-startAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
				converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
		public List<@NonNull String> startAtPositions = new ArrayList<>();

		@Parameter(names = "-stopAtPosition", description = "Formatted as chrI:12,000,000 or chrI:12000000; specify up to once per contig", required = false,
				converter = SwallowCommasConverter.class, listConverter = SwallowCommasConverter.class)
		public List<@NonNull String> stopAtPositions = new ArrayList<>();

		@Parameter(names = "-outputAll", description = "Output file with a reported value at each position", required = false)
		public boolean outputAll = false;

		@Parameter(names = "-threshold", description = "Only positions with counts greater than or equal to this value will be reported", required = true)
		public int threshold = 1;

		@Parameter(names = "-input", description = "Protobuf file name or - for stdin", required = true, variableArity = true)
		public List<String> inputs;

		@Parameter(names = "-invertInputs", description = "Protobuf file name or - for stdin", required = false)
		public boolean invertInputs;

		@Parameter(names = "-domainBedFile", description = "Only domains defined in following bed file will be used for histogram computation", required = false)
		public String domainBedFile = "";

		@Parameter(names = "-output", description = "File name or - for stdout", required = true)
		public String output;

		@Parameter(names = "-expMovAverage", description = "For thresholdToBed, compute exponential moving average", required = false)
		public boolean expMovAverage;

		@Parameter(names = "-binSize", description = "bin size for exponential moving average", required = false)
		public int binSize = 100_000;

		@Parameter(names = "-alpha", description = "alpha for exponential moving average", required = false)
		public float alpha;

		@Parameter
		public List<String> mainParam;

		@HideInToString
		private static final Params defaultValues = new Params();

		@Override
		public String toString() {
			String defaultValuesString = "";
			String nonDefaultValuesString = "";
			for (Field field: Params.class.getDeclaredFields()) {
				try {
					if (field.getAnnotation(HideInToString.class) != null)
						continue;
					Object fieldValue = field.get(this);
					Object fieldDefaultValue = field.get(defaultValues);
					String stringValue;
					if (fieldValue == null)
						stringValue = field.getName()+ " = null";
					else
						stringValue = field.getName() + " = " + fieldValue.getClass().getMethod("toString").invoke
							(fieldValue);
					boolean fieldHasDefaultValue = fieldValue == null ? fieldDefaultValue == null :
						Boolean.TRUE.equals(fieldValue.getClass().getMethod("equals",Object.class).invoke
							(fieldValue, fieldDefaultValue));
					if (fieldHasDefaultValue) {
						defaultValuesString += "Default parameter value: " + stringValue + '\n';
					} else {
						nonDefaultValuesString += "Non-default parameter value: " + stringValue + '\n';
					}
				} catch (IllegalArgumentException | IllegalAccessException | 
						InvocationTargetException | NoSuchMethodException | SecurityException e) {
					throw new RuntimeException(e);
				}
			}
			return "Working directory: " + System.getProperty("user.dir") + '\n' +
					nonDefaultValuesString + '\n' + defaultValuesString + '\n';
		}
	}

	public static void main(String [] args) throws IOException, InterruptedException {

		final Params argValues = new Params();
		JCommander commander = new JCommander();
		commander.setAcceptUnknownOptions(false);
		commander.setAllowAbbreviatedOptions(false);
		commander.addObject(argValues);
		commander.parse(args);

		if (argValues.mainParam.isEmpty()) {
			throw new IllegalArgumentException("No main command found in arguments " +
					Arrays.toString(args));
		} else if (argValues.mainParam.size() > 1) {
			throw new IllegalArgumentException("Multiple main commands found: " + 
					argValues.mainParam);
		}

		String command = argValues.mainParam.get(0);

		switch (command) {
			case "sum":
				mathOp(argValues);
			break;
			case "hist": histogram(argValues);
			break;
			case "thresholdToBed":
				thresholdToBed(argValues);
			break;
			case "selectBedIntervals": selectBedIntervals(argValues);
			break;
			default: throw new IllegalArgumentException("Unknown command " + args[0]);
		}
		if (ParFor.defaultThreadPool != null) {
			ParFor.defaultThreadPool.shutdown();
		}
	}

	private static void checkArgumentLength(int length, List<?> list, String errorMessage) {
		if (list.size() != length) {
			throw new IllegalArgumentException(errorMessage + " but found " + list.size() +
					" elements: " + list);
		}
	}

	private static void iterateGenomeNumbers(Params argValues, GenomeNumbers gn1,
			BiConsumer<SequenceLocation, Integer> processor) {
		List<@NonNull String> contigNames0 = gn1.getContigNumbersList().stream().map(ContigNumbers::getContigName).
				collect(Collectors.toList());
		contigNames0.sort(null);
		Map<String, Integer> contigIndices = new HashMap<>();
		for (int i = 0; i < contigNames0.size(); i++) {
			contigIndices.put(contigNames0.get(i), i);
		}

		Pair<List<String>, List<Integer>> p = 
				Util.parseListPositions(argValues.startAtPositions, true, "startAtPositions");	
		final List<String> startAtContigs = p.fst;
		final List<Integer> startAtPositions = p.snd;

		Pair<List<String>, List<Integer>> p2 = Util.parseListPositions(argValues.stopAtPositions, true, "stopAtPositions");	
		final List<String> stopAtContigs = p2.fst;
		final List<Integer> stopAtPositions = p2.snd;

		Util.checkPositionsOrdering(p, p2);

		for (ContigNumbers cnl: gn1.getContigNumbersList()) {
			final @NonNull String contigName = cnl.getContigName();
			final int[] numbers1 = cnl.getNumbersArray();
			if (numbers1 == null) {
				continue;
			}
			final int initialI;
			if (!startAtContigs.contains(contigName)) {
				if (!startAtContigs.isEmpty()) {
					continue;
				}
				initialI = 0;
			} else {
				initialI = startAtPositions.get(startAtContigs.indexOf(contigName));
			}
			final int finalI;
			if (!stopAtContigs.contains(contigName)) {
				finalI = numbers1.length - 1;
			} else {
				finalI = stopAtPositions.get(stopAtContigs.indexOf(contigName));
			}

			final int contigId = Objects.requireNonNull(contigIndices.get(contigName));

			for (int i = initialI; i <= finalI; i++) {
				processor.accept(new SequenceLocation(contigId, contigName, i), numbers1[i]);
			}
		}
	}

	/**
	 * Given a proto input and a BED file, computes intervals within the bed file
	 * that have at least one count in the proto input
	 * TODO Send output to file if specified instead of stdout
	 * @param argValues
	 * @throws IOException
	 */
	private static void selectBedIntervals(Params argValues) throws IOException {
		checkArgumentLength(1, argValues.inputs, "Exactly 1 input expected for selectBedIntervals command");
		if ("".equals(argValues.domainBedFile)) {
			throw new IllegalArgumentException("Must specify input bed file");
		}

		Set<GenomeInterval> resultIntervals = new HashSet<>();
		SettableInteger nPos = new SettableInteger(0);

		GenomeNumbers gn1 = getFromFile(argValues.inputs.get(0));
		List<@NonNull String> contigNames0 = gn1.getContigNumbersList().stream().
				map(ContigNumbers::getContigName).collect(Collectors.toList());
		contigNames0.sort(null);
		GenomeFeatureTester reader;
		try (FileReader fileReader = new FileReader(new File(argValues.domainBedFile))) {
			GenomeFeatureTester reader0 = new BedReader(contigNames0,
					new BufferedReader(fileReader),
					argValues.domainBedFile, null, false);
			if (argValues.invertInputs) {
				System.err.println("Inverting input bed " + argValues.domainBedFile);
				reader = new BedComplement(reader0);
			} else {
				reader = reader0;
			}
		}
		//Do a first pass to identify BED intervals that have at least 1 non-zero count
		//in the protobuf file
		iterateGenomeNumbers(argValues, gn1, (location, n) -> {
			if (n > 0) {
				resultIntervals.addAll(reader.apply(location));
			}
			nPos.incrementAndGet();			
		});
		System.err.print("Iterated over " + nPos + " positions");
		Pair<List<String>, List<Integer>> p = 
				Util.parseListPositions(argValues.startAtPositions, true, "startAtPositions");	
		final List<String> startAtContigs = p.fst;
		if (startAtContigs.size() > 1) {
			System.err.println(" in contigs " + startAtContigs);
		} else {
			System.err.println();
		}

		//Second pass to compute overall coverage of selected intervals
		//Note we can't just sum sizes of selected intervals since they
		//may overlap

		final MapOfLists<String, IntervalTree.IntervalData<GenomeInterval>>
			bedFileIntervals = new MapOfLists<>();		
		AtomicInteger lineCount = new AtomicInteger(0);
		int contigIndex = 0;
		for (String s: contigNames0) {
			lineCount.incrementAndGet();
			bedFileIntervals.addAt(s, new IntervalTree.IntervalData<>(-1, -1, 
					new GenomeInterval("", contigIndex++, s, -1, -1, null, Util.emptyOptional(), 0)));
		}

		for (GenomeInterval interval: resultIntervals) {
			bedFileIntervals.addAt(interval.contigName, 
					new IntervalTree.IntervalData<>(interval.getStart(), interval.getEnd(), interval));
		}

		List<Entry<String, List<IntervalData<GenomeInterval>>>> sortedContigs = 
				bedFileIntervals.entrySet().stream().sorted(
					Comparator.comparing(Entry::getKey)).collect(Collectors.toList());

		final List<IntervalTree<GenomeInterval>> contigTrees = new ArrayList<>();
		//NB For this to work the contig IDs used in the test function must match
		//alphabetical order of contig names
		for (Entry<String, List<IntervalData<GenomeInterval>>> sortedContig: sortedContigs) {
			contigTrees.add(new IntervalTree<>(sortedContig.getValue()));
		}

		SettableInteger positivePositions = new SettableInteger(0);
		SettableInteger withinBedPositions = new SettableInteger(0);
		SettableInteger testedPositions = new SettableInteger(0);

		iterateGenomeNumbers(argValues, gn1, (location, n) -> {
			testedPositions.incrementAndGet();
			if (reader.test(location)) {
				withinBedPositions.getAndIncrement();
			}
			if (!contigTrees.get(location.contigIndex).query(location.position).getUnprotectedData().isEmpty()) {
				positivePositions.incrementAndGet();
			}
		});

		System.out.println(positivePositions + " of " + withinBedPositions + " positions in BED file " + 
				"belong to interval with at least one hit (total of " + testedPositions + " genome positions scanned)");
	}

	//TODO Use iterateGenomeNumbers instead of duplicating code
	private static void histogram(Params argValues) throws IOException {
		checkArgumentLength(1, argValues.inputs, "Exactly 1 input expected for histogram command");
		GenomeNumbers gn1 = getFromFile(argValues.inputs.get(0));
		List<@NonNull String> contigNames0 = gn1.getContigNumbersList().stream().map(ContigNumbers::getContigName).
				collect(Collectors.toList());
		contigNames0.sort(null);
		Map<String, Integer> contigIndices = new HashMap<>();
		for (int i = 0; i < contigNames0.size(); i++) {
			contigIndices.put(contigNames0.get(i), i);
		}

		Pair<List<String>, List<Integer>> p = 
				Util.parseListPositions(argValues.startAtPositions, true, "startAtPositions");	
		final List<String> startAtContigs = p.fst;
		final List<Integer> startAtPositions = p.snd;

		Pair<List<String>, List<Integer>> p2 = Util.parseListPositions(argValues.stopAtPositions, true, "stopAtPositions");	
		final List<String> stopAtContigs = p2.fst;
		final List<Integer> stopAtPositions = p2.snd;

		Util.checkPositionsOrdering(p, p2);

		String output = argValues.output;

		@SuppressWarnings("resource")
		Counter<Integer> counter = new Counter<>(false, new MutinackGroup(false));

		int nPos = 0;

		final GenomeFeatureTester reader;
		if (!argValues.domainBedFile.isEmpty()) {
			try (FileReader fileReader = new FileReader(new File(argValues.domainBedFile))) {
				GenomeFeatureTester reader0 = new BedReader(contigNames0,
						new BufferedReader(fileReader),
						argValues.domainBedFile, null, false);
				if (argValues.invertInputs) {
					System.err.println("Inverting input bed " + argValues.domainBedFile);
					reader = new BedComplement(reader0);
				} else {
					reader = reader0;
				}
			}
		} else {
			reader = null;
		}

		try (PrintStream writer = output.equals("-") ?
							System.out :
							new PrintStream(new FileOutputStream(output))) {
			for (ContigNumbers cnl: gn1.getContigNumbersList()) {
				final @NonNull String contigName = cnl.getContigName();
				final int[] numbers1 = cnl.getNumbersArray();
				if (numbers1 == null) {
					continue;
				}
				final int initialI;
				if (!startAtContigs.contains(contigName)) {
					if (!startAtContigs.isEmpty()) {
						continue;
					}
					initialI = 0;
				} else {
					initialI = startAtPositions.get(startAtContigs.indexOf(contigName));
				}
				final int finalI;
				if (!stopAtContigs.contains(contigName)) {
					finalI = numbers1.length - 1;
				} else {
					finalI = stopAtPositions.get(stopAtContigs.indexOf(contigName));
				}

				final int contigId = Objects.requireNonNull(contigIndices.get(contigName));

				for (int i = initialI; i <= finalI; i++) {
					if (reader != null) {
						if (!reader.test(new SequenceLocation(contigId, contigName, i))) {
							continue;
						}
					}
					counter.accept(numbers1[i]);
					nPos++;
				}
			}
			System.err.print("Iterated over " + nPos + " positions");
			if (!startAtContigs.isEmpty()) {
				System.err.println(" in contigs " + startAtContigs);
			} else {
				System.err.println();
			}

			@NonNull Map<Object, @NonNull Object> counts = counter.getCounts();
			int max = counts.keySet().stream().mapToInt(i -> ((Integer) i)).
					max().getAsInt();

			for (int i = 0; i <= max; i++) {
				Object o = counts.get(i);
				final int value;
				if (o == null) {
					value = 0;
				} else {
					value = (int) ((DoubleAdderFormatter) o).sum();
				}
				writer.println(i + "\t" + value);
			}
		}
	}

	//Adapted from http://stackoverflow.com/questions/22694884/filter-java-stream-to-1-and-only-1-element
	private static Collector<Pair<String, ContigNumbers>, List<ContigNumbers>, ContigNumbers> singletonCollector() {
	    return Collector.of(
	            ArrayList<ContigNumbers>::new,
	            (list, pair) -> list.add(pair.snd),
	            (left, right) -> { left.addAll(right); return left; },
	            list -> {
	                if (list.size() != 1) {
	                    throw new IllegalStateException("Multiple samples with same name");
	                }
	                return list.get(0);
	            }
	    );
	}

	private static void mathOp(Params argValues) throws IOException, InterruptedException {
		Builder builder = PosByPosNumbersPB.GenomeNumbers.newBuilder();
		builder.setGeneratingProgramVersion(GitCommitInfo.getGitCommit());
		builder.setGeneratingProgramArgs(argValues.toString());
		builder.setSampleName(argValues.output);

		final List<GenomeNumbers> inputs = new ArrayList<>();
		ParFor parFor = new ParFor("Parse protobuf", 0, argValues.inputs.size() - 1, null, true);
		for (int thread = 0; thread < parFor.getNThreads(); thread++) {
			parFor.addLoopWorker((i, j) -> {
				GenomeNumbers gn;
				try {
					gn = getFromFile(argValues.inputs.get(i));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				inputs.add(gn);
				return null;
			});
		}
		parFor.run(true);

		Set<String> sampleNames = new HashSet<>();
		for (GenomeNumbers g: inputs) {
			int increment = 0;
			while (!sampleNames.add(g.getSampleName())) {
				g.setSampleName(g.getSampleName() + (increment++));
			}
		}

		Set<String> contigNames0 = inputs.get(0).getContigNumbersList().stream().map(ContigNumbers::getContigName).
			collect(Collectors.toSet());
		for (int i = 1; i < inputs.size(); i++) {
			Set<String> contigNames1 = inputs.get(i).getContigNumbersList().stream().map(ContigNumbers::getContigName).
					collect(Collectors.toSet());
			if (!contigNames0.equals(contigNames1)) {
				throw new IllegalArgumentException("Contig name sets not equal: " +
						contigNames0 + " vs " + contigNames1);
			}
		}

		for (ContigNumbers cnl: inputs.get(0).getContigNumbersList()) {
			PosByPosNumbersPB.ContigNumbers.Builder builder2 =
					PosByPosNumbersPB.ContigNumbers.newBuilder();
			builder2.setContigName(cnl.getContigName());

			Map<String, ContigNumbers> mapArrays = inputs.stream().flatMap(c -> 
					c.getContigNumbersList().stream().map(cnl2 -> new Pair<>(c.getSampleName(), Objects.requireNonNull(cnl2)))).
				filter(cn -> cn.snd.getContigName().equals(cnl.getContigName())).
				collect(Collectors.groupingBy(p -> p.fst, singletonCollector()));

			SettableInteger length = new SettableInteger(-1);
			List<int[]> allNumbers = mapArrays.entrySet().stream().map(entry -> {
				String sampleName = entry.getKey();
				ContigNumbers contigNumbers = entry.getValue();
				int [] numbers = contigNumbers.getNumbersArray();
				if (numbers == null) {
					throw new IllegalArgumentException("No numbers for contig " + cnl.getContigName() +
							" in sample " + sampleName);
				}
				if (length.get() > -1 && numbers.length != length.get()) {
					throw new IllegalArgumentException("Contig " + cnl.getContigName() +
							" in sample " + sampleName + " has a different length than same contig from " +
							" another sample: " + numbers.length + " vs " + length.get());
				}
				length.set(numbers.length);
				return numbers;
			}).collect(Collectors.toList());


			builder2.ensureNumbersIsMutable(length.get());
			builder2.numUsedInNumbers_ = length.get();
			final int[] resultNumbers = builder2.getNumbersArray();

			SettableInteger index = new SettableInteger(0);
			final Runnable operation;
			switch (argValues.mainParam.get(0)) {
				case "sum": operation = () -> {
						int i = index.get();
						for (int[] a: allNumbers) {
							resultNumbers[i] += a[i];
						}
					};
				break;
				default: 
					throw new AssertionFailedException(
							"Unknown operation " + argValues.mainParam.get(0));
			}

			for (int i = 0; i < length.get(); i++) {
				index.set(i);
				operation.run();
			}
			builder.addContigNumbers(builder2);
		}

		if (builder.getSampleName().startsWith("-")) {
			builder.setSampleName(builder.getSampleName().substring(1));
			System.out.write(builder.build().toByteArray());
		} else {
			Path path = Paths.get(builder.getSampleName() + (
					builder.getSampleName().endsWith(".proto") ? "" : ".proto"));
			Files.write(path, builder.build().toByteArray());
		}
	}

	private static void thresholdToBed(Params argValues) throws IOException {
		checkArgumentLength(1, argValues.inputs, "Exactly 1 input expected for thresholdToBed");

		final int threshold = argValues.threshold;
		final boolean outputAll = argValues.outputAll;

		Pair<List<String>, List<Integer>> p = 
				Util.parseListPositions(argValues.startAtPositions, true, "startAtPositions");	
		final List<String> startAtContigs = p.fst;
		final List<Integer> startAtPositions = p.snd;

		Pair<List<String>, List<Integer>> p2 = Util.parseListPositions(argValues.stopAtPositions, true, "stopAtPositions");	
		final List<String> stopAtContigs = p2.fst;
		final List<Integer> stopAtPositions = p2.snd;

		Util.checkPositionsOrdering(p, p2);

		GenomeNumbers gn1 = getFromFile(argValues.inputs.get(0));

		final boolean movingAverage = argValues.expMovAverage;

		try (PrintStream writer = argValues.output.equals("-") ?
									System.out :
									new PrintStream(new FileOutputStream(argValues.output))) {
			int nPos = 0;
			for (ContigNumbers cnl: gn1.getContigNumbersList()) {
				final int[] numbers1 = cnl.getNumbersArray();
				if (numbers1 == null) {
					continue;
				}
				final String contigName = cnl.getContigName();
				final int initialI;
				if (!startAtContigs.contains(contigName)) {
					if (!startAtContigs.isEmpty()) {
						continue;
					}
					initialI = 0;
				} else {
					initialI = startAtPositions.get(startAtContigs.indexOf(contigName));
				}
				final int finalI;
				if (!stopAtContigs.contains(contigName)) {
					finalI = numbers1.length - 1;
				} else {
					finalI = stopAtPositions.get(stopAtContigs.indexOf(contigName));
				}
				int indexLastChange = initialI;
				boolean aboveThreshold = numbers1[initialI] >= threshold;
				double averaged = numbers1[initialI];
				final double alpha = argValues.alpha;
				int nInBin = 0;
				for (int i = initialI; i <= finalI; i++) {
					nPos++;
					final int localValue = numbers1[i];
					final boolean newAboveThreshold = localValue >= threshold;

					if (newAboveThreshold && movingAverage) {
						averaged = alpha * localValue + (1 - alpha) * averaged;
					}

					if (movingAverage) {
						if (nInBin++ == argValues.binSize) {
							nInBin = 0;
							writer.append(contigName + '\t' +
									i + '\t' +
									averaged + '\n');
						}
					} else if ((aboveThreshold ^ newAboveThreshold) || i == finalI
							|| (outputAll && i > initialI)) {
						if (aboveThreshold) {
							writer.append(	contigName + '\t' +
									(indexLastChange + 1)  + '\t' +
									((i - 1) + 1) + '\t' +
									numbers1[i - 1] /* Need other stuff?*/ + '\n');
						}
						indexLastChange = i;
					}
					aboveThreshold = newAboveThreshold;
				}
			}
			System.err.print("Iterated over " + nPos + " positions");
			if (startAtContigs.size() > 1) {
				System.err.println(" in contigs " + startAtContigs);
			} else {
				System.err.println();
			}
		}	
	}

	private static GenomeNumbers getFromFile(String path) throws IOException {
		final byte[] bytes;
		if (path.equals("-")) {
			//Adapted from http://stackoverflow.com/questions/18936195/read-all-standard-input-into-a-java-byte-array
			ByteBuffer buf = ByteBuffer.allocate(200_000_000);
			try (ReadableByteChannel channel = Channels.newChannel(System.in)) {
				while (channel.read(buf) >= 0) {}
				buf.flip();
			}
			bytes = Arrays.copyOf(buf.array(), buf.limit());
		} else {
			Path path1 = Paths.get(path);
			bytes = Files.readAllBytes(path1);
		}
		GenomeNumbers gn = PosByPosNumbersPB.GenomeNumbers.parseFrom(bytes);
		checkNoContigNameDups(gn, path);
		return gn;
	}

	private static void checkNoContigNameDups(GenomeNumbers gn1, Set<String> nameSet, String sampleName) {
		for (ContigNumbers cn: gn1.getContigNumbersList()) {
			String name = cn.getContigName();
			if (!nameSet.add(name)) {
				throw new IllegalArgumentException("Duplicate entry of " + name +
					" in " + sampleName);
			}
		}
	}

	private static void checkNoContigNameDups(GenomeNumbers gn1, String sampleName) {
		checkNoContigNameDups(gn1, new HashSet<>(), sampleName);
	}
}
