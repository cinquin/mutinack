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

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;

final class LocationExaminationResults {
	Collection<CandidateSequence> analyzedCandidateSequences;
	int nGoodOrDubiousDuplexes = 0;
	int nGoodDuplexesIgnoringDisag = 0;
	int nGoodDuplexes = 0;
	int strandCoverageImbalance;
	int nMissingStrands;
	final @NonNull 
		Collection<@NonNull DuplexDisagreement> disagreements = new ArrayList<>();
	final @NonNull 
	Collection<@NonNull ComparablePair<String, String>> rawMismatchesQ2 = new ArrayList<>(),
		rawDeletionsQ2 = new ArrayList<>(), rawInsertionsQ2 = new ArrayList<>();
	public Object disagreementsNoWt;
}
