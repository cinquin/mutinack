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

package uk.org.cinquin.mutinack.misc_util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public class Assert {
	@SuppressWarnings("rawtypes")
	public static void isTrue(boolean condition, String format, Object... args) {
		if (!condition) {
			ByteArrayOutputStream errStream = new ByteArrayOutputStream();
			PrintStream errPS = new PrintStream(errStream);
			Object[] argsRetrievedStrings = new Object[args.length];
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Supplier) {
						argsRetrievedStrings[i] = ((Supplier) args[i]).get();
				} else {
					argsRetrievedStrings[i] = args[i];
				}
			}
			errPS.printf(format, argsRetrievedStrings);
			throw new AssertionFailedException(errStream.toString());
		}
	}

	public static void isTrue(boolean condition, String message) {
		if (!condition) {
			//noinspection ConstantConditions
			isTrue(condition, message, "");
		}
	}

	public static void isTrue(boolean condition, Supplier<String> format, Object... args) {
		if (!condition) {
			//noinspection ConstantConditions
			isTrue(condition, format.get(), args);
		}
	}

	public static void isFalse(boolean condition, String format, Object... args) {
		isTrue(!condition, format, args);
	}

	public static void isFalse(boolean condition, String message) {
		if (condition) {
			//noinspection ConstantConditions
			isFalse(condition, message, "");
		}
	}

	public static void isFalseVarArg(boolean condition, Supplier<String> format, Object... args) {
		isTrue(!condition, format, args);
	}

	public static void isFalse(boolean condition, Supplier<String> message) {
		isTrue(!condition, message);
	}

	/**
	 * Use this form instead of varags form above to avoid varargs array creation.
	 * @param condition
	 * @param arg1
	 * @param arg2
	 * @param arg3
	 * @param arg4
	 * @param format
	 */
	public static void isFalse(boolean condition,
			Supplier<Object> arg1,
			Supplier<Object> arg2,
			Supplier<Object> arg3,
			Supplier<Object> arg4,
			String format) {
		if (condition) {
			isTrue(false, format, arg1.get(), arg2.get(), arg3.get(), arg4.get());
		}
	}

	public static void isTrue(boolean condition,
			Supplier<Object> arg1,
			Supplier<Object> arg2,
			String format) {
		if (!condition) {
			isTrue(false, format, arg1.get(), arg2.get());
		}
	}

	public static void isTrueVarArg(Supplier<Boolean> s, String format, Object... args) {
		isTrue(s.get(), format, args);
	}

	public static void isTrue(Supplier<Boolean> s, String message) {
		isTrue(s.get(), message);
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
