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
	
	public static final ThreadLocal<NumberFormat> nf = new ThreadLocal<NumberFormat>() {
		@Override
		protected NumberFormat initialValue() {
			NumberFormat f = NumberFormat.getInstance();
			setNanAndInfSymbols(f);
			return f;
		}
	};
	
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

}
