package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.content.Context
import io.github.romantsisyk.blex.exception.BleCharacteristicException
import io.github.romantsisyk.blex.exception.BleCharacteristicException.CharacteristicOperation
import io.github.romantsisyk.blex.exception.BleNotFoundException
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.models.ConnectionState
import io.github.romantsisyk.blex.result.BleResult
import io.github.romantsisyk.blex.result.bleFailure
import io.github.romantsisyk.blex.result.bleSuccess
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Manages a Bluetooth Low Energy (BLE) connection to a remote device.
 *
 * This class provides a high-level API for establishing and managing BLE connections,
 * including reading and writing characteristics, enabling/disabling notifications,
 * and handling device bonding. It wraps a [ConnectionStateMachine] to manage the
 * underlying connection state and provides coroutine-based suspend functions for
 * asynchronous BLE operations.
 *
 * ## Usage Example
 * ```kotlin
 * val connection = BleConnection(context, bluetoothDevice)
 * connection.connect()
 *
 * // Observe connection state
 * connection.connectionStateFlow.collect { state ->
 *     when (state) {
 *         ConnectionState.Connected -> println("Connected!")
 *         ConnectionState.Disconnected -> println("Disconnected")
 *         // Handle other states...
 *     }
 * }
 *
 * // Read a characteristic
 * val value = connection.readCharacteristic(serviceUuid, characteristicUuid)
 *
 * // Don't forget to close when done
 * connection.close()
 * ```
 *
 * @param context The Android [Context] used for BLE operations. An application context
 *                is recommended to avoid memory leaks.
 * @param device The [BluetoothDevice] to connect to.
 * @param bondManager Optional [BondManager] for handling device bonding operations.
 *                    If null, [bond] will return an empty flow.
 * @param autoReconnect Whether to automatically attempt reconnection when the connection
 *                      is lost unexpectedly. Defaults to `true`.
 * @param reconnectDelayMs Base delay in milliseconds for exponential backoff reconnection.
 *                         The actual delay increases exponentially with each attempt.
 *                         Defaults to [ConnectionStateMachine.DEFAULT_RECONNECT_DELAY_MS].
 * @param maxReconnectDelayMs Maximum delay in milliseconds between reconnection attempts.
 *                            The exponential backoff will be capped at this value.
 *                            Defaults to [ConnectionStateMachine.DEFAULT_MAX_RECONNECT_DELAY_MS].
 * @param maxReconnectAttempts Maximum number of reconnection attempts before giving up.
 *                             When this limit is reached, the state transitions to [ConnectionState.Error].
 *                             Defaults to [ConnectionStateMachine.DEFAULT_MAX_RECONNECT_ATTEMPTS].
 *
 * @see ConnectionStateMachine
 * @see ConnectionState
 * @see BondManager
 */
class BleConnection(
    context: Context,
    private val device: BluetoothDevice,
    private val bondManager: BondManager? = null,
    autoReconnect: Boolean = true,
    reconnectDelayMs: Long = ConnectionStateMachine.DEFAULT_RECONNECT_DELAY_MS,
    maxReconnectDelayMs: Long = ConnectionStateMachine.DEFAULT_MAX_RECONNECT_DELAY_MS,
    maxReconnectAttempts: Int = ConnectionStateMachine.DEFAULT_MAX_RECONNECT_ATTEMPTS
) {

    private val connectionStateMachine = ConnectionStateMachine(
        context,
        device,
        bondManager,
        autoReconnect,
        reconnectDelayMs,
        maxReconnectDelayMs,
        maxReconnectAttempts
    )

    /**
     * A [StateFlow] that emits the current connection state of the BLE device.
     *
     * Collectors will receive updates whenever the connection state changes,
     * including states such as [ConnectionState.Disconnected], [ConnectionState.Connecting],
     * [ConnectionState.Connected], and [ConnectionState.Disconnecting].
     *
     * This flow is hot and will always have a current value available.
     *
     * @see ConnectionState
     */
    val connectionStateFlow: StateFlow<ConnectionState> = connectionStateMachine.connectionStateFlow

    /**
     * Initiates a connection to the BLE device.
     *
     * This method triggers the connection process asynchronously. The connection state
     * can be observed through [connectionStateFlow]. If the device is already connected
     * or a connection attempt is in progress, this method may have no effect depending
     * on the [ConnectionStateMachine] implementation.
     *
     * If `autoReconnect` was enabled during construction and the connection is lost,
     * the system will automatically attempt to reconnect after the configured delay.
     *
     * @see disconnect
     * @see close
     * @see connectionStateFlow
     */
    fun connect() {
        connectionStateMachine.connect()
    }

    /**
     * Disconnects from the BLE device while keeping resources allocated.
     *
     * This method gracefully terminates the active connection but retains the underlying
     * [BluetoothGatt] object, allowing for potential reconnection without full reinitialization.
     * Use [close] instead if you want to fully release all resources.
     *
     * After calling this method, the [connectionStateFlow] will transition to
     * [ConnectionState.Disconnecting] and eventually [ConnectionState.Disconnected].
     *
     * @see connect
     * @see close
     */
    fun disconnect() {
        connectionStateMachine.disconnect()
    }

    /**
     * Closes the BLE connection and releases all associated resources.
     *
     * This method disconnects from the device (if connected) and releases the underlying
     * [BluetoothGatt] object and all associated resources. After calling this method,
     * the [BleConnection] instance should not be reused. Create a new instance if you
     * need to reconnect to the device.
     *
     * It is important to call this method when you are done with the BLE connection
     * to avoid resource leaks.
     *
     * @see disconnect
     */
    fun close() {
        connectionStateMachine.close()
    }

    /**
     * Resets the reconnect attempt counter to zero.
     *
     * Call this method to allow the connection to attempt reconnection again after
     * it has reached the maximum number of attempts and transitioned to
     * [ConnectionState.Error]. This is useful when:
     *
     * - The user manually triggers a retry
     * - External conditions have changed (e.g., Bluetooth was toggled)
     * - A significant amount of time has passed
     *
     * Note that [connect] can be called directly without resetting the counter;
     * the counter is automatically reset when a connection succeeds.
     *
     * @see connect
     * @see ConnectionStateMachine.resetReconnectAttempts
     */
    fun resetReconnectAttempts() {
        connectionStateMachine.resetReconnectAttempts()
    }

    /**
     * Retrieves the active [BluetoothGatt] instance or throws an exception.
     *
     * @return The active [BluetoothGatt] instance.
     * @throws IllegalStateException If no GATT connection is currently established.
     */
    private fun getGattOrThrow(): BluetoothGatt {
        return connectionStateMachine.bluetoothGatt
            ?: throw IllegalStateException("BluetoothGatt is null for device ${device.address}")
    }

    /**
     * Initiates the bonding (pairing) process with the BLE device.
     *
     * Bonding creates a trusted relationship between the Android device and the BLE peripheral,
     * enabling encrypted communication and persistent pairing information. This method requires
     * a [BondManager] to be provided during construction.
     *
     * The returned [Flow] emits [BondState] updates as the bonding process progresses,
     * including states such as [BondState.Bonding], [BondState.Bonded], and [BondState.NotBonded].
     *
     * @return A [Flow] emitting [BondState] updates during the bonding process.
     *         Returns an empty flow if no [BondManager] was provided during construction.
     *
     * @see BondManager
     * @see BondState
     */
    fun bond(): Flow<BondState> {
        return bondManager?.bondDevice(device) ?: flow { }
    }

    /**
     * Reads the value of a characteristic from the connected BLE device.
     *
     * This is a suspend function that will complete when the characteristic read operation
     * finishes. The device must be connected before calling this method.
     *
     * For more detailed error information, consider using [readCharacteristicResult] instead,
     * which returns a [BleResult] with specific error types.
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to read.
     * @return The characteristic value as a [ByteArray] if the read was successful,
     *         or `null` if the read operation failed (e.g., characteristic not found,
     *         device disconnected, or GATT error).
     *
     * @throws IllegalStateException If called when the device is not connected
     *         (depending on implementation).
     *
     * @see writeCharacteristic
     * @see enableNotifications
     * @see readCharacteristicResult
     */
    suspend fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): ByteArray? {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.readCharacteristic(serviceUuid, characteristicUuid) { status, value ->
                if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                    continuation.resume(value)
                } else {
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Writes a value to a characteristic on the connected BLE device.
     *
     * This is a suspend function that will complete when the characteristic write operation
     * finishes. The device must be connected before calling this method. The write type
     * used depends on the characteristic's properties and the underlying implementation.
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to write to.
     * @param value The [ByteArray] value to write to the characteristic.
     * @return `true` if the write operation completed successfully (GATT_SUCCESS),
     *         `false` otherwise (e.g., characteristic not found, device disconnected,
     *         write not permitted, or GATT error).
     *
     * @throws IllegalStateException If called when the device is not connected
     *         (depending on implementation).
     *
     * @see readCharacteristic
     * @see writeCharacteristicResult
     */
    suspend fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, value: ByteArray): Boolean {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.writeCharacteristic(serviceUuid, characteristicUuid, value) { status ->
                continuation.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    /**
     * Reads the value of a characteristic from the connected BLE device, returning a [BleResult].
     *
     * This is a type-safe alternative to [readCharacteristic] that returns detailed error
     * information instead of `null` on failure. Use this method when you need to distinguish
     * between different failure causes.
     *
     * ## Usage Example
     *
     * ```kotlin
     * connection.readCharacteristicResult(serviceUuid, charUuid)
     *     .onSuccess { data ->
     *         val text = String(data, Charsets.UTF_8)
     *         Log.i("BLE", "Received: $text")
     *     }
     *     .onFailure { error ->
     *         when (error) {
     *             is BleNotFoundException -> Log.e("BLE", "Characteristic not found")
     *             is BleCharacteristicException -> Log.e("BLE", "Read failed: ${error.gattStatus}")
     *             else -> Log.e("BLE", "Error: ${error.message}")
     *         }
     *     }
     * ```
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to read.
     * @return A [BleResult] containing either:
     *         - [BleResult.Success] with the characteristic value as a [ByteArray]
     *         - [BleResult.Failure] with a [BleCharacteristicException] or [BleNotFoundException]
     *
     * @see readCharacteristic
     * @see BleResult
     * @see BleCharacteristicException
     */
    suspend fun readCharacteristicResult(
        serviceUuid: UUID,
        characteristicUuid: UUID
    ): BleResult<ByteArray> {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.readCharacteristic(serviceUuid, characteristicUuid) { status, value ->
                val result = when {
                    status == BluetoothGatt.GATT_SUCCESS && value != null -> {
                        bleSuccess(value)
                    }
                    value == null && status == BluetoothGatt.GATT_SUCCESS -> {
                        bleFailure(
                            BleNotFoundException(
                                message = "Characteristic returned null value",
                                serviceUuid = serviceUuid.toString(),
                                characteristicUuid = characteristicUuid.toString()
                            )
                        )
                    }
                    else -> {
                        bleFailure(
                            BleCharacteristicException(
                                message = "Failed to read characteristic: GATT status $status",
                                gattStatus = status,
                                characteristicUuid = characteristicUuid.toString(),
                                operation = CharacteristicOperation.READ
                            )
                        )
                    }
                }
                continuation.resume(result)
            }
        }
    }

    /**
     * Writes a value to a characteristic on the connected BLE device, returning a [BleResult].
     *
     * This is a type-safe alternative to [writeCharacteristic] that returns detailed error
     * information instead of a boolean. Use this method when you need to distinguish
     * between different failure causes.
     *
     * ## Usage Example
     *
     * ```kotlin
     * val data = "Hello".toByteArray()
     * connection.writeCharacteristicResult(serviceUuid, charUuid, data)
     *     .onSuccess {
     *         Log.i("BLE", "Write successful")
     *     }
     *     .onFailure { error ->
     *         when (error) {
     *             is BleCharacteristicException -> {
     *                 Log.e("BLE", "Write failed with GATT status: ${error.gattStatus}")
     *             }
     *             else -> Log.e("BLE", "Error: ${error.message}")
     *         }
     *     }
     * ```
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to write to.
     * @param value The [ByteArray] value to write to the characteristic.
     * @return A [BleResult] containing either:
     *         - [BleResult.Success] with [Unit] indicating successful write
     *         - [BleResult.Failure] with a [BleCharacteristicException]
     *
     * @see writeCharacteristic
     * @see BleResult
     * @see BleCharacteristicException
     */
    suspend fun writeCharacteristicResult(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray
    ): BleResult<Unit> {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.writeCharacteristic(serviceUuid, characteristicUuid, value) { status ->
                val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                    bleSuccess(Unit)
                } else {
                    bleFailure(
                        BleCharacteristicException(
                            message = "Failed to write characteristic: GATT status $status",
                            gattStatus = status,
                            characteristicUuid = characteristicUuid.toString(),
                            operation = CharacteristicOperation.WRITE
                        )
                    )
                }
                continuation.resume(result)
            }
        }
    }

    /**
     * Writes a large value to a characteristic using sequential chunked writes.
     *
     * This is a suspend function that automatically splits the data into chunks that fit
     * within the current MTU size and writes them sequentially. If the value is small enough
     * to fit in a single write (within MTU - 3 bytes for ATT header), it delegates to the
     * regular [writeCharacteristic] method.
     *
     * This is useful for writing values larger than the negotiated MTU, such as firmware
     * updates, large configuration blobs, or any data that exceeds the maximum single-write
     * payload size.
     *
     * ## Chunk Size Calculation
     *
     * - Default MTU: 23 bytes (if no MTU negotiation has occurred)
     * - Chunk size: MTU - 3 bytes (ATT protocol header)
     * - Example: With MTU of 247, chunk size is 244 bytes
     *
     * **Note**: This implementation writes chunks sequentially using standard write operations.
     * It does NOT use BLE Reliable Write (prepared writes with execute), which would provide
     * atomicity but requires different device-side handling.
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to write to.
     * @param value The [ByteArray] value to write. Can be larger than MTU.
     * @return `true` if all chunks were written successfully, `false` if any chunk failed.
     *
     * @see writeCharacteristic
     * @see writeLongCharacteristicResult
     */
    suspend fun writeLongCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.writeLongCharacteristic(serviceUuid, characteristicUuid, value) { status ->
                continuation.resume(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    /**
     * Writes a large value to a characteristic using sequential chunked writes, returning a [BleResult].
     *
     * This is a type-safe alternative to [writeLongCharacteristic] that returns detailed error
     * information instead of a boolean. Use this method when you need to distinguish between
     * different failure causes.
     *
     * The method automatically splits the data into chunks that fit within the current MTU size
     * and writes them sequentially. If the value is small enough to fit in a single write
     * (within MTU - 3 bytes for ATT header), it delegates to regular write.
     *
     * ## Usage Example
     *
     * ```kotlin
     * val firmwareData = readFirmwareFile()
     * connection.writeLongCharacteristicResult(serviceUuid, charUuid, firmwareData)
     *     .onSuccess {
     *         Log.i("BLE", "Firmware upload complete")
     *     }
     *     .onFailure { error ->
     *         when (error) {
     *             is BleCharacteristicException -> {
     *                 Log.e("BLE", "Write failed with GATT status: ${error.gattStatus}")
     *             }
     *             else -> Log.e("BLE", "Error: ${error.message}")
     *         }
     *     }
     * ```
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to write to.
     * @param value The [ByteArray] value to write. Can be larger than MTU.
     * @return A [BleResult] containing either:
     *         - [BleResult.Success] with [Unit] indicating all chunks were written successfully
     *         - [BleResult.Failure] with a [BleCharacteristicException]
     *
     * @see writeLongCharacteristic
     * @see writeCharacteristicResult
     * @see BleResult
     */
    suspend fun writeLongCharacteristicResult(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray
    ): BleResult<Unit> {
        return suspendCancellableCoroutine { continuation ->
            connectionStateMachine.writeLongCharacteristic(serviceUuid, characteristicUuid, value) { status ->
                val result = if (status == BluetoothGatt.GATT_SUCCESS) {
                    bleSuccess(Unit)
                } else {
                    bleFailure(
                        BleCharacteristicException(
                            message = "Failed to write long characteristic: GATT status $status",
                            gattStatus = status,
                            characteristicUuid = characteristicUuid.toString(),
                            operation = CharacteristicOperation.WRITE
                        )
                    )
                }
                continuation.resume(result)
            }
        }
    }

    /**
     * Enables notifications for a characteristic and registers a callback for received values.
     *
     * This method configures the BLE device to send notifications when the specified
     * characteristic's value changes. The provided callback will be invoked each time
     * a notification is received from the device.
     *
     * Internally, this writes to the Client Characteristic Configuration Descriptor (CCCD)
     * with [CLIENT_CHARACTERISTIC_CONFIG_UUID] to enable notifications on the remote device.
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to enable notifications for.
     *                           The characteristic must support notifications (PROPERTY_NOTIFY).
     * @param callback The callback function invoked with the [ByteArray] value each time
     *                 a notification is received from the characteristic.
     *
     * @see disableNotifications
     * @see CLIENT_CHARACTERISTIC_CONFIG_UUID
     */
    fun enableNotifications(serviceUuid: UUID, characteristicUuid: UUID, callback: (ByteArray) -> Unit) {
        connectionStateMachine.enableNotifications(serviceUuid, characteristicUuid, callback) { status ->
            if (status != BluetoothGatt.GATT_SUCCESS) {
                io.github.romantsisyk.blex.logging.BleLog.e(io.github.romantsisyk.blex.util.Constants.TAG,
                    "Failed to enable notifications for $characteristicUuid, status=$status")
            }
        }
    }

    /**
     * Disables notifications for a characteristic.
     *
     * This method stops the BLE device from sending notifications for the specified
     * characteristic. Any previously registered callback for this characteristic will
     * no longer receive updates.
     *
     * Internally, this writes to the Client Characteristic Configuration Descriptor (CCCD)
     * with [CLIENT_CHARACTERISTIC_CONFIG_UUID] to disable notifications on the remote device.
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to disable notifications for.
     *
     * @see enableNotifications
     * @see CLIENT_CHARACTERISTIC_CONFIG_UUID
     */
    fun disableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        connectionStateMachine.disableNotifications(serviceUuid, characteristicUuid)
    }

    /**
     * Gets the list of discovered GATT services for this device.
     *
     * This method returns services from the cache if available from previous connections,
     * or from the current active GATT connection. The service cache allows faster access
     * to service information on reconnection without waiting for a new service discovery.
     *
     * The cache is populated automatically when service discovery completes successfully.
     * Use [clearServiceCache] if you need to force a fresh service discovery (e.g., after
     * a firmware update on the remote device).
     *
     * @return A list of [BluetoothGattService] objects representing the device's GATT services,
     *         or `null` if no services have been discovered yet and no cache exists.
     *
     * @see clearServiceCache
     * @see ConnectionStateMachine.getCachedServices
     */
    fun getServices(): List<BluetoothGattService>? {
        // First try to get services from the active GATT connection
        connectionStateMachine.bluetoothGatt?.services?.let { services ->
            if (services.isNotEmpty()) {
                return services
            }
        }
        // Fall back to cached services
        return connectionStateMachine.getCachedServices()
    }

    /**
     * Clears the service cache for this device.
     *
     * Call this method when you know or suspect that the remote device's GATT services
     * have changed (e.g., after a firmware update) and you want to force a fresh service
     * discovery on the next connection.
     *
     * After calling this method, the next successful connection will perform a full
     * service discovery, and the newly discovered services will be cached.
     *
     * @see getServices
     * @see ConnectionStateMachine.clearServiceCache
     */
    fun clearServiceCache() {
        ConnectionStateMachine.clearServiceCache(device.address)
    }

    companion object {
        /**
         * The standard UUID for the Client Characteristic Configuration Descriptor (CCCD).
         *
         * This descriptor is used to enable or disable notifications and indications
         * for a characteristic. It is defined by the Bluetooth SIG as part of the
         * GATT specification (0x2902).
         *
         * @see enableNotifications
         * @see disableNotifications
         */
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
