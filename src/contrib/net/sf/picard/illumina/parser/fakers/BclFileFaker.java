package contrib.net.sf.picard.illumina.parser.fakers;

import java.nio.ByteBuffer;

/**
 * Created by jcarey on 3/13/14.
 */
public class BclFileFaker extends FileFaker {

    @Override
    public void fakeFile(final ByteBuffer buffer) {

        // Write the number of elements to the header. The state variable "size" contains
        // the number of elements; we've allocated "size" plus the size of the header
        // (four bytes) to the buffer.
        buffer.putInt(size);

        while (size > 0) {
            // Fill the file with no calls
            buffer.put((byte) 0);
            size--;
        }
    }

    @Override
    protected boolean addLeadingZeros() {
        return false;
    }

    @Override
    protected int bufferSize() {
        return size + 4;
    }
}
