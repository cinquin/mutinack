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
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import contrib.net.sf.samtools.SAMRecord;
import uk.org.cinquin.mutinack.misc_util.Assert;

public final class SequenceLocation implements Comparable<SequenceLocation>, Serializable {
	
	public final int contigIndex;
	public final String contigName;
	public final int position;
	public final boolean plusHalf;

	@JsonIgnore
	private final int hash;
	
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
	
	public SequenceLocation(int contigIndex, String contigName, int position, boolean plusHalf) {
		Assert.isFalse(contigIndex < 0);
		this.contigName = contigName;
		this.contigIndex = contigIndex;
		this.position = position;
		this.plusHalf = plusHalf;
		
		this.hash = computeHash();
	}
	
	public SequenceLocation(int contigIndex, Map<Integer, String> nameMap, int position, boolean plusHalf) {
		this(contigIndex, nameMap.get(contigIndex), position, plusHalf);
	}
	
	public SequenceLocation(int contigIndex, String contigName, int position) {
		this(contigIndex, contigName, position, false);
	}

	public SequenceLocation(int contigIndex, Map<Integer, String> nameMap, int position) {
		this(contigIndex, nameMap.get(contigIndex), position, false);
	}
	
	public SequenceLocation(@NonNull String contigName, Map<String, Integer> indexContigNameReverseMap,
			int position) {
		final int contigIndex1;
		try {
			contigIndex1 = Objects.requireNonNull(
					indexContigNameReverseMap.get(contigName));
		} catch (NullPointerException e) {
			throw new RuntimeException("Could not retrieve contig index for " + contigName +
					" in " + indexContigNameReverseMap);
		}
		this.contigIndex = contigIndex1;
		this.position = position;
		this.plusHalf = false;
		this.contigName = contigName;
		
		this.hash = computeHash();
	}
	
	public @NonNull SequenceLocation add(int offset) {
		return new SequenceLocation(contigIndex, contigName, position + offset, plusHalf);
	}
	
	public SequenceLocation(SAMRecord rec) {
		this(rec.getReferenceIndex() , rec.getReferenceName(), rec.getAlignmentStart());
	}

	@Override
	public String toString() {
		return getContigName() + ":" +
				nf.get().format(position + 1) +
				(plusHalf ? ".5" : "");//Internal indexing starts at 0
	}
	
	public String getContigName() {
		return contigName;
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
