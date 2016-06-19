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

package uk.org.cinquin.mutinack.tests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.statistics.CounterWithSeqLocation;
import uk.org.cinquin.mutinack.statistics.DoubleAdderFormatter;
import uk.org.cinquin.mutinack.statistics.Histogram;
import uk.org.cinquin.mutinack.statistics.LongAdderFormatter;
import uk.org.cinquin.mutinack.statistics.MultiCounter;
import uk.org.cinquin.mutinack.statistics.StatsCollector;
import uk.org.cinquin.mutinack.statistics.json.DoubleAdderFormatterSerializer;
import uk.org.cinquin.mutinack.statistics.json.HistogramSerializer;
import uk.org.cinquin.mutinack.statistics.json.LongAdderFormatterSerializer;
import uk.org.cinquin.mutinack.statistics.json.StatsCollectorSerializer;

public class TestPhredLigSiteFormatting {

	@SuppressWarnings("static-method")
	@Test
	public void test() throws JsonGenerationException, JsonMappingException, IOException {
		try (MutinackGroup settings = new MutinackGroup()) {
			settings.setContigNames(new ArrayList<@NonNull String>() {
				private static final long serialVersionUID = 6273864437450331956L;

				{
					add("chrI");
				}});
			MultiCounter<ComparablePair<Integer, Integer>> phredAndLigSiteDistance = 
				new MultiCounter<>(() -> new CounterWithSeqLocation<>(true, settings),
					null, false);

			phredAndLigSiteDistance.accept(new SequenceLocation(0, "chrI", 0),
				new ComparablePair<>(2,3));

			ObjectMapper mapper = new ObjectMapper();
			SimpleModule module = new SimpleModule();
			module.addSerializer(LongAdderFormatter.class, new LongAdderFormatterSerializer()).
			addSerializer(DoubleAdderFormatter.class, new DoubleAdderFormatterSerializer()).
			addSerializer(Histogram.class, new HistogramSerializer()).
			addSerializer(StatsCollector.class, new StatsCollectorSerializer());
			mapper.registerModule(module).setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
				.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
				.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withSetterVisibility(JsonAutoDetect.Visibility.NONE)
				.withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
				.withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));
			mapper.writerWithDefaultPrettyPrinter().writeValue(
				new File("test_json.json"), phredAndLigSiteDistance);
		}
	}

}
