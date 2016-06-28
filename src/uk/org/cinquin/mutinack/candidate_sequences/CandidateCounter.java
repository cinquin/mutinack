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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.THashSet;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public final class CandidateCounter {
	private final @NonNull THashSet<CandidateSequence> candidates;
	private List<@NonNull ExtendedSAMRecord> records;
	private final @NonNull SequenceLocation location;
	public int minBasePhredScore = 0;
	public final @NonNull TObjectIntHashMap<@NonNull CandidateSequence>
		candidateCounts;
	public final Set<@NonNull ExtendedSAMRecord> keptRecords;
	
	public long nPhreds, sumPhreds;

	public CandidateCounter(@NonNull THashSet<CandidateSequence> candidates,
			@NonNull SequenceLocation location) {
		this.candidates = candidates;
		this.location = location;
		keptRecords = new THashSet<>();
		candidateCounts = new TObjectIntHashMap<>(5);
	}

	public void reset() {
		keptRecords.clear();
		candidateCounts.clear();
	}
	
	public void compute() {
		reset();
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS) {
			if (keptRecords.size() > 0 || candidateCounts.size() > 0) {
				throw new AssertionFailedException();
			}
		}
		sumPhreds = 0;
		nPhreds = 0;
		candidates.forEach(candidate -> {
			for (int i = records.size() - 1; i >= 0; --i) {
				ExtendedSAMRecord r = records.get(i);
				if (candidate.getNonMutableConcurringReads().containsKey(r)) {
					Byte phredScore = r.basePhredScores.get(location);
					if (phredScore != null) {
						sumPhreds += phredScore;
						nPhreds++;
					}
					if (minBasePhredScore > 0) {
						if (phredScore != null && phredScore < minBasePhredScore) {
							continue;
						}
					}
					keptRecords.add(r);
					candidateCounts.adjustOrPutValue(candidate, 1, 1);
				}
			}//End loop over records
			return true;
		}//End loop over candidates
		);
	}
	
	public void setRecords(@NonNull List<@NonNull ExtendedSAMRecord> records) {
		this.records = records;
	}

}
