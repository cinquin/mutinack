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

package uk.org.cinquin.mutinack.features.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.Ignore;
import org.junit.Test;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

public class BedReaderTest {

	public BedReaderTest() throws IOException {
		contigNames1 = new ArrayList<>(contigNames);
		bc = new BedReader(contigNames1, new BufferedReader(new StringReader(bed)), "",
			"test reader", null, null);
	}

	private final List<@NonNull String> contigNames = Arrays.asList(
			"chrI", "chrII", "chrIII", "chrIV", "chrM", "chrV", "chrX");

	private final List<@NonNull String>  contigNames1;

	private static final String bed = "chrI	6_029_950	6_033_366	NM_001025782	0	+	6029950	6033366	0	4	\"707,586,522,138,\"	\"0,1110,2238,3278,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
			"chrI	6_028_501	6_033_366	NM_001025783	0	+	6028501	6033366	0	6	\"150,591,707,586,522,138,\"	\"0,659,1449,2559,3687,4727,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
			"chrI	4_655_370	4_659_318	NM_001025786	0	+	4655370	4659318	0	7	\"127,738,270,276,317,78,309,\"	\"0,351,1141,1842,2569,3315,3639,\"	B0041.2	Caenorhabditis elegans	B0041.2\n" +
			"chrX	7_823_625	7_825_216	NM_182442	0	+	7823625	7825216	0	2	\"192,42,\"	\"0,1549,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
			"chrX	7_823_625	7_824_631	NM_182443	0	+	7823625	7824631	0	3	\"313,127,364,\"	\"0,463,642,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
			"chrX	7_823_625	7_825_216	NM_182444	0	+	7823625	7825216	0	4	\"313,32,687,258,\"	\"0,463,597,1333,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
			"";

	final BedReader bc;
	@Test
	public void test() throws ParseRTException {
		assertTrue(bc.test(new SequenceLocation("", 0, contigNames1, 6_028_500)));
		assertFalse(bc.test(new SequenceLocation("", 0, contigNames1, 6_028_499)));
		assertFalse(bc.test(new SequenceLocation("", 1, contigNames1, 6_028_500)));
		assertTrue(bc.test(new SequenceLocation("", 6, contigNames1, 7_824_630)));
		assertTrue(bc.test(new SequenceLocation("", 6, contigNames1, 7_824_631)));
		assertFalse(bc.test(new SequenceLocation("", 6, contigNames1, 7_825_216)));
		assertFalse(bc.test(new SequenceLocation("", 6, contigNames1, 0)));
		assertFalse(bc.test(new SequenceLocation("", 6, contigNames1, Integer.MAX_VALUE)));
		assertFalse(bc.test(new SequenceLocation("", 0, contigNames1, 0)));
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testOutOfBounds() {
		assertFalse(bc.test(new SequenceLocation("", 7, "", 7)));
	}

	@Test(expected=IndexOutOfBoundsException.class)
	public void testOutOfBounds2() {
		assertFalse(bc.test(new SequenceLocation("", Integer.MAX_VALUE, "", Integer.MAX_VALUE)));
	}

	@SuppressWarnings("unused")
	@Test(expected=ParseRTException.class)
	@Ignore
	public void testFormat1() throws ParseRTException, IOException {
		final String bed1 = "chrI	6029950	6033366	NM_001025782	0	+	6029950	6033366	0	4	\"707,586,522,138,\"	\"0,1110,2238,3278,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrI	6028501	6033366	NM_001025783	0	+	6028501	6033366	0	6	\"150,591,707,586,522,138,\"	\"0,659,1449,2559,3687,4727,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrI	4655370	4659318	\n" +
				"chrX	7823625	7825216	NM_182442	0	+	7823625	7825216	0	2	\"192,42,\"	\"0,1549,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7824631	NM_182443	0	+	7823625	7824631	0	3	\"313,127,364,\"	\"0,463,642,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7825216	NM_182444	0	+	7823625	7825216	0	4	\"313,32,687,258,\"	\"0,463,597,1333,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"";

		new BedReader(contigNames1, new BufferedReader(new StringReader(bed1)), "",
			"test reader", null, null);
		throw new RuntimeException();
	}

	@SuppressWarnings("unused")
	@Test(expected=ParseRTException.class)
	@Ignore
	public void testFormat2() throws ParseRTException, IOException {
		final String bed2 = "chrI	6029950	6033366	NM_001025782	0	+	6029950	6033366	0	4	\"707,586,522,138,\"	\"0,1110,2238,3278,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrI	6028501	6033366	NM_001025783	0	+	6028501	6033366	0	6	\"150,591,707,586,522,138,\"	\"0,659,1449,2559,3687,4727,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrI	4655370	4659318	\n" +
				"chrX	7823625	7825216	NM_182442	0	+	7823625	7825216	0	2	\"192,42,\"	\"0,1549,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7824631	NM_182443	0	+	7823625	7824631	0	3	\"313,127,364,\"	\"0,463,642,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7825216	NM_182444	0	+	7823625	7825216	0	4	\"313,32,687,258,\"	\"0,463,597,1333,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"";

		new BedReader(contigNames1, new BufferedReader(new StringReader(bed2)), "",
			"test reader", null, null);
		throw new RuntimeException();
	}

	@SuppressWarnings("unused")
	@Test(expected=ParseRTException.class)
	public void testFormat3() throws ParseRTException, IOException {
		final String bed3 = "chrI	6029950	6033366	NM_001025782	0	+	6029950	6033366	0	4	\"707,586,522,138,\"	\"0,1110,2238,3278,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrI	6028501	6033366	NM_001025783	0	+	6028501	6033366	0	6	\"150,591,707,586,522,138,\"	\"0,659,1449,2559,3687,4727,\"	B0025.1	Caenorhabditis elegans	related to yeast Vacuolar Protein Sorting factor\n" +
				"chrX	7823625	7825216	NM_182442	0	_	7823625	7825216	0	2	\"192,42,\"	\"0,1549,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7824631	NM_182443	0	+	7823625	7824631	0	3	\"313,127,364,\"	\"0,463,642,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"chrX	7823625	7825216	NM_182444	0	+	7823625	7825216	0	4	\"313,32,687,258,\"	\"0,463,597,1333,\"	R03G5.1	Caenorhabditis elegans	Elongation FacTor\n" +
				"";

		new BedReader(contigNames1, new BufferedReader(new StringReader(bed3)), "",
			"test reader", null, null);
		throw new RuntimeException();
	}

}
