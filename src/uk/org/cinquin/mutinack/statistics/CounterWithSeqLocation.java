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
 * Reports counts following contig blocks whose size is defined by {@link #BIN_SIZE},
 * and additionally breaking down counts using an indexing object.
 * @author olivier
 *
 * @param <T>
 */
public class CounterWithSeqLocation<T> extends Counter<T> implements Serializable {
	
	private static final long serialVersionUID = 1472307547807376993L;

	public static final int BIN_SIZE = 100_000;

	public static Map<Object, @NonNull Object> contigNames;
	
	public CounterWithSeqLocation(boolean sortByValue) {
		super(sortByValue);
		List<SerializableFunction<Object, Object>> contigNames0 = new ArrayList<>();
		contigNames0.add(0, null);
		contigNames0.add(contigNames::get);
		contigNames0.add(i -> ((Integer) i) * BIN_SIZE);
		setKeyNamePrintingProcessor(contigNames0);
	}
	
	public CounterWithSeqLocation() {
		this(false);
	}
		
	public void accept(SequenceLocation loc, Object o) {
		super.acceptVarArgs(1d, o, loc.contigIndex, loc.position / BIN_SIZE);
	}
}
