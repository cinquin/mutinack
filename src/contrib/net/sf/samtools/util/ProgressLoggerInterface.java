package contrib.net.sf.samtools.util;

import contrib.net.sf.samtools.SAMRecord;

/**
 * An interface defining the record() methods of the Picard-public ProgressLogger implementation.
 */
public interface ProgressLoggerInterface {

	boolean record(final String chrom, final int pos);
	boolean record(final SAMRecord rec);
	boolean record(final SAMRecord... recs);

}
