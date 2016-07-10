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
import uk.org.cinquin.mutinack.DuplexRead;
import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.distributed.Job;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.collections.ByteArray;
import uk.org.cinquin.mutinack.misc_util.collections.MapBalancer;

//TODO Some classes are missing because ExtendedSAMRecord is recursive and
//is not easy to instantiate from scratch
@RunWith(Parameterized.class)
public class TestEqualsHashcodeContracts {
	
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
		EqualsVerifier.forClass(clazz).suppress(Warning.NONFINAL_FIELDS).verify();
	}
	
	@SuppressWarnings("static-method")
	@Test
	public void seqLocEqualsContract1() {
		EqualsVerifier.forClass(SequenceLocation.class).withCachedHashCode(
				"hash", "computeHash", new SequenceLocation(0, "", 0)).verify();
	}
	
	@SuppressWarnings("static-method")
	@Test
	public void duplexReadEqualsContract1() {
		EqualsVerifier.forClass(DuplexRead.class).withPrefabValues(
				Thread.class, new Thread(), new Thread()).
				suppress(Warning.NONFINAL_FIELDS).verify();
	}

	@SuppressWarnings({ "static-method", "resource" })
	@Test
	public void parametersJobEquals() {
		EqualsVerifier.forClass(Parameters.class).withPrefabValues(
				MutinackGroup.class, new MutinackGroup(), new MutinackGroup()).
				suppress(Warning.NONFINAL_FIELDS).verify();
		EqualsVerifier.forClass(Job.class).withPrefabValues(
			MutinackGroup.class, new MutinackGroup(), new MutinackGroup()).
			suppress(Warning.NONFINAL_FIELDS).verify();
	}
}
