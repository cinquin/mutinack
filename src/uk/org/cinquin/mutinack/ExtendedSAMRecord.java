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
import java.util.function.Supplier;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.stanford.nlp.util.HasInterval;
import contrib.edu.stanford.nlp.util.Interval;
import contrib.net.sf.samtools.Cigar;
import contrib.net.sf.samtools.CigarElement;
import contrib.net.sf.samtools.CigarOperator;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileReader.QueryInterval;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import contrib.nf.fr.eraasoft.pool.PoolException;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TObjectByteHashMap;
import uk.org.cinquin.mutinack.candidate_sequences.ExtendedAlignmentBlock;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

/**
 * Hashcode and equality based on read name + first or second of pair.
 * @author olivier
 *
 */
public final class ExtendedSAMRecord implements HasInterval<Integer> {

	static final Logger logger = LoggerFactory.getLogger(ExtendedSAMRecord.class);

	public boolean discarded = false;
	private final @Nullable Map<String, ExtendedSAMRecord> extSAMCache;
	public final @NonNull SAMRecord record;
	private final @NonNull String name;
	private @Nullable ExtendedSAMRecord mate;
	private volatile boolean triedRetrievingMateFromFile = false;
	private final @NonNull String mateName;
	private final int hashCode;
	public @Nullable DuplexRead duplexRead;
	private byte @Nullable[] mateVariableBarcode;
	public final byte @NonNull[] variableBarcode;
	public final byte @Nullable[] constantBarcode;
	public final @NonNull SequenceLocation location;
	final int medianPhred;
	final float averagePhred;
	private final Cigar cigar;
	/**
	 * Length of read ignoring trailing Ns.
	 */
	public final int effectiveLength;
	int nReferenceDisagreements = 0;
	public static final byte PHRED_NO_ENTRY = -1;
	public final @NonNull TObjectByteHashMap<SequenceLocation> basePhredScores =
		new TObjectByteHashMap<>(150, 0.5f, PHRED_NO_ENTRY);
	private int nClipped = -1;
	private Boolean formsWrongPair;
	public boolean processed = false;
	public boolean duplexAlreadyVisitedForStats = false;

	public final int xLoc, yLoc;
	public final String runAndTile;
	public boolean opticalDuplicate = false;
	public boolean hasOpticalDuplicates = false;
	public boolean visitedForOptDups = false;
	public int tempIndex0 = -1, tempIndex1 = -1;

	private final @NonNull MutinackGroup groupSettings;
	private final @NonNull Mutinack analyzer;

	public static @NonNull String getReadFullName(SAMRecord rec, boolean getMate) {
		return (rec.getReadName() + "--" + ((getMate ^ rec.getFirstOfPairFlag())? "1" : "2") + "--" +
			(getMate ? rec.getMateAlignmentStart() : rec.getAlignmentStart())) +
			(!getMate && rec.getSupplementaryAlignmentFlag() ? "--suppl" : "")/*.intern()*/;
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
		if (obj == null) {
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
			@NonNull List<@NonNull AnalysisStats> stats,
			@NonNull Mutinack analyzer, @NonNull SequenceLocation location,
			@Nullable Map<String, ExtendedSAMRecord> extSAMCache) {

		this.groupSettings = Objects.requireNonNull(analyzer.groupSettings);
		this.analyzer = Objects.requireNonNull(analyzer);
		this.extSAMCache = extSAMCache;
		this.name = Objects.requireNonNull(fullName);
		this.record = Objects.requireNonNull(rec);
		this.cigar = rec.getCigar();
		this.location = location;
		hashCode = fullName.hashCode();
		mateName = getReadFullName(rec, true);

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

		int sumBaseQualities0 = 0;
		int nConsidered0 = 0;
		TIntList qualities = new TIntArrayList(effectiveLength);
		int n =  Math.min(effectiveLength, readLength / 2);
		for (int index1 = 0; index1 < n; index1++) {
			nConsidered0++;
			final byte b = baseQualities[index1];
			sumBaseQualities0 += b;
			stats.forEach(s -> s.nProcessedBases.add(location, 1));
			stats.forEach(s -> s.phredSumProcessedbases.add(b));
			qualities.add(b);
		}

		int avQuality = sumBaseQualities0 / nConsidered0;
		stats.forEach(s-> s.averageReadPhredQuality0.insert(avQuality));

		int sumBaseQualities1 = 0;
		int nConsidered1 = 0;
		for (int index1 = readLength / 2; index1 < effectiveLength; index1++) {
			nConsidered1++;
			final byte b = baseQualities[index1];
			sumBaseQualities1 += b;
			stats.forEach(s -> s.nProcessedBases.add(location, 1));
			stats.forEach(s -> s.phredSumProcessedbases.add(b));
			qualities.add(b);
		}
		if (nConsidered1 > 0) {
			int avQuality1 = sumBaseQualities1 / nConsidered1;
			stats.forEach(s -> s.averageReadPhredQuality1.insert(avQuality1));
		}

		qualities.sort();
		medianPhred = qualities.get(qualities.size() / 2);
		averagePhred = (sumBaseQualities0 + sumBaseQualities1) / ((float) (nConsidered0 + nConsidered1));
		stats.forEach(s -> s.medianReadPhredQuality.insert(medianPhred));

		Assert.isTrue(rec.getUnclippedEnd() - 1 >= getAlignmentEnd(),
			(Supplier<Object>) () -> "" + (rec.getUnclippedEnd() - 1),
			(Supplier<Object>) this::toString,
			"Unclipped end is %s for read %s");
		Assert.isTrue(rec.getAlignmentStart() - 1 >= getUnclippedStart());

		final @NonNull String fullBarcodeString;
		String bcAttr = (String) record.getAttribute("BC");
		if (groupSettings.getVariableBarcodeEnd() > 0) {
			final int firstBarcodeInNameIndex = name.indexOf("BC:Z:");
			if (bcAttr == null) {
				if (firstBarcodeInNameIndex == -1) {
					throw new ParseRTException("Missing first barcode for read " + name +
						' ' + record.toString());
				}
				final int index;
				if (record.getFirstOfPairFlag()) {
					index = firstBarcodeInNameIndex;
				} else {
					index = name.indexOf("BC:Z:", firstBarcodeInNameIndex + 1);
					if (index == -1) {
						throw new ParseRTException("Missing second barcode for read " + name +
							' ' + record.toString());
					}
				}
				fullBarcodeString = nonNullify(name.substring(index + 5, name.indexOf('_', index)));
			} else {
				fullBarcodeString = bcAttr;
			}
			variableBarcode = Util.getInternedVB(fullBarcodeString.substring(
				groupSettings.getVariableBarcodeStart(), groupSettings.getVariableBarcodeEnd() + 1).getBytes());
			constantBarcode = Util.getInternedCB(fullBarcodeString.substring(
				groupSettings.getConstantBarcodeStart(), groupSettings.getConstantBarcodeEnd() + 1).getBytes());
			if (firstBarcodeInNameIndex > -1) {
				mateVariableBarcode = getMateBarcode(name, firstBarcodeInNameIndex);
			}
		} else {
			variableBarcode = EMPTY_BARCODE;
			constantBarcode = DUMMY_BARCODE;
		}

		String readName = record.getReadName();
		int endFirstChunk = nthIndexOf(readName, ':', 5);
		//Interning below required for equality checks performed in optical duplicate detection
		runAndTile = record.getReadName().substring(0, endFirstChunk).intern();
		byte[] readNameBytes = readName.getBytes();

		xLoc = parseInt(readNameBytes, endFirstChunk + 1);
		int endXLoc = readName.indexOf(':', endFirstChunk + 1);
		yLoc = parseInt(readNameBytes, endXLoc + 1);
		//interval = Interval.toInterval(rec.getAlignmentStart(), rec.getAlignmentEnd());
	}

	private byte[] getBarcode(String s) {
		if (record.getFirstOfPairFlag()) {
			return getBarcodeFromString(s, true, 0);
		} else {
			return getBarcodeFromString(s, false, 0);
		}
	}

	private byte[] getMateBarcode(String s, int firstBarcodeInNameIndex) {
		if (record.getFirstOfPairFlag()) {
			return getBarcodeFromString(s, false, firstBarcodeInNameIndex);
		} else {
			return getBarcodeFromString(s, true, firstBarcodeInNameIndex);
		}
	}

	@SuppressWarnings("static-access")
	private byte[] getBarcodeFromString(String s, boolean firstOccurrence, int startIndex) {
		int index = s.indexOf("BC:Z:", startIndex);
		if (!firstOccurrence) {
			return getBarcodeFromString(s, true, index + 1);
		}
		return Util.getInternedVB(s.substring(index + 5, s.indexOf('_', index)).substring(
			groupSettings.getVariableBarcodeStart(), groupSettings.getVariableBarcodeEnd() + 1).getBytes());
	}

	private static boolean LENIENT_COORDINATE_PARSING = true;

	private static int parseInt(final byte[] b, final int fromIndex) {
		final int end = b.length - 1;
		int i = fromIndex;
		int result = 0;
		while (i <= end) {
			if (b[i] == ':') {
				return result;
			}
			byte character = b[i];
			if (character < 48 || character > 57) {
				if (LENIENT_COORDINATE_PARSING) {
					return result;
				}
				throw new ParseRTException("Character " + character + " is not a digit when parsing " + new String(b)
					+ " from " + fromIndex);
			}
			result = 10 * result + b[i] - 48;
			i++;
		}
		return result;
	}

	private static int nthIndexOf(final String s, final char c, final int n) {
		int i = -1;
		int found = 0;
		while (found < n) {
			i = s.indexOf(c, i + 1);
			found++;
		}
		return i;
	}

	private static final byte @NonNull[] EMPTY_BARCODE = new byte [0];
	private static final byte @NonNull[] DUMMY_BARCODE = {'N', 'N', 'N'};

	public ExtendedSAMRecord(@NonNull SAMRecord rec,
			@NonNull Mutinack analyzer, @NonNull SequenceLocation location,
			@NonNull Map<String, ExtendedSAMRecord> extSAMCache) {
		this(rec, getReadFullName(rec, false),
				analyzer.stats, analyzer, location, extSAMCache);
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
		return (discarded ? "DISCARDED" : "" ) + name + ": " + "startNoBC: " + getAlignmentStart() +
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
		List<CigarElement> cElmnts = getCigar().getCigarElements();
		final int nElmnts = cElmnts.size();
		int ceIndex = 0;
		int nReadBasesProcessed = getAlignmentStart() - getUnclippedStart();

		final int nBasesToAlign = refPosition - getAlignmentStart();
		int nBasesAligned = 0;
		while (ceIndex < nElmnts && nBasesAligned < nBasesToAlign) {
			final CigarElement c = cElmnts.get(ceIndex);
			final int blockLength = c.getLength();

			switch(c.getOperator()) {
				case M:
					int nTakenBases = Math.min(blockLength, nBasesToAlign - nBasesAligned);
					nBasesAligned += nTakenBases;
					nReadBasesProcessed += nTakenBases;
					break;
				case I:
					nReadBasesProcessed += blockLength;
					break;
				case D:
				case N:
					nBasesAligned += blockLength;
					break;
				default://Nothing to do
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

	public Cigar getCigar() {
		return cigar;
	}

	private int intronAdjustment(final int readPosition, boolean reverse) {

		if (!groupSettings.isRnaSeq()) {
			return 0;
		}

		SettableInteger nReadBases = new SettableInteger(0);
		SettableInteger intronBases = new SettableInteger(0);

		MutableList<CigarElement> cigarElements = Lists.mutable.withAll(getCigar().getCigarElements());
		if (reverse) {
			cigarElements.reverseThis();
		}

		cigarElements.detect(e -> {
			CigarOperator operator = e.getOperator();
			int truncatedLength = operator.consumesReadBases() ?
					Math.min(e.getLength(), readPosition - nReadBases.get())
				:
					e.getLength();
			if (operator.consumesReadBases()) {
				nReadBases.addAndGet(truncatedLength);
			}
			if (operator == CigarOperator.N) {
				intronBases.addAndGet(e.getLength());
			}
			if (nReadBases.get() == readPosition) {
				return true;
			}
			return false;
		});

		Assert.isTrue(nReadBases.get() == readPosition);

		return intronBases.get();
	}

	public static final int NO_MATE_POSITION = Integer.MAX_VALUE - 1000;

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
		Assert.isFalse(referenceEnd < 0, () -> "Negative alignment end in read " + this);
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
		return record.getInferredInsertSize();
	}

	public ExtendedSAMRecord getMate() {
		checkMate();
		return mate;
	}

	//Adapted from SamPairUtil
	public PairOrientation getPairOrientation() {
		final boolean readIsOnReverseStrand = record.getReadNegativeStrandFlag();

		if (record.getReadUnmappedFlag() || !record.getReadPairedFlag() || record.getMateUnmappedFlag()) {
			throw new IllegalArgumentException("Invalid SAMRecord: " + record.getReadName() + ". This method only works for SAMRecords " +
				"that are paired reads with both reads aligned.");
		}

		if (readIsOnReverseStrand == record.getMateNegativeStrandFlag()) {
			return PairOrientation.TANDEM;
		}

		final int positiveStrandFivePrimePos =
			readIsOnReverseStrand ?
				getMateOffsetUnclippedStart()
			:
				getOffsetUnclippedStart();

		final int negativeStrandFivePrimePos =
			readIsOnReverseStrand ?
				getOffsetUnclippedEnd()
			:
				getMateOffsetUnclippedEnd();

		return
			positiveStrandFivePrimePos < negativeStrandFivePrimePos ?
				PairOrientation.FR
			:
				PairOrientation.RF;
	}

	public boolean formsWrongPair() {
		final PairOrientation po;
		if (formsWrongPair == null) {
			formsWrongPair = record.getReadPairedFlag() && (
					record.getReadUnmappedFlag() ||
					record.getMateUnmappedFlag() ||
					!record.getReferenceIndex().equals(record.getMateReferenceIndex()) ||
					(po = getPairOrientation()) == PairOrientation.TANDEM ||
					po == PairOrientation.RF
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

	public ExtendedSAMRecord checkMate() {
		if (mate != null) {
			return mate;
		}
		if (record.getMateUnmappedFlag()) {
			return null;
		}
		if (extSAMCache != null) {
			mate = extSAMCache.get(mateName);
			if (mate != null) {
				return mate;
			}
		}
		if (Math.abs(record.getAlignmentStart() - record.getMateAlignmentStart()) < analyzer.param.maxInsertSize &&
				Objects.equals(record.getReferenceIndex(), record.getMateReferenceIndex())) {
			return null;
		}
		if (!triedRetrievingMateFromFile) {
			synchronized (this) {
				if (!triedRetrievingMateFromFile) {
					mate = getRead(analyzer, record.getReadName(), !record.getFirstOfPairFlag(),
						new SequenceLocation(record.getMateReferenceName(), groupSettings.indexContigNameReverseMap,
							record.getMateAlignmentStart() - 1, false) , -1, 1);
					triedRetrievingMateFromFile = true;
				}
			}
		}
		return mate;
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

	private SequenceLocation unclippedEndHelper(boolean noMatePosition) {
		return new SequenceLocation(getLocation().contigIndex, groupSettings.getContigNames(),
			noMatePosition ? NO_MATE_POSITION : record.getUnclippedEnd() - 1 - intronAdjustment(16, true));
	}

	public SequenceLocation getOffsetUnclippedEndLoc() {
		return unclippedEndHelper(false);
	}

	public int getOffsetUnclippedEnd() {
		return getOffsetUnclippedEndLoc().position;
	}

	private SequenceLocation unclippedStartHelper(boolean noMatePosition) {
		return new SequenceLocation(getLocation().contigIndex, groupSettings.getContigNames(),
			noMatePosition ? NO_MATE_POSITION : record.getUnclippedStart() - 1 + intronAdjustment(16, false));
	}

	public SequenceLocation getOffsetUnclippedStartLoc() {
		return unclippedStartHelper(false);
	}

	public int getOffsetUnclippedStart() {
		return getOffsetUnclippedStartLoc().position;
	}

	public int getMateOffsetUnclippedEnd() {
		checkMate();
		if (mate == null) {
			return NO_MATE_POSITION;
		}
		return nonNullify(mate).getOffsetUnclippedEnd();
	}

	public SequenceLocation getMateOffsetUnclippedEndLoc() {
		checkMate();
		if (mate == null) {
			return unclippedEndHelper(true);
		}
		return nonNullify(mate).getOffsetUnclippedEndLoc();
	}

	public int getMateOffsetUnclippedStart() {
		checkMate();
		if (mate == null) {
			return NO_MATE_POSITION;
		}
		return nonNullify(mate).getOffsetUnclippedStart();
	}

	public SequenceLocation getMateOffsetUnclippedStartLoc() {
		checkMate();
		if (mate == null) {
			return unclippedStartHelper(true);
		}
		return nonNullify(mate).getOffsetUnclippedStartLoc();
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

	public boolean overlapsWith(SequenceLocation otherLocation) {
		if (getRefAlignmentStart() > otherLocation.position ||
			getRefAlignmentEnd() < otherLocation.position ||
			otherLocation.contigIndex != getReferenceIndex()) {
			return false;
		}
		return true;
	}

	boolean duplexLeft() {
		return formsWrongPair() ?
				getOffsetUnclippedStart() <= getMateOffsetUnclippedStart()
				: getReadPositiveStrand();
	}

	public @NonNull SequenceLocation getLocation() {
		return location;
	}

	/**
	 * Not necessarily the same as that of SAMRecord
	 * @return
	 */
	public int getReferenceIndex() {
		return location.contigIndex;
	}

	public @NonNull String getReferenceName() {
		return location.getContigName();
	}

	public int getxLoc() {
		return xLoc;
	}

	public int getyLoc() {
		return yLoc;
	}

	public String getRunAndTile() {
		return runAndTile;
	}

	public boolean isOpticalDuplicate() {
		return opticalDuplicate;
	}

	public float getAveragePhred() {
		return averagePhred;
	}

	public List<ExtendedAlignmentBlock> getAlignmentBlocks() {
		return ExtendedAlignmentBlock.getAlignmentBlocks(getCigar(), record.getAlignmentStart(), "read cigar");
	}

	/**
	 *
	 * @param analyzer
	 * @param name
	 * @param firstOfPair
	 * @param location
	 * @param avoidAlignmentStart0Based 	 Used to make sure we don't just retrieve the same read as the original,
	 * in the case where both alignments are close together
	 * @param windowHalfWidth
	 * @return
	 */
	public static @Nullable ExtendedSAMRecord getRead(Mutinack analyzer, String name, boolean firstOfPair,
			SequenceLocation location, int avoidAlignmentStart0Based, int windowHalfWidth) {

		SAMFileReader bamReader;
		try {
			bamReader = analyzer.readerPool.getObj();
		} catch (PoolException e) {
			throw new RuntimeException(e);
		}

		try {
			final QueryInterval[] bamContig = {
				bamReader.makeQueryInterval(location.contigName, Math.max(location.position + 1 - windowHalfWidth, 1),
					location.position + 1 + windowHalfWidth)};

			try (SAMRecordIterator it = bamReader.queryOverlapping(bamContig)) {
				while (it.hasNext()) {
					SAMRecord record = it.next();
					if (record.getReadName().equals(name) && record.getFirstOfPairFlag() == firstOfPair &&
							record.getAlignmentStart() - 1 != avoidAlignmentStart0Based) {
						return SubAnalyzer.getExtendedNoCaching(record,
							new SequenceLocation(location.contigName, analyzer.groupSettings.indexContigNameReverseMap,
								record.getAlignmentStart() - 1, false), analyzer);
					}
				}
				return null;
			}
		} finally {
			analyzer.readerPool.returnObj(bamReader);
		}
	}
}
