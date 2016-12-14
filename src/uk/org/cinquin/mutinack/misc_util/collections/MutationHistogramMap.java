package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.Mutation;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.json.MutationHistogramMapSerializer;

@JsonSerialize(using = MutationHistogramMapSerializer.class)
public class MutationHistogramMap extends ActualizableConcurrentHashMap<Mutation, Histogram> {
	private static final long serialVersionUID = 499928736587546789L;

	@Override
	public String toString() {
		return entrySet().stream().map(e -> e.getKey().toLongString() + " = " + e.getValue().toString()).
			collect(Collectors.toList()).toString();
	}
}
