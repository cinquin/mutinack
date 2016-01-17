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
	
	private final static List<Class<?>> classes = 
			Arrays.asList(new Class<?>[] {Mutation.class, DuplexRead.class, Job.class,
				Pair.class, ByteArray.class, MapBalancer.class,
				GenomeInterval.class, Parameters.class});
	
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
}
