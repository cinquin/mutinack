package contrib.net.sf.picard.fastq;

/**
 * Simple interface for a class that can write out fastq records.
 *
 * @author Tim Fennell
 */
public interface FastqWriter {
    void write(final FastqRecord rec);
    void close();
}
