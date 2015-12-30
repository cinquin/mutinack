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
package uk.org.cinquin.mutinack;

import static uk.org.cinquin.mutinack.misc_util.DebugControl.ENABLE_TRACE;
import static uk.org.cinquin.mutinack.misc_util.Util.nonNullify;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.standford.nlp.util.HasInterval;
import contrib.edu.standford.nlp.util.Interval;
import contrib.net.sf.samtools.AlignmentBlock;
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
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * Hashcode and equality based on read name + first or second of pair.
 * @author olivier
 *
 */
public final class ExtendedSAMRecord implements HasInterval<Integer> {
	
	final static Logger logger = LoggerFactory.getLogger(ExtendedSAMRecord.class);
			
	private final @NonNull Map<String, ExtendedSAMRecord> extSAMCache;
	public final @NonNull SAMRecord record;
	private final @NonNull String name;
	private @Nullable ExtendedSAMRecord mate;
	private final @Nullable String mateName;
	private final int hashCode;
	public transient @Nullable DuplexRead duplexRead;
	private byte @Nullable[] mateVariableBarcode; //TODO Could probably be made final
	public final byte @NonNull[] variableBarcode;
	final byte @Nullable[] constantBarcode;
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
	
	static int UNCLIPPED_BARCODE_LENGTH = Integer.MAX_VALUE;
	private static final int VARIABLE_BARCODE_START = 0;
	static int VARIABLE_BARCODE_END = Integer.MAX_VALUE;
	private static final int CONSTANT_BARCODE_START = 3;
	private static final int CONSTANT_BARCODE_END = 5;
	private static byte[] Ns;
	
	private static void checkSet(String var, int previousVal, int newVal) {
		if (previousVal == Integer.MAX_VALUE) {
			return;
		}
		if (previousVal != newVal) {
			throw new IllegalArgumentException("Trying to set " + var +
					" to " + newVal + " but it has already been set to " + previousVal);
		}
	}
	
	public static synchronized void setBarcodePositions(int variableStart, int variableEnd,
			int constantStart, int constantEnd, int unclippedBarcodeLength) {
		if (variableStart != 0) {
			throw new IllegalArgumentException("Unimplemented");
		}
		checkSet("variable barcode end", VARIABLE_BARCODE_END, variableEnd);
		checkSet("unclipped barcode length", UNCLIPPED_BARCODE_LENGTH, unclippedBarcodeLength);
		if (VARIABLE_BARCODE_END == Integer.MAX_VALUE) {
			VARIABLE_BARCODE_END = variableEnd;
			UNCLIPPED_BARCODE_LENGTH = unclippedBarcodeLength;
			System.out.println("Set variable barcode end position to " + ExtendedSAMRecord.VARIABLE_BARCODE_END);
			//VARIABLE_BARCODE_START == 0;
			final byte[] localNs = new byte [VARIABLE_BARCODE_END - VARIABLE_BARCODE_START + 1];
			for (int i = 0; i < VARIABLE_BARCODE_END - VARIABLE_BARCODE_START + 1; i++) {
				localNs[i] = 'N';
			}
			if (Ns != null) {
				throw new AssertionFailedException();
			}
			Ns = localNs;
		}
	}
	
	int getnClipped() {
		computeNClipped();
		return nClipped;
	}

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
		int adapterClipped = record.getReadLength() - effectiveLength;
		
		int nClippedLeft = Math.max(0, (!getReadNegativeStrandFlag() ? 
								/* positive strand */
								getAlignmentStart() - getUnclippedStart() - UNCLIPPED_BARCODE_LENGTH :
								/* negative strand */
								/* Note: getMateAlignmentEndNoBarcode will return Integer.MAX_INT if mate not loaded*/
								(getMateAlignmentEndNoBarcode() >= getAlignmentStartNoBarcode() /* adapter run through, causes clipping we should ignore */ ? 0 :
								getAlignmentStart() - getUnclippedStart() - adapterClipped))
							 );
		
		int nClippedRight =	Math.max(0, (getReadNegativeStrandFlag() ? 
						/* negative strand */
						record.getUnclippedEnd() - record.getAlignmentEnd() -UNCLIPPED_BARCODE_LENGTH :
						/* positive strand */
						( getAlignmentEnd() >= getMateAlignmentStartNoBarcode() /* adapter run through, causes clipping we should ignore */ ? 0 :
						record.getUnclippedEnd() - record.getAlignmentEnd() - adapterClipped)));
		
		nClipped = nClippedLeft + nClippedRight;	
	}

	public ExtendedSAMRecord(@NonNull SAMRecord rec, @NonNull String fullName, @NonNull Mutinack analyzer,
			@NonNull SequenceLocation location, @NonNull Map<String, ExtendedSAMRecord> extSAMCache) {
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
			while (read[i] == 'N' && i < readLength) {
				i++;
			}
			effectiveLength = readLength - i;
		} else {
			while (read[effectiveLength - 1] == 'N' && effectiveLength > 0) {
				effectiveLength--;
			}
		}
		
		if (effectiveLength < 0) {
			throw new AssertionFailedException();
		}
		
		this.effectiveLength = effectiveLength;
		
		int sumBaseQualities = 0;
		int nConsidered = 0;
		TIntList qualities = new TIntArrayList(effectiveLength);
		int n =  Math.min(effectiveLength, readLength / 2);
		for (int index1 = 0; index1 < n; index1++) {
			nConsidered++;
			byte b = baseQualities[index1];
			sumBaseQualities += b;
			analyzer.stats.forEach(s -> s.nProcessedBases.add(location, 1));
			analyzer.stats.forEach(s -> s.phredSumProcessedbases.add(b));
			qualities.add(b);
		}
		int avQuality = sumBaseQualities / nConsidered;
		analyzer.stats.forEach( s-> s.averageReadPhredQuality0.insert(avQuality));

		sumBaseQualities = 0;
		nConsidered = 0;
		for (int index1 = readLength / 2; index1 < effectiveLength; index1++) {
			nConsidered++;
			byte b = baseQualities[index1];
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
		
		if (DebugControl.NONTRIVIAL_ASSERTIONS && (rec.getUnclippedEnd() - rec.getAlignmentEnd() < 0 ||
				rec.getAlignmentStart() - rec.getUnclippedStart() < 0)) {
			throw new AssertionFailedException();
		}
		
		final @NonNull String fullBarcodeString;
		String bcAttr = (String) record.getAttribute("BC");
		if (bcAttr == null) {
			final int firstIndex =  name.indexOf("BC:Z:");
			if (firstIndex == -1) {
				throw new RuntimeException("Missing first barcode for read " + name + 
						" " + record.toString() + " from analyzer " + analyzer);
			}
			final int index;
			if (record.getFirstOfPairFlag()) {
				index = firstIndex;
			} else {
				index = name.indexOf("BC:Z:", firstIndex + 1);
				if (index == -1) {
					throw new RuntimeException("Missing second barcode for read " + name + 
							" " + record.toString() + " from analyzer " + analyzer);
				}
			}
			fullBarcodeString = nonNullify(name.substring(index + 5, name.indexOf("_", index)));
		} else {
			fullBarcodeString = bcAttr;
		}
		variableBarcode = Util.getInternedVB(nonNullify(fullBarcodeString.substring(VARIABLE_BARCODE_START, VARIABLE_BARCODE_END + 1).getBytes()));
		constantBarcode = Util.getInternedCB(nonNullify(fullBarcodeString.substring(CONSTANT_BARCODE_START, CONSTANT_BARCODE_END + 1).getBytes()));

		//interval = Interval.toInterval(rec.getAlignmentStart(), rec.getAlignmentEnd());
	}
	
	public ExtendedSAMRecord(@NonNull SAMRecord rec, @NonNull Mutinack analyzer, 
			@NonNull SequenceLocation location, @NonNull Map<String, ExtendedSAMRecord> extSAMCache) {
		this(rec, (rec.getReadName() + "--" +  (rec.getFirstOfPairFlag() ? "1" : "2"))/*.intern()*/, analyzer, location, extSAMCache);
	}
	
	public byte @NonNull[] getMateVariableBarcode() {
		if (mateVariableBarcode == null || mateVariableBarcode == Ns) {
			checkMate();
			if (mate == null) {
				mateVariableBarcode = Ns;
			} else {
				mateVariableBarcode = nonNullify(mate).variableBarcode;
			}
		}
		if (mateVariableBarcode != null) {
			return mateVariableBarcode;
		} else {
			throw new AssertionFailedException();
		}
	}
		
	@Override
	public String toString() {
		return name + ": " + "startNoBC: " + getAlignmentStartNoBarcode() +
			"; endNoBC: " + getAlignmentEndNoBarcode() +
			"; alignmentStart: " + (getReadNegativeStrandFlag() ? "-" : "+") + getAlignmentStart() +
			"; alignmentEnd: " + getAlignmentEnd() + 
			"; cigar: " + record.getCigarString() +
			"; length: " + record.getReadLength() +
			"; effectiveLength: " + effectiveLength +
			"; nClipped: " + (nClipped == -1 ? "Uncomputed" : getnClipped()) +
			"; insertSize: " + getInsertSizeNoBarcodes(true) +
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
			} if (c.getOperator() == CigarOperator.DELETION) {
				nBasesAligned += blockLength;
			}
			//Ignoring clipping at end of read		
			
			ceIndex++;
		}
		
		if (nBasesAligned == nBasesToAlign) {
			return nReadBasesProcessed;
		}
		else {
			return nReadBasesProcessed + (nBasesToAlign - nBasesAligned);
		}
	}
		
	public int tooCloseToBarcode(int refPosition, int readPosition, int ignoreFirstNBases) {
		
		final boolean readOnNegativeStrand = getReadNegativeStrandFlag();
		final int distance0;

		boolean tooCloseToBarcode;
		if (readOnNegativeStrand) {
			distance0 = readPosition - (record.getReadLength() - ignoreFirstNBases - UNCLIPPED_BARCODE_LENGTH);
			tooCloseToBarcode = distance0 > 0;
			if (ENABLE_TRACE && tooCloseToBarcode) {
				logger.trace("Ignoring indel too close to barcode A " + refPosition + " " + (record.getReadLength() - 1 - ignoreFirstNBases - UNCLIPPED_BARCODE_LENGTH) + "; " + getFullName() + " at " + getAlignmentStart());
			}
		} else {
			distance0 = ignoreFirstNBases + UNCLIPPED_BARCODE_LENGTH - readPosition;
			tooCloseToBarcode = distance0 > 0;
			if (ENABLE_TRACE && tooCloseToBarcode) {
				logger.trace("Ignoring indel too close to barcode B " + refPosition + " " + readPosition + " vs "  + (ignoreFirstNBases + UNCLIPPED_BARCODE_LENGTH) + "; " + getFullName() + " at " + getAlignmentStart());
			}
		}
		
		if (tooCloseToBarcode) {
			return distance0;
		}
		
		//Now check if position is too close to other adapter barcode ligation site,
		//or on the wrong side of it
		final int refPositionOfMateLigationSite = getRefPositionOfMateLigationSite();
		final int distance1;

		if (refPositionOfMateLigationSite < Integer.MAX_VALUE) {

			final int readPositionOfLigSiteA = referencePositionToReadPosition(refPositionOfMateLigationSite - 1) + 1;
			final int readPositionOfLigSiteB = referencePositionToReadPosition(refPositionOfMateLigationSite + 1) - 1;
			
			if (getReadNegativeStrandFlag()) {
				distance1 = Math.max(readPositionOfLigSiteA, readPositionOfLigSiteB) + ignoreFirstNBases - readPosition;
				if (distance1 > 0) {
					if (ENABLE_TRACE) {
						logger.trace("Ignoring indel too close to barcode C " + refPosition + " " + (Math.max(readPositionOfLigSiteA, readPositionOfLigSiteB) - 1 + ignoreFirstNBases) + "; " + getFullName());
					}
					return distance1;
				}
			} else {
				distance1 = readPosition - (Math.min(readPositionOfLigSiteA, readPositionOfLigSiteB) - ignoreFirstNBases);
				if (ENABLE_TRACE) {
					logger.trace("readPosition=" + readPosition +
							"readPositionOfLigSiteA=" + readPositionOfLigSiteA +
							" readPositionOfLigSiteB=" + readPositionOfLigSiteB +
							" ignoreFirstNBases=" + ignoreFirstNBases +
							" finalProduct=" + distance1 +
							" distance0=" + distance0);
				}
				if (distance1 > 0)  {
					if (ENABLE_TRACE) {
						logger.trace("Ignoring indel too close to barcode D; " + refPosition + " " + " mate ligation site " + refPositionOfMateLigationSite + "   " + (Math.min(readPositionOfLigSiteA, readPositionOfLigSiteB) + 1 - ignoreFirstNBases) + "; " + getFullName());
					}
					return distance1;
				}
			}
		} else {
			//Did not use to have mate details; this does not account for barcode alignment in the mate
			if (readOnNegativeStrand) {
				distance1 = getMateAlignmentStart() + ignoreFirstNBases - refPosition;
				tooCloseToBarcode = distance1 > 0;
				if (ENABLE_TRACE) {
					logger.trace("Ignoring indel too close to barcode E " + 
							(getMateAlignmentStart() + ignoreFirstNBases) + "; " +
							getFullName());
				}
			} else {
				distance1 = refPosition - (getMateAlignmentStart() + record.getReadLength() - ignoreFirstNBases);
				tooCloseToBarcode = distance1 > 0;
				if (ENABLE_TRACE && tooCloseToBarcode) {
					logger.trace("Ignoring indel too close to barcode F " +
							(getMateAlignmentStart() + record.getReadLength()) + "; "
							+ getFullName());
				}
			}
		}
		
		return Math.max(distance0, distance1);
	}
	
	public int getRefPositionOfMateLigationSite() {
		return getReadNegativeStrandFlag() ?
			getMateUnclippedStart() :
			getMateUnclippedEnd();
	}

	public static int getNBarcodeBasesAtAlignmentEnd() {
		return 0;
	}
		
	int getNBarcodeBasesAtAlignmentStart() {
		if (getReadNegativeStrandFlag()) {
			return 0;
		}
		
		//Of the first TOTAL_BARCODE_LENGTH bases of the read, compute how many are part of an insertion
		List<CigarElement> cElmnts = record.getCigar().getCigarElements();
		int ceIndex = 0;
		int nInsertedBases = 0;
		int nProcessedBases = 0;
		while (true) {
			CigarElement c = cElmnts.get(ceIndex);
			int blockLength = c.getLength();
			int nTakenBases = Math.min(blockLength, UNCLIPPED_BARCODE_LENGTH - nProcessedBases);
			nProcessedBases += nTakenBases;
			
			if (c.getOperator() == CigarOperator.INSERTION) {
				nInsertedBases += nTakenBases;
			}
			
			if (nProcessedBases == UNCLIPPED_BARCODE_LENGTH) {
				break;	
			} else if (nProcessedBases > UNCLIPPED_BARCODE_LENGTH) {
				throw new AssertionFailedException("Problem with cigar " + record.getCigarString() + "; " + c.toString() + "; " + nProcessedBases + "; " + nTakenBases);
			}
			ceIndex++;
		}

		List<AlignmentBlock> alignmentBlocks = record.getAlignmentBlocks();
		int readStart = alignmentBlocks.get(0).getReadStart() - 1;
		
		int adjustment = readStart < (UNCLIPPED_BARCODE_LENGTH - nInsertedBases) ? 
				(UNCLIPPED_BARCODE_LENGTH - nInsertedBases) - readStart : 0;
		return adjustment;
	}
	
	public int getRefAlignmentStartNoBarcode() {
		int referenceStart = getAlignmentStart();
		if (referenceStart < 0) {
			throw new AssertionFailedException();
		}
		if (getReadNegativeStrandFlag()) {
			return referenceStart;
		} else {
			int adjustment = getNBarcodeBasesAtAlignmentStart();
			return referenceStart + adjustment;
		}
	}
	
	public int getRefAlignmentEndNoBarcode() {
		int referenceEnd = getAlignmentEnd();
		if (referenceEnd < 0) {
			throw new AssertionFailedException();
		}
		if (getReadNegativeStrandFlag()) {
			int adjustment = getNBarcodeBasesAtAlignmentEnd();
			return referenceEnd - adjustment;
		} else {
			return referenceEnd;
		}
	}

	public int getMateRefAlignmentStartNoBarcode() {
		checkMate();
		return mate == null ? Integer.MAX_VALUE : nonNullify(mate).getRefAlignmentStartNoBarcode();
	}
	
	public int getMateRefAlignmentEndNoBarcode() {
		checkMate();
		return mate == null ? Integer.MAX_VALUE : nonNullify(mate).getRefAlignmentEndNoBarcode();
	}
	
	public Integer getInsertSizeNoBarcodes(boolean fallBackOnNoBarcodeValue) {
		int inferredInsertSize = record.getInferredInsertSize();
		if (inferredInsertSize == 0) {
			return 0;
		}
		
		checkMate();
		
		if (mate == null) {
			if (fallBackOnNoBarcodeValue) 
				return inferredInsertSize;
			else
				return null;
		}
		
		int actualSize;
		if (inferredInsertSize < 0) {
			actualSize = getMateRefAlignmentStartNoBarcode() - getRefAlignmentEndNoBarcode() - 1;
		} else {
			actualSize = getMateRefAlignmentEndNoBarcode() - getRefAlignmentStartNoBarcode() + 1;
		}
		
		return actualSize;
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
															) ||
					getMate() == null;
		}
		return formsWrongPair;
	}

	public SequenceLocation getMateLocation() {
		int mateReferenceIndex = record.getMateReferenceIndex();
		if (mateReferenceIndex == -1) {
			return null;
		}
		return new SequenceLocation(record.getMateReferenceName(), getMateAlignmentStart());
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
	
	public int getAlignmentStartNoBarcode() {
		return getAlignmentStart() + getNBarcodeBasesAtAlignmentStart();
	}
	
	public int getAlignmentEndNoBarcode() {
		return getAlignmentEnd() - getNBarcodeBasesAtAlignmentEnd();
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
	
	public int getMateAlignmentStartNoBarcode() {
		checkMate();
		if (mate == null) {
			return record.getMateAlignmentStart() - 1;
		}
		return nonNullify(mate).getAlignmentStartNoBarcode();
	}

	public int getMateAlignmentEndNoBarcode() {
		checkMate();
		if (mate == null) {
			return Integer.MAX_VALUE;
		}
		return nonNullify(mate).getAlignmentEndNoBarcode();
	}
	
	/** Indexing starts at 0
	 * 
	 * @return
	 */
	public int getMateAlignmentEnd() {
		checkMate();
		return mate == null ? Integer.MAX_VALUE : nonNullify(mate).getAlignmentEnd();
	}
	
	public int getMateUnclippedEnd() {
		checkMate();
		if (mate == null) {
			return Integer.MAX_VALUE;
		}
		return nonNullify(mate).getUnclippedEnd();
	}

	public int getUnclippedEnd() {
		return record.getUnclippedEnd() - 1;
	}
	
	public int getMateUnclippedStart() {
		checkMate();
		if (mate == null) {
			return Integer.MAX_VALUE;
		}
		return nonNullify(mate).getUnclippedStart();
	}

	public static byte @NonNull[] getNs() {
		Objects.requireNonNull(Ns);
		return Ns;
	}

	public int getMappingQuality() {
		return record.getMappingQuality();
	}

}
