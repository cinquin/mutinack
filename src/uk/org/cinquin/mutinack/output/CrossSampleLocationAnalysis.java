package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.SequenceLocation;

@PersistenceCapable
public class CrossSampleLocationAnalysis implements Serializable {
	private static final long serialVersionUID = 8408952788062841827L;

	public @Final SequenceLocation location;
	public boolean randomlySelected;
	public boolean lowTopAlleleFreq;
	public int candidateCount;
	public boolean twoOrMoreSamplesWithSameQ2MutationCandidate;
	public boolean oneSampleNoWt;
	public boolean noWt;

	@Final
	public List<Integer> nDuplexesUniqueQ2MutationCandidate = new ArrayList<>();

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

		if (!nDuplexesUniqueQ2MutationCandidate.isEmpty()) {
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
