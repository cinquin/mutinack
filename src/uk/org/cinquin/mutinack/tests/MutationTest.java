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
				if (b != 0) {
					Mutation.checkIsValidUCBase(uc);
				}
			} else {
				final byte bFinal = b;
				checkThrowsIllegalArg(() -> Mutation.complement(bFinal),
					"Should have thrown exception for " + new String(new byte[] {b}));
				checkThrowsIllegalArg(() -> Mutation.checkIsValidUCBase(bFinal),
					"Should have thrown exception for " + new String(new byte[] {b}));
			}
			if (b == Byte.MAX_VALUE) {
				break;
			}
		}
	}

	private static void checkThrowsIllegalArg(Runnable r, String errorMessage) {
		try {
			r.run();
			fail(errorMessage);
		} catch (IllegalArgumentException x) {
			return;
		}
	}

}
