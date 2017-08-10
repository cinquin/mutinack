package uk.org.cinquin.mutinack.statistics.collectors;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import uk.org.cinquin.mutinack.statistics.Histogram;

public class HistogramSumCollector implements Collector<Histogram, Histogram, Histogram> {

	@Override
	public Supplier<Histogram> supplier() {
		return () -> new Histogram(Integer.MAX_VALUE, 0);
	}

	private BiConsumer<Histogram, Histogram> addToLeft = (Histogram accumulator, Histogram value) -> {
		for(int i = value.size() - 1; i >= 0; i--) {
			accumulator.insert(i, value.get(i).sum());
		}
	};

	@Override
	public BiConsumer<Histogram, Histogram> accumulator() {
		return addToLeft;
	}

	@Override
	public BinaryOperator<Histogram> combiner() {
		return (a, b) -> {
			addToLeft.accept(a, b);
			return a;
		};
	}

	@Override
	public Function<Histogram, Histogram> finisher() {
		return a -> a;
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.unmodifiableSet(
			EnumSet.of(Characteristics.CONCURRENT, Characteristics.IDENTITY_FINISH));
	}
}
