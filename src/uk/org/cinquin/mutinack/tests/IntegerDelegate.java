package uk.org.cinquin.mutinack.tests;

import mockit.Delegate;

public class IntegerDelegate implements Delegate<Object> {
	int value;
	public int get() {
		return value;
	}
}
