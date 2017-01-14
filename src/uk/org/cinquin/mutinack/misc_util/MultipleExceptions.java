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

package uk.org.cinquin.mutinack.misc_util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class MultipleExceptions extends RuntimeException {
	
	private static final long serialVersionUID = -3843826759182832179L;
	private final List<Throwable> causeList;

	public MultipleExceptions(List<Throwable> causeList) {
		super();
		this.causeList = causeList;
	}
	
	@Override
	public String getMessage() {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream outPS = new PrintStream(outStream);
		outPS.print(causeList.size() + " exceptions received:");
		for (Throwable t: causeList) {
			outPS.print(' ' + t.getMessage());
		}
		outPS.println();
		int index = 0;
		for (Throwable t: causeList) {
			index++;
			outPS.println("Exception " + index + ": " + t.getMessage());
			t.printStackTrace(outPS);
			outPS.println("--------------");
		}

		return outStream.toString();
	}
}
