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
package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import javax.jdo.annotations.PersistenceCapable;

import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.set.sorted.SortedSetIterable;
import org.eclipse.collections.impl.factory.Bags;
import org.eclipse.jdt.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.DuplexDisagreement;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.ComparablePair;
import uk.org.cinquin.mutinack.misc_util.collections.MapOfLists;

@PersistenceCapable
public final class LocationExaminationResults implements Serializable {
	private static final long serialVersionUID = -2966237959317593137L;

	@JsonIgnore //Already listed in LocationAnalysis
	public transient SortedSetIterable<CandidateSequence> analyzedCandidateSequences;
	public int nGoodOrDubiousDuplexes = 0;
	public Boolean tooHighCoverage;
	public int nGoodOrDubiousDuplexesSisterSamples = 0;
	public int nGoodDuplexesIgnoringDisag = 0;
	public int nGoodDuplexes = 0;
	public int strandCoverageImbalance;
	public int nMissingStrands;
	public final transient @NonNull
		MapOfLists<@NonNull DuplexDisagreement, @NonNull Duplex>
		disagreements = new MapOfLists<>();//Transient because DuplexRead is not serializable
	public int disagQ2Coverage = 0;
	public int disagOneStrandedCoverage = 0;
	@JsonIgnore
	public final transient @NonNull MutableBag<@NonNull ComparablePair<String, String>>
		rawMismatchesQ2,
		rawDeletionsQ2,
		rawInsertionsQ2;
	@JsonIgnore
	public final transient @NonNull Collection<@NonNull ComparablePair<String, String>>
		intraStrandSubstitutions,
		intraStrandDeletions,
		intraStrandInsertions;

	public int intraStrandNReads = 0;

	public int duplexInsertSize10thP = -1;
	public int duplexInsertSize90thP = -1;
	public double probAtLeastOneCollision = -1;

	//To assert that an instance is not concurrently modified by
	//multiple threads
	@JsonIgnore
	public final transient AtomicInteger threadCount = new AtomicInteger();

	MutableBag<@NonNull ComparablePair<String, String>> EMPTY_BAG =
		Bags.mutable.empty();

	public LocationExaminationResults(Parameters param) {
		if (param.computeRawMismatches) {
			rawMismatchesQ2 = Bags.mutable.empty();
			rawDeletionsQ2 = Bags.mutable.empty();
			rawInsertionsQ2 = Bags.mutable.empty();
		} else {
			rawMismatchesQ2 = EMPTY_BAG;
			rawDeletionsQ2 = EMPTY_BAG;
			rawInsertionsQ2 = EMPTY_BAG;
		}

		if (param.computeIntraStrandMismatches) {
			intraStrandSubstitutions = new ArrayList<>();
			intraStrandDeletions = new ArrayList<>();
			intraStrandInsertions = new ArrayList<>();
		} else {
			intraStrandSubstitutions = Collections.emptyList();
			intraStrandDeletions = Collections.emptyList();
			intraStrandInsertions = Collections.emptyList();
		}
	}

	public static Optional<Float> getTopAlleleFrequency(SortedSetIterable<CandidateSequence> candidates) {
		return candidates.getFirstOptional().map(CandidateSequence::getFrequencyAtPosition);
	}

	public Optional<Float> getTopAlleleFrequency() {
		return Optional.ofNullable(analyzedCandidateSequences).flatMap(LocationExaminationResults::getTopAlleleFrequency);
	}

	public FloatPair getTopTwoAlleleFreqFloat() {
		float [] freq = new float[2];
		iterateTopTwoCandidates((opt, index) -> freq[index] = opt.map(c ->
			c.getFrequencyAtPosition()).orElse(Float.NaN));
		return new FloatPair(freq[0], freq[1]);
	}

	public IntPair getTopTwoAlleleFreq() {
		int [] freq = new int[2];
		iterateTopTwoCandidates((opt, index) -> freq[index] = opt.map(c ->
			(int) (10f * nanTo99(c.getFrequencyAtPosition()))).orElse(99));
		return new IntPair(freq[0], freq[1]);
	}

	private void iterateTopTwoCandidates(BiConsumer<Optional<CandidateSequence>, Integer> consumer) {
		Iterator<CandidateSequence> it = analyzedCandidateSequences.iterator();
		for (int index = 0; index < 2; index++) {
			if (it.hasNext()) {
				consumer.accept(Optional.of(it.next()), index);
			} else {
				consumer.accept(Optional.empty(), index);
			}
		}
	}

	public @NonNull List<Integer> getTopTwoAlleleFreqList() {
		IntPair p = getTopTwoAlleleFreq();
		return Arrays.asList(p.i1, p.i2);
	}

	private static float nanTo99(float f) {
		return Float.isNaN(f) ? 9.9f : f;
	}

	public static class IntPair {
		public final int i1, i2;
		public IntPair(int i1, int i2) {
			this.i1 = i1;
			this.i2 = i2;
		}
	}

	public static class FloatPair {
		public final float f1, f2;
		public FloatPair(float f1, float f2) {
			this.f1 = f1;
			this.f2 = f2;
		}
	}
}
