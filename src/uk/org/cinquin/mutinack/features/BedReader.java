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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jwetherell.algorithms.data_structures.IntervalTree;
import com.jwetherell.algorithms.data_structures.IntervalTree.IntervalData;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.FileCache;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.MapOfLists;
import uk.org.cinquin.mutinack.misc_util.collections.TSVMapReader;

public class BedReader implements GenomeFeatureTester, Serializable {

	private static final long serialVersionUID = 7826378727266972258L;
	private static final Pattern underscorePattern = Pattern.compile("_");
	private static final Pattern quotePattern = Pattern.compile("\"");

	@JsonIgnore
	public final MapOfLists<String, IntervalTree.IntervalData<GenomeInterval>> bedFileIntervals;
	@JsonIgnore
	private final List<IntervalTree<GenomeInterval>> contigTrees = new ArrayList<>();
	@JsonIgnore
	private final @NonNull Map<@NonNull String, @NonNull String> suppInfo;
	private final String readerName;
		
	@Override
	public String toString() {
		return "Tester for BED file at " + readerName;
	}
	
	@SuppressWarnings("resource")
	public static BedReader getCachedBedFileReader(String path0, String cacheExtension,
			Map<Integer, @NonNull String> indexContigNameMap, String readerName, boolean parseScore) {
		return FileCache.getCached(path0, cacheExtension, path -> {
			try {
				return new BedReader(indexContigNameMap, 
					new BufferedReader(new FileReader(new File(path))), readerName, null, false);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
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
	
	@SuppressWarnings("null")
	public BedReader(Map<Integer, @NonNull String> indexContigNameMap, BufferedReader reader,
			String readerName, BufferedReader suppInfoReader, boolean parseScore) throws ParseRTException {
		
		Map<String, Integer> reverseIndex = invertMap(indexContigNameMap);
		
		this.readerName = readerName;
		bedFileIntervals = new MapOfLists<>();
		
		int entryCount = 0;
		final AtomicInteger lineCount = new AtomicInteger(0);

		for (Entry<Integer, @NonNull String> s: indexContigNameMap.entrySet()) {
			lineCount.incrementAndGet();
			bedFileIntervals.addAt(s.getValue(), new IntervalTree.IntervalData<>(-1, -1, 
					new GenomeInterval("", s.getKey(), s.getValue(), -1, -1, null, Optional.empty(), 0)));
		}
		
		try(Stream<String> lines = reader.lines()) {
			lines.forEachOrdered(l -> {
				try {
					final int line = lineCount.incrementAndGet();
					@NonNull String[] components = l.split("\t");
					if (components.length < 3) {
						throw new ParseRTException("Missing fields");
					}
					final boolean autogenerateName = components.length == 3;
					int start = Integer.parseInt(underscorePattern.matcher(components[1]).replaceAll("")) - 1;
					int end = Integer.parseInt(underscorePattern.matcher(components[2]).replaceAll("")) - 1;
					final String name;
					if (autogenerateName) {
						name = "line_" + line;
					} else {
						name = components[3];
					}
					final Integer length;
					final @NonNull Optional<Boolean> strandPolarity;
					if (components.length >= 6) {
						switch (components[5]) {
							case "+": strandPolarity = Optional.of(false); break;
							case "-": strandPolarity = Optional.of(true); break;
							default: throw new ParseRTException("Expected + or - in strand field " +
									" but found " + components[5]);
						}
						try {
							if (components.length >= 11) {
								String[] blockLengths = quotePattern.matcher(components[10]).replaceAll("").split(",");
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
							score = Float.parseFloat(components[4]);
						} catch (NumberFormatException e) {
							throw new ParseRTException("Could not parse score " +
								components[4]);
						}
					} else {
						score = 0f;
					}

					Integer contigIndex = reverseIndex.get(components[0]);
					if (contigIndex == null) {
						throw new IllegalArgumentException("Could not find contig " + components[0]);
					}
					GenomeInterval interval = new GenomeInterval(name, contigIndex, 
							/*contig*/ components[0], start, end, length, strandPolarity, score);
					bedFileIntervals.addAt(interval.contigName, new IntervalTree.IntervalData<>(start, end, interval));
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

		List<Entry<String, List<IntervalData<GenomeInterval>>>> sortedContigs = 
				bedFileIntervals.entrySet().stream().sorted((a, b) ->
							a.getKey().compareTo(b.getKey())).collect(Collectors.toList());

		//NB For this to work the contig IDs used in the test function must match
		//alphabetical order of contig names
		for (Entry<String, List<IntervalData<GenomeInterval>>> sortedContig: sortedContigs) {
			entryCount += sortedContig.getValue().size();
			contigTrees.add(new IntervalTree<>(sortedContig.getValue()));
		}
		
		Assert.isFalse(entryCount != lineCount.get(),
				"Incorrect number of entries after BED file reading: %s vs %s", entryCount, lineCount.get());
		
		if (suppInfoReader == null) {
			suppInfo = Collections.emptyMap();
		} else {
			suppInfo = TSVMapReader.getMap(suppInfoReader);
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
		return !contigTrees.get(loc.contigIndex).query(loc.position).getUnprotectedData().isEmpty();
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
	public Collection<GenomeInterval> apply(SequenceLocation loc) {
		return contigTrees.get(loc.contigIndex).query(loc.position).getUnprotectedData();
	}

	@Override
	public @NonNull Optional<Boolean> getNegativeStrand(SequenceLocation loc) {
		Collection<GenomeInterval> matches = contigTrees.get(loc.contigIndex).
				query(loc.position).getUnprotectedData();
		if (matches.isEmpty()) {
			return Util.emptyOptional();
		}
		if (matches.size() > 1) {
			//Do some sanity checking
			boolean r1 = matches.iterator().next().isNegativeStrand().get();
			Iterator<GenomeInterval> iterator = matches.iterator();
			while (iterator.hasNext()) {
				if (iterator.next().isNegativeStrand().get() != r1) {
					//Overlapping features with opposite orientations
					//Since we do not know which should be used, return null
					return Util.emptyOptional();
				}
			}
		}
		return matches.iterator().next().isNegativeStrand();
	}

}
