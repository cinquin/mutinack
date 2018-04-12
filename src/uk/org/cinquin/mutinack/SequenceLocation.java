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

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

import javax.annotation.concurrent.Immutable;
import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.StaticStuffToAvoidMutating;
import uk.org.cinquin.mutinack.misc_util.collections.InterningSet;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

@PersistenceCapable//(identityType = IdentityType.APPLICATION, objectIdClass = SequenceLocation.PK.class)
@Immutable
public final class SequenceLocation implements Comparable<SequenceLocation>, Serializable {

	public @Final int contigIndex;
	public @Final @NonNull String contigName;
	public @Final int position;
	public @Final boolean plusHalf;
	public @Final @NonNull String referenceGenome;

	//Unused for now, until potential data store uniqueness and performance issues are resolved
	public static class PK implements Serializable {
		private static final long serialVersionUID = 4701545116982926026L;

		public int contigIndex;
		public int position;
		public boolean plusHalf;
		public String referenceGenome;

		public PK() {
		}

		public PK(String s) {
			StringTokenizer token = new StringTokenizer(s, "\t");
			contigIndex = Integer.parseInt(token.nextToken());
			position = Integer.parseInt(token.nextToken());
			plusHalf = Boolean.parseBoolean(token.nextToken());
			referenceGenome = token.nextToken();
		}

		@Override
		public String toString() {
			return contigIndex + "\t" + position + '\t' + plusHalf + '\t' + referenceGenome;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + contigIndex;
			result = prime * result + (plusHalf ? 1231 : 1237);
			result = prime * result + position;
			result = prime * result + referenceGenome.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PK other = (PK) obj;
			if (contigIndex != other.contigIndex)
				return false;
			if (plusHalf != other.plusHalf)
				return false;
			if (position != other.position)
				return false;
			if (!referenceGenome.equals(other.referenceGenome))
				return false;
			return true;
		}
	}

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
		SequenceLocation other = (SequenceLocation) obj;
		if (!referenceGenomesEqual(other)) {
			return false;
		}
		if (position != other.position)
			return false;
		if (contigIndex != other.contigIndex)
			return false;
		if (plusHalf != other.plusHalf) {
			return false;
		}
		return true;
	}

	public SequenceLocation(@NonNull String referenceGenome, int contigIndex, @NonNull String contigName, int position, boolean plusHalf) {
		Assert.isFalse(contigIndex < 0);
		this.referenceGenome = referenceGenome;
		this.contigName = contigName;
		this.contigIndex = contigIndex;
		this.position = position;
		this.plusHalf = plusHalf;

		this.hash = computeHash();
	}

	public static @NonNull SequenceLocation get(InterningSet<@NonNull SequenceLocation> interningSet,
			int contigIndex, @NonNull String referenceGenome, @NonNull String contigName, int position, boolean plusHalf) {
		//TODO Since escape analysis does not remove the allocation below (as shown by HotSpot logs;
		//removing the allocation would need to be done only for objects not already present in the set),
		//create a custom method to retrieve pre-existing objects from the set based only on the
		//constructor parameters (without instantiating a temporary object).
		return interningSet.intern(new SequenceLocation(referenceGenome, contigIndex, contigName, position, plusHalf));
	}

	public static @NonNull SequenceLocation get(InterningSet<@NonNull SequenceLocation> interningSet,
			int contigIndex, @NonNull String referenceGenome, @NonNull String contigName, int position) {
		return get(interningSet, contigIndex, referenceGenome, contigName, position, false);
	}


	public SequenceLocation(@NonNull String referenceGenome, int contigIndex, List<String> contigNames, int position, boolean plusHalf) {
		this(referenceGenome, contigIndex, Objects.requireNonNull(contigNames.get(contigIndex)), position, plusHalf);
	}

	public SequenceLocation(@NonNull String referenceGenome, int contigIndex, @NonNull String contigName, int position) {
		this(referenceGenome, contigIndex, contigName, position, false);
	}

	public SequenceLocation(@NonNull String referenceGenome, int contigIndex, List<String> contigNames, int position) {
		this(referenceGenome, contigIndex, Objects.requireNonNull(contigNames.get(contigIndex)), position, false);
	}

	public SequenceLocation(@NonNull String referenceGenome, @NonNull String contigName, Map<String, Integer> indexContigNameReverseMap,
			int position, boolean plusHalf) {
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
		this.plusHalf = plusHalf;
		this.referenceGenome = referenceGenome;
		this.contigName = contigName;

		this.hash = computeHash();
	}

	public SequenceLocation(@NonNull String referenceGenome, @NonNull String contigName, Map<String, Integer> indexContigNameReverseMap,
			int position) {
		this(referenceGenome, contigName, indexContigNameReverseMap, position, false);
	}

	public @NonNull SequenceLocation add(int offset) {
		return new SequenceLocation(referenceGenome, contigIndex, contigName, position + offset, plusHalf);
	}

	/*
	public SequenceLocation(SAMRecord rec) {
		this(rec.getReferenceIndex() , rec.getReferenceName(), rec.getAlignmentStart());
	}*/

	@Override
	public String toString() {
		return getContigName() + ':' +
				nf.get().format(position + 1) +
				(plusHalf ? ".5" : "");//Internal indexing starts at 0
	}

	public @NonNull String getContigName() {
		return contigName;
	}

	private static final ThreadLocal<NumberFormat> nf = ThreadLocal.withInitial(() -> new DecimalFormat("00,000,000"));

	private boolean referenceGenomesEqual(SequenceLocation other) {
		if (other == null) {
			return false;
		}
		if (!referenceGenome.equals(other.referenceGenome)) {
			return false;
		}
		return true;
	}

	private void checkSameReferenceGenome(SequenceLocation o) {
		if (!referenceGenomesEqual(o)) {
			throw new IllegalArgumentException("Comparing locations on different genomes: " +
				referenceGenome + " and " + o.referenceGenome);
		}
	}

	@Override
	public final int compareTo(SequenceLocation o) {
		checkSameReferenceGenome(o);
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
		checkSameReferenceGenome(location);
		if (contigIndex != location.contigIndex) {
			return null;
		} else {
			return this.position - location.position;
		}
	}

	public int distanceOnSameContig(@NonNull SequenceLocation location) {
		checkSameReferenceGenome(location);
		if (contigIndex != location.contigIndex) {
			throw new IllegalArgumentException("Different contigs " + location.contigIndex +
					" and " + contigIndex);
		} else {
			return this.position - location.position;
		}
	}

	public static SequenceLocation parse(@NonNull String referenceGenome, String location,
			Map<String, Integer> indexContigNameReverseMap) {
		@NonNull String [] split = location.split(":");
		if (split.length != 2) {
			throw new ParseRTException("Need one occurence of : in location " + location);
		}
		int position;
		try {
			position = Integer.parseInt(split[1]) - 1;
		} catch (Exception e) {
			throw new ParseRTException("Could not parse number in location " + location, e);
		}
		return new SequenceLocation(referenceGenome, split[0], indexContigNameReverseMap, position);
	}

	public byte[] getSequenceContext(int windowHalfWidth) {
		final byte[] refSeq = StaticStuffToAvoidMutating.getContigSequence(
			referenceGenome, contigName).getBases();
		return Arrays.copyOfRange(refSeq, Math.max(0, position - windowHalfWidth),
			1 + Math.min(refSeq.length - 1, position + windowHalfWidth));
	}

}
