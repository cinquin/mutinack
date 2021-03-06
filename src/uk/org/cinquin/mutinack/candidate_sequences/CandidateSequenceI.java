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

import org.eclipse.collections.api.bag.Bag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.multimap.set.SetMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import gnu.trove.TByteCollection;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.output.LocationExaminationResults;
import uk.org.cinquin.mutinack.qualities.DetailedQualities;

public interface CandidateSequenceI extends Serializable {

	int getnMatchingCandidatesOtherSamples();
	void setnMatchingCandidatesOtherSamples(int i);
	@NonNull String toOutputString(Parameters param, LocationExaminationResults examResults);
	int getMaxInsertSize();
	void setMaxInsertSize(int maxInsertSize);
	int getMinInsertSize();
	void setMinInsertSize(int minInsertSize);
	int getAverageMappingQuality();
	void setAverageMappingQuality(int averageMappingQuality);
	Mutinack getOwningAnalyzer();
	SubAnalyzer getOwningSubAnalyzer();
	String getSampleName();
	@NonNull SequenceLocation getLocation();
	int getTotalReadsAtPosition();
	void setTotalReadsAtPosition(int totalReadsAtPosition);
	int getTotalAllDuplexes();
	void setTotalAllDuplexes(int totalAllDuplexes);
	int getTotalGoodOrDubiousDuplexes();
	void setTotalGoodOrDubiousDuplexes(int totalGoodOrDubiousDuplexes);
	int getTotalGoodDuplexes();
	void setTotalGoodDuplexes(int totalGoodDuplexes);
	int getnDuplexes() ;
	void setnDuplexes(int nDuplexes);
	int getnGoodOrDubiousDuplexes();
	void setnGoodOrDubiousDuplexes(int nGoodOrDubiousDuplexes);
	int getnGoodDuplexes();
	void setnGoodDuplexes(int nGoodDuplexes);
	MutableSet<@NonNull Duplex> getDuplexes();
	void setDuplexes(@NonNull MutableSet<@NonNull Duplex> duplexes);
	@NonNull TObjectIntMap<ExtendedSAMRecord> getMutableConcurringReads(Parameters param);
	@NonNull TObjectIntMap<ExtendedSAMRecord> getNonMutableConcurringReads();
	StringBuilder getSupplementalMessage();
	void setSupplementalMessage(StringBuilder supplementalMessage);
	DetailedQualities<PositionAssay> getQuality();
	byte getWildtypeSequence();
	void setWildtypeSequence(byte wildtypeSequence);
	byte @Nullable[] getSequence();
	MutationType getMutationType();
	String getKind();
	String getChange();
	void addPhredScoresToList(@NonNull TByteCollection ql);
	void mergeWith(@NonNull CandidateSequenceI c, Parameters param);
	int getMaxDistanceToLigSite();
	int getMinDistanceToLigSite();
	Bag<ComparablePair<String, String>> getRawMismatchesQ2();
	@NonNull MutableBag<ComparablePair<String, String>> getMutableRawMismatchesQ2();
	Bag<ComparablePair<String, String>> getRawDeletionsQ2();
	MutableBag<ComparablePair<String, String>> getMutableRawDeletionsQ2();
	Bag<ComparablePair<String, String>> getRawInsertionsQ2();
	MutableBag<ComparablePair<String, String>> getMutableRawInsertionsQ2();
	float getMeanDistanceToLigSite();
	int getInsertSizeAtPos10thP();
	void setInsertSizeAtPos10thP(int insertSizeAtPos10thP);
	int getInsertSizeAtPos90thP();
	void setInsertSizeAtPos90thP(int insertSizeAtPos90thP);
	Serializable getPreexistingDetection();
	void setPreexistingDetection(Serializable preexistingDetection);
	int getPositiveStrandCount();
	int getNegativeStrandCount();
	void incrementPositiveStrandCount(int i);
	void incrementNegativeStrandCount(int i);
	boolean isHidden();
	int removeConcurringRead(@NonNull ExtendedSAMRecord r);
	void setMedianPhredAtPosition(byte positionMedianPhred);
	void setProbCollision(float probAtLeastOneCollision);
	void setnWrongPairs(int count);
	int getnWrongPairs();
	@NonNull TObjectLongHashMap<Duplex> getIssues();
	void reset();
	void acceptLigSiteDistance(int maxDistanceToLigSite);
	void setnGoodDuplexesIgnoringDisag(int size);
	int getnGoodDuplexesIgnoringDisag();
	Mutation getMutation();
	int getnDuplexesSisterSamples();
	void setnDuplexesSisterSamples(int nDuplexesSisterSamples);
	void setInsertSize(int insertSize);
	void setPositionInRead(int readPosition);
	void setReadEL(int effectiveReadLength);
	void setReadName(@NonNull String fullName);
	void setReadAlignmentStart(int refAlignmentStart);
	void setMateReadAlignmentStart(int mateRefAlignmentStart);
	void setReadAlignmentEnd(int refAlignmentEnd);
	void setMateReadAlignmentEnd(int mateRefAlignmentEnd);
	void setRefPositionOfMateLigationSite(int refPositionOfMateLigationSite);
	void setHidden(boolean b);
	void addBasePhredScore(byte b);
	byte getMedianPhredScore();
	@Nullable Boolean getNegativeCodingStrand();
	void setNegativeCodingStrand(@Nullable Boolean negativeCodingStrand);
	int getSmallestConcurringDuplexDistance();
	int getLargestConcurringDuplexDistance();
	void setGoodCandidateForUniqueMutation(boolean b);
	boolean isGoodCandidateForUniqueMutation();
	int getnQ1PlusConcurringDuplexes();
	SetMultimap<String, GenomeInterval> getMatchingGenomeIntervals();

}
