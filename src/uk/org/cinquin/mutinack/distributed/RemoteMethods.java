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

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteMethods extends Remote {
	//For workers
	Job getMoreWork(String workerID) throws RemoteException, InterruptedException;
	void submitWork(String workerID, Job job) throws RemoteException;
	void declineJob(String workerID, Job job) throws RemoteException;
	void notifyStillAlive(String workerID, Job job) throws RemoteException;
	void releaseWorkers(String workerIDBase) throws RemoteException;

	//For clients
	EvaluationResult submitJob(String clientID, Job job) throws RemoteException, InterruptedException;

	boolean shouldTerminate(String workerID) throws RemoteException;

	String getServerUUID() throws RemoteException;

}
