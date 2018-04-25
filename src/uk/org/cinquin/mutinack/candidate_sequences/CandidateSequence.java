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
import java.util.Arrays;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.multimap.set.MutableSetMultimap;
import org.eclipse.collections.api.multimap.set.SetMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Bags;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.multimap.set.UnifiedSetMultimap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import contrib.net.sf.samtools.util.StringUtil;
import gnu.trove.TByteCollection;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import uk.org.cinquin.final_annotation.Final;
import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.features.BedComplement;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;
import uk.org.cinquin.mutinack.features.GenomeInterval;
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
import uk.org.cinquin.mutinack.statistics.Histogram;


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
	private transient @Nullable MutableBag<ComparablePair<String, String>> rawMismatchesQ2,
		rawDeletionsQ2, rawInsertionsQ2;
	@Persistent private DetailedQualities<PositionAssay> quality;
	private transient TObjectLongHashMap<Duplex> issues;
	private @Nullable StringBuilder supplementalMessage;
	private transient TObjectIntHashMap<ExtendedSAMRecord> concurringReads;
	private transient @Nullable MutableSet<@NonNull Duplex> duplexes;
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
	private int nDuplexesSisterSamples = -1;
	private int nMatchingCandidatesOtherSamples = -1;
	private int smallestConcurringDuplexDistance = -1;
	private int largestConcurringDuplexDistance = -1;
	private int nQ1PlusConcurringDuplexes = -1;
	private float fractionTopStrandReads;
	private boolean topAndBottomStrandsPresent;
	private int topStrandDuplexes = -1;
	private int bottomStrandDuplexes = -1;

	private int negativeStrandCount = 0, positiveStrandCount = 0;

	@Final @Persistent private @NonNull MutationType mutationType;
	@Final @Persistent @JsonSerialize(using = ByteArrayStringSerializer.class)
		private byte @Nullable[] sequence;
	@JsonIgnore private transient int hashCode;
	@JsonSerialize(using = ByteStringSerializer.class)
		private byte wildtypeSequence;
	private final transient @NonNull ExtendedSAMRecord initialConcurringRead;
	@Final private int initialLigationSiteD;
	@Final @Persistent private @NonNull SequenceLocation location;
	private final transient SubAnalyzer owningSubAnalyzer;
	@Final private String sampleName;
	private boolean hidden = false;
	private Boolean negativeCodingStrand;
	private @Persistent boolean goodCandidateForUniqueMutation;
	@Persistent MutableSetMultimap<String, GenomeInterval> matchingGenomeIntervals;
	private float frequencyAtPosition;

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
	public void reset() {
		restoreConcurringReads();
		if (quality != null) {
			quality.reset();
		}
		if (issues != null) {
			issues.clear();
		}
		if (duplexes != null) {
			duplexes.clear();//Should have no effect
		}
		setMinDistanceToLigSite(Integer.MAX_VALUE);
		setMaxDistanceToLigSite(Integer.MIN_VALUE);
		setMeanDistanceToLigSite(Float.NaN);
		setnDuplexesSisterSamples(-1);
		setnDistancesToLigSite(0);
		smallestConcurringDuplexDistance = -1;
		largestConcurringDuplexDistance = -1;
		nQ1PlusConcurringDuplexes = -1;
		setGoodCandidateForUniqueMutation(false);
		matchingGenomeIntervals = null;
		frequencyAtPosition = -1;
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
		if (sequence != null) {
			for (byte b: sequence) {
				byte up = StringUtil.toUpperCase(b);
				try {
					Mutation.checkIsValidUCBase(up);
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Unknown base " + new String(new byte[] {b}) + " at " + location +
						" from deleted reference sequence or from sequence of read " + initialConcurringRead);
				}
			}
		}
		this.location = location;
		this.initialConcurringRead = initialConcurringRead;
		this.initialLigationSiteD = initialLigationSiteD;
		hashCode = computeHashCode();
	}

	@Override
	public String toString() {
		String result = goodCandidateForUniqueMutation ? "*" : "";
		result += getnQ1PlusConcurringDuplexes() + " ";
		result += getMutationType().toString();
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
		result += " at " + getLocation() + ' ' + matchingGenomeIntervals + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
		return result;
	}

	@Override
	public final int hashCode() {
		if (hashCode == 0) {//Can happen because of serialization
			hashCode = computeHashCode();
		}
		return hashCode;
	}

	private int computeHashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getMutationType().ordinal();
		result = prime * result + Arrays.hashCode(getSequence());
		return result;
	}

	@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		CandidateSequence other = (CandidateSequence) obj;
		if (getMutationType() != other.getMutationType()) {
			return false;
		}
		if (!Arrays.equals(getSequence(), other.getSequence())) {
			return false;
		}
		return true;
	}

	public static final Comparator<CandidateSequence> reverseFrequencyComparator = (c1, c2) -> {
		int cmp1 = Float.compare(c2.getFrequencyAtPosition(), c1.getFrequencyAtPosition());
		if (cmp1 != 0) {
			return cmp1;
		}
		return c1.getMutation().compareTo(c2.getMutation());
	};

	@Override
	public String getChange() {
		return getMutation().getChange(Boolean.TRUE.equals(getNegativeCodingStrand()));
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
	public @NonNull MutableSet<@NonNull Duplex> getDuplexes() {
		if (duplexes == null) {
			 duplexes = Sets.mutable.empty();
		}
		return duplexes;
	}

	@Override
	public void setDuplexes(@NonNull MutableSet<@NonNull Duplex> duplexes) {
		this.duplexes = duplexes;
	}

	public UnifiedSet<Duplex> computeSupportingDuplexes() {
		UnifiedSet<Duplex> duplexesSupportingC = new UnifiedSet<>(30);
		getNonMutableConcurringReads().forEachKey(r -> {
			Assert.isFalse(r.discarded);
			Duplex d = r.duplex;
			if (d != null) {
				Assert.isFalse(d.invalid);
				duplexesSupportingC.add(d);
			}
			return true;
		});//Collect *unique* duplexes
		return duplexesSupportingC;
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

	private void restoreConcurringReads() {
		if (originalConcurringReads != null) {
			concurringReads = originalConcurringReads;
			originalConcurringReads = null;
		}
	}

	@SuppressWarnings("unused")
	private @NonNull TObjectIntMap<ExtendedSAMRecord> getOriginalConcurringReads() {
		if (originalConcurringReads == null) {
			return getNonMutableConcurringReads();
		} else {
			return Objects.requireNonNull(originalConcurringReads);
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
			getMutableRawMismatchesQ2().addAllIterable(candidate.getRawMismatchesQ2()); //TODO Is it
			//worth optimizing this out if not keeping track of raw disagreements? That would
			//save one list allocation per position
		}
		if (!candidate.getRawDeletionsQ2().isEmpty()) {
			getMutableRawDeletionsQ2().addAllIterable(candidate.getRawDeletionsQ2());
		}
		if (!candidate.getRawInsertionsQ2().isEmpty()) {
			getMutableRawInsertionsQ2().addAllIterable(candidate.getRawInsertionsQ2());
		}
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull TObjectLongHashMap<Duplex> getIssues() {
		if (issues == null) {
			issues = new TObjectLongHashMap<>();
		}
		return issues;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull MutableBag<ComparablePair<String, String>> getMutableRawMismatchesQ2() {
		if (rawMismatchesQ2 == null) {
			rawMismatchesQ2 = Bags.mutable.empty();
		}
		return rawMismatchesQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Bag<ComparablePair<String, String>> getRawMismatchesQ2() {
		if (rawMismatchesQ2 == null) {
			return Bags.immutable.empty();
		}
		return rawMismatchesQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull MutableBag<ComparablePair<String, String>> getMutableRawDeletionsQ2() {
		if (rawDeletionsQ2 == null) {
			rawDeletionsQ2 = Bags.mutable.empty();
		}
		return rawDeletionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Bag<ComparablePair<String, String>> getRawDeletionsQ2() {
		if (rawDeletionsQ2 == null) {
			return Bags.immutable.empty();
		}
		return rawDeletionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull MutableBag<ComparablePair<String, String>> getMutableRawInsertionsQ2() {
		if (rawInsertionsQ2 == null) {
			rawInsertionsQ2 = Bags.mutable.empty();
		}
		return rawInsertionsQ2;
	}

	@Override
	@SuppressWarnings("null")
	public @NonNull Bag<ComparablePair<String, String>> getRawInsertionsQ2() {
		if (rawInsertionsQ2 == null) {
			return Bags.immutable.empty();
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

	private void initializeGenomeIntervals() {
		if (matchingGenomeIntervals == null) {
			matchingGenomeIntervals = new UnifiedSetMultimap<>();
		}
	}

	public void addMatchingGenomeIntervals(String name, GenomeFeatureTester intervals) {
		initializeGenomeIntervals();
		intervals.apply(location).forEach(gi -> matchingGenomeIntervals.put(name, gi));
	}

	public void recordMatchingGenomeIntervals(TMap<String, GenomeFeatureTester> intervalsMap) {
		initializeGenomeIntervals();
		intervalsMap.forEachEntry((setName, tester) -> {
			if (tester instanceof BedComplement) {
				return true;
			}
			addMatchingGenomeIntervals(setName, tester);
			return true;
		});
	}

	public byte[] getSequenceContext(int windowHalfWidth) {
		return location.getSequenceContext(windowHalfWidth);
	}

	@Override
	public @NonNull String toOutputString(@Nullable Parameters param, @Nullable LocationExaminationResults examResults) {
		StringBuilder result = new StringBuilder();

		NumberFormat formatter = Util.mediumLengthFloatFormatter.get();
		Stream<String> qualityKD = Arrays.stream(getIssues().values()).mapToObj(
				iss -> DetailedDuplexQualities.fromLong(iss).getQualities().
				filter(entry -> entry.getValue().lowerThan(Quality.GOOD)).
				map(Object::toString).
				collect(Collectors.joining(",", "{", "}")));
		Assert.isTrue(nDuplexesSisterSamples > -1);
		//TODO The following issues added below to qualityKD should just be retrieved from the
		//qualities field instead of being recomputed
		if (param != null && nDuplexesSisterSamples < param.minNumberDuplexesSisterSamples) {
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

		Iterator<CandidateSequence> localCandidates =
			examResults == null ? null : examResults.analyzedCandidateSequences.iterator();

		String qualityKDString = qualityKD.collect(Collectors.joining(","));
		/**
		 * Make sure columns stay in sync with Mutinack.outputHeader
		 */
		result.append(getnGoodDuplexes() + "\t" +
			getnGoodOrDubiousDuplexes() + '\t' +
			getnDuplexes() + '\t' +
			getNonMutableConcurringReads().size() + '\t' +
			formatter.format((getnGoodDuplexes() / ((float) getTotalGoodDuplexes()))) + '\t' +
			formatter.format((getnGoodOrDubiousDuplexes() / ((float) getTotalGoodOrDubiousDuplexes()))) + '\t' +
			formatter.format((getnDuplexes() / ((float) getTotalAllDuplexes()))) + '\t' +
			formatter.format((getNonMutableConcurringReads().size() / ((float) getTotalReadsAtPosition()))) + '\t' +
			(getAverageMappingQuality() == -1 ? "?" : getAverageMappingQuality()) + '\t' +
			formatter.format(fractionTopStrandReads) + '\t' +
			topAndBottomStrandsPresent + '\t' +
			new String(getSequenceContext(5)) + '\t' +
			nDuplexesSisterSamples + '\t' +
			getInsertSize() + '\t' +
			getInsertSizeAtPos10thP() + '\t' +
			getInsertSizeAtPos90thP() + '\t' +
			getMinDistanceToLigSite() + '\t' +
			getMaxDistanceToLigSite() + '\t' +
			Optional.ofNullable(negativeCodingStrand).map(String::valueOf).orElse("?") + '\t' +
			formatter.format(getMeanDistanceToLigSite()) + '\t' +
			formatter.format(getProbCollision()) + '\t' +
			getPositionInRead() + '\t' +
			getReadEL() + '\t' +
			getReadName() + '\t' +
			getReadAlignmentStart()  + '\t' +
			getMateReadAlignmentStart()  + '\t' +
			getReadAlignmentEnd() + '\t' +
			getMateReadAlignmentEnd() + '\t' +
			getRefPositionOfMateLigationSite() + '\t' +
			( ( param != null && (param.outputDuplexDetails || param.annotateMutationsInFile != null)) ?
					qualityKDString
				:
					"" /*getIssues()*/) + '\t' +
			//getIssues() + '\t' +
			getMedianPhredAtPosition() + '\t' +
			(getMinInsertSize() == -1 ? "?" : getMinInsertSize()) + '\t' +
			(getMaxInsertSize() == -1 ? "?" : getMaxInsertSize()) + '\t' +
			formatter.format(localCandidates == null || !localCandidates.hasNext() ? Float.NaN : localCandidates.next().getFrequencyAtPosition()) + '\t' +
			formatter.format(localCandidates == null || !localCandidates.hasNext() ? Float.NaN : localCandidates.next().getFrequencyAtPosition()) + '\t' +
			getSmallestConcurringDuplexDistance() + '\t' +
			getLargestConcurringDuplexDistance() + '\t' +
			(getSupplementalMessage() != null ? getSupplementalMessage() : "") + '\t'
			);

		/*result.append(matchingGenomeIntervals.keyMultiValuePairsView().
			collect(p -> p.getOne().toString() + ": " + p.getTwo().makeString(", ")).
			makeString("; "));*/
		result.append(matchingGenomeIntervals);

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
	public @NonNull Mutation getMutation() {
		if (mutation == null) {
			mutation = new Mutation(this);
		}
		return Objects.requireNonNull(mutation);
	}

	@Override
	public int getnDuplexesSisterSamples() {
		if (nDuplexesSisterSamples < 0) {
			throw new IllegalStateException();
		}
		return nDuplexesSisterSamples;
	}

	@Override
	public void setnDuplexesSisterSamples(int nDuplexesSisterSamples) {
		this.nDuplexesSisterSamples = nDuplexesSisterSamples;
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
	public int getSmallestConcurringDuplexDistance() {
		return smallestConcurringDuplexDistance;
	}

	@Override
	public int getLargestConcurringDuplexDistance() {
		return largestConcurringDuplexDistance;
	}

	@Override
	public void setGoodCandidateForUniqueMutation(boolean b) {
		goodCandidateForUniqueMutation = b;
	}

	@Override
	public boolean isGoodCandidateForUniqueMutation() {
		return goodCandidateForUniqueMutation;
	}

	@SuppressWarnings("static-method")
	protected Comparator<Duplex> getDuplexQualityComparator() {
		return duplexQualitycomparator;
	}

	private static final Comparator<Duplex> duplexQualitycomparator =
		Comparator.comparing((Duplex dr) -> CandidateSequence.staticFilterQuality(dr.localAndGlobalQuality)).
		thenComparing(Comparator.comparing((Duplex dr) -> dr.allRecords.size())).
		thenComparing(Duplex::getUnclippedAlignmentStart);

	@SuppressWarnings("ReferenceEquality")
	public int computeNQ1PlusConcurringDuplexes(Histogram concurringDuplexDistances, Parameters param) {
		MutableIntList alignmentStarts = IntLists.mutable.empty();

		final Duplex bestSupporting = getDuplexes().max(getDuplexQualityComparator());

		//Exclude duplexes whose reads all have an unmapped mate from the count
		//of Q1-Q2 duplexes that agree with the mutation; otherwise failed reads
		//may cause an overestimate of that number
		//Also exclude duplexes with clipping greater than maxConcurringDuplexClipping
		final int nConcurringDuplexes = getDuplexes().count(d -> {
			//noinspection ObjectEquality
			if (d == bestSupporting) {
				return true;
			}
			boolean good =
				d.allRecords.size() >= param.minConcurringDuplexReads * 2 /* double to account for mates */ &&
				d.localAndGlobalQuality.getValueIgnoring(assaysToIgnoreIncludingDuplexNStrands()).
					atLeast(Quality.GOOD) &&
				d.allRecords.anySatisfy(r -> !r.record.getMateUnmappedFlag()) &&
				d.allRecords.anySatisfy(r -> concurringDuplexReadClippingOK(r, param)) &&
				d.computeMappingQuality() >= param.minMappingQualityQ1;
			if (good) {
				final int distance = d.distanceTo(bestSupporting);
				alignmentStarts.add(distance);
				if (concurringDuplexDistances != null) {
					concurringDuplexDistances.insert(distance);
				}
			}
			return good;
		});

		if (!alignmentStarts.isEmpty()) {
			IntSummaryStatistics stats = alignmentStarts.summaryStatistics();
			smallestConcurringDuplexDistance = stats.getMin();
			largestConcurringDuplexDistance = stats.getMax();
		}

		nQ1PlusConcurringDuplexes = nConcurringDuplexes;
		return nConcurringDuplexes;
	}

	@Override
	public int getnQ1PlusConcurringDuplexes() {
		return nQ1PlusConcurringDuplexes;
	}

	@Override
	public SetMultimap<String, GenomeInterval> getMatchingGenomeIntervals() {
		return matchingGenomeIntervals;
	}

	public static @NonNull Quality staticFilterQuality(DetailedQualities<DuplexAssay> localAndGlobalQuality) {
		return localAndGlobalQuality.getNonNullValue();
	}

	@SuppressWarnings("static-method")
	public @NonNull Quality filterQuality(DetailedQualities<DuplexAssay> localAndGlobalQuality) {
		return staticFilterQuality(localAndGlobalQuality);
	}

	@SuppressWarnings("static-method")
	protected Set<DuplexAssay> assaysToIgnoreIncludingDuplexNStrands() {
		return SubAnalyzer.ASSAYS_TO_IGNORE_FOR_DUPLEX_NSTRANDS;
	}

	@SuppressWarnings("static-method")
	protected boolean concurringDuplexReadClippingOK(ExtendedSAMRecord r, Parameters param) {
		return r.getnClipped() < param.maxConcurringDuplexClipping &&
			r.getMate() != null && r.getMate().getnClipped() < param.maxConcurringDuplexClipping;
	}

	public void updateQualities(@SuppressWarnings("unused") @NonNull Parameters param) {
	}

	public void computeNBottomTopStrandReads() {
		final long topReads = getDuplexes().sumOfInt(d -> d.topStrandRecords.size());
		final float bottomReads = getDuplexes().sumOfInt(d -> d.bottomStrandRecords.size());
		setFractionTopStrandReads(topReads / (topReads + bottomReads));
		setTopAndBottomStrandsPresent(topReads > 0 && bottomReads > 0);

		topStrandDuplexes = (int) getDuplexes().sumOfInt(d -> Math.min(1, d.topStrandRecords.size()));
		bottomStrandDuplexes = (int) getDuplexes().sumOfInt(d -> Math.min(1, d.bottomStrandRecords.size()));
	}

	private void setFractionTopStrandReads(float f) {
		this.fractionTopStrandReads = f;
	}

	public float getFractionTopStrandReads() {
		return fractionTopStrandReads;
	}

	public boolean getTopAndBottomStrandsPresent() {
		return topAndBottomStrandsPresent;
	}

	private void setTopAndBottomStrandsPresent(boolean topAndBottomStrandsPresent) {
		this.topAndBottomStrandsPresent = topAndBottomStrandsPresent;
	}

	public int getTopStrandDuplexes() {
		return topStrandDuplexes;
	}

	public int getBottomStrandDuplexes() {
		return bottomStrandDuplexes;
	}

	public float getFrequencyAtPosition() {
		return frequencyAtPosition;
	}

	public void setFrequencyAtPosition(float frequencyAtPosition) {
		this.frequencyAtPosition = frequencyAtPosition;
	}
}
