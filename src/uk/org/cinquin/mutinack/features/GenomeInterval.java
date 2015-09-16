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
package uk.org.cinquin.mutinack.features;

import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.SequenceLocation;
import edu.stanford.nlp.util.HasInterval;
import edu.stanford.nlp.util.Interval;

public final class GenomeInterval implements HasInterval<Integer> {
	public final String name;
	public final @NonNull String contig;
	private final int start, end;
	private final int length;
	private final double lengthInverse;
	private final Interval<Integer> interval;
	private @NonNull Optional<Boolean> negativeStrand;
	
	public GenomeInterval(String name, @NonNull String contig, int start, int end, Integer length,
			@NonNull Optional<Boolean> negativeStrand) {
		this.name = name;
		this.contig = contig;
		this.start = start;
		this.end = end;
		this.length = length == null ? (end - start + 1) : length;
		lengthInverse = 1d / this.length;
		interval = Interval.toInterval(start, end);
		this.negativeStrand = negativeStrand;
	}
	
	public @NonNull Optional<Boolean> isNegativeStrand() {
		return negativeStrand;
	}
	
	public @NonNull SequenceLocation getStartLocation() {
		return new SequenceLocation(contig, start);
	}

	public void setNegativeStrand(@NonNull Optional<Boolean> negativeStrand) {
		this.negativeStrand = negativeStrand;
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + contig.hashCode();
		result = prime * result + end;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + start;
		return result;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof GenomeInterval)) {
			return false;
		}
		GenomeInterval other = (GenomeInterval) obj;
		if (!contig.equals(other.contig)) {
			return false;
		}
		if (end != other.end) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (start != other.start) {
			return false;
		}
		return true;
	}
	
	@Override
	public String toString() {
		return name + " on contig " + contig + " " + start + " to " + end;
	}

	public double getLength() {
		return length;
	}
	
	public double getLengthInverse() {
		return lengthInverse;
	}

	@Override
	public Interval<Integer> getInterval() {
		return interval;
	}
	
}
