/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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
import java.text.NumberFormat;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.statistics.LongAdderFormatter;


public final class ByteArray {

	public final byte @NonNull[] array;
	public final @NonNull LongAdderFormatter nHits = new LongAdderFormatter();

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(array);
		return result;
	}
	
	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ByteArray other = (ByteArray) obj;
		if (!Arrays.equals(array, other.array))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return new String(array) + " (" + NumberFormat.getInstance().format(nHits.sum()) + " hits)";
	}
		
	public ByteArray(byte @NonNull[] array){
		this.array = array;
	}
}