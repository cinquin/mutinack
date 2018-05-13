package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;

import org.eclipse.collections.api.block.HashingStrategy;
import org.eclipse.collections.impl.set.AbstractUnifiedSet;
import org.eclipse.collections.impl.set.strategy.mutable.UnifiedSetWithHashingStrategy;
import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileReader.ValidationStringency;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;

public class FindIdenticalBarcodes {

	public final static int BARCODE_LENGTH = 12;

	public static void main(String[] args) {
		final File alignments1 = Paths.get(args[0]).toFile();
		final File alignments2 = Paths.get(args[1]).toFile();

		AbstractUnifiedSet<Pair<String, SAMRecord>> barcodes1 = getBarcodes(alignments1);

		try(SAMFileReader bamReader = new SAMFileReader(alignments2);
				SAMRecordIterator it = bamReader.iterator()) {
			bamReader.setValidationStringency(ValidationStringency.SILENT);
			while (it.hasNext()) {
				@NonNull SAMRecord r = Objects.requireNonNull(it.next());
				Pair<String, SAMRecord> pair = new Pair<>(getBarcode(r), r);
				Pair<String, SAMRecord> other = barcodes1.get(pair);
				if (other != null &&
						r.getAlignmentStart() == other.snd.getAlignmentStart() &&
						r.getMateAlignmentStart() == other.snd.getMateAlignmentStart()) {
					System.out.println("Barcode " + pair.fst + " matches reads " + format(r) + " and " + format(other.snd));
				}
			}
		}
	}

	private static String format(SAMRecord r) {
		return r.getReadName() + " at " + r.getReferenceName() + ":" + r.getAlignmentStart();
	}

	private static @NonNull String getBarcode(SAMRecord r) {
		final String name = ExtendedSAMRecord.getFullName(r, true);
		final int firstBarcodeInNameIndex = name.indexOf("BC:Z:");
		return ExtendedSAMRecord.getFullBarcodeString(r, name, firstBarcodeInNameIndex).
			substring(0, BARCODE_LENGTH);
	}

	private static AbstractUnifiedSet<Pair<String, SAMRecord>> getBarcodes(File alignments1) {
		AbstractUnifiedSet<Pair<String, SAMRecord>> result = new UnifiedSetWithHashingStrategy<>(new BarcodeHash());
		try(SAMFileReader bamReader = new SAMFileReader(alignments1);
				SAMRecordIterator it = bamReader.iterator()) {
			bamReader.setValidationStringency(ValidationStringency.SILENT);
			while (it.hasNext()) {
				final @NonNull SAMRecord r = Objects.requireNonNull(it.next());
				result.add(new Pair<>(getBarcode(r), r));
			}
		}
		return result;
	}

	public static class BarcodeHash implements HashingStrategy<Pair<String, SAMRecord>> {
		private static final long serialVersionUID = 1L;

			@Override
			public int computeHashCode(Pair<String, SAMRecord> object) {
				return object.fst.hashCode();
			}

			@Override
			public boolean equals(Pair<String, SAMRecord> object1, Pair<String, SAMRecord> object2) {
				return object1.fst.equals(object2.fst);
			}
	}
}
