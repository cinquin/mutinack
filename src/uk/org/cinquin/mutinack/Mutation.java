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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequenceI;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.json.ByteArrayStringSerializer;

public final class Mutation implements Comparable<Mutation>, Serializable, Cacheable {

	private static final long serialVersionUID = 6679657529214343514L;
	public final @NonNull MutationType mutationType;
	public final byte wildtype;
	@JsonSerialize(using = ByteArrayStringSerializer.class)
	public final byte[] mutationSequence;
	private Boolean templateStrand;

	public static final @NonNull Mutation UNKNOWN_STATUS = new Mutation(MutationType.UNKNOWN,
		(byte) 0, null, Optional.empty());

	public Mutation(@NonNull MutationType mutationType, byte wildtype,
			byte[] mutationSequence, @NonNull Optional<Boolean> templateStrand) {
		if (mutationType == MutationType.SUBSTITUTION && mutationSequence[0] == wildtype) {
			throw new IllegalArgumentException("Substitution with identical base");
		}
		this.mutationType = Objects.requireNonNull(mutationType);
		this.wildtype = wildtype;
		this.mutationSequence = mutationSequence;
		this.setTemplateStrand(templateStrand);
	}

	public static final List<Character> KNOWN_BASES_AS_CHARS = Arrays.asList(
			'A', 'T', 'G', 'C', 'N', 'Y', 'R', 'W', 'S', 'B', 'D', 'H', 'K', 'M', 'V'
		);

	public static final List<Byte> KNOWN_BASES = Collections.unmodifiableList(
		KNOWN_BASES_AS_CHARS.stream().map(c -> Byte.valueOf((byte) c.charValue())).
			collect(Collectors.toList()));

	public static void checkIsValidUCBase(byte b) {
		switch (b) {
			case 'B':
			case 'D':
			case 'H':
			case 'K':
			case 'M':
			case 'R':
			case 'S':
			case 'V':
			case 'W':
			case 'Y':
			case 'A':
			case 'T':
			case 'G':
			case 'C':
			case 'N':
				break;
			default:
				throw new IllegalArgumentException(new String(new byte [] {b}) + " is not a valid upper-case base");
		}
	}

	public static byte complement (byte b) {
		switch (b) {
			case 'A': return 'T';
			case 'T': return 'A';
			case 'G': return 'C';
			case 'C': return 'G';
			case 'a': return 't';
			case 't': return 'a';
			case 'g': return 'c';
			case 'c': return 'g';
			case 'n': return 'n';
			case 'N': return 'N';
			case 'Y': return 'R';
			case 'R': return 'Y';
			case 'W': return 'W';
			case 'S': return 'S';
			case 'B': return 'V';
			case 'D': return 'H';
			case 'H': return 'D';
			case 'K': return 'M';
			case 'M': return 'K';
			case 'V': return 'B';
			case 'y': return 'r';
			case 'r': return 'y';
			case 'w': return 'w';
			case 's': return 's';
			case 'b': return 'b';
			case 'd': return 'd';
			case 'h': return 'h';
			case 'k': return 'k';
			case 'm': return 'm';
			case 'v': return 'v';
			case 0 : return 0;
			default : throw new IllegalArgumentException("Cannot complement " +
					new String(new byte[] {b}));
		}
	}

	public static @NonNull String reverseComplement(@NonNull String s) {
		byte [] bytes = s.getBytes();
		byte [] rcBytes = new byte[bytes.length];
		for (int i = 0, r = bytes.length - 1; i < bytes.length; i++, r--) {
			rcBytes[i] = complement(bytes[r]);
		}
		return new String(rcBytes);
	}

	public @NonNull Mutation reverseComplement() {
		final byte[] cMutSeq;
		if (mutationSequence != null) {
			if (mutationSequence.length < 1) {
				throw new AssertionFailedException();
			}
			cMutSeq = new byte[mutationSequence.length];
			try {
				for (int i = 0, r = mutationSequence.length - 1; r >= 0; i++, r--) {
					cMutSeq[i] = complement(mutationSequence[r]);
				}
			} catch (Exception e) {
				throw new RuntimeException("Problem reverse complementing " +
						new String(mutationSequence), e);
			}
		} else {
			cMutSeq = null;
		}
		return new Mutation(mutationType, complement(wildtype), cMutSeq, isTemplateStrand());
	}

	public static ComparablePair<@NonNull String, @NonNull String> reverseComplement(
			Pair<@NonNull String, @NonNull String> pair) {
		return new ComparablePair<>(Mutation.reverseComplement(pair.fst), Mutation.reverseComplement(pair.snd));
	}

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(mutationSequence);
		result = prime * result + mutationType.hashCode();
		result = prime * result + wildtype;
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
		Mutation other = (Mutation) obj;
		if (mutationType != other.mutationType)
			return false;
		if (!Arrays.equals(mutationSequence, other.mutationSequence))
			return false;
		if (wildtype != other.wildtype)
			return false;
		return true;
	}

	public byte[] getSequence() {
		if (mutationType.isWildtype()) {
			return new byte[] {wildtype};
		} else {
			return mutationSequence;
		}
	}

	public Mutation(CandidateSequenceI c) {
		mutationType = Objects.requireNonNull(c.getMutationType());
		wildtype = c.getWildtypeSequence();
		mutationSequence = c.getSequence();
	}

	private String mutationSequenceString () {
		if (mutationSequence == null) {
			return "";
		} else {
			return new String(mutationSequence);
		}
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean longForm) {
		switch(mutationType) {
			case WILDTYPE:
				return "wt " + new String(new byte[] {wildtype});
			case DELETION:
				return "del " + mutationSequenceString();
			case INTRON:
				return "int";
			case INSERTION:
				return "ins " + mutationSequenceString();
			case SUBSTITUTION:
				return "subst " + (longForm ? (new String(new byte[] {wildtype}) + "->") : "") +
					mutationSequenceString();
			case REARRANGEMENT:
				return "rearr ";
			case UNKNOWN:
				return "?";
			default : throw new AssertionFailedException();
		}
	}

	public String toLongString() {
		return toString(true);
	}

	@Override
	public final int compareTo(@SuppressWarnings("null") @NonNull Mutation o) {
		if (this.equals(o)) {
			return 0;
		}
		int mutationTypeCompare = mutationType.compareTo(o.mutationType);
		if (mutationTypeCompare != 0) {
			return mutationTypeCompare;
		}
		int c1 = Byte.compare(wildtype, o.wildtype);
		if (c1 != 0)
			return c1;
		if (mutationSequence != null && o.mutationSequence != null) {
			return compareByteArray(mutationSequence, o.mutationSequence);
		}
		throw new AssertionFailedException();
	}

	//Copied from JDK's String::compareTo
	public static int compareByteArray(byte [] a1, byte [] a2) {
		int len1 = a1.length;
		int len2 = a2.length;
		int lim = Math.min(len1, len2);

		int k = 0;
		while (k < lim) {
			byte c1 = a1[k];
			byte c2 = a2[k];
			if (c1 != c2) {
				return c1 - c2;
			}
			k++;
		}
		return len1 - len2;
	}

	public @NonNull Optional<Boolean> isTemplateStrand() {
		return Optional.ofNullable(templateStrand);
	}

	public void setTemplateStrand(Optional<Boolean> templateStrand) {
		this.templateStrand = templateStrand.orElse(null);
	}

	@Override
	public boolean shouldCache() {
		return mutationSequence == null || mutationSequence.length < 3;
	}

	public String getChange(final boolean reverseComplement) {
		final Mutation mut = reverseComplement ? reverseComplement() : this;
		switch (mutationType) {
			case DELETION:
				return '-' + new String(mut.getSequence()) + '-';
			case INTRON:
				return "-INTRON-";
			case INSERTION:
				return '^' + new String(mut.getSequence()) + '^';
			case SUBSTITUTION:
				return new String(new byte[] {mut.wildtype}) +
						"->" + new String(mut.getSequence());
			case WILDTYPE:
				return "wt";
			default:
				throw new AssertionFailedException();
		}
	}

}
