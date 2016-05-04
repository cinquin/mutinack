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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.statistics.json.StatsCollectorSerializer;

@JsonSerialize(using=StatsCollectorSerializer.class)
public class StatsCollector implements Serializable, Traceable, Actualizable {
	
	private static final long serialVersionUID = -2681471547369656383L;
	
	@JsonIgnore
	private String tracePrefix = null;

	public final @NonNull List<@NonNull LongAdderFormatter> values =
			new ArrayList<>();

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
			Objects.requireNonNull(result);
		}
		return result;
	}
	
	public void increment(@NonNull SequenceLocation location) {
		if (tracePrefix != null) {
			System.err.println(tracePrefix + "+1 at " + location);
		}
		get(location.contigIndex).increment();
	}

	public void add(@NonNull SequenceLocation location, long n) {
		if (tracePrefix != null) {
			System.err.println(tracePrefix + "+" + n + " at " + location);
		}
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
		return values.toString() + "; total: " + DoubleAdderFormatter.nf.get().format(
				transformer.apply(sum()));
	}

	@Override
	public void setPrefix(@Nullable String prefix) {
		tracePrefix = prefix;
	}

	public String total;
	
	@Override
	public void actualize() {
		total = DoubleAdderFormatter.nf.get().format(sum());
		for (LongAdderFormatter laf: values) {
			laf.actualize();
		}
	}
}

