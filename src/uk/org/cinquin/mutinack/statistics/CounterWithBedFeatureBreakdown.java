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
package uk.org.cinquin.mutinack.statistics;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.BedReader;
import uk.org.cinquin.mutinack.features.GenomeInterval;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.Util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.AbstractMap;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author olivier
 *
 */
@SuppressWarnings("null")
public class CounterWithBedFeatureBreakdown implements ICounterSeqLoc, Serializable {
	
	private static final long serialVersionUID = 9168551060568948486L;

	@JsonIgnore
	protected boolean on = true;
	
	private final @NonNull Counter<GenomeInterval> counter;
	@JsonIgnore
	private final @NonNull BedReader bedFeatures;
	@JsonIgnore
	private @Nullable File outputFile;
	@JsonIgnore
	private final @Nullable Function<@NonNull String, @NonNull String> supplementaryInfoProvider;
	@JsonIgnore
	private final @Nullable Map<@NonNull String, @NonNull String> refSeqToOfficialGeneName;
	@JsonIgnore
	private @Nullable String analyzerName = null;
	@JsonIgnore
	private boolean normalizedOutput = false;
	
	public CounterWithBedFeatureBreakdown(@NonNull BedReader bedFeatures, 
			Map<@NonNull String, @NonNull String> refSeqToOfficialGeneName,
			MutinackGroup groupSettings) {
		counter = new Counter<>(false, groupSettings);
		this.bedFeatures = bedFeatures;
		supplementaryInfoProvider = bedFeatures::getSuppInfo;
		this.refSeqToOfficialGeneName = refSeqToOfficialGeneName;				
	}
		
	@Override
	public void accept(@NonNull SequenceLocation loc, long n) {
		if (on)
			accept(loc, (double) n);
	}
	
	@Override
	public void accept(@NonNull SequenceLocation loc, double d) {
		if (!on)
			return;
		for (GenomeInterval f: bedFeatures.apply(loc)) {
			counter.accept(f, d * f.getLengthInverse());
		}
	}
	
	@Override
	public void accept (@NonNull SequenceLocation loc) {
		if (on)
			accept(loc, 1d);
	}
	
	private final NumberFormat formatter = new DecimalFormat("0.###E0");

	private String getStats() {
		if (counter.getCounts().isEmpty()) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		double[] coverage = counter.getCounts().entrySet().stream().
				mapToDouble(e -> ((DoubleAdderFormatter) e.getValue()).sum()).
				sorted().toArray();
		result.append("median = ").append(DoubleAdderFormatter.
				formatDouble(coverage[coverage.length/2])).append("; mean = ");
		result.append(formatter.format(
				counter.getCounts().entrySet().stream().mapToDouble(e ->
					((DoubleAdderFormatter) e.getValue()).sum()).average().getAsDouble()));
		result.append("\n");
		return result.toString();
	}
	
	private String getString() {
		StringBuilder result = new StringBuilder();
		if (normalizedOutput) {
			final List<Entry<@NonNull String, Pair<Double, Double>>> entriesByName = counter.getCounts().
					entrySet().stream().sorted(
							(a, b) -> - ((GenomeInterval) a.getKey()).name.
							compareTo(((GenomeInterval) b.getKey()).name)).
					map(e -> 
						new AbstractMap.SimpleImmutableEntry<>(((GenomeInterval) e.getKey()).name, 
						new Pair<>(((DoubleAdderFormatter) e.getValue()).sum(), //Already normalized to transcript length
								((DoubleAdderFormatter) e.getValue()).sum() * ((GenomeInterval) e.getKey()).getLength()))).
					collect(Collectors.toList());
			
			final DoubleSummaryStatistics coverageStats =
					entriesByName.stream().mapToDouble(o -> o.getValue().fst).summaryStatistics();
			final double sumCoverage = coverageStats.getSum();

			result.append("RefSeqID\t" + (analyzerName + "_raw\t") + (analyzerName + "_length_n\t") +
					(analyzerName + "_TPM\t") +
					"geneName\t" +
					"suppInfo\n" 
					);

			Function<String, String> suppInfoP = this.supplementaryInfoProvider;
			
			for (Entry<@NonNull String, Pair<Double, Double>> e: entriesByName) {				
				result.append(e.getKey() + "\t" + //RefSeqID
						e.getValue().snd + "\t" + //Raw counts
						e.getValue().fst + "\t" + //Counts normalized to transcript length
						(e.getValue().fst / sumCoverage * 1_000_000 ) + "\t" + //TPM (Wagner et al, Theory Biosci 2012)
						(refSeqToOfficialGeneName == null ? "" :
							refSeqToOfficialGeneName.get(e.getKey())) + "\t" +
						(suppInfoP == null ? "" : ("\t" + suppInfoP.apply(e.getKey()))) +
						"\n");
			}			
			return result.toString();
		} else {
			counter.getCounts().entrySet().stream().sorted((a, b) -> - ((DoubleAdderFormatter) a.getValue()).
					compareTo((DoubleAdderFormatter) b.getValue())).forEach (e -> 
					{
						result.append(e.getKey()).append("=").append(e.getValue()).append(", ");
					});
			String r = result.toString();
			return r.isEmpty() ? "" : r.substring(0, r.length() - 2);
		}
	}
	
	@Override
	public String toString() {
		if (outputFile != null) {
			try (FileWriter fw = new FileWriter(outputFile)) {
				fw.append(getString());
			} catch (IOException e) {
				System.err.println("Problem writing BED counts to file " + outputFile.getName() + "\n" + e);
			}
			return getStats() + "Output written to " + Util.nonNullify(outputFile).getAbsolutePath();
		} else {
			return getStats() + getString();
		}
	}
		
	public void setOutputFile(File f) {
		outputFile = f;
	}
	
	public void setNormalizedOutput(boolean normalized) {
		normalizedOutput = normalized;
	}
	
	public void setAnalyzerName(String analyzerName) {
		this.analyzerName = analyzerName;
	}

	@Override
	public double totalSum() {
		return counter.sum();
	}
	
	public Counter<GenomeInterval> getCounter() {
		return counter;
	}
	
	public BedReader getBedFeatures() {
		return bedFeatures;
	}
	
	@Override
	public @NonNull Map<Object, @NonNull Object> getCounts() {
		throw new RuntimeException("Not implemented");
	}
	
	@Override
	public void turnOff() {
		on = false;
	}

	@Override
	public void turnOn() {
		on = true;
	}
	
	@Override
	public boolean isOn() {
		return on;
	}

	@Override
	public int compareTo(Object o) {
		return Double.compare(totalSum(), ((ICounterSeqLoc) o).totalSum());
	}
	
	@Override
	public boolean equals(Object o) {
		throw new RuntimeException("Unimplemented");
	}
	
	@Override
	public int hashCode() {
		throw new RuntimeException("Unimplemented");
	}
}
