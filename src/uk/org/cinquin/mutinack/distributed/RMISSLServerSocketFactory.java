package uk.org.cinquin.mutinack.distributed;

/*
 *
 * Copyright (c) 1994, 2004, Oracle and/or its affiliates. All rights reserved.
 *
 * Minor cleanup and addition of SSL client authentication, Olivier Cinquin (2016).
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistribution of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * Redistribution in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Oracle nor the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT
 * OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR
 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class RMISSLServerSocketFactory implements RMIServerSocketFactory {

	/*
	 * Create one SSLServerSocketFactory, so we can reuse sessions
	 * created by previous sessions of this SSLContext.
	 */
	private SSLServerSocketFactory ssf = null;

	public RMISSLServerSocketFactory(String keysFile) {
		final SSLContext ctx;
		final KeyManagerFactory kmf;
		final KeyStore ks;

		try {
			char[] passphrase = "passphrase".toCharArray();
			ks = KeyStore.getInstance("JKS");
			try (InputStream keys = new FileInputStream(keysFile)) {
				ks.load(keys, passphrase);
			}

			kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, passphrase);

			ctx = SSLContext.getInstance("TLS");
			ctx.init(kmf.getKeyManagers(), null, null);
			ssf = ctx.getServerSocketFactory();
		} catch (IOException | NoSuchAlgorithmException | KeyStoreException  |
				UnrecoverableKeyException | KeyManagementException | CertificateException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		ServerSocket result = ssf.createServerSocket(port);
		((SSLServerSocket) result).setNeedClientAuth(true);
		return result;
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		return true;
	}
}
