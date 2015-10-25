/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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

final class AlignmentExtremetiesDistance {

	private static final byte @NonNull[] emptyBarcode = new byte[0];

	private DuplexRead dr;
	private final @NonNull DuplexRead temp = new DuplexRead(emptyBarcode, emptyBarcode);
		
	public void set(ExtendedSAMRecord r, DuplexRead d) {	
		if (r.getReadPositiveStrand()) {
			temp.setPositions(
					r.getUnclippedStart(),
					r.getMateUnclippedEnd());
		} else {
			temp.setPositions(
					r.getMateUnclippedStart(),
					r.getUnclippedEnd());			
		}		
		dr = d;
	}

	public int getMaxDistance() {
		return temp.distanceTo(dr);
	}
}