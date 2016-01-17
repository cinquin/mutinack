package uk.org.cinquin.mutinack;

import java.io.Closeable;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import sun.misc.Signal;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.misc_util.Signals;
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
	
	final static Logger logger = LoggerFactory.getLogger(MutinackGroup.class);

	/**
	 * Terminate analysis but finish writing output BAM file.
	 */
	public volatile boolean terminateAnalysis = false;

	private int UNCLIPPED_BARCODE_LENGTH = Integer.MAX_VALUE;
	private static final int VARIABLE_BARCODE_START = 0;
	private int VARIABLE_BARCODE_END = Integer.MAX_VALUE;
	private static final int CONSTANT_BARCODE_START = 3;
	private static final int CONSTANT_BARCODE_END = 5;
	private byte[] Ns;
	
	public final Collection<BiConsumer<PrintStream, Integer>> statusUpdateTasks = new ArrayList<>();
	private Map<Integer, @NonNull String> indexContigNameMap;
	public final Map<String, @NonNull Integer> indexContigNameReverseMap = new ConcurrentHashMap<>();
	public final Set<SequenceLocation> forceOutputAtLocations = new HashSet<>();
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
			int constantStart, int constantEnd, int unclippedBarcodeLength) {
		Assert.isTrue(variableStart == 0, "Unimplemented");
		Assert.isTrue(constantStart == 3, "Unimplemented");
		Assert.isTrue(constantEnd == 5, "Unimplemented");
		checkSet("variable barcode end", getVariableBarcodeEnd(), variableEnd);
		checkSet("unclipped barcode length", getUnclippedBarcodeLength(), unclippedBarcodeLength);
		if (getVariableBarcodeEnd() == Integer.MAX_VALUE) {
			setVariableBarcodeEnd(variableEnd);
			setUnclippedBarcodeLength(unclippedBarcodeLength);
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

	Signals.SignalProcessor sigINTHandler = new Signals.SignalProcessor() {
		private static final long serialVersionUID = -1666210584038132608L;

		@Override
		public void handle(Signal signal) {
			Util.printUserMustSeeMessage("Writing output files and terminating");
			terminateAnalysis = true;
		}
	};

	public MutinackGroup() {
	}
	
	public void registerInterruptSignalProcessor() {
		Signals.registerSignalProcessor("URG", sigINTHandler);
	}

	@Override
	public void close() {
		Signals.removeSignalProcessor("URG", sigINTHandler);
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

	public int getUnclippedBarcodeLength() {
		return UNCLIPPED_BARCODE_LENGTH;
	}

	private void setUnclippedBarcodeLength(int unclippedBarcodeLength) {
		this.UNCLIPPED_BARCODE_LENGTH = unclippedBarcodeLength;
	}

	public Map<Integer, @NonNull String> getIndexContigNameMap() {
		Assert.isNonNull(indexContigNameMap, "Uninitialized indexContigNameMap");
		return indexContigNameMap;
	}

	public void setIndexContigNameMap(Map<Integer, @NonNull String> indexContigNameMap) {
		this.indexContigNameMap = indexContigNameMap;
	}
}
