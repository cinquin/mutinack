package uk.org.cinquin.mutinack.tests;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.FileCache;
import uk.org.cinquin.mutinack.misc_util.exceptions.FunctionalTestFailed;

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
			@SuppressWarnings("unchecked")
			Map<String, Parameters> testArgs = (Map<String, Parameters>) conf.asObject(bytes);
			testArguments = testArgs;
		} catch (IOException e) {
			logger.error("Problem reading data from " + new File(pathToRecordedTests).getAbsolutePath(), e);
		}
	}
	
	private final static List<String> listDuplexKeepTypes = Arrays.asList(new String [] 
			{"DuplexHashMapKeeper", "DuplexITKeeper", "DuplexArrayListKeeper", null});
	
	private final static List<String> dontForceDuplexKeepTypes = Arrays.asList(new String []
			{null});
	
	@org.junit.runners.Parameterized.Parameters(name = "{0}-{1}-{2}")
    public static Iterable<Object[]> data() {
    	List<String> param2List = false ? dontForceDuplexKeepTypes : listDuplexKeepTypes ;
    	List<Object[]> result = testArguments.keySet().stream().flatMap(s -> param2List.
    			stream().map(duplex -> new Object [] {s, duplex, true})).
        		collect(Collectors.toList());
    	if (result.size() > 0) {
    		result.get(0)[2] = false;
    	}
    	return result;
    }

	@Parameter(0)
	public String testName;
	
	@Parameter(1)
	public String duplexKeeperType;
	
	@Parameter(2)
	public boolean suppressAlignmentOutput;
	
	Parameters param;
	
	@Test
	public void test() throws InterruptedException, IOException {
		if (param == null) {
			param = testArguments.get(testName);
			//TODO Switch to throwing TestRunFailure
			Assert.isNonNull(param, "Could not find parameters for test %s within %s",
						testName, 
						testArguments.keySet().stream().collect(Collectors.joining("\t")));
			Assert.isNonNull(param.referenceOutput,
					"No reference output specified for test GenericTest");
			Assert.isTrue(new File(param.referenceOutput).exists(),
					"File %s does not exist", new File(param.referenceOutput).getAbsolutePath());
			param.terminateImmediatelyUponError = false;
			if (suppressAlignmentOutput) {
				param.outputAlignmentFile = null;
			}
		}
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream outPS = new PrintStream(outStream);
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream errPS = new PrintStream(errStream);
		
		SubAnalyzer.forceKeeperType = duplexKeeperType;
		Mutinack.realMain1(param, outPS, errPS);
		SubAnalyzer.forceKeeperType = null;
		
		final String out = outStream.toString();
		
		try(Stream<String> lines = Files.lines(Paths.get(param.referenceOutput))) {
			lines.forEach((final String line) -> {
				String[] split = line.split("\t");
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
						split = line.split("\\s+");
						subset = split[0];
					}
					final StringBuilder suppMessage = new StringBuilder();
					try (final BufferedReader reader = new BufferedReader(
							new StringReader(out))) {
							String outLine;
							while((outLine = reader.readLine()) != null) {
								if (outLine.indexOf(subset) > -1) {
									if (suppMessage.length() == 0) {
										suppMessage.append("Did find\n");
									}
									suppMessage.append(outLine).append("\n");
								}
							}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
					throw new FunctionalTestFailed("Could not find " + line + 
							"\n" + suppMessage);
				}
			});
		}
	}
}
