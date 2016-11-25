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
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequenceI;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public final class Mutation implements Comparable<Mutation>, Serializable {
	
	private static final long serialVersionUID = 6679657529214343514L;
	public final MutationType mutationType;
	private final byte wildtype;
	public final byte[] mutationSequence;
	private Boolean templateStrand;

	public Mutation(MutationType mutationType, byte wildtype, boolean negativeStrand,
			byte[] mutationSequence, @NonNull Optional<Boolean> templateStrand) {
		this.mutationType = mutationType;
		this.wildtype = wildtype;
		this.mutationSequence = mutationSequence;
		this.setTemplateStrand(templateStrand);
	}
	
	static byte complement (byte b) {
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
			case 0 : return 0;
			default : throw new AssertionFailedException("Cannot complement " + 
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

	public Mutation reverseComplement() {
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
		Mutation c = new Mutation(mutationType, complement(wildtype), /* XXX */ false, cMutSeq,
				isTemplateStrand());
		return c;
	}
	
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(mutationSequence);
		result = prime * result + ((mutationType == null) ? 0 : mutationType.hashCode());
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
		if (!Arrays.equals(mutationSequence, other.mutationSequence))
			return false;
		if (mutationType != other.mutationType)
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
		mutationType = c.getMutationType();
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
		switch(mutationType) {
		case WILDTYPE:
			return "wt " + new String(new byte[] {wildtype});
		case DELETION:
			return "del " + mutationSequenceString();
		case INSERTION:
			return "ins " + mutationSequenceString();
		case SUBSTITUTION:
			return "subst " + mutationSequenceString();
		default : throw new AssertionFailedException();
		}
	}

	@Override
	public final int compareTo(@SuppressWarnings("null") @NonNull Mutation o) {
		if (this.equals(o)) {
			return 0;
		}
		if (mutationSequence != null && o.mutationSequence != null &&
				mutationSequence.length > 0 && o.mutationSequence.length > 0 ){
			return Byte.compare(mutationSequence[0], o.mutationSequence[0]);
		}
		int mutationTypeCompare = mutationType.compareTo(o.mutationType);
		if (mutationTypeCompare != 0) {
			return mutationTypeCompare;
		}
		return Byte.compare(wildtype, o.wildtype);
	}

	public @NonNull Optional<Boolean> isTemplateStrand() {
		return Util.nonNullify(Optional.ofNullable(templateStrand));
	}

	public void setTemplateStrand(Optional<Boolean> templateStrand) {
		this.templateStrand = templateStrand.orElse(null);
	}
}
