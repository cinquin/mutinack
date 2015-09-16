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
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.SerializableFunction;

/**
 * Reports counts following contig blocks whose size is defined by {@link #binSize}.
 * See also {@link CounterWithSeqLocation}.
 * @author olivier
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class CounterWithSeqLocOnly extends Counter implements ICounterSeqLoc, Serializable {
		
	private static final long serialVersionUID = 6490500502909806438L;
	public static Map<Object, @NonNull Object> contigNames;
	
	public CounterWithSeqLocOnly(boolean sortByValue) {
		super(sortByValue);
		List<SerializableFunction<Object, Object>> contigNames0 = new ArrayList<>();
		contigNames0.add(contigNames::get);
		contigNames0.add(i -> ((Integer) i) * binSize);
		setKeyNamePrintingProcessor(contigNames0);
	}
	
	private static final int binSize = 1_000_000;
	
	@Override
	public void accept(@NonNull SequenceLocation loc) {
		if (on)
			super.acceptVarArgs(1d, loc.contigIndex, loc.position / binSize);
	}
	
	@Override
	public void accept (@NonNull SequenceLocation loc, long n) {
		if (on)
			super.acceptVarArgs(n, loc.contigIndex, loc.position / binSize);
	}
	
	@Override
	public double totalSum() {
		return sum();
	}

	@Override
	public void accept(@NonNull SequenceLocation loc, double d) {
		if (on)
			super.acceptVarArgs(d, loc.contigIndex, loc.position / binSize);
	}
}
