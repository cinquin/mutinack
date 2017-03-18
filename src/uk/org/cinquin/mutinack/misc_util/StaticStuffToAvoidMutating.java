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

package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.picard.reference.ReferenceSequence;
import contrib.net.sf.picard.reference.ReferenceSequenceFile;
import contrib.net.sf.picard.reference.ReferenceSequenceFileFactory;
import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.parfor.ParFor;

public class StaticStuffToAvoidMutating {

	static final Logger logger = LoggerFactory.getLogger(StaticStuffToAvoidMutating.class);

	private static final Map<String, ReferenceSequence> contigSequences =
		new ConcurrentHashMap<>();
	private static ReferenceSequenceFile refFile;

	private static ExecutorService executorService;

	private static final int HARD_MAX_THREADS_PER_POOL = 1500;

	public static void instantiateThreadPools(int nMaxThreads) {
		if (getExecutorService() != null) {
			return;
		}

		if (nMaxThreads > HARD_MAX_THREADS_PER_POOL) {
			logger.warn("Capping number of threads from " + nMaxThreads +
				" to " + HARD_MAX_THREADS_PER_POOL);
			nMaxThreads = HARD_MAX_THREADS_PER_POOL;
		}

		setExecutorService(new ThreadPoolExecutor(0, nMaxThreads,
                300, TimeUnit.SECONDS, new SynchronousQueue<>(),
                new NamedPoolThreadFactory("Mutinack executor pool - "),
                new ThreadPoolExecutor.AbortPolicy()));

		if (ParFor.defaultThreadPool == null) {
			ParFor.defaultThreadPool = new ThreadPoolExecutor(0, nMaxThreads,
					300, TimeUnit.SECONDS, new SynchronousQueue<>(),
					new NamedPoolThreadFactory("Mutinack ParFor pool - "),
					new ThreadPoolExecutor.AbortPolicy());
		}
	}

	public static final String hostName;
	static {
		String name;
		try {
			name = Util.convertStreamToString(
					Runtime.getRuntime().exec("hostname").getInputStream());
		} catch (Throwable t) {
			name = "Could not retrieve hostname";
			t.printStackTrace();
		}
		hostName = name;
	}

	public static ExecutorService getExecutorService() {
		return executorService;
	}

	private static void setExecutorService(ExecutorService executorService) {
		StaticStuffToAvoidMutating.executorService = executorService;
	}

	public static void loadContigs(String referenceGenome, List<@NonNull String> contigNames) {
		if (refFile == null) {
			try {
				refFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(
					new File(referenceGenome));
			} catch (Exception e) {
				throw new RuntimeException("Problem reading reference file " + referenceGenome, e);
			}
		}

		for (String contigName: contigNames) {
			contigSequences.computeIfAbsent(contigName, name -> {
				try {
					ReferenceSequence ref = refFile.getSequence(contigName);
					if (ref.getBases()[0] == 0) {
						throw new RuntimeException("Found null byte in " + contigName +
								"; contig might not exist in reference file");
					}
					return ref;
				} catch (Exception e) {
					throw new RuntimeException("Problem reading reference file " + referenceGenome, e);
				}
			});
		}
	}

	public static ReferenceSequence getContigSequence(String name) {
		return contigSequences.get(name);
	}

	public static @NonNull Map<@NonNull String, @NonNull Integer> loadContigsFromFile(
			String referenceGenome) {
		return Objects.requireNonNull(FileCache.getCached(referenceGenome, ".info", path -> {
			@NonNull Map<@NonNull String, @NonNull Integer> contigSizes0 = new HashMap<>();
			try(Stream<String> lines = Files.lines(Paths.get(referenceGenome))) {
				Handle<String> currentName = new Handle<>();
				SettableInteger currentLength = new SettableInteger(0);
				lines.forEachOrdered(l -> {
					if (l.startsWith(">")) {
						String curName = currentName.get();
						if (curName != null) {
							contigSizes0.put(curName, currentLength.get());
							currentLength.set(0);
						}
						int endName = l.indexOf(' ');
						if (endName == -1) {
							endName = l.length();
						}
						currentName.set(l.substring(1, endName));
					} else {
						int lineLength = 0;
						int n = l.length();
						for (int i = 0; i < n; i++) {
							char c = l.charAt(i);
							if (c != ' ' && c != '\t') {
								lineLength++;
							}
						}
						currentLength.addAndGet(lineLength);
					}
				});
				String curName = currentName.get();
				contigSizes0.put(Objects.requireNonNull(curName), currentLength.get());
				return Collections.unmodifiableMap(contigSizes0);
			} catch (IOException e) {
				throw new RuntimeException("Problem reading size of contigs from reference file " + path, e);
			}
		}));
	}
}
