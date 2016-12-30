package uk.org.cinquin.mutinack.output.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ByteStringSerializer extends JsonSerializer<Byte> {

	@Override
	public void serialize(Byte value, JsonGenerator gen,
			SerializerProvider serializers) throws IOException {
		if (value != null) {
			gen.writeStartObject();
			gen.writeObjectField("value", new String(new byte[] {value}));
			gen.writeEndObject();
		}
	}

}
