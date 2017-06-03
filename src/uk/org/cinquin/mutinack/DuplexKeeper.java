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

package uk.org.cinquin.mutinack;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNull;

public interface DuplexKeeper {
	@NonNull Collection<DuplexRead> getOverlapping(DuplexRead d);
	@NonNull List<DuplexRead> getOverlappingWithSlop(DuplexRead d, int shift, int slop);
	@NonNull Iterable<DuplexRead> getStartingAtPosition(int position);
	@NonNull Iterable<DuplexRead> getIterable();
	void forEach(Consumer<? super DuplexRead> consumer);
	boolean add(DuplexRead d);
	DuplexRead getAndRemove(DuplexRead d);
	boolean supportsMutableDuplexes();
	boolean contains(DuplexRead duplexRead);
	int size();

}
