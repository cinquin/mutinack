package uk.org.cinquin.mutinack.statistics.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;

public class DoubleAdderFormatterSerializer extends JsonSerializer<DoubleAdderFormatter> {

	@Override
	public void serialize(DoubleAdderFormatter daf, JsonGenerator jgen, SerializerProvider arg2) throws IOException,
			JsonProcessingException {
		jgen.writeStartObject();
		jgen.writeStringField("sum", daf.sum);
		jgen.writeEndObject();
	}

}
