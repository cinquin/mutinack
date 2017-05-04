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
package uk.org.cinquin.mutinack.features;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.SerializablePredicate;

public interface GenomeFeatureTester extends Function<SequenceLocation,
	@NonNull Collection<@NonNull GenomeInterval>>, SerializablePredicate<SequenceLocation> {

	@Override
	boolean test(SequenceLocation loc);

	@Override
	@NonNull Collection<@NonNull GenomeInterval> apply(SequenceLocation loc);

	@NonNull Optional<Boolean> getNegativeStrand(SequenceLocation loc);

}
