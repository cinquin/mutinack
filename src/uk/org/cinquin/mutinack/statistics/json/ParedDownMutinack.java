package uk.org.cinquin.mutinack.statistics.json;

import java.io.Serializable;
import java.util.List;

import uk.org.cinquin.mutinack.AnalysisStats;
import uk.org.cinquin.mutinack.Mutinack;

public class ParedDownMutinack implements Serializable {
	private static final long serialVersionUID = -3034113898428992850L;
	
	public ParedDownMutinack(Mutinack a) {
		name = a.name;
		stats = a.stats;
	}
	public String name;
	public List<AnalysisStats> stats;
}
