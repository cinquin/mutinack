package contrib.net.sf.picard.fastq;

import java.io.File;

import contrib.net.sf.samtools.Defaults;
import contrib.net.sf.samtools.util.AbstractAsyncWriter;

/**
 * Factory class for creating FastqWriter objects.
 *
 * @author Tim Fennell
 */
public class FastqWriterFactory {
    boolean useAsyncIo = Defaults.USE_ASYNC_IO;
    boolean createMd5  = Defaults.CREATE_MD5;

    /** Sets whether or not to use async io (i.e. a dedicated thread per writer. */
    public void setUseAsyncIo(final boolean useAsyncIo) { this.useAsyncIo = useAsyncIo; }

    /** If true, compute MD5 and write appropriately-named file when file is closed. */
    public void setCreateMd5(final boolean createMd5) { this.createMd5 = createMd5; }

    public FastqWriter newWriter(final File out) {
        final FastqWriter writer = new BasicFastqWriter(out, createMd5);
        if (useAsyncIo) {
            return new AsyncFastqWriter(writer, AbstractAsyncWriter.DEFAULT_QUEUE_SIZE);
        }
        else {
            return writer;
        }
    }
}
