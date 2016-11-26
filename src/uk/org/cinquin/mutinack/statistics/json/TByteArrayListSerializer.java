package uk.org.cinquin.mutinack.statistics.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import gnu.trove.list.array.TByteArrayList;

public class TByteArrayListSerializer extends JsonSerializer<@NonNull TByteArrayList> {

	@Override
	public void serialize(@NonNull TByteArrayList value, JsonGenerator gen,
			SerializerProvider serializers) throws IOException {
		gen.writeStartObject();
		List<Integer> values = new ArrayList<>();
		value.forEach(b -> values.add((int) b));
		gen.writeObjectField("values", values);
		gen.writeEndObject();
	}

}
