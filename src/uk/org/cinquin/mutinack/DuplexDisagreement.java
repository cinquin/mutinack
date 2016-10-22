package uk.org.cinquin.mutinack;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.ComparablePair;

public class DuplexDisagreement extends ComparablePair<Mutation, Mutation> {

	private static final long serialVersionUID = 8966639744689364931L;

	public final boolean hasAWtStrand;
	
	//Not taken into account for equality
	public double probCollision;

	public DuplexDisagreement(@NonNull Mutation first, @NonNull Mutation second,
			boolean hasAWtStrand) {
		super(first, second);
		this.hasAWtStrand = hasAWtStrand;
	}

}
