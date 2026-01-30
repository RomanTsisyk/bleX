package io.github.romantsisyk.blex.exception

/**
 * Base exception class for all BLE-related errors in the BleX library.
 *
 * This sealed class hierarchy provides a type-safe way to handle different categories
 * of BLE errors. Each subclass represents a specific type of failure that can occur
 * during BLE operations.
 *
 * ## Exception Hierarchy
 *
 * - [BleConnectionException] - Connection establishment or maintenance failures
 * - [BleCharacteristicException] - Characteristic read/write/notify operation failures
 * - [BleTimeoutException] - Operation timeout errors
 * - [BleNotFoundException] - Service or characteristic not found errors
 * - [BlePermissionException] - Missing Android permissions errors
 *
 * ## Usage Example
 *
 * ```kotlin
 * try {
 *     connection.readCharacteristicResult(serviceUuid, charUuid)
 *         .onSuccess { data -> processData(data) }
 *         .onFailure { error ->
 *             when (error) {
 *                 is BleConnectionException -> handleConnectionError(error)
 *                 is BleCharacteristicException -> handleCharacteristicError(error)
 *                 is BleTimeoutException -> retryOperation()
 *                 is BleNotFoundException -> logMissingService(error)
 *                 is BlePermissionException -> requestPermissions(error.missingPermissions)
 *             }
 *         }
 * } catch (e: BleException) {
 *     Log.e("BLE", "GATT status: ${e.gattStatus}, message: ${e.message}")
 * }
 * ```
 *
 * @property message A human-readable description of the error.
 * @property cause The underlying cause of this exception, if any.
 *
 * @see io.github.romantsisyk.blex.result.BleResult
 */
sealed class BleException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * The GATT status code associated with this error, if available.
     *
     * Common GATT status codes include:
     * - `0x00` (0) - GATT_SUCCESS
     * - `0x05` (5) - GATT_INSUFFICIENT_AUTHENTICATION
     * - `0x06` (6) - GATT_REQUEST_NOT_SUPPORTED
     * - `0x0D` (13) - GATT_INVALID_ATTRIBUTE_LENGTH
     * - `0x85` (133) - GATT_ERROR (generic error, often timeout)
     * - `0x08` (8) - GATT_CONN_TIMEOUT
     *
     * @return The GATT status code, or `null` if not applicable.
     */
    abstract val gattStatus: Int?
}

/**
 * Exception thrown when a BLE connection attempt fails or an existing connection is lost.
 *
 * This exception is used for errors that occur during:
 * - Initial connection establishment
 * - Connection state changes
 * - Unexpected disconnections
 *
 * ## Common Causes
 *
 * - Device out of range
 * - Device powered off
 * - Too many connected devices
 * - GATT server unavailable
 * - Connection timeout
 *
 * ## Usage Example
 *
 * ```kotlin
 * when (error) {
 *     is BleConnectionException -> {
 *         Log.e("BLE", "Connection failed to ${error.deviceAddress}")
 *         error.gattStatus?.let { status ->
 *             Log.e("BLE", "GATT status: $status")
 *         }
 *     }
 * }
 * ```
 *
 * @property message A human-readable description of the connection error.
 * @property gattStatus The GATT status code from the connection callback, if available.
 * @property deviceAddress The MAC address of the device that failed to connect.
 *
 * @see BleException
 */
class BleConnectionException(
    message: String,
    override val gattStatus: Int? = null,
    val deviceAddress: String? = null
) : BleException(message)

/**
 * Exception thrown when a characteristic operation (read, write, or notify) fails.
 *
 * This exception provides detailed information about which operation failed
 * and on which characteristic, making it easier to diagnose and handle
 * specific failures.
 *
 * ## Common Causes
 *
 * - Characteristic does not support the requested operation
 * - Insufficient encryption or authentication
 * - Invalid value length
 * - Characteristic not found
 * - Device disconnected during operation
 *
 * ## Usage Example
 *
 * ```kotlin
 * when (error) {
 *     is BleCharacteristicException -> {
 *         val opName = error.operation?.name ?: "UNKNOWN"
 *         Log.e("BLE", "$opName failed on ${error.characteristicUuid}")
 *
 *         when (error.operation) {
 *             CharacteristicOperation.READ -> handleReadFailure()
 *             CharacteristicOperation.WRITE -> handleWriteFailure()
 *             CharacteristicOperation.NOTIFY -> handleNotifyFailure()
 *             null -> handleUnknownFailure()
 *         }
 *     }
 * }
 * ```
 *
 * @property message A human-readable description of the characteristic error.
 * @property gattStatus The GATT status code from the operation callback.
 * @property characteristicUuid The UUID of the characteristic that failed, as a string.
 * @property operation The type of operation that failed.
 *
 * @see CharacteristicOperation
 * @see BleException
 */
class BleCharacteristicException(
    message: String,
    override val gattStatus: Int? = null,
    val characteristicUuid: String? = null,
    val operation: CharacteristicOperation? = null
) : BleException(message) {

    /**
     * Enumeration of characteristic operations that can fail.
     *
     * Used to identify which type of characteristic operation caused the exception.
     */
    enum class CharacteristicOperation {
        /** A characteristic read operation */
        READ,
        /** A characteristic write operation */
        WRITE,
        /** A notification/indication enable or disable operation */
        NOTIFY
    }
}

/**
 * Exception thrown when a BLE operation times out.
 *
 * BLE operations can time out for various reasons, including:
 * - Device moved out of range during operation
 * - Device became unresponsive
 * - Radio interference
 * - Device processing taking too long
 *
 * ## GATT Timeout Status
 *
 * The default [gattStatus] for timeout exceptions is `0x85` (133), which is the
 * standard GATT_ERROR code often used to indicate timeout conditions.
 *
 * ## Usage Example
 *
 * ```kotlin
 * when (error) {
 *     is BleTimeoutException -> {
 *         Log.w("BLE", "Operation '${error.operation}' timed out")
 *         // Implement retry logic
 *         retryWithBackoff()
 *     }
 * }
 * ```
 *
 * @property message A human-readable description of the timeout.
 * @property operation A description of the operation that timed out (e.g., "readCharacteristic", "connect").
 * @property gattStatus The GATT status code, defaults to `0x85` (133) for generic GATT error/timeout.
 *
 * @see BleException
 */
class BleTimeoutException(
    message: String,
    val operation: String? = null,
    override val gattStatus: Int? = GATT_ERROR
) : BleException(message) {

    companion object {
        /** Standard GATT error code often indicating timeout (133 / 0x85) */
        const val GATT_ERROR = 0x85
    }
}

/**
 * Exception thrown when a requested GATT service or characteristic is not found.
 *
 * This exception indicates that the device does not expose the expected
 * service or characteristic, which could mean:
 * - The device is a different model than expected
 * - The device firmware version doesn't support the feature
 * - Service discovery was incomplete
 * - The UUID is incorrect
 *
 * ## Usage Example
 *
 * ```kotlin
 * when (error) {
 *     is BleNotFoundException -> {
 *         error.serviceUuid?.let { service ->
 *             Log.e("BLE", "Service not found: $service")
 *         }
 *         error.characteristicUuid?.let { char ->
 *             Log.e("BLE", "Characteristic not found: $char")
 *         }
 *         // Suggest user to check device compatibility
 *         showIncompatibleDeviceMessage()
 *     }
 * }
 * ```
 *
 * @property message A human-readable description of what was not found.
 * @property serviceUuid The UUID of the service that was not found, as a string.
 * @property characteristicUuid The UUID of the characteristic that was not found, as a string.
 * @property gattStatus The GATT status code, if available.
 *
 * @see BleException
 */
class BleNotFoundException(
    message: String,
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    override val gattStatus: Int? = null
) : BleException(message)

/**
 * Exception thrown when required Android permissions for BLE operations are missing.
 *
 * Starting with Android 12 (API 31), BLE operations require runtime permissions
 * from the `android.permission.BLUETOOTH_*` family. This exception is thrown
 * when an operation is attempted without the necessary permissions.
 *
 * ## Required Permissions
 *
 * - `BLUETOOTH_SCAN` - Required for scanning (Android 12+)
 * - `BLUETOOTH_CONNECT` - Required for connecting and GATT operations (Android 12+)
 * - `BLUETOOTH_ADVERTISE` - Required for advertising (Android 12+)
 * - `ACCESS_FINE_LOCATION` - Required for scanning on older Android versions
 *
 * ## Usage Example
 *
 * ```kotlin
 * when (error) {
 *     is BlePermissionException -> {
 *         Log.e("BLE", "Missing permissions: ${error.missingPermissions}")
 *         // Request the missing permissions
 *         requestPermissions(error.missingPermissions.toTypedArray())
 *     }
 * }
 * ```
 *
 * @property message A human-readable description of the permission error.
 * @property missingPermissions A list of permission strings that are required but not granted.
 * @property gattStatus The GATT status code, typically `null` for permission errors.
 *
 * @see BleException
 */
class BlePermissionException(
    message: String,
    val missingPermissions: List<String> = emptyList(),
    override val gattStatus: Int? = null
) : BleException(message)
