package uk.org.cinquin.mutinack.misc_util;

import java.util.concurrent.ThreadFactory;

public class NamedPoolThreadFactory implements ThreadFactory {
	private int counter = 0;
	final String name;
	public NamedPoolThreadFactory(String name) {
		this.name = name;
	}
	
	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName(name + counter++);
		return t;
	}
	
}
