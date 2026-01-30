package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.romantsisyk.blex.logging.BleLog
import io.github.romantsisyk.blex.models.ConnectionState
import io.github.romantsisyk.blex.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A finite state machine that manages Bluetooth Low Energy (BLE) connections and operations.
 *
 * This class implements the State Machine pattern to handle the complex lifecycle of BLE connections,
 * ensuring that operations are performed in the correct sequence and providing clear state transitions
 * that can be observed via [connectionStateFlow].
 *
 * ## State Transitions
 *
 * The state machine follows this primary flow:
 * ```
 * [Disconnected] --> [Connecting] --> [Connected] --> [DiscoveringServices] --> [Ready]
 *       ^                                                                          |
 *       |                                                                          |
 *       +--------------------------------------------------------------------------+
 *                                    (on disconnect)
 * ```
 *
 * - **Disconnected**: Initial state, or after a connection is closed/lost.
 * - **Connecting**: Connection attempt is in progress via [connect].
 * - **Connected**: Physical connection established; automatically triggers service discovery.
 * - **DiscoveringServices**: GATT service discovery is in progress (implicit during Connected -> Ready).
 * - **Ready**: Services discovered successfully; the device is ready for read/write/notify operations.
 * - **Error**: An error occurred; contains an error message. May trigger auto-reconnect if enabled.
 *
 * ## Features
 *
 * - **Automatic Reconnection**: When [autoReconnect] is enabled, the state machine will automatically
 *   attempt to reconnect after a disconnection or error, with a configurable delay.
 * - **Operation Timeouts**: All BLE operations (MTU requests, PHY updates, read/write) have configurable
 *   timeouts to prevent indefinite waiting.
 * - **Bond Management**: Optional integration with [BondManager] for handling device pairing.
 * - **Reactive State Observation**: Connection state changes are exposed via [connectionStateFlow],
 *   allowing UI and other components to reactively observe state changes.
 *
 * ## Thread Safety
 *
 * BLE callbacks are dispatched on the Android Bluetooth thread. This class uses coroutines with
 * [Dispatchers.IO] for timeout handling and reconnection scheduling.
 *
 * ## Usage Example
 *
 * ```kotlin
 * val stateMachine = ConnectionStateMachine(
 *     context = applicationContext,
 *     device = bluetoothDevice,
 *     autoReconnect = true
 * )
 *
 * // Observe connection state
 * lifecycleScope.launch {
 *     stateMachine.connectionStateFlow.collect { state ->
 *         when (state) {
 *             is ConnectionState.Ready -> { /* Device ready for operations */ }
 *             is ConnectionState.Error -> { /* Handle error */ }
 *             else -> { /* Handle other states */ }
 *         }
 *     }
 * }
 *
 * // Initiate connection
 * stateMachine.connect()
 * ```
 *
 * @param context The Android context used for GATT connection.
 * @param device The [BluetoothDevice] to connect to.
 * @param bondManager Optional [BondManager] for handling device bonding/pairing.
 * @param autoReconnect If true, automatically attempts reconnection on disconnection or error.
 *                      Defaults to true.
 * @param reconnectDelayMs Base delay in milliseconds for exponential backoff reconnection.
 *                         The actual delay increases exponentially with each attempt.
 *                         Defaults to [DEFAULT_RECONNECT_DELAY_MS] (1000ms).
 * @param maxReconnectDelayMs Maximum delay in milliseconds between reconnection attempts.
 *                            The exponential backoff will be capped at this value.
 *                            Defaults to [DEFAULT_MAX_RECONNECT_DELAY_MS] (30000ms).
 * @param maxReconnectAttempts Maximum number of reconnection attempts before giving up.
 *                             When this limit is reached, the state transitions to [ConnectionState.Error].
 *                             Defaults to [DEFAULT_MAX_RECONNECT_ATTEMPTS] (10).
 * @param operationTimeoutMs Timeout in milliseconds for BLE operations (MTU, PHY, read, write).
 *                           Defaults to [DEFAULT_OPERATION_TIMEOUT_MS] (10000ms).
 *
 * @see ConnectionState
 * @see BondManager
 * @see BleGattCallback
 */
class ConnectionStateMachine(
    private val context: Context,
    private val device: BluetoothDevice,
    private val bondManager: BondManager? = null,
    private val autoReconnect: Boolean = true,
    private val reconnectDelayMs: Long = DEFAULT_RECONNECT_DELAY_MS,
    private val maxReconnectDelayMs: Long = DEFAULT_MAX_RECONNECT_DELAY_MS,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    private val operationTimeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS
) {
    /**
     * The underlying [BluetoothGatt] instance for this connection.
     *
     * This is `null` when disconnected or before [connect] is called.
     * Access is provided for advanced use cases where direct GATT operations are needed,
     * but prefer using the high-level methods like [readCharacteristic], [writeCharacteristic],
     * and [enableNotifications] when possible.
     */
    var bluetoothGatt: BluetoothGatt? = null
        private set

    /**
     * The current negotiated MTU (Maximum Transmission Unit) for this connection.
     *
     * This value is updated when an MTU change is successfully negotiated via [requestMtu].
     * If no MTU has been negotiated, this will be `null`, and the default BLE MTU of 23 bytes
     * will be assumed for operations like [writeLongCharacteristic].
     *
     * The effective payload size for characteristics is MTU - 3 bytes (ATT header overhead).
     */
    private var currentMtu: Int? = null

    private var reconnectJob: Job? = null

    /**
     * Tracks the current reconnection attempt number for exponential backoff.
     *
     * This counter is incremented with each reconnection attempt and is reset to 0
     * when a successful connection is established. Used by [calculateBackoffDelay]
     * to determine the appropriate delay before the next reconnection attempt.
     */
    private var reconnectAttempt = 0

    private val _connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /**
     * A [StateFlow] that emits the current [ConnectionState] of this state machine.
     *
     * Observers can collect this flow to react to connection state changes.
     * The flow emits the current state immediately upon collection.
     *
     * Possible states:
     * - [ConnectionState.Disconnected]: Not connected to the device.
     * - [ConnectionState.Connecting]: Connection attempt in progress.
     * - [ConnectionState.Connected]: Physical connection established.
     * - [ConnectionState.DiscoveringServices]: Service discovery in progress.
     * - [ConnectionState.Ready]: Device is ready for BLE operations.
     * - [ConnectionState.Error]: An error occurred with a descriptive message.
     */
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionStateFlow

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mtuChangeHandler = ConcurrentHashMap<String, (Int, Int) -> Unit>()
    private val phyUpdateHandler = ConcurrentHashMap<String, (Int, Int, Int) -> Unit>()
    private val readCharacteristicHandler = ConcurrentHashMap<String, (Int, ByteArray) -> Unit>()
    private val writeCharacteristicHandler = ConcurrentHashMap<String, (Int) -> Unit>()
    private val notificationHandler = ConcurrentHashMap<UUID, (ByteArray) -> Unit>()
    private val descriptorWriteHandler = ConcurrentHashMap<String, (Int) -> Unit>()

    // Timeout jobs for pending operations
    private val operationTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val callback = BleGattCallback(this, bondManager)

    /**
     * Initiates a connection to the Bluetooth device.
     *
     * This method transitions the state machine from [ConnectionState.Disconnected] to
     * [ConnectionState.Connecting]. If the connection is successful, the state will
     * automatically progress through [ConnectionState.Connected] and then to
     * [ConnectionState.Ready] after service discovery completes.
     *
     * If already connected or connecting, this method logs a warning and returns without
     * taking any action.
     *
     * The connection attempt uses `autoConnect = false`, meaning it will try to connect
     * immediately rather than waiting for the device to become available.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see disconnect
     * @see close
     * @see connectionStateFlow
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        if (_connectionStateFlow.value is ConnectionState.Connected ||
            _connectionStateFlow.value is ConnectionState.Connecting
        ) {
            BleLog.w(Constants.TAG, "Already connected or connecting to ${device.address}")
            return
        }
        transitionTo(ConnectionState.Connecting)
        bluetoothGatt = device.connectGatt(context, false, callback)
    }

    /**
     * Gracefully disconnects from the Bluetooth device.
     *
     * This triggers a disconnection that will result in a callback to
     * [handleConnectionStateChange] with [BluetoothProfile.STATE_DISCONNECTED].
     * The state machine will transition to [ConnectionState.Disconnected].
     *
     * If [autoReconnect] is enabled, a reconnection will be scheduled after
     * [reconnectDelayMs] milliseconds. Use [close] instead if you want to
     * permanently disconnect without auto-reconnection.
     *
     * This method is safe to call even if not currently connected.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see close
     * @see connect
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    /**
     * Closes the GATT connection and releases all associated resources.
     *
     * This method performs a complete cleanup:
     * - Cancels any pending reconnection attempts.
     * - Cancels all pending operation timeouts.
     * - Closes the [BluetoothGatt] instance.
     * - Transitions to [ConnectionState.Disconnected].
     *
     * Unlike [disconnect], this method does NOT trigger auto-reconnection,
     * making it suitable for permanent disconnection scenarios.
     *
     * After calling this method, you must call [connect] again to re-establish
     * a connection. The [bluetoothGatt] property will be null after this call.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see disconnect
     */
    @SuppressLint("MissingPermission")
    fun close() {
        reconnectJob?.cancel()
        cancelAllOperationTimeouts()
        bluetoothGatt?.close()
        bluetoothGatt = null
        transitionTo(ConnectionState.Disconnected)
    }

    /**
     * Requests an MTU (Maximum Transmission Unit) change with timeout handling.
     * @param mtu The desired MTU size (must be between 23 and 517)
     * @param callback Called with (negotiatedMtu, status) when the operation completes or times out
     */
    @SuppressLint("MissingPermission")
    fun requestMtu(mtu: Int, callback: (Int, Int) -> Unit) {
        val gatt = bluetoothGatt ?: run {
            callback(0, BluetoothGatt.GATT_FAILURE)
            return
        }
        val key = "mtu_request"
        mtuChangeHandler[key] = callback
        if (!gatt.requestMtu(mtu)) {
            mtuChangeHandler.remove(key)
            callback(0, BluetoothGatt.GATT_FAILURE)
        } else {
            startOperationTimeout(key) {
                mtuChangeHandler.remove(key)
                BleLog.e(Constants.TAG, "MTU request timeout")
                callback(0, TIMEOUT_ERROR_STATUS)
            }
        }
    }

    /**
     * Requests a PHY (Physical Layer) update with timeout handling.
     * @param txPhy Preferred TX PHY (e.g., BluetoothDevice.PHY_LE_1M, PHY_LE_2M, PHY_LE_CODED)
     * @param rxPhy Preferred RX PHY
     * @param phyOptions PHY options for coded PHY (e.g., BluetoothDevice.PHY_OPTION_NO_PREFERRED)
     * @param callback Called with (txPhy, rxPhy, status) when the operation completes or times out
     */
    @SuppressLint("MissingPermission")
    fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int, callback: (Int, Int, Int) -> Unit) {
        val gatt = bluetoothGatt ?: run {
            callback(0, 0, BluetoothGatt.GATT_FAILURE)
            return
        }
        val key = "phy_update"
        phyUpdateHandler[key] = callback
        gatt.setPreferredPhy(txPhy, rxPhy, phyOptions)
        startOperationTimeout(key) {
            phyUpdateHandler.remove(key)
            BleLog.e(Constants.TAG, "PHY update timeout")
            callback(0, 0, TIMEOUT_ERROR_STATUS)
        }
    }

    @SuppressLint("MissingPermission")
    internal fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            handleError("Connection failed with status: $status")
            return
        }
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                reconnectAttempt = 0 // Reset on successful connection
                transitionTo(ConnectionState.Connected)
                gatt.discoverServices()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                transitionTo(ConnectionState.Disconnected)
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    internal fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Cache the discovered services for faster reconnection
            serviceCache[device.address] = gatt.services.toList()
            BleLog.d(Constants.TAG, "Cached ${gatt.services.size} services for ${device.address}")
            transitionTo(ConnectionState.Ready)
        } else {
            handleError("Service discovery failed with status: $status")
        }
    }

    internal fun handleMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            currentMtu = mtu
        }
        val key = "mtu_request"
        cancelOperationTimeout(key)
        mtuChangeHandler[key]?.invoke(mtu, status)
        mtuChangeHandler.remove(key)
    }

    internal fun handlePhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        val key = "phy_update"
        cancelOperationTimeout(key)
        phyUpdateHandler[key]?.invoke(txPhy, rxPhy, status)
        phyUpdateHandler.remove(key)
    }

    internal fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = "read_${characteristic.uuid}"
        cancelOperationTimeout(key)
        @Suppress("DEPRECATION")
        readCharacteristicHandler[characteristic.uuid.toString()]?.invoke(status, characteristic.value)
        readCharacteristicHandler.remove(characteristic.uuid.toString())
    }

    internal fun handleCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = "write_${characteristic.uuid}"
        cancelOperationTimeout(key)
        writeCharacteristicHandler[characteristic.uuid.toString()]?.invoke(status)
        writeCharacteristicHandler.remove(characteristic.uuid.toString())
    }

    internal fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        @Suppress("DEPRECATION")
        notificationHandler[characteristic.uuid]?.invoke(characteristic.value)
    }

    /**
     * Handles characteristic changed events for API 33+.
     * The value is passed directly instead of being read from the characteristic.
     */
    internal fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        notificationHandler[characteristic.uuid]?.invoke(value)
    }

    internal fun handleDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        val key = descriptor.uuid.toString()
        descriptorWriteHandler[key]?.invoke(status)
        descriptorWriteHandler.remove(key)
    }

    /**
     * Reads the value of a GATT characteristic from the connected device.
     *
     * This is an asynchronous operation. The result is delivered via the [callback]
     * parameter when the read completes or times out.
     *
     * The state machine must be in [ConnectionState.Ready] for this operation to succeed.
     * The method will look up the service and characteristic by their UUIDs from the
     * discovered services.
     *
     * A timeout is automatically applied to this operation. If the device does not respond
     * within [operationTimeoutMs], the callback will be invoked with [TIMEOUT_ERROR_STATUS].
     *
     * @param serviceUuid The UUID of the GATT service containing the characteristic.
     * @param characteristicUuid The UUID of the characteristic to read.
     * @param callback Invoked with (status, value) when the operation completes.
     *                 - status: [BluetoothGatt.GATT_SUCCESS] on success, or an error code.
     *                 - value: The characteristic value as a byte array, or null on failure.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see writeCharacteristic
     * @see enableNotifications
     */
    @SuppressLint("MissingPermission")
    fun readCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        callback: (Int, ByteArray?) -> Unit
    ) {
        val gatt = bluetoothGatt ?: run {
            callback(BluetoothGatt.GATT_FAILURE, null)
            return
        }
        val service = gatt.getService(serviceUuid) ?: run {
            BleLog.e(Constants.TAG, "Service $serviceUuid not found")
            callback(BluetoothGatt.GATT_FAILURE, null)
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            BleLog.e(Constants.TAG, "Characteristic $characteristicUuid not found")
            callback(BluetoothGatt.GATT_FAILURE, null)
            return
        }
        val handlerKey = characteristicUuid.toString()
        val timeoutKey = "read_$characteristicUuid"
        readCharacteristicHandler[handlerKey] = { status, value ->
            callback(status, value)
        }
        if (!gatt.readCharacteristic(characteristic)) {
            readCharacteristicHandler.remove(handlerKey)
            callback(BluetoothGatt.GATT_FAILURE, null)
        } else {
            startOperationTimeout(timeoutKey) {
                readCharacteristicHandler.remove(handlerKey)
                BleLog.e(Constants.TAG, "Read characteristic timeout for $characteristicUuid")
                callback(TIMEOUT_ERROR_STATUS, null)
            }
        }
    }

    /**
     * Writes a value to a GATT characteristic on the connected device.
     *
     * This is an asynchronous operation. The result is delivered via the [callback]
     * parameter when the write completes or times out.
     *
     * The state machine must be in [ConnectionState.Ready] for this operation to succeed.
     * The method will look up the service and characteristic by their UUIDs from the
     * discovered services.
     *
     * A timeout is automatically applied to this operation. If the device does not respond
     * within [operationTimeoutMs], the callback will be invoked with [TIMEOUT_ERROR_STATUS].
     *
     * Note: This method uses the deprecated `characteristic.value` setter for compatibility.
     * For Android 13+ (API 33+), consider using the newer write methods if available.
     *
     * @param serviceUuid The UUID of the GATT service containing the characteristic.
     * @param characteristicUuid The UUID of the characteristic to write to.
     * @param value The byte array value to write to the characteristic.
     * @param callback Invoked with the status code when the operation completes.
     *                 [BluetoothGatt.GATT_SUCCESS] indicates success.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see readCharacteristic
     * @see enableNotifications
     */
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray,
        callback: (Int) -> Unit
    ) {
        val gatt = bluetoothGatt ?: run {
            callback(BluetoothGatt.GATT_FAILURE)
            return
        }
        val service = gatt.getService(serviceUuid) ?: run {
            BleLog.e(Constants.TAG, "Service $serviceUuid not found")
            callback(BluetoothGatt.GATT_FAILURE)
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            BleLog.e(Constants.TAG, "Characteristic $characteristicUuid not found")
            callback(BluetoothGatt.GATT_FAILURE)
            return
        }
        @Suppress("DEPRECATION")
        characteristic.value = value
        val handlerKey = characteristicUuid.toString()
        val timeoutKey = "write_$characteristicUuid"
        writeCharacteristicHandler[handlerKey] = callback
        @Suppress("DEPRECATION")
        if (!gatt.writeCharacteristic(characteristic)) {
            writeCharacteristicHandler.remove(handlerKey)
            callback(BluetoothGatt.GATT_FAILURE)
        } else {
            startOperationTimeout(timeoutKey) {
                writeCharacteristicHandler.remove(handlerKey)
                BleLog.e(Constants.TAG, "Write characteristic timeout for $characteristicUuid")
                callback(TIMEOUT_ERROR_STATUS)
            }
        }
    }

    /**
     * Writes a large value to a characteristic using sequential chunked writes.
     *
     * This method automatically splits the data into chunks that fit within the current
     * MTU size and writes them sequentially. If the value is small enough to fit in a
     * single write (within MTU - 3 bytes for ATT header), it delegates to the regular
     * [writeCharacteristic] method.
     *
     * This is useful for writing values larger than the negotiated MTU, such as firmware
     * updates, large configuration blobs, or any data that exceeds the maximum single-write
     * payload size.
     *
     * The state machine must be in [ConnectionState.Ready] for this operation to succeed.
     * Each chunk write has its own timeout applied via [operationTimeoutMs].
     *
     * **Note**: This implementation writes chunks sequentially using standard write operations.
     * It does NOT use BLE Reliable Write (prepared writes with execute), which would provide
     * atomicity but requires different device-side handling. For most use cases, sequential
     * writes are sufficient and more widely supported.
     *
     * ## Chunk Size Calculation
     *
     * - Default MTU: 23 bytes (if no MTU negotiation has occurred)
     * - Chunk size: MTU - 3 bytes (ATT protocol header)
     * - Example: With MTU of 247, chunk size is 244 bytes
     *
     * @param serviceUuid The UUID of the GATT service containing the characteristic.
     * @param characteristicUuid The UUID of the characteristic to write to.
     * @param value The full byte array value to write. Can be larger than MTU.
     * @param callback Invoked with the final status code when all chunks have been written
     *                 or when an error occurs. [BluetoothGatt.GATT_SUCCESS] indicates all
     *                 chunks were written successfully.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see writeCharacteristic
     * @see requestMtu
     */
    @SuppressLint("MissingPermission")
    fun writeLongCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray,
        callback: (Int) -> Unit
    ) {
        if (bluetoothGatt == null) {
            callback(BluetoothGatt.GATT_FAILURE)
            return
        }

        // Get effective MTU (MTU - 3 for ATT header)
        val mtu = currentMtu ?: DEFAULT_MTU
        val chunkSize = mtu - ATT_HEADER_SIZE

        if (value.size <= chunkSize) {
            // Use regular write for small values
            writeCharacteristic(serviceUuid, characteristicUuid, value, callback)
            return
        }

        // Split into chunks and write sequentially
        val chunks = value.toList().chunked(chunkSize).map { it.toByteArray() }
        BleLog.d(Constants.TAG, "Writing ${value.size} bytes in ${chunks.size} chunks (MTU=$mtu, chunkSize=$chunkSize)")
        writeChunksSequentially(serviceUuid, characteristicUuid, chunks, 0, callback)
    }

    /**
     * Recursively writes chunks of data to a characteristic.
     *
     * This helper method writes chunks one at a time, waiting for each write to complete
     * before starting the next. If any chunk fails, the entire operation is aborted and
     * the error status is passed to the callback.
     *
     * @param serviceUuid The UUID of the GATT service.
     * @param characteristicUuid The UUID of the characteristic.
     * @param chunks The list of data chunks to write.
     * @param index The current chunk index being written.
     * @param finalCallback Called with the final status when all chunks complete or on error.
     */
    private fun writeChunksSequentially(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        chunks: List<ByteArray>,
        index: Int,
        finalCallback: (Int) -> Unit
    ) {
        if (index >= chunks.size) {
            BleLog.d(Constants.TAG, "All ${chunks.size} chunks written successfully")
            finalCallback(BluetoothGatt.GATT_SUCCESS)
            return
        }

        BleLog.d(Constants.TAG, "Writing chunk ${index + 1}/${chunks.size} (${chunks[index].size} bytes)")
        writeCharacteristic(serviceUuid, characteristicUuid, chunks[index]) { status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                writeChunksSequentially(serviceUuid, characteristicUuid, chunks, index + 1, finalCallback)
            } else {
                BleLog.e(Constants.TAG, "Chunk write failed at index $index with status $status")
                finalCallback(status)
            }
        }
    }

    /**
     * Enables notifications for a GATT characteristic.
     *
     * This method performs the following steps:
     * 1. Enables local notification listening via [BluetoothGatt.setCharacteristicNotification].
     * 2. Writes the [BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE] to the Client
     *    Characteristic Configuration Descriptor (CCCD) to enable server-side notifications.
     *
     * Once enabled, the [onNotification] callback will be invoked each time the device
     * sends a notification with the updated characteristic value.
     *
     * The state machine must be in [ConnectionState.Ready] for this operation to succeed.
     * The characteristic must support notifications (have a CCCD descriptor).
     *
     * @param serviceUuid The UUID of the GATT service containing the characteristic.
     * @param characteristicUuid The UUID of the characteristic to enable notifications for.
     * @param onNotification Callback invoked with the new value each time a notification is received.
     *                       This callback may be called multiple times until notifications are disabled.
     * @param onComplete Invoked once when the notification setup completes.
     *                   [BluetoothGatt.GATT_SUCCESS] indicates notifications are now active.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see disableNotifications
     * @see CLIENT_CHARACTERISTIC_CONFIG_UUID
     */
    @SuppressLint("MissingPermission")
    fun enableNotifications(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        onNotification: (ByteArray) -> Unit,
        onComplete: (Int) -> Unit
    ) {
        val gatt = bluetoothGatt ?: run {
            onComplete(BluetoothGatt.GATT_FAILURE)
            return
        }
        val service = gatt.getService(serviceUuid) ?: run {
            BleLog.e(Constants.TAG, "Service $serviceUuid not found")
            onComplete(BluetoothGatt.GATT_FAILURE)
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            BleLog.e(Constants.TAG, "Characteristic $characteristicUuid not found")
            onComplete(BluetoothGatt.GATT_FAILURE)
            return
        }
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            BleLog.e(Constants.TAG, "Failed to set characteristic notification")
            onComplete(BluetoothGatt.GATT_FAILURE)
            return
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: run {
            BleLog.e(Constants.TAG, "CCCD descriptor not found for $characteristicUuid")
            onComplete(BluetoothGatt.GATT_FAILURE)
            return
        }
        @Suppress("DEPRECATION")
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val descriptorKey = CLIENT_CHARACTERISTIC_CONFIG_UUID.toString()
        descriptorWriteHandler[descriptorKey] = { status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notificationHandler[characteristicUuid] = onNotification
            }
            onComplete(status)
        }
        @Suppress("DEPRECATION")
        if (!gatt.writeDescriptor(descriptor)) {
            descriptorWriteHandler.remove(descriptorKey)
            onComplete(BluetoothGatt.GATT_FAILURE)
        }
    }

    /**
     * Disables notifications for a GATT characteristic.
     *
     * This method:
     * 1. Removes the local notification handler for the characteristic.
     * 2. Disables local notification listening via [BluetoothGatt.setCharacteristicNotification].
     * 3. Writes [BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE] to the CCCD to disable
     *    server-side notifications.
     *
     * After this method completes successfully, no more notification callbacks will be
     * received for this characteristic.
     *
     * @param serviceUuid The UUID of the GATT service containing the characteristic.
     * @param characteristicUuid The UUID of the characteristic to disable notifications for.
     * @param onComplete Optional callback invoked when the operation completes.
     *                   [BluetoothGatt.GATT_SUCCESS] indicates notifications are now disabled.
     *                   If null, the operation is fire-and-forget.
     *
     * @throws SecurityException if Bluetooth permissions are not granted.
     *
     * @see enableNotifications
     */
    @SuppressLint("MissingPermission")
    fun disableNotifications(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        onComplete: ((Int) -> Unit)? = null
    ) {
        notificationHandler.remove(characteristicUuid)
        val gatt = bluetoothGatt ?: run {
            onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
            return
        }
        val service = gatt.getService(serviceUuid) ?: run {
            BleLog.e(Constants.TAG, "Service $serviceUuid not found")
            onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
            return
        }
        val characteristic = service.getCharacteristic(characteristicUuid) ?: run {
            BleLog.e(Constants.TAG, "Characteristic $characteristicUuid not found")
            onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
            return
        }
        gatt.setCharacteristicNotification(characteristic, false)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor != null) {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            val descriptorKey = CLIENT_CHARACTERISTIC_CONFIG_UUID.toString()
            if (onComplete != null) {
                descriptorWriteHandler[descriptorKey] = onComplete
            }
            @Suppress("DEPRECATION")
            if (!gatt.writeDescriptor(descriptor)) {
                descriptorWriteHandler.remove(descriptorKey)
                onComplete?.invoke(BluetoothGatt.GATT_FAILURE)
            }
        } else {
            onComplete?.invoke(BluetoothGatt.GATT_SUCCESS)
        }
    }

    /**
     * Gets the list of cached GATT services for this device.
     *
     * This method returns services that were discovered during a previous connection
     * and cached for faster reconnection. The cache persists across connection cycles,
     * allowing the application to access service information without waiting for a
     * new service discovery operation.
     *
     * Note: The cached services are snapshots from the last successful service discovery.
     * If the remote device's services have changed (e.g., firmware update), call
     * [clearServiceCache] to force a fresh discovery on the next connection.
     *
     * @return A list of [BluetoothGattService] objects if services have been cached
     *         for this device, or `null` if no cache exists.
     *
     * @see hasServiceCache
     * @see clearServiceCache
     */
    fun getCachedServices(): List<BluetoothGattService>? {
        return serviceCache[device.address]
    }

    /**
     * Checks whether services are cached for this device.
     *
     * This is a quick check to determine if a previous connection has cached
     * the device's GATT services. When services are cached, reconnection can
     * be faster as the application may be able to use cached service information.
     *
     * @return `true` if services are cached for this device's address, `false` otherwise.
     *
     * @see getCachedServices
     * @see clearServiceCache
     */
    fun hasServiceCache(): Boolean {
        return serviceCache.containsKey(device.address)
    }

    /**
     * Companion object containing default configuration values, standard BLE UUIDs,
     * and the global service cache.
     */
    companion object {
        /**
         * Default base delay in milliseconds for exponential backoff reconnection.
         *
         * This is the initial delay before the first reconnection attempt. Subsequent
         * attempts use exponentially increasing delays up to [DEFAULT_MAX_RECONNECT_DELAY_MS].
         */
        const val DEFAULT_RECONNECT_DELAY_MS = 1000L

        /**
         * Default maximum delay in milliseconds between reconnection attempts.
         *
         * The exponential backoff delay is capped at this value to prevent
         * excessively long waits between reconnection attempts.
         */
        const val DEFAULT_MAX_RECONNECT_DELAY_MS = 30_000L

        /**
         * Default maximum number of reconnection attempts before giving up.
         *
         * After this many failed reconnection attempts, the state machine will
         * transition to [ConnectionState.Error] and stop trying to reconnect.
         */
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 10

        /**
         * Default timeout in milliseconds for BLE operations (MTU, PHY, read, write).
         *
         * If a BLE operation does not receive a response within this time,
         * the operation will be considered failed and the callback will be
         * invoked with [TIMEOUT_ERROR_STATUS].
         */
        const val DEFAULT_OPERATION_TIMEOUT_MS = 10_000L

        /**
         * Default BLE MTU (Maximum Transmission Unit) size in bytes.
         *
         * This is the minimum MTU size guaranteed by the BLE specification.
         * If no MTU negotiation has occurred, this value is used for
         * [writeLongCharacteristic] chunk size calculations.
         */
        private const val DEFAULT_MTU = 23

        /**
         * ATT (Attribute Protocol) header size in bytes.
         *
         * The ATT protocol header consumes 3 bytes of the MTU, leaving
         * (MTU - 3) bytes available for the characteristic value payload.
         * This is used when calculating chunk sizes for [writeLongCharacteristic].
         */
        private const val ATT_HEADER_SIZE = 3

        /**
         * Custom GATT status code indicating an operation timed out.
         *
         * This is a non-standard status code (0x85 / 133) used internally to
         * distinguish timeout failures from other GATT errors. Callbacks receiving
         * this status should treat it as a failure and may retry the operation.
         */
        private const val TIMEOUT_ERROR_STATUS = 0x85

        /**
         * The standard UUID for the Client Characteristic Configuration Descriptor (CCCD).
         *
         * This descriptor is defined by the Bluetooth specification and is used to
         * enable or disable notifications/indications on a characteristic. The value
         * `00002902-0000-1000-8000-00805f9b34fb` is the 128-bit representation of the
         * 16-bit UUID `0x2902`.
         *
         * @see enableNotifications
         * @see disableNotifications
         */
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Global cache of discovered GATT services, keyed by device address.
         *
         * This cache persists across connection cycles and allows faster reconnection
         * by avoiding repeated service discovery when the device's services haven't changed.
         * The cache is shared across all [ConnectionStateMachine] instances.
         *
         * Use [clearServiceCache] to invalidate the cache for a specific device or all devices.
         *
         * @see getCachedServices
         * @see hasServiceCache
         * @see clearServiceCache
         */
        private val serviceCache = ConcurrentHashMap<String, List<BluetoothGattService>>()

        /**
         * Clears the service cache for a specific device or all devices.
         *
         * Call this method when you know or suspect that a device's GATT services
         * have changed (e.g., after a firmware update) and you want to force a
         * fresh service discovery on the next connection.
         *
         * This is a static method that affects the global service cache shared
         * by all [ConnectionStateMachine] instances.
         *
         * @param deviceAddress The MAC address of the device whose cache should be cleared.
         *                      If `null`, clears the cache for all devices.
         *
         * @see getCachedServices
         * @see hasServiceCache
         */
        fun clearServiceCache(deviceAddress: String? = null) {
            if (deviceAddress != null) {
                serviceCache.remove(deviceAddress)
                BleLog.d(Constants.TAG, "Cleared service cache for device: $deviceAddress")
            } else {
                serviceCache.clear()
                BleLog.d(Constants.TAG, "Cleared service cache for all devices")
            }
        }
    }

    private fun transitionTo(newState: ConnectionState) {
        BleLog.d(Constants.TAG, "Transitioning to $newState")
        _connectionStateFlow.value = newState
    }

    private fun handleError(message: String) {
        BleLog.e(Constants.TAG, "Error on device ${device.address}: $message")
        transitionTo(ConnectionState.Error("Device ${device.address}: $message"))
        if (autoReconnect) {
            scheduleReconnect()
        }
    }

    /**
     * Schedules a reconnection attempt using exponential backoff with jitter.
     *
     * The delay before reconnection is calculated as:
     * - Base formula: `baseDelay * 2^attempt` (exponential growth)
     * - Capped at [maxReconnectDelayMs] to prevent excessively long waits
     * - Jitter of +/-20% is applied to prevent thundering herd problems
     *
     * If the maximum number of reconnection attempts ([maxReconnectAttempts]) is reached,
     * the state machine transitions to [ConnectionState.Error] and stops attempting
     * to reconnect. Call [resetReconnectAttempts] to manually reset the counter and
     * retry, or call [connect] to start fresh.
     *
     * @see calculateBackoffDelay
     * @see resetReconnectAttempts
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        if (reconnectAttempt >= maxReconnectAttempts) {
            BleLog.w(Constants.TAG, "Max reconnect attempts ($maxReconnectAttempts) reached for ${device.address}")
            transitionTo(ConnectionState.Error("Max reconnect attempts reached for ${device.address}"))
            return
        }

        reconnectJob = coroutineScope.launch {
            val delayMs = calculateBackoffDelay()
            BleLog.d(Constants.TAG, "Reconnect attempt ${reconnectAttempt + 1}/$maxReconnectAttempts in ${delayMs}ms for ${device.address}")
            delay(delayMs)
            reconnectAttempt++
            connect()
        }
    }

    /**
     * Calculates the delay before the next reconnection attempt using exponential backoff with jitter.
     *
     * The algorithm works as follows:
     * 1. **Exponential growth**: `baseDelay * 2^attempt` doubles the delay with each attempt
     * 2. **Cap**: The delay is capped at [maxReconnectDelayMs] to prevent excessive waits
     * 3. **Jitter**: A random factor of +/-20% is applied to prevent synchronized reconnection
     *    attempts from multiple devices (thundering herd problem)
     * 4. **Floor**: The final delay is never less than [reconnectDelayMs]
     *
     * Example delays with default settings (1000ms base, 30000ms max):
     * - Attempt 0: ~1000ms (800-1200ms with jitter)
     * - Attempt 1: ~2000ms (1600-2400ms with jitter)
     * - Attempt 2: ~4000ms (3200-4800ms with jitter)
     * - Attempt 3: ~8000ms (6400-9600ms with jitter)
     * - Attempt 4: ~16000ms (12800-19200ms with jitter)
     * - Attempt 5+: ~30000ms (24000-36000ms capped with jitter)
     *
     * @return The calculated delay in milliseconds before the next reconnection attempt.
     */
    private fun calculateBackoffDelay(): Long {
        // Exponential backoff: baseDelay * 2^attempt
        // Coerce attempt to max 10 to prevent overflow with bit shift
        val exponentialDelay = reconnectDelayMs * (1L shl reconnectAttempt.coerceAtMost(10))
        val cappedDelay = exponentialDelay.coerceAtMost(maxReconnectDelayMs)
        // Add jitter: +/-20% to prevent thundering herd
        val jitter = (cappedDelay * 0.2 * (Math.random() * 2 - 1)).toLong()
        return (cappedDelay + jitter).coerceAtLeast(reconnectDelayMs)
    }

    /**
     * Resets the reconnect attempt counter to zero.
     *
     * Call this method to allow the state machine to attempt reconnection again
     * after it has reached the maximum number of attempts and transitioned to
     * [ConnectionState.Error]. This is useful when:
     *
     * - The user manually triggers a retry
     * - External conditions have changed (e.g., Bluetooth was toggled)
     * - A significant amount of time has passed
     *
     * Note that [connect] can be called directly without resetting the counter;
     * the counter is automatically reset when a connection succeeds.
     *
     * @see scheduleReconnect
     * @see maxReconnectAttempts
     */
    fun resetReconnectAttempts() {
        reconnectAttempt = 0
        BleLog.d(Constants.TAG, "Reconnect attempts reset for ${device.address}")
    }

    /**
     * Starts a timeout job for a BLE operation.
     * When the timeout expires, the onTimeout callback is invoked to clean up handlers and report error.
     * @param key Unique identifier for this operation
     * @param onTimeout Callback invoked when timeout occurs
     */
    private fun startOperationTimeout(key: String, onTimeout: () -> Unit) {
        // Cancel any existing timeout for this key
        operationTimeoutJobs[key]?.cancel()

        operationTimeoutJobs[key] = coroutineScope.launch {
            delay(operationTimeoutMs)
            BleLog.w(Constants.TAG, "Operation timeout for key: $key")
            operationTimeoutJobs.remove(key)
            onTimeout()
        }
    }

    /**
     * Cancels the timeout job for a specific operation.
     * Should be called when the operation completes successfully or with an error from the device.
     * @param key Unique identifier for the operation
     */
    private fun cancelOperationTimeout(key: String) {
        operationTimeoutJobs[key]?.cancel()
        operationTimeoutJobs.remove(key)
    }

    /**
     * Cancels all pending operation timeout jobs.
     * Called during close() to clean up resources.
     */
    private fun cancelAllOperationTimeouts() {
        operationTimeoutJobs.values.forEach { it.cancel() }
        operationTimeoutJobs.clear()
    }
}