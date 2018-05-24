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

package uk.org.cinquin.mutinack.features.tests;

import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import com.jwetherell.algorithms.data_structures.IntervalData;
import com.jwetherell.algorithms.data_structures.IntervalTree;

import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.TroveSetCollector;

@SuppressWarnings("static-method")
public class GenomeIntervalTest {

	@Test
	public void test() {

		List<IntervalData<GenomeInterval>> intervalDataList = getTestIntervalDataList();

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
			final int start = (int) (Math.random() * 200);
			final int end = start + (int) (Math.random() * 200);
			GenomeInterval i1 = new GenomeInterval("Random interval", -1, "", "",
					start,
					end, null, Util.emptyOptional(), 0, null, null);

			intervalDataList.add(new IntervalData<>(i1.getStart(), i1.getEnd(), i1));
		}

		intervalDataList = new ArrayList<>(
			intervalDataList.stream().collect(TroveSetCollector.uniqueValueCollector()));

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

	@Test(expected = UnsupportedOperationException.class)
	public void testUnmodifiableQueryResultSet() {
		new IntervalTree<>(getTestIntervalDataList()).
			query(Integer.MIN_VALUE, Integer.MAX_VALUE).getData().
			add(null);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testUnmodifiableQueryResultSet1() {
		new IntervalTree<>(getTestIntervalDataList()).
			query(Integer.MIN_VALUE, Integer.MAX_VALUE).getData().
			clear();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testUnmodifiableEmptyQueryResultSet() {
		new IntervalTree<>(Collections.emptyList()).query(0).getData().
		add(null);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testUnmodifiableEmptyQueryResultSet1() {
		new IntervalTree<>(Collections.emptyList()).query(0).getData().
		clear();
	}

	@Test
	@SuppressWarnings("ReferenceEquality")
	public void testIdentityOfEmptyQueryResults() {
		//Check that IntervalTree is not creating a new empty set every time
		//a query has no result, but instead returns the same (immutable)
		//empty set
		assertTrue(new IntervalTree<>(Collections.emptyList()).query(0) == IntervalData.EMPTY);

		//Check that IntervalTree is creating a new empty set if requesting
		//a defensive copy of the result
		IntervalData<Object> queryResult = new IntervalTree<>(Collections.emptyList()).query(0);
		assertTrue(queryResult.getData() != queryResult.getUnprotectedData());
	}

	private List<IntervalData<GenomeInterval>> deepClone(List<IntervalData<GenomeInterval>> list) {
		//Ideally we would clone the Interval as well
		return list.stream().map(id -> new IntervalData<>(id.getStart(), id.getEnd(),
				new HashSet<>(id.getData()))).collect(Collectors.toList());
	}

	@SuppressWarnings("unused")
	private final SecureRandom random = new SecureRandom();

	private List<IntervalData<GenomeInterval>> getTestIntervalDataList() {
		final List<IntervalData<GenomeInterval>> intervalDataList = new ArrayList<>();

		List<String> names = IntStream.iterate(0, i -> i + 1).limit(15).mapToObj(i -> "name_" + i).
				 collect(Collectors.toList());
		for (int i = 0; i < 91; i++) {
			for (String name: names) {
				GenomeInterval i1 = new GenomeInterval(name, -1, "", "", 10 + i, 100, null, Util.emptyOptional(), 0, null, null);
				intervalDataList.add(new IntervalData<>(i1.getStart(), i1.getEnd(), i1));
				GenomeInterval i2 = new GenomeInterval(name, -1, "", "", 10, 101 - i, null, Util.emptyOptional(), 0, null, null);
				intervalDataList.add(new IntervalData<>(i2.getStart(), i2.getEnd(), i2));
			}
		}

		GenomeInterval i3 = new GenomeInterval("name_3", -1, "", "", 105, 150, null, Util.emptyOptional(), 0, null, null);

		intervalDataList.add(new IntervalData<>(i3.getStart(), i3.getEnd(), i3));

		return new ArrayList<>(
			intervalDataList.stream().collect(TroveSetCollector.uniqueValueCollector()));
	}

	private void check(IntervalTree<GenomeInterval> tree, List<IntervalData<GenomeInterval>> intervalList) {
		for (int position = -200 ; position <= 200; position ++) {
			final int position0 = position;

			Set<GenomeInterval> groundTruthSet = intervalList.stream().filter(v ->
					v.getStart() <= position0 && v.getEnd() >= position0).
					flatMap(v -> v.getData().stream()).collect(Collectors.toSet());

			Collection<GenomeInterval> data = tree.query(position0).getData();
			assertTrue(data.equals(groundTruthSet));

			data = tree.query(position0).getUnprotectedData();
			assertTrue(data.equals(groundTruthSet));

			Collection<GenomeInterval> data0 = new THashSet<>();
			tree.forEach(position0, x -> {
					Assert.assertTrue(data0.add(x));
					return true;
				}
			);
			assertTrue(data0.equals(groundTruthSet));

			/* This part of the test disabled because of long run time (but the
			test does pass if code is uncommented).
			final int position1 = random.nextInt(400) - 200;
			groundTruthSet = intervalList.stream().filter(v ->
				v.getStart() <= position1 && v.getEnd() >= position0).
				flatMap(v -> v.getData().stream()).collect(Collectors.toSet());;

			data = tree.query(position0, position1).getData();
			assertTrue(data.equals(groundTruthSet));

			data = tree.query(position0, position1).getUnprotectedData();
			assertTrue(data.equals(groundTruthSet));*/
		}
	}
}
