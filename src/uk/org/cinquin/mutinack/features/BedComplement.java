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

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Util;

public class BedComplement implements GenomeFeatureTester, Serializable {

	private static final long serialVersionUID = -8569897991630712531L;
	private final GenomeFeatureTester featureSet;

	public BedComplement(GenomeFeatureTester featureSet) {
		this.featureSet = featureSet;
	}

	@Override
	public boolean test(SequenceLocation t) {
		return !featureSet.test(t);
	}

	@Override
	public @NonNull Collection<@NonNull GenomeInterval> apply(SequenceLocation t) {
		throw new RuntimeException("Unimplemented");
	}

	@Override
	public @NonNull Optional<Boolean> getNegativeStrand(SequenceLocation loc) {
		return Util.emptyOptional();
	}

}
