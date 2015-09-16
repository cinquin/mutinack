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

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;


public class StatsCollector implements Serializable {
	
	private static final long serialVersionUID = -2681471547369656383L;

	private final @NonNull List<@NonNull LongAdderFormatter> values = new ArrayList<>();

	private @NonNull LongAdderFormatter get(int index) {
		if (values.size() < index + 1) {
			synchronized(values) {
				while (values.size() < index + 1) {
					values.add(new LongAdderFormatter());
				}
			}
		}
		LongAdderFormatter result = Util.nullableify(values.get(index));
		if (result == null) {
			synchronized(values) {
				result = Util.nullableify(values.get(index));
			}
			if (result == null) {
				throw new AssertionFailedException();
			}
		}
		return result;
	}
	
	public void increment(@NonNull SequenceLocation location) {
		get(location.contigIndex).increment();
	}

	public void add(@NonNull SequenceLocation location, long n) {
		get(location.contigIndex).add(n);
	}
	
	public long sum() {
		try {
			return values.stream().mapToLong(LongAdder::sum).sum();
		} catch (Exception e) {//Problems can happen because of concurrent modification
			synchronized(values) {
				return values.stream().mapToLong(LongAdder::sum).sum();
			}
		}
	}

	@Override
	public String toString() {
		return toString(l -> l);
	}
	
	public String toString(@NonNull Function<Long, Long> transformer) {
		return values.toString() + "; total: " + NumberFormat.getInstance().format(
				transformer.apply(sum()));
	}
}

