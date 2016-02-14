package uk.org.cinquin.mutinack.misc_util.collections;

import java.io.BufferedReader;
import java.util.ArrayList;
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

	@SuppressWarnings("null")
	public static Map<@NonNull String, @NonNull String> getMap(BufferedReader r) {
		Map<String, Pair<Set<String>, Set<String>>> tempMap = new HashMap<>();
		try(Stream<String> lines = r.lines()) {
			lines.forEachOrdered(l -> {
				@NonNull String[] components = l.split("\t");
				List<String> suppInfo = new ArrayList<>();
				for (int i = 2; i < components.length; i++) {
					suppInfo.add(components[i]);
				}
				Pair<Set<String>, Set<String>> entry = tempMap.get(components[0]);
				if (entry != null) {
					entry.fst.add(components[1]);
					entry.snd.addAll(suppInfo);
				} else {
					Set<String> suppInfoSet = new HashSet<>();
					suppInfoSet.addAll(suppInfo);
					
					Set<String> nameSet = new HashSet<>();
					nameSet.add(components[1]);
					
					tempMap.put(components[0], new Pair<>(nameSet, suppInfoSet));
				}
			});
		}
		
		Map<String, String> result = new HashMap<>();
		
		for (Entry<String, Pair<Set<String>, Set<String>>> e: tempMap.entrySet()) {
			Set<String> geneNames = e.getValue().fst;
			final String name;
			Assert.isFalse(geneNames.size() == 0);
			name = geneNames.stream().collect(Collectors.joining("="));
			result.put(e.getKey(), name + "\t" + e.getValue().snd);
		}
		
		return result;
	}
}
