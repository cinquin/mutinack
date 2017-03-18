package uk.org.cinquin.mutinack.misc_util.collections;

import gnu.trove.strategy.HashingStrategy;
import uk.org.cinquin.mutinack.DuplexRead;

public class HashingStrategies {

	public static final HashingStrategy<DuplexRead> identityHashingStrategy = 
		new HashingStrategy<DuplexRead>() {
		private static final long serialVersionUID = -5196382307004964827L;

		@Override
		public int computeHashCode(DuplexRead arg0) {
			return System.identityHashCode(arg0);
		}

		@Override
		@SuppressWarnings("ReferenceEquality")
		public boolean equals(DuplexRead arg0, DuplexRead arg1) {
			return arg0 == arg1;
		}
	};
}
