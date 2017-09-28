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

package uk.org.cinquin.mutinack;

import java.io.Closeable;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.misc_util.Util;

/**
 * Class to store settings that are common to a set of analyzers running
 * together. Some of the fields are final and static, but that can easily
 * be changed in the future if need be.
 * @author olivier
 *
 */
public class MutinackGroup implements Closeable, Serializable {
	private static final long serialVersionUID = 4569342905242962301L;

	static final Logger logger = LoggerFactory.getLogger(MutinackGroup.class);

	/**
	 * Terminate analysis but finish writing output BAM file.
	 */
	public volatile transient boolean terminateAnalysis = false;
	public volatile transient Throwable errorCause = null;

	private static final int VARIABLE_BARCODE_START = 0;
	private int VARIABLE_BARCODE_END = Integer.MAX_VALUE;
	private static final int CONSTANT_BARCODE_START = 3;
	private static final int CONSTANT_BARCODE_END = 5;
	private byte[] Ns;

	private final transient SortedMap<String, BiConsumer<PrintStream, Integer>>
		statusUpdateTasks = new TreeMap<>();
	private List<@NonNull String> contigNames, contigNamesToProcess;
	private Map<@NonNull String, @NonNull Integer> contigSizes;
	private final @NonNull Map<String, @NonNull Integer> indexContigNameReverseMap = new ConcurrentHashMap<>();
	public final @NonNull Map<@NonNull SequenceLocation, @NonNull Boolean> forceOutputAtLocations = new HashMap<>();
	public final @NonNull ConcurrentMap<Pair<SequenceLocation, String>,
		@NonNull List<@NonNull Pair<@NonNull Mutation, @NonNull String>>>
		mutationsToAnnotate = new ConcurrentHashMap<>(5_000);
	public int PROCESSING_CHUNK;

	public int INTERVAL_SLOP;
	public int BIN_SIZE = 10_000;

	public MutinackGroup(boolean rnaSeq) {
		this.rnaSeq = rnaSeq;
	}

	private static void checkSet(String var, int previousVal, int newVal) {
		if (previousVal == Integer.MAX_VALUE) {
			return;
		}
		Assert.isTrue(previousVal == newVal,
			"Trying to set %s to %s but it has already been set to %s", var, newVal, previousVal);
	}

	public synchronized void setBarcodePositions(int variableStart, int variableEnd,
			int constantStart, int constantEnd) {
		Assert.isTrue(variableStart == 0, "Unimplemented");
		Assert.isTrue(constantStart == 3, "Unimplemented");
		Assert.isTrue(constantEnd == 5, "Unimplemented");
		checkSet("variable barcode end", getVariableBarcodeEnd(), variableEnd);
		if (getVariableBarcodeEnd() == Integer.MAX_VALUE) {
			setVariableBarcodeEnd(variableEnd);
			logger.info("Set variable barcode end position to " + getVariableBarcodeEnd());
			//VARIABLE_BARCODE_START == 0;
			final byte[] localNs = new byte [getVariableBarcodeEnd() - getVariableBarcodeStart() + 1];
			for (int i = 0; i < getVariableBarcodeEnd() - getVariableBarcodeStart() + 1; i++) {
				localNs[i] = 'N';
			}
			Assert.isNonNull(localNs);
			Ns = localNs;
		}
	}

	public byte @NonNull[] getNs() {
		return Objects.requireNonNull(Ns);
	}

	private transient Thread hook = new Thread(() -> {
			Util.printUserMustSeeMessage("Writing output files and terminating");
			terminateAnalysis = true;
		}
	);

	public boolean terminateImmediatelyUponError = true;

	private final boolean rnaSeq;

	public static String forceKeeperType = null;

	public void registerInterruptSignalProcessor() {
		Runtime.getRuntime().addShutdownHook(hook);
	}

	@Override
	public void close() {
		Runtime.getRuntime().removeShutdownHook(hook);
	}

	public static int getVariableBarcodeStart() {
		return VARIABLE_BARCODE_START;
	}

	public static int getConstantBarcodeStart() {
		return CONSTANT_BARCODE_START;
	}

	public static int getConstantBarcodeEnd() {
		return CONSTANT_BARCODE_END;
	}

	public int getVariableBarcodeEnd() {
		return VARIABLE_BARCODE_END;
	}

	private void setVariableBarcodeEnd(int variableBarcodeEnd) {
		this.VARIABLE_BARCODE_END = variableBarcodeEnd;
	}

	public @NonNull List<@NonNull String> getContigNames() {
		Assert.isNonNull(contigNames, "Uninitialized contigNames");
		return Objects.requireNonNull(contigNames);
	}

	public @NonNull List<@NonNull String> getContigNamesToProcess() {
		Assert.isNonNull(contigNamesToProcess, "Uninitialized contigNamesToProcess");
		return Objects.requireNonNull(contigNamesToProcess);
	}

	public @NonNull Map<@NonNull String, @NonNull Integer> getContigSizes() {
		Assert.isNonNull(contigNames, "Uninitialized contigSizes");
		return Objects.requireNonNull(contigSizes);
	}

	public void setContigNames(@NonNull List<@NonNull String> contigNames) {
		this.contigNames = contigNames;
	}

	public void setContigNamesToProcess(@NonNull List<@NonNull String> contigNamesToProcess) {
		this.contigNamesToProcess = contigNamesToProcess;
	}

	public void setContigSizes(@NonNull Map<@NonNull String, @NonNull Integer> contigSizes) {
		this.contigSizes = contigSizes;
	}

	public void errorOccurred(Throwable t) {
		if (errorCause == null) {//Inconsequential race condition
			errorCause = t;
			if (terminateImmediatelyUponError) {
				terminateAnalysis = true;
			}
		}
	}

	public boolean isRnaSeq() {
		return rnaSeq;
	}

	public void addStatusUpdateTask(String name, BiConsumer<PrintStream, Integer> task) {
		synchronized(statusUpdateTasks) {
			if (statusUpdateTasks.put(name, task) != null) {
				throw new IllegalArgumentException("Duplicate status update task " + name);
			}
		}
	}

	public boolean removeStatusUpdateTask(String name) {
		synchronized(statusUpdateTasks) {
			return statusUpdateTasks.remove(name) != null;
		}
	}


	public void statusUpdate() {
		synchronized(statusUpdateTasks) {
			statusUpdateTasks.forEach((name, task) -> task.accept(System.err, 0));
		}
	}

	public Map<String, @NonNull Integer> getIndexContigNameReverseMap() {
		return indexContigNameReverseMap;
	}
}
