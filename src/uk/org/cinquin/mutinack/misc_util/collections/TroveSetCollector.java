package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.Set;
import java.util.stream.Collector;

import gnu.trove.set.hash.THashSet;

public class TroveSetCollector {
	public static<T> Collector<T, Set<T>, Set<T>> uniqueValueCollector() {
	    return Collector.of(
	            THashSet::new,
	            (set, e) -> set.add(e),
	            (set1, set2) -> { 
	            	set1.addAll(set2);
	            	return set1;
	            	},
	            set -> set
	    );
	}
}
