package uk.org.cinquin.mutinack.output.json;

import java.io.IOException;

import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ByteArrayStringSerializer extends JsonSerializer<byte @Nullable[]> {

	@Override
	public void serialize(byte @Nullable[] value, JsonGenerator gen,
			SerializerProvider serializers) throws IOException {
		if (value != null) {
			gen.writeStartObject();
			gen.writeObjectField("value", new String(value));
			gen.writeEndObject();
		}
	}

}
