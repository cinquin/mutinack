package uk.org.cinquin.mutinack.misc_util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

@SuppressWarnings("static-method")
public class UtilTest {
	
	@Test
	public void testBasesEqualByteByteBoolean() {
	    assertEquals(Util.basesEqual((byte) 'a', (byte) 'b', true), false);
	    assertEquals(Util.basesEqual((byte) 'a', (byte) 'N', true), true);
	    assertEquals(Util.basesEqual((byte) 'a', (byte) 'a', true), true);
	    assertEquals(Util.basesEqual((byte) 'N', (byte) 'N', true), true);
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
