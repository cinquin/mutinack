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
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import gnu.trove.TByteCollection;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.DuplexRead;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.collections.SingletonObjectIntMap;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.output.LocationExaminationResults;
import uk.org.cinquin.mutinack.output.json.ByteArrayStringSerializer;
import uk.org.cinquin.mutinack.output.json.ByteStringSerializer;
import uk.org.cinquin.mutinack.output.json.TByteArrayListSerializer;
import uk.org.cinquin.mutinack.qualities.DetailedDuplexQualities;
import uk.org.cinquin.mutinack.qualities.DetailedPositionQualities;
import uk.org.cinquin.mutinack.qualities.DetailedQualities;
import uk.org.cinquin.mutinack.qualities.Quality;


/**
 * Equality is computed solely based on the mutation type and sequence, and in particular
 * not on the list of reads that support the candidate sequence.
 * Note that many methods in this class are *not* thread-safe.
 * @author olivier
 *
 */
@PersistenceCapable
public class CandidateSequence implements CandidateSequenceI, Serializable {

	private static final long serialVersionUID = 8222086925028013360L;

	@JsonIgnore
	private transient @Nullable Collection<ComparablePair<String, String>> rawMismatchesQ2,
		rawDeletionsQ2, rawInsertionsQ2;
	@Persistent private DetailedQualities<PositionAssay> quality;
	private transient TObjectLongHashMap<DuplexRead> issues;
	private @Nullable StringBuilder supplementalMessage;
	private transient TObjectIntHashMap<ExtendedSAMRecord> concurringReads;
	private transient @Nullable MutableSet<@NonNull DuplexRead> duplexes;
	private int nGoodDuplexes;
	private int nGoodDuplexesIgnoringDisag;
	private int nGoodOrDubiousDuplexes;
	private int nDuplexes;
	private int totalGoodDuplexes;
	private int totalGoodOrDubiousDuplexes;
	private int totalAllDuplexes;
	private int totalReadsAtPosition;
	private int averageMappingQuality = -1;
	private int minInsertSize = -1;
	private int maxInsertSize = -1;
	private int insertSizeAtPos10thP = -1;
	private int insertSizeAtPos90thP = -1;
	private int nWrongPairs;
	private byte singleBasePhredScore = -1;
	@Persistent @JsonSerialize(using = TByteArrayListSerializer.class)
		private TByteArrayList phredScores;
	@Persistent private Serializable preexistingDetection;
	private byte medianPhredAtPosition;
	private int minDistanceToLigSite = Integer.MAX_VALUE;
	private int maxDistanceToLigSite = Integer.MIN_VALUE;
	private float meanDistanceToLigSite = Float.NaN;
	private int nDistancesToLigSite = 0;
	private float probCollision = Float.NaN;
	private int nDuplexesSisterArm = -1;
	private int nMatchingCandidatesOtherSamples = -1;
	private int smallestDuplexAlignmentOffset = -1;

	private int negativeStrandCount = 0, positiveStrandCount = 0;

	@Final @Persistent private @NonNull MutationType mutationType;
	@Final @Persistent @JsonSerialize(using = ByteArrayStringSerializer.class)
		private byte @Nullable[] sequence;
	@JsonIgnore private final int hashCode;
	@JsonSerialize(using = ByteStringSerializer.class)
		private byte wildtypeSequence;
	private final transient @NonNull ExtendedSAMRecord initialConcurringRead;
	@Final private int initialLigationSiteD;
	@Final @Persistent private @NonNull SequenceLocation location;
	private final transient SubAnalyzer owningSubAnalyzer;
	@Final private String sampleName;
	private boolean hidden = false;
	private Boolean negativeCodingStrand;

	@JsonIgnore private transient Mutation mutation;

	//For debugging purposes
	private int insertSize = -1;
	private int positionInRead = -1;
	private int readEL = -1;
	@Nullable private String readName;
	private int readAlignmentStart = -1;
	private int mateReadAlignmentStart = -1;
	private int readAlignmentEnd = -1;
	private int mateReadAlignmentEnd = -1;
	private int refPositionOfMateLigationSite = 1;

	@Override
	public void resetLigSiteDistances() {
		setMinDistanceToLigSite(Integer.MAX_VALUE);
		setMaxDistanceToLigSite(Integer.MIN_VALUE);
		setMeanDistanceToLigSite(Float.NaN);
		setnDistancesToLigSite(0);
		setSmallestDuplexAlignmentOffset(-1);
	}

	@Override
	public void acceptLigSiteDistance(int distance) {
		if (distance < getMinDistanceToLigSite()) {
			setMinDistanceToLigSite(distance);
		}
		if (distance > getMaxDistanceToLigSite()) {
			setMaxDistanceToLigSite(distance);
		}
		if (getnDistancesToLigSite() == 0) {
			setMeanDistanceToLigSite(distance);
		} else {
			setMeanDistanceToLigSite((getMeanDistanceToLigSite() * getnDistancesToLigSite() + distance) /
				(getnDistancesToLigSite() + 1));
		}
		setnDistancesToLigSite(getnDistancesToLigSite() + 1);
	}

	@Override
	public boolean isHidden() {
		return hidden;
	}

	@Override
	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public byte getMedianPhredAtPosition() {
		return medianPhredAtPosition;
	}

	@Override
	public void setMedianPhredAtPosition(byte medianPhredAtPosition) {
		this.medianPhredAtPosition = medianPhredAtPosition;
	}

	@Override
	public void setProbCollision(float probCollision) {
		this.probCollision = probCollision;
	}

	public float getProbCollision() {
		return probCollision;
	}

	@SuppressWarnings("null")
	public CandidateSequence(String sampleName, @NonNull MutationType mutationType,
			byte @Nullable[] sequence) {
		Assert.isFalse(mutationType == MutationType.UNKNOWN);
		this.mutationType = Objects.requireNonNull(mutationType);
		this.sequence = sequence;
		this.sampleName = sampleName;
		this.owningSubAnalyzer = null;
		this.location = null;
		this.initialLigationSiteD = -1;
		this.initialConcurringRead = null;
		hashCode = computeHashCode();
	}

	@SuppressWarnings("null")
	public CandidateSequence(
			@NonNull SubAnalyzer owningSubAnalyzer,
			@NonNull MutationType mutationType,
			byte @Nullable[] sequence,
			@NonNull SequenceLocation location,
			@NonNull ExtendedSAMRecord initialConcurringRead,
			int initialLigationSiteD) {
		this.owningSubAnalyzer = owningSubAnalyzer;
		this.sampleName = owningSubAnalyzer == null ? null : owningSubAnalyzer.analyzer.name;
		Assert.isFalse(mutationType == MutationType.UNKNOWN);
		this.mutationType = Objects.requireNonNull(mutationType);
		this.sequence = sequence;
		this.location = location;
		this.initialConcurringRead = initialConcurringRead;
		this.initialLigationSiteD = initialLigationSiteD;
		hashCode = computeHashCode();
	}

	@Override
	public String toString() {
		String result = getMutationType().toString();
		switch(getMutationType()) {
			case WILDTYPE:
				break;
			case DELETION:
				throw new AssertionFailedException();
			case INSERTION:
				break;
			case SUBSTITUTION:
				result += ": " + new String(new byte[] {getWildtypeSequence()}) +
				"->" + new String(getSequence());
				break;
			default:
				throw new AssertionFailedException();
		}
		result += " at " + getLocation() + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
		return result;
	}

	@Override
	public final int hashCode() {
		return hashCode;
	}

	private int computeHashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getMutationType().hashCode();
		result = prime * result + Arrays.hashCode(getSequence());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		CandidateSequence other = (CandidateSequence) obj;
		/*if (obj instanceof CandidateDeletion) {
			return false;
		}*/
		if (getMutationType() != other.getMutationType()) {
			return false;
		}
		if (!Arrays.equals(getSequence(), other.getSequence())) {
			return false;
		}
		return true;
	}

	@Override
	public String getChange() {
		final String result;
		switch (getMutationType()) {
			case DELETION:
				result = "-" + new String(getSequence()) + "-";
				break;
			case INTRON:
				result = "-INTRON-";
				break;
			case INSERTION:
				result = "^" + new String(getSequence()) + "^";
				break;
			case SUBSTITUTION:
				if (Boolean.TRUE.equals(getNegativeCodingStrand())) {
					@NonNull Mutation rc = getMutation().reverseComplement();
					result = new String(new byte[] {rc.wildtype}) +
						"->" + new String(rc.mutationSequence);
				} else {
					result = new String(new byte[] {getWildtypeSequence()}) +
						"->" + new String(getSequence());
				}
				break;
			case WILDTYPE:
				result = "wt";
				break;
			default:
				throw new AssertionFailedException();
		}
		return result;
	}

	@Override
	public int getMaxInsertSize() {
		return maxInsertSize;
	}

	@Override
	public void setMaxInsertSize(int maxInsertSize) {
		if (maxInsertSize < 0) {
			throw new IllegalArgumentException("Negative insert size: " + minInsertSize);
		}
		this.maxInsertSize = maxInsertSize;
	}

	@Override
	public int getMinInsertSize() {
		return minInsertSize;
	}

	@Override
	public void setMinInsertSize(int minInsertSize) {
		if (minInsertSize < 0) {
			throw new IllegalArgumentException("Negative insert size: " + minInsertSize);
		}
		this.minInsertSize = minInsertSize;
	}

	@Override
	public int getAverageMappingQuality() {
		return averageMappingQuality;
	}

	@Override
	public void setAverageMappingQuality(int averageMappingQuality) {
		this.averageMappingQuality = averageMappingQuality;
	}

	@Override
	public Mutinack getOwningAnalyzer() {
		return getOwningSubAnalyzer().analyzer;
	}

	@Override
	public SubAnalyzer getOwningSubAnalyzer() {
		return owningSubAnalyzer;
	}

	@Override
	public @NonNull SequenceLocation getLocation() {
		return location;
	}

	@Override
	public int getTotalReadsAtPosition() {
		return totalReadsAtPosition;
	}

	@Override
	public void setTotalReadsAtPosition(int totalReadsAtPosition) {
		this.totalReadsAtPosition = totalReadsAtPosition;
	}

	@Override
	public int getTotalAllDuplexes() {
		return totalAllDuplexes;
	}

	@Override
	public void setTotalAllDuplexes(int totalAllDuplexes) {
		this.totalAllDuplexes = totalAllDuplexes;
	}

	@Override
	public int getTotalGoodOrDubiousDuplexes() {
		return totalGoodOrDubiousDuplexes;
	}

	@Override
	public void setTotalGoodOrDubiousDuplexes(int totalGoodOrDubiousDuplexes) {
		this.totalGoodOrDubiousDuplexes = totalGoodOrDubiousDuplexes;
	}

	@Override
	public int getTotalGoodDuplexes() {
		return totalGoodDuplexes;
	}

	@Override
	public void setTotalGoodDuplexes(int totalGoodDuplexes) {
		this.totalGoodDuplexes = totalGoodDuplexes;
	}

	@Override
	public int getnDuplexes() {
		return nDuplexes;
	}

	@Override
	public void setnDuplexes(int nDuplexes) {
		this.nDuplexes = nDuplexes;
	}

	@Override
	public int getnGoodOrDubiousDuplexes() {
		return nGoodOrDubiousDuplexes;
	}

	@Override
	public void setnGoodOrDubiousDuplexes(int nGoodOrDubiousDuplexes) {
		this.nGoodOrDubiousDuplexes = nGoodOrDubiousDuplexes;
	}

	@Override
	public int getnGoodDuplexes() {
		return nGoodDuplexes;
	}

	@Override
	public void setnGoodDuplexes(int nGoodDuplexes) {
		this.nGoodDuplexes = nGoodDuplexes;
	}

	@SuppressWarnings("null")
	@Override
	public @NonNull MutableSet<@NonNull DuplexRead> getDuplexes() {
		if (duplexes == null) {
			 duplexes = Sets.mutable.empty();
		}
		return duplexes;
	}

	@Override
	public void setDuplexes(@NonNull MutableSet<@NonNull DuplexRead> duplexes) {
		this.duplexes = duplexes;
	}

	public static final int NO_ENTRY_VALUE = SingletonObjectIntMap.NO_ENTRY_VALUE;

	@SuppressWarnings("null")
	@Override
	public @NonNull TObjectIntHashMap<ExtendedSAMRecord> getMutableConcurringReads() {
		if (concurringReads == null) {
			concurringReads = new TObjectIntHashMap<>(100, 0.5f, NO_ENTRY_VALUE);
			if (initialConcurringRead != null) {
				concurringReads.put(initialConcurringRead, initialLigationSiteD);
			}
		}
		return concurringReads;
	}

	private transient @Nullable TObjectIntMap<ExtendedSAMRecord> singletonConcurringRead;

	@SuppressWarnings("null")
	@Override
	public @NonNull TObjectIntMap<ExtendedSAMRecord> getNonMutableConcurringReads() {
		if (concurringReads != null) {
			return concurringReads;
		} else {
			if (singletonConcurringRead == null) {
				singletonConcurringRead =
					new SingletonObjectIntMap<>(initialConcurringRead, initialLigationSiteD);
			}
			return singletonConcurringRead;
		}
	}

	public void forEachConcurringRead(Consumer<ExtendedSAMRecord> consumer) {
		getNonMutableConcurringReads().forEachKey(er -> {consumer.accept(er); return true;});
	}

	public IntSummaryStatistics getConcurringReadSummaryStatistics(ToIntFunction<ExtendedSAMRecord> stat) {
		IntSummaryStatistics stats = new IntSummaryStatistics();
		forEachConcurringRead(er -> stats.accept(stat.applyAsInt(er)));
		return stats;
	}

	private transient @Nullable TObjectIntHashMap<ExtendedSAMRecord> originalConcurringReads;

	@Override
	public int removeConcurringRead(@NonNull ExtendedSAMRecord er) {
		if (originalConcurringReads == null) {
			@NonNull TObjectIntMap<ExtendedSAMRecord> mutable = getMutableConcurringReads();
			originalConcurringReads = new TObjectIntHashMap<>(mutable.size(), 0.5f, NO_ENTRY_VALUE);
			originalConcurringReads.putAll(mutable);
		}
		return getMutableConcurringReads().remove(er);
	}

	@Override
	public void restoreConcurringReads() {
		if (originalConcurringReads != null) {
			concurringReads = originalConcurringReads;
			originalConcurringReads = null;
		}
	}

	@Override
	public @Nullable StringBuilder getSupplementalMessage() {
		return supplementalMessage;
	}

	@Override
	public void setSupplementalMessage(StringBuilder supplementalMessage) {
		this.supplementalMessage = supplementalMessage;
	}

	@SuppressWarnings("null")
	@Override
	public @NonNull DetailedQualities<PositionAssay> getQuality() {
		if (quality == null) {
			quality = new DetailedPositionQualities();
		}
		return quality;
	}

	@Override
	public int getMaxDistanceToLigSite() {
		return maxDistanceToLigSite;
	}

	@Override
	public int getMinDistanceToLigSite() {
		return minDistanceToLigSite;
	}

	@Override
	public float getMeanDistanceToLigSite() {
		return meanDistanceToLigSite;
	}

	@Override
	public byte getWildtypeSequence() {
		return wildtypeSequence;
	}

	@Override
	public void setWildtypeSequence(byte wildtypeSequence) {
		this.wildtypeSequence = wildtypeSequence;
	}

	@Override
	public byte @Nullable[] getSequence() {
		return sequence;
	}

	@Override
	public @NonNull MutationType getMutationType() {
		Assert.isFalse(mutationType == MutationType.UNKNOWN);
		return mutationType;
	}

	@Override
	public @NonNull String getKind() {
		return getMutationType().toString();
	}

	@Override
	public void addBasePhredScore(byte q) {
		Assert.isFalse(q < 0, "Negative Phred quality score: %s"/*, q*/);
		if (phredScores == null && singleBasePhredScore == -1) {
			singleBasePhredScore = q;
		} else {
			allocateBasePhredQualityArray();
			phredScores.add(q);
		}
	}

	private void allocateBasePhredQualityArray() {
		if (phredScores != null) {
			return;
		}
		Assert.isFalse(singleBasePhredScore == -2);
		phredScores = new TByteArrayList(1_000);
		if (singleBasePhredScore != -1) {
			phredScores.add(singleBasePhredScore);
		}
		singleBasePhredScore = -2;
	}

	@SuppressWarnings("null")
	private @NonNull TByteArrayList getPhredScores() {
		allocateBasePhredQualityArray();
		return phredScores;
	}

	@Override
	public byte getMedianPhredScore() {
		if (phredScores == null) {
			if (singleBasePhredScore == -2) {
				throw new AssertionFailedException();
			} else if (singleBasePhredScore == -1) {
				return -1;
			} else {
				return singleBasePhredScore;
			}
		}
		final int nScores = phredScores.size();
		if (nScores == 0) {
			return -1;
		}
		phredScores.sort();
		return phredScores.get(nScores / 2);
	}

	@Override
	public void addPhredScoresToList(@NonNull TByteCollection ql) {
		if (phredScores == null) {
			if (singleBasePhredScore == -1) {
				return;
			} else if (singleBasePhredScore < 0) {
				throw new AssertionFailedException();
			} else {
				ql.add(singleBasePhredScore);
			}
		} else {
			ql.addAll(phredScores);
		}
	}

	@Override
	public int getnWrongPairs() {
		return nWrongPairs;
	}

	@Override
	public void setnWrongPairs(int nWrongPairs) {
		this.nWrongPairs = nWrongPairs;
	}

	@Override
	public int getnGoodDuplexesIgnoringDisag() {
		return nGoodDuplexesIgnoringDisag;
	}

	@Override
	public void setnGoodDuplexesIgnoringDisag(int nGoodDuplexesIgnoringDisag) {
		this.nGoodDuplexesIgnoringDisag = nGoodDuplexesIgnoringDisag;
	}

	public boolean containsType(Class<? extends CandidateSequence> class1) {
		return class1.isInstance(this);
	}

	@Override
	public void mergeWith(@NonNull CandidateSequenceI candidate) {
		Assert.isTrue(this.getClass().isInstance(candidate), "Cannot merge %s to %s %s"/*,
				this, candidate, candidate.getNonMutableConcurringReads()*/);
		final Boolean ncs = getNegativeCodingStrand();
		final Boolean ncsOther = candidate.getNegativeCodingStrand();
		if (ncs != null && ncsOther != null && !ncs.equals(ncsOther)) {
			throw new IllegalArgumentException("At location " + location + ", candidates " + this +
				" and " + candidate +
				"disagree on template strand orientation: " + ncs + " vs " + ncsOther);
		}
		if (ncs == null) {
			setNegativeCodingStrand(ncsOther);
		}
		getMutableConcurringReads().putAll(candidate.getNonMutableConcurringReads());
		candidate.addPhredScoresToList(getPhredScores());
		acceptLigSiteDistance(candidate.getMinDistanceToLigSite());
		acceptLigSiteDistance(candidate.getMaxDistanceToLigSite());
		incrementNegativeStrandCount(candidate.getNegativeStrandCount());
		incrementPositiveStrandCount(candidate.getPositiveStrandCount());
		if (!candidate.getRawMismatchesQ2().isEmpty()) {
			getMutableRawMismatchesQ2().addAll(candidate.getRawMismatchesQ2()); //TODO Is it
			//worth optimizing this out if not keeping track of raw disagreements? That would
			//save one list allocation per position
		}
		if (!candidate.getRawDeletionsQ2().isEmpty()) {
			getMutableRawDeletionsQ2().addAll(candidate.getRawDeletionsQ2());
		}
		if (!candidate.getRawInsertionsQ2().isEmpty()) {
			getMutableRawInsertionsQ2().addAll(candidate.getRawInsertionsQ2());
		}
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull TObjectLongHashMap<DuplexRead> getIssues() {
		if (issues == null) {
			issues = new TObjectLongHashMap<>();
		}
		return issues;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getMutableRawMismatchesQ2() {
		if (rawMismatchesQ2 == null) {
			rawMismatchesQ2 = new ArrayList<>();
		}
		return rawMismatchesQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getRawMismatchesQ2() {
		if (rawMismatchesQ2 == null) {
			return Collections.emptyList();
		}
		return rawMismatchesQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getMutableRawDeletionsQ2() {
		if (rawDeletionsQ2 == null) {
			rawDeletionsQ2 = new ArrayList<>();
		}
		return rawDeletionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getRawDeletionsQ2() {
		if (rawDeletionsQ2 == null) {
			return Collections.emptyList();
		}
		return rawDeletionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getMutableRawInsertionsQ2() {
		if (rawInsertionsQ2 == null) {
			rawInsertionsQ2 = new ArrayList<>();
		}
		return rawInsertionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Collection<ComparablePair<String, String>> getRawInsertionsQ2() {
		if (rawInsertionsQ2 == null) {
			return Collections.emptyList();
		}
		return rawInsertionsQ2;
	}

	@Override
	public int getInsertSizeAtPos10thP() {
		return insertSizeAtPos10thP;
	}

	@Override
	public void setInsertSizeAtPos10thP(int insertSizeAtPos10thP) {
		this.insertSizeAtPos10thP = insertSizeAtPos10thP;
	}

	@Override
	public int getInsertSizeAtPos90thP() {
		return insertSizeAtPos90thP;
	}

	@Override
	public void setInsertSizeAtPos90thP(int insertSizeAtPos90thP) {
		this.insertSizeAtPos90thP = insertSizeAtPos90thP;
	}

	@Override
	public String getSampleName() {
		return sampleName;
	}

	@SuppressWarnings("resource")
	@Override
	public @NonNull String toOutputString(Parameters param, LocationExaminationResults examResults) {
		StringBuilder result = new StringBuilder();

		NumberFormat formatter = Util.mediumLengthFloatFormatter.get();
		Stream<String> qualityKD = Arrays.stream(getIssues().values()).mapToObj(
				iss -> DetailedDuplexQualities.fromLong(iss).getQualities().
				filter(entry -> entry.getValue().lowerThan(Quality.GOOD)).
				map(Object::toString).
				collect(Collectors.joining(",", "{", "}")));
		Assert.isTrue(nDuplexesSisterArm > -1);
		//TODO The following issues added below to qualityKD should just be retrieved from the
		//qualities field instead of being recomputed
		if (nDuplexesSisterArm < param.minNumberDuplexesSisterSamples) {
			qualityKD = Stream.concat(Stream.of(PositionAssay.MIN_DUPLEXES_SISTER_SAMPLE.toString()),
				qualityKD);
		}
		final Quality disagQ = getQuality().getQuality(PositionAssay.AT_LEAST_ONE_DISAG);
		if (disagQ != null) {
			qualityKD = Stream.concat(Stream.of(DuplexAssay.DISAGREEMENT + "<=" + disagQ),
				qualityKD);
		}
		if (getQuality().qualitiesContain(PositionAssay.DISAG_THAT_MISSED_Q2)) {
			qualityKD = Stream.concat(Stream.of(PositionAssay.DISAG_THAT_MISSED_Q2.toString()),
				qualityKD);
		}
		if (getnMatchingCandidatesOtherSamples() > 1) {
			qualityKD = Stream.concat(Stream.of(PositionAssay.PRESENT_IN_SISTER_SAMPLE.toString()),
				qualityKD);//TODO This is redundant now
		}

		String qualityKDString = qualityKD.collect(Collectors.joining(","));
		/**
		 * Make sure columns stay in sync with Mutinack.outputHeader
		 */
		result.append(getnGoodDuplexes() + "\t" +
			getnGoodOrDubiousDuplexes() + "\t" +
			getnDuplexes() + "\t" +
			getNonMutableConcurringReads().size() + "\t" +
			formatter.format((getnGoodDuplexes() / ((float) getTotalGoodDuplexes()))) + "\t" +
			formatter.format((getnGoodOrDubiousDuplexes() / ((float) getTotalGoodOrDubiousDuplexes()))) + "\t" +
			formatter.format((getnDuplexes() / ((float) getTotalAllDuplexes()))) + "\t" +
			formatter.format((getNonMutableConcurringReads().size() / ((float) getTotalReadsAtPosition()))) + "\t" +
			(getAverageMappingQuality() == -1 ? "?" : getAverageMappingQuality()) + "\t" +
			nDuplexesSisterArm + "\t" +
			getInsertSize() + "\t" +
			getInsertSizeAtPos10thP() + "\t" +
			getInsertSizeAtPos90thP() + "\t" +
			getMinDistanceToLigSite() + "\t" +
			getMaxDistanceToLigSite() + "\t" +
			Optional.ofNullable(negativeCodingStrand).map(String::valueOf).orElse("?") + '\t' +
			formatter.format(getMeanDistanceToLigSite()) + "\t" +
			formatter.format(getProbCollision()) + "\t" +
			getPositionInRead() + "\t" +
			getReadEL() + "\t" +
			getReadName() + "\t" +
			getReadAlignmentStart()  + "\t" +
			getMateReadAlignmentStart()  + "\t" +
			getReadAlignmentEnd() + "\t" +
			getMateReadAlignmentEnd() + "\t" +
			getRefPositionOfMateLigationSite() + "\t" +
			((param.outputDuplexDetails || param.annotateMutationsInFile != null) ?
					qualityKDString
				:
					"" /*getIssues()*/) + "\t" +
			getMedianPhredAtPosition() + "\t" +
			(getMinInsertSize() == -1 ? "?" : getMinInsertSize()) + "\t" +
			(getMaxInsertSize() == -1 ? "?" : getMaxInsertSize()) + "\t" +
			formatter.format(examResults.alleleFrequencies.get(0)) + "\t" +
			formatter.format(examResults.alleleFrequencies.get(1)) + "\t" +
			getSmallestDuplexAlignmentOffset() + "\t" +
			(getSupplementalMessage() != null ? getSupplementalMessage() : "") + "\t"
			);

		boolean needComma = false;
		final Mutinack analyzer = getOwningAnalyzer();
		for (Entry<String, GenomeFeatureTester> a: analyzer.filtersForCandidateReporting.entrySet()) {
			if (!(a.getValue() instanceof BedComplement) && a.getValue().test(location)) {
				if (needComma) {
					result.append(", ");
				}
				needComma = true;
				Object val = a.getValue().apply(location);
				result.append(a.getKey() + (val != null ? (": " + val) : ""));
			}
		}

		return result.toString();
	}

	@Override
	public int getnMatchingCandidatesOtherSamples() {
		return nMatchingCandidatesOtherSamples;
	}

	@Override
	public void setnMatchingCandidatesOtherSamples(int nMatchingCandidatesOtherSamples) {
		this.nMatchingCandidatesOtherSamples = nMatchingCandidatesOtherSamples;
	}

	@Override
	public Serializable getPreexistingDetection() {
		return preexistingDetection;
	}

	@Override
	public void setPreexistingDetection(Serializable preexistingDetection) {
		this.preexistingDetection = preexistingDetection;
	}

	@Override
	public int getPositiveStrandCount() {
		return positiveStrandCount;
	}

	@Override
	public int getNegativeStrandCount() {
		return negativeStrandCount;
	}

	@Override
	public void incrementPositiveStrandCount(int i) {
		positiveStrandCount += i;
	}

	@Override
	public void incrementNegativeStrandCount(int i) {
		negativeStrandCount += i;
	}

	@Override
	public Mutation getMutation() {
		if (mutation == null) {
			mutation = new Mutation(this);
		}
		return mutation;
	}

	@Override
	public int getnDuplexesSisterArm() {
		return nDuplexesSisterArm;
	}

	@Override
	public void setnDuplexesSisterArm(int nDuplexesSisterArm) {
		this.nDuplexesSisterArm = nDuplexesSisterArm;
	}

	public void setMinDistanceToLigSite(int minDistanceToLigSite) {
		this.minDistanceToLigSite = minDistanceToLigSite;
	}

	public void setMaxDistanceToLigSite(int maxDistanceToLigSite) {
		this.maxDistanceToLigSite = maxDistanceToLigSite;
	}

	public void setMeanDistanceToLigSite(float meanDistanceToLigSite) {
		this.meanDistanceToLigSite = meanDistanceToLigSite;
	}

	public int getnDistancesToLigSite() {
		return nDistancesToLigSite;
	}

	public void setnDistancesToLigSite(int nDistancesToLigSite) {
		this.nDistancesToLigSite = nDistancesToLigSite;
	}

	public int getInsertSize() {
		return insertSize;
	}

	@Override
	public void setInsertSize(int insertSize) {
		this.insertSize = insertSize;
	}

	public int getPositionInRead() {
		return positionInRead;
	}

	@Override
	public void setPositionInRead(int positionInRead) {
		this.positionInRead = positionInRead;
	}

	public int getReadEL() {
		return readEL;
	}

	@Override
	public void setReadEL(int readEL) {
		this.readEL = readEL;
	}

	public String getReadName() {
		return readName;
	}

	@Override
	public void setReadName(@NonNull String readName) {
		this.readName = readName;
	}

	public int getReadAlignmentStart() {
		return readAlignmentStart;
	}

	@Override
	public void setReadAlignmentStart(int readAlignmentStart) {
		this.readAlignmentStart = readAlignmentStart;
	}

	public int getMateReadAlignmentStart() {
		return mateReadAlignmentStart;
	}

	@Override
	public void setMateReadAlignmentStart(int mateReadAlignmentStart) {
		this.mateReadAlignmentStart = mateReadAlignmentStart;
	}

	public int getReadAlignmentEnd() {
		return readAlignmentEnd;
	}

	@Override
	public void setReadAlignmentEnd(int readAlignmentEnd) {
		this.readAlignmentEnd = readAlignmentEnd;
	}

	public int getMateReadAlignmentEnd() {
		return mateReadAlignmentEnd;
	}

	@Override
	public void setMateReadAlignmentEnd(int mateReadAlignmentEnd) {
		this.mateReadAlignmentEnd = mateReadAlignmentEnd;
	}

	public int getRefPositionOfMateLigationSite() {
		return refPositionOfMateLigationSite;
	}

	@Override
	public void setRefPositionOfMateLigationSite(int refPositionOfMateLigationSite) {
		this.refPositionOfMateLigationSite = refPositionOfMateLigationSite;
	}

	@Override
	public Boolean getNegativeCodingStrand() {
		return negativeCodingStrand;
	}

	@Override
	public void setNegativeCodingStrand(@Nullable Boolean negativeCodingStrand) {
		this.negativeCodingStrand = negativeCodingStrand;
	}

	@Override
	public int getSmallestDuplexAlignmentOffset() {
		return smallestDuplexAlignmentOffset;
	}

	@Override
	public void setSmallestDuplexAlignmentOffset(int smallestOffset) {
		smallestDuplexAlignmentOffset = smallestOffset;
	}

}
