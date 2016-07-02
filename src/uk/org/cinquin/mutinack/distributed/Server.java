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

import java.awt.event.ActionListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.Timer;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.nustaq.serialization.FSTConfiguration;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;
import uk.org.cinquin.mutinack.misc_util.exceptions.ParseRTException;

public class Server extends UnicastRemoteObject implements RemoteMethods {

	private static final long serialVersionUID = 7331182254489507945L;
	private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();

	public static int PING_INTERVAL_SECONDS = 20;

	private final Map<Job, Job> jobs = new ConcurrentHashMap<>();
	private final String recordRunsTo;
	private final Map<String, Parameters> recordedRuns;

	@SuppressWarnings("unused")
	private static Registry registry;

	private static final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	private void dumpRecordedRuns() {
		if (recordedRuns == null) {
			return;
		}
		try (FileOutputStream os = new FileOutputStream(recordRunsTo)) {
			try (FileLock lock = os.getChannel().tryLock()) {
				if (lock != null) {
					os.write(conf.asByteArray(recordedRuns));
				}
			}
		} catch (OverlappingFileLockException e) {
			throw new AssertionFailedException("Concurrent write attempt when dumping recorded runs");
		} catch (IOException e) {
			//An IOException can be thrown if locking is not supported by
			//the OS / filesystem. In that case, try again without lock
			if ("Operation not supported".equals(e.getMessage())) {
				try (FileOutputStream os = new FileOutputStream(recordRunsTo)) {
					os.write(conf.asByteArray(recordedRuns));
				} catch (IOException e1) {
					throw new RuntimeException("Could not save cached data to "
						+ recordRunsTo, e1);
				}
			} else {
				throw new RuntimeException("Could not save cached data to "
					+ recordRunsTo, e);
			}
		}
		System.err.println("Dumped " + recordedRuns.size() + " runs to file " + recordRunsTo);
	}

	public static void createRegistry(String suggestedHostName0) {
		String suggestedHostName = suggestedHostName0 == null ?
				""
			:
				suggestedHostName0.split("/")[0];

		String hostName = "".equals(suggestedHostName) ?
				getDefaultHostName()
			:
				suggestedHostName;
		System.setProperty("java.rmi.server.hostname", hostName);

		if (System.getSecurityManager() == null) {
			//SecurityManager manager = new SecurityManager();
			//System.setSecurityManager(new SecurityManager());
			//System.out.println("Security manager installed.");
		} else {
			System.err.println("Security manager already exists.");
		}

		try {
			registry = LocateRegistry.createRegistry(1099);
			System.err.println("Java RMI registry created; hostname is " + hostName);
		} catch (RemoteException e) {
			// do nothing, error means registry already exists
			System.err.println("Java RMI registry already exists");
		}

	}

	public static String getDefaultHostName() {
		String result;
		try {
			result = InetAddress.getLocalHost().getCanonicalHostName();
			System.err.println("Choosing default host name " + result);
		} catch (UnknownHostException e) {
			System.err.println("Unable to determine localhost address; reverting to localhost");
			result = "localhost";
		}
		return result;
	}

	private Timer rebindTimer;

	public Server(int port, @Nullable String fullPath0, String recordRunsTo)
			throws RemoteException {
		super(port);
		final String fullPath = fillInDefaultRMIPath(fullPath0);
		this.recordRunsTo = recordRunsTo;
		if (recordRunsTo != null) {
			recordedRuns = new HashMap<>();
		} else {
			recordedRuns = null;
		}
		final AtomicBoolean firstRun = new AtomicBoolean(true);

		ActionListener rebindRunnable = event -> {
			if (!rebindTimer.isRunning())
				return;
			try {
				if (firstRun.get()) {
					Naming.rebind(fullPath, Server.this);
					System.err.println("Server " + fullPath + " bound in registry");
				}
			} catch (Exception e) {
				System.err.println("RMI server rebinding exception:" + e);
				e.printStackTrace(System.err);
			}
			firstRun.set(false);
		};

		rebindTimer = new Timer(300 * 1000, rebindRunnable); //Run every 5min
		rebindTimer.setInitialDelay(0);
		rebindTimer.start();

		Signals.registerSignalProcessor("INFO", s -> dumpRecordedRuns());
		Thread shutdownHook = new Thread(this::dumpRecordedRuns);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	@Override
	public Job getMoreWork(String workerID) throws RemoteException, InterruptedException {
		Job job = queue.poll(Integer.MAX_VALUE, TimeUnit.DAYS);
		job.workerID = workerID;
		job.timeLastWorkerPing = System.currentTimeMillis();
		job.timeGivenToWorker = System.nanoTime();
		return job;
	}

	@Override
	public void submitWork(String workerID, Job job) throws RemoteException {
		//System.err.println("Job result " + job.result.output);
		Job localJobObj = jobs.get(job);
		if (localJobObj == null) {
			throw new IllegalStateException("Unknown job " + job + " from client " + workerID);
		}
		localJobObj.result = job.result;
		localJobObj.completed = true;
		synchronized(localJobObj) {
			localJobObj.notifyAll();
		}
	}

	@Override
	public EvaluationResult submitJob(String clientID, Job job) throws RemoteException, InterruptedException {
		job.timeSubmitted = System.nanoTime();
		if (job.completed) {
			throw new IllegalArgumentException("Job " + job + " from client " + clientID +
				" already marked as completed");
		}
		boolean cancelled = false;
		do {
			job.cancelled = false;
			jobs.put(job, job);
			queue.put(job);

			if (recordedRuns != null) {
				recordedRuns.put(job.parameters.runName, job.parameters);
				System.err.println("Recorded job " + job.parameters.runName);
			}

			synchronized(job) {
				while (!job.completed) {
					job.wait(PING_INTERVAL_SECONDS * 1_000L);
					if (!job.completed && job.timeGivenToWorker > 0 &&
							(System.currentTimeMillis() - job.timeLastWorkerPing > 3 * PING_INTERVAL_SECONDS * 1_000)) {
						throw new RuntimeException("Worker " + job.workerID + " unresponsive while " +
							"processing " + job);
					}
				}
			}
			jobs.remove(job);
			cancelled = job.cancelled;
		} while (cancelled);

		job.timeReturnedToSubmitter = System.nanoTime();
		//System.err.println("RETURNING " + job.result.output);
		return job.result;
	}

	@Override
	public void declineJob(String workerID, Job job) throws RemoteException {
		Job localJobObj = jobs.get(job);
		if (localJobObj == null) {
			throw new IllegalStateException("Unknown job " + job + " from client " + workerID);
		}
		localJobObj.cancelled = true;
		localJobObj.completed = true;
		synchronized(localJobObj) {
			localJobObj.notifyAll();
		}
	}

	@Override
	public boolean shouldTerminate(String workerID) throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getServerUUID() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public static @NonNull String fillInDefaultRMIPath(@Nullable String hostNameOrFullPath) {
		if (hostNameOrFullPath == null || hostNameOrFullPath.equals("")) {
			return getDefaultHostName() + "/mutinack";
		}
		String[] split = hostNameOrFullPath.split("/");
		if (split.length > 1) {
			if (split.length != 2) {
				throw new ParseRTException("Incorrect formatting of " + hostNameOrFullPath);
			}
			return hostNameOrFullPath;
		} else {
			return hostNameOrFullPath + "/mutinack";
		}
	}

	public static RemoteMethods getServer(String fullPath)
			throws MalformedURLException, RemoteException {
		//if (System.getSecurityManager() == null) {
		//	System.setSecurityManager(new SecurityManager());
		//}
		RemoteMethods server;
		try {
			server = (RemoteMethods) Naming.lookup(fillInDefaultRMIPath(fullPath));
		} catch (NotBoundException e) {
			throw new RuntimeException(e);
		}
		return server;
	}

	@Override
	public void notifyStillAlive(String workerID, Job job) throws RemoteException {
		Job localJobObj = jobs.get(job);
		if (localJobObj == null) {
			System.err.println("Ping for unknown job " + job + " from worker " + workerID);
			return;
		}
		localJobObj.timeLastWorkerPing = System.currentTimeMillis();
	}

}
