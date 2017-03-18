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

import org.junit.Test;

import uk.org.cinquin.mutinack.candidate_sequences.Quality;
import uk.org.cinquin.mutinack.misc_util.Assert;

public class QualityTest {

	@SuppressWarnings("static-method")
	@Test
	public void testMax() {
		Assert.isTrue(Quality.max(Quality.ATROCIOUS, Quality.DUBIOUS) == Quality.DUBIOUS);
		Assert.isTrue(Quality.max(Quality.ATROCIOUS, Quality.ATROCIOUS) == Quality.ATROCIOUS);
		Assert.isTrue(Quality.max(Quality.ATROCIOUS, Quality.GOOD) == Quality.GOOD);
		Assert.isTrue(Quality.max(Quality.DUBIOUS, Quality.GOOD) == Quality.GOOD);
	}

	@SuppressWarnings("static-method")
	@Test
	public void testMin() {
		Assert.isTrue(Quality.min(Quality.ATROCIOUS, Quality.DUBIOUS) == Quality.ATROCIOUS);
		Assert.isTrue(Quality.min(Quality.ATROCIOUS, Quality.ATROCIOUS) == Quality.ATROCIOUS);
		Assert.isTrue(Quality.min(Quality.ATROCIOUS, Quality.GOOD) == Quality.ATROCIOUS);
		Assert.isTrue(Quality.min(Quality.DUBIOUS, Quality.GOOD) == Quality.DUBIOUS);
	}
}
