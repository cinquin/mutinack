package uk.org.cinquin.mutinack.candidate_sequences;

public class CandidateDuplexEval {
	public CandidateDuplexEval(CandidateSequenceI candidateSequence) {
		this.candidate = candidateSequence;
	}
	public final CandidateSequenceI candidate;
	public int count = 0;
	public int maxDistanceToLigSite = -1;
}
