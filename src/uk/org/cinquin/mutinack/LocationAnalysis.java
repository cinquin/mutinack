package uk.org.cinquin.mutinack;

import java.util.HashSet;
import java.util.Set;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

public class LocationAnalysis {

	public CrossSampleLocationAnalysis csla;
	public final Set<CandidateSequence> candidates = new HashSet<>();
	public final Set<DuplexDisagreement> disagreements = new HashSet<>();

	public LocationAnalysis(CrossSampleLocationAnalysis csla) {
		this.csla = csla;
	}

	public LocationAnalysis setCsla(CrossSampleLocationAnalysis csla) {
		this.csla = csla;
		return this;
	}

}
