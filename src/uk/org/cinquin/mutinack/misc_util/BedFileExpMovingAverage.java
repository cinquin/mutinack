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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public class BedFileExpMovingAverage {

	private static final int BIN_SIZE = 100_000;

	private static final List<@NonNull Integer> defaultTruncateContigPositions = Arrays.asList(
		15_072_423, 15_279_345, 13_783_700, 17_493_793, 13_794, 20_924_149, 17_718_866);

	@SuppressWarnings("resource")
	public static void main(String[] args) throws IOException, ParseRTException {
		@SuppressWarnings("null")
		final @NonNull String refFile = args[0];
		final double alpha = Double.parseDouble(args[1]);
		final boolean parseExpressionLevel = args.length >= 3 && args[2].equals("-parseExpressionLevel");
		final double truncate = args.length >= 4 ? Double.parseDouble(args[3]) : Double.MAX_VALUE;

		System.err.println("parseExpressionLevel: " + parseExpressionLevel);
		System.err.println("truncateAt: " + truncate);

		final List<@NonNull String> contigNames = BedReader.getContigNames(refFile);

		try (FileReader fileReader = new FileReader(new File(refFile))) {

			BedReader bedReader = new BedReader(contigNames,
					new BufferedReader(fileReader), "", refFile, null);

			for (int contig = 0; contig < contigNames.size(); contig++) {
				double v = 0;
				String contigName = contigNames.get(contig);
				for (int c = 0; c < defaultTruncateContigPositions.get(contig) / BIN_SIZE; c++) {
					int l = c * BIN_SIZE;
					for (; l < (c + 1) * BIN_SIZE; l++) {
						double vl = 0;
						final SequenceLocation loc = new SequenceLocation("", contig, contigName, l);
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
