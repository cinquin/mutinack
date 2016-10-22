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
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.SerializableFunction;

/**
 * Reports counts at each position; best used on small genome regions only.
 * See also {@link CounterWithSeqLocation}.
 * @author olivier
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CounterWithSeqLocOnlyReportAll extends Counter implements ICounterSeqLoc, Serializable {

	private static final long serialVersionUID = 6490500502909806438L;

	public CounterWithSeqLocOnlyReportAll(boolean sortByValue, MutinackGroup groupSettings) {
		super(sortByValue, groupSettings);
		List<SerializableFunction<Object, Object>> contigNames0 = new ArrayList<>();
		contigNames0.add((Object i) -> groupSettings.getContigNames().get((Integer) i));
		contigNames0.add(i -> i);
		setKeyNamePrintingProcessor(contigNames0);
	}

	@Override
	public void accept(@NonNull SequenceLocation loc) {
		if (on)
			super.acceptVarArgs(1d, loc.contigIndex, loc.position);
	}

	@Override
	public void accept (@NonNull SequenceLocation loc, long n) {
		if (on)
			super.acceptVarArgs(n, loc.contigIndex, loc.position);
	}

	@Override
	public double totalSum() {
		return sum();
	}

	@Override
	public void accept(@NonNull SequenceLocation loc, double d) {
		if (on) {
			List<Object> l = new ArrayList<>(2);
			l.add(loc.contigIndex);
			l.add(loc.position);
			super.accept(l, d);
		}
	}
}
