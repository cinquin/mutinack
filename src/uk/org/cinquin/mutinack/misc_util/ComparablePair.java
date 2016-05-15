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

import java.io.Serializable;

//Adapted from http://stackoverflow.com/questions/156275/what-is-the-equivalent-of-the-c-pairl-r-in-java
public class ComparablePair<A extends Comparable<A>, B extends Comparable<B> > extends Pair<A,B>
	implements Comparable<ComparablePair<A,B>>, Serializable {

	private static final long serialVersionUID = -2800696832250328844L;

	public ComparablePair(A first, B second) {
    	super(first, second);
    }

	@Override
	public int compareTo(ComparablePair<A, B> o) {
		if (this.fst.equals(o.fst)) {
			return this.snd.compareTo(o.snd);
		} else {
			return this.fst.compareTo(o.fst);
		}
	}
}
