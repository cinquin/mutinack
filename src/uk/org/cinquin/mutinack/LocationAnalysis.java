package uk.org.cinquin.mutinack;

import java.util.HashSet;
import java.util.Set;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

public class LocationAnalysis {

	public final CrossSampleLocationAnalysis csla;
	public final Set<CandidateSequence> candidates = new HashSet<>();

	public LocationAnalysis(CrossSampleLocationAnalysis csla) {
		this.csla = csla;
	}

}
