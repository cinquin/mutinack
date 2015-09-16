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
package uk.org.cinquin.mutinack.statistics;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.concurrent.atomic.LongAdder;


public final class LongAdderFormatter extends LongAdder implements Serializable {

	private static final long serialVersionUID = -4695889470689380766L;

	@Override
	public String toString(){
		return NumberFormat.getInstance().format(sum());
	}
}
