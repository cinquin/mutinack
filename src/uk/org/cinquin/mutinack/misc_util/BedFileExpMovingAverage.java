package uk.org.cinquin.mutinack.misc_util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.features.ParseRTException;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public class BedFileExpMovingAverage {

	private final static int BIN_SIZE = 100_000;

	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException, ParseRTException {
		final String refFile = args[0];
		final double alpha = Double.parseDouble(args[1]);
		final boolean parseExpressionLevel = args.length >= 3 && args[2].equals("-parseExpressionLevel");
		final double truncate = args.length >= 4 ? Double.parseDouble(args[3]) : Double.MAX_VALUE;
		
		System.err.println("parseExpressionLevel: " + parseExpressionLevel);
		System.err.println("truncateAt: " + truncate);
		
		final Map<Integer, @NonNull String> indexContigNameMap = new ConcurrentHashMap<>();
		final List<String> contigNames = Parameters.defaultTruncateContigNames;
		
		for (int i = 0; i < contigNames.size(); i++) {
			indexContigNameMap.put(i, Util.nonNullify(contigNames.get(i)));
		}

		try (FileReader fileReader = new FileReader(new File(refFile))) {

			BedReader bedReader = new BedReader(indexContigNameMap.values(), 
					new BufferedReader(fileReader), refFile, null);

			for (int contig = 0; contig < contigNames.size(); contig++) {
				double v = 0;
				String contigName = contigNames.get(contig);
				for (int c = 0; c < Parameters.defaultTruncateContigPositions.get(contig) / BIN_SIZE; c++) {
					int l = c * BIN_SIZE; 
					for (; l < (c + 1) * BIN_SIZE; l++) {
						double vl = 0;
						final SequenceLocation loc = new SequenceLocation(contig, l);
						if (parseExpressionLevel) {
							if (bedReader.apply(loc).size() > 1) {
								System.err.println("Warning: " + bedReader.apply(loc).size() + " values at " + loc);
							}
							for (GenomeInterval var: bedReader.apply(loc)) {
								try {
									vl += Math.min(Double.parseDouble(var.name), truncate);
								} catch (NumberFormatException e) {
									throw new RuntimeException("Could not parse " + var.name, e);
								}
							}
						} else {
							boolean t = bedReader.test(loc);
							vl = t ? 1 : 0;
						}
						v = alpha * vl + (1 - alpha) * v;
					}
					System.out.println(contigName + "\t" + l + "\t" + DoubleAdderFormatter.formatDouble(v));
				}
			}
		}
	}
}
