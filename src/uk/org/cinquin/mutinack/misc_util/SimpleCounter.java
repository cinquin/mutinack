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
package uk.org.cinquin.mutinack.misc_util;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;

/**
 * Not thread-safe.
 * @author olivier
 *
 * @param <T>
 */
public final class SimpleCounter<T> {
	private final @NonNull Map<T, SettableInteger> map = new HashMap<>();

	public void put (T t) {
		put(t, 1);
	}

	public void removeAll (T t) {
		SettableInteger v = map.get(t);
		if (v == null) {
			throw new IllegalArgumentException();
		}
		if (v.addAndGet(-1) == 0) {
			map.remove(t);
		}
	}

	public int get (T t) {
		SettableInteger v = map.get(t);
		return v == null ? 0 : v.get();
	}

	public @NonNull Map<T, SettableInteger> getMap() {
		return map;
	}

	public void clear() {
		map.clear();
	}

	public Set<Entry<T, SettableInteger>> entrySet() {
		return map.entrySet();
	}

	public void put(T t, int value) {
		SettableInteger v = map.computeIfAbsent(t, k -> new SettableInteger(0));
		v.addAndGet(value);
	}

	private static final byte[] BASES = {'A', 'C', 'G', 'N', 'T', 'a', 'c', 'g', 'n', 't'};
	static {
		for (int i = 0; i < BASES.length; i++) {
			BASES[i] = (byte) (BASES[i] - 'A');
		}
	}

	private static final int BASES_SPAN = 't' - 'A' + 1;

	public static byte @NonNull[] getBarcodeConsensus(Stream<byte[]> records,
			int barcodeLength) {
		//Use hand-implemented 2D array instead of Java multidimensional array
		//to minimize number of objects created
		final int[] counts = new int[barcodeLength * BASES_SPAN];

		records.forEach(barcode -> {
			for (int i = 0; i < barcodeLength; i++) {
				final byte baseAti = barcode[i];
				if (baseAti != 'N') {
					counts[i*BASES_SPAN + (baseAti - 'A')]++;
				}
			}
		});

		final byte[] consensus = new byte [barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			@SuppressWarnings("unused")
			byte maxByte = 'Z', runnerUp = 'Z';
			int maxCount = 0, runnerUpCount = 0;
			final int offset = i*BASES_SPAN;
			for (int baseIndex = 0; baseIndex < BASES.length; baseIndex++) {
				final byte b = BASES[baseIndex];
				final int count = counts[offset + b];
				if (count > maxCount) {
					runnerUp = maxByte;
					runnerUpCount = maxCount;
					maxByte = b;
					maxCount = count;
				} else if (count == maxCount) {
					runnerUp = b;
					runnerUpCount = count;
				}
			}

			if (runnerUpCount == maxCount) {
				consensus[i] = 'N';
			} else {
				consensus[i] = (byte) (maxByte + 'A');
			}
		}
		return Util.getInternedVB(consensus);
	}

}
