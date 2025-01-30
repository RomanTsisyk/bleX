package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.util.Log
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.coroutines.resumeWithException

class BleConnection(
    context: Context,
    private val device: BluetoothDevice,
    private val bondManager: BondManager? = null
) {

    private val connectionStateMachine = ConnectionStateMachine(context, device, bondManager)
    val connectionStateFlow: StateFlow<ConnectionState> = connectionStateMachine.connectionStateFlow

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    init {
        // Start listening to connection states
        coroutineScope.launch {
            connectionStateFlow.collect { state ->
                // Handle state changes if needed
                when (state) {
                    is ConnectionState.Ready -> Unit
                    is ConnectionState.Error -> {
                        Log.e(Constants.TAG, "Connection error: ${state.message}")
                    }

                    else -> Unit
                }
            }
        }
    }

    /**
     * Initiates the connection process.
     */
    fun connect() {
        connectionStateMachine.connect()
    }

    /**
     * Disconnects from the BLE device.
     */
    fun disconnect() {
        connectionStateMachine.disconnect()
    }

    /**
     * Closes the connection and cleans up resources.
     */
    fun close() {
        connectionStateMachine.close()
    }

    /**
     * Requests an MTU size.
     *
     * @param desiredMtu The desired MTU size.
     * @return The negotiated MTU size.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun requestMtu(desiredMtu: Int): Int = suspendCancellableCoroutine { continuation ->
        val gatt = bGatt

        val key = "mtu_request"

        connectionStateMachine.mtuChangeCallbacks[key] = { mtu, status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(mtu, null)
            } else {
                continuation.resumeWithException(
                    RuntimeException("MTU negotiation failed with status $status")
                )
            }
            connectionStateMachine.mtuChangeCallbacks.remove(key)
        }

        val success = gatt.requestMtu(desiredMtu)
        if (!success) {
            connectionStateMachine.mtuChangeCallbacks.remove(key)
            continuation.resumeWithException(RuntimeException("Failed to initiate MTU request."))
        }

        continuation.invokeOnCancellation {
            connectionStateMachine.mtuChangeCallbacks.remove(key)
        }
    }

    /**
     * Sets the preferred PHY for the connection.
     *
     * @param txPhy Transmission PHY.
     * @param rxPhy Reception PHY.
     * @param phyOptions PHY options.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun setPreferredPhy(
        txPhy: Int = BluetoothDevice.PHY_LE_2M,
        rxPhy: Int = BluetoothDevice.PHY_LE_2M,
        phyOptions: Int = BluetoothDevice.PHY_OPTION_NO_PREFERRED
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val gatt = bGatt

        val key = "phy_update"

        connectionStateMachine.phyUpdateCallbacks[key] = { tx, rx, status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(Unit, null)
            } else {
                continuation.resumeWithException(
                    RuntimeException("PHY update failed with status: $status")
                )
            }
            connectionStateMachine.phyUpdateCallbacks.remove(key)
        }

        try {
            gatt.setPreferredPhy(txPhy, rxPhy, phyOptions)
        } catch (e: Exception) {
            connectionStateMachine.phyUpdateCallbacks.remove(key)
            continuation.resumeWithException(
                RuntimeException("Failed to initiate PHY update: ${e.message}", e)
            )
        }

        continuation.invokeOnCancellation {
            connectionStateMachine.phyUpdateCallbacks.remove(key)
        }
    }

    /**
     * Reads a characteristic's value.
     *
     * @param characteristic The characteristic to read.
     * @return The value as a ByteArray.
     */
    @SuppressLint("MissingPermission")
    suspend fun readCharacteristic(characteristic: BluetoothGattCharacteristic): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val gatt = bGatt

            // Use a unique key for tracking
            val key = characteristic.uuid.toString()
            connectionStateMachine.readCharacteristicCallbacks[key] = { status, value ->
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    continuation.resume(value, null)
                } else {
                    continuation.resumeWithException(
                        RuntimeException("Characteristic read failed with status: $status")
                    )
                }
                connectionStateMachine.readCharacteristicCallbacks.remove(key)
            }

            val success = gatt.readCharacteristic(characteristic)
            if (!success) {
                connectionStateMachine.readCharacteristicCallbacks.remove(key)
                continuation.resumeWithException(RuntimeException("Failed to initiate characteristic read."))
            }

            continuation.invokeOnCancellation {
                connectionStateMachine.readCharacteristicCallbacks.remove(key)
            }
        }

    /**
     * Writes a value to a characteristic.
     *
     * @param characteristic The characteristic to write to.
     * @param value The value to write.
     */
    @SuppressLint("MissingPermission")
    suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val gatt = bGatt

        characteristic.value = value

        // Use a unique key for tracking
        val key = characteristic.uuid.toString()
        connectionStateMachine.writeCharacteristicCallbacks[key] = { status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continuation.resume(Unit, null)
            } else {
                continuation.resumeWithException(
                    RuntimeException("Characteristic write failed with status: $status")
                )
            }
            connectionStateMachine.writeCharacteristicCallbacks.remove(key)
        }

        val success = gatt.writeCharacteristic(characteristic)
        if (!success) {
            connectionStateMachine.writeCharacteristicCallbacks.remove(key)
            continuation.resumeWithException(RuntimeException("Failed to initiate characteristic write."))
        }

        continuation.invokeOnCancellation {
            connectionStateMachine.writeCharacteristicCallbacks.remove(key)
        }
    }

    /**
     * Observes notifications from a characteristic.
     *
     * @param characteristic The characteristic to observe.
     * @return Flow emitting ByteArray values from notifications.
     */
    @SuppressLint("MissingPermission")
    fun observeCharacteristicNotifications(characteristic: BluetoothGattCharacteristic): Flow<ByteArray> =
        callbackFlow {
            val gatt = bGatt

            gatt.setCharacteristicNotification(characteristic, true)

            // Configure descriptor for notifications
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                val success = gatt.writeDescriptor(descriptor)
                if (!success) {
                    close(RuntimeException("Failed to write descriptor for notifications."))
                }
            } else {
                close(RuntimeException("CCCD descriptor not found for characteristic ${characteristic.uuid}."))
            }

            // Register a callback to emit notification values
            val notificationCallback: (ByteArray) -> Unit = { value: ByteArray ->
                val result = trySend(value)
                if (!result.isSuccess) {
                    Log.e(Constants.TAG, "Failed to send notification value for characteristic ${characteristic.uuid}")
                }
            }

            connectionStateMachine.notificationCallbacks[characteristic.uuid] = notificationCallback

            awaitClose {
                gatt.setCharacteristicNotification(characteristic, false)
                connectionStateMachine.notificationCallbacks.remove(characteristic.uuid)
            }
        }

    /**
     * Bonds with the device.
     *
     * @return Flow emitting BondState changes.
     */
    fun bond(): Flow<BondState> {
        return bondManager?.bondDevice(device) ?: flow { }
    }

    private val bGatt: BluetoothGatt
        get() = connectionStateMachine.bluetoothGatt
            ?: throw IllegalStateException("BluetoothGatt is null")


    companion object {
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}