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

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.rmi.RemoteException;

import com.healthmarketscience.rmiio.RemoteOutputStreamServer;
import com.healthmarketscience.rmiio.SimpleRemoteOutputStream;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.misc_util.Signals;
import uk.org.cinquin.mutinack.misc_util.Signals.SignalProcessor;

public class Submitter {

	public static void submitToServer(Parameters param)
			throws RemoteException, InterruptedException, MalformedURLException {

		RemoteMethods server = Server.getServer(param.submitToServer);
		Job job = new Job();
		param.submitToServer = null;
		if (param.workingDirectory != null) {
			synchronized(Runtime.getRuntime()) {
				String saveUserDir = System.getProperty("user.dir");
				System.setProperty("user.dir", param.workingDirectory);
				param.canonifyFilePaths();
				System.setProperty("user.dir", saveUserDir);
			}
		} else {
			param.canonifyFilePaths();
		}
		job.parameters = param;
		ByteArrayOutputStream bostdout = new ByteArrayOutputStream();
		@SuppressWarnings("resource")
		RemoteOutputStreamServer osstdout = new SimpleRemoteOutputStream(bostdout);
		ByteArrayOutputStream bosstderr = new ByteArrayOutputStream();
		@SuppressWarnings("resource")
		RemoteOutputStreamServer osstderr = new SimpleRemoteOutputStream(bosstderr);
		job.stdoutStream = osstdout.export();
		job.stderrStream = osstderr.export();
		Runnable r = () -> {
			try {
				boolean done = false;
				while (!done) {
					try {
						Thread.sleep(60_000);
					} catch (InterruptedException e) {
						done = true;
					}
					synchronized(bostdout) {
						System.out.print(new String(bostdout.toByteArray()));
						bostdout.reset();
					}
					synchronized(bosstderr) {
						if (!param.suppressStderrOutput) {
							System.err.print(new String(bosstderr.toByteArray()));
						}
						bosstderr.reset();
					}
				}
			} finally {
				osstdout.close();
				osstderr.close();
			}
		};
		Thread t = new Thread(r);
		t.setName("Stream output thread");

		final EvaluationResult result;

		SignalProcessor infoSignalHandler = signal -> System.err.println("Submitted job " + job + " to server " +
			param.submitToServer);

		try {
			Signals.registerSignalProcessor("INFO", infoSignalHandler);
			t.start();
			result = server.submitJob(ManagementFactory.getRuntimeMXBean().getName(),
				job);
		} finally {
			t.interrupt();
			Signals.removeSignalProcessor("INFO", infoSignalHandler);
		}

		if (result.executionThrowable != null) {
			throw new RuntimeException(result.executionThrowable);
		} else {
			t.join();
		}
	}

}
