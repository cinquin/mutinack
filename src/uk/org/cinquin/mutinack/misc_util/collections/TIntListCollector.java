package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.stream.Collector;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

public class TIntListCollector {

	public static<T> Collector<Integer, TIntList, TIntList> tIntListCollector() {
		return Collector.of(
				TIntArrayList::new,
				(list, e) -> list.add(e),
				(list1, list2) -> { 
					list1.addAll(list2);
					return list1;
				},
				list -> list
				);
	}
}
