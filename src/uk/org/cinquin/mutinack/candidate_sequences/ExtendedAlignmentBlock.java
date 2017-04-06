package uk.org.cinquin.mutinack.candidate_sequences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import contrib.net.sf.samtools.Cigar;
import contrib.net.sf.samtools.CigarElement;
import contrib.net.sf.samtools.CigarOperator;

/**
 * Represents the contiguous alignment of a subset of read bases to a reference
 * sequence. Simply put an alignment block tells you that read bases from
 * readStart are aligned to the reference (matching or mismatching) from
 * referenceStart for length bases.
 *
 * @author Tim Fennell
 */
public class ExtendedAlignmentBlock {
	private int readStart;
	private int referenceStart;
	private int length;

	public CigarOperator previousCigarOperator, nextCigarOperator;

	/** Constructs a new alignment block with the supplied read and ref starts and length. */
	ExtendedAlignmentBlock(int readStart, int referenceStart, int length) {
		this.readStart = readStart;
		this.referenceStart = referenceStart;
		this.length = length;
	}

	/** The first, 1-based, base in the read that is aligned to the reference reference. */
	public int getReadStart() { return readStart; }

	/** The first, 1-based, position in the reference to which the read is aligned. */
	public int getReferenceStart() { return referenceStart; }

	/** The number of contiguous bases aligned to the reference. */
	public int getLength() { return length; }

	public static List<ExtendedAlignmentBlock> getAlignmentBlocks(final Cigar cigar, final int alignmentStart, final String cigarTypeName) {
		if (cigar == null) {
			return Collections.emptyList();
		}

		final List<ExtendedAlignmentBlock> alignmentBlocks = new ArrayList<>();
		int readBase = 1;
		int refBase  = alignmentStart;

		CigarOperator previous = null;
		ExtendedAlignmentBlock previousBlock = null;
		for (final CigarElement e : cigar.getCigarElements()) {
			CigarOperator operator = e.getOperator();
			if (previousBlock != null) {
				previousBlock.nextCigarOperator = operator;
			}
			ExtendedAlignmentBlock block;
			switch (operator) {
				case H : break; // ignore hard clips
				case P : break; // ignore pads
				case S : readBase += e.getLength(); break; // soft clip read bases
				case N : refBase += e.getLength(); break;  // reference skip
				case D : refBase += e.getLength(); break;
				case I : readBase += e.getLength(); break;
				case M :
				case EQ :
				case X :
					final int length = e.getLength();
					block = new ExtendedAlignmentBlock(readBase, refBase, length);
					block.previousCigarOperator = previous;
					previousBlock = block;
					alignmentBlocks.add(block);
					readBase += length;
					refBase  += length;
					break;
				default : throw new IllegalStateException("Case statement didn't deal with " + cigarTypeName + " op: " + e.getOperator());
			}
			previous = operator;
		}
		return alignmentBlocks;
	}

}
