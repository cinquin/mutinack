package uk.org.cinquin.mutinack.candidate_sequences;

/**
 * See also {@link uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay}
 * @author olivier
 *
 */
public enum PositionAssay implements AssayInfo {

	MEDIAN_PHRED_AT_POS,

	FRACTION_WRONG_PAIRS_AT_POS,

	/**
	 * Also used for Q2 raw disagreements
	 */
	MAX_AVERAGE_CLIPPING_OF_DUPLEX_AT_POS,

	TOO_HIGH_COVERAGE,

	INSERT_SIZE,

	NO_DUPLEXES,

	MAX_Q_FOR_ALL_DUPLEXES(false, true),

	N_Q1_DUPLEXES(false, true),

	/**
	 * Redundant with MAX_Q_FOR_ALL_DUPLEXES for quality computation, but used to keep a record
	 * of what duplex quality would have been if it had not been for disagreements.
	 * Currently not computed (see {@link #COMPUTE_MAX_DPLX_Q_IGNORING_DISAG}).
	 */
	MAX_DPLX_Q_IGNORING_DISAG(false, false),

	/**
	 * Depending on settings and on whether disagreement is Q2, may be set to {@link Quality.GOOD},
	 * which does not downgrade quality
	 */
	AT_LEAST_ONE_DISAG(false, false),

	/**
	 * Used to mark disagreements that are supported by too few strands to reach Q2
	 */
	DISAG_THAT_MISSED_Q2(false, false),

	MIN_DUPLEXES_SISTER_SAMPLE,

	PRESENT_IN_SISTER_SAMPLE;

	public final static boolean COMPUTE_MAX_DPLX_Q_IGNORING_DISAG = false;

	private final boolean isMinGroup, isMaxGroup;

	PositionAssay(boolean isMinGroup, boolean isMaxGroup) {
		this.isMinGroup = isMinGroup;
		this.isMaxGroup = isMaxGroup;
	}

	PositionAssay() {
		this.isMinGroup = true;
		this.isMaxGroup = false;
	}

	@Override
	public boolean isMinGroup() {
		return isMinGroup;
	}

	@Override
	public boolean isMaxGroup() {
		return isMaxGroup;
	}

	public static boolean hasMaxGroup() {
		return true;
	}
}
