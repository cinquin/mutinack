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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

public class MultipleExceptionGatherer {
	private List<Throwable> exceptions = new ArrayList<>();

	@FunctionalInterface
	public interface ThrowingRunnable {
		void run() throws Exception;
	}

	public void tryAdd(ThrowingRunnable r) {
		try {
			r.run();
		} catch (Exception e) {
			exceptions.add(e);
		}
	}
	
	public void add(@Nullable Throwable t) {
		if (t != null) {
			exceptions.add(t);
		}
	}

	public void throwIfPresent() {
		if (exceptions.size() > 1) {
			throw new MultipleExceptions(exceptions);
		} else if (!exceptions.isEmpty()) {
			throw new RuntimeException(exceptions.get(0));
		}
	}

	@Override
	public String toString() {
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		exceptions.forEach(t -> {
			printWriter.append(t.toString()).append("\n");
			t.printStackTrace(printWriter);
			printWriter.append("-----\n");
		});
		return stringWriter.toString();
	}
}
