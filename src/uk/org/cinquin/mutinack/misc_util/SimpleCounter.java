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

import org.eclipse.jdt.annotation.NonNull;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;

import java.util.*;
import java.util.Map.Entry;

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
		SettableInteger v = map.get(t);
		if (v == null) {
			v = new SettableInteger(0);
			map.put(t, v);
		}
		v.addAndGet(value);
	}
	
	public static byte @NonNull[] getBarcodeConsensus(@NonNull List<@NonNull ExtendedSAMRecord> records,
			int barcodeLength) {
		final List<SimpleCounter<Byte>> counts = new ArrayList<>(barcodeLength);
		for (int i = 0; i < barcodeLength; i++) {
			counts.add(new SimpleCounter<>());
		}
		for (ExtendedSAMRecord r: records) {
			byte[] barcode = r.variableBarcode;
			for (int i = 0; i < barcodeLength; i++) {
				if (barcode[i] != 'N') {
					counts.get(i).put(barcode[i]);
				}
			}
		}
		
		final byte[] consensus = new byte [barcodeLength];
		for (int i = 0; i < barcodeLength; i++) {
			@SuppressWarnings("unused")
			byte maxByte = 'Z', runnerUp = 'Z';
			int maxByteCount = 0, runnerUpCount = 0;
			for (Entry<Byte, SettableInteger> entry: counts.get(i).entrySet()) {
				SettableInteger v = entry.getValue();
				int count = v.get();
				if (count > maxByteCount) {
					runnerUp = maxByte;
					runnerUpCount = maxByteCount;
					maxByte = entry.getKey();
					maxByteCount = count;
				} else if (count == maxByteCount) {
					runnerUp = entry.getKey();
					runnerUpCount = count;
				}
			}

			if (runnerUpCount == maxByteCount) {
				consensus[i] = 'N';
			} else {
				consensus[i] = maxByte;
			}
		}
		return Util.getInternedVB(consensus);
	}

}
