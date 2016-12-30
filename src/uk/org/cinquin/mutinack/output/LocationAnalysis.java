package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.DuplexDisagreement;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

@PersistenceCapable
public class LocationAnalysis implements Serializable {
	private static final long serialVersionUID = 622542608557547921L;

	public @Persistent CrossSampleLocationAnalysis crossSampleLocationAnalysis;
	public @Final @Persistent
	LocationExaminationResults locationStats;
	public @Final @Persistent Set<CandidateSequence> candidates = new HashSet<>();
	public @Final @Persistent Set<DuplexDisagreement> disagreements = new HashSet<>();

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
