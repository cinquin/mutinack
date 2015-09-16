/*
 * The MIT License
 *
 * Copyright (c) 2012 The Broad Institute
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

import static contrib.net.sf.samtools.util.CollectionUtil.*;

import java.io.File;
import java.util.*;

import contrib.net.sf.picard.illumina.parser.readers.FilterFileReader;
import contrib.net.sf.samtools.util.CloseableIterator;

/**
 * Sequentially parses filter files for the given tiles.  One tile is processed at a time.  IlluminaDataProvider should
 * be the ONLY client class for this class except for test classes.  For more information on the filterFile format
 * and reading it, see FilterFileReader.
 */
class FilterParser extends PerTileParser<PfData> {
    private static Set<IlluminaDataType> supportedTypes = Collections.unmodifiableSet(makeSet(IlluminaDataType.PF));

    public FilterParser(final IlluminaFileMap tilesToFiles){
        super(tilesToFiles);
    }

    public FilterParser(final IlluminaFileMap tilesToFiles, final int startingTile){
        super(tilesToFiles, startingTile);
    }

    /** Wrap a filterFile reader in a closeable iterator and return it*/
    @Override
    protected CloseableIterator<PfData> makeTileIterator(final File iterator) {
        return new CloseableIterator<PfData>() {
            private FilterFileReader reader = new FilterFileReader(iterator);

            @Override
            public void close() {
                reader = null;
            }

            @Override
            public boolean hasNext() {
                return reader.hasNext();
            }

            @Override
            public PfData next() {
                final boolean nextValue = reader.next();
                return new PfData() {
                    @Override
                    public boolean isPf() {
                        return nextValue;
                    }
                };
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Set<IlluminaDataType> supportedTypes() {
        return supportedTypes;
    }
}