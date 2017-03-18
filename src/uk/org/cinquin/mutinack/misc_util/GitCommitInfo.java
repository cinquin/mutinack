package uk.org.cinquin.mutinack.misc_util;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class GitCommitInfo {

	public static String getGitCommit() {
		try {
			//Adapted from
			//http://stackoverflow.com/questions/1272648/reading-my-own-jars-manifest
			Class<?> clazz = GitCommitInfo.class;
			String className = clazz.getSimpleName() + ".class";
			String classPath = clazz.getResource(className).toString();
			final String manifestPath;
			if (!classPath.startsWith("jar")) {
				//Class not from JAR
				File manifest = new File(classPath + "../../../../../../MANIFEST.MF");
				if (manifest.exists()) {
					manifestPath = manifest.getAbsolutePath();
				} else {
					return "Could not retrieve git version info from " + classPath;
				}
			} else {
				manifestPath = classPath.substring(0, classPath.lastIndexOf('!') + 1) +
					"/META-INF/MANIFEST.MF";
			}
			try (InputStream is = new URL(manifestPath).openStream()) {
				Manifest manifest = new Manifest(is);
				Attributes attr = manifest.getMainAttributes();
				return attr.getValue("Git-version");
			}
		} catch (Exception e) {
			return "Could not retrieve git version: " + e.toString();
		}
	}
}
