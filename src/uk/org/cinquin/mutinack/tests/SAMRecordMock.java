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

import contrib.net.sf.samtools.SAMRecord;
import mockit.Mock;
import mockit.MockUp;

@SuppressWarnings("static-method")
public class SAMRecordMock extends MockUp<SAMRecord> {

	private String readName;
	private byte[] bases;

	@Mock
	@SuppressWarnings("hiding")
	public void $init(String readName, byte[] bases) {
		this.readName = readName;
		this.bases = bases;
	}

	@Mock
	byte[] getReadBases() {return bases;}

	@Mock
	String getReadName() {return readName;}

	@Mock
	int getReadLength() {return bases.length;}
	@Mock
	byte[] getBaseQualities() {return bases;}

	@Mock
	boolean getFirstOfPairFlag() {return true;}

	@Mock
	boolean getReadNegativeStrandFlag() {return false;}

	@Mock
	String getAttribute(String attribute) {
		if ("BC".equals(attribute))
				return "AAA";
		else throw new RuntimeException();
	}

	@Mock
	int getAlignmentStart() {return 1;}

	@Mock
	int getUnclippedStart() {return 1;}

	@Mock
	int getAlignmentEnd() {return bases.length;}

	@Mock
	int getUnclippedEnd() {
			return getUnclippedStart() + getReadBases().length - 1;
	}

	@Mock
	int getMateAlignmentStart() {return 11;}


}
