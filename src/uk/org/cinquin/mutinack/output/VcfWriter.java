package uk.org.cinquin.mutinack.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

public class VcfWriter {

	public static void writeNested(String path, Collection<List<CandidateSequence>> candidates)
			throws IOException {
		try (FileWriter fileWriter = new FileWriter(path)) {
			fileWriter.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
			candidates.stream().flatMap(l -> l.stream())
			.collect(Collectors.groupingBy(CandidateSequence::getLocation, TreeMap::new, Collectors.toList()))
			.forEach((loc, list) ->
				writeLocation(list, fileWriter));
		}
	}

	private static void writeLocation(List<CandidateSequence> list, OutputStreamWriter writer) {
		//TODO All the variants belonging to one position should be written together on a single line
		//For now, treat them separately
		list.stream().collect(Collectors.groupingBy(CandidateSequence::getMutation)).forEach((mut, candList) -> {
			CandidateSequence randomCandidate = candList.get(0);
			try {
				writeCandidate(randomCandidate, writer);
				writer.append("   ");
				writer.append(
					candList.stream().map(CandidateSequence::getSampleName).sorted().collect(Collectors.joining("___")));
				writer.append('\n');
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static void write(String path, Collection<CandidateSequence> candidates)
			throws IOException {
		try (FileWriter fileWriter = new FileWriter(path)) {
			fileWriter.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n");
			candidates.forEach(c -> {
				try {
					writeCandidate(c, fileWriter);
					fileWriter.append('\n');
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
	}

	public static String toString(byte b) {
		return new String(new byte[] {b});
	}

	public static void writeCandidate(CandidateSequence c, OutputStreamWriter writer)
			throws IOException {
		writer.write((c.getLocation().contigName.replace("chr", "")) + "\t"
				+ (c.getLocation().position + 1) + "\t" + "." + "\t");
		writer.write(new String(c.getFullWildtypeSequence()) + "\t");
		switch(c.getMutationType()) {
			case WILDTYPE:
				writer.write(toString(c.getWildtypeSequence()));
				break;
			case DELETION:
				writer.write(toString(c.getPrecedingWildtypeBase()));
				break;
			case SUBSTITUTION:
				writer.write(toString(Objects.requireNonNull(c.getSequence())[0]));
				break;
			case INSERTION:
				writer.write(toString(c.getWildtypeSequence()) + new String(c.getSequence()));
				break;
			default:
				throw new IllegalArgumentException("Unhandled mutation type for " + c);
		}
		writer.write("\t100\tPASS\t" + c);
	}
}
