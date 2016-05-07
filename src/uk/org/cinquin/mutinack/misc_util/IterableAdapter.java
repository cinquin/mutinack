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

import java.util.Iterator;
import java.util.function.Supplier;

public class IterableAdapter<V> implements Iterable<V> {
	
	private Supplier<Iterator<V>> supplier;

	public IterableAdapter(Supplier<Iterator<V>> supplier) {
		this.supplier = supplier;
	}

	@Override
	public Iterator<V> iterator() {
		return supplier.get();
	}

}
