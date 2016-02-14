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

package uk.org.cinquin.mutinack.misc_util;

import contrib.uk.org.lidalia.slf4jext.Level;
import contrib.uk.org.lidalia.slf4jext.Logger;

public class DebugLogControl {

	//It turns out that, for many sample types, having both switches
	//below on does not result in a substantial performance cost.
	//So keep them on by default.
	
	public static final boolean NONTRIVIAL_ASSERTIONS = true;
	
	/**
	 * Used to optimize out TRACE-level log statements at compile time.
	 */
	public static final boolean ENABLE_TRACE = true;

	public static boolean COSTLY_ASSERTIONS = true;

	public static final boolean shouldLog(Level level, Logger logger) {
		return logger.isEnabled(level);
	}

}
