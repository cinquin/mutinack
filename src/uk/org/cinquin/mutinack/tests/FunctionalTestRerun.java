package uk.org.cinquin.mutinack.tests;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.nustaq.serialization.FSTConfiguration;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.misc_util.FileCache;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

/**
 * The "recordedFunctionalTestRuns.bin" file must have been created prior to running
 * these tests. The file is created by the run_functional_tests script. The absolute
 * paths recorded in recordedFunctionalTestRuns must be valid at the time tests from
 * this class are run.
 * @author olivier
 *
 */
@RunWith(Parameterized.class)
public class FunctionalTestRerun {

	private final static Logger logger = LoggerFactory.getLogger(FileCache.class);

	public static Map<String, Parameters> testArguments;
	private static final String pathToRecordedTests = "recordedFunctionalTestRuns.bin";
	private static final FSTConfiguration conf = FSTConfiguration.createFastBinaryConfiguration();

	static {
		try (DataInputStream dataIS = new DataInputStream(new FileInputStream(pathToRecordedTests))) {
			byte [] bytes = new byte [(int) new File(pathToRecordedTests).length()];
			dataIS.readFully(bytes);
			testArguments = (Map<String, Parameters>) conf.asObject(bytes);
		} catch (IOException e) {
			logger.error("Problem reading data from " + new File(pathToRecordedTests).getAbsolutePath(), e);
		}
	}
	
	@org.junit.runners.Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
    	return testArguments.keySet().stream().map(s -> new Object [] {s}).
        		collect(Collectors.toList());
    }

	@Parameter
	public String testName;
	
	Parameters param;
	
	@Test
	public void test() throws InterruptedException, IOException {
		if (param == null) {
			param = testArguments.get(testName);
			if (param == null) {
				throw new AssertionFailedException("Could not find parameters for test " +
						testName + " within " + 
						testArguments.keySet().stream().collect(Collectors.joining("\t")));
			}
			if (param.referenceOutput == null) {
				throw new AssertionFailedException("No reference output specified for test GenericTest");
			}
			if (! new File(param.referenceOutput).exists()) {
				throw new AssertionFailedException("File " + new File(param.referenceOutput).getAbsolutePath() + " does not exist");
			}
			param.terminateImmediatelyUponError = false;
		}

		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream outPS = new PrintStream(outStream);
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream errPS = new PrintStream(errStream);
		Mutinack.realMain1(param, outPS, errPS);
		
		final String out = outStream.toString();
		
		try(Stream<String> lines = Files.lines(Paths.get(param.referenceOutput))) {
			lines.forEach(l -> {
				String[] split = l.split("\t");
				StringBuilder headSB = new StringBuilder();
				for (int i = 0; i < Math.min(8, split.length); i++) {
					if (i > 0) {
						headSB.append('\t');
					}
					headSB.append(split[i]);
				}
				final String head = headSB.toString();
				if (out.indexOf(head) < 0) {
					final String subset;
					if (split.length > 1 && !split[1].equals("")) {
						subset = split[1];
					} else {
						split = l.split("\\s+");
						subset = split[0];
					}
					final String suppMessage;
					final int i = out.indexOf(subset);
					if (i > -1) {
						int lineEnd = out.indexOf("\n", i);
						if (lineEnd == -1) {
							lineEnd = out.length() - 2;
						}
						suppMessage = "\nDid find\n" + out.substring(i, lineEnd + 1);
					} else {
						suppMessage = "\n";
					}
					throw new AssertionFailedException("Could not find " + l + suppMessage);
				}
			});
		}
	}
}
