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

package uk.org.cinquin.mutinack.tests;

import java.util.Arrays;
import java.util.HashMap;
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
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

@RunWith(JMockit.class)
@SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
@SuppressWarnings("static-method")
public class ExtendedSAMRecordTest {
	
	@SuppressWarnings("unused")
	@Test
	public void testNClippingRight(
			@NonNull @Injectable SAMRecord sr1, 
			@NonNull @Injectable SAMRecord sr2, 
			@Mocked MutinackGroup settings,
			@Mocked Mutinack analyzer) {
		
		settings.BIN_SIZE = 10_000_000;
		AnalysisStats s = new AnalysisStats("test_stats", new Parameters(), false, settings, false);
		analyzer.stats = Arrays.asList(s);
		
		IntegerDelegate alignmentStart1 = new IntegerDelegate();
		alignmentStart1.value = 1;
		IntegerDelegate alignmentEnd1 = new IntegerDelegate();
		alignmentEnd1.value = 5;
		
		/**
		 * Subtest 1
		 */
		//Don't take Ns into account when measuring clipping
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCGTNNNNNNNNNNNNNNNNNNNNNNNNNN".getBytes();
			sr1.getReadName(); result = "read_pair_1";
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = 
					"FFFFFFFFFFFFFFFFFFFFFFFF".getBytes();
			sr1.getFirstOfPairFlag(); result = true;
			sr1.getReadNegativeStrandFlag(); result = false;
			sr1.getAttribute("BC"); result = "AAA";
			sr1.getAlignmentStart(); result = alignmentStart1;
			sr1.getUnclippedStart(); result = 1;
			sr1.getAlignmentEnd(); result = alignmentEnd1;
			sr1.getUnclippedEnd(); result = new Delegate<Object>() {
				int delegate() {
					return sr1.getUnclippedStart() + bases1.length - 1;
				}
			};
			sr1.getMateAlignmentStart(); result = 11;
			
			byte [] bases2 = "ATGC".getBytes();
			sr2.getReadName(); result = "read_pair_1";
			sr2.getReadBases(); result = bases2;
			sr2.getReadLength(); result = bases2.length;
			sr2.getBaseQualities(); result = bases2;
			sr2.getFirstOfPairFlag(); result = false;
			sr2.getReadNegativeStrandFlag(); result = true;
			sr2.getAttribute("BC"); result = "TTT";
			sr2.getAlignmentStart(); result = 11;
			sr2.getAlignmentEnd(); result = 14;
			sr2.getUnclippedStart(); result = 11;
			sr2.getUnclippedEnd(); result = 14;
			sr2.getMateAlignmentStart(); result = alignmentStart1;

		}};
		alignmentStart1.value = 2;
		
		Map<String, ExtendedSAMRecord> extSAMCache = new HashMap<>();
		SequenceLocation location = new SequenceLocation(sr1.getReferenceIndex(),
			sr1.getReferenceName(), sr1.getAlignmentStart());
		
		ExtendedSAMRecord e1 = 
				new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		ExtendedSAMRecord e2 = 
				new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(6, e1.getnClipped());
		
		alignmentStart1.value = 4;//3 clipped 3', so 2 more total clipping: 6 -> 8
		e1.resetnClipped();
		Assert.assertEquals(8, e1.getnClipped());
		
		/**
		 * Subtest 2
		 */
		//Ns that were *not* clipped at the alignment step (may not happen in practice)
		new NonStrictExpectations() {{
			//bases1 set to "ATGCGCCCNNNNNNNNNNNNNNNNNNNNNNNNNNNN".getBytes();
			
			sr2.getAlignmentEnd(); result = 40; //Force non-overlapping mate situation
			sr2.getUnclippedEnd(); result = 40; //Force non-overlapping mate situation
		}};
		alignmentStart1.value = 2;
		alignmentEnd1.value = 35;
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		
		Assert.assertEquals(1, e1.getnClipped());

		alignmentEnd1.value = 5;

		/**
		 * Subtest 3
		 */
		//Clipping started before mate alignment end, so report full clipped length
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCGTTTTTTTTTTTTTTTTTTTTTTTTTTTTT".getBytes();
			sr1.getReadBases(); result = bases1;
			sr1.getBaseQualities(); result = bases1;
			sr1.getUnclippedEnd(); result = new Delegate<Object>() {
				int delegate() {
					return sr1.getUnclippedStart() + bases1.length - 1;
				}
			};
			//sr1 alignment end: 5
			
			sr2.getAlignmentStart(); result = 11;
			sr2.getUnclippedStart(); result = 11;
			sr2.getAlignmentEnd(); result = 14;
			sr2.getUnclippedEnd(); result = 14;
		}};
		alignmentEnd1.value = 13;
		alignmentStart1.value = 4;//3 clipped 3'
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		
		Assert.assertEquals(28, e1.getnClipped());
		
		/**
		 * Subtest 4
		 */
		//Clipping starts at same position as mate alignment end, so ignore it
		//altogether (on the 5' end)
		
		alignmentEnd1.value = 14;
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		
		Assert.assertEquals(3, e1.getnClipped());

		
		/**
		 * Subtest 5
		 */
		//Clipping started before mate alignment end, so report full clipping
		//ignoring trailing Ns
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCTTTTTAAAANNNNNNNNNNNNNNNNNNNNN".getBytes();
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = bases1;
			
			sr2.getAlignmentStart(); result = 11;
			sr2.getUnclippedStart(); result = 11;
			sr2.getAlignmentEnd(); result = 14;
			sr2.getUnclippedEnd(); result = 14;
		}};
		alignmentStart1.value = 4;//3 clipped 3'
		alignmentEnd1.value = 13;
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		
		Assert.assertEquals(7, e1.getnClipped());//3 + 4
		
		/**
		 * Subtest 6
		 */
		//Clipping started at mate alignment end, so report no clipping
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCTTTTTNNNNNNNNNNNNNNNNNNNNNNNNN".getBytes();
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = bases1;
			
			sr2.getAlignmentStart(); result = 11;
			sr2.getUnclippedStart(); result = 11;
			sr2.getAlignmentEnd(); result = 14;
			sr2.getUnclippedEnd(); result = 14;
		}};
		alignmentStart1.value = 4;//3 clipped 3'
		alignmentEnd1.value = 14;
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		
		Assert.assertEquals(3, e1.getnClipped());//only 3' clipping
		
		/**
		 * Subtest 7
		 */
		//Clipping started past mate alignment end, so report no clipping
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCTTTTTNNNNNNNNNNNNNNNNNNNNNNNNN".getBytes();
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = bases1;
			
			sr2.getAlignmentStart(); result = 11;
			sr2.getUnclippedStart(); result = 11;
			sr2.getAlignmentEnd(); result = 14;
			sr2.getUnclippedEnd(); result = 14;
		}};
		alignmentStart1.value = 4;//3 clipped 3'
		alignmentEnd1.value = 15;
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);		
		
		Assert.assertEquals(3, e1.getnClipped());//only 3' clipping
	}
	
	@SuppressWarnings("unused")
	@Test
	public void testNClippingLeft(
			@NonNull @Injectable SAMRecord sr1, 
			@NonNull @Injectable SAMRecord sr2, 
			@Mocked MutinackGroup settings,
			@Mocked Mutinack analyzer) {
		
		settings.BIN_SIZE = 10_000_000;
		settings.setContigNames(Arrays.asList("contig1"));
		settings.setContigSizes(new HashMap<@NonNull String, @NonNull Integer>() {
			private static final long serialVersionUID = -7506734058327588018L;
			{
				put("contig1", 10_000_000);
			}});
		AnalysisStats s = new AnalysisStats("test_stats", new Parameters(), false, settings, false);
		analyzer.stats = Arrays.asList(s);
		
		IntegerDelegate alignmentStart2 = new IntegerDelegate();
		alignmentStart2.value = 36;
		IntegerDelegate alignmentEnd2 = new IntegerDelegate();
		alignmentEnd2.value = 39;
		
		/**
		 * Subtest 1
		 */
		//Don't take Ns into account when measuring clipping
		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCGT".getBytes();
			sr1.getReadName(); result = "read1";
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = bases1;
			sr1.getFirstOfPairFlag(); result = true;
			sr1.getReadNegativeStrandFlag(); result = false;
			sr1.getAttribute("BC"); result = "AAA";
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 30;
			sr1.getUnclippedEnd(); result = 30;
			sr1.getMateAlignmentStart(); result = alignmentStart2;
			
			byte [] bases2 = "NNNNNATGC".getBytes();
			sr2.getReadName(); result = "read1";
			sr2.getReadBases(); result = bases2;
			sr2.getReadLength(); result = bases2.length;
			sr2.getBaseQualities(); result = bases2;
			sr2.getFirstOfPairFlag(); result = false;
			sr2.getReadNegativeStrandFlag(); result = true;
			sr2.getAttribute("BC"); result = "TTT";
			sr2.getAlignmentStart(); result = alignmentStart2;
			sr2.getAlignmentEnd(); result = alignmentEnd2;
			sr2.getUnclippedStart(); result = 31;
			sr2.getUnclippedEnd(); result = 39;
			sr2.getMateAlignmentStart(); result = 21;

		}};
		
		Map<String, ExtendedSAMRecord> extSAMCache = new HashMap<>();
		SequenceLocation location = new SequenceLocation(sr1.getReferenceIndex(),
			sr1.getReferenceName(), sr1.getAlignmentStart());
		
		ExtendedSAMRecord e1 = 
				new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		ExtendedSAMRecord e2 = 
				new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(0, e2.getnClipped());
		
		/**
		 * Subtest 2
		 */
		//Do take non-Ns into account when measuring clipping
		new NonStrictExpectations() {{
			byte [] bases2 = "GGGGGATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getUnclippedStart(); result = 31;
			
		}};
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(5, e2.getnClipped());
		
		/**
		 * Subtest 3
		 */
		//Ns that were not clipped at alignment step
		new NonStrictExpectations() {{
			byte [] bases2 = "NNNNNATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getAlignmentStart(); result = 31;
			sr2.getUnclippedStart(); result = 31;
		}};
				
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(0, e2.getnClipped());
		
		/**
		 * Subtest 4
		 */
		
		//Clipping started before running into mate, so report full clipping
		//just based on alignment
		new NonStrictExpectations() {{
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 38;
			sr1.getUnclippedEnd(); result = 38;

			byte [] bases2 = "GGGGGATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getAlignmentStart(); result = 44;
			sr2.getUnclippedStart(); result = 39;
		}};
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(5, e2.getnClipped());
		
		/**
		 * Subtest 5
		 */
		//Clipping started at the position mate alignment starts, so do not
		//report any clipping
		new NonStrictExpectations() {{			
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 38;
			sr1.getUnclippedEnd(); result = 38;

			byte [] bases2 = "GGGGGATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getAlignmentStart(); result = 21;
			sr2.getUnclippedStart(); result = 16;
		}};
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(0, e2.getnClipped());

		/**
		 * Subtest 6
		 */
		//Clipping started at the position mate alignment starts + 1, so do
		//report clipping
		new NonStrictExpectations() {{			
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 38;
			sr1.getUnclippedEnd(); result = 38;

			byte [] bases2 = "GGGGGATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getAlignmentStart(); result = 22;
			sr2.getUnclippedStart(); result = 17;
		}};
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(5, e2.getnClipped());
		
		/**
		 * Subtest 7
		 */
		//Clipping started at the position mate alignment starts - 1, so do not
		//report clipping
		new NonStrictExpectations() {{			
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 38;
			sr1.getUnclippedEnd(); result = 38;

			byte [] bases2 = "GGGGGATGC".getBytes();
			sr2.getReadBases(); result = bases2;
			sr2.getAlignmentStart(); result = 20;
			sr2.getUnclippedStart(); result = 15;
		}};
		
		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);
		
		e2 = new ExtendedSAMRecord(sr2, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e2.getFullName(), e2);
		
		Assert.assertEquals(0, e2.getnClipped());
	}
	
	@Rule
    public ExpectedException thrown = ExpectedException.none();
	
	@SuppressWarnings("unused")
	private ExtendedSAMRecord setup(
			@NonNull @Injectable SAMRecord sr1,
			String readName, boolean firstOfPair, AnalysisStats stats, MutinackGroup settings, Mutinack analyzer) {
		
		settings.BIN_SIZE = 10_000_000;
		AnalysisStats s = new AnalysisStats("test_stats", new Parameters(), firstOfPair, settings, false);
		analyzer.stats = Arrays.asList(s);

		new NonStrictExpectations() {{
			byte [] bases1 = "ATGCGCCCGT".getBytes();
			sr1.getReadName(); result = readName;
			sr1.getReadBases(); result = bases1;
			sr1.getReadLength(); result = bases1.length;
			sr1.getBaseQualities(); result = bases1;
			sr1.getFirstOfPairFlag(); result = firstOfPair;
			sr1.getReadNegativeStrandFlag(); result = false;
			sr1.getAttribute("BC"); result = null;
			sr1.getAlignmentStart(); result = 21;
			sr1.getUnclippedStart(); result = 21;
			sr1.getAlignmentEnd(); result = 30;
			sr1.getUnclippedEnd(); result = 30;
		}};
		
		Map<String, ExtendedSAMRecord> extSAMCache = new HashMap<>();
		SequenceLocation location = new SequenceLocation(sr1.getReferenceIndex(),
			sr1.getReferenceName(), sr1.getAlignmentStart());

		ExtendedSAMRecord e1 = 
				new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		extSAMCache.put(e1.getFullName(), e1);

		return e1;
	}
	
	@Test
	@Ignore
	@SuppressWarnings("unused")
	public void testBarcodeRetrievalFromNameAbsent1(@NonNull @Injectable SAMRecord sr1,
			@Injectable AnalysisStats stats,
			@Mocked MutinackGroup settings,
			@Mocked Mutinack analyzer) {
		
		thrown.expect(ParseRTException.class);
		thrown.expectMessage("Missing first");
		
		ExtendedSAMRecord e1 = setup(sr1, "readName", true, stats, settings, analyzer);
	}
	
	@Test
	@Ignore
	@SuppressWarnings("unused")
	public void testBarcodeRetrievalFromNameAbsent2(@NonNull @Injectable SAMRecord sr1,
			@Injectable AnalysisStats stats,
			@Mocked MutinackGroup settings,
			@Mocked Mutinack analyzer) {
		
		new NonStrictExpectations() {{
			sr1.getFirstOfPairFlag(); result = false;
		}};
		thrown.expect(ParseRTException.class);
		thrown.expectMessage("Missing second");
		
		ExtendedSAMRecord e1 = setup(sr1, "XXXXX_BC:Z:GCCATCT_BQ:Z:AAAAAEE_BC:NN", false, stats, settings, analyzer);
	}
	
	@Test
	@SuppressWarnings({ "unused", "static-access" })
	public void testBarcodeRetrievalFromNamePresent(@NonNull @Injectable SAMRecord sr1,
			@Injectable AnalysisStats stats,
			@NonNull @Mocked MutinackGroup settings,
			@NonNull @Mocked Mutinack analyzer) {
		
		new NonStrictExpectations() {{
			settings.getVariableBarcodeStart(); result = 0;
			settings.getVariableBarcodeEnd(); result = 3;
			settings.getConstantBarcodeStart(); result = 4;
			settings.getConstantBarcodeEnd(); result = 6;
		}};
		
		ExtendedSAMRecord e1 = setup(sr1, "XXXXX_BC:Z:GCCATCT_BQ:Z:AAAAAEE_BC:Z:CGGATTT_BQ:Z:AAAA<EE BC:Z:GCCATCT BQ:Z:AAAAAEE", true, stats, settings, analyzer);
		
		Assert.assertArrayEquals("GCCA".getBytes(), e1.variableBarcode);
		Assert.assertArrayEquals("TCT".getBytes(), e1.constantBarcode);
		
		new NonStrictExpectations() {{
			sr1.getFirstOfPairFlag(); result = false;
			sr1.getReadName(); result = "XXXXX_BC:Z:GCCATCT_BQ:Z:AAAAAEE_BC:Z:CGGATTT_BQ:Z:AAAA<EE BC:Z:GCCATCT BQ:Z:AAAAAEE";
		}};
		
		Map<String, ExtendedSAMRecord> extSAMCache = new HashMap<>();
		SequenceLocation location = new SequenceLocation(sr1.getReferenceIndex(),
			sr1.getReferenceName(), sr1.getAlignmentStart());

		e1 = new ExtendedSAMRecord(sr1, settings, analyzer, location, extSAMCache);
		
		Assert.assertArrayEquals("CGGA".getBytes(), e1.variableBarcode);
		Assert.assertArrayEquals("TTT".getBytes(), e1.constantBarcode);

	}
}
