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
package uk.org.cinquin.mutinack;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public enum Quality {
	ATROCIOUS, POOR, DUBIOUS, GOOD;

	static final @NonNull Quality MINIMUM = Quality.ATROCIOUS;
	static final @NonNull Quality MAXIMUM = Quality.GOOD;
	
	public static @NonNull Quality min(@NonNull Quality a, @NonNull Quality b) {
		return a.compareTo(b) < 0 ? a : b;
	}

	public static @NonNull Quality max(@NonNull Quality a, @NonNull Quality b) {
		return a.compareTo(b) >= 0 ? a : b;
	}

	public String toShortString() {
		switch (this) {
			case ATROCIOUS: return "-1";
			case POOR: return "0";
			case DUBIOUS: return "1";
			case GOOD: return "2";
			default: throw new AssertionFailedException();
		}
	}
}
