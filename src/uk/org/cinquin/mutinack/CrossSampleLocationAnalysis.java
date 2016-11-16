package uk.org.cinquin.mutinack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

public class CrossSampleLocationAnalysis {
	public final SequenceLocation location;
	public boolean randomlySelected;
	public boolean lowTopAlleleFreq;
	public int candidateCount;
	boolean twoOrMoreSamplesWithSameQ2MutationCandidate;
	boolean oneSampleNoWt;
	boolean noWt;

	final List<Integer> nDuplexesUniqueQ2MutationCandidate = new ArrayList<>();

	final Map<String, CandidateSequence> candidates = new HashMap<>();

	public CrossSampleLocationAnalysis(@NonNull SequenceLocation location) {
		this.location = location;
	}

	@Override
	public @NonNull String toString() {
		StringBuilder result = new StringBuilder();

		if (randomlySelected) {
			result.append("+");
		}

		if (lowTopAlleleFreq) {
			result.append("%");
		}

		if (twoOrMoreSamplesWithSameQ2MutationCandidate) {
			result.append("!");
		}

		if (nDuplexesUniqueQ2MutationCandidate.size() > 0) {
			result.append("*").
				append(nDuplexesUniqueQ2MutationCandidate.stream().map(String::valueOf).
					collect(Collectors.joining("_")));
		}

		if (oneSampleNoWt) {
			result.append("|");
		}

		if (noWt) {
			result.append("_");
		}

		return result.toString();
	}
}
