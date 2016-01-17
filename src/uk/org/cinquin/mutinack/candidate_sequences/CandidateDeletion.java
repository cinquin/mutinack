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
package uk.org.cinquin.mutinack.candidate_sequences;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.SequenceLocation;

/**
 * Equality test does not include sequence itself, just its span in the reference genome.
 * @author olivier
 *
 */
public final class CandidateDeletion extends CandidateSequence implements Serializable {
	
	private static final long serialVersionUID = 3708709622820371707L;
	private final @NonNull SequenceLocation deletionStart, deletionEnd;
	
	@Override
	public final String toString() {
		String result = "deletion at " + getLocation() + " spanning " + deletionStart + "--" + deletionEnd + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
		return result;
	}
	
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + deletionEnd.hashCode();
		result = prime * result + deletionStart.hashCode();
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof CandidateDeletion))
			return false;
		CandidateDeletion other = (CandidateDeletion) obj;
		if (!deletionEnd.equals(other.deletionEnd))
			return false;
		if (!deletionStart.equals(other.deletionStart))
			return false;
		return true;
	}

	public CandidateDeletion(int analyzerID, @NonNull SequenceLocation location,
			@NonNull ExtendedSAMRecord initialConcurringRead,
			int initialLigationSiteD,
			@NonNull SequenceLocation deletionStart, @NonNull SequenceLocation deletionEnd) {
		super(analyzerID, MutationType.DELETION, location, initialConcurringRead,
			initialLigationSiteD);
		this.deletionStart = deletionStart;
		this.deletionEnd = deletionEnd;
	}

}
