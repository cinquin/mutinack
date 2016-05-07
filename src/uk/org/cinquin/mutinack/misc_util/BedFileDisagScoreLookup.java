package uk.org.cinquin.mutinack.misc_util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.features.ParseRTException;

public class BedFileDisagScoreLookup {

	/**
	 * First argument: path to file with list of disagreements and their locations,
	 * 	formatted as the "outputTopBottomDisagreementBED" Mutinack output files.
	 * Second argument: path to BED file containing scores
	 * Third and fourth arguments: min and max length of the disagreement (inclusive)
	 * 
	 * Output: score of each disagreement (or -1 if not found in BED file).
	 * @param args
	 * @throws IOException
	 * @throws ParseRTException
	 */
	public static void main(String[] args) throws IOException, ParseRTException {
		final String locationsFile = args[0];
		final String scoreBedFile = args[1];
		final float minLength = Float.parseFloat(args[2]);
		final float maxLength = Float.parseFloat(args[3]);
		
		final Map<Integer, @NonNull String> indexContigNameMap = new HashMap<>();
		final List<@NonNull String> contigNames = Parameters.defaultTruncateContigNames;
		
		for (int i = 0; i < contigNames.size(); i++) {
			indexContigNameMap.put(i, contigNames.get(i));
		}
		
		final BedReader scores;
		try (BufferedReader br = new BufferedReader(new FileReader(
				new File(scoreBedFile)))) {
			scores = new BedReader(indexContigNameMap, br, "scores reader", null, true);
		}

		SettableInteger inRange = new SettableInteger(0);
		SettableInteger outOfRange = new SettableInteger(0);

		try (FileReader fileReader = new FileReader(new File(locationsFile))) {
			Files.lines(Paths.get(locationsFile)).forEach(line -> {
				final String[] split = line.split("\t");
				
				final int sequenceLength = split[3].length();
				if (sequenceLength > maxLength || sequenceLength < minLength) {
					outOfRange.incrementAndGet();
					return;
				} else {
					inRange.incrementAndGet();
				}

				final String contig = split[0];
				final int position = Integer.parseInt(split[1]);
				Collection<GenomeInterval> intervals = scores.apply(
					new SequenceLocation(contigNames.indexOf(contig),
						indexContigNameMap, position));
				
				List<String> addToEnd = new ArrayList<>();
				Arrays.stream(split).skip(3).forEach(addToEnd::add);
								
				final float result;
				if (intervals.isEmpty()) {
					result = -1;
				} else {
					result = (float) 
						intervals.stream().mapToDouble(i -> i.getScore()).sum();
					intervals.forEach(i -> addToEnd.add(i.toString()));
				}
				
				String endOfLine = addToEnd.stream().
					collect(Collectors.joining("\t"));
				
				System.out.println(contig + ":" + position + "\t" + result + "\t" +
					endOfLine);
			});
			
			System.err.println(outOfRange.get() + " out of range; " +
				inRange.get() + " in range");
		}
	}
}
