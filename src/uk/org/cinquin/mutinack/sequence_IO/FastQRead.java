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
package uk.org.cinquin.mutinack.sequence_IO;

import java.io.Serializable;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.misc_util.Util;

public final class FastQRead implements Serializable {
	public final byte @NonNull[] bases;
	public byte @Nullable[] qualities;

	private static final long serialVersionUID = 2266195648270695631L;

	public FastQRead (byte @NonNull[] bases){
		this.bases = bases;
	}

	public FastQRead(byte @NonNull[] bases, byte @Nullable[] qualities) {
		this.bases = Util.getInternedVB(bases);
		this.qualities = qualities;
	}
}
