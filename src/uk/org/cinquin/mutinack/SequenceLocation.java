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

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.SAMRecord;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public final class SequenceLocation implements Comparable<SequenceLocation>, Serializable {
	
	final public int contigIndex;
	final public int position;
	final public int hash;
	final public boolean plusHalf;

	private static final long serialVersionUID = -8294857765048137986L;

	@Override
	public final int hashCode() {
		return hash;
	}
	
	private int computeHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + contigIndex;
		result = prime * result + position;
		if (plusHalf) {
			result = prime * result + 1;
		}
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SequenceLocation))
			return false;
		SequenceLocation other = (SequenceLocation) obj;
		if (position != other.position)
			return false;
		if (contigIndex != other.contigIndex)
			return false;
		if (plusHalf != other.plusHalf) {
			return false;
		}
		return true;
	}
	
	public SequenceLocation(int contigIndex, int position, boolean plusHalf) {
		if (contigIndex < 0)
			throw new AssertionFailedException();
		this.contigIndex = contigIndex;
		this.position = position;
		this.plusHalf = plusHalf;
		
		this.hash = computeHash();

	}
	
	public SequenceLocation(int contigIndex, int position) {
		this(contigIndex, position, false);
	}
	
	public SequenceLocation(@NonNull String contigName, int position) {
		final int contigIndex1;
		try {
			contigIndex1 = Mutinack.indexContigNameReverseMap.get(contigName);
		} catch (NullPointerException e) {
			throw new RuntimeException("Could not retrieve contig index for " + contigName +
					" in " + Mutinack.indexContigNameReverseMap);
		}
		this.contigIndex = contigIndex1;
		this.position = position;
		this.plusHalf = false;
		
		this.hash = computeHash();
	}
	
	public @NonNull SequenceLocation add(int offset) {
		return new SequenceLocation(contigIndex, position + offset);
	}
	
	public SequenceLocation(SAMRecord rec) {
		this(rec.getReferenceName(), rec.getAlignmentStart());
	}

	@Override
	public String toString() {
		return Mutinack.indexContigNameMap.get(contigIndex) + ":"
				+ nf.get().format(position + 1) +
				(plusHalf ? ".5" : "");//Internal indexing starts at 0
	}
	
	public String getContigName() {
		return Mutinack.indexContigNameMap.get(contigIndex);
	}
	
	private static final ThreadLocal<NumberFormat> nf = new ThreadLocal<NumberFormat>() {
		@Override
		protected NumberFormat initialValue() {
			return new DecimalFormat("00,000,000");
		}
	};

	@Override
	public final int compareTo(SequenceLocation o) {
		int contigCompare = Integer.compare(this.contigIndex, o.contigIndex);
		if (contigCompare != 0) {
			return contigCompare;
		}
		else {
			int positionCompare = Integer.compare(this.position, o.position);
			if (positionCompare != 0) {
				return positionCompare;
			}
			return Integer.compare(plusHalf ? 1 : 0, o.plusHalf ? 1 : 0);
		}
	}

	public @Nullable Integer distanceTo(@NonNull SequenceLocation location) {
		if (contigIndex != location.contigIndex) {
			return null;
		} else {
			return this.position - location.position;
		}
	}
	
	public int distanceOnSameContig(@NonNull SequenceLocation location) {
		if (contigIndex != location.contigIndex) {
			throw new IllegalArgumentException("Different contigs " + location.contigIndex + 
					" and " + contigIndex);
		} else {
			return this.position - location.position;
		}
	}
}
