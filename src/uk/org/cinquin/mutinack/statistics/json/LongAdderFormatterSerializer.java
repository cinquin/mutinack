package uk.org.cinquin.mutinack.statistics.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import uk.org.cinquin.mutinack.statistics.LongAdderFormatter;

public class LongAdderFormatterSerializer extends JsonSerializer<LongAdderFormatter> {

	@Override
	public void serialize(LongAdderFormatter laf, JsonGenerator jgen, SerializerProvider arg2) throws IOException,
			JsonProcessingException {
		jgen.writeStartObject();
		jgen.writeStringField("sum", laf.sum);
		jgen.writeEndObject();
	}

}
