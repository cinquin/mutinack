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
package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.collections.api.list.primitive.MutableFloatList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.DuplexDisagreement;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.collections.MapOfLists;

@PersistenceCapable
public final class LocationExaminationResults implements Serializable {
	private static final long serialVersionUID = -2966237959317593137L;

	@JsonIgnore //Already listed in LocationAnalysis
	public transient ImmutableSet<CandidateSequence> analyzedCandidateSequences;
	public int nGoodOrDubiousDuplexes = 0;
	public Boolean tooHighCoverage;
	public int nGoodOrDubiousDuplexesSisterSamples = 0;
	public int nGoodDuplexesIgnoringDisag = 0;
	public int nGoodDuplexes = 0;
	public int strandCoverageImbalance;
	public int nMissingStrands;
	public MutableFloatList alleleFrequencies;
	public final transient @NonNull
		MapOfLists<@NonNull DuplexDisagreement, @NonNull Duplex>
		disagreements = new MapOfLists<>();//Transient because DuplexRead is not serializable
	public int disagQ2Coverage = 0;
	public int disagOneStrandedCoverage = 0;
	@JsonIgnore
	public final transient @NonNull Collection<@NonNull ComparablePair<String, String>>
		rawMismatchesQ2,
		rawDeletionsQ2,
		rawInsertionsQ2,
		intraStrandSubstitutions,
		intraStrandDeletions,
		intraStrandInsertions;

	public int intraStrandNReads = 0;

	public int duplexInsertSize10thP = -1;
	public int duplexInsertSize90thP = -1;
	public double probAtLeastOneCollision = -1;

	//To assert that an instance is not concurrently modified by
	//multiple threads
	@JsonIgnore
	public final transient AtomicInteger threadCount = new AtomicInteger();

	public LocationExaminationResults(Parameters param) {
		if (param.computeRawMismatches) {
			rawMismatchesQ2 = new ArrayList<>();
			rawDeletionsQ2 = new ArrayList<>();
			rawInsertionsQ2 = new ArrayList<>();
		} else {
			rawMismatchesQ2 = Collections.emptyList();
			rawDeletionsQ2 = Collections.emptyList();
			rawInsertionsQ2 = Collections.emptyList();
		}

		if (param.computeIntraStrandMismatches) {
			intraStrandSubstitutions = new ArrayList<>();
			intraStrandDeletions = new ArrayList<>();
			intraStrandInsertions = new ArrayList<>();
		} else {
			intraStrandSubstitutions = Collections.emptyList();
			intraStrandDeletions = Collections.emptyList();
			intraStrandInsertions = Collections.emptyList();
		}
	}
}
