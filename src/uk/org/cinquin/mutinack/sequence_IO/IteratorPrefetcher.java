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
package uk.org.cinquin.mutinack.sequence_IO;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**

 * @author olivier
 *
 * @param <T>
 */
public class IteratorPrefetcher<T> implements Iterator<T>, Closeable {

	private final int minQueueSize;

	private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
	private final AtomicInteger queueSize = new AtomicInteger();

	private volatile boolean iteratorExhausted = false;
	private final Object fetch = new Object();

	private final Object semaphore = new Object();

	private final Object THE_END = new Object();
	private volatile RuntimeException exception = null;
	
	final @NonNull Thread fetchingThread;
	
	public IteratorPrefetcher(final Iterator<T> it, final int nReadAhead, 
			final @Nullable Closeable closeWhenDone,
			final Consumer<T> preProcessor) {
		minQueueSize = nReadAhead;
		Runnable r = new Runnable() {
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				try {
					while (!iteratorExhausted) {
						while (queueSize.get() < minQueueSize * 2) {
							if (Thread.interrupted()) {
								throw new InterruptedException();
							}
							T t;
							if (!it.hasNext()) {
								((BlockingQueue<Object>) queue).add(THE_END);
								iteratorExhausted = true;
								break;
							}
							t = it.next();
							preProcessor.accept(t);
							queue.add(t);
							queueSize.incrementAndGet();
							synchronized(semaphore) {
								semaphore.notifyAll();
							}
						}
						synchronized(fetch) {
							while (queueSize.get() >= minQueueSize) {
								fetch.wait();
							}
						}
					}
					synchronized(semaphore) {
						semaphore.notifyAll();
					}
				} catch (InterruptedException e) {
					//Do nothing; would be caused e.g. by closing of iterator
					//before it has been exhausted
				} catch (Throwable t) {
					if (exception != null) {
						exception = new RuntimeException("Problem in iterator prefetcher", t);
					}
				} finally {
					if (closeWhenDone != null) {
						try {
							closeWhenDone.close();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}
		};
		fetchingThread = new Thread(r, "Iterator prefetch");
		fetchingThread.start();
	}

	@Override
	public boolean hasNext() {
		if (exception != null) {
			throw exception;
		}
		if (!queue.isEmpty()) {
			return queue.peek() != THE_END;
		}

		synchronized(semaphore) {
			while (queue.isEmpty()) {
				try {
					semaphore.wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return (queue.peek() != THE_END);
	}

	@Override
	public T next() {
		if (exception != null) {
			throw exception;
		}
		T result;
		try {
			result = queue.take();
			if (result == THE_END) {
				throw new IllegalStateException();
			}
		} catch (InterruptedException e1) {
			throw new RuntimeException();
		}
		if (queueSize.decrementAndGet() < minQueueSize) {
			synchronized(fetch) {
				fetch.notifyAll();
			}	
		}
		return result;
	}

	@Override
	public void close() throws IOException {
		fetchingThread.interrupt();
	}

}
