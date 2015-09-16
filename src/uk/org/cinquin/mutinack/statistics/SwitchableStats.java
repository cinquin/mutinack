package uk.org.cinquin.mutinack.statistics;

import java.io.PrintStream;

public interface SwitchableStats {

	void turnOff();

	void turnOn();
	
	boolean isOn();
	
	default void logValueIfOn(PrintStream stream, String prefix) {
		if (isOn()) {
			stream.println(prefix + this.toString());
		}
	}

}