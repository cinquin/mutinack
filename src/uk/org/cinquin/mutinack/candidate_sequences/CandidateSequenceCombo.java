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

import org.eclipse.jdt.annotation.NonNull;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Order of mutations matters for equality. A combination with a single mutation is considered
 * equal to that single mutation (although the way this class is used such 1-mutation combinations do not appear).
 * @author olivier
 *
 */
public final class CandidateSequenceCombo extends CandidateSequence implements Serializable {

	private static final long serialVersionUID = 8188110941971649756L;

	public CandidateSequenceCombo(int analyzerID, @NonNull SequenceLocation location,
			ExtendedSAMRecord initialConcurringRead, int initialLigationSiteD) {
		super(analyzerID, MutationType.COMBINATION, location, initialConcurringRead,
			initialLigationSiteD);
	}

	/**
	 * No recursive CandidateSequenceCombo inclusion
	 */
	private final List<CandidateSequenceI> candidates = new ArrayList<>();

	public List<CandidateSequenceI> getCandidates() {
		return candidates;
	}
	
	public void mergeCandidate(CandidateSequenceI c) {
		getCandidates().add(c);
		c.addPhredQualitiesToList(getPhredQualityScores());
		getMutableRawMismatchesQ2().addAll(c.getRawMismatchesQ2());
		getMutableRawDeletionsQ2().addAll(c.getRawDeletionsQ2());
		getMutableRawInsertionsQ2().addAll(c.getRawInsertionsQ2());
	}

	@Override
	public int hashCode() {
		if (DebugControl.NONTRIVIAL_ASSERTIONS && getCandidates().size() == 1) {
			throw new AssertionFailedException();
		}
		final int prime = 31;
		int result = 0;
		for (CandidateSequenceI c: candidates) {
			if (c.getMutationType() == MutationType.WILDTYPE) {
				continue;
			}
			result = prime * result + c.hashCode();
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (DebugControl.NONTRIVIAL_ASSERTIONS && getCandidates().size() == 1) {
			throw new AssertionFailedException();
		}
		if (!(obj instanceof CandidateSequenceCombo)) {
			if (obj instanceof CandidateSequence) {
				CandidateSequence cast = (CandidateSequence) obj;
				List<CandidateSequenceI> nonWt = getCandidates().stream().filter(
							c -> c.getMutationType() != MutationType.WILDTYPE).
						collect(Collectors.toList());
				if (cast.getMutationType() == MutationType.WILDTYPE) {
					//Returned value should always be false
					return nonWt.size() == 0 && getCandidates().size() == 1;
				} else {
					return nonWt.size() == 1 && nonWt.get(0).equals(cast);
				}
			} else {
				return false;
			}
		}
		CandidateSequenceCombo other = (CandidateSequenceCombo) obj;
		if (getCandidates().size() != other.getCandidates().size()) {
			return false;
		}
		for (int i = 0; i < getCandidates().size(); i++) {
			if (!getCandidates().get(i).equals(other.getCandidates().get(i))) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public String getChange() {
		return candidates.stream().map(CandidateSequenceI::getChange).collect(Collectors.joining("; "));
	}
	
	@Override
	public String toString() {
		return candidates.stream().map(CandidateSequenceI::getChange).collect(Collectors.joining("; "))
				 + " at " + getLocation() + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
	}
	
	@Override
	public @NonNull String getKind() {
		String result = "";
		CandidateSequenceI wt = null;
		for (CandidateSequenceI c: candidates) {
			if (c.getMutationType() != MutationType.WILDTYPE) {
				if (!"".equals(result)) {
					result += "+";
				}
				result += c.getKind();
			} else {
				wt = c;
			}
		}
		return "".equals(result) ? (wt == null ? "Empty combo" : wt.getKind()) : result;
	}

	@Override
	public boolean containsType(Class<? extends CandidateSequence> class1) {
		for (CandidateSequenceI c: candidates) {
			if (class1.isInstance(c)) {
				return true;
			}
		}
		return false;
	}

	@Override
	/**
	 * Get type that can be represented at most once in the combination
	 * @return Null if type not present
	 */
	public CandidateSequenceI getUniqueType(@NonNull Class<? extends CandidateSequence> class1) {
		CandidateSequenceI result = null;
		for (CandidateSequenceI c: candidates) {
			if (class1.isInstance(c)) {
				if (result != null) {
					throw new AssertionFailedException();
				} else {
					result = c;
				}
			}
		}
		return result;
	}
	
	@Override
	public CandidateSequenceI getUniqueType(@NonNull MutationType type) {
		CandidateSequenceI result = null;
		for (CandidateSequenceI c: candidates) {
			if (c.getMutationType() == type) {
				if (result != null) {
					throw new AssertionFailedException();
				} else {
					result = c;
				}
			}
		}
		return result;
	}
	
	@Override
	public boolean containsMutationType(@NonNull MutationType type) {
		return candidates.stream().filter(c -> c.containsMutationType(type)).
				findAny().isPresent();
	}
}
