package uk.org.cinquin.mutinack;

import java.io.Closeable;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.Assert;
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

	private static final int VARIABLE_BARCODE_START = 0;
	private int VARIABLE_BARCODE_END = Integer.MAX_VALUE;
	private static final int CONSTANT_BARCODE_START = 3;
	private static final int CONSTANT_BARCODE_END = 5;
	private byte[] Ns;
	
	public final transient Collection<BiConsumer<PrintStream, Integer>>
		statusUpdateTasks = new ArrayList<>();
	private Map<Integer, @NonNull String> indexContigNameMap;
	public final Map<String, @NonNull Integer> indexContigNameReverseMap = new ConcurrentHashMap<>();
	public final Map<SequenceLocation, Boolean> forceOutputAtLocations = new HashMap<>();
	public int PROCESSING_CHUNK;

	public int INTERVAL_SLOP;
	public int BIN_SIZE = 10_000;
	
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
		Objects.requireNonNull(Ns);
		return Ns;
	}

	private transient Thread hook = new Thread(() -> {
			Util.printUserMustSeeMessage("Writing output files and terminating");
			terminateAnalysis = true;
		}
	);

	public boolean terminateImmediatelyUponError = true;

	public static String forceKeeperType = null;

	public MutinackGroup() {
	}
	
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

	public Map<Integer, @NonNull String> getIndexContigNameMap() {
		Assert.isNonNull(indexContigNameMap, "Uninitialized indexContigNameMap");
		return indexContigNameMap;
	}

	public void setIndexContigNameMap(Map<Integer, @NonNull String> indexContigNameMap) {
		this.indexContigNameMap = indexContigNameMap;
	}

	public void errorOccurred() {
		if (terminateImmediatelyUponError) {
			terminateAnalysis = true;
		}
	}
}
