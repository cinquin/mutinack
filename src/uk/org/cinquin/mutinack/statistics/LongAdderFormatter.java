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
import java.util.concurrent.atomic.LongAdder;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.statistics.json.LongAdderFormatterSerializer;

@JsonSerialize(using=LongAdderFormatterSerializer.class)
public final class LongAdderFormatter extends LongAdder implements Serializable,
	Actualizable {

	private static final long serialVersionUID = -4695889470689380766L;

	@Override
	public String toString() {
		return DoubleAdderFormatter.nf.get().format(sum());
	}

	public String sum;
	
	@Override
	public void actualize() {
		sum = toString();
	}

}
