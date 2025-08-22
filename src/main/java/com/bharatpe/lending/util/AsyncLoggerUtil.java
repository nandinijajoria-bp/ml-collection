package com.bharatpe.lending.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import java.util.concurrent.*;

/**
 * Utility class for asynchronous logging operations that reduces impact on application threads
 * while ensuring log messages eventually get processed.
 */
@Slf4j
public class AsyncLoggerUtil {

	// Configurable properties
	private static final int CORE_POOL_SIZE = 4;
	private static final int MAX_POOL_SIZE = 8;
	private static final int KEEP_ALIVE_TIME = 60;
	private static final int QUEUE_CAPACITY = 10000;

	// ThreadPoolExecutor with bounded queue to prevent memory issues
	private static final ThreadPoolExecutor logExecutor = new ThreadPoolExecutor(
			CORE_POOL_SIZE,
			MAX_POOL_SIZE,
			KEEP_ALIVE_TIME, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>(QUEUE_CAPACITY),
			r -> {
				Thread t = new Thread(r, "async-logger-thread");
				t.setDaemon(true);
				return t;
			},
			new ThreadPoolExecutor.CallerRunsPolicy() // If queue is full, run in caller thread
	);

	// Monitor for log queue size
	private static final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "async-logger-monitor");
		t.setDaemon(true);
		return t;
	});

	static {
		// Periodically check queue size and log warnings if backed up
		monitor.scheduleAtFixedRate(() -> {
			int queueSize = logExecutor.getQueue().size();
			if (queueSize > QUEUE_CAPACITY * 0.8) {
				AsyncLoggerUtil.log.info("WARNING: Async logger queue is 80% full: " + queueSize + " items");
			}
		}, 30, 30, TimeUnit.SECONDS);

		// Register shutdown hook to flush pending logs
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logExecutor.shutdown();
			try {
				if (!logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
					logExecutor.shutdownNow();
				}
			} catch (InterruptedException e) {
				logExecutor.shutdownNow();
			}
			monitor.shutdownNow();
		}));
	}

	/**
	 * Log trace message asynchronously
	 */
	public static void logTrace(Logger logger, String message, Object... args) {
		if (logger.isTraceEnabled()) {
			logExecutor.submit(() -> logger.trace(message, args));
		}
	}

	/**
	 * Log debug message asynchronously
	 */
	public static void logDebug(Logger logger, String message, Object... args) {
		if (logger.isDebugEnabled()) {
			logExecutor.submit(() -> logger.debug(message, args));
		}
	}

	/**
	 * Log info message asynchronously
	 */
	public static void logInfo(Logger logger, String message, Object... args) {
		if (logger.isInfoEnabled()) {
			logExecutor.submit(() -> logger.info(message, args));
		}
	}

	/**
	 * Log warning message asynchronously
	 */
	public static void logWarn(Logger logger, String message, Object... args) {
		if (logger.isWarnEnabled()) {
			logExecutor.submit(() -> logger.warn(message, args));
		}
	}

	/**
	 * Log error message asynchronously with high priority
	 */
	public static void logError(Logger logger, String message, Object... args) {
		if (logger.isErrorEnabled()) {
			// Use highest priority for error logs
			try {
				logExecutor.submit(() -> logger.error(message, args));
			} catch (RejectedExecutionException e) {
				// Fallback to synchronous logging if queue is full
				logger.error(message, args);
			}
		}
	}

	/**
	 * Log error message with throwable asynchronously
	 */
	public static void logError(Logger logger, String message, Throwable throwable) {
		if (logger.isErrorEnabled()) {
			try {
				logExecutor.submit(() -> logger.error(message, throwable));
			} catch (RejectedExecutionException e) {
				// Fallback to synchronous logging if queue is full
				logger.error(message, throwable);
			}
		}
	}

	/**
	 * Returns the current size of the logging queue
	 */
	public static int getQueueSize() {
		return logExecutor.getQueue().size();
	}

	/**
	 * Returns the number of active logging threads
	 */
	public static int getActiveThreadCount() {
		return logExecutor.getActiveCount();
	}
}