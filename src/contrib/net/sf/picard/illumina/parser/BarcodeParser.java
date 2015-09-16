/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package contrib.net.sf.picard.illumina.parser;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import contrib.net.sf.picard.illumina.parser.readers.BarcodeFileReader;
import contrib.net.sf.samtools.util.CloseableIterator;
import contrib.net.sf.samtools.util.CollectionUtil;

/**
 * @author jburke@broadinstitute.org
 */
class BarcodeParser extends PerTileParser<BarcodeData> {

    private static final Set<IlluminaDataType> SUPPORTED_TYPES = Collections.unmodifiableSet(CollectionUtil.makeSet(IlluminaDataType.Barcodes));

    public BarcodeParser(final IlluminaFileMap tilesToFiles) {
        super(tilesToFiles);
    }

    public BarcodeParser(final IlluminaFileMap tilesToFiles, final int nextTile) {
        super(tilesToFiles, nextTile);
    }

    @Override
    protected CloseableIterator<BarcodeData> makeTileIterator(File nextTileFile) {
        return new BarcodeDataIterator(nextTileFile);
    }

    @Override
    public Set<IlluminaDataType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    private static class BarcodeDataIterator implements CloseableIterator<BarcodeData>{
        private BarcodeFileReader bfr;
        public BarcodeDataIterator(final File file) {
            bfr = new BarcodeFileReader(file);
        }

        @Override
        public void close() {
            bfr.close();
        }

        @Override
        public boolean hasNext() {
            return bfr.hasNext();
        }

        @Override
        public BarcodeData next() {
            return new BarcodeData() {
                @Override
                public String getBarcode() {
                    return bfr.next();
                }
            };
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
