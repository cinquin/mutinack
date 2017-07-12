package uk.org.cinquin.mutinack.misc_util.collections;

import gnu.trove.strategy.HashingStrategy;
import uk.org.cinquin.mutinack.Duplex;

public class HashingStrategies {

	public static final HashingStrategy<Duplex> identityHashingStrategy =
		new HashingStrategy<Duplex>() {
		private static final long serialVersionUID = -5196382307004964827L;

		@Override
		public int computeHashCode(Duplex arg0) {
			return System.identityHashCode(arg0);
		}

		@Override
		@SuppressWarnings("ReferenceEquality")
		public boolean equals(Duplex arg0, Duplex arg1) {
			return arg0 == arg1;
		}
	};
}
