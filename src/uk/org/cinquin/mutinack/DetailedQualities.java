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
package uk.org.cinquin.mutinack;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.misc_util.Handle;

public class DetailedQualities implements Serializable {
	
	private @Nullable Map<Assay, @NonNull Quality> unmodifiableMap;
	
	private static final long serialVersionUID = -5423960175598403757L;
	@SuppressWarnings("null")
	private final @NonNull Map<Assay, @NonNull Quality> qualities = new EnumMap<>(Assay.class);
	private Quality min = null;
	
	@Override
	public String toString() {
		return qualities.toString();
	}
	
	@SuppressWarnings("null")
	public Map<Assay, Quality> getQualities() {
		if (unmodifiableMap == null) {
			//Not thread safe but that doesn't matter
			unmodifiableMap = Collections.unmodifiableMap(qualities);
		}
		return unmodifiableMap;
	}

	public void addUnique(Assay assay, @NonNull Quality q) {
		if (qualities.put(assay, q) != null) {
			throw new IllegalStateException(assay + " already defined");
		}
		updateMin(q);
	}
	
	@SuppressWarnings("null")
	private void updateMin(@NonNull Quality q) {
		if (min == null) {
			min = q;
		} else {
			min = Quality.min(q, min);
		}
	}

	public void add(Assay assay, @NonNull Quality q) {
		Quality previousQ = qualities.get(assay);
		if (previousQ == null) {
			qualities.put(assay, q);
		} else {
			qualities.put(assay, Quality.min(previousQ, q));
		}
		updateMin(q);
	}
	
	public Quality getMin() {
		return min;
	}
	
	public Quality getMinIgnoring(Set<Assay> assaysToIgnore) {
		Handle<@NonNull Quality> min1 = new Handle<>(Quality.MAXIMUM);
		//NB forEach does not seem to have an efficient implementation
		//in EnumMap (it maps to the default interface implementation)
		qualities.forEach((k, v) -> {
			if (!assaysToIgnore.contains(k)) {
				min1.set(Quality.min(min1.get(), v));
			}
		});
		return min1.get();
	}
	
	public Quality getQuality(Assay assay) {
		Quality q = qualities.get(assay);
		if (q == null) {
			return Quality.ATROCIOUS;
		} else {
			return q;
		}
	}

	public void reset() {
		qualities.clear();
		min = null;
	}
}
