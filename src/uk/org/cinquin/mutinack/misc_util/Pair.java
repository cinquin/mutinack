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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import uk.org.cinquin.final_annotation.Final;

//Adapted from http://stackoverflow.com/questions/156275/what-is-the-equivalent-of-the-c-pairl-r-in-java
@Immutable
public class Pair<A,B> implements Serializable {

	private static final long serialVersionUID = -1873509799696495621L;
	@JsonIgnore @Final public A fst;
	@JsonUnwrapped @Final public B snd;

	protected Pair() {
		fst = null;
		snd = null;
	}

	public Pair(A first, @Nullable B second) {
		super();
		this.fst = first;
		this.snd = second;
	}

	@Override
	public final int hashCode() {
		int hashFirst = fst != null ? fst.hashCode() : 0;
		int hashSecond = snd != null ? snd.hashCode() : 0;

		return (hashFirst + hashSecond) * hashSecond + hashFirst;
	}

	@Override
	public final boolean equals(Object other) {
		if (other instanceof Pair) {
			Pair<?, ?> otherPair = (Pair<?, ?>) other;
			return
				((  this.fst == otherPair.fst ||
				( this.fst != null && otherPair.fst != null &&
				this.fst.equals(otherPair.fst))) &&
					(	this.snd == otherPair.snd ||
					( this.snd != null && otherPair.snd != null &&
					this.snd.equals(otherPair.snd))) );
		}

		return false;
	}

	@Override
	public String toString()
	{
		return "(" + fst + ", " + snd + ')';
	}

	public A getFst() {
		return fst;
	}

	public B getSnd() {
		return snd;
	}

	public A[] firstAsArray(Collection<Pair<A,B>> col, Class<?> clazz){
		@SuppressWarnings("unchecked")
		A[] result=(A[]) Array.newInstance(clazz,col.size());
		int index=0;
		for (Pair<A,B> pair: col){
			result[index]=pair.getFst();
			index++;
		}
		return result;
	}

	public B[] secondAsArray(Collection<Pair<A,B>> col, Class<?> clazz){
		@SuppressWarnings("unchecked")
		B[] result=(B[]) Array.newInstance(clazz,col.size());
		int index=0;
		for (Pair<A,B> pair: col){
			result[index]=pair.getSnd();
			index++;
		}
		return result;
	}

}
