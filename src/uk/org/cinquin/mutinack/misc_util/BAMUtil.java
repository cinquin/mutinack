package uk.org.cinquin.mutinack.misc_util;

import java.io.File;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.samtools.SAMFileReader;
import contrib.net.sf.samtools.SAMRecordIterator;

/**
 * Created by olivier on 12/29/16.
 */
public class BAMUtil {

	public static @NonNull String getHash(@NonNull File inputBam) {
		long hash = 0;
		try (SAMFileReader tempReader = new SAMFileReader(inputBam)) {
			try (SAMRecordIterator it = tempReader.iterator()) {
				while (it.hasNext()) {
					hash += it.next().hashCode();
				}
			}
		}
		return String.valueOf(hash);
	}
}
