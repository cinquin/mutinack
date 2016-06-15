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

import java.util.Set;
import java.util.stream.Collector;

import gnu.trove.set.hash.THashSet;

public class TroveSetCollector {
	public static<T> Collector<T, Set<T>, Set<T>> uniqueValueCollector() {
	    return Collector.of(
	            THashSet<T>::new,
	            (set, e) -> set.add(e),
	            (set1, set2) -> {
	            	set1.addAll(set2);
	            	return set1;
	            	},
	            set -> set
	    );
	}
}
