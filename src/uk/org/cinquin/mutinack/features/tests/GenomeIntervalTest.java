/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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

package uk.org.cinquin.mutinack.features.tests;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.jwetherell.algorithms.data_structures.IntervalTree;
import com.jwetherell.algorithms.data_structures.IntervalTree.IntervalData;

import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.Util;

@SuppressWarnings("static-method")
public class GenomeIntervalTest {

	@Test
	public void test() {
		
		ArrayList<IntervalData<GenomeInterval>> intervalDataList = new ArrayList<>();
		List<String> names = IntStream.iterate(0, i -> i + 1).limit(15).mapToObj(i -> "name_" + i).
				collect(Collectors.toList());
		for (int i = 0; i < 91; i++) {
			for (String name: names) {
				GenomeInterval i1 = new GenomeInterval(name, "", 10 + i, 100, null, Util.emptyOptional());
				intervalDataList.add(new IntervalTree.IntervalData<>(i1.getStart(), i1.getEnd(), i1));
				GenomeInterval i2 = new GenomeInterval(name, "", 10, 101 - i, null, Util.emptyOptional());
				intervalDataList.add(new IntervalTree.IntervalData<>(i2.getStart(), i2.getEnd(), i2));
			}
		}

		GenomeInterval i3 = new GenomeInterval("name_3", "", 105, 150, null, Util.emptyOptional());
		
		intervalDataList.add(new IntervalTree.IntervalData<>(i3.getStart(), i3.getEnd(), i3));

		List<IntervalData<GenomeInterval>> clone0 = deepClone(intervalDataList);
		IntervalTree<GenomeInterval> tree1 = new IntervalTree<>(clone0);
		
		List<IntervalData<GenomeInterval>> clone1 = deepClone(intervalDataList);
		Collections.reverse(clone1);
		IntervalTree<GenomeInterval> tree2 = new IntervalTree<> (clone1);

		List<IntervalData<GenomeInterval>> clone2 = deepClone(intervalDataList);
		Collections.shuffle(clone2);
		IntervalTree<GenomeInterval> tree3 = new IntervalTree<> (clone2);
		
		//Run query multiple times for each tree to ensure that query does not end up
		//modifying contents
		for (IntervalTree<GenomeInterval> tree: Arrays.asList(tree1, tree2, tree3, tree1, tree2, tree3)) {
			check(tree, intervalDataList);
		}
		
		//Same sort of test as above, but using random intervals
		for (int i = 0; i < 10_000; i++) {
			GenomeInterval i1 = new GenomeInterval("Random interval", "", 
					(int) (Math.random() - 0.5) * 2 * 200, 
					(int) (Math.random() - 0.5) * 2 * 200, null, Util.emptyOptional());
			
			intervalDataList.add(new IntervalTree.IntervalData<>(i1.getStart(), i1.getEnd(), i1));
		}
		
		clone0 = deepClone(intervalDataList);
		tree1 = new IntervalTree<>(clone0);
		
		clone1 = deepClone(intervalDataList);
		Collections.reverse(clone1);
		tree2 = new IntervalTree<> (clone1);

		clone2 = deepClone(intervalDataList);
		Collections.shuffle(clone2);
		tree3 = new IntervalTree<> (clone2);

		for (IntervalTree<GenomeInterval> tree: Arrays.asList(tree1, tree2, tree3, tree1, tree2, tree3)) {
			check(tree, intervalDataList);
		}
	}
	
	@Test
	public void checkImmutableEmptySet() {
		//Check that IntervalTree is not creating a new empty set every time
		//a query has no result, but instead returns the (immutable) Collections
		//empty set
		assertTrue(new IntervalTree<>(Collections.emptyList()).query(0).getUnprotectedData() == 
				Collections.emptySet());

		//Check that IntervalTree is creating a new empty set if requesting
		//a defensive copy of the result
		assertTrue(new IntervalTree<>(Collections.emptyList()).query(0).getData() != 
				Collections.emptySet());
	}
	
	private List<IntervalData<GenomeInterval>> deepClone(List<IntervalData<GenomeInterval>> list) {
		//Ideally we would clone the Interval as well
		return list.stream().map(id -> new IntervalData<>(id.getStart(), id.getEnd(),
				new HashSet<>(id.getData()))).collect(Collectors.toList());
	}
	
	private void check(IntervalTree<GenomeInterval> tree, List<IntervalData<GenomeInterval>> intervalList) {
		for (int position = -200 ; position <= 200; position ++) {
			int position0 = position;
			Set<GenomeInterval> groundTruthSet = intervalList.stream().filter(v -> 
					v.getStart() <= position0 && v.getEnd() >= position0).
				flatMap(v -> v.getData().stream()).collect(Collectors.toSet());;

			Collection<GenomeInterval> data = tree.query(position).getData();
			assertTrue(data.equals(groundTruthSet));
			
			data = tree.query(position).getUnprotectedData();
			assertTrue(data.equals(groundTruthSet));			
		}
	}
}
