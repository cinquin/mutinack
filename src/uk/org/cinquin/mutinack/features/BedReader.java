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
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jwetherell.algorithms.data_structures.IntervalData;
import com.jwetherell.algorithms.data_structures.IntervalTree;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.FileCache;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.MapOfLists;
import uk.org.cinquin.mutinack.misc_util.collections.TSVMapReader;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

public class BedReader implements GenomeFeatureTester, Serializable {

	private static final long serialVersionUID = 7826378727266972258L;
	private static final Pattern underscorePattern = Pattern.compile("_");
	private static final Pattern quotePattern = Pattern.compile("\"");
	private static final boolean IGNORE_MISSING_CONTIGS = false;
	private final static @NonNull Optional<Boolean> TRUE_OPTIONAL = Optional.of(true);
	private final static @NonNull Optional<Boolean> FALSE_OPTIONAL = Optional.of(false);
	private static final Set<@NonNull String> missingContigNames = new HashSet<>();

	@JsonIgnore
	public final transient MapOfLists<String, IntervalData<GenomeInterval>> bedFileIntervals;
	@JsonIgnore
	private final transient List<IntervalTree<@NonNull GenomeInterval>> contigTrees = new ArrayList<>();
	@JsonIgnore
	private final transient @NonNull Map<@NonNull String, @NonNull String> suppInfo;
	private final String readerName;

	@Override
	public String toString() {
		return readerName + " BED file tester";
	}

	@SuppressWarnings("resource")
	public static @NonNull BedReader getCachedBedFileReader(String path0, String cacheExtension,
			List<@NonNull String> contigNames, @NonNull String readerName, @NonNull String referenceGenomeName,
			@NonNull Map<@NonNull String, @NonNull String> transcriptToGeneNameMap, Parameters param) {
		@NonNull BedReader result = FileCache.getCached(path0, cacheExtension, path -> {
			try {
				return new BedReader(contigNames,
					new BufferedReader(new FileReader(new File(path))), readerName, referenceGenomeName, null,
						transcriptToGeneNameMap, false, param, null);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, r -> r.contigTrees == null);
		Objects.requireNonNull(result.contigTrees);
		return result;
	}

	public static @NonNull BedReader getCachedBedFileReader(String path0, String cacheExtension,
			List<@NonNull String> contigNames, @NonNull String readerName, @NonNull String referenceGenomeName,
			Parameters param) {
		return getCachedBedFileReader(path0, cacheExtension, contigNames, readerName, referenceGenomeName,
			Collections.emptyMap(), param);
	}

	public final static <K, V> Map<V, K> invertMap(Map<K, V> map) {
		Map<V, K> result = new HashMap<>();
		for (Entry<K, V> e: map.entrySet()) {
			if (result.put(e.getValue(), e.getKey()) != null) {
				throw new IllegalArgumentException("Repeated entry " + e.getValue());
			}
		}
		return result;
	}

	public final static <V> Map<V, Integer> invertList(List<V> list) {
		Map<V, Integer> result = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			if (result.put(list.get(i), i) != null) {
				throw new IllegalArgumentException("Repeated entry " + list.get(i));
			}
		}
		return result;
	}

	public BedReader(
			List<@NonNull String> contigNames,
			BufferedReader reader,
			@NonNull String readerName,
			@NonNull String referenceGenomeName,
			BufferedReader suppInfoReader,
			Parameters param) {
		this(contigNames, reader, readerName, referenceGenomeName, suppInfoReader, Collections.emptyMap(), false, param, null);
	}

	public static class BedFileColumnIndices {
		public int contigNameColumn;
		public int entryNameColumn;
		public int entryStartColumn;
		public int entryEndColumn;
		public int entryOrientationColumn;
		public int scoreColumn;
		public int blockLengthsColumn;
		public int annotationsColumn;
		public boolean autogenerateName;
	}

	public static Pair<Integer, Integer> consumeBed(
			List<@NonNull String> contigNames,
			BufferedReader reader,
			@NonNull String readerName,
			@NonNull String referenceGenomeName,
			@NonNull Map<@NonNull String, @NonNull String> transcriptToGeneNameMap,
			boolean parseScore,
			@Nullable Parameters param,
			@Nullable BedFileColumnIndices columnIndices,
			Consumer<GenomeInterval> consumer,
			boolean parallelize
		) {

		Map<String, Integer> reverseIndex = invertList(contigNames);

		final AtomicInteger lineCount = new AtomicInteger(0);
		final AtomicInteger skipped = new AtomicInteger(0);

		try(Stream<String> lines = reader.lines()) {
			Iterator<String> lineIt = lines.iterator();
			final Stream<String> remaining = StreamSupport.stream(Spliterators.spliteratorUnknownSize(
				lineIt, Spliterator.ORDERED), parallelize);

			final String line1String = lineIt.next();
			final String[] line1 = line1String.split("\t");

			final int contigNameColumn;
			final int entryNameColumn;
			final int entryStartColumn;
			final int entryEndColumn;
			final int entryOrientationColumn;
			final int scoreColumn;
			final int blockLengthsColumn;
			final int annotationsColumn;
			final boolean autogenerateName;

			final Stream<String> lines1;

			if (columnIndices != null) {
				contigNameColumn = columnIndices.contigNameColumn;
				entryNameColumn = columnIndices.entryNameColumn;
				entryStartColumn = columnIndices.entryStartColumn;
				entryEndColumn = columnIndices.entryEndColumn;
				entryOrientationColumn = columnIndices.entryOrientationColumn;
				scoreColumn = columnIndices.scoreColumn;
				blockLengthsColumn = columnIndices.blockLengthsColumn;
				autogenerateName = columnIndices.autogenerateName;
				annotationsColumn = columnIndices.annotationsColumn;
				lines1 = Stream.concat(Stream.of(line1String), remaining);
			} else if (line1[0].startsWith("#")) {
				final Parameters param1 = param == null ? new Parameters() : param;
				line1[0] = line1[0].substring(1);
				final List<String> components = Arrays.asList(line1);
				Set<String> eatenColumnNames = new HashSet<>();
				contigNameColumn = getIndexOf(components, param1.bedContigNameColumn, eatenColumnNames, false);
				entryNameColumn = getIndexOf(components, param1.bedEntryNameColumn, eatenColumnNames, true);
				entryStartColumn = getIndexOf(components, param1.bedEntryStartColumn, eatenColumnNames, false);
				entryEndColumn = getIndexOf(components, param1.bedEntryEndColumn, eatenColumnNames, false);
				entryOrientationColumn = getIndexOf(components, param1.bedEntryOrientationColumn, eatenColumnNames, true);
				scoreColumn = getIndexOf(components, param1.bedEntryScoreColumn, eatenColumnNames, true);
				autogenerateName = entryNameColumn == -1;
				blockLengthsColumn = getIndexOf(components, param1.bedBlockLengthsColumn, eatenColumnNames, true);
				annotationsColumn = -1;
				lines1 = remaining;
			} else {
				contigNameColumn = 0;
				entryNameColumn = 3;
				entryStartColumn = 1;
				entryEndColumn = 2;
				scoreColumn = 4;
				entryOrientationColumn = line1.length > 5 ? 5 : -1;
				autogenerateName = line1.length == 3;
				blockLengthsColumn = line1.length >= 11 ? 10 : -1;
				annotationsColumn = -1;
				lines1 = Stream.concat(Stream.of(line1String), remaining);
			}

			lines1.forEach(l -> {
				try {
					final int line = lineCount.incrementAndGet();
					@NonNull String[] components = l.split("\t");
					if (components.length < (parseScore ? 4 : 3)) {
						throw new ParseRTException("Missing fields");
					}
					int start = Integer.parseInt(underscorePattern.matcher(components[entryStartColumn]).replaceAll("")) - 1;
					int end = Integer.parseInt(underscorePattern.matcher(components[entryEndColumn]).replaceAll("")) - 1;
					final String name;
					if (autogenerateName) {
						name = "line_" + line;
					} else {
						name = components[entryNameColumn];
					}
					final Integer length;
					final @NonNull Optional<Boolean> strandPolarity;
					if (entryOrientationColumn != -1) {
						switch (components[entryOrientationColumn]) {
							case "+": strandPolarity = Optional.of(false); break;
							case "-": strandPolarity = Optional.of(true); break;
							default: throw new ParseRTException("Expected + or - in strand field " +
								" but found " + components[entryOrientationColumn]);
						}
						try {
							if (blockLengthsColumn != -1) {
								String[] blockLengths = quotePattern.matcher(components[blockLengthsColumn]).replaceAll("").split(",");
								int totalLength = 0;
								boolean foundEmptyBlock = false;
								for (String bl: blockLengths) {
									if (bl.equals("")) {
										if (foundEmptyBlock) {
											throw new ParseRTException("Two empty block length items");
										}
										foundEmptyBlock = true;
									} else {
										totalLength += Integer.parseInt(bl);
									}
								}
								length = totalLength;
							} else {
								length = null;
							}
						} catch (RuntimeException e) {
							throw new RuntimeException("Problem extracting block sizes from " + l, e);
						}
					} else {
						length = null;
						strandPolarity = Optional.empty();
					}
					final float score;
					if (parseScore) {
						try {
							score = Float.parseFloat(components[scoreColumn]);
						} catch (NumberFormatException e) {
							throw new ParseRTException("Could not parse score " +
								components[scoreColumn], e);
						}
					} else {
						score = 0f;
					}

					final String annotations = annotationsColumn == -1 || annotationsColumn > components.length - 1 ? null
						: components[annotationsColumn].intern();

					Integer contigIndex = reverseIndex.get(components[contigNameColumn]);
					if (contigIndex == null) {
						if (IGNORE_MISSING_CONTIGS) {
							warnMissingContigOnce(components[contigNameColumn], readerName);
							skipped.incrementAndGet();
							return;
						} else {
							throw new IllegalArgumentException("Could not find contig " + components[contigNameColumn]);
						}
					}
					GenomeInterval interval = new GenomeInterval(name.intern(), contigIndex, referenceGenomeName,
						/*contig*/ components[contigNameColumn].intern(), start, end, length, strandPolarity, score,
						transcriptToGeneNameMap.get(name), annotations);
					consumer.accept(interval);
				} catch (IllegalArgumentException | ParseRTException e) {
					throw new ParseRTException("Error parsing line: " + l + " of " + readerName, e);
				}
			});
		} finally {
			try {
				reader.close();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		return new Pair<>(lineCount.get(), skipped.get());
	}

	public BedReader(
			List<@NonNull String> contigNames,
			BufferedReader reader,
			@NonNull String readerName,
			@NonNull String referenceGenomeName,
			@Nullable BufferedReader suppInfoReader,
			@NonNull Map<@NonNull String, @NonNull String> transcriptToGeneNameMap,
			boolean parseScore,
			@Nullable Parameters param,
			@Nullable BedFileColumnIndices columnIndices) throws ParseRTException {

		this.readerName = readerName;
		bedFileIntervals = new MapOfLists<>();

		final List<@NonNull String> contigNameIgnorePatternsUC = param == null ? Collections.emptyList() :
			param.ignoreContigsContaining;

		int lineCountSupp = 0;
		for (int i = 0; i < contigNames.size(); i++) {
			final String contigName = contigNames.get(i);
			if (contigNameIgnorePatternsUC.stream().anyMatch(pattern -> contigName.toUpperCase().contains(pattern))) {
				bedFileIntervals.addAt(contigName, Collections.emptyList());
				continue;
			}
			lineCountSupp++;
			bedFileIntervals.addAt(contigName, new IntervalData<>(-1, -1,
					new GenomeInterval("", i, referenceGenomeName, contigNames.get(i), -1, -1, null, Optional.empty(), 0, null, null)));
		}

		Pair<Integer, Integer> counts = consumeBed(contigNames, reader, readerName, referenceGenomeName,
			transcriptToGeneNameMap, parseScore, param, columnIndices, interval ->
				bedFileIntervals.addAt(interval.contigName, new IntervalData<>(interval.getStart(), interval.getEnd(), interval)),
				false
			);

		List<Entry<@NonNull String, @NonNull List<@NonNull IntervalData<@NonNull GenomeInterval>>>> sortedContigs =
				bedFileIntervals.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).collect(Collectors.toList());

		//NB For this to work the contig IDs used in the test function must match
		//alphabetical order of contig names
		int entryCount = 0;
		for (Entry<String, @NonNull List<IntervalData<@NonNull GenomeInterval>>> sortedContig: sortedContigs) {
			entryCount += sortedContig.getValue().size();
			contigTrees.add(new IntervalTree<>(sortedContig.getValue()));
		}

		Assert.isFalse(entryCount != (counts.fst + lineCountSupp) - counts.snd,
				"Incorrect number of entries after BED file reading: %s vs %s", entryCount, (counts.fst + lineCountSupp));

		if (suppInfoReader == null) {
			suppInfo = Collections.emptyMap();
		} else {
			suppInfo = TSVMapReader.getMap(suppInfoReader);
		}
	}

	private static int getIndexOf(@NonNull List<String> components, String string,
			Set<String> eatenColumnNames, boolean allowMissing) {
		final int index = components.indexOf(string);
		if (!allowMissing && index == -1) {
			throw new IllegalArgumentException("Missing entry " + string);
		}
		if (index != -1 && !eatenColumnNames.add(string)) {
			throw new IllegalArgumentException("Repeated column name " + string);
		}
		if (components.lastIndexOf(string) != index) {
			throw new IllegalArgumentException("Repeated entry " + string);
		}
		return index;
	}

	private synchronized static void warnMissingContigOnce(@NonNull String name,
			@NonNull String readerName) {
		if (missingContigNames.add(name)) {
			Util.printUserMustSeeMessage("Ignoring all entries in missing contig " + name +
				" present in at least bed file " + readerName);
		}
	}

	public @Nullable String getSuppInfo(String feature) {
		return suppInfo.get(feature);
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.features.BedFeatureTester#test(uk.org.cinquin.duplex_analysis.SequenceLocation)
	 */
	@Override
	public boolean test(SequenceLocation loc) {
		return contigTrees.get(loc.contigIndex).contains(loc.position);
	}

	public static boolean anyMatch(Collection<GenomeFeatureTester> testers,
			SequenceLocation location) {
		for (GenomeFeatureTester tester: testers) {
			if (tester.test(location)) {
				return true;
			}
		}
		return false;
	}

	public SerializablePredicate<SequenceLocation> getStrandSpecificTester(final boolean negativeStrand) {
		return loc -> {
			Collection<GenomeInterval> matches = contigTrees.get(loc.contigIndex).query(loc.position).getUnprotectedData();
			if (matches.isEmpty()) {
				return false;
			}
			if (matches.size() > 1) {
				throw new IllegalArgumentException("Cannot perform strand-specific match if " +
						" multiple regions match");
			}
			return matches.iterator().next().isNegativeStrand().get() == negativeStrand;
		};
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.features.BedFeatureTester#apply(uk.org.cinquin.duplex_analysis.SequenceLocation)
	 */
	@Override
	public @NonNull Collection<@NonNull GenomeInterval> apply(SequenceLocation loc) {
		return contigTrees.get(loc.contigIndex).query(loc.position).getUnprotectedData();
	}

	public void forEach(SequenceLocation loc, Predicate<@NonNull GenomeInterval> keepGoingPredicate) {
		contigTrees.get(loc.contigIndex).forEach(loc.position, keepGoingPredicate);
	}

	public void forEach(Consumer<@NonNull GenomeInterval> action) {
		contigTrees.forEach(tree -> tree.forEach(action));
	}

	@Override
	public @NonNull Optional<Boolean> getNegativeStrand(SequenceLocation loc) {
		Handle<Boolean> positiveStrand = new Handle<>();
		contigTrees.get(loc.contigIndex).forEach(loc.position, interval -> {
				return interval.isNegativeStrand().map(b -> {
					Boolean ps = positiveStrand.get();
					if (ps == null) {
						positiveStrand.set(b);
						return true;
					} else {
						if (!ps.equals(b)) {
							//Overlapping features with opposite orientations
							//Since we do not know which should be used, return null
							positiveStrand.set(null);
							return false;
						}
						return true;
					}
				}).orElse(true);
			}
		);
		Boolean result = positiveStrand.get();
		if (result == null) {
			return Util.emptyOptional();
		} else {
			return result ? TRUE_OPTIONAL : FALSE_OPTIONAL;
		}
	}

	public static List<@NonNull String> getContigNames(String locationsFilePath)
			throws IOException {
		Set<@NonNull String> set = new HashSet<>();
		try (Stream<String> lines = Files.lines(Paths.get(locationsFilePath))) {
			lines.forEach(line -> {
				final @NonNull String[] split = line.split("\t");
				set.add(split[0]);
			});
		}
		List<@NonNull String> result = new ArrayList<>(set);
		result.sort(null);
		return result;
	}

}
