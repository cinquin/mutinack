package uk.org.cinquin.mutinack.tests;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;

public class PitestTestHelper {

	private static final Logger logger = LoggerFactory.getLogger(PitestTestHelper.class);

	public boolean b;
	
	public void testAvoidMethodCall() {
		if (DebugLogControl.shouldLog(contrib.uk.org.lidalia.slf4jext.Level.WARN, logger)) {
			Assert.isTrue("".equals("".substring(0)));
		}
		b = false;
		Assert.isFalse(b);
		if (b) {
			throw new RuntimeException();
		}
	}
}
