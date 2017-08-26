/**
 * Mutinack mutation detection program.
 * Copyright (C) 2017 Olivier Cinquin
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

package uk.org.cinquin.mutinack.candidate_sequences;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import contrib.net.sf.samtools.SAMRecord;
import contrib.net.sf.samtools.SamPairUtil.PairOrientation;
import uk.org.cinquin.mutinack.AnalysisStats;
import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.SubAnalyzer;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.misc_util.exceptions.IllegalInputException;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

public class SAMTranslocationTagParser {

	public static class ParseXT {
		public final @NonNull SequenceLocation otherLocation;

		public ParseXT(@NonNull String referenceGenomeName, String string, Map<String, Integer> indexContigNameReverseMap,
				ExtendedSAMRecord originalRecord) {
			final String info;
			try {
				info = string.split(",")[3];
			} catch (Exception e) {
				throw new ParseRTException("Could not retrieve fourth comma-separated item from " + string, e);
			}
			final String from, to;
			try {
				@NonNull String[] split = info.split("\\.");
				from = split[0];
				to = split[2];
			} catch (Exception e) {
				throw new ParseRTException("Could not retrieve first or third dot-separated item from " + info, e);
			}
			if (!to.startsWith("-") && !to.startsWith("+")) {
				throw new ParseRTException(to + " should start with + or -");
			}
			SequenceLocation loc1 = parseGSNAPLocation(referenceGenomeName, from, indexContigNameReverseMap);
			SequenceLocation loc2 = parseGSNAPLocation(referenceGenomeName, to, indexContigNameReverseMap);

			final SequenceLocation joinTo;

			if (!loc2.contigName.equals(originalRecord.record.getReferenceName())) {
				joinTo = loc2;
			} else if (!loc1.contigName.equals(originalRecord.record.getReferenceName())) {
				joinTo = loc1;
			} else if (Math.abs(originalRecord.getAlignmentEnd() - loc1.position) < 2 ||
					Math.abs(originalRecord.getAlignmentStart() - loc1.position) < 2) {
				joinTo = loc2;
			} else {
				joinTo = loc1;
			}

			otherLocation = joinTo;
		}
	}

	public static boolean hasRearrangementAttribute(SAMRecord r) {
		return r.getAttribute("XT") != null ||
			r.getAttribute("SA") != null;
	}

	public static @Nullable CandidateRearrangement parse(
			ExtendedSAMRecord extendedRec,
			Parameters param,
			AnalysisStats stats,
			@NonNull SequenceLocation roughLocation,
			Map<String, Integer> indexContigNameReverseMap,
			@NonNull SubAnalyzer subAnalyzer) {

		final SAMRecord rec = extendedRec.record;
		String tagSA = (String) extendedRec.record.getAttribute("SA");//Supplementary alignment
		String tagXT = (String) extendedRec.record.getAttribute("XT");//Set by GSNAP

		if (tagSA != null && tagXT != null) {
			throw new IllegalInputException("Both SA and XT tags are set in read " + extendedRec);
		}

		if (tagSA == null && tagXT == null) {
			return null;
		}

		final CigarSplitAnalysis cigarAnalysis = new CigarSplitAnalysis(extendedRec);
		final Boolean leftMatch = cigarAnalysis.leftMatchNoRevcomp;

		if (leftMatch == null) {
			return null;
		}

		@NonNull SequenceLocation joinTo;

		if (tagXT != null) {//GSNAP
			ParseXT parsed = new ParseXT(param.referenceGenomeShortName, tagXT, indexContigNameReverseMap, extendedRec);
			joinTo = parsed.otherLocation;
		} else if (tagSA != null) {//BWA
			final String[] alignments = tagSA.split(";");
			if (alignments.length != 1) {
				stats.nNotSingleSupplementary.increment(roughLocation);
				return null;
			}
			final String a = alignments[0];
			final @NonNull String @NonNull[] aSplit = a.split(",");
			final int otherAlignmentStart = Integer.parseInt(aSplit[1]) - 1;
			joinTo = new SequenceLocation(param.referenceGenomeShortName, aSplit[0], indexContigNameReverseMap, otherAlignmentStart, true);
		} else {
			throw new AssertionFailedException();
		}

		ExtendedSAMRecord other0 = subAnalyzer.analyzer.getRead(
			extendedRec.record.getReadName(),
			extendedRec.record.getFirstOfPairFlag(),
			joinTo,
			joinTo.contigIndex == extendedRec.getLocation().contigIndex ? extendedRec.getAlignmentStart() : -1,
			500,
			param.filterOpticalDuplicates);
		if (other0 == null) {//Could happen e.g. if same read aligns in opposite directions at the same position
			return null;
		}
		final @NonNull ExtendedSAMRecord other = other0;
		final int otherMapQ = other.getMappingQuality();
		final String otherCigar = other.getCigar().toString();

		//Read should have a large clipped region on one end; figure out which

		final @NonNull SequenceLocation joinFrom = new SequenceLocation(param.referenceGenomeShortName,
			extendedRec.getReferenceName(), indexContigNameReverseMap, cigarAnalysis.breakpointPosition - 1, true);

		final CigarSplitAnalysis otherCigarAnalysis = new CigarSplitAnalysis(other);
		final boolean twoAlignmentsMatchUp = otherCigarAnalysis.leftMatchNoRevcomp != null &&
			(cigarAnalysis.leftMatchNoRevcomp ^ otherCigarAnalysis.leftMatchNoRevcomp) &&
			Math.abs(cigarAnalysis.localBreakpointPosition - otherCigarAnalysis.localBreakpointPosition) < 15;

		if (joinTo.position == extendedRec.getAlignmentStart() || joinTo.position == other.getAlignmentStart()) {
			joinTo = new SequenceLocation(joinTo.referenceGenome, joinTo.contigIndex, joinTo.contigName, joinTo.position - 1,
				joinTo.plusHalf);
		}

		if (!(twoAlignmentsMatchUp && otherMapQ >= param.minMappingQualityQ1)) {
			return null;
		}

		if (rec.getMateUnmappedFlag()) {
			stats.nChimericReadsUnmappedMate.increment(joinFrom);
			return null;
		}

		final boolean sameStrands = !(extendedRec.getReadNegativeStrandFlag() ^ other.getReadNegativeStrandFlag());
		final RearrangementType putativeKind;
		final int sizeCoveredByRearrangement;
		if (joinFrom.contigIndex != joinTo.contigIndex) {
			putativeKind = RearrangementType.INTERCHROMOSOMAL;
			sizeCoveredByRearrangement = -1;
		} else {
			Util.SimpleAlignmentInfo primary = new Util.SimpleAlignmentInfo(rec);
			Util.SimpleAlignmentInfo sup = new Util.SimpleAlignmentInfo(other.record);
			PairOrientation primSupOrientation = Util.getPairOrientation(primary, sup);
			switch(primSupOrientation) {
				case FR:
					putativeKind = RearrangementType.INVERSION;
					break;
				case RF:
					putativeKind = RearrangementType.INVERSION;
					break;
				case TANDEM:
					final boolean orderSwitched;
					if (extendedRec.getReadNegativeStrandFlag()) {
						orderSwitched = extendedRec.getAlignmentStart() > joinTo.position;
					} else {
						orderSwitched = extendedRec.getAlignmentStart() < joinTo.position;
					}
					putativeKind = (orderSwitched ^ leftMatch) ?
							RearrangementType.TANDEM_REPEAT
						:
							RearrangementType.DELETION;
					break;
				default:
					throw new AssertionFailedException();
			}
			sizeCoveredByRearrangement = sameStrands ?
						extendedRec.getAlignmentStart() - joinTo.position
					:
						extendedRec.getAlignmentStart() - joinTo.position ; //TODO Replace with alignment ends
			stats.inferredRearrangementDistanceLogt100Histogram.insert((int) (100 * Math.round(Math.log(sizeCoveredByRearrangement + 1))));
		}

		CandidateRearrangement candidate = new CandidateRearrangement(subAnalyzer,
			joinFrom, extendedRec, Integer.MAX_VALUE, joinTo, putativeKind, sizeCoveredByRearrangement,
			!sameStrands, Math.min(rec.getMappingQuality(),  otherMapQ), extendedRec.record.getCigarString(), otherCigar);
		candidate.acceptLigSiteDistance(-9999);
		return candidate;
	}

	private static @NonNull SequenceLocation parseGSNAPLocation(@NonNull String referenceGenomeName, String loc,
			Map<String, Integer> indexContigNameReverseMap) {
		try {
			@NonNull String [] split = loc.split("@");
			String contigName = split[0].substring(1);
			int alignmentStart = Integer.parseInt(split[1]) - 1;
			return new SequenceLocation(referenceGenomeName, contigName, indexContigNameReverseMap, alignmentStart, true);
		} catch (Exception e) {
			throw new ParseRTException("Could not parse " + loc + " as contigName@position", e);
		}
	}

}
