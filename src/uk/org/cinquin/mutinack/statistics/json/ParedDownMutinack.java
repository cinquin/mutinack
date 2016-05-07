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
