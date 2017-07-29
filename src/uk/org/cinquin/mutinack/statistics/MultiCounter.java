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
package uk.org.cinquin.mutinack.statistics;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.SerializableSupplier;

@PersistenceCapable
public class MultiCounter<T> implements ICounterSeqLoc, Serializable, Actualizable {

	@JsonIgnore
	private static final long serialVersionUID = 8621583719293625759L;

	@JsonIgnore
	protected boolean on = true;

	@JsonIgnore
	private final transient @Nullable SerializableSupplier<@NonNull ICounter<T>> factory1;
	@JsonIgnore
	private final transient @Nullable SerializableSupplier<@NonNull ICounterSeqLoc> factory2;
	private final THashMap<String, Pair<SerializablePredicate<SequenceLocation>, ICounter<T>>>
		counters = new THashMap<>();
	private final THashMap<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>>
		seqLocCounters = new THashMap<>();
	@JsonIgnore
	private final DoubleAdderFormatter adderForTotal = new DoubleAdderFormatter();

	@JsonIgnore
	private static final transient @NonNull SerializablePredicate<SequenceLocation> yes = l -> true;

	@SuppressWarnings("rawtypes")
	private interface SerializableComparator extends
		Comparator<Entry<String, Pair<Predicate<SequenceLocation>, Comparable>>>, Serializable {
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final SerializableComparator
		byKeySorter = (e, f) -> ((Comparable) e.getKey()).compareTo(f.getKey());

	@SuppressWarnings({ "unchecked" })
	private static final SerializableComparator
		byValueSorter = (e, f) -> {
			int result =
					- e.getValue().snd.compareTo(f.getValue().snd);
			if (result == 0) {
				result = e.getKey().compareTo(f.getKey());
			}
			return result;
		};

	@JsonIgnore
	@SuppressWarnings("rawtypes")
	private @Final transient Comparator<? super Entry<String, Pair<Predicate<SequenceLocation>, Comparable>>>
		printingSorter;

	public MultiCounter(@Nullable SerializableSupplier<@NonNull ICounter<T>> factory1,
			@Nullable SerializableSupplier<@NonNull ICounterSeqLoc> factory2) {
		this(factory1, factory2, false);
	}

	public MultiCounter(@Nullable SerializableSupplier<@NonNull ICounter<T>> factory1,
			@Nullable SerializableSupplier<@NonNull ICounterSeqLoc> factory2, boolean sortByValue) {
		if (sortByValue) {
			printingSorter = byValueSorter;
		} else {
			printingSorter = byKeySorter;
		}
		this.factory1 = factory1;
		this.factory2 = factory2;
		addPredicate("All", yes);
	}

	public Map<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>> getSeqLocCounters() {
		return seqLocCounters;
	}

	public void addPredicate(String name, @NonNull
			SerializablePredicate<SequenceLocation> predicate) {
		if (factory1 != null) {
			counters.put(name, new Pair<>(predicate, factory1.get()));
		}
		if (factory2 != null) {
			seqLocCounters.put(name, new Pair<>(predicate, factory2.get()));
		}
	}

	public void addPredicate(String name,
			@NonNull SerializablePredicate<SequenceLocation> predicate,
			@NonNull ICounterSeqLoc counter) {
		if (seqLocCounters.put(name, new Pair<>(predicate, counter)) != null) {
			throw new IllegalArgumentException("Counter with name " + name + " already exists");
		}
	}

	@Override
	public void accept(@NonNull SequenceLocation loc) {
		if (on)
			accept(loc, 1d);
	}

	@Override
	public void accept(@NonNull SequenceLocation loc, double d) {
		if (!on) {
			return;
		}
		adderForTotal.add(d);
		seqLocCounters.forEachValue(c -> {
			if (c.fst.test(loc)) {
				c.snd.accept(loc, d);
			}
			return true;
		});
	}

	public void accept(@NonNull SequenceLocation loc, @NonNull T t, double d) {
		if (!on) {
			return;
		}
		adderForTotal.add(d);

		seqLocCounters.forEachValue(c -> {
			if (c.fst.test(loc)) {
				c.snd.accept(loc, d);
			}
			return true;
		});

		counters.forEachValue(c -> {
			if (c.fst.test(loc)) {
				c.snd.accept(t, d);
			}
			return true;
		});
	}

	public void accept(@NonNull SequenceLocation loc, @NonNull T t) {
		if (on)
			accept(loc, t, 1d);
	}

	public void accept(@NonNull SequenceLocation loc, @NonNull Iterable<@NonNull T> t) {
		if (on) {
			t.forEach(e -> accept(loc, e, 1d));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounter<T>>> e: counters.entrySet().
				stream().sorted((Comparator<? super Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounter<T>>>>)
						printingSorter).collect(Collectors.toList())) {
			b.append(e.getKey()).append(": ").append(e.getValue().snd.toString()).
			append("; total: ").append(DoubleAdderFormatter.formatDouble(e.getValue().snd.sum())).
			append('\n');
		}
		for (Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>> e: seqLocCounters.entrySet().
				stream().sorted((Comparator<? super Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>>>)
						printingSorter).collect(Collectors.toList())) {
			b.append(e.getKey()).append(": ").append(e.getValue().snd.toString()).append('\n');
			/*b.append(e.getKey()).append(": total ").append(e.getValue().snd.totalSum()).
			append("; ").append(e.getValue().snd.toString()).append("\n");*/
		}
		return b.toString();
	}

	public long sum() {
		return (long) adderForTotal.sum();
	}

	@Override
	public double totalSum() {
		return adderForTotal.sum();
	}

	@Override
	public void accept(@NonNull SequenceLocation loc, long l) {
		if (on)
			accept(loc, (double) l);
	}

	public void acceptSkip0(@NonNull SequenceLocation loc, long l) {
		if (l == 0) {
			return;
		}
		if (on)
			accept(loc, (double) l);
	}

	public double getSum(String predicateName) {
		if (counters.containsKey(predicateName)) {
			return counters.get(predicateName).getSnd().sum();
		} else if (seqLocCounters.containsKey(predicateName)) {
			return seqLocCounters.get(predicateName).getSnd().totalSum();
		} else {
			return Double.NaN;
		}
	}

	public Set<String> getCounterNames() {
		Set<String> result = new TreeSet<>();
		result.addAll(counters.keySet());
		seqLocCounters.forEachEntry((k , v) -> {
			if (!(v.snd instanceof CounterWithBedFeatureBreakdown)) {
				result.add(k);
			}
			return true;
		});
		return result;
	}

	@Override
	public @NonNull Map<Object, @NonNull Object> getCounts() {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public void turnOff() {
		on = false;
	}

	@Override
	public void turnOn() {
		on = true;
	}

	@Override
	public boolean isOn() {
		return on;
	}

	@Override
	public int compareTo(Object o) {
		return Double.compare(totalSum(), ((ICounterSeqLoc) o).totalSum());
	}

	@Override
	public boolean equals(Object o) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public int hashCode() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public void actualize() {
		actualizeValues(counters.values());
		actualizeValues(seqLocCounters.values());
	}

	@SuppressWarnings("unchecked")
	private static void actualizeValues(@SuppressWarnings("rawtypes") Collection values) {
		values.forEach(o -> {
			Pair<?, ?> p = (Pair<?, ?>) o;
			if (p.snd instanceof Actualizable) {
				((Actualizable) p.snd).actualize();
			}
		});
	}

}
