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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.SerializableFunction;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * Count occurrences of different instances of objects of type T, based on objects' equal method.
 * If T is a List, the counts are broken down following the indices defined in the list.
 * @author olivier
 *
 * @param <T>
 */
public class Counter<T> implements ICounter<T>, Serializable, Actualizable {
	private static final long serialVersionUID = -8737720765068575377L;
	@JsonIgnore
	protected boolean on = true;
	@JsonIgnore
	protected final boolean sortByValue;
	@JsonUnwrapped
	private final @NonNull Map<Object, @NonNull Object> map = new THashMap<>();
	@JsonIgnore
	private boolean isMultidimensionalCounter = false;
	@JsonIgnore
	transient protected final MutinackGroup groupSettings;
	@JsonIgnore
	transient private List<SerializableFunction<Object, Object>> nameProcessors;

	@SuppressWarnings("unchecked")
	private static final Comparator<? super Map.Entry<Object, Object>>
		byKeySorter = (e, f) -> ((Comparable<Object>) e.getKey()).compareTo(f.getKey());

	@SuppressWarnings("unchecked")
	private static final Comparator<? super Map.Entry<Object, Object>>
		byValueSorter = (e, f) -> {
			int result =
					- ((Comparable<Object>) e.getValue()).compareTo(f.getValue());
			if (result == 0) {
				result = ((Comparable<Object>) e.getKey()).compareTo(f.getKey());
			}
			return result;
		};

	@JsonIgnore
	private final transient Comparator<? super Map.Entry<Object,Object>> printingSorter;

	public Counter(boolean sortByValue, MutinackGroup groupSettings) {
		this.sortByValue = sortByValue;
		if (sortByValue) {
			printingSorter = byValueSorter;
		} else {
			printingSorter = byKeySorter;
		}
		this.groupSettings = groupSettings;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void setKeyNamePrintingProcessor(List<SerializableFunction<Object, Object>> l) {
		nameProcessors = l;
		for (Object v: map.values()) {
			if (v instanceof Counter) {
				((Counter) v).setKeyNamePrintingProcessor(
						new ArrayList<>(l.subList(1, l.size())));
			}
		}
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.ICounter#accept(T)
	 */
	@Override
	public void accept(@NonNull T t) {
		if (on)
			accept(t, 1d);
	}

	void acceptVarArgs(long n, @NonNull Object ... indices) {
		if (on)
			accept(Arrays.asList(indices), n);
	}

	void acceptVarArgs(double d, @NonNull Object ... indices) {
		if (on)
			accept(Arrays.asList(indices), d);
	}

	@Override
	public void accept(@NonNull Object t, long l) {
		if (on)
			accept(t, (double) l);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void accept(@NonNull Object t, double d, int offset) {
		@NonNull Object index = t;
		boolean terminal = true;
		final List<@NonNull Object> list;
		if (t instanceof List) {
			list = (List<@NonNull Object>) t;
			index = list.get(offset);
			terminal = offset == list.size() - 1;
		} else {
			list = null;
		}
		Object preExistingValue = map.get(index);
		if (preExistingValue == null) {
			synchronized (map) {
				preExistingValue = map.get(index);
				if (preExistingValue == null) {
					if (terminal) {
						preExistingValue = new DoubleAdderFormatter();
					} else {
						preExistingValue = new Counter<>(sortByValue, groupSettings);
						((Counter) preExistingValue).setKeyNamePrintingProcessor(nameProcessors.subList(1, nameProcessors.size()));
						isMultidimensionalCounter = true;
					}
					map.put(index, Util.nonNullify(preExistingValue));
				}
			}
		}
		if (terminal) {
			((DoubleAdderFormatter) preExistingValue).add(d);
		} else {
			((Counter<Object>) preExistingValue).accept(Objects.requireNonNull(list), d, offset + 1);
		}
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.ICounter#accept(T, long)
	 */
	@Override
	public void accept(@NonNull Object t, double d) {
		if (!on)
				return;
		accept(t, d, 0);
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.ICounter#sum()
	 */
	@Override
	public double sum() {
		double result = 0;
		for (Object laf: map.values()) {
			if (laf instanceof DoubleAdderFormatter) {
				result += ((DoubleAdderFormatter) laf).sum();
			} else if (laf instanceof Counter<?>) {
				result += ((Counter<?>) laf).sum();
			} else
				throw new AssertionFailedException();
		}
		return result;
	}

	@Override
	public @NonNull Map<Object, @NonNull Object> getCounts() {
		return map;
	}

	/* (non-Javadoc)
	 * @see uk.org.cinquin.duplex_analysis.ICounter#toString()
	 */
	String toString(String linePrefix) {
		if (!isMultidimensionalCounter) {
			if (map.isEmpty() || !(map.keySet().iterator().next() instanceof Comparable)) {
				return linePrefix + map.toString();
			} else {
				return linePrefix + '{' + map.entrySet().stream().sorted(printingSorter).
						map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")) + '}';
			}
		}
		StringBuilder result = new StringBuilder();
		Handle<Boolean> first = new Handle<>(Boolean.TRUE);
		if (linePrefix.equals("")) {
			double sum = map.values().stream().mapToDouble(o -> {
				if (o instanceof DoubleAdderFormatter) {
					return ((DoubleAdderFormatter) o).sum();
				} else
					return ((ICounter<?>) o).sum();
			}).sum();
			result.append(DoubleAdderFormatter.nf.get().format(sum));
			result.append('\n');
		}
		map.entrySet().stream().sorted(printingSorter).forEach( e -> {
			if (!first.get()) {
				result.append('\n');
			} else {
				first.set(Boolean.FALSE) ;
			}
			result.append(linePrefix);
			result.append(nameProcessors == null || nameProcessors.get(0) == null ?
					e.getKey().toString()
					: nameProcessors.get(0).apply(e.getKey()));
			result.append(": ");
			Object val = e.getValue();
			if (val instanceof DoubleAdderFormatter) {
				result.append(val);
			} else {
				result.append(DoubleAdderFormatter.nf.get().format(((Counter<?>) val).sum())).append('\n');
				result.append(((Counter<?>) val).toString(linePrefix + "  "));
			}
		});
		return result.toString();
	}

	@Override
	public String toString() {
		return toString("");
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
	public int compareTo(ICounter<T> o) {
		return Double.compare(sum(), o.sum());
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
		//TODO Finish this
		map.values().stream().filter(o -> o instanceof Actualizable).
			forEach(a -> ((Actualizable) a).actualize());
	}
}
