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
import static uk.org.cinquin.mutinack.misc_util.Util.basesEqual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.edu.standford.nlp.util.HasInterval;
import contrib.edu.standford.nlp.util.Interval;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.SimpleCounter;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * Equality and hashcode ignore list of reads assigned to duplex, quality, and roughLocation,
 * among other things.
 * @author olivier
 *
 */
public final class DuplexRead implements HasInterval<Integer> {
	
	public byte @NonNull[] leftBarcode, rightBarcode;
	public SequenceLocation leftAlignmentStart, rightAlignmentStart, leftAlignmentEnd, rightAlignmentEnd;
	public final @NonNull List<@NonNull ExtendedSAMRecord> topStrandRecords = new ArrayList<>(100), 
			bottomStrandRecords = new ArrayList<>(100);
	public int totalNRecords = -1;
	public final @NonNull List<String> issues = new ArrayList<>(10);
	private @Nullable Interval<Integer> interval;
	//Only used for debugging
	public boolean invalid = false;
	public int nReadsWrongPair = 0;
	
	public static int intervalSlop = 0;
	
	/**
	 * Quality factoring in number of reads for top or bottom strand, percent consensus for
	 * reads from a given strand. Minimum and maximum across all base positions in duplex.
	*/
	@SuppressWarnings("null")
	@NonNull Quality minQuality = Quality.MAXIMUM, maxQuality = Quality.MINIMUM;
	DetailedQualities localQuality;
	SequenceLocation roughLocation;
	float referenceDisagreementRate;
	int averageNClipped;
	int position0;
	private int position3;
	private int maxDistanceToLig = Integer.MIN_VALUE;
	
	public DuplexRead(byte @NonNull[] leftBarcode, byte @NonNull[] rightBarcode) {
		this.leftBarcode = leftBarcode;
		this.rightBarcode = rightBarcode;
	}
	
	public void assertAllBarcodesEqual() {
		if (DebugControl.NONTRIVIAL_ASSERTIONS) {
			final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords =
					new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
			allDuplexRecords.addAll(topStrandRecords);
			allDuplexRecords.addAll(bottomStrandRecords);
			allDuplexRecords.stream().forEach(r -> {
				if (r.record.getInferredInsertSize() < 0) {
					if (!basesEqual(rightBarcode, r.variableBarcode, true, 0)) {
						throw new AssertionFailedException("Unequal barcodes: " +
								new String(rightBarcode) + " vs " + new String(r.variableBarcode) +
								"; other barcode is " + new String(leftBarcode) + " and mate is " +
								new String(r.getMateVariableBarcode()) + "; nRecords=" +
								allDuplexRecords.size() + " (1)");
					}
				} else {
					if (!basesEqual(leftBarcode, r.variableBarcode, true, 0)) {
						throw new AssertionFailedException("Unequal barcodes: " +
								new String(leftBarcode) + " vs " + new String(r.variableBarcode) +
								"; other barcode is " + new String(rightBarcode) + " and mate is " +
								new String(r.getMateVariableBarcode()) + "; nRecords=" +
								allDuplexRecords.size() + " (2)");
					}					
				}
			});
		}
	}
	
	@SuppressWarnings("null")
	public void computeConsensus(boolean allReadsSameBarcode, int barcodeLength) {
		final Collection</*@NonNull*/ ExtendedSAMRecord> allDuplexRecords = 
				new ArrayList<>(topStrandRecords.size() + bottomStrandRecords.size());
		allDuplexRecords.addAll(topStrandRecords);
		allDuplexRecords.addAll(bottomStrandRecords);
		totalNRecords = allDuplexRecords.size();
		if (allReadsSameBarcode) {
			leftBarcode = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					findAny().map(r -> r.variableBarcode).orElse(ExtendedSAMRecord.getNs()));
			rightBarcode = (allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					findAny().map(r -> r.variableBarcode).orElse(ExtendedSAMRecord.getNs()));
		} else {
			leftBarcode = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() >= 0).
					collect(Collectors.toList()), barcodeLength);
			rightBarcode = SimpleCounter.getBarcodeConsensus(allDuplexRecords.stream().
					filter(rExt -> rExt.record.getInferredInsertSize() < 0).
					collect(Collectors.toList()), barcodeLength);
		}		
	}
	
	public void resetMaxDistanceToLigSite() {
		maxDistanceToLig = Integer.MIN_VALUE;
	}
	
	public void acceptDistanceToLigSite(int d) {
		if (d > maxDistanceToLig) {
			maxDistanceToLig = d;
		}
	}
	
	public int getDistanceToLigSite() {
		return maxDistanceToLig;
	}
		
	void setPositions(int position0, int position3) {
		this.position0 = position0;
		this.position3 = position3;
	}

	public int distanceTo(DuplexRead d2) {
		return Math.max(Math.abs(position0 - d2.position0), Math.abs(position3 - d2.position3));
	}
	
	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((leftAlignmentEnd == null) ? 0 : leftAlignmentEnd.hashCode());
		result = prime * result + ((leftAlignmentStart == null) ? 0 : leftAlignmentStart.hashCode());
		result = prime * result + Arrays.hashCode(leftBarcode);
		result = prime * result + Arrays.hashCode(rightBarcode);
		result = prime * result + ((rightAlignmentEnd == null) ? 0 : rightAlignmentEnd.hashCode());
		result = prime * result + ((rightAlignmentStart == null) ? 0 : rightAlignmentStart.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof DuplexRead))
			return false;
		DuplexRead other = (DuplexRead) obj;
		if (leftAlignmentEnd == null) {
			if (other.leftAlignmentEnd != null)
				return false;
		} else if (!leftAlignmentEnd.equals(other.leftAlignmentEnd))
			return false;
		if (leftAlignmentStart == null) {
			if (other.leftAlignmentStart != null)
				return false;
		} else if (!leftAlignmentStart.equals(other.leftAlignmentStart))
			return false;
		if (!Arrays.equals(leftBarcode, other.leftBarcode))
			return false;
		if (!Arrays.equals(rightBarcode, other.rightBarcode))
			return false;
		if (rightAlignmentEnd == null) {
			if (other.rightAlignmentEnd != null)
				return false;
		} else if (!rightAlignmentEnd.equals(other.rightAlignmentEnd))
			return false;
		if (rightAlignmentStart == null) {
			if (other.rightAlignmentStart != null)
				return false;
		} else if (!rightAlignmentStart.equals(other.rightAlignmentStart))
			return false;
		return true;
	}
		
	public int getMinMedianPhred() {
		int result = Integer.MAX_VALUE;
		for (ExtendedSAMRecord r: topStrandRecords) {
			int i = r.medianPhred;
			if (i < result) {
				result = i;
			}
		}
		
		for (ExtendedSAMRecord r: bottomStrandRecords) {
			int i = r.medianPhred;
			if (i < result) {
				result = i;
			}
		}
		
		return result;
	}
		
	@Override
	public String toString() {
		return leftAlignmentStart + ", " + rightAlignmentStart + ", " + 
			leftAlignmentEnd + ", " + rightAlignmentEnd + ", " + new String(leftBarcode) + "-" + 
			new String(rightBarcode) + "," + " Q" + minQuality +
			(localQuality == null ? "" : (" " + localQuality.getQualities().entrySet().stream().
				min((e1, e2) -> e1.getValue().compareTo(e2.getValue())).map(e -> e.getKey()).map(
						i -> i.toString()).orElse("") + " ")) +
			"->" + maxQuality + topStrandRecords.toString() + " "
			+ bottomStrandRecords.toString();
	}

	@Override
	public Interval<Integer> getInterval() {
		if (interval == null) {
			interval = Interval.toInterval(leftAlignmentStart != null ? leftAlignmentStart.position - intervalSlop : 0,
					leftAlignmentEnd != null ? leftAlignmentEnd.position + intervalSlop : Integer.MAX_VALUE);
			if (DebugControl.NONTRIVIAL_ASSERTIONS && interval == null) {
				throw new AssertionFailedException();
			}
		}
		return interval;
	}
	
}
