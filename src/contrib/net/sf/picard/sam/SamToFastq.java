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
package contrib.net.sf.picard.sam;

import java.io.File;
import java.util.*;

import contrib.net.sf.picard.PicardException;
import contrib.net.sf.picard.cmdline.CommandLineProgram;
import contrib.net.sf.picard.cmdline.Option;
import contrib.net.sf.picard.cmdline.StandardOptionDefinitions;
import contrib.net.sf.picard.cmdline.Usage;
import contrib.net.sf.picard.fastq.FastqRecord;
import contrib.net.sf.picard.fastq.FastqWriter;
import contrib.net.sf.picard.fastq.FastqWriterFactory;
import contrib.net.sf.picard.io.IoUtil;
import contrib.net.sf.picard.util.Log;
import contrib.net.sf.picard.util.ProgressLogger;
import contrib.net.sf.samtools.*;
import contrib.net.sf.samtools.util.Lazy;
import contrib.net.sf.samtools.util.SequenceUtil;
import contrib.net.sf.samtools.util.StringUtil;

/**
 * <p/>
 * Extracts read sequences and qualities from the input SAM/BAM file and writes them into
 * the output file in Sanger fastq format.
 * See <a href="http://maq.sourceforge.net/fastq.shtml">MAQ FastQ specification</a> for details.
 * In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome,
 * the read's sequence from input sam file will be reverse-complemented prior to writing it to fastq in order restore correctly
 * the original read sequence as it was generated by the sequencer.
 */
public class SamToFastq extends CommandLineProgram {
    @Usage
    public String USAGE = getStandardUsagePreamble() + "Extracts read sequences and qualities from the input SAM/BAM file and writes them into " +
            "the output file in Sanger fastq format. In the RC mode (default is True), if the read is aligned and the alignment is to the reverse strand on the genome, " +
            "the read's sequence from input SAM file will be reverse-complemented prior to writing it to fastq in order restore correctly" +
            "the original read sequence as it was generated by the sequencer.";

    @Option(doc = "Input SAM/BAM file to extract reads from", shortName = StandardOptionDefinitions.INPUT_SHORT_NAME)
    public File INPUT;

    @Option(shortName = "F", doc = "Output fastq file (single-end fastq or, if paired, first end of the pair fastq).",
            mutex = {"OUTPUT_PER_RG"})
    public File FASTQ;

    @Option(shortName = "F2", doc = "Output fastq file (if paired, second end of the pair fastq).", optional = true,
            mutex = {"OUTPUT_PER_RG"})
    public File SECOND_END_FASTQ;

    @Option(shortName = "FU", doc = "Output fastq file for unpaired reads; may only be provided in paired-fastq mode", optional = true, mutex = {"OUTPUT_PER_RG"})
    public File UNPAIRED_FASTQ;

    @Option(shortName = "OPRG", doc = "Output a fastq file per read group (two fastq files per read group if the group is paired).",
            optional = true, mutex = {"FASTQ", "SECOND_END_FASTQ", "UNPAIRED_FASTQ"})
    public boolean OUTPUT_PER_RG;

    @Option(shortName = "ODIR", doc = "Directory in which to output the fastq file(s).  Used only when OUTPUT_PER_RG is true.",
            optional = true)
    public File OUTPUT_DIR;

    @Option(shortName = "RC", doc = "Re-reverse bases and qualities of reads with negative strand flag set before writing them to fastq",
            optional = true)
    public boolean RE_REVERSE = true;

    @Option(shortName = "INTER", doc = "Will generate an interleaved fastq if paired, each line will have /1 or /2 to describe which end it came from")
    public boolean INTERLEAVE = false;

    @Option(shortName = "NON_PF", doc = "Include non-PF reads from the SAM file into the output FASTQ files.")
    public boolean INCLUDE_NON_PF_READS = false;

    @Option(shortName = "CLIP_ATTR", doc = "The attribute that stores the position at which " +
            "the SAM record should be clipped", optional = true)
    public String CLIPPING_ATTRIBUTE;

    @Option(shortName = "CLIP_ACT", doc = "The action that should be taken with clipped reads: " +
            "'X' means the reads and qualities should be trimmed at the clipped position; " +
            "'N' means the bases should be changed to Ns in the clipped region; and any " +
            "integer means that the base qualities should be set to that value in the " +
            "clipped region.", optional = true)
    public String CLIPPING_ACTION;

    @Option(shortName = "R1_TRIM", doc = "The number of bases to trim from the beginning of read 1.")
    public int READ1_TRIM = 0;

    @Option(shortName = "R1_MAX_BASES", doc = "The maximum number of bases to write from read 1 after trimming. " +
            "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional = true)
    public Integer READ1_MAX_BASES_TO_WRITE;

    @Option(shortName = "R2_TRIM", doc = "The number of bases to trim from the beginning of read 2.")
    public int READ2_TRIM = 0;

    @Option(shortName = "R2_MAX_BASES", doc = "The maximum number of bases to write from read 2 after trimming. " +
            "If there are fewer than this many bases left after trimming, all will be written.  If this " +
            "value is null then all bases left after trimming will be written.", optional = true)
    public Integer READ2_MAX_BASES_TO_WRITE;

    @Option(doc = "If true, include non-primary alignments in the output.  Support of non-primary alignments in SamToFastq " +
            "is not comprehensive, so there may be exceptions if this is set to true and there are paired reads with non-primary alignments.")
    public boolean INCLUDE_NON_PRIMARY_ALIGNMENTS = false;

    private final Log log = Log.getInstance(SamToFastq.class);

    public static void main(final String[] argv) {
        System.exit(new SamToFastq().instanceMain(argv));
    }

    @Override
		protected int doWork() {
        IoUtil.assertFileIsReadable(INPUT);
        final SAMFileReader reader = new SAMFileReader(IoUtil.openFileForReading(INPUT));
        final Map<String, SAMRecord> firstSeenMates = new HashMap<>();
        final FastqWriterFactory factory = new FastqWriterFactory();
        factory.setCreateMd5(CREATE_MD5_FILE);
        final Map<SAMReadGroupRecord, FastqWriters> writers = generateWriters(reader.getFileHeader().getReadGroups(), factory);

        final ProgressLogger progress = new ProgressLogger(log);
        for (final SAMRecord currentRecord : reader) {
            if (currentRecord.isSecondaryOrSupplementary() && !INCLUDE_NON_PRIMARY_ALIGNMENTS)
                continue;

            // Skip non-PF reads as necessary
            if (currentRecord.getReadFailsVendorQualityCheckFlag() && !INCLUDE_NON_PF_READS)
                continue;

            final FastqWriters fq = writers.get(currentRecord.getReadGroup());
            if (currentRecord.getReadPairedFlag()) {
                final String currentReadName = currentRecord.getReadName();
                final SAMRecord firstRecord = firstSeenMates.remove(currentReadName);
                if (firstRecord == null) {
                    firstSeenMates.put(currentReadName, currentRecord);
                } else {
                    assertPairedMates(firstRecord, currentRecord);

                    final SAMRecord read1 =
                            currentRecord.getFirstOfPairFlag() ? currentRecord : firstRecord;
                    final SAMRecord read2 =
                            currentRecord.getFirstOfPairFlag() ? firstRecord : currentRecord;
                    writeRecord(read1, 1, fq.getFirstOfPair(), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
                    final FastqWriter secondOfPairWriter = fq.getSecondOfPair();
                    if (secondOfPairWriter == null) {
                        throw new PicardException("Input contains paired reads but no SECOND_END_FASTQ specified.");
                    }
                    writeRecord(read2, 2, secondOfPairWriter, READ2_TRIM, READ2_MAX_BASES_TO_WRITE);
                }
            } else {
                writeRecord(currentRecord, null, fq.getUnpaired(), READ1_TRIM, READ1_MAX_BASES_TO_WRITE);
            }

            progress.record(currentRecord);
        }

        reader.close();

        // Close all the fastq writers being careful to close each one only once!
        for (final FastqWriters writerMapping : new HashSet<>(writers.values())) {
            writerMapping.closeAll();
        }

        if (firstSeenMates.size() > 0) {
            SAMUtils.processValidationError(new SAMValidationError(SAMValidationError.Type.MATE_NOT_FOUND,
                    "Found " + firstSeenMates.size() + " unpaired mates", null), VALIDATION_STRINGENCY);
        }

        return 0;
    }

    /**
     * Generates the writers for the given read groups or, if we are not emitting per-read-group, just returns the single set of writers.
     */
    private Map<SAMReadGroupRecord, FastqWriters> generateWriters(final List<SAMReadGroupRecord> samReadGroupRecords,
                                                                  final FastqWriterFactory factory) {

        final Map<SAMReadGroupRecord, FastqWriters> writerMap = new HashMap<>();

        final FastqWriters fastqWriters;
        if (!OUTPUT_PER_RG) {
            IoUtil.assertFileIsWritable(FASTQ);
            IoUtil.openFileForWriting(FASTQ);
            final FastqWriter firstOfPairWriter = factory.newWriter(FASTQ);

            final FastqWriter secondOfPairWriter;
            if (INTERLEAVE) {
                secondOfPairWriter = firstOfPairWriter;
            } else if (SECOND_END_FASTQ != null) {
                IoUtil.assertFileIsWritable(SECOND_END_FASTQ);
                IoUtil.openFileForWriting(SECOND_END_FASTQ);
                secondOfPairWriter = factory.newWriter(SECOND_END_FASTQ);
            } else {
                secondOfPairWriter = null;
            }

            /** Prepare the writer that will accept unpaired reads.  If we're emitting a single fastq - and assuming single-ended reads -
             * then this is simply that one fastq writer.  Otherwise, if we're doing paired-end, we emit to a third new writer, since 
             * the other two fastqs are accepting only paired end reads. */
            final FastqWriter unpairedWriter = UNPAIRED_FASTQ == null ? firstOfPairWriter : factory.newWriter(UNPAIRED_FASTQ);
            fastqWriters = new FastqWriters(firstOfPairWriter, secondOfPairWriter, unpairedWriter);

            // For all read groups we may find in the bam, register this single set of writers for them.
            writerMap.put(null, fastqWriters);
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                writerMap.put(rg, fastqWriters);
            }
        } else {
            // When we're creating a fastq-group per readgroup, by convention we do not emit a special fastq for unpaired reads.
            for (final SAMReadGroupRecord rg : samReadGroupRecords) {
                final FastqWriter firstOfPairWriter = factory.newWriter(makeReadGroupFile(rg, "_1"));
                // Create this writer on-the-fly; if we find no second-of-pair reads, don't bother making a writer (or delegating, 
                // if we're interleaving).
                final Lazy<FastqWriter> lazySecondOfPairWriter = new Lazy<>(new Lazy.LazyInitializer<FastqWriter>() {
                    @Override
                    public FastqWriter make() {
                        return INTERLEAVE ? firstOfPairWriter : factory.newWriter(makeReadGroupFile(rg, "_2"));
                    }
                });
                writerMap.put(rg, new FastqWriters(firstOfPairWriter, lazySecondOfPairWriter, firstOfPairWriter));
            }
        }
        return writerMap;
    }


    private File makeReadGroupFile(final SAMReadGroupRecord readGroup, final String preExtSuffix) {
        String fileName = readGroup.getPlatformUnit();
        if (fileName == null) fileName = readGroup.getReadGroupId();
        fileName = IoUtil.makeFileNameSafe(fileName);
        if (preExtSuffix != null) fileName += preExtSuffix;
        fileName += ".fastq";

        final File result = (OUTPUT_DIR != null)
                ? new File(OUTPUT_DIR, fileName)
                : new File(fileName);
        IoUtil.assertFileIsWritable(result);
        return result;
    }

    void writeRecord(final SAMRecord read, final Integer mateNumber, final FastqWriter writer,
                     final int basesToTrim, final Integer maxBasesToWrite) {
        final String seqHeader = mateNumber == null ? read.getReadName() : read.getReadName() + "/" + mateNumber;
        String readString = read.getReadString();
        String baseQualities = read.getBaseQualityString();

        // If we're clipping, do the right thing to the bases or qualities
        if (CLIPPING_ATTRIBUTE != null) {
            final Integer clipPoint = (Integer) read.getAttribute(CLIPPING_ATTRIBUTE);
            if (clipPoint != null) {
                if (CLIPPING_ACTION.equalsIgnoreCase("X")) {
                    readString = clip(readString, clipPoint, null,
                            !read.getReadNegativeStrandFlag());
                    baseQualities = clip(baseQualities, clipPoint, null,
                            !read.getReadNegativeStrandFlag());

                } else if (CLIPPING_ACTION.equalsIgnoreCase("N")) {
                    readString = clip(readString, clipPoint, 'N',
                            !read.getReadNegativeStrandFlag());
                } else {
                    final char newQual = SAMUtils.phredToFastq(
                            new byte[]{(byte) Integer.parseInt(CLIPPING_ACTION)}).charAt(0);
                    baseQualities = clip(baseQualities, clipPoint, newQual,
                            !read.getReadNegativeStrandFlag());
                }
            }
        }
        if (RE_REVERSE && read.getReadNegativeStrandFlag()) {
            readString = SequenceUtil.reverseComplement(readString);
            baseQualities = StringUtil.reverseString(baseQualities);
        }
        if (basesToTrim > 0) {
            readString = readString.substring(basesToTrim);
            baseQualities = baseQualities.substring(basesToTrim);
        }

        if (maxBasesToWrite != null && maxBasesToWrite < readString.length()) {
            readString = readString.substring(0, maxBasesToWrite);
            baseQualities = baseQualities.substring(0, maxBasesToWrite);
        }

        writer.write(new FastqRecord(seqHeader, readString, "", baseQualities));

    }

    /**
     * Utility method to handle the changes required to the base/quality strings by the clipping
     * parameters.
     *
     * @param src         The string to clip
     * @param point       The 1-based position of the first clipped base in the read
     * @param replacement If non-null, the character to replace in the clipped positions
     *                    in the string (a quality score or 'N').  If null, just trim src
     * @param posStrand   Whether the read is on the positive strand
     * @return String       The clipped read or qualities
     */
    private String clip(final String src, final int point, final Character replacement, final boolean posStrand) {
        final int len = src.length();
        String result = posStrand ? src.substring(0, point - 1) : src.substring(len - point + 1);
        if (replacement != null) {
            if (posStrand) {
                for (int i = point; i <= len; i++) {
                    result += replacement;
                }
            } else {
                for (int i = 0; i <= len - point; i++) {
                    result = replacement + result;
                }
            }
        }
        return result;
    }

    private void assertPairedMates(final SAMRecord record1, final SAMRecord record2) {
        if (!(record1.getFirstOfPairFlag() && record2.getSecondOfPairFlag() ||
                record2.getFirstOfPairFlag() && record1.getSecondOfPairFlag())) {
            throw new PicardException("Illegal mate state: " + record1.getReadName());
        }
    }


    /**
     * Put any custom command-line validation in an override of this method.
     * clp is initialized at this point and can be used to print usage and access argv.
     * Any options set by command-line parser can be validated.
     *
     * @return null if command line is valid.  If command line is invalid, returns an array of error
     * messages to be written to the appropriate place.
     */
    @Override
		protected String[] customCommandLineValidation() {
        if (INTERLEAVE && SECOND_END_FASTQ != null) {
            return new String[]{
                    "Cannot set INTERLEAVE to true and pass in a SECOND_END_FASTQ"
            };
        }

        if (UNPAIRED_FASTQ != null && SECOND_END_FASTQ == null) {
            return new String[]{
                    "UNPAIRED_FASTQ may only be set when also emitting read1 and read2 fastqs (so SECOND_END_FASTQ must also be set)."
            };
        }

        if ((CLIPPING_ATTRIBUTE != null && CLIPPING_ACTION == null) ||
                (CLIPPING_ATTRIBUTE == null && CLIPPING_ACTION != null)) {
            return new String[]{
                    "Both or neither of CLIPPING_ATTRIBUTE and CLIPPING_ACTION should be set."};
        }

        if (CLIPPING_ACTION != null) {
            if (CLIPPING_ACTION.equals("N") || CLIPPING_ACTION.equals("X")) {
                // Do nothing, this is fine
            } else {
                try {
                    Integer.parseInt(CLIPPING_ACTION);
                } catch (NumberFormatException nfe) {
                    return new String[]{"CLIPPING ACTION must be one of: N, X, or an integer"};
                }
            }
        }

        if ((OUTPUT_PER_RG && OUTPUT_DIR == null) || ((!OUTPUT_PER_RG) && OUTPUT_DIR != null)) {
            return new String[]{
                    "If OUTPUT_PER_RG is true, then OUTPUT_DIR should be set. " +
                            "If "};
        }


        return null;
    }

    /**
     * A collection of {@link contrib.net.sf.picard.fastq.FastqWriter}s for particular types of reads.
     * <p/>
     * Allows for lazy construction of the second-of-pair writer, since when we are in the "output per read group mode", we only wish to
     * generate a second-of-pair fastq if we encounter a second-of-pair read.
     */
    static class FastqWriters {
        private final FastqWriter firstOfPair, unpaired;
        private final Lazy<FastqWriter> secondOfPair;

        /** Constructor if the consumer wishes for the second-of-pair writer to be built on-the-fly. */
        private FastqWriters(final FastqWriter firstOfPair, final Lazy<FastqWriter> secondOfPair, final FastqWriter unpaired) {
            this.firstOfPair = firstOfPair;
            this.unpaired = unpaired;
            this.secondOfPair = secondOfPair;
        }

        /** Simple constructor; all writers are pre-initialized.. */
        private FastqWriters(final FastqWriter firstOfPair, final FastqWriter secondOfPair, final FastqWriter unpaired) {
            this(firstOfPair, new Lazy<>(new Lazy.LazyInitializer<FastqWriter>() {
                @Override
                public FastqWriter make() {
                    return secondOfPair;
                }
            }), unpaired);
        }

        public FastqWriter getFirstOfPair() {
            return firstOfPair;
        }

        public FastqWriter getSecondOfPair() {
            return secondOfPair.get();
        }

        public FastqWriter getUnpaired() {
            return unpaired;
        }

        public void closeAll() {
            final Set<FastqWriter> fastqWriters = new HashSet<>();
            fastqWriters.add(firstOfPair);
            fastqWriters.add(unpaired);
            // Make sure this is a no-op if the second writer was never fetched.
            if (secondOfPair.isInitialized()) fastqWriters.add(secondOfPair.get());
            for (final FastqWriter fastqWriter : fastqWriters) {
                fastqWriter.close();
            }
        }
    }
}
