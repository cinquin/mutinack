package uk.org.cinquin.mutinack.distributed;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.rmi.Naming;
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

import org.nustaq.serialization.FSTConfiguration;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public class Server extends UnicastRemoteObject implements RemoteMethods {
	
	private static final long serialVersionUID = 7331182254489507945L;
	private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();

	private final Map<Job, Job> jobs = new ConcurrentHashMap<>();
	private final String recordRunsTo;
	private final Map<String, Parameters> recordedRuns;
	
	private static Registry registry;
	
	private static final FSTConfiguration conf = FSTConfiguration.createFastBinaryConfiguration();

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
			throw new RuntimeException("Could not save cached data to "
					+ recordRunsTo, e);
		}
	}
	
	public static void createRegistry(String suggestedHostName) {
		String hostName = getHostName(suggestedHostName);
		System.setProperty("java.rmi.server.hostname", hostName);

		if (System.getSecurityManager() == null) {
			//SecurityManager manager = new SecurityManager();
			//System.setSecurityManager(new SecurityManager());
			//System.out.println("Security manager installed.");
		} else {
			System.out.println("Security manager already exists.");
		}

		try {
			registry = LocateRegistry.createRegistry(1099);
			System.out.println("Java RMI registry created; hostname is " + hostName);
		} catch (RemoteException e) {
			// do nothing, error means registry already exists
			System.out.println("Java RMI registry already exists");
		}

	}
	
	public static String getHostName(String suggestedName) {
		String result;
		if ("".equals(suggestedName)) {
			try {
				result = InetAddress.getLocalHost().getCanonicalHostName();
				System.out.println("Choosing default host name " + result);
			} catch (UnknownHostException e) {
				System.out.println("Unable to determine localhost address; reverting to localhost");
				result = "localhost";
			}
		} else {
			result = suggestedName;
		}
		return result;
	}

	private Timer rebindTimer;

	public Server(int port, String hostName0, String recordRunsTo) throws RemoteException {
		super(port);
		this.recordRunsTo = recordRunsTo;
		if (recordRunsTo != null) {
			recordedRuns = new HashMap<>();
		} else {
			recordedRuns = null;
		}
		final AtomicBoolean firstRun = new AtomicBoolean(true);
		
		String hostName1 = getHostName(hostName0);

		ActionListener rebindRunnable = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent event) {

				synchronized (rebindTimer) {
					if (!rebindTimer.isRunning())
						return;
					try {
						if (firstRun.get()) {
							Naming.rebind("//" + hostName1 + "/mutinack", Server.this);
							System.out.println("Server " + "//" + hostName1 + "/mutinack"
									+ " bound in registry");
						}
					} catch (Exception e) {
						System.err.println("RMI server rebinding exception:" + e);
						e.printStackTrace(System.out);
					}
					firstRun.set(false);
				}
			}
		};
		
		rebindTimer = new Timer(300 * 1000, rebindRunnable); //Run every 5min
		rebindTimer.setInitialDelay(0);
		rebindTimer.start();
		
		Signals.registerSignalProcessor("INFO", s -> dumpRecordedRuns());
	}

	@Override
	public Job getMoreWork(String workerID) throws RemoteException, InterruptedException {
		Job job = queue.poll(Integer.MAX_VALUE, TimeUnit.DAYS);
		job.workerID = workerID;
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
		jobs.put(job, job);
		queue.put(job);
		
		if (recordedRuns != null) {
			recordedRuns.put(job.parameters.runName, job.parameters);
			System.err.println("Recorded job " + job.parameters.runName);
		}
		
		synchronized(job) {
			while (!job.completed) {
				job.wait();
			}
		}
		jobs.remove(job);
		job.timeReturnedToSubmitter = System.nanoTime();
		//System.err.println("RETURNING " + job.result.output);
		return job.result;
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

}
