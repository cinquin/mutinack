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

import java.io.Serializable;

public class Handle<T> implements Serializable {

	private static final long serialVersionUID = -1949439930400688820L;
	private T t;

	public Handle(T t) {
		this.t = t;
	}
	
	public Handle() {
	}

	public T get() {
		return t;
	}

	public void set(T t) {
		this.t = t;
	}

	@Override
	public String toString() {
		if (t == null)
			return "null";
		return t.toString();
	}
}
