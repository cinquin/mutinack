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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.TIntCollection;
import gnu.trove.function.TIntFunction;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.procedure.TIntProcedure;
import gnu.trove.procedure.TObjectIntProcedure;
import gnu.trove.procedure.TObjectProcedure;

public final class SingletonObjectIntMap<T> implements TObjectIntMap<T> {

	final @NonNull T obj;
	final int value;
	
	public SingletonObjectIntMap(@NonNull T initialConcurringRead,
			int initialLigationSiteD) {
		obj = initialConcurringRead;
		value = initialLigationSiteD;
	}

	@Override
	public int adjustOrPutValue(T arg0, int arg1, int arg2) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean adjustValue(T arg0, int arg1) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public void clear() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean containsKey(Object arg0) {
		return obj.equals(arg0);
	}

	@Override
	public boolean containsValue(int arg0) {
		return value == arg0;
	}

	@Override
	public boolean forEachEntry(TObjectIntProcedure<? super T> arg0) {
		return arg0.execute(obj, value);
	}

	@Override
	public boolean forEachKey(TObjectProcedure<? super T> arg0) {
		return arg0.execute(obj);
	}

	@Override
	public boolean forEachValue(TIntProcedure arg0) {
		return arg0.execute(value);
	}

	public final static int NO_ENTRY_VALUE = -1234567;

	@Override
	public int get(Object arg0) {
		if (arg0.equals(obj)) {
			return value;
		} else {
			return NO_ENTRY_VALUE;
		}
	}

	@Override
	public int getNoEntryValue() {
		return NO_ENTRY_VALUE;
	}

	@Override
	public boolean increment(T arg0) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public TObjectIntIterator<T> iterator() {
		TObjectIntIterator<T> result = 
				new TObjectIntIterator<T>() {
			
			boolean done = false; 

			@Override
			public boolean hasNext() {
				return !done;
			}

			@Override
			public void advance() {
				if (done) {
					throw new IllegalStateException();
				} else {
					done = true;
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public T key() {
				return obj;
			}

			@Override
			public int setValue(int arg0) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int value() {
				return value;
			}
		};
		return result;
	}

	@Override
	public Set<T> keySet() {
		return Collections.singleton(obj);
	}

	@Override
	public Object[] keys() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public T[] keys(T[] arg0) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public int put(T arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends T, ? extends Integer> arg0) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void putAll(TObjectIntMap<? extends T> arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int putIfAbsent(T arg0, int arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int remove(Object arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainEntries(TObjectIntProcedure<? super T> arg0) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public void transformValues(TIntFunction arg0) {
		throw new RuntimeException("Unimplemented");			
	}

	@Override
	public TIntCollection valueCollection() {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public int[] values() {
		return new int [] {value};
	}

	@Override
	public int[] values(int[] arg0) {
		throw new RuntimeException("Unimplemented");
	}
}
