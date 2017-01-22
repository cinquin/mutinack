/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2017 Olivier Cinquin
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

package uk.org.cinquin.mutinack.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import contrib.net.sf.samtools.SAMRecord;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import mockit.Delegate;
import mockit.Injectable;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.integration.junit4.JMockit;
import uk.org.cinquin.mutinack.AnalysisStats;
import uk.org.cinquin.mutinack.DuplexRead;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

@RunWith(JMockit.class)
@SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
@SuppressWarnings("static-method")
public class OpticalDuplicateTest {

	private final ExtendedSAMRecordTest esrtInstance =  new ExtendedSAMRecordTest();

	@Test
	public void testOpticalDuplication(
			@NonNull @Injectable SAMRecord sr1,
			@NonNull @Mocked MutinackGroup settings,
			@NonNull @Mocked Mutinack analyzer) {

		new NonStrictExpectations() {{
			settings.getVariableBarcodeStart(); result = 0;
			settings.getVariableBarcodeEnd(); result = 3;
			settings.getConstantBarcodeStart(); result = 4;
			settings.getConstantBarcodeEnd(); result = 6;
		}};

		final Parameters param = new Parameters();
		AnalysisStats stats = new AnalysisStats("", param, false, settings, false);

		final byte[] lowBaseQ = "0000000000".getBytes();
		final byte[] highBaseQ2 = "0000000100".getBytes();
		final byte[] highBaseQ1 = "0100000000".getBytes();

		final String bcStuff = "XXXXX_BC:Z:GCCATCT_BQ:Z:AAAAAEE_BC:Z:CGGATTT_BQ:Z:AAAA<EE BC:Z:GCCATCT BQ:Z:AAAAAEE";
		ExtendedSAMRecord e1 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26245:13357" + bcStuff, true, stats, settings, analyzer, lowBaseQ);
		ExtendedSAMRecord e2 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26245:13367" + bcStuff, true, stats, settings, analyzer, lowBaseQ);
		ExtendedSAMRecord e3 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26255:13357" + bcStuff, true, stats, settings, analyzer, lowBaseQ);
		ExtendedSAMRecord e4 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26245:13363" + bcStuff, true, stats, settings, analyzer, highBaseQ1);
		ExtendedSAMRecord e5 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26236:13357" + bcStuff, true, stats, settings, analyzer, lowBaseQ);

		ExtendedSAMRecord e6 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26235:13367" + bcStuff, true, stats, settings, analyzer, lowBaseQ);
		ExtendedSAMRecord e7 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21309:26226:13367" + bcStuff, true, stats, settings, analyzer, highBaseQ2);

		ExtendedSAMRecord e8 = esrtInstance.getMockedESR(sr1,
			"@NS5XXXXX0169:19:H0WJ9BGXX:2:21309:26246:13357" + bcStuff, true, stats, settings, analyzer, lowBaseQ);
		ExtendedSAMRecord e9 = esrtInstance.getMockedESR(sr1,
			"@NS500169:19:H0WJ9BGXX:2:21XXXXX309:26246:13357" + bcStuff, true, stats, settings, analyzer, lowBaseQ);

		DuplexRead dr = new DuplexRead(settings, "AAA".getBytes(), "TTT".getBytes(), false, false);
		dr.leftAlignmentStart = e1.getLocation();

		param.opticalDuplicateDistance = 10;
		List<ExtendedSAMRecord> reads = Arrays.asList(e1, e2, e3, e4, e5, e6, e7, e8, e9);
		dr.totalNRecords = reads.size();
		Collections.shuffle(reads);
		dr.markDuplicates(param, stats, reads);

		Assert.assertTrue(e1.opticalDuplicate);//duplicate of e4
		Assert.assertFalse(e1.hasOpticalDuplicates);
		Assert.assertTrue(e2.opticalDuplicate);//duplicate of e4
		Assert.assertFalse(e2.hasOpticalDuplicates);
		Assert.assertTrue(e5.opticalDuplicate);//duplicate of e4
		Assert.assertFalse(e5.hasOpticalDuplicates);
		Assert.assertFalse(e4.opticalDuplicate);
		Assert.assertTrue(e4.hasOpticalDuplicates);
		Assert.assertTrue(e6.opticalDuplicate);//duplicate of e7
		Assert.assertFalse(e6.hasOpticalDuplicates);
		Assert.assertFalse(e7.opticalDuplicate);
		Assert.assertTrue(e7.hasOpticalDuplicates);

		Assert.assertFalse(e3.hasOpticalDuplicates);
		Assert.assertFalse(e3.opticalDuplicate);

		Assert.assertFalse(e8.hasOpticalDuplicates);
		Assert.assertFalse(e8.opticalDuplicate);
		Assert.assertFalse(e9.hasOpticalDuplicates);
		Assert.assertFalse(e9.opticalDuplicate);
	}

}
