package uk.org.cinquin.mutinack.tests;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.CustomEnumToEnumMap;
import uk.org.cinquin.mutinack.misc_util.collections.DuplexAssayToQualityMap;
import uk.org.cinquin.mutinack.misc_util.collections.PositionAssayToQualityMap;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.qualities.DetailedDuplexQualities;
import uk.org.cinquin.mutinack.qualities.DetailedPositionQualities;
import uk.org.cinquin.mutinack.qualities.Quality;

public class DuplexAssayToQualityMapTest {

	private static<K, V> void iterateEqualMaps(Map<K, V> toIterate, Map<K, V> reference0) {
		Map<K, V> reference = Util.serializeAndDeserialize(reference0);//Avoid modifying the
		//reference map

		reference.forEach((k, v) -> {
			Assert.isTrue(v == toIterate.get(k));
		});

		toIterate.forEach((k, v) -> {
			V other = reference.remove(k);
			if (other != v) {
				throw new AssertionFailedException();
			}
		});

		if (!reference.isEmpty()) {
			throw new AssertionFailedException();
		}
	}

	private static<K, V> void iterateEqualMapsAndCheckSerialization(Map<K, V> toIterate, Map<K, V> reference) {
		iterateEqualMaps(toIterate, reference);
		iterateEqualMaps(Util.serializeAndDeserialize(Util.serializeAndDeserialize(toIterate)), reference);
	}

	private static<K, V> void emptyMap(Map<K, V> map) {
		Map<K, V> collect = new HashMap<>();
		@SuppressWarnings("unchecked")
		Map<K, V> clone = (Map<K, V>) ((DuplexAssayToQualityMap) map).clone();

		int size = map.size();
		Iterator<Entry<K, V>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			size--;
			Entry<K, V> e = it.next();
			collect.put(e.getKey(), e.getValue());
			it.remove();
			Assert.isTrue(map.size() == size);
		}

		if (!map.isEmpty()) {
			throw new AssertionFailedException();
		}

		assertEqual(clone, collect);
	}

	private static void assertEqual(Map<?,?> map1, Map<?,?> map2) {
		Assert.isTrue(map1.equals(map2) && map2.equals(map1));
	}

	private static void assertNotEqual(Map<?,?> map1, Map<?,?> map2) {
		Assert.isFalse(map1.equals(map2) || map2.equals(map1));
	}

	private static<K extends Enum<K>> K getRandomValue(Class<K> clazz) {
		K[] values = CustomEnumToEnumMap.getUniverse(clazz);
		return values[(int) Math.round(Math.random() * (values.length - 1))];
	}

	@SuppressWarnings({"static-method", "CollectionIncompatibleType", "unlikely-arg-type"})
	@Test
	public void test() {

		{
			Map<PositionAssay, Quality> referenceMap = new HashMap<>();
			Map<PositionAssay, Quality> referenceMap2 = new HashMap<>();
			PositionAssayToQualityMap testMap = new PositionAssayToQualityMap();
			DetailedPositionQualities dq = new DetailedPositionQualities();


			for (int i = 0; i < 10_000; i++) {
				PositionAssay assay = getRandomValue(PositionAssay.class);
				if (assay.isMaxGroup()) {
					continue;
				}
				Quality q = getRandomValue(Quality.class);

				Assert.isTrue(referenceMap.containsKey(assay) == testMap.containsKey(assay));
				Assert.isTrue(referenceMap.containsValue(q) == testMap.containsValue(q));
				Assert.isFalse(testMap.containsKey(q));
				Assert.isFalse(testMap.containsValue(assay));

				Assert.isTrue(referenceMap.put(assay, q) == testMap.put(assay, q));

				Quality previousMin = dq.getMin();
				Quality previousMax = dq.getMax();
				dq.add(assay, q);

				Quality newMin = Quality.nullableMin(previousMin, q);

				Assert.isTrue(dq.getMin() == newMin);
				Assert.isTrue(dq.getMax() == previousMax);
				Quality previousQ = referenceMap2.get(assay);
				Quality newQ = Quality.nullableMin(previousQ, q);
				referenceMap2.put(assay, newQ);

				Assert.isTrue(dq.getMin() == Quality.min(q, Quality.GOOD));

				dq.setMin(Quality.GOOD);

				referenceMap2.forEach((k, v) -> {
					Quality v2 = dq.getQuality(k);
					Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
				});
				dq.forEach((k, v) -> {
					Quality v2 = referenceMap2.get(k);
					Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
				});
				dq.getQualityMap().forEach((k, v) -> {
					Quality v2 = referenceMap2.get(k);
					Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
				});

				Assert.isFalse(testMap.isEmpty());
				assertEqual(testMap, referenceMap);
				assertEqual(testMap, testMap);
				Assert.isTrue(testMap.size() == referenceMap.size());
				iterateEqualMapsAndCheckSerialization(testMap, referenceMap);
			}
		}

		Map<DuplexAssay, Quality> referenceMap = new HashMap<>();
		Map<DuplexAssay, Quality> referenceMap2 = new HashMap<>();

		DuplexAssayToQualityMap testMap = new DuplexAssayToQualityMap();

		Assert.isTrue(testMap.isEmpty());

		DetailedDuplexQualities dq = new DetailedDuplexQualities();
		dq.add(DuplexAssay.CONSENSUS_Q0, Quality.DUBIOUS);
		Assert.isTrue(dq.getQuality(DuplexAssay.CONSENSUS_Q0) == Quality.DUBIOUS);
		dq.add(DuplexAssay.CONSENSUS_THRESHOLDS_1, Quality.POOR);
		Assert.isTrue(dq.getQuality(DuplexAssay.CONSENSUS_THRESHOLDS_1) == Quality.POOR);
		Assert.isTrue(dq.getQuality(DuplexAssay.CONSENSUS_Q0) == Quality.DUBIOUS);

		dq.addUnique(DuplexAssay.TOTAL_N_READS_Q2, Quality.DUBIOUS);
		Assert.isTrue(dq.getMax() == null);

		DetailedPositionQualities dq2 = new DetailedPositionQualities();
		dq2.addUnique(PositionAssay.MAX_Q_FOR_ALL_DUPLEXES, Quality.POOR);
		Assert.isTrue(dq2.getMax() == Quality.POOR);
		dq2.reset();
		dq2.addUnique(PositionAssay.N_Q1_DUPLEXES, Quality.DUBIOUS);
		Assert.isTrue(dq2.getMax() == Quality.DUBIOUS);
		dq2.reset();
		dq2.addUnique(PositionAssay.MAX_DPLX_Q_IGNORING_DISAG, Quality.GOOD);
		Assert.isTrue(dq2.getMax() == null);
		dq2.reset();

		dq.reset();
		dq.setMin(Quality.GOOD);
		dq.setMax(Quality.GOOD);

		for (int i = 0; i < 10_000; i++) {
			DuplexAssay assay = getRandomValue(DuplexAssay.class);
			Quality q = getRandomValue(Quality.class);

			Assert.isTrue(referenceMap.containsKey(assay) == testMap.containsKey(assay));
			Assert.isTrue(referenceMap.containsValue(q) == testMap.containsValue(q));
			Assert.isFalse(testMap.containsKey(q));
			Assert.isFalse(testMap.containsValue(assay));

			Assert.isTrue(referenceMap.put(assay, q) == testMap.put(assay, q));

			Quality previousQ = referenceMap2.get(assay);
			Quality newQ = Quality.nullableMin(previousQ, q);
			Assert.isTrue(dq.getMin() == Quality.GOOD);
			Assert.isTrue(dq.getMax() == Quality.GOOD);
			referenceMap2.put(assay, newQ);

			dq.add(assay, q);
			Assert.isTrue(dq.getMin() == Quality.min(q, Quality.GOOD));
			Assert.isTrue(dq.getMax() == Quality.GOOD);

			dq.setMin(Quality.GOOD);

			referenceMap2.forEach((k, v) -> {
				Quality v2 = dq.getQuality(k);
				Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
			});
			dq.forEach((k, v) -> {
				Quality v2 = referenceMap2.get(k);
				Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
			});
			dq.getQualityMap().forEach((k, v) -> {
				Quality v2 = referenceMap2.get(k);
				Assert.isTrue(v2 == v, "Expected " + k + "=" + v + " but got " + v2);
			});

			Assert.isFalse(testMap.isEmpty());
			assertEqual(testMap, referenceMap);
			assertEqual(testMap, testMap);
			iterateEqualMapsAndCheckSerialization(testMap, referenceMap);
			Assert.isTrue(testMap.size() == referenceMap.size());
		}

		emptyMap(testMap);

		testMap = Util.serializeAndDeserialize(testMap);

		referenceMap.clear();
		testMap.clear();
		Assert.isTrue(testMap.isEmpty());
		assertEqual(testMap, referenceMap);

		testMap.put(getRandomValue(DuplexAssay.class), getRandomValue(Quality.class));
		assertNotEqual(testMap, referenceMap);

		referenceMap.clear();
		testMap.clear();

		referenceMap.put(getRandomValue(DuplexAssay.class), getRandomValue(Quality.class));
		assertNotEqual(testMap, referenceMap);
	}

}
