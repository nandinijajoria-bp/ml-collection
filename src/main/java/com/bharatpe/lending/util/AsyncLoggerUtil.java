package com.bharatpe.lending.util;

import org.slf4j.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Utility class for asynchronous logging operations
 */
public class AsyncLoggerUtil {

	private static final ExecutorService logExecutor = Executors.newFixedThreadPool(2);

	/**
	 * Log info message asynchronously
	 */
	public static void logInfo(Logger logger, String message, Object... args) {
		logExecutor.submit(() -> logger.info(message, args));
	}

	/**
	 * Log error message asynchronously
	 */
	public static void logError(Logger logger, String message, Object... args) {
		logExecutor.submit(() -> logger.error(message, args));
	}

	/**
	 * Log debug message asynchronously
	 */
	public static void logDebug(Logger logger, String message, Object... args) {
		logExecutor.submit(() -> logger.debug(message, args));
	}

	/**
	 * Shutdown the executor service (call during application shutdown)
	 */
	public static void shutdown() {
		logExecutor.shutdown();
	}
}