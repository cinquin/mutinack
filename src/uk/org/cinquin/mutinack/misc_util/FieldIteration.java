package uk.org.cinquin.mutinack.misc_util;

import java.lang.reflect.Field;

public class FieldIteration {

	@FunctionalInterface
	public interface ThrowingFieldValueBiConsumer {
		void accept(Field field, Object fieldValue) throws IllegalArgumentException, IllegalAccessException;
	}

	public static void iterateFields(ThrowingFieldValueBiConsumer consumer, Object o, Class<?> clazz) {
		for (Field field: clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);
				consumer.accept(field, field.get(o));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void iterateFields(ThrowingFieldValueBiConsumer consumer, Object o) {
		iterateFields(consumer, o, o.getClass());
	}

}
