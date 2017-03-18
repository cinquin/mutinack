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
