package uk.org.cinquin.mutinack.misc_util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.nustaq.serialization.FSTConfiguration;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;

public class FileCache<T extends Serializable> {
	
	private final static Logger logger = LoggerFactory.getLogger(FileCache.class);

	private static final FSTConfiguration conf = FSTConfiguration.createFastBinaryConfiguration();
	
	private static final Map<String, SoftReference<Object>> cache = new ConcurrentHashMap<>();
	
	@SuppressWarnings("unchecked")
	public static <T> T getCached(String path, String cacheExtension, Function<String, T> processor) {
		String canonicalPath;
		try {
			canonicalPath = new File(path + cacheExtension).getCanonicalPath();
		} catch (IOException e) {
			throw new RuntimeException("Error getting canonical path for cache for " + path);
		}
		SoftReference<Object> sr = cache.get(canonicalPath);
		Object o = sr != null ? sr.get() : null;
		if (o != null) {
			return (T) o;
		}
		T result = getCached0(path, cacheExtension, processor);
		cache.put(canonicalPath, new SoftReference<Object> (result));
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T getCached0(String path, String cacheExtension, Function<String, T> processor) {
		File cachedInfo;
		try {
			cachedInfo = new File(path + cacheExtension).getCanonicalFile();
		} catch (IOException e1) {
			throw new RuntimeException("Error getting canonical path for cache for " + path);
		}
		@SuppressWarnings("null")
		T result = null;
		boolean recreate = true;
		if (cachedInfo.exists() && 
				cachedInfo.lastModified() > new File(path).lastModified()) {
			try (DataInputStream dataIS = new DataInputStream(new FileInputStream(cachedInfo))) {
				byte [] bytes = new byte [(int) cachedInfo.length()];
				dataIS.readFully(bytes);
				result = (T) conf.asObject(bytes);
				recreate = false;
			} catch (IOException e) {
				logger.debug("Problem reading cache from " + cachedInfo.getAbsolutePath(), e);
			}
		} 
		if (recreate) {
			logger.info("Could not read from cache for " + path);
			result = processor.apply(path);
			try (FileOutputStream os = new FileOutputStream(cachedInfo)) {
				try (FileLock lock = os.getChannel().tryLock()) {
					if (lock != null) {
						os.write(conf.asByteArray(result));
					}
				}
			} catch (OverlappingFileLockException e) {
				logger.debug("Ignoring concurrent write attempt to cache file");
			} catch (IOException e) {
				logger.debug("Could not save cached data to "
						+ cachedInfo.getAbsolutePath() + "; continuing anyway", e);
			}
		}
		return result;
	}

}
