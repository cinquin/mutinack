package uk.org.cinquin.mutinack.candidate_sequences;

public class CandidateDuplexEval {
	public CandidateDuplexEval(CandidateSequence candidateSequence) {
		this.candidate = candidateSequence;
	}
	public final CandidateSequence candidate;
	public int count = 0;
	public int maxDistanceToLigSite = -1;
}
