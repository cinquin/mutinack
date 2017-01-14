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
package uk.org.cinquin.mutinack.candidate_sequences;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public enum Quality {
	ATROCIOUS, POOR, DUBIOUS, GOOD;

	static final public @NonNull Quality
		MINIMUM = Quality.ATROCIOUS,
		MAXIMUM = Quality.GOOD;

	public static @NonNull Quality min(@NonNull Quality a, @NonNull Quality b) {
		return a.compareTo(b) < 0 ? a : b;
	}

	public static @Nullable Quality nullableMin(@Nullable Quality a, @Nullable Quality b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		} else {
			return a.compareTo(b) < 0 ? a : b;
		}
	}

	public static @Nullable Quality nullableMax(@Nullable Quality a, @Nullable Quality b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		} else {
			return a.compareTo(b) > 0 ? a : b;
		}
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

	public int toInt() {
		switch (this) {
			case ATROCIOUS: return -1;
			case POOR: return 0;
			case DUBIOUS: return 1;
			case GOOD: return 2;
			default: throw new AssertionFailedException();
		}
	}

	public boolean atLeast(@NonNull Quality q) {
		return this.compareTo(q) >= 0;
	}

	public boolean greaterThan(@NonNull Quality q) {
		return this.compareTo(q) > 0;
	}

	public boolean atMost(@NonNull Quality q) {
		return this.compareTo(q) <= 0;
	}

	public boolean lowerThan(@NonNull Quality q) {
		return this.compareTo(q) < 0;
	}
}
