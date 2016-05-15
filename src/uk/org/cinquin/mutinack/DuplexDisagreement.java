package uk.org.cinquin.mutinack;

import uk.org.cinquin.mutinack.misc_util.ComparablePair;

public class DuplexDisagreement extends ComparablePair<Mutation, Mutation> {

	private static final long serialVersionUID = 8966639744689364931L;

	public final boolean hasAWtStrand;
	
	public DuplexDisagreement(Mutation first, Mutation second, boolean hasAWtStrand) {
		super(first, second);
		this.hasAWtStrand = hasAWtStrand;
	}

}
