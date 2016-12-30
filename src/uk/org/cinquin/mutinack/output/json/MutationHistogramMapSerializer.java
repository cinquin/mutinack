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

package uk.org.cinquin.mutinack.output.json;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import uk.org.cinquin.mutinack.misc_util.collections.MutationHistogramMap;
import uk.org.cinquin.mutinack.statistics.Histogram;

public class MutationHistogramMapSerializer extends JsonSerializer<@NonNull MutationHistogramMap> {

	@Override
	public void serialize(@NonNull MutationHistogramMap value, JsonGenerator gen,
			SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        Map<String, Histogram> values = new HashMap<>();
        value.entrySet().forEach(e ->values.put(e.getKey().toLongString(), e.getValue()));
        gen.writeObjectField("values", values);
        gen.writeEndObject();
	}
}
