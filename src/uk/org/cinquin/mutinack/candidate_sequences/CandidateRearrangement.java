/**
 * Mutinack mutation detection program.
 * Copyright (C) 2017 Olivier Cinquin
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

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.qualities.DetailedQualities;
import uk.org.cinquin.mutinack.qualities.Quality;

/**
 *
 * @author olivier
 * TODO Should take location into account for equality?
 *
 */
@SuppressWarnings("EqualsAndHashcode")
public final class CandidateRearrangement extends CandidateSequence implements Serializable {

	private static final long serialVersionUID = 1L;
	private final @NonNull SequenceLocation joinLocation;
	private final boolean strandSignSwitch;
	private int minMapQ;
	private final RearrangementType putativeKind;
	private final int sizeCoveredByRearrangement;
	private final String cigar1, cigar2;

	public CandidateRearrangement(
			@NonNull SubAnalyzer owningSubAnalyzer,
			@NonNull SequenceLocation location,
			@NonNull ExtendedSAMRecord initialConcurringRead,
			int initialLigationSiteD,
			@NonNull SequenceLocation joinLocation,
			@NonNull RearrangementType putativeKind,
			int sizeCoveredByRearrangement,
			boolean strandSignSwitch,
			int minMapQ,
			String cigar1,
			String cigar2) {
		super(owningSubAnalyzer, MutationType.REARRANGEMENT, null, location, initialConcurringRead,
			initialLigationSiteD);
		Objects.requireNonNull(putativeKind, "Putative type cannot be null");
		this.joinLocation = joinLocation;
		this.strandSignSwitch = strandSignSwitch;
		this.minMapQ = minMapQ;
		this.putativeKind = putativeKind;
		this.sizeCoveredByRearrangement = sizeCoveredByRearrangement;
		this.cigar1 = cigar1;
		this.cigar2 = cigar2;
	}

	public int getMinMapQ() {
		return minMapQ;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof CandidateRearrangement)) {
			return false;
		}
		CandidateRearrangement cast = (CandidateRearrangement) obj;
		return (strandSignSwitch == cast.strandSignSwitch) && joinLocation.equals(cast.joinLocation) &&
				putativeKind == cast.putativeKind;
	}

	@Override
	public String getChange() {
		return "--> " + (strandSignSwitch ? "-" : "+") + joinLocation + " (" + putativeKind + ')' +
				" sizeCoveredByRearrangement=" + sizeCoveredByRearrangement + "; cigar1=" + cigar1 +
				"; cigar2=" + cigar2;
	}

	@Override
	public String toString() {
		return getChange()
				 + " at " + getLocation() + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
	}

	@Override
	public void mergeWith(@NonNull CandidateSequenceI candidate, Parameters param) {
		super.mergeWith(candidate, param);
		CandidateRearrangement r = (CandidateRearrangement) candidate;
		minMapQ = Math.max(minMapQ, r.getMinMapQ());
	}

	private static final Set<@NonNull DuplexAssay> ASSAYS_TO_IGNORE_FOR_REARRANGEMENT_DUPLEXES =
		Collections.unmodifiableSet(EnumSet.of(
			DuplexAssay.MISSING_STRAND, DuplexAssay.N_READS_WRONG_PAIR, DuplexAssay.AVERAGE_N_CLIPPED,
			DuplexAssay.INSERT_SIZE));

	@Override
	protected Comparator<Duplex> getDuplexQualityComparator() {
		return duplexQualitycomparator;
	}

	private static final Comparator<Duplex> duplexQualitycomparator =
		Comparator.comparing((Duplex dr) -> CandidateRearrangement.staticFilterQuality(dr.localAndGlobalQuality)).
		thenComparing(Comparator.comparing((Duplex dr) -> dr.allRecords.size())).
		thenComparing(Duplex::getUnclippedAlignmentStart);

	public static @NonNull Quality staticFilterQuality(DetailedQualities<DuplexAssay> localAndGlobalQuality) {
		return Objects.requireNonNull(
			localAndGlobalQuality.getValueIgnoring(ASSAYS_TO_IGNORE_FOR_REARRANGEMENT_DUPLEXES));
	}

	@Override
	public @NonNull Quality filterQuality(DetailedQualities<DuplexAssay> localAndGlobalQuality) {
		return CandidateRearrangement.staticFilterQuality(localAndGlobalQuality);
	}

	private static final Set<DuplexAssay> ASSAYS_TO_IGNORE_INCLUDING_DUPLEX_NSTRANDS;
	static {
		EnumSet<@NonNull DuplexAssay> tempSet = EnumSet.noneOf(DuplexAssay.class);
		tempSet.addAll(ASSAYS_TO_IGNORE_FOR_REARRANGEMENT_DUPLEXES);
		tempSet.addAll(SubAnalyzer.ASSAYS_TO_IGNORE_FOR_DUPLEX_NSTRANDS);
		ASSAYS_TO_IGNORE_INCLUDING_DUPLEX_NSTRANDS = Collections.unmodifiableSet(tempSet);
	}

	@Override
	protected Set<DuplexAssay> assaysToIgnoreIncludingDuplexNStrands() {
		return ASSAYS_TO_IGNORE_INCLUDING_DUPLEX_NSTRANDS;
	}

	@Override
	protected boolean concurringDuplexReadClippingOK(ExtendedSAMRecord r, Parameters param) {
		return true;
	}

	@Override
	public void updateQualities(@NonNull Parameters param) {
		getQuality().addUnique(PositionAssay.SPLIT_READ_MIN_MAPQ, minMapQ >= param.minMappingQualityQ2 ? Quality.GOOD : Quality.DUBIOUS);
	}
}
