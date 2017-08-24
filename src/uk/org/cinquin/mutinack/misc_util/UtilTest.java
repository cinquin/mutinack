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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

@SuppressWarnings("static-method")
public class UtilTest {

	@Test
	public void testBasesEqualByteByteBoolean() {
		assertFalse(Util.basesEqual((byte) 'a', (byte) 'N', false));
		assertFalse(Util.basesEqual((byte) 'A', (byte) 'N', false));
		assertFalse(Util.basesEqual((byte) 'a', (byte) 'b', true));
		assertFalse(Util.basesEqual((byte) 'a', (byte) 'B', true));
		assertTrue(Util.basesEqual((byte) 'a', (byte) 'N', true));
		assertTrue(Util.basesEqual((byte) 'a', (byte) 'a', true));
		assertTrue(Util.basesEqual((byte) 'N', (byte) 'N', true));
	}

	@Test
	public void testBasesEqualByteArrayByteArrayBooleanInt() {
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, 0));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, 2));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, Integer.MAX_VALUE));

		assertFalse(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, 0));
		assertFalse(Util.basesEqual(new byte[] {'a', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, true, 0));
		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, true, 0));

		assertTrue(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));

		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertFalse(Util.basesEqual(new byte[] {'a', 'd', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertFalse(Util.basesEqual(new byte[] {'d', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));

		//Disallow Ns
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, 0));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, 2));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, Integer.MAX_VALUE));

		assertFalse(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, 0));
		assertFalse(Util.basesEqual(new byte[] {'a', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, false, 0));
		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, false, 0));

		assertTrue(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'a', 'b', 'c'}, false, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, false, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, false, 1));

		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'c'}, new byte[] {'a', 'b', 'c'}, false, 1));
		assertFalse(Util.basesEqual(new byte[] {'a', 'd', 'd'}, new byte[] {'a', 'b', 'c'}, false, 1));
		assertFalse(Util.basesEqual(new byte[] {'d', 'b', 'd'}, new byte[] {'a', 'b', 'c'}, false, 1));

		//Include Ns

		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'a', 'N', 'c'}, true, 0));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'N', 'b', 'N'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'N', 'N', 'N'}, true, 2));
		assertTrue(Util.basesEqual(new byte[] {'a', 'b', 'c'}, new byte[] {'N', 'b', 'c'}, true, Integer.MAX_VALUE));

		assertFalse(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'a', 'N', 'c'}, true, 0));
		assertFalse(Util.basesEqual(new byte[] {'a', 'b', 'd'}, new byte[] {'N', 'b', 'c'}, true, 0));
		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'c'}, new byte[] {'N', 'b', 'c'}, true, 0));

		assertTrue(Util.basesEqual(new byte[] {'d', 'b', 'c'}, new byte[] {'N', 'b', 'c'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'd', 'c'}, new byte[] {'N', 'b', 'N'}, true, 1));
		assertTrue(Util.basesEqual(new byte[] {'a', 'N', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));

		assertFalse(Util.basesEqual(new byte[] {'d', 'd', 'N'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertFalse(Util.basesEqual(new byte[] {'N', 'd', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));
		assertFalse(Util.basesEqual(new byte[] {'d', 'N', 'd'}, new byte[] {'a', 'b', 'c'}, true, 1));

	}

}
