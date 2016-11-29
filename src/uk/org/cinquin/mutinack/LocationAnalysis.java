package uk.org.cinquin.mutinack;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

public class LocationAnalysis implements Serializable {
	private static final long serialVersionUID = 622542608557547921L;

	public CrossSampleLocationAnalysis crossSampleLocationAnalysis;
	public final LocationExaminationResults locationStats;
	public final Set<CandidateSequence> candidates = new HashSet<>();
	public final Set<DuplexDisagreement> disagreements = new HashSet<>();

	public LocationAnalysis(CrossSampleLocationAnalysis crossSampleLocationAnalysis,
			LocationExaminationResults locationStats) {
		this.crossSampleLocationAnalysis = crossSampleLocationAnalysis;
		this.locationStats = locationStats;
	}

	public LocationAnalysis setCrossSampleLocationAnalysis(
			CrossSampleLocationAnalysis crossSampleLocationAnalysis) {
		this.crossSampleLocationAnalysis = crossSampleLocationAnalysis;
		return this;
	}

}
