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
package uk.org.cinquin.mutinack.features;

import java.io.Serializable;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import contrib.edu.standford.nlp.util.HasInterval;
import contrib.edu.standford.nlp.util.Interval;
import uk.org.cinquin.mutinack.SequenceLocation;

/**
 * Equality based on name, contigName, start and end.
 * @author olivier
 *
 */
public final class GenomeInterval implements HasInterval<Integer>, Serializable {

	private static final long serialVersionUID = -8173244932350184778L;
	public final String name;
	public final @NonNull String contigName;
	public final int contigIndex;
	private final int start, end;
	private final int length;
	private final float score;
	@JsonIgnore
	private final double lengthInverse;
	@JsonIgnore
	private final Interval<Integer> interval;
	private @Nullable Boolean negativeStrand;
	
	public GenomeInterval(String name, int contigIndex, @NonNull String contigName, int start, int end, Integer length,
			@NonNull Optional<Boolean> negativeStrand, float score) {
		this.name = name;
		this.contigName = contigName;
		this.contigIndex = contigIndex;
		this.start = start;
		this.end = end;
		this.length = length == null ? (end - start + 1) : length;
		lengthInverse = 1d / this.length;
		interval = Interval.toInterval(start, end);
		this.negativeStrand = negativeStrand.orElse(null);
		this.score = score;
	}
	
	private static final Optional<Boolean> optionalTrue = Optional.of(true);
	private static final Optional<Boolean> optionalFalse = Optional.of(false);
	
	public @NonNull Optional<Boolean> isNegativeStrand() {
		return negativeStrand == null ? Optional.empty() : (negativeStrand ? optionalTrue : optionalFalse);
	}
	
	public @NonNull SequenceLocation getStartLocation() {
		return new SequenceLocation(contigIndex, contigName, start);
	}

	public void setNegativeStrand(@NonNull Optional<Boolean> negativeStrand) {
		this.negativeStrand = negativeStrand.orElse(null);
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public float getScore() {
		return score;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + contigName.hashCode();
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
		if (!contigName.equals(other.contigName)) {
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
		return name + " on contig " + contigName + " " + start + " to " + end;
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
