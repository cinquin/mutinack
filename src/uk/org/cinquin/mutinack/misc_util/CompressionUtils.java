package uk.org.cinquin.mutinack.misc_util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.zip.GZIPInputStream;

import org.tukaani.xz.XZInputStream;

public class CompressionUtils {

	public static BufferedReader decompressFile(File f) throws IOException {
		final BufferedReader br;
		if (f.getName().endsWith(".gz") || f.getName().endsWith(".xz")) {
			InputStream is = new FileInputStream(f);
			InputStream xStream = f.getName().endsWith(".gz") ?
					new GZIPInputStream(is)
				:
					new XZInputStream(is);
			Reader decoder = new InputStreamReader(xStream);
			br = new BufferedReader(decoder);
		} else {
			br = new BufferedReader(new FileReader(f));
		}
		return br;
	}

}
