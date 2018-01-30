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

package uk.org.cinquin.mutinack;

import static uk.org.cinquin.mutinack.features.BedReader.invertList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SingleTimeAction;
import uk.org.cinquin.mutinack.misc_util.SingleTimePrinter;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

public class MutationListReader {

	@SuppressWarnings("resource")
	public static Map<Pair<@NonNull SequenceLocation, @NonNull String>,
			@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>> readMutationList(
				String path, String readerName, List<@NonNull String> contigNames, @NonNull String referenceGenomeName,
		@NonNull Set<String> sampleNames, @NonNull Set<String> unknownSamples) {
		try {
			return readMutationList(new BufferedReader(new FileReader(new File(path))), readerName,
				contigNames, referenceGenomeName, sampleNames, unknownSamples);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Pattern groupOfDigits = Pattern.compile("\\d+");

	public static ConcurrentMap<Pair<@NonNull SequenceLocation, @NonNull String>,
		@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>> readMutationList(
			BufferedReader reader, String readerName, List<@NonNull String> contigNames,
			@NonNull String referenceGenomeName,
			@NonNull Set<String> sampleNames, @NonNull Set<String> unknownSamples) {

		final ConcurrentMap<Pair<@NonNull SequenceLocation, @NonNull String>,
				@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>> result =
					new ConcurrentHashMap<>();
		final SingleTimeAction<String> contigTranslationPrinter = new SingleTimePrinter();
		try(Stream<String> lines = reader.lines()) {
			lines.forEach(l -> {
				try {
					@NonNull String [] components = l.split("\t");
					if (components.length < 4) {
						throw new ParseRTException("Missing fields");
					}
					final @NonNull String sampleName = components[0];
					if (!sampleNames.contains(sampleName)) {
						unknownSamples.add(sampleName);
						return;
					}
					final String contigName;
					if (!components[1].startsWith("c")) {
						Matcher m = groupOfDigits.matcher(components[1]);
						if (!m.find()) {
							throw new ParseRTException("Could not find digits in " + components[1]);
						}
						contigName = "chr" + m.group();
						contigTranslationPrinter.accept("Translating contig name " + components[1] + " to " + contigName);
					} else {
						contigName = components[1];
					}
					final long position = Long.parseLong(components[2]) - 1;
					final String mutationString = components[3];
					final String mutationKind = components[4];
					final String extra = /*String.join("\t", Arrays.asList(
						Arrays.copyOfRange(components, 5, components.length)));*/ l;
					final Map<String, Integer> reverseIndex = invertList(contigNames);
					final Integer contigIndex = reverseIndex.get(contigName);
					if (contigIndex == null) {
						throw new IllegalArgumentException("Could not find contig " + contigName);
					}
					final SequenceLocation location =
						new SequenceLocation(referenceGenomeName, contigIndex, contigName, (int) position, mutationKind.equals("insertion"));
					final Mutation mutation;
					switch(mutationKind) {
						case "substitution":
							if (mutationString.length() != 2) {
								throw new ParseRTException("Mutation string " + mutationString +
									" should have been of length 2");
							}
							mutation = new Mutation(MutationType.SUBSTITUTION, (byte) mutationString.charAt(0),
								new byte[] {(byte) mutationString.charAt(1)}, Optional.empty());
							break;
						case "deletion":
							if (mutationString.length() < 3 || !mutationString.startsWith("-") ||
									!mutationString.endsWith("-")) {
								throw new ParseRTException("Deletion string " + mutationString +
									" should have been of length at least 3 and start and end with -");
							}
							mutation = new Mutation(MutationType.DELETION, (byte) 0 /* Don't know wt*/,
								mutationString.substring(1, mutationString.length() - 1).getBytes(), Optional.empty());
							break;
						case "insertion":
							if (mutationString.length() < 3 || !mutationString.startsWith("^") ||
									!mutationString.endsWith("^")) {
								throw new ParseRTException("Insertion string " + mutationString +
									" should have been of length at least 3 and start and end with ^");
							}
							mutation = new Mutation(MutationType.INSERTION, (byte) 0 /* Don't know wt*/,
								mutationString.substring(1, mutationString.length() - 1).getBytes(), Optional.empty());
							break;
						default:
							throw new ParseRTException("Unknown mutation type " + mutationKind);
					}
					result.computeIfAbsent(new Pair<>(location, sampleName), o -> new ArrayList<>()).add
						(new Pair<>(mutation, extra));
				} catch (IllegalArgumentException | ParseRTException e) {
					throw new ParseRTException("Error parsing line: " + l + " of " + readerName, e);
				}
			});
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return result;
	}
}
