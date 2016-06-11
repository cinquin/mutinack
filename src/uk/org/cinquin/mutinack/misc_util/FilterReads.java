/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2016 Olivier Cinquin
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.util.Iterator;

import contrib.net.sf.samtools.SAMFileHeader;
import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMFileWriter;
import contrib.net.sf.samtools.SAMFileWriterFactory;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SamPairUtil;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import uk.org.cinquin.mutinack.sequence_IO.IteratorPrefetcher;

/**
 * Simple class for ad-hoc filtering of reads (BAM file specified by first
 * argument) to be written to a new file (path name specified by second
 * argument).
 * @author olivier
 *
 */
public class FilterReads {
	
	public static void main(String[] args) {
		final SAMFileWriter alignmentWriter;
		SAMFileWriterFactory factory = new SAMFileWriterFactory();
		SAMFileHeader header = new SAMFileHeader();
		factory.setCreateIndex(true);
		header.setSortOrder(contrib.net.sf.samtools.SAMFileHeader.SortOrder.coordinate);
		if (true) {
			factory.setMaxRecordsInRam(1000000000);
		}
		final File inputBam = new File(args[0]);

		try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
			header.setSequenceDictionary(tempReader.getFileHeader().getSequenceDictionary());
		}
		alignmentWriter = factory.
				makeBAMWriter(header, false, new File(args[1]), 0);

		SAMFileReader.setDefaultValidationStringency(SAMFileReader.ValidationStringency.LENIENT);

		try (SAMFileReader bamReader = new SAMFileReader(inputBam)) {
			float nRecords = 0;
			for (int i = 0; i <= 6; i++) {
				nRecords += bamReader.getIndex().getMetaData(i).getAlignedRecordCount() +
						bamReader.getIndex().getMetaData(0).getUnalignedRecordCount();
			}
			nRecords /= 100f;
			
			int nProcessedMod = 0;
			float nProcessed = 0;
			
			int nWrongPair = 0, nTooBig = 0, nDiffContig = 0;
			
			for (Iterator<SAMRecord> iterator = new IteratorPrefetcher<>(bamReader.iterator(), 100, null, e -> {}, null);
					iterator.hasNext() ;) {
				if (nProcessed == 1 || nProcessedMod++ == 100_000 || ! iterator.hasNext()) {
					nProcessedMod = 0;
					float nPt100 = nProcessed / 100;
					System.err.print("\r" + String.format("%.2f",(nProcessed/nRecords)) + "% done; " +
							String.format("%.2f",(nWrongPair / nPt100)) + " % wrong pair; " + 
							String.format("%.2f",(nTooBig / nPt100)) + " % insertsize too large; " + 
							String.format("%.2f",(nDiffContig / nPt100)) + " % mate diff contig; " + 
							"                     ");
				}
				nProcessed++;

				final SAMRecord samRecord = iterator.next();

				if (samRecord.getReadUnmappedFlag() || samRecord.getMateUnmappedFlag() ||
						samRecord.getReferenceIndex() == 2 || samRecord.getMateReferenceIndex() == 2) {
					continue;
				}
				
				boolean formsWrongPair = 
						! (SamPairUtil.getPairOrientation(samRecord) == PairOrientation.FR);
				
				int insertSize = samRecord.getInferredInsertSize();
				
				boolean differentContigs = !samRecord.getMateReferenceIndex().equals(samRecord.getReferenceIndex());
				
				if (formsWrongPair || Math.abs(insertSize) > 1_000 || differentContigs) {
					if (formsWrongPair) {
						nWrongPair++;
					}
					if (Math.abs(insertSize) > 1.000) {
						nTooBig++;
					}
					if (differentContigs) {
						nDiffContig++;
					}
					alignmentWriter.addAlignment(samRecord);
				}
			}
		}
		
		alignmentWriter.close();
		
	}
}
