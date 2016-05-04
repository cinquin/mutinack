package uk.org.cinquin.mutinack.statistics.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import uk.org.cinquin.mutinack.statistics.Histogram;

public class HistogramSerializer extends JsonSerializer<Histogram> {

	@Override
	public void serialize(Histogram value, JsonGenerator gen, SerializerProvider serializers) throws IOException,
			JsonProcessingException {
        gen.writeStartObject();
        gen.writeStringField("nEntries", value.nEntries);
        gen.writeStringField("average", value.average);
        gen.writeStringField("median", value.median);
        if (!gen.canOmitFields() || (value.notes != null && !value.notes.isEmpty())) {
        	gen.writeStringField("nEntries", value.nEntries);
        }
        List<Long> values = new ArrayList<>();
        value.stream().map(laf -> laf.sum()).forEachOrdered(s -> values.add(s));
        gen.writeObjectField("values", values);
        gen.writeEndObject();
	}
}