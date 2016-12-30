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

package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.AnalysisStats;
import uk.org.cinquin.mutinack.Mutinack;

@PersistenceCapable
public class ParedDownMutinack implements Serializable {
	private static final long serialVersionUID = -3034113898428992850L;

	public @Final @Persistent String name;
	public @Final @Persistent List<AnalysisStats> stats;
	public @Final @Persistent Date startDate, endDate;
	public @Final @Persistent String runBatch;
	public @Final @Persistent String runName;

	public ParedDownMutinack(Mutinack a, Date startDate, Date endDate, String runBatch, String runName) {
		name = a.name;
		stats = a.stats;
		this.startDate = startDate;
		this.endDate = endDate;
		this.runBatch = runBatch;
		this.runName = runName;
	}
}
