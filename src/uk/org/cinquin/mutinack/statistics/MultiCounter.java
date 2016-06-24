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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;
import uk.org.cinquin.mutinack.misc_util.SerializableSupplier;
public class MultiCounter<T> implements ICounterSeqLoc, Serializable, Actualizable {
	
	@JsonIgnore
	private static final long serialVersionUID = 8621583719293625759L;

	@JsonIgnore
	protected boolean on = true;
	
	@JsonIgnore
	private final @Nullable SerializableSupplier<@NonNull ICounter<T>> factory1;
	@JsonIgnore
	private final @Nullable SerializableSupplier<@NonNull ICounterSeqLoc> factory2;
	private final Map<String, Pair<SerializablePredicate<SequenceLocation>, ICounter<T>>>
		counters = new THashMap<>();
	private final Map<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>>
		seqLocCounters = new THashMap<>();
	@JsonIgnore
	private final DoubleAdderFormatter adderForTotal = new DoubleAdderFormatter();

	@JsonIgnore
	private static final @NonNull SerializablePredicate<SequenceLocation> yes = l -> true;
		
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final Comparator<? super Entry<String, Pair<Predicate<SequenceLocation>, Comparable>>>
		byKeySorter = (e, f) -> ((Comparable) e.getKey()).compareTo(f.getKey());

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final Comparator<? super Entry<String, Pair<Predicate<SequenceLocation>, Comparable>>>
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
	private final transient Comparator<? super Entry<String, Pair<Predicate<SequenceLocation>, Comparable>>> 
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
			ICounterSeqLoc counter) {
		seqLocCounters.put(name, new Pair<>(predicate, counter));
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
		for (Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc> c: seqLocCounters.values()) {
			if (c.fst.test(loc)) {
				c.snd.accept(loc, d);
			}
		}
	}
	
	public void accept(@NonNull SequenceLocation loc, @NonNull T t, double d) {
		if (!on) {
			return;
		}
		adderForTotal.add(d);
		
		for (Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc> c: seqLocCounters.values()) {
			if (c.fst.test(loc)) {
				c.snd.accept(loc, d);
			}
		}
		
		for (Pair<SerializablePredicate<SequenceLocation>, ICounter<T>> c: counters.values()) {
			if (c.fst.test(loc)) {
				c.snd.accept(t, d);
			}
		}
	}
	
	public void accept(@NonNull SequenceLocation loc, @NonNull T t) {
		if (on)
			accept(loc, t, 1d);
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
			append("\n");
		}
		for (Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>> e: seqLocCounters.entrySet().
				stream().sorted((Comparator<? super Entry<String, Pair<SerializablePredicate<SequenceLocation>, ICounterSeqLoc>>>)
						printingSorter).collect(Collectors.toList())) {
			b.append(e.getKey()).append(": ").append(e.getValue().snd.toString()).append("\n");
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
	
	@SuppressWarnings("null")
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
		Set<String> result = new HashSet<>(counters.keySet().size() + 
				seqLocCounters.keySet().size());
		result.addAll(counters.keySet());
		result.addAll(seqLocCounters.keySet());
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
		Stream.concat(counters.values().stream(), seqLocCounters.values().
			stream()).map(p -> p.snd).filter(o -> o instanceof Actualizable).
			forEach(a -> ((Actualizable) a).actualize());
	}
	
}
