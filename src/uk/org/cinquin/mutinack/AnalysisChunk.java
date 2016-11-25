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
package uk.org.cinquin.mutinack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.SettableInteger;

public class AnalysisChunk {
	int contig;
	@NonNull final String contigName;
	int startAtPosition;
	int terminateAtPosition;
	SettableInteger pauseAtPosition;
	SettableInteger lastProcessedPosition;
	Phaser phaser;
	final List<@NonNull SubAnalyzer> subAnalyzers = new ArrayList<>();
	public MutinackGroup groupSettings;
	public final int nParameterSets;

	public AnalysisChunk(@NonNull String contigName, int nParameterSets) {
		this.contigName = contigName;
		this.nParameterSets = nParameterSets;
	}

	@Override
	public String toString() {
		return new SequenceLocation(contig, contigName, startAtPosition) + " -> " +
			new SequenceLocation(contig, contigName, terminateAtPosition);
	}
}
