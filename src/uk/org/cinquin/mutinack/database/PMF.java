package uk.org.cinquin.mutinack.database;

import java.util.Properties;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

import uk.org.cinquin.mutinack.Parameters;

public class PMF {

	private static Properties commonProperties(boolean autoCreate) {
		final Properties properties = new Properties();
		properties.setProperty("datanucleus.ConnectionDriverName", "org.postgresql.Driver");
		if (autoCreate) {
			properties.setProperty("datanucleus.schema.autoCreateAll", "true");
		}
		properties.setProperty("datanucleus.RetainValues", "true");//Do not make objects hollow when they are made persistent
		properties.setProperty("javax.jdo.PersistenceManagerFactoryClass", "org.datanucleus.api.jdo.JDOPersistenceManagerFactory");
		return properties;
	}

	public static PersistenceManagerFactory getPMF() {
		final Properties properties = commonProperties(false);
		properties.setProperty("javax.jdo.option.ConnectionURL", "jdbc:postgresql://localhost/mutinack_test_db");
		properties.setProperty("javax.jdo.option.ConnectionDriverName", "org.postgresql.Driver");
		properties.setProperty("javax.jdo.option.ConnectionUserName", "testuser3");
		properties.setProperty("javax.jdo.option.ConnectionPassword", "testpassword34");
		PersistenceManagerFactory pmf = JDOHelper.getPersistenceManagerFactory(properties);
		return pmf;
	}

	public static PersistenceManagerFactory getPMF(Parameters param, boolean autoCreate) {
		final Properties properties = commonProperties(autoCreate);
		properties.setProperty("javax.jdo.option.ConnectionURL", param.outputToDatabaseURL);
		properties.setProperty("javax.jdo.option.ConnectionDriverName", "org.postgresql.Driver");
		properties.setProperty("javax.jdo.option.ConnectionUserName", param.outputToDatabaseUserName);
		properties.setProperty("javax.jdo.option.ConnectionPassword", param.outputToDatabaseUserPassword);
		PersistenceManagerFactory pmf = JDOHelper.getPersistenceManagerFactory(properties);
		return pmf;
	}

}
