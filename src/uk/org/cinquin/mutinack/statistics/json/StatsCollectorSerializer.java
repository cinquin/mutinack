package uk.org.cinquin.mutinack.statistics.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import uk.org.cinquin.mutinack.statistics.StatsCollector;

public class StatsCollectorSerializer extends JsonSerializer<StatsCollector> {

	@Override
	public void serialize(StatsCollector value, JsonGenerator gen, SerializerProvider serializers) throws IOException,
			JsonProcessingException {
        gen.writeStartObject();
        gen.writeStringField("total", value.total);
        List<Long> values = new ArrayList<>();
        value.values.stream().map(laf -> laf.sum()).forEachOrdered(s -> values.add(s));
        gen.writeObjectField("values", values);
        gen.writeEndObject();
	}
}