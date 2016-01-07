package uk.org.cinquin.mutinack.distributed;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface RemoteMethods extends Remote {
	//For workers
	public Job getMoreWork(String workerID) throws RemoteException, InterruptedException;
	public void submitWork(String workerID, Job job) throws RemoteException;

	//For clients
	public EvaluationResult submitJob(String clientID, Job job) throws RemoteException, InterruptedException;
	
	public boolean shouldTerminate(String workerID) throws RemoteException;

	public String getServerUUID() throws RemoteException;

}
