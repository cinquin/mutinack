/*******************************************************************************
 * Parismi v0.1
 * Copyright (c) 2009-2013 Cinquin Lab.
 * All rights reserved. This code is made available under a dual license:
 * the two-clause BSD license or the GNU Public License v2.
 ******************************************************************************/
package uk.org.cinquin.parfor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;
import uk.org.cinquin.mutinack.misc_util.StaticStuffToAvoidMutating;

public final class ParFor {
	
	static final Logger logger = LoggerFactory.getLogger("Parfor");
	/**
	 * Profiling suggests setting thread names represents a substantial
	 * cost when doing many short ParFor runs.
	 */
	private static final boolean SET_THREAD_NAMES = false;

	private String name = "";
	private final int startIndex, endIndex;
	private final int nIterations;
	private final boolean stopAllUponException;
	
	private ILoopWorker[] workers = null;
	private int workerArrayIndex = 0;
	private final transient AtomicInteger index = new AtomicInteger();
	private final ProgressReporter progressReporter;
	private List<?>[] partialResults;
	private Future<?>[] futures;
	private volatile boolean abort;
	private volatile Exception e;
	private volatile boolean clientWillGetExceptions;
	private volatile boolean printedMissingExceptionWarning;
	private volatile boolean started = false;
	private final Object doneSemaphore = new Object();
	private volatile boolean done = false;
	
	public static volatile ExecutorService defaultThreadPool;
	private final ExecutorService threadPool;
	@SuppressWarnings("unused")
	private static final int nProc = Runtime.getRuntime().availableProcessors();

	public interface ProgressReporter {
		int getValue();
		void setValueThreadSafe(int ourProgress);
	}
	
	public static final class PluginRuntimeException extends RuntimeException {

		private static final long serialVersionUID = 1737304285173402447L;
		public boolean unmaskable;

		public PluginRuntimeException(String message, Exception e, boolean b) {
			super(message, e);
		}
	}
	

	public ParFor(int startIndex, int endIndex, ProgressReporter progressBar,
			ExecutorService threadPool, boolean stopAllUponException) {
		this(startIndex, endIndex, progressBar, threadPool, stopAllUponException, Integer.MAX_VALUE);
	}

	private ParFor(int startIndex, int endIndex, ProgressReporter progressBar,
			ExecutorService providedThreadPool, boolean stopAllUponException, int maxNThreads) {
		if (providedThreadPool == null) {
			if (defaultThreadPool == null) {
				synchronized(ParFor.class) {
					if (ParFor.defaultThreadPool == null) {
						System.err.println("Instantiating ParFor thread pool with default number of threads");
						StaticStuffToAvoidMutating.instantiateThreadPools(128);
					}
				}
			}
			threadPool = ParFor.defaultThreadPool;
		} else {
			threadPool = providedThreadPool;
		}
		this.startIndex = startIndex;
		this.endIndex = endIndex;
		nIterations = endIndex - startIndex + 1;
		this.progressReporter = progressBar;
		this.stopAllUponException = stopAllUponException;

		int useNThreads = ((ThreadPoolExecutor) threadPool).getMaximumPoolSize();
		/*int activeThreads = ((ThreadPoolExecutor) threadPool).getActiveCount();
		if (activeThreads > nProc) {
			logger.warn("Capping number of parallel threads");
			useNThreads = 2;
		}*/
		int result = Math.min(Math.min(nIterations, useNThreads), maxNThreads);
		workers = new ILoopWorker[result];
	}

	public ParFor(String name, int startIndex, int endIndex, ProgressReporter progressBar, boolean stopAllUponException) {
		this(startIndex, endIndex, progressBar, defaultThreadPool, stopAllUponException);
		if (name != null)
			setName(name);
	}
	
	public int getNThreads() {
		return workers.length;
	}

	private void checkNotStarted() {
		if (started)
			throw new IllegalStateException("Illegal operation when ParFor already started");
	}

	public void setNThreads(int n) {
		checkNotStarted();
		workers = new ILoopWorker[n];
	}

	public void addLoopWorker(ILoopWorker task) {
		checkNotStarted();
		workers[workerArrayIndex] = task;
		workerArrayIndex++;
	}

	public List<Object> run(boolean block) throws InterruptedException {
		return run(block, -1);
	}
	
	public List<Object> runNonBlocking() {
		try {
			return run(false, -1);
		} catch (InterruptedException e1) {
			throw new IllegalStateException(e1);
		}
	}

	@SuppressWarnings({ "rawtypes" })
	public List<Object> run(boolean block, final int nToCompleteBeforeReturn) throws InterruptedException {

		try {
			checkNotStarted();
			started = false;

			clientWillGetExceptions = block || (nToCompleteBeforeReturn!=-1);
			printedMissingExceptionWarning = false;

			partialResults = new ArrayList<?> [workers.length];
			for (int i = 0; i < partialResults.length; i++) {
				partialResults[i] = new ArrayList<>();
			}
			Runnable[] runnables = new Runnable[workers.length];
			futures = new Future[workers.length];
			abort = false;
			e = null;
			final float progressMultiplyingFactor = 100.0f / nIterations;
			final Thread parentThread1 = Thread.currentThread();
			final AtomicInteger nCompleted = new AtomicInteger(0);
			index.set(startIndex);

			for (int i = 0; i < workers.length; i++) {
				final int finalI = i;
				runnables[i] = new Runnable() {

					private long lastGUIUpdate = 0;
					private int modulo = 10;

					private final int workerID = finalI;
					final ProgressReporter localProgress = progressReporter;

					@Override
					public final void run() {
						try {

							if (SET_THREAD_NAMES) {
								Thread.currentThread().setName(name + workerID);
							}
							
							final ILoopWorker localWorker = workers[workerID];
							@SuppressWarnings("unchecked")
							final List<Object> localResults = (List<Object>) partialResults[workerID];

							int sliceModulo = 0;

							for (int n = index.getAndAdd(1); n <= endIndex; n = index.getAndAdd(1)) {
								if (abort) return;

								Object result = localWorker.run(n, workerID);
								if (result != null)
									localResults.add(result);

								if (localProgress != null && sliceModulo++ == modulo) {
									long currentTime = System.currentTimeMillis();
									if (lastGUIUpdate > 0){
										long timeLag = currentTime - lastGUIUpdate;
										if (Math.abs(timeLag) > 3000) {
											modulo = Math.max((int) (modulo*0.7),1);
										} else if (Math.abs(timeLag) < 500) {
											modulo = Math.min(Integer.MAX_VALUE/2, (int) (modulo*1.3));
										}
									}
									lastGUIUpdate = currentTime;
									sliceModulo = 0;
									int ourProgress = (int) ( (n-startIndex)*progressMultiplyingFactor);
									if (ourProgress > localProgress.getValue()) 
										localProgress.setValueThreadSafe(ourProgress);
									//Not perfect but minimizes synchronization
								}
							}
						} catch (Exception e1) {
							if (!clientWillGetExceptions) {
								e1.printStackTrace();
							}

							if (stopAllUponException && !abort) {
								abort = true;
								synchronized (ParFor.this) {
									if (e == null) {
										e = e1;
										synchronized(doneSemaphore) {
											doneSemaphore.notifyAll();
										}
									} else if (!printedMissingExceptionWarning) {
										printedMissingExceptionWarning = true;
										logger.error(name + ": multiple exceptions caught in ParFor");
									}
								}
								logger.error(name + "Aborting ParFor run");
								if (clientWillGetExceptions && parentThread1 != null)
									parentThread1.interrupt();
							}
						} finally {
							if (nToCompleteBeforeReturn != -1) {
								int completed = nCompleted.incrementAndGet();
								if (completed == nToCompleteBeforeReturn) {
									synchronized(nCompleted) {
										nCompleted.notifyAll();
									}
								}
							}
							if (SET_THREAD_NAMES) {
								Thread.currentThread().setName("");
							}
						}
					}
				};
				futures[i] = threadPool.submit(runnables[i], 0);
			}

			if (!block && nToCompleteBeforeReturn == -1) {	
				return null;
			}

			if (nToCompleteBeforeReturn != -1) {
				synchronized(nCompleted) {
					while (nCompleted.get() < nToCompleteBeforeReturn && (!abort)) {
						try {
							nCompleted.wait();
						} catch (InterruptedException e1) {
							if (!abort) {
								abort = true;
								synchronized (ParFor.this) {
									if (e == null) {
										e = e1;
										synchronized(doneSemaphore) {
											doneSemaphore.notifyAll();
										}
									}
								}
								logger.error(name + "aborting ParFor run");
								for (Future future:futures) {
									future.cancel(true);
								}
							}
						}
					}
				}
			} else {
				getAllFutures();
			}
			done = true;

			if (!abort)
				clientWillGetExceptions=false;
			if (e != null) {
				if (e instanceof InterruptedException)
					throw ((InterruptedException) e);
				PluginRuntimeException repackaged = new PluginRuntimeException(e.getMessage(), e, false);
				repackaged.unmaskable = true;
				throw repackaged;
			}

			if (block) {
				List<Object> aggregatedResults = new ArrayList<>();
				for (int i = 0;i < futures.length; i++){
					aggregatedResults.addAll(partialResults[i]);
				}
				return aggregatedResults;
			} else {
				return null;
			}
		} finally {
			if (!abort)
				clientWillGetExceptions = false;
			synchronized(doneSemaphore) {
				doneSemaphore.notifyAll();
			}
		}
	}

	private void getAllFutures() {
		for (int i = 0; i < futures.length && (!abort); i++) {
			try {
				futures[i].get();
			} catch (InterruptedException | ExecutionException e1) {
				if (!abort) {
					abort = true;
					synchronized (ParFor.this) {
						if (e == null) {
							e = e1;
							synchronized(doneSemaphore) {
								doneSemaphore.notifyAll();
							}
						}
						else
							logger.error(name + ": multiple exceptions caught in ParFor; only 1 will be rethrown");
					}
					logger.error(name + "Aborting ParFor run");
				}
				for (Future<?> future:futures){
					future.cancel(true);
				}
			}
		}
	}

	public final void waitForCompletion() throws InterruptedException {
		clientWillGetExceptions = true;
		try {
			if (!done) {
				getAllFutures();
			}
		} finally {
			if (abort) {
				synchronized(doneSemaphore) {
					while (e == null) {
						doneSemaphore.wait();
					}
				}
			}
			if (e != null) {
				if (e instanceof InterruptedException)
					throw ((InterruptedException) e);
				PluginRuntimeException repackaged=new PluginRuntimeException(e.getMessage(), e, false);
				repackaged.unmaskable = true;
				throw repackaged;
			}
		}
	}

	public void interrupt() {
		for (Future<?> future:futures) {
			if (future != null) 
				future.cancel(true);
		}
	}

	public void setName(String name) {
		this.name = name + ": ";
	}

	public static void shutdownExecutor() {
		if (defaultThreadPool != null) {
			defaultThreadPool.shutdown();
		}
	}

}
