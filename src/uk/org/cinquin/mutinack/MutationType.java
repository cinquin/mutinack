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

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;


public enum MutationType {
	INSERTION, DELETION, SUBSTITUTION, WILDTYPE;

	@Override
	public @NonNull String toString() {
		switch(this) {
			case WILDTYPE: return "wildtype";
			case INSERTION: return "insertion";
			case DELETION: return "deletion";
			case SUBSTITUTION: return "substitution";
			default: throw new AssertionFailedException();
		}
	}
	
	public boolean isWildtype() {
		return this == WILDTYPE;
	}
}

