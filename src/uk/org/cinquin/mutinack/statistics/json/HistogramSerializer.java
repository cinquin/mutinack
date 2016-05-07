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
