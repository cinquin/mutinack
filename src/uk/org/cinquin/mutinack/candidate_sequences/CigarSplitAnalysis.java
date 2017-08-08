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

import contrib.net.sf.samtools.Cigar;
import contrib.net.sf.samtools.CigarElement;
import contrib.net.sf.samtools.CigarOperator;
import contrib.net.sf.samtools.TextCigarCodec;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.misc_util.Assert;

public class CigarSplitAnalysis {

	private static class BlockInfo {
		CigarElement e;
		@SuppressWarnings("unused")
		int leftNs, rightNs;
		int lengthWithoutNs;
	}

	public final Boolean leftMatchNoRevcomp;
	public final int breakpointPosition;
	public final int localBreakpointPosition;

	public CigarSplitAnalysis(final ExtendedSAMRecord record) {
		final Cigar c = record.getCigar();
		final byte[] bases = record.record.getReadBases();
		final boolean readOnNegativeStrand = record.record.getReadNegativeStrandFlag();
		final int alignmentStart = record.record.getAlignmentStart();
		final int alignmentEnd = record.record.getAlignmentEnd();

		BlockInfo largestMatch = new BlockInfo();
		largestMatch.e = new CigarElement(0, CigarOperator.MATCH_OR_MISMATCH);
		int largestMatchPositionWithHardClip = 0;
		int currentPosition = 0;
		int currentPositionWithHardClip = 0;

		for (CigarElement e: c.getCigarElements()) {
			if (e.getOperator() == CigarOperator.MATCH_OR_MISMATCH) {
				final int leftNs = nNsAtReadEnd(bases, false, currentPosition, currentPosition + e.getLength() - 1);
				final int rightNs = nNsAtReadEnd(bases, true, currentPosition, currentPosition + e.getLength() - 1);
				Assert.isTrue(leftNs >= 0);
				Assert.isTrue(rightNs >= 0);
				final int lengthWithoutNs = e.getLength() - leftNs - rightNs;
				if (lengthWithoutNs > largestMatch.lengthWithoutNs) {
					largestMatch.e = e;
					largestMatch.leftNs = leftNs;
					largestMatch.rightNs = rightNs;
					largestMatch.lengthWithoutNs = lengthWithoutNs;
					largestMatchPositionWithHardClip = currentPositionWithHardClip;
				}
			}
			if (e.getOperator().consumesReadBases()) {
				currentPosition += e.getLength();
			}
			currentPositionWithHardClip += e.getLength();
		}
		final int totalReadLengthWithHardClip = currentPositionWithHardClip;

		Boolean leftMatch = null;
		int leftClipping = 0;
		int rightClipping = 0;
		int largestMatchEndP1WithHardClip = 0;

		if (largestMatch.lengthWithoutNs == 0) {

		} else {
			largestMatchEndP1WithHardClip = largestMatchPositionWithHardClip + largestMatch.e.getLength();

			boolean pastLeft = false;
			for (CigarElement e: c.getCigarElements()) {
				CigarOperator op = e.getOperator();
				if (op != CigarOperator.HARD_CLIP && op != CigarOperator.SOFT_CLIP) {
					pastLeft = true;
				} else {
					if (pastLeft) {
						rightClipping += e.getLength();
					} else {
						leftClipping += e.getLength();
					}
				}
			}

			if (leftClipping < 0 || rightClipping < 0) {
				System.err.println("leftClipping=" + leftClipping + "; rightClipping=" + rightClipping +
						"; readOnNegativeStrand=" + readOnNegativeStrand + "; cigar=" + c.toString() + "; record=" + record.toString());
			}
			leftMatch = leftClipping < rightClipping;
		}
		leftMatchNoRevcomp = leftMatch == null ? null : leftMatch ^ readOnNegativeStrand;
		breakpointPosition = leftMatch == null ? -1 :
			(leftMatch ? alignmentEnd : (alignmentStart - 1));
		int localBreakpointPosition0 = leftMatch == null ? -1 :
			(leftMatch ?
					largestMatchEndP1WithHardClip
				:
					(largestMatchPositionWithHardClip - 1));
		if (readOnNegativeStrand) {
			localBreakpointPosition = totalReadLengthWithHardClip - localBreakpointPosition0;
		} else {
			localBreakpointPosition = localBreakpointPosition0;
		}
	}

	private static int nNsAtReadEnd(byte[] bytes, boolean reverse, int leftBound, int rightBound) {
		final int increment = reverse ? -1 : 1;
		final int start = reverse ? rightBound : leftBound;
		int result = 0;
		for (int i = start; i != leftBound -1 && i != rightBound + 1; i += increment) {
			if (bytes[i] == 'N') {
				result++;
			} else {
				break;
			}
		}
		return result;
	}

	public static int getLengthAlignedRegion(String s) {
		Cigar c = TextCigarCodec.getSingleton().decode(s);
		int result = 0;
		for (CigarElement e: c.getCigarElements()) {
			CigarOperator op = e.getOperator();
			if (op != CigarOperator.DELETION && op != CigarOperator.SOFT_CLIP && op != CigarOperator.HARD_CLIP) {
				result += e.getLength();
			}
		}
		return result;
	}

}
