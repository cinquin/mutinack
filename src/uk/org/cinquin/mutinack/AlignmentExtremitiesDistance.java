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

import org.eclipse.jdt.annotation.NonNull;

final class AlignmentExtremitiesDistance {

	private static final byte @NonNull[] EMPTY_BARCODE = new byte[0];

	private Duplex dr;
	public final @NonNull Duplex temp;

	public AlignmentExtremitiesDistance(MutinackGroup groupSettings, Parameters param) {
		temp = new Duplex(groupSettings, EMPTY_BARCODE, EMPTY_BARCODE, false, false);
	}

	public void set(ExtendedSAMRecord r) {
		if (r.duplexLeft()) {
			temp.leftAlignmentStart = r.getOffsetUnclippedStartLoc();
			temp.rightAlignmentEnd = r.getMateOffsetUnclippedEndLoc();
		} else {
			temp.leftAlignmentStart = r.getMateOffsetUnclippedStartLoc();
			temp.rightAlignmentEnd = r.getOffsetUnclippedEndLoc();
		}
	}

	public void set(Duplex d) {
		dr = d;
	}

	public int getMaxDistance() {
		return temp.distanceTo(dr);
	}
}
