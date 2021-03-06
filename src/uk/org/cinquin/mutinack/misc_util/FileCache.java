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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;
import org.nustaq.serialization.FSTConfiguration;

import contrib.uk.org.lidalia.slf4jext.Logger;
import contrib.uk.org.lidalia.slf4jext.LoggerFactory;

public class FileCache<T extends Serializable> {

	private static final Logger logger = LoggerFactory.getLogger(FileCache.class);

	private static final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

	private static final Map<String, SoftReference<Object>> cache = new ConcurrentHashMap<>();

	@SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
	public static <T> @NonNull T getCached(String path, String cacheExtension, Function<String, @NonNull T> processor,
			Predicate<T> mustRecompute) {
		String canonicalPath;
		try {
			canonicalPath = new File(path + cacheExtension).getCanonicalPath();
		} catch (IOException e) {
			throw new RuntimeException("Error getting canonical path for cache for " + path, e);
		}
		SoftReference<Object> sr = cache.get(canonicalPath);
		Object o = sr != null ? sr.get() : null;
		if (o != null) {
			return (T) o;
		}
		@NonNull T result = getCached0(path, cacheExtension, processor, mustRecompute);
		cache.put(canonicalPath, new SoftReference<> (result));
		return result;
	}

	@SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
	public static <T> @NonNull T getCached0(String path, String cacheExtension, Function<String, @NonNull T> processor,
			Predicate<T> mustRecompute) {
		File cachedInfo;
		try {
			cachedInfo = new File(path + cacheExtension).getCanonicalFile();
		} catch (IOException e1) {
			throw new RuntimeException("Error getting canonical path for cache for " + path, e1);
		}

		T result = null;
		boolean recreate = true;
		if (cachedInfo.exists() &&
				cachedInfo.lastModified() > new File(path).lastModified()) {
			try (DataInputStream dataIS = new DataInputStream(new FileInputStream(cachedInfo))) {
				byte [] bytes = new byte [(int) cachedInfo.length()];
				dataIS.readFully(bytes);
				result = (T) conf.asObject(bytes);
				recreate = mustRecompute != null && mustRecompute.test(result);
			} catch (Exception e) {
				logger.debug("Problem reading cache from " + cachedInfo.getAbsolutePath(), e);
			}
		}
		if (recreate) {
			logger.debug("Could not read from cache for " + path);
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
		return Objects.requireNonNull(result);
	}

}
