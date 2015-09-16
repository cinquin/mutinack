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
package uk.org.cinquin.mutinack.misc_util;

import java.io.Serializable;

public class SettableInteger implements Serializable {
	
	private static final long serialVersionUID = 8580008588332408546L;
	
	public int value;
	private boolean initialized;

	public SettableInteger (int value) {
		initialized = true;
		this.value = value;
	}
	
	public SettableInteger() {
		initialized = false;
	}
	
	private void checkInitialized() {
		if (!initialized) {
			throw new IllegalStateException("Not initialized");
		}		
	}
	
	public final int get() {
		checkInitialized();
		return value;
	}
	
	public final void set(int value) {
		initialized = true;
		this.value = value;
	}
	
	public final void set(SettableInteger i) {
		initialized = true;
		set(i.value);
	}
		
	public final int incrementAndGet() {
		checkInitialized();
		return ++value;
	}
	
	public final int addAndGet(int i) {
		checkInitialized();
		value += i;
		return value;
	}

	public int getAndIncrement() {
		checkInitialized();
		return value++;
	}
	
	@Override
	public String toString() {
		return Integer.toString(value);
	}

}
