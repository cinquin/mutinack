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
package uk.org.cinquin.mutinack.candidate_sequences;

import java.io.Serializable;

import javax.jdo.annotations.Discriminator;
import javax.jdo.annotations.DiscriminatorStrategy;
import javax.jdo.annotations.Inheritance;
import javax.jdo.annotations.InheritanceStrategy;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.misc_util.Assert;

/**
 * @author olivier
 * It is assumed (and asserted) that two deletions that span the same genome range
 * must have the same sequence.
 */
@PersistenceCapable
@Discriminator(strategy = DiscriminatorStrategy.CLASS_NAME)
@Inheritance(strategy = InheritanceStrategy.NEW_TABLE)
public final class CandidateDeletion extends CandidateSequence implements Serializable {
	private static final long serialVersionUID = 3708709622820371707L;

	private @NotPersistent @Final @NonNull SequenceLocation deletionStart, deletionEnd;

	@Override
	public final String toString() {
		String result = "deletion at " + getLocation() + " spanning " + deletionStart + "--" + deletionEnd +
			getConcurringReadString();
		return result;
	}

	/*@Override
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
		Assert.isTrue(getLocation().equals(other.getLocation()));//At the moment
		//there is no reason to compare deletions in different contigs
		Assert.isTrue(Arrays.equals(getSequence(), other.getSequence()), () ->
			"Two deletions with same span but different sequences: " +
			new String(getSequence()) + " vs " + new String(other.getSequence()) + "; " + this + "; " + other);
		return true;
	}*/

	public CandidateDeletion(
			@NonNull SubAnalyzer subAnalyzer,
			byte @Nullable[] sequence,
			@NonNull SequenceLocation location,
			@NonNull ExtendedSAMRecord initialConcurringRead,
			int initialLigationSiteD,
			@NonNull MutationType mutationType,
			@NonNull SequenceLocation deletionStart,
			@NonNull SequenceLocation deletionEnd) {
		super(subAnalyzer, mutationType, sequence, location, initialConcurringRead,
			initialLigationSiteD);
		Assert.isTrue(mutationType == MutationType.DELETION || mutationType == MutationType.INTRON);
		Assert.isTrue(deletionStart.contigIndex == deletionEnd.contigIndex);
		this.deletionStart = deletionStart;
		this.deletionEnd = deletionEnd;
	}

}
