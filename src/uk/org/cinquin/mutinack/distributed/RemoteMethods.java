package uk.org.cinquin.mutinack.distributed;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface RemoteMethods extends Remote {
	//For workers
	Job getMoreWork(String workerID) throws RemoteException, InterruptedException;
	void submitWork(String workerID, Job job) throws RemoteException;

	//For clients
	EvaluationResult submitJob(String clientID, Job job) throws RemoteException, InterruptedException;
	
	boolean shouldTerminate(String workerID) throws RemoteException;

	String getServerUUID() throws RemoteException;

}
