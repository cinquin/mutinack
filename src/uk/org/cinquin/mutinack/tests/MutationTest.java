package uk.org.cinquin.mutinack.tests;

import static org.junit.Assert.fail;

import org.junit.Test;

import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Util;

public class MutationTest {

	@SuppressWarnings("static-method")
	@Test
	public void testComplementation() {
		Assert.isTrue(Util.getDuplicates(Mutation.KNOWN_BASES).isEmpty());
		for (byte b = Byte.MIN_VALUE; ; b++) {
			final byte lc = (byte) Character.toLowerCase(b);
			final byte uc = (byte) Character.toUpperCase(b);
			Assert.isFalse(Mutation.KNOWN_BASES.contains(lc));
			if (Mutation.KNOWN_BASES.contains(uc) || b == 0) {
				Assert.isTrue(Mutation.complement(Mutation.complement(lc)) == lc);
				Assert.isTrue(Mutation.complement(Mutation.complement(uc)) == uc);
			} else {
				try {
					Mutation.complement(b);
					fail("Should have thrown exception for " + new String(new byte[] {b}));
				} catch (IllegalArgumentException e) {
					//Expected exception
				}
			}
			if (b == Byte.MAX_VALUE) {
				break;
			}
		}
	}

}
