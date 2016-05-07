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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class Signals {

	public interface SignalProcessor extends Serializable {
		void handle(Signal signal);
	}

	private static Map<String, List<SignalProcessor>> processors = new HashMap<>();
	
	static boolean initialized = false;

	public static void registerSignalProcessor(String name, SignalProcessor p) {
		synchronized(processors) {
			if (!initialized) {
				initialize();
				initialized = true;
			}
			List<SignalProcessor> list = processors.get(name);
			if (list == null) {
				list = new ArrayList<>();
				processors.put(name, list);
			}
			list.add(p);
		}
	}
	
	public static void clearSignalProcessors() {
		synchronized(processors) {
			processors.clear();
			initialize();
		}
	}

	public static synchronized void removeSignalProcessor(String name, SignalProcessor p) {
		synchronized(processors) {
			List<SignalProcessor> list = processors.get(name);
			if (list == null)
				throw new IllegalArgumentException();
			list.remove(p);
		}
	}

	private static class Handler {
		String signalName;
		public Handler(String signal) {
			this.signalName = signal;
		}
		
		SignalHandler sigHandler = new SignalHandler() {
			@Override
			public void handle(Signal signal) {
				List<SignalProcessor> list = processors.get(signalName);
				if (list == null) {
					return;
				}
				for (SignalProcessor p: list) {
					try {
						p.handle(signal);
					} catch (Throwable t) {
						t.printStackTrace();
					}
				}
			}
		};
	}

	private static void initialize () {
		try {
			Signal.handle(new Signal("INFO"), new Handler("INFO").sigHandler);
		} catch (IllegalArgumentException e) { 
			if (!System.getProperty("os.name", "Linux default").contains("Linux")) {
				System.err.println(e.toString());
			}
		}
	}
}
