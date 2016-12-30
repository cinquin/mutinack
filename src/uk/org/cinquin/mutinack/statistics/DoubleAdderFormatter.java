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
package uk.org.cinquin.mutinack.statistics;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.concurrent.atomic.DoubleAdder;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.statistics.json.DoubleAdderFormatterSerializer;

@JsonSerialize(using=DoubleAdderFormatterSerializer.class)
public final class DoubleAdderFormatter extends DoubleAdder
		implements Comparable<DoubleAdderFormatter>, Serializable, Actualizable {

	private static final long serialVersionUID = -4695889470689380766L;

	@Override
	public String toString() {
		double dsum = sum();
		return formatDouble(dsum);
	}
	
	public static final ThreadLocal<NumberFormat> nf = ThreadLocal.withInitial(() -> {
		NumberFormat f = NumberFormat.getInstance();
		setNanAndInfSymbols(f);
		return f;
	});
	
	public static boolean setNanAndInfSymbols(NumberFormat f) {
		 if (f instanceof DecimalFormat) {
			 DecimalFormat f2 = (DecimalFormat) f;
			 DecimalFormatSymbols symbols = f2.getDecimalFormatSymbols();
			 symbols.setNaN("NaN");
			 symbols.setInfinity("Inf");
			 f2.setDecimalFormatSymbols(symbols);
			 return true;
		 } else
			 return false;
	}

	public static String formatDouble(double d) {
		NumberFormat f = nf.get();
		return f.format(d == Math.rint(d) ? (long) d : d);
	}

	@Override
	public int compareTo(DoubleAdderFormatter value) {
		return Double.compare(sum(), value.sum());
	}
	
	@Override
	public boolean equals(Object o) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public int hashCode() {
		throw new RuntimeException("Unimplemented");
	}
	
	public String sum;
	
	@Override
	public void actualize() {
		sum = toString();
	}

	//Following copied from parent class in JDK
	/**
	 * Serialization proxy, used to avoid reference to the non-public
	 * Striped64 superclass in serialized forms.
	 * @serial include
	 */
	private static class SerializationProxy implements Serializable {
		private static final long serialVersionUID = 7249069246863182397L;

		/**
		 * The current value returned by sum().
		 * @serial
		 */
		private final double value;

		SerializationProxy(DoubleAdderFormatter a) {
			value = a.sum();
		}

		/**
		 * Return a {@code LongAdder} object with initial state
		 * held by this proxy.
		 *
		 * @return a {@code LongAdder} object with initial state
		 * held by this proxy.
		 */
		private Object readResolve() {
			DoubleAdder a = new DoubleAdderFormatter();
			a.add(value);
			return a;
		}
	}

	/**
	 * Returns a
	 * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
	 * SerializationProxy</a>
	 * representing the state of this instance.
	 *
	 * @return a {@link SerializationProxy}
	 * representing the state of this instance
	 */
	private Object writeReplace() {
		return new SerializationProxy(this);
	}

	/**
	 * @param s the stream
	 * @throws java.io.InvalidObjectException always
	 */
	@SuppressWarnings("static-method")
	private void readObject(java.io.ObjectInputStream s)
			throws java.io.InvalidObjectException {
		throw new java.io.InvalidObjectException("Proxy required");
	}
}
