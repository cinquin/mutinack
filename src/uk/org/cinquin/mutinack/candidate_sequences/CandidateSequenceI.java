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
import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import gnu.trove.TByteCollection;
import gnu.trove.list.array.TByteArrayList;
import gnu.trove.map.TObjectIntMap;
import uk.org.cinquin.mutinack.DetailedQualities;
import uk.org.cinquin.mutinack.DuplexRead;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.LocationExaminationResults;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.Quality;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;

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
	String getSampleName();
	@NonNull SequenceLocation getLocation();
	float getTotalReadsAtPosition();
	void setTotalReadsAtPosition(float totalReadsAtPosition);
	float getTotalAllDuplexes();
	void setTotalAllDuplexes(float totalAllDuplexes);
	float getTotalGoodOrDubiousDuplexes();
	void setTotalGoodOrDubiousDuplexes(float totalGoodOrDubiousDuplexes);
	float getTotalGoodDuplexes();
	void setTotalGoodDuplexes(float totalGoodDuplexes);
	int getnDuplexes() ;
	void setnDuplexes(int nDuplexes);
	int getnGoodOrDubiousDuplexes();
	void setnGoodOrDubiousDuplexes(int nGoodOrDubiousDuplexes);
	int getnGoodDuplexes();
	void setnGoodDuplexes(int nGoodDuplexes);
	Collection<@NonNull DuplexRead> getDuplexes();
	void setDuplexes(@NonNull Collection<@NonNull DuplexRead> duplexes);
	@NonNull TObjectIntMap<ExtendedSAMRecord> getMutableConcurringReads();
	@NonNull TObjectIntMap<ExtendedSAMRecord> getNonMutableConcurringReads();
	StringBuilder getSupplementalMessage();
	void setSupplementalMessage(StringBuilder supplementalMessage);
	DetailedQualities getQuality();
	byte getWildtypeSequence();
	void setWildtypeSequence(byte wildtypeSequence);
	byte @Nullable[] getSequence();
	MutationType getMutationType();
	String getKind();
	String getChange();
	@NonNull TByteArrayList getPhredQualityScores();
	void addPhredQualitiesToList(@NonNull TByteCollection ql);
	void mergeWith(@NonNull CandidateSequenceI c);
	@Nullable Quality getSupplQuality();
	void setSupplQuality(@Nullable Quality supplQuality);
	int getMaxDistanceToLigSite();
	int getMinDistanceToLigSite();
	Collection<ComparablePair<String, String>> getRawMismatchesQ2();
	Collection<ComparablePair<String, String>> getMutableRawMismatchesQ2();
	Collection<ComparablePair<String, String>> getRawDeletionsQ2();
	Collection<ComparablePair<String, String>> getMutableRawDeletionsQ2();
	Collection<ComparablePair<String, String>> getRawInsertionsQ2();
	Collection<ComparablePair<String, String>> getMutableRawInsertionsQ2();
	boolean isNegativeStrand();
	float getMeanDistanceToLigSite();
	int getInsertSizeAtPos10thP();
	void setInsertSizeAtPos10thP(int insertSizeAtPos10thP);
	int getInsertSizeAtPos90thP();
	void setInsertSizeAtPos90thP(int insertSizeAtPos90thP);
}
