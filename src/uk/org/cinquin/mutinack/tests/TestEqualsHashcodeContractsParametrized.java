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

package uk.org.cinquin.mutinack.tests;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.collections.MapBalancer;

//TODO Some classes are missing because ExtendedSAMRecord is recursive and
//is not easy to instantiate from scratch
@RunWith(Parameterized.class)
public class TestEqualsHashcodeContractsParametrized {

	private static final List<Class<?>> classes =
			Arrays.asList(Mutation.class, Pair.class, ByteArray.class,
				MapBalancer.class, GenomeInterval.class);

	@org.junit.runners.Parameterized.Parameters(name = "{0}")
	public static Iterable<Object[]> data() {
		List<Object[]> result = classes.stream().map(s -> new Object [] {s}).
			collect(Collectors.toList());
		return result;
	}

	@Parameter(0)
	public Class<?> clazz;

	@Test
	public void defaultEqualsContractTest() {
		EqualsVerifier.forClass(clazz).suppress(Warning.NONFINAL_FIELDS).suppress(Warning.NULL_FIELDS).verify();
	}

}
