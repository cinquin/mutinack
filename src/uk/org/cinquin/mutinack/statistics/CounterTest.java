package uk.org.cinquin.mutinack.statistics;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import uk.org.cinquin.mutinack.misc_util.SerializableFunction;

@SuppressWarnings("static-method")
public class CounterTest {

	@SuppressWarnings("null")
	@Test
	public void testMain() {
		//TODO Should update this test to read the counter's contents, once an
		//API for that is put together, and not just check String output
		Counter<Object> c = new Counter<>(false);
		SerializableFunction<Object, Object> map = (o -> o.equals(3) ? "trois" : o);
		c.setKeyNamePrintingProcessor(Arrays.asList(null, map, null));
		c.accept(CompositeIndex.asCompositeIndex(new Object[] {2,3,4}), 10);
		c.accept(CompositeIndex.asCompositeIndex(new Object[] {2,3,7}), 20);
		c.accept(CompositeIndex.asCompositeIndex(new Object[] {4,3,2}), 1);
		c.accept(CompositeIndex.asCompositeIndex(new Object[] {2,3,7}), 12);
		c.accept(CompositeIndex.asCompositeIndex(new Object[] {2,7,7}), 13);
		
		String reference =
				"56\n" +
				"2: 55\n" + 
				"  trois: 42\n" + 
				"    {4=10, 7=32}\n" + 
				"  7: 13\n" + 
				"    {7=13}\n" + 
				"4: 1\n" + 
				"  trois: 1\n" + 
				"    {2=1}";		
		
		assertTrue(c.toString().equals(reference));
	}

}
