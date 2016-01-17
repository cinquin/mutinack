package uk.org.cinquin.mutinack.tests;

import org.junit.Test;

import uk.org.cinquin.mutinack.Quality;
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
