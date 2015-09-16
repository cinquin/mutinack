/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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

import uk.org.cinquin.mutinack.misc_util.SettableInteger;

public class AnalysisChunk {
	int contig;
	int startAtPosition;
	int terminateAtPosition;
	SettableInteger pauseAtPosition = new SettableInteger();
	SettableInteger lastProcessedPosition = new SettableInteger();
	Phaser phaser;
	final List<SubAnalyzer> subAnalyzers = new ArrayList<>();

	@Override
	public String toString() {
		return new SequenceLocation(contig, startAtPosition) + " -> " +
			new SequenceLocation(contig, terminateAtPosition);
	}
}
