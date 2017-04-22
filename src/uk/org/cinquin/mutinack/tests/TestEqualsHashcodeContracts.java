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

import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import uk.org.cinquin.mutinack.DuplexRead;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.distributed.Job;

//TODO Some classes are missing because ExtendedSAMRecord is recursive and
//is not easy to instantiate from scratch
@SuppressWarnings("static-method")
public class TestEqualsHashcodeContracts {

	@Test
	@Ignore//Fails because hashCode does not rely on referenceGenome, which is by design
	public void seqLocEqualsContract1() {
		EqualsVerifier.forClass(SequenceLocation.class).withCachedHashCode(
				"hash", "computeHash", new SequenceLocation(0, "", 0)).suppress(Warning.NULL_FIELDS).verify();
	}

	@Test
	@Ignore//Fails because of code generation error internal to EqualsVerifier
	public void duplexReadEqualsContract1() {
		EqualsVerifier.forClass(DuplexRead.class).
			suppress(Warning.NONFINAL_FIELDS).
			suppress(Warning.NULL_FIELDS).
			withPrefabValues(Thread.class, new Thread(), new Thread()).
			verify();
	}

	@SuppressWarnings({ "resource" })
	@Test
	@Ignore
	public void parametersJobEquals() {
		EqualsVerifier.forClass(Parameters.class).withPrefabValues(
				MutinackGroup.class, new MutinackGroup(false), new MutinackGroup(false)).
				suppress(Warning.NONFINAL_FIELDS).suppress(Warning.NULL_FIELDS).verify();
		EqualsVerifier.forClass(Job.class).withPrefabValues(
			MutinackGroup.class, new MutinackGroup(false), new MutinackGroup(false)).
			suppress(Warning.NONFINAL_FIELDS).suppress(Warning.NULL_FIELDS).verify();
	}
}
