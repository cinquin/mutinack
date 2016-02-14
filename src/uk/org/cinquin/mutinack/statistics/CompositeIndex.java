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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

public interface CompositeIndex {
	List<@NonNull Object> getIndices();
	
	static @NonNull CompositeIndex asCompositeIndex(@NonNull List<@NonNull Object> l) {
			return new CompositeIndex() {
				@Override
				public List<@NonNull Object> getIndices() {
					return l;
				}

				@Override
				public @NonNull CompositeIndex pop() {
					return asCompositeIndex(new ArrayList<>(l.subList(1, l.size())));
				}
			};
	}
	
	static @NonNull CompositeIndex asCompositeIndex(@NonNull Object @NonNull[] a) {
		return asCompositeIndex(Arrays.asList(a));
	}

	@NonNull CompositeIndex pop();

}
