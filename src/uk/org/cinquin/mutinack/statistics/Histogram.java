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
import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.statistics.json.HistogramSerializer;

@JsonSerialize(using = HistogramSerializer.class)
public class Histogram extends ArrayList<LongAdderFormatter>
		implements SwitchableStats, Serializable, Actualizable {
	@JsonIgnore
	protected boolean on = true;
	private static final long serialVersionUID = -1557536590861199764L;
	
	@JsonIgnore
	public final int maxSize;

	public String nEntries;
	public String average;
	public String median;
	public String notes;
	
	private final LongAdderFormatter sum = new LongAdderFormatter();
	
	public Histogram(int maxSize) {
		super(maxSize);
		if (maxSize < 1) {
			throw new IllegalArgumentException("Histogram max size must be at least 1");
		}
		this.maxSize = maxSize;
	}

	@Override
	public String toString() {
		double nEntriesAsD = 0;
		boolean medianSet = false;
		float medianAsF = Float.NaN;
		long size = size();
		double totalNEntries = 0;
		for (LongAdderFormatter longAdderFormatter: this) {
			totalNEntries += longAdderFormatter.doubleValue();
		}
		int index = 0;
		for (; index < size; index++) {
			nEntriesAsD += get(index).doubleValue();
			if (!medianSet && nEntriesAsD >= totalNEntries * 0.5) {
				medianSet = true;
				medianAsF = index;
			}
		}
		return super.toString() + "; nEntries = " + DoubleAdderFormatter.formatDouble(nEntriesAsD) +
			"; average = " + DoubleAdderFormatter.formatDouble(sum.sum() / nEntriesAsD) +
			"; median = " + DoubleAdderFormatter.formatDouble(medianAsF) +
				(index == size - 1 ? " (UNDERESTIMATE)" : "");
	}

	public double @NonNull[] toProbabilityArray(boolean smoothen) {
		final double @NonNull[] result = new double[maxSize];

		for (int i = size() - 1; i >= 0; i--) {
			result[i] = get(i).sum();
		}

		if (smoothen) {
			smoothen(result);
		}

		double resultSum = 0;
		for (int i = 0; i < result.length; i++) {
			resultSum += result[i];
		}

		final double sumInverse = 1d / resultSum;
		for (int i = 0; i < result.length; i++) {
			result[i] *= sumInverse;
		}
		return result;
	}

	public void insert(int value) {
		if (!on)
			return;
		sum.add(value);
		value = Math.min(value, maxSize - 1);
		if (size() < value + 1) {
			synchronized(this) {
				while (size() < value + 1) {
					add(new LongAdderFormatter());
				}
			}
		}
		try {
			get(value).increment();
		} catch (NullPointerException e) {
			//List might have been in the process of growing while
			//we were trying to grab the element at index value
			synchronized(this) {
				get(value).increment();
			}
		}
	}
	
	@Override
	public void turnOff() {
		on = false;
	}

	@Override
	public void turnOn() {
		on = true;
	}

	@Override
	public boolean isOn() {
		return on;
	}
	
	@Override
	public void actualize() {
		double nEntries0 = 0;
		boolean medianSet = false;
		float median0 = Float.NaN;
		long size = size();
		double totalNEntries = 0;
		for (LongAdderFormatter longAdderFormatter: this) {
			longAdderFormatter.actualize();
			totalNEntries += longAdderFormatter.doubleValue();
		}
		int index = 0;
		for (; index < size; index++) {
			nEntries0 += get(index).doubleValue();
			if (!medianSet && nEntries0 >= totalNEntries * 0.5) {
				medianSet = true;
				median0 = index;
			}
		}
		nEntries = DoubleAdderFormatter.formatDouble(nEntries0);
		average = DoubleAdderFormatter.formatDouble(sum.sum() / nEntries0);
		median = DoubleAdderFormatter.formatDouble(median0);
		notes = index == size - 1 ? " (UNDERESTIMATE)" : "";
	}

	private static int indexOfMax(double[] a) {
		double max = -Double.MAX_VALUE;
		int result = -1;
		for (int i = a.length - 1; i >= 0; i--) {
			if (a[i] > max) {
				max = a[i];
				result = i;
			}
		}
		return result;
	}

	private final static double exponentialSmoothingAlphaHigh = 0.5;
	private final static double exponentialSmoothingAlphaLow = 0.2;

	private static void smoothen(double [] a, final int startIndex, double startValue, final int increment) {
		int position = startIndex;
		double smoothened = startValue;
		boolean seenLowValue = false;
		double currentAlpha = exponentialSmoothingAlphaHigh;
		while (position >= 0 && position < a.length) {
			final double valueAtPosition = a[position];
			if (!seenLowValue && valueAtPosition < 50) {
				seenLowValue = true;
				currentAlpha = exponentialSmoothingAlphaLow;
			}
			smoothened = currentAlpha * valueAtPosition +
				(1 - currentAlpha) * smoothened;
				a[position] = smoothened;
			position += increment;
		}
	}

	private static void smoothen(double [] a) {
		int maxIndex = indexOfMax(a);
		double localAverage = 0;
		int nPoints = 0;
		for (int i = Math.max(0, maxIndex - 3); i < Math.min(a.length - 1, maxIndex + 3); i++) {
			nPoints++;
			localAverage += a[i];
		}
		localAverage /= nPoints;
		smoothen(a, maxIndex, localAverage, 1);
		smoothen(a, maxIndex, localAverage, -1);
	}
}
