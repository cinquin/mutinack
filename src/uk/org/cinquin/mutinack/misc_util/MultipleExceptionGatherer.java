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

import java.util.ArrayList;
import java.util.List;

public class MultipleExceptionGatherer {
	List<Throwable> exceptions = new ArrayList<>();

	public void tryAdd(Runnable r) {
		try {
			r.run();
		} catch (Exception e) {
			exceptions.add(e);
		}
	}
	
	public void throwIfPresent() {
		if (exceptions.size() > 1) {
			throw new MultipleExceptions(exceptions);
		} else if (exceptions.size() > 0) {
			throw new RuntimeException(exceptions.get(0));
		}
	}
}
