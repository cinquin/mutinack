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
package uk.org.cinquin.mutinack.candidate_sequences;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import gnu.trove.TByteCollection;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import uk.org.cinquin.mutinack.*;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.collections.SingletonObjectIntMap;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

import java.io.Serializable;
import java.util.*;


/**
 * Equality is computed solely based on the mutation type and sequence, and in particular
 * not on the list of reads that support the candidate sequence.
 * Note that many methods in this class are *not* thread-safe.
 * @author olivier
 *
 */
public class CandidateSequence implements CandidateSequenceI, Serializable {
	
	private static final long serialVersionUID = 8222086925028013360L;
	private @Nullable TIntList distancesToLigSite;
	private @Nullable Collection<ComparablePair<String, String>> rawMismatchesQ2,
		rawDeletionsQ2, rawInsertionsQ2;
	private final @NonNull MutationType mutationType;
	private byte @Nullable[] sequence;
	private byte wildtypeSequence;
	private DetailedQualities quality;
	private @Nullable Quality supplQuality;
	private @Nullable StringBuilder supplementalMessage;
	private transient final @Nullable ExtendedSAMRecord initialConcurringRead;
	private final int initialLigationSiteD;
	private transient TObjectIntHashMap<ExtendedSAMRecord> concurringReads;
	private transient @Nullable Collection<@NonNull DuplexRead> duplexes;
	private int nGoodDuplexes;
	private int nGoodDuplexesIgnoringDisag;
	private int nGoodOrDubiousDuplexes;
	private int nDuplexes;
	private float totalGoodDuplexes;
	private float totalGoodOrDubiousDuplexes;
	private float totalAllDuplexes;
	private float totalReadsAtPosition;
	private final @NonNull SequenceLocation location;
	private final int owningAnalyzer;
	private boolean hasFunnyInserts = false;
	private int averageMappingQuality = -1;
	private int minInsertSize = -1;
	private int maxInsertSize = -1;
	private int nWrongPairs;
	private boolean hidden = false;
	
	private byte singleBasePhredQuality = -1;
	private TByteArrayList phredQualityScores;
	private transient Map<DuplexRead, DetailedQualities> issues;
	
	//For debugging purposes
	public int insertSize = -1;
	public int localIgnoreLastNBases = -1;
	public int positionInRead = -1;
	public int readEL = -1;
	public boolean insertSizeNoBarcodeAccounting = false;
	public @Nullable String readName;
	public int readAlignmentStart = -1;
	public int mateReadAlignmentStart = -1;
	public int readAlignmentEnd = -1;
	public int mateReadAlignmentEnd = -1;
	public int refPositionOfMateLigationSite = 1;
	
	private byte medianPhredAtLocus;
	
	public int minDistanceToLigSite = Integer.MAX_VALUE;
	public int maxDistanceToLigSite = Integer.MIN_VALUE;
	
	private boolean negativeStrand;
	public int nDuplexesSisterArm = -1;
	
	public void resetLigSiteDistances() {
		minDistanceToLigSite = Integer.MAX_VALUE;
		maxDistanceToLigSite = Integer.MIN_VALUE;
	}
	
	public void acceptLigSiteDistance(int distance) {
		if (distance < minDistanceToLigSite) {
			minDistanceToLigSite = distance;
		}
		if (distance > maxDistanceToLigSite) {
			maxDistanceToLigSite = distance;
		}
	}
	
	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public byte getMedianPhredAtLocus() {
		return medianPhredAtLocus;
	}

	public void setMedianPhredAtLocus(byte medianPhredAtLocus) {
		this.medianPhredAtLocus = medianPhredAtLocus;
	}

	public CandidateSequence(int analyzerID, @NonNull MutationType mutationType,
			@NonNull SequenceLocation location, @Nullable ExtendedSAMRecord initialConcurringRead,
			int initialLigationSiteD) {
		owningAnalyzer = analyzerID;
		this.mutationType = mutationType;
		this.location = location;
		this.initialConcurringRead = initialConcurringRead;
		this.initialLigationSiteD = initialLigationSiteD;
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
			default : throw new AssertionFailedException();
		}
		result += " at " + getLocation() + " (" + getNonMutableConcurringReads().size() + " concurring reads)";
		return result;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getMutationType().hashCode();
		result = prime * result + Arrays.hashCode(getSequence());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (obj instanceof CandidateSequenceCombo) {
			return obj.equals(this);
		}
		if (!(obj.getClass().equals(CandidateSequence.class))) {
			return false;
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

	@Override
	public String getChange() {
		final String result;
		switch (getMutationType()) {
			case COMBINATION:
				throw new AssertionFailedException();
			case DELETION:
				result = "-" + new String(getSequence()) + "-";
				break;
			case INSERTION:
				result = "^" + new String(getSequence()) + "^";
				break;
			case SUBSTITUTION:
				result = new String(new byte[] {getWildtypeSequence()}) +
				"->" + new String(getSequence());
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
	public boolean isHasFunnyInserts() {
		return hasFunnyInserts;
	}

	@Override
	public void setHasFunnyInserts(boolean hasFunnyInserts) {
		this.hasFunnyInserts = hasFunnyInserts;
	}

	@Override
	public int getOwningAnalyzer() {
		return owningAnalyzer;
	}

	@Override
	public SequenceLocation getLocation() {
		return location;
	}

	@Override
	public float getTotalReadsAtPosition() {
		return totalReadsAtPosition;
	}

	@Override
	public void setTotalReadsAtPosition(float totalReadsAtPosition) {
		this.totalReadsAtPosition = totalReadsAtPosition;
	}

	@Override
	public float getTotalAllDuplexes() {
		return totalAllDuplexes;
	}

	@Override
	public void setTotalAllDuplexes(float totalAllDuplexes) {
		this.totalAllDuplexes = totalAllDuplexes;
	}

	@Override
	public float getTotalGoodOrDubiousDuplexes() {
		return totalGoodOrDubiousDuplexes;
	}

	@Override
	public void setTotalGoodOrDubiousDuplexes(float totalGoodOrDubiousDuplexes) {
		this.totalGoodOrDubiousDuplexes = totalGoodOrDubiousDuplexes;
	}

	@Override
	public float getTotalGoodDuplexes() {
		return totalGoodDuplexes;
	}

	@Override
	public void setTotalGoodDuplexes(float totalGoodDuplexes) {
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
	public @NonNull Collection<@NonNull DuplexRead> getDuplexes() {
		if (duplexes == null) {
			 duplexes = new ArrayList<>();
		}
		return duplexes;
	}

	@Override
	public void setDuplexes(@NonNull Collection<@NonNull DuplexRead> duplexes) {
		this.duplexes = duplexes;
	}

	@SuppressWarnings("null")
	@Override
	public @NonNull TObjectIntHashMap<ExtendedSAMRecord> getMutableConcurringReads() {
		if (concurringReads == null) {
			concurringReads = new TObjectIntHashMap<>(500);
			if (initialConcurringRead != null) {
				concurringReads.put(initialConcurringRead, initialLigationSiteD);
			}
		}
		return concurringReads;
	}
	
	private final static TObjectIntMap<ExtendedSAMRecord> 
		emptyObjectIntHashMap = new TObjectIntHashMap<>(0);
	
	@SuppressWarnings("null")
	@Override
	public @NonNull TObjectIntMap<ExtendedSAMRecord> getNonMutableConcurringReads() {
		if (concurringReads != null) {
			return concurringReads;
		} else {
			if (initialConcurringRead == null) {
				return emptyObjectIntHashMap;
			} else {
				return new SingletonObjectIntMap<>(initialConcurringRead, initialLigationSiteD);
			}
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
	public @NonNull DetailedQualities getQuality() {
		if (quality == null) {
			quality = new DetailedQualities();
		}
		return quality;
	}
	
	@Override
	public /*@Nullable*/ Quality getSupplQuality() {//Remove Nullable annotation to
		//silence spurious Eclipse warning in Mutinack.java
		return supplQuality;
	}

	@Override
	public void setSupplQuality(@Nullable Quality supplQuality) {
		this.supplQuality = supplQuality;
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
	public void setSequence(byte[] sequence) {
		this.sequence = sequence;
	}

	@Override
	public @NonNull MutationType getMutationType() {
		return mutationType;
	}
	
	@Override
	public @NonNull String getKind() {
		return getMutationType().toString();
	}
	
	public void addBasePhredQualityScore(byte q) {
		if (q < 0) {
			throw new IllegalArgumentException("Negative Phred quality score: " + q);
		}
		if (phredQualityScores == null && singleBasePhredQuality == -1) {
			singleBasePhredQuality = q;
		} else {
			allocateBasePhredQualityArray();
			phredQualityScores.add(q);
		}
	}
	
	private void allocateBasePhredQualityArray() {
		if (phredQualityScores != null) {
			return;
		}
		if (singleBasePhredQuality == -2) {
			throw new AssertionFailedException();
		}
		phredQualityScores = new TByteArrayList(1000);
		if (singleBasePhredQuality != -1) {
			phredQualityScores.add(singleBasePhredQuality);
			singleBasePhredQuality = -2;
		}
	}
	
	@SuppressWarnings("null")
	@Override
	public @NonNull TByteArrayList getPhredQualityScores() {
		allocateBasePhredQualityArray();
		return phredQualityScores;
	}
	
	public byte getMedianPhredQuality() {
		if (phredQualityScores == null) {
			if (singleBasePhredQuality == -2) {
				throw new AssertionFailedException();
			} else if (singleBasePhredQuality == -1) {
				throw new IllegalStateException("No base to compute median from");
			} else {
				return singleBasePhredQuality;
			}
		}
		phredQualityScores.sort();
		return phredQualityScores.get(phredQualityScores.size() / 2);
	}
	
	@Override
	public void addPhredQualitiesToList(@NonNull TByteCollection ql) {
		if (phredQualityScores == null) {
			if (singleBasePhredQuality == -1) {
				return;
			} else if (singleBasePhredQuality < 0) {
				throw new AssertionFailedException();
			} else {
				ql.add(singleBasePhredQuality);
			}
		} else {
			ql.addAll(phredQualityScores);
		}
	}

	public int getnWrongPairs() {
		return nWrongPairs;
	}

	public void setnWrongPairs(int nWrongPairs) {
		this.nWrongPairs = nWrongPairs;
	}
	
	public void setNegativeStrand(boolean negativeStrand) {
		this.negativeStrand = negativeStrand;
	}
	
	@Override
	public boolean isNegativeStrand() {
		return negativeStrand;
	}

	public int getnGoodDuplexesIgnoringDisag() {
		return nGoodDuplexesIgnoringDisag;
	}

	public void setnGoodDuplexesIgnoringDisag(int nGoodDuplexesIgnoringDisag) {
		this.nGoodDuplexesIgnoringDisag = nGoodDuplexesIgnoringDisag;
	}
	
	public boolean containsType(Class<? extends CandidateSequence> class1) {
		return class1.isInstance(this);
	}
	
	@Override
	public CandidateSequenceI getUniqueType(@NonNull Class<? extends CandidateSequence> class1) {
		if (class1.isInstance(this)) {
			return this;
		} else {
			return null;
		}
	}
	
	@Override
	public CandidateSequenceI getUniqueType(@NonNull MutationType type) {
		if (mutationType == type) {
			return this;
		} else {
			return null;
		}
	}

	@Override
	public void mergeWith(@NonNull CandidateSequenceI candidate) {
		if (DebugControl.NONTRIVIAL_ASSERTIONS && !this.getClass().isInstance(candidate)) {
			throw new IllegalArgumentException("Cannot merge " + this + " to " + candidate +
					" " + candidate.getNonMutableConcurringReads());
		}
		if (getSupplQuality() != null || candidate.getSupplQuality() != null) {
			throw new AssertionFailedException();
		}
		getMutableConcurringReads().putAll(candidate.getNonMutableConcurringReads());
		candidate.addPhredQualitiesToList(getPhredQualityScores());
		acceptLigSiteDistance(candidate.getMinDistanceToLigSite());
		acceptLigSiteDistance(candidate.getMaxDistanceToLigSite());
		if (candidate.getRawMismatchesQ2().size() > 0) {
			getMutableRawMismatchesQ2().addAll(candidate.getRawMismatchesQ2()); //TODO Is it
			//worth optimizing this out if not keeping track of raw disagreements? That would
			//save one list allocation per locus
		}
		if (candidate.getRawDeletionsQ2().size() > 0) {
			getMutableRawDeletionsQ2().addAll(candidate.getRawDeletionsQ2());
		}
		if (candidate.getRawInsertionsQ2().size() > 0) {
			getMutableRawInsertionsQ2().addAll(candidate.getRawInsertionsQ2());
		}
		//*****
		//NB: make sure to field merging in sync in CandidateSequenceCombo::mergeCandidate 
		//TODO Merge those pieces of code
		//*****
	}

	@SuppressWarnings("null")
	public @NonNull Map<DuplexRead, DetailedQualities> getIssues() {
		if (issues == null) {
			issues = new THashMap<>();
		}
		return issues;
	}

	public void setIssues(@NonNull Map<DuplexRead, DetailedQualities> issues) {
		this.issues = issues;
	}
	
	public TIntList getDistancesToLigSite() {
		return distancesToLigSite;
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

	public void setDistancesToLigSite(TIntList listDistancesToLigSite) {
		this.distancesToLigSite = listDistancesToLigSite;
	}

	@Override
	public boolean containsMutationType(@NonNull MutationType type) {
		return type == mutationType;
	}
}
