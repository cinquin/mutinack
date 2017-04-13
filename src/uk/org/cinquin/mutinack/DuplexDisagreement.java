package uk.org.cinquin.mutinack;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.qualities.Quality;

@PersistenceCapable
public class DuplexDisagreement extends ComparablePair<Mutation, Mutation> {

	private static final long serialVersionUID = 8966639744689364931L;

	public @Final boolean hasAWtStrand;
	public @Persistent Quality quality;

	//Not taken into account for equality
	public double probCollision;

	public DuplexDisagreement(@NonNull Mutation first, @NonNull Mutation second,
			boolean hasAWtStrand, Quality quality) {
		super(first, second);
		this.hasAWtStrand = hasAWtStrand;
		Assert.isTrue(quality.atLeast(Quality.DUBIOUS));
		this.quality = quality;
	}

}
