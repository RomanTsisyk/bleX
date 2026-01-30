package io.github.romantsisyk.blex.logging

/**
 * Logger interface for BLE-X library.
 *
 * Implement this interface to provide custom logging behavior for the BLE-X library.
 * This allows integration with various logging frameworks (Timber, SLF4J, etc.)
 * or custom logging implementations.
 *
 * ## Usage Example
 *
 * ### Custom Logger Implementation
 * ```kotlin
 * class TimberBleLogger : BleLogger {
 *     override fun verbose(tag: String, message: String) {
 *         Timber.tag(tag).v(message)
 *     }
 *     override fun debug(tag: String, message: String) {
 *         Timber.tag(tag).d(message)
 *     }
 *     override fun info(tag: String, message: String) {
 *         Timber.tag(tag).i(message)
 *     }
 *     override fun warn(tag: String, message: String) {
 *         Timber.tag(tag).w(message)
 *     }
 *     override fun error(tag: String, message: String, throwable: Throwable?) {
 *         if (throwable != null) {
 *             Timber.tag(tag).e(throwable, message)
 *         } else {
 *             Timber.tag(tag).e(message)
 *         }
 *     }
 * }
 *
 * // Set the custom logger
 * BleLog.logger = TimberBleLogger()
 * ```
 *
 * @see BleLog
 * @see AndroidBleLogger
 * @see NoOpBleLogger
 */
interface BleLogger {
    /**
     * Logs a verbose message.
     *
     * Verbose messages are the most detailed level of logging, typically used
     * for tracing execution flow during development.
     *
     * @param tag The log tag, typically identifying the source class or component.
     * @param message The message to log.
     */
    fun verbose(tag: String, message: String)

    /**
     * Logs a debug message.
     *
     * Debug messages are used for information useful during development
     * and debugging, but not needed in production.
     *
     * @param tag The log tag, typically identifying the source class or component.
     * @param message The message to log.
     */
    fun debug(tag: String, message: String)

    /**
     * Logs an informational message.
     *
     * Info messages highlight the progress of the application at a coarse-grained level,
     * such as connection state changes or significant events.
     *
     * @param tag The log tag, typically identifying the source class or component.
     * @param message The message to log.
     */
    fun info(tag: String, message: String)

    /**
     * Logs a warning message.
     *
     * Warning messages indicate potentially harmful situations that should be
     * reviewed but don't prevent the application from functioning.
     *
     * @param tag The log tag, typically identifying the source class or component.
     * @param message The message to log.
     */
    fun warn(tag: String, message: String)

    /**
     * Logs an error message with an optional throwable.
     *
     * Error messages indicate serious problems that have occurred,
     * such as failed operations or unexpected exceptions.
     *
     * @param tag The log tag, typically identifying the source class or component.
     * @param message The message to log.
     * @param throwable Optional throwable associated with the error.
     */
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Log level for filtering log output.
 *
 * Log levels are ordered by priority, where lower priority values represent
 * more verbose logging. Setting a minimum log level filters out all messages
 * below that level.
 *
 * ## Level Hierarchy (lowest to highest priority)
 * 1. [VERBOSE] - Most detailed logging
 * 2. [DEBUG] - Debug information
 * 3. [INFO] - General information
 * 4. [WARN] - Warning messages
 * 5. [ERROR] - Error messages only
 * 6. [NONE] - Disable all logging
 *
 * ## Usage Example
 * ```kotlin
 * // Only show warnings and errors
 * BleLog.setLevel(LogLevel.WARN)
 *
 * // Show all logs including verbose
 * BleLog.setLevel(LogLevel.VERBOSE)
 *
 * // Disable all logging
 * BleLog.setLevel(LogLevel.NONE)
 * ```
 *
 * @property priority The numeric priority of this log level. Lower values
 *                   indicate more verbose logging.
 */
enum class LogLevel(val priority: Int) {
    /**
     * Verbose log level (priority 0).
     *
     * The most detailed logging level, used for tracing execution flow.
     * Should typically only be enabled during active debugging.
     */
    VERBOSE(0),

    /**
     * Debug log level (priority 1).
     *
     * Used for information helpful during development and debugging.
     * This is the default minimum level for [AndroidBleLogger].
     */
    DEBUG(1),

    /**
     * Info log level (priority 2).
     *
     * Used for general informational messages about application progress.
     */
    INFO(2),

    /**
     * Warning log level (priority 3).
     *
     * Used for potentially harmful situations that don't prevent operation.
     */
    WARN(3),

    /**
     * Error log level (priority 4).
     *
     * Used for error events that might still allow the application to continue.
     */
    ERROR(4),

    /**
     * None log level (priority 5).
     *
     * Disables all logging when set as the minimum level.
     */
    NONE(5)
}

/**
 * Default logger implementation using Android's [android.util.Log].
 *
 * This logger wraps Android's built-in logging system and supports
 * filtering by minimum log level. Messages below the minimum level
 * are silently ignored.
 *
 * ## Usage Example
 * ```kotlin
 * // Create a logger that only shows warnings and errors
 * val logger = AndroidBleLogger(minLevel = LogLevel.WARN)
 *
 * // Set as the global logger
 * BleLog.logger = logger
 * ```
 *
 * @param minLevel The minimum log level to output. Messages below this level
 *                 will be filtered out. Defaults to [LogLevel.DEBUG].
 *
 * @see BleLogger
 * @see LogLevel
 */
class AndroidBleLogger(
    private val minLevel: LogLevel = LogLevel.DEBUG
) : BleLogger {

    /**
     * Logs a verbose message if [minLevel] permits.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    override fun verbose(tag: String, message: String) {
        if (minLevel.priority <= LogLevel.VERBOSE.priority) {
            android.util.Log.v(tag, message)
        }
    }

    /**
     * Logs a debug message if [minLevel] permits.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    override fun debug(tag: String, message: String) {
        if (minLevel.priority <= LogLevel.DEBUG.priority) {
            android.util.Log.d(tag, message)
        }
    }

    /**
     * Logs an info message if [minLevel] permits.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    override fun info(tag: String, message: String) {
        if (minLevel.priority <= LogLevel.INFO.priority) {
            android.util.Log.i(tag, message)
        }
    }

    /**
     * Logs a warning message if [minLevel] permits.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    override fun warn(tag: String, message: String) {
        if (minLevel.priority <= LogLevel.WARN.priority) {
            android.util.Log.w(tag, message)
        }
    }

    /**
     * Logs an error message if [minLevel] permits.
     *
     * @param tag The log tag.
     * @param message The message to log.
     * @param throwable Optional throwable to include in the log.
     */
    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (minLevel.priority <= LogLevel.ERROR.priority) {
            if (throwable != null) {
                android.util.Log.e(tag, message, throwable)
            } else {
                android.util.Log.e(tag, message)
            }
        }
    }
}

/**
 * No-op logger that discards all log messages.
 *
 * Use this logger when you want to completely disable logging in the BLE-X library.
 * This is more efficient than setting [LogLevel.NONE] as it avoids the level check
 * overhead entirely.
 *
 * ## Usage Example
 * ```kotlin
 * // Completely disable logging
 * BleLog.logger = NoOpBleLogger
 *
 * // Or use the convenience method
 * BleLog.disable()
 * ```
 *
 * @see BleLog.disable
 */
object NoOpBleLogger : BleLogger {
    /** No-op implementation. */
    override fun verbose(tag: String, message: String) {}
    /** No-op implementation. */
    override fun debug(tag: String, message: String) {}
    /** No-op implementation. */
    override fun info(tag: String, message: String) {}
    /** No-op implementation. */
    override fun warn(tag: String, message: String) {}
    /** No-op implementation. */
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}

/**
 * Global logging configuration for BLE-X library.
 *
 * This object provides centralized logging functionality for the entire BLE-X library.
 * It allows configuration of the logging implementation, minimum log level, and
 * provides convenient shorthand methods for logging at different levels.
 *
 * ## Configuration
 *
 * ### Setting a Custom Logger
 * ```kotlin
 * // Use a custom logger implementation
 * BleLog.logger = MyCustomLogger()
 * ```
 *
 * ### Setting the Minimum Log Level
 * ```kotlin
 * // Only show warnings and errors
 * BleLog.setLevel(LogLevel.WARN)
 *
 * // Show all logs
 * BleLog.setLevel(LogLevel.VERBOSE)
 * ```
 *
 * ### Disabling Logging
 * ```kotlin
 * // Completely disable all logging
 * BleLog.disable()
 * ```
 *
 * ## Thread Safety
 *
 * The [logger] and [minLevel] properties are marked as `@Volatile` to ensure
 * visibility across threads. However, if you need to atomically check and set
 * these properties, external synchronization is required.
 *
 * ## Logging Methods
 *
 * Shorthand logging methods are provided for convenience:
 * - [v] - Verbose
 * - [d] - Debug
 * - [i] - Info
 * - [w] - Warning
 * - [e] - Error
 *
 * @see BleLogger
 * @see LogLevel
 * @see AndroidBleLogger
 */
object BleLog {
    /**
     * The current logger implementation.
     *
     * Defaults to [AndroidBleLogger] with [LogLevel.DEBUG] minimum level.
     * This property is volatile to ensure thread-safe reads and writes.
     */
    @Volatile
    var logger: BleLogger = AndroidBleLogger()

    /**
     * The current minimum log level.
     *
     * This property tracks the minimum level for reference. Note that
     * the actual filtering is performed by the [logger] implementation.
     * This property is volatile to ensure thread-safe reads and writes.
     */
    @Volatile
    var minLevel: LogLevel = LogLevel.DEBUG

    /**
     * Logs a verbose message.
     *
     * @param tag The log tag identifying the source.
     * @param message The message to log.
     */
    fun v(tag: String, message: String) = logger.verbose(tag, message)

    /**
     * Logs a debug message.
     *
     * @param tag The log tag identifying the source.
     * @param message The message to log.
     */
    fun d(tag: String, message: String) = logger.debug(tag, message)

    /**
     * Logs an info message.
     *
     * @param tag The log tag identifying the source.
     * @param message The message to log.
     */
    fun i(tag: String, message: String) = logger.info(tag, message)

    /**
     * Logs a warning message.
     *
     * @param tag The log tag identifying the source.
     * @param message The message to log.
     */
    fun w(tag: String, message: String) = logger.warn(tag, message)

    /**
     * Logs an error message with an optional throwable.
     *
     * @param tag The log tag identifying the source.
     * @param message The message to log.
     * @param t Optional throwable associated with the error.
     */
    fun e(tag: String, message: String, t: Throwable? = null) = logger.error(tag, message, t)

    /**
     * Disables all logging by setting the logger to [NoOpBleLogger].
     *
     * This is the most efficient way to disable logging as it completely
     * bypasses all logging logic. Use [setLevel] with [LogLevel.NONE] if
     * you want to temporarily disable logging while preserving the ability
     * to re-enable it at a specific level.
     *
     * ## Example
     * ```kotlin
     * // Disable logging for release builds
     * if (!BuildConfig.DEBUG) {
     *     BleLog.disable()
     * }
     * ```
     */
    fun disable() {
        logger = NoOpBleLogger
    }

    /**
     * Sets the minimum log level and updates the logger accordingly.
     *
     * This method creates a new [AndroidBleLogger] with the specified
     * minimum level. If you're using a custom logger implementation,
     * you should configure its level directly instead of using this method.
     *
     * ## Example
     * ```kotlin
     * // Development: show all logs
     * BleLog.setLevel(LogLevel.VERBOSE)
     *
     * // Production: only show warnings and errors
     * BleLog.setLevel(LogLevel.WARN)
     *
     * // Disable all logging
     * BleLog.setLevel(LogLevel.NONE)
     * ```
     *
     * @param level The minimum [LogLevel] for log output.
     */
    fun setLevel(level: LogLevel) {
        minLevel = level
        logger = AndroidBleLogger(level)
    }
}
