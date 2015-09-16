/*******************************************************************************
 * Parismi v0.1
 * Copyright (c) 2009-2013 Cinquin Lab.
 * All rights reserved. This code is made available under a dual license:
 * the two-clause BSD license or the GNU Public License v2.
 ******************************************************************************/
package uk.org.cinquin.parfor;

public interface ILoopWorker {
	Object run(int loopIndex, int threadIndex) throws InterruptedException;
}
