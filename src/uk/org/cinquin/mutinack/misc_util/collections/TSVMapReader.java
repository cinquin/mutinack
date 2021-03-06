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

package uk.org.cinquin.mutinack.misc_util.collections;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Pair;

public class TSVMapReader {
	static final Logger logger = LoggerFactory.getLogger(TSVMapReader.class);

	public static @NonNull Map<@NonNull String, @NonNull String> getMap(BufferedReader r) {
		Map<@NonNull String, Pair<Set<String>, Set<String>>> tempMap = new HashMap<>();
		try(Stream<String> lines = r.lines()) {
			lines.forEachOrdered(l -> {
				@NonNull String[] components = l.split("\t");
				List<String> suppInfo = new ArrayList<>(Arrays.asList(components).stream().
					skip(2).
					map(String::intern).
					collect(Collectors.toList()));
				Pair<Set<String>, Set<String>> entry = tempMap.get(components[0]);
				if (entry != null) {
					entry.fst.add(components[1].intern());
					entry.snd.addAll(suppInfo);
				} else {
					Set<String> suppInfoSet = new HashSet<>(suppInfo);

					Set<String> nameSet = new HashSet<>();
					nameSet.add(components[1].intern());

					tempMap.put(components[0].intern(), new Pair<>(nameSet, suppInfoSet));
				}
			});
		}

		Map<@NonNull String, @NonNull String> result = new HashMap<>();

		for (Entry<@NonNull String, Pair<Set<String>, Set<String>>> e: tempMap.entrySet()) {
			Set<String> geneNames = e.getValue().fst;
			Assert.isFalse(geneNames.isEmpty());
			final String name = geneNames.stream().collect(Collectors.joining("="));
			final Set<String> suppInfo = e.getValue().snd;
			result.put(e.getKey(), name + '\t' + (suppInfo.isEmpty() ? "" : suppInfo));
		}

		return Collections.unmodifiableMap(result);
	}
}
