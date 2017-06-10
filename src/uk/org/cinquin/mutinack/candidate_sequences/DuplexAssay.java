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

package uk.org.cinquin.mutinack.candidate_sequences;

/**
 * List of assays used to evaluate mutation candidate quality.
 * See also {@link uk.org.cinquin.mutinack.candidate_sequences.PositionAssay}
 * @author olivier
 *
 */
public enum DuplexAssay implements AssayInfo {

	N_READS_PER_STRAND,

	TOTAL_N_READS_Q2,

	/**
	 * Also used for Q2 raw disagreements
	 */
	N_READS_WRONG_PAIR,

	/**
	 * Also used for Q2 raw disagreements
	 */
	N_STRAND_READS_ABOVE_Q2_PHRED,

	/**
	 * Also used for Q2 raw disagreements
	 */
	AVERAGE_N_CLIPPED,

	/**
	 * Also used for Q2 raw disagreements
	 */
	MAPPING_QUALITY,

	CONSENSUS_THRESHOLDS_1,

	/**
	 * Also used for Q2 raw disagreements
	 */
	INSERT_SIZE,

	/**
	 * Also used for Q2 raw disagreements
	 */
	CLOSE_TO_LIG,

	CONSENSUS_Q1,

	CONSENSUS_Q0,

	MISSING_STRAND,

	DISAGREEMENT,

	/**
	 * Used to downgrade duplex quality based on {@link PositionAssay}
	 */
	QUALITY_AT_POSITION;

	private final boolean isMinGroup;

	DuplexAssay() {
		this.isMinGroup = true;
	}

	@Override
	public boolean isMinGroup() {
		return isMinGroup;
	}

	@Override
	public boolean isMaxGroup() {
		return false;
	}

	public static boolean hasMaxGroup() {
		return false;
	}

}
