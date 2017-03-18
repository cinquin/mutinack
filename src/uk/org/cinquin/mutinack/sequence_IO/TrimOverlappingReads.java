/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 * Copyright 2013 Pierre Lindenbaum
 * https://github.com/lindenb/jvarkit
 * Modified by Olivier Cinquin, 2017
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package uk.org.cinquin.mutinack.sequence_IO;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import contrib.net.sf.picard.util.CigarUtil;
import contrib.net.sf.samtools.Cigar;
import contrib.net.sf.samtools.CigarElement;
import contrib.net.sf.samtools.CigarOperator;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMTag;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;

public class TrimOverlappingReads {

	/**
	 * Checks to see whether the ends of the reads overlap and soft clips reads
	 * them if necessary.
	 */
	public static void clipForNoOverlap(final SAMRecord read1, final SAMRecord read2,
			final ExtendedSAMRecord er1, final ExtendedSAMRecord er2) {
		// If both reads are mapped, see if we need to clip the ends due to small
		// insert size
		if (!(read1.getReadUnmappedFlag() || read2.getReadUnmappedFlag()) &&
					read1.getReadNegativeStrandFlag() != read2.getReadNegativeStrandFlag() &&
					!read1.getSupplementaryAlignmentFlag() && !read2.getSupplementaryAlignmentFlag()) {
			final SAMRecord pos = (read1.getReadNegativeStrandFlag()) ? read2 : read1;
			final SAMRecord neg = (read1.getReadNegativeStrandFlag()) ? read1 : read2;

			final ExtendedSAMRecord poser = (read1.getReadNegativeStrandFlag()) ? er2 : er1;

			final int posDiff = pos.getReadLength() -
				poser.referencePositionToReadPosition(neg.getAlignmentStart() - 1);

			// Innies only -- do we need to do anything else about jumping libraries?
			if (posDiff > 0) {
				final List<CigarElement> elems = new ArrayList<>(pos.getCigar().getCigarElements());
				Collections.reverse(elems);
				final int clipFrom = Math.max(1, pos.getReadLength() - posDiff + 1);
				try {
					CigarUtil.softClip3PrimeEndOfRead(pos, Math.min(pos.getReadLength(), clipFrom));
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem clipping read from " + Math.min(pos.getReadLength(), clipFrom) +
						"; pos.getReadLength()=" + pos.getReadLength() + "; clipFrom=" + clipFrom + "; read cigar: " +
						pos.getCigarString() + "; name: " + pos.getReadName() + "; bases: " + new String(pos.getReadBases()), e);
				}
				removeNmMdAndUqTags(pos); // these tags are now invalid!
			}
		}
	}

	/** Returns the number of soft-clipped bases until a non-soft-clipping element is encountered. */
	@SuppressWarnings("unused")
	private static int lengthOfSoftClipping(Iterator<CigarElement> iterator) {
		int clipped = 0;
		while (iterator.hasNext()) {
			final CigarElement elem = iterator.next();
			if (elem.getOperator() != CigarOperator.SOFT_CLIP && elem.getOperator() != CigarOperator.HARD_CLIP) break;
			if (elem.getOperator() == CigarOperator.SOFT_CLIP) clipped = elem.getLength();
		}

		return clipped;
	}

	/** Removes the NM, MD, and UQ tags.  This is useful if we modify the read and are not able to recompute these tags,
	 * for example when no reference is available.
	 * @param rec the record to modify.
	 */
	private static void removeNmMdAndUqTags(final SAMRecord rec) {
		rec.setAttribute(SAMTag.NM.name(), null);
		rec.setAttribute(SAMTag.MD.name(), null);
		rec.setAttribute(SAMTag.UQ.name(), null);
	}

	//TODO Should probably add a hard-clipping Cigar element when removing bases
	//(or increase the length of pre-existing H)
	public static void removeClippedBases(SAMRecord rec) {

		if (rec.getReadUnmappedFlag()) {
			return;
		}

		final Cigar cigar = rec.getCigar();
		if (cigar == null) {
			return;
		}

		final byte bases[] = rec.getReadBases();
		if (bases == null) {
			throw new IllegalArgumentException();
		}

		final ArrayList<CigarElement> L = new ArrayList<>();
		final ByteArrayOutputStream nseq = new ByteArrayOutputStream();
		final ByteArrayOutputStream nqual = new ByteArrayOutputStream();

		final byte quals[] = rec.getBaseQualities();

		int indexBases = 0;
		for(CigarElement ce: cigar.getCigarElements()) {
			switch(ce.getOperator()) {
				case S:
					indexBases += ce.getLength();
					break;
				case H://fall-through
				case P://fall-through
				case N://fall-through
				case D:
					L.add(ce);
					break;
				case I:
				case EQ:
				case X:
				case M:
					L.add(ce);
					nseq.write(bases, indexBases, ce.getLength());
					if (quals.length!=0) {
						nqual.write(quals, indexBases, ce.getLength());
					}
					indexBases += ce.getLength();
					break;
				default:
					throw new RuntimeException("Unsupported Cigar opertator: " + ce.getOperator());
			}
		}
		if (indexBases != bases.length) {
			throw new RuntimeException("ERRROR " + rec.getCigarString());
		}

		if (L.size() == cigar.numCigarElements()) {
			return;
		}

		rec.setCigar(new Cigar(L));
		rec.setReadBases(nseq.toByteArray());
		if (quals.length!=0) {
			rec.setBaseQualities(nqual.toByteArray());
		}
	}

}
