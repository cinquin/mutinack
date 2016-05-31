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
package uk.org.cinquin.mutinack;

import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.standford.nlp.util.HasInterval;
import contrib.edu.standford.nlp.util.Interval;
import contrib.net.sf.samtools.CigarElement;
import contrib.net.sf.samtools.CigarOperator;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SamPairUtil;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.features.ParseRTException;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Util;

/**
 * Hashcode and equality based on read name + first or second of pair.
 * @author olivier
 *
 */
public final class ExtendedSAMRecord implements HasInterval<Integer> {
	
	static final Logger logger = LoggerFactory.getLogger(ExtendedSAMRecord.class);
			
	private final @NonNull Map<String, ExtendedSAMRecord> extSAMCache;
	public final @NonNull SAMRecord record;
	private final @NonNull String name;
	private @Nullable ExtendedSAMRecord mate;
	private final @NonNull String mateName;
	private final int hashCode;
	public @Nullable DuplexRead duplexRead;
	private byte @Nullable[] mateVariableBarcode;
	public final byte @NonNull[] variableBarcode;
	public final byte @Nullable[] constantBarcode;
	final int medianPhred;
	/**
	 * Length of read ignoring trailing Ns.
	 */
	public final int effectiveLength;
	int nReferenceDisagreements = 0;
	public final @NonNull Map<SequenceLocation, Byte> basePhredScores = new THashMap<>(150);
	private int nClipped = -1;
	private Boolean formsWrongPair;
	public boolean processed = false;
	public boolean duplexAlreadyVisitedForStats = false;

	private final @NonNull MutinackGroup groupSettings;
			
	public static String getReadFullName(SAMRecord rec) {
		return (rec.getReadName() + "--" + (rec.getFirstOfPairFlag()? "1" : "2"))/*.intern()*/;
	}

	public @NonNull String getFullName() {
		return name;
	}

	@Override
	public final int hashCode() {
		return hashCode;
	}
	
	@Override
	public final boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ExtendedSAMRecord)) {
			return false;
		}
		return name.equals(((ExtendedSAMRecord) obj).name);
	}
	
	private void computeNClipped() {
		final int readLength = record.getReadLength();
		final int adapterClipped = readLength - effectiveLength;
		
		int nClippedLeft = (!getReadNegativeStrandFlag() ? 
				
						/* positive strand */
						getAlignmentStart() - getUnclippedStart() :
								
						/* negative strand */
						/* Note: getMateAlignmentEnd will return Integer.MAX_INT if mate not loaded*/
						(getAlignmentStart() <= getMateAlignmentStart() ?
							/* adapter run through, causes clipping we should ignore */
							0 :
							getAlignmentStart() - getUnclippedStart() - adapterClipped));
		nClippedLeft = Math.max(0, nClippedLeft);
		
		int nClippedRight =	getReadNegativeStrandFlag() ? 
				
						/* negative strand */
						getUnclippedEnd() - getAlignmentEnd() :
								
						/* positive strand */
						(getAlignmentEnd() >= getMateAlignmentEnd() ? 
							/* adapter run through, causes clipping we should ignore */
							0 :
							getUnclippedEnd() - getAlignmentEnd() - adapterClipped);
		nClippedRight = Math.max(0, nClippedRight);
		
		nClipped = nClippedLeft + nClippedRight;	
	}
	
	public int getnClipped() {
		if (nClipped == -1) {
			computeNClipped();
		}
		return nClipped;
	}
	
	public void resetnClipped() {
		nClipped = -1;
	}

	@SuppressWarnings("static-access")
	public ExtendedSAMRecord(@NonNull SAMRecord rec, @NonNull String fullName,
			@NonNull MutinackGroup groupSettings, @NonNull Mutinack analyzer,
			@NonNull SequenceLocation location, @NonNull Map<String, ExtendedSAMRecord> extSAMCache) {
		this.groupSettings = groupSettings;
		this.extSAMCache = extSAMCache;
		this.name = fullName;
		this.record = rec;
		hashCode = fullName.hashCode();
		mateName = (rec.getReadName() + "--" +  (rec.getFirstOfPairFlag() ? "2" : "1"))/*.intern()*/;
		
		final int readLength = rec.getReadLength();
		
		//Find effective end of read, i.e. first position that is not an 'N' (the trimming
		//step run prior to mutation detection might shorten reads that ran into the
		//adapter because the insert was shorter than read length, by transforming all
		//bases that should be ignored to an N)
		@SuppressWarnings("hiding")
		int effectiveLength = readLength;
		
		final byte[] read = record.getReadBases();
		final byte[] baseQualities = record.getBaseQualities();

		if (getReadNegativeStrandFlag()) {
			int i = 0;
			while (read[i] == 'N' && 
					i < readLength - 1) {
				i++;
			}
			effectiveLength = readLength - i;
		} else {
			while (read[effectiveLength - 1] == 'N' &&
					effectiveLength > 0) {
				effectiveLength--;
			}
		}
		Assert.isFalse(effectiveLength < 0);
		this.effectiveLength = effectiveLength;
		
		int sumBaseQualities = 0;
		int nConsidered = 0;
		TIntList qualities = new TIntArrayList(effectiveLength);
		int n =  Math.min(effectiveLength, readLength / 2);
		for (int index1 = 0; index1 < n; index1++) {
			nConsidered++;
			final byte b = baseQualities[index1];
			sumBaseQualities += b;
			analyzer.stats.forEach(s -> s.nProcessedBases.add(location, 1));
			analyzer.stats.forEach(s -> s.phredSumProcessedbases.add(b));
			qualities.add(b);
		}
		int avQuality = sumBaseQualities / nConsidered;
		analyzer.stats.forEach(s-> s.averageReadPhredQuality0.insert(avQuality));

		sumBaseQualities = 0;
		nConsidered = 0;
		for (int index1 = readLength / 2; index1 < effectiveLength; index1++) {
			nConsidered++;
			final byte b = baseQualities[index1];
			sumBaseQualities += b;
			analyzer.stats.forEach(s -> s.nProcessedBases.add(location, 1));
			analyzer.stats.forEach(s -> s.phredSumProcessedbases.add(b));
			qualities.add(b);
		}
		if (nConsidered > 0) {
			int avQuality1 = sumBaseQualities / nConsidered;
			analyzer.stats.forEach(s -> s.averageReadPhredQuality1.insert(avQuality1));
		}

		qualities.sort();
		medianPhred = qualities.get(qualities.size() / 2);
		analyzer.stats.forEach(s -> s.medianReadPhredQuality.insert(medianPhred));
		
		Assert.isTrue(rec.getUnclippedEnd() >= getAlignmentEnd());
		Assert.isTrue(rec.getAlignmentStart() >= getUnclippedStart());
		
		final @NonNull String fullBarcodeString;
		String bcAttr = (String) record.getAttribute("BC");
		if (bcAttr == null) {
			final int firstIndex = name.indexOf("BC:Z:");
			if (firstIndex == -1) {
				throw new ParseRTException("Missing first barcode for read " + name + 
						" " + record.toString() + " from analyzer " + analyzer);
			}
			final int index;
			if (record.getFirstOfPairFlag()) {
				index = firstIndex;
			} else {
				index = name.indexOf("BC:Z:", firstIndex + 1);
				if (index == -1) {
					throw new ParseRTException("Missing second barcode for read " + name + 
							" " + record.toString() + " from analyzer " + analyzer);
				}
			}
			fullBarcodeString = nonNullify(name.substring(index + 5, name.indexOf("_", index)));
		} else {
			fullBarcodeString = bcAttr;
		}
		variableBarcode = Util.getInternedVB(nonNullify(fullBarcodeString.substring(
				groupSettings.getVariableBarcodeStart(), groupSettings.getVariableBarcodeEnd() + 1).getBytes()));
		constantBarcode = Util.getInternedCB(nonNullify(fullBarcodeString.substring(
				groupSettings.getConstantBarcodeStart(), groupSettings.getConstantBarcodeEnd() + 1).getBytes()));

		//interval = Interval.toInterval(rec.getAlignmentStart(), rec.getAlignmentEnd());
	}
	
	public ExtendedSAMRecord(@NonNull SAMRecord rec, @NonNull MutinackGroup groupSettings,
			@NonNull Mutinack analyzer, @NonNull SequenceLocation location,
			@NonNull Map<String, ExtendedSAMRecord> extSAMCache) {
		this(rec, (rec.getReadName() + "--" +  (rec.getFirstOfPairFlag() ? "1" : "2"))/*.intern()*/,
				groupSettings, analyzer, location, extSAMCache);
	}
	
	public byte @NonNull[] getMateVariableBarcode() {
		if (mateVariableBarcode == null ||
				mateVariableBarcode == groupSettings.getNs()) {
			checkMate();
			if (mate == null) {
				mateVariableBarcode = groupSettings.getNs();
			} else {
				mateVariableBarcode = nonNullify(mate).variableBarcode;
			}
		}
		return Objects.requireNonNull(mateVariableBarcode);
	}
		
	@Override
	public String toString() {
		return name + ": " + "startNoBC: " + getAlignmentStart() +
			"; endNoBC: " + getAlignmentEnd() +
			"; alignmentStart: " + (getReadNegativeStrandFlag() ? "-" : "+") + getAlignmentStart() +
			"; alignmentEnd: " + getAlignmentEnd() + 
			"; cigar: " + record.getCigarString() +
			"; length: " + record.getReadLength() +
			"; effectiveLength: " + effectiveLength +
			"; nClipped: " + (nClipped == -1 ? "Uncomputed" : getnClipped()) +
			"; insertSize: " + getInsertSize() +
			"; bases: " + new String(record.getReadBases());
	}

	@Override
	public Interval<Integer> getInterval() {
		throw new RuntimeException("Unimplemented");
		//return interval;
	}
	
	public int referencePositionToReadPosition(int refPosition) {
		if (refPosition <= getAlignmentStart()) {
			return refPosition - getUnclippedStart();
		}
		List<CigarElement> cElmnts = record.getCigar().getCigarElements();
		final int nElmnts = cElmnts.size();
		int ceIndex = 0;
		int nReadBasesProcessed = getAlignmentStart() - getUnclippedStart();

		final int nBasesToAlign = refPosition - getAlignmentStart();
		int nBasesAligned = 0;
		while (ceIndex < nElmnts && nBasesAligned < nBasesToAlign) {
			final CigarElement c = cElmnts.get(ceIndex);
			final int blockLength = c.getLength();
			
			if (c.getOperator() == CigarOperator.MATCH_OR_MISMATCH) {
				int nTakenBases = Math.min(blockLength, nBasesToAlign - nBasesAligned);
				nBasesAligned += nTakenBases;
				nReadBasesProcessed += nTakenBases;
			} else if (c.getOperator() == CigarOperator.INSERTION) {
				nReadBasesProcessed += blockLength;
			} else if (c.getOperator() == CigarOperator.DELETION) {
				nBasesAligned += blockLength;
			}
			//Ignoring clipping at end of read		
			
			ceIndex++;
		}
		
		if (nBasesAligned == nBasesToAlign) {
			return nReadBasesProcessed;
		} else {
			return nReadBasesProcessed + (nBasesToAlign - nBasesAligned);
		}
	}
	
	private static final int NO_MATE_POSITION = Integer.MAX_VALUE - 1000;
		
	public int tooCloseToBarcode(int readPosition, int ignoreFirstNBases) {
		
		final boolean readOnNegativeStrand = getReadNegativeStrandFlag();
		
		final int distance0;

		if (readOnNegativeStrand) {
			distance0 = readPosition - ((record.getReadLength() - 1) - ignoreFirstNBases);
		} else {
			distance0 = ignoreFirstNBases - readPosition;
		}
				
		//Now check if position is too close to other adapter barcode ligation site,
		//or on the wrong side of it
		final int refPositionOfMateLigationSite = getRefPositionOfMateLigationSite();
		final int distance1;

		if (!formsWrongPair() && refPositionOfMateLigationSite != NO_MATE_POSITION) {

			final int readPositionOfLigSiteA = referencePositionToReadPosition(refPositionOfMateLigationSite - 1) + 1;
			final int readPositionOfLigSiteB = referencePositionToReadPosition(refPositionOfMateLigationSite + 1) - 1;
			
			if (getReadNegativeStrandFlag()) {
				distance1 = Math.max(readPositionOfLigSiteA, readPositionOfLigSiteB) + ignoreFirstNBases - readPosition;
			} else {
				distance1 = readPosition - (Math.min(readPositionOfLigSiteA, readPositionOfLigSiteB ) - ignoreFirstNBases);
			}
		} else {
			//Mate info not available, or pair is "wrong" pair
			//Just go by effectiveLength to infer presence of adapter, although
			//it should not happen in practice that reads form a wrong pair
			//when there is adapter read-through

			final int readLength = record.getReadLength();
			final int adapterClipped = readLength - effectiveLength;
			if (readOnNegativeStrand) {
				distance1 = (adapterClipped == 0) ?
						Integer.MIN_VALUE :
						ignoreFirstNBases + adapterClipped - readPosition;
			} else {
				distance1 = (adapterClipped == 0) ?
						Integer.MIN_VALUE :
						readPosition - (effectiveLength - ignoreFirstNBases - 1);
			}
		}
		
		return Math.max(distance0, distance1);
	}
	
	public int getRefPositionOfMateLigationSite() {
		return getReadNegativeStrandFlag() ?
			getMateUnclippedStart() :
			getMateUnclippedEnd();
	}

	public int getRefAlignmentStart() {
		int referenceStart = getAlignmentStart();
		Assert.isFalse(referenceStart < 0);
		return referenceStart;
	}
	
	public int getRefAlignmentEnd() {
		int referenceEnd = getAlignmentEnd();
		Assert.isFalse(referenceEnd < 0);
		return referenceEnd;
	}

	public int getMateRefAlignmentStart() {
		checkMate();
		return mate == null ? NO_MATE_POSITION : nonNullify(mate).getRefAlignmentStart();
	}
	
	public int getMateRefAlignmentEnd() {
		checkMate();
		return mate == null ? NO_MATE_POSITION : nonNullify(mate).getRefAlignmentEnd();
	}
	
	public int getInsertSize() {
		int inferredInsertSize = record.getInferredInsertSize();
		return inferredInsertSize;
	}
	
	public ExtendedSAMRecord getMate() {
		checkMate();
		return mate;
	}
		
	public boolean formsWrongPair() {
		if (formsWrongPair == null) {
			formsWrongPair = record.getReadPairedFlag() && (
					record.getReadUnmappedFlag() ||
					record.getMateUnmappedFlag() || 
					SamPairUtil.getPairOrientation(record) == PairOrientation.TANDEM ||
					SamPairUtil.getPairOrientation(record) == PairOrientation.RF
															);
		}
		return formsWrongPair;
	}

	public boolean getReadNegativeStrandFlag() {
		return record.getReadNegativeStrandFlag();
	}
	
	public boolean getReadPositiveStrand() {
		return !record.getReadNegativeStrandFlag();
	}
	
	private void checkMate() {
		if (mate == null) {
			mate = extSAMCache.get(mateName);
		}
	}
	
	/** Indexing starts at 0
	 */
	public int getAlignmentStart() {
		return record.getAlignmentStart() - 1;
	}
	
	/** Indexing starts at 0
	 */
	public int getUnclippedStart() {
		return record.getUnclippedStart() - 1;
	}
	
	/** Indexing starts at 0
	 */
	public int getMateAlignmentStart() {
		return record.getMateAlignmentStart() - 1;
	}
	
	/** Indexing starts at 0
	 */
	public int getAlignmentEnd() {
		return record.getAlignmentEnd() - 1;
	}

	/** Indexing starts at 0
	 * 
	 * @return
	 */
	public int getMateAlignmentEnd() {
		checkMate();
		if (mate == null) {
			return NO_MATE_POSITION;
		}
		return nonNullify(mate).getAlignmentEnd();
	}
	
	public int getMateUnclippedEnd() {
		checkMate();
		if (mate == null) {
			return NO_MATE_POSITION;
		}
		return nonNullify(mate).getUnclippedEnd();
	}

	public int getUnclippedEnd() {
		return record.getUnclippedEnd() - 1;
	}
	
	public int getMateUnclippedStart() {
		checkMate();
		if (mate == null) {
			return NO_MATE_POSITION;
		}
		return nonNullify(mate).getUnclippedStart();
	}

	public int getMappingQuality() {
		return record.getMappingQuality();
	}

	public boolean overlapsWith(SequenceLocation location) {
		if (getRefAlignmentStart() > location.position ||
			getRefAlignmentEnd() < location.position ||
			location.contigIndex != record.getReferenceIndex()) {
			return false;
		}
		return true;
	}
	
	boolean duplexLeft() {
		return formsWrongPair() ?
				getAlignmentStart() <= getMateAlignmentStart()
				: getReadPositiveStrand();
	}
}
