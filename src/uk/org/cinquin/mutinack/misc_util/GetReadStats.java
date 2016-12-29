package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SAMRecordIterator;
import uk.org.cinquin.mutinack.statistics.Histogram;

public class GetReadStats {

	private static Histogram getInsertSizeHist(SAMRecordIterator it, long nToScan,
		Predicate<SAMRecord> filter) {
			Histogram result = new Histogram(1_000);
			int nScanned = 0;
			while (nScanned < nToScan && it.hasNext()) {
				SAMRecord r = it.next();
				if (!filter.test(r)) {
					continue;
				}
				result.insert(Math.abs(r.getInferredInsertSize()));
				nScanned++;
			}
			return result;
	}

	public static @NonNull Histogram getApproximateReadInsertSize(
			File bamFile, int maxInsertSize, int minMappingQualityQ2) {
		final Histogram h;
		try (SAMFileReader tempReader = new SAMFileReader(bamFile)) {
			h = GetReadStats.getInsertSizeHist(tempReader.iterator(), 500_000,
				r -> r.getInferredInsertSize() != 0 &&
						r.getMappingQuality() >= minMappingQualityQ2 &&
						Math.abs(r.getInferredInsertSize()) < maxInsertSize);
			Assert.isTrue(h.isEmpty() || h.get(0).sum() == 0);
		} catch (Exception e) {
			throw new RuntimeException("Error computing insert size distribution from file "
				+ bamFile.getAbsolutePath(), e);
		}
		return h;
	}

}
