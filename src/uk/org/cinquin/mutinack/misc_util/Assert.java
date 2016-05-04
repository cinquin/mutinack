package uk.org.cinquin.mutinack.misc_util;

import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

public class Assert {
	public static void isTrue(boolean condition, String format, Object... args) {
		if (!condition) {
			ByteArrayOutputStream errStream = new ByteArrayOutputStream();
			PrintStream errPS = new PrintStream(errStream);
			errPS.printf(format, args);
			throw new AssertionFailedException(errStream.toString());
		}
	}
	
	public static void isFalse(boolean condition, String format, Object... args) {
		isTrue(!condition, format, args);
	}
	
	public static void isTrue(Supplier<Boolean> s, String format, Object... args) {
		isTrue(s.get(), format, args);
	}
	
	public static void isTrue(Supplier<Boolean> s, boolean nonTrivial, String format, Object... args) {
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS || !nonTrivial) {
			isTrue(s.get(), format, args);
		}
	}
	
	public static void isFalse(Supplier<Boolean> s, String format, Object... args) {
		isFalse(s.get(), format, args);
	}
	
	public static void isFalse(Supplier<Boolean> s, boolean nonTrivial, String format, Object... args) {
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS || !nonTrivial) {
			isFalse(s.get(), format, args);
		}
	}

	public static void isTrue(boolean condition) {
		isTrue(condition, "");
	}
	
	public static void isFalse(boolean condition) {
		isFalse(condition, "");
	}
	
	public static void noException(Runnable r) {
		r.run();
	}

	public static void isNonNull(Object o) {
		isFalse(o == null);
	}
	
	public static void isNonNull(Object o, String format, Object... args) {
		isFalse(o == null, format, args);
	}

	public static void isNull(Object o) {
		isTrue(o == null);
	}
}
