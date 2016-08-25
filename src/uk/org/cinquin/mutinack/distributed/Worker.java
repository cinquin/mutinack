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

package uk.org.cinquin.mutinack.distributed;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

import com.healthmarketscience.rmiio.RemoteOutputStreamClient;

import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.MultipleExceptionGatherer;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Signals.SignalProcessor;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;
import uk.org.cinquin.parfor.ILoopWorker;
import uk.org.cinquin.parfor.ParFor;

public class Worker {

	public static void runWorker(Parameters argValues) throws InterruptedException {
		final int nWorkers;
		final String cleanedUpName;
		final int columnIndex = argValues.startWorker.indexOf(":");
		if (columnIndex < 0) {
			nWorkers = 1;
			cleanedUpName = argValues.startWorker;
		} else {
			try {
				nWorkers =
					Integer.parseInt(argValues.startWorker.substring(columnIndex + 1));
				cleanedUpName = argValues.startWorker.substring(0, columnIndex);
			} catch (NumberFormatException e) {
				throw new ParseRTException("Problem parsing " + argValues.startWorker, e);
			}
		}
		final ParFor pf;
		final Handle<Boolean> terminate = new Handle<>(false);
		final Handle<Boolean> terminateImmediately = new Handle<>(false);
		KeySetView<Job, Boolean> pendingJobs = ConcurrentHashMap.newKeySet();
		final Handle<Long> timeLastTermSignal = new Handle<>(0L);

		final String workerIDBase = ManagementFactory.getRuntimeMXBean().getName() + "_";

		final RemoteMethods server;
		try {
			server = Server.getServer(cleanedUpName);
		} catch (MalformedURLException | RemoteException e2) {
			throw new RuntimeException(e2);
		}

		final SignalProcessor termSignalProcessor = s -> {
			final boolean alreadyTerminating = terminate.get();
			if (alreadyTerminating) {
				if (System.currentTimeMillis() - timeLastTermSignal.get() < 2_000) {
					terminateImmediately.set(true);
					for (Job j: pendingJobs) {
						if (j.parameters.group != null) {
							j.parameters.group.terminateAnalysis = true;
						}
					}
					System.err.println("Will terminate as soon as results are written out");
				} else if (!terminateImmediately.get()){
					System.err.println("Worker will terminate when current " + pendingJobs.size() +
						" jobs have completed; one more in next 2 seconds to force immediate termination");
				}
				timeLastTermSignal.set(System.currentTimeMillis());
				return;
			}
			timeLastTermSignal.set(System.currentTimeMillis());
			terminate.set(true);

			//There does not seem to be a clean way of canceling
			//getMoreWork call blocked in socketRead, so get the
			//server to interrupt its corresponding waiting thread
			//and return null
			try {
				server.releaseWorkers(workerIDBase);
			} catch (RemoteException e1) {
				throw new RuntimeException(e1);
			}

			Thread terminateThread = new Thread(() -> {
				synchronized(pendingJobs) {
					while (!pendingJobs.isEmpty()) {//Small race conditions
						System.err.println("Worker will terminate when current " + pendingJobs.size() +
							" jobs have completed; one more in next 2 seconds to force immediate termination");
						try {
							pendingJobs.wait();
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
				}
			}, "Terminate thread");
			terminateThread.start();
		};
		Signals.registerSignalProcessor("TERM", termSignalProcessor);

		pf = new ParFor("Main worker loop", 0, nWorkers - 1, null, true);
		@SuppressWarnings("resource")
		ILoopWorker worker = (loopIndex, threadIndex) -> {
			final String workerID = workerIDBase + loopIndex;
			Job job;
			while (!terminate.get()) {
				job = null;
				SignalProcessor infoSignalHandlerWaiting = signal -> {
					System.err.println("Worker " + workerID + " waiting for job from " +
						cleanedUpName);
				};
				try {
					Signals.registerSignalProcessor("INFO", infoSignalHandlerWaiting);
					job = server.getMoreWork(workerID);
					if (job == null) {
						terminate.set(true);
						break;
					}
				} catch (RemoteException e2) {
					throw new RuntimeException(e2);
				} finally {
					Signals.removeSignalProcessor("INFO", infoSignalHandlerWaiting);
				}
				if (!pendingJobs.add(job)) {
					throw new AssertionFailedException("Job " + job + "already pending");
				}
				Job job1 = job;
				try {
					if (terminate.get()) {
						try {
							pendingJobs.remove(job);
							server.declineJob(workerID, job);
						} catch (RemoteException e) {
							throw new RuntimeException(e);
						}
						break;
					}
					Thread pingThread = new Thread(() -> {
						try {
							while(true) {
								Thread.sleep(Server.PING_INTERVAL_SECONDS * 1_000L);
								try {
									server.notifyStillAlive(workerID, job1);
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
							}
						} catch (InterruptedException e) {
							//Nothing to do; just exit and die
						}
					});
					pingThread.setDaemon(true);
					pingThread.setName("Ping thread of worker");
					pingThread.start();
					job.result = new EvaluationResult();
					PrintStream outPS, errPS;
					try {
						outPS = new PrintStream(
							RemoteOutputStreamClient.wrap(job.stdoutStream));
						errPS = new PrintStream(
							RemoteOutputStreamClient.wrap(job.stderrStream));
					} catch (IOException e1) {
						throw new RuntimeException(e1);
					}
					RuntimeException die = null;

					final Job finalJob = job;
					SignalProcessor infoSignalHandlerWorking = signal -> {
						System.err.println("Worker " + workerID + " working on " +
							finalJob + " for server " + cleanedUpName);
					};
					Signals.registerSignalProcessor("INFO", infoSignalHandlerWorking);

					try {
						int parameterHashCode = job.parameters.hashCode();
						Mutinack.realMain1(job.parameters, outPS, errPS);
						if (parameterHashCode != job.parameters.hashCode()) {
							die = new AssertionFailedException("Parameters modified by worker");
							//Send the result back to the server so that one could figure out
							//from the server what happened, and then stop the worker
							throw die;
						}
					} catch (Throwable t) {
						if (!Util.isSerializable(t)) {
							t = new RuntimeException("Unserializable exception " +
								t.toString());
						}
						job.result.executionThrowable = t;
					}
					try {
						try {
							outPS.close();
							errPS.close();
						} finally {
							server.submitWork(workerID, job);
						}
					} catch (Throwable t) {
						MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
						gatherer.add(job.result.executionThrowable);
						gatherer.add(t);
						gatherer.add(die);
						Util.printUserMustSeeMessage(gatherer.toString());
						gatherer.throwIfPresent();
					} finally {
						Signals.removeSignalProcessor("INFO", infoSignalHandlerWorking);
					}
					pingThread.interrupt();
					if (die != null) {
						throw die;
					}
				} finally {
					pendingJobs.remove(job);
					synchronized(pendingJobs) {
						pendingJobs.notifyAll();
					}
				}
			}//Worker never leaves this loop unless interrupted or killed
			return null;
		};
		try {
			for (int i = 0; i < nWorkers; i++) {
				pf.addLoopWorker(worker);
			}
			pf.run(true);
		} finally {
			Signals.removeSignalProcessor("TERM", termSignalProcessor);
		}
	}

}
