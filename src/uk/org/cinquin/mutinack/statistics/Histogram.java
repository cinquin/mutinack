/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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

public class Histogram extends ArrayList<LongAdderFormatter>
		implements SwitchableStats, Serializable {
	protected boolean on = true;
	private static final long serialVersionUID = -1557536590861199764L;
	
	private final int maxSize;
	private final LongAdderFormatter sum = new LongAdderFormatter();
	
	public Histogram(int maxSize) {
		super(maxSize);
		this.maxSize = maxSize;
	}

	@Override
	public String toString() {
		double nEntries = 0;
		boolean medianSet = false;
		int median = 0;
		long size = size();
		double totalNEntries = 0;
		for (LongAdderFormatter longAdderFormatter: this) {
			totalNEntries += longAdderFormatter.doubleValue();
		}
		int index = 0;
		for (; index < size; index++) {
			nEntries += get(index).doubleValue();
			if (!medianSet && nEntries >= totalNEntries * 0.5) {
				medianSet = true;
				median = index;
			}
		}
		return super.toString() + "; nEntries = " + DoubleAdderFormatter.formatDouble(nEntries) +
			"; average = " + DoubleAdderFormatter.formatDouble(sum.sum() / nEntries) +
			"; median = " + DoubleAdderFormatter.formatDouble(median) +
				(index == size - 1 ? " (UNDERESTIMATE)" : "");
	}

	public void insert (int value) {
		if (!on)
			return;
		sum.add(value);
		value = Math.min(value, maxSize);
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
}