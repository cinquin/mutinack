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

/**
 * List of assays used to evaluate mutation candidate quality.
 * @author olivier
 *
 */
public enum Assay {

	N_READS_PER_STRAND,
	TOTAL_N_READS_Q2,
	N_STRANDS_DISAGREEMENT,//Used to mark disagreements that are supported by too few strands to reach Q2
	N_READS_WRONG_PAIR,//Also used for Q2 raw disagreements
	N_STRAND_READS_ABOVE_Q2_PHRED,//Also used for Q2 raw disagreements
	AVERAGE_N_CLIPPED,//Also used for Q2 raw disagreements
	TOP_STRAND_MAP_Q2,//Also used for Q2 raw disagreements
	BOTTOM_STRAND_MAP_Q2,//Also used for Q2 raw disagreements
	CONSENSUS_THRESHOLDS_1,
	INSERT_SIZE,//Also used for Q2 raw disagreements
	CLOSE_TO_LIG,//Also used for Q2 raw disagreements
	CONSENSUS_Q1,
	CONSENSUS_Q0,
	MISSING_STRAND,
	DISAGREEMENT,
	MAX_AVERAGE_CLIPPING_ALL_COVERING_DUPLEXES,//Also used for Q2 raw disagreements
	NO_DUPLEXES,
	MAX_Q_FOR_ALL_DUPLEXES,
	MAX_DPLX_Q_IGNORING_DISAG,
	MIN_DUPLEXES_SISTER_SAMPLE,
	PRESENT_IN_SISTER_SAMPLE;

}
