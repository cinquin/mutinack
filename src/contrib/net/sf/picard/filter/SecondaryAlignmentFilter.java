package contrib.net.sf.picard.filter;

import contrib.net.sf.samtools.SAMRecord;

/**
 * SamRecordFilter that filters out secondary alignments, but not supplemental alignments.
 */
public class SecondaryAlignmentFilter implements SamRecordFilter {
    /**
     * Returns true if the read is marked as secondary.
     */
    @Override
    public boolean filterOut(final SAMRecord record) { return record.getNotPrimaryAlignmentFlag(); }

    /**
     * Returns true if either read is marked as secondary.
     */
    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        return first.getNotPrimaryAlignmentFlag() || second.getNotPrimaryAlignmentFlag();
    }
}
