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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.nustaq.serialization.FSTConfiguration;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.MutinackGroup;
import uk.org.cinquin.mutinack.Parameters;
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
	
	private static final Logger logger = LoggerFactory.getLogger(FileCache.class);

	public static Map<String, Parameters> testArguments;
	private static final String pathToRecordedTests = "recordedFunctionalTestRuns.bin";
	private static final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

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
	
	private static final List<String> listDuplexKeepTypes = Arrays.asList("DuplexHashMapKeeper",
			/*"DuplexITKeeper",*/ "DuplexArrayListKeeper", null);
	
	private static final List<String> dontForceDuplexKeepTypes = Arrays.asList(new String []
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
	
	private static final int nColumnsToCheck = 14;
	
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
		
		Path referenceOutputPath = Paths.get(param.referenceOutput);
		String auxOutputFileBaseName = Files.createTempDirectory("functional_test_").toString();
		param.auxOutputFileBaseName = auxOutputFileBaseName + "/";

		try {
			try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
					ByteArrayOutputStream errStream = new ByteArrayOutputStream()) {
				PrintStream outPS = new PrintStream(outStream);
				PrintStream errPS = new PrintStream(errStream);

				//Only one test should run at a time, so it's OK to use a static variable
				try {
					MutinackGroup.forceKeeperType = duplexKeeperType;
					Mutinack.realMain1(param, outPS, errPS);
				} finally {
					MutinackGroup.forceKeeperType = null;
				}

				final String out = outStream.toString();
				try(Stream<String> referenceOutputLines = Files.lines(referenceOutputPath)) {
					checkOutput(out, referenceOutputLines);
				}
			}

			File baseOutputDir = referenceOutputPath.getParent().toFile();

			for (String expectedOutputBaseName: baseOutputDir.list((file, name) -> name.startsWith("expected_")
					&& !name.equals("expected_run.out") && !name.equals("expected_output.txt"))) {
				File actualOutputFile = new File(auxOutputFileBaseName + "/" +
					expectedOutputBaseName.replace("expected_", ""));
				if (!actualOutputFile.exists()) {
					throw new FunctionalTestFailed("Output file " +
							actualOutputFile.getAbsolutePath() +
							" was not created");
				}
				//Files.lines needs to be explicitly closed, or else the
				//underlying file does not get closed (apparently even
				//after the stream gets read to the end)
				try(Stream<String> linesToLookFor = Files.lines(Paths.get(baseOutputDir.getAbsolutePath() +
						"/" + expectedOutputBaseName))) {
					checkOutput(FileUtils.readFileToString(actualOutputFile),
						linesToLookFor);
				}
			}
		} finally {
			FileUtils.deleteDirectory(new File(auxOutputFileBaseName));
		}
	}
	
	private static void checkOutput(String output, Stream<String> linesToLookFor) {
		linesToLookFor.forEach((final String line) -> {
			String[] split = line.split("\t");
			StringBuilder headSB = new StringBuilder();
			for (int i = 0; i < Math.min(nColumnsToCheck, split.length); i++) {
				if (i > 0) {
					headSB.append('\t');
				}
				headSB.append(split[i]);
			}
			final String head = headSB.toString();
			if (output.indexOf(head) < 0) {
				final String subset;
				if (split.length > 1 && !split[1].equals("")) {
					subset = split[1];
				} else {
					split = line.split("\\s+");
					subset = split[0];
				}
				final StringBuilder suppMessage = new StringBuilder("\n");
				try (final BufferedReader reader = new BufferedReader(
						new StringReader(output))) {
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
						suppMessage);
			}
		});
	}
}
