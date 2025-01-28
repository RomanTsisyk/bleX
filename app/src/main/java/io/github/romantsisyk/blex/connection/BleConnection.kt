package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
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
        val gatt = connectionStateMachine.bluetoothGatt
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                IllegalStateException("BluetoothGatt is null")
            )

        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    continuation.resume(mtu, null)
                } else {
                    continuation.resumeWithException(
                        RuntimeException("MTU negotiation failed with status $status")
                    )
                }
                gatt.setCharacteristicNotification(null, false)
            }
        }

        gatt.requestMtu(desiredMtu)
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
        val gatt = connectionStateMachine.bluetoothGatt
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                IllegalStateException("BluetoothGatt is null")
            )

        gatt.setPreferredPhy(txPhy, rxPhy, phyOptions)

        continuation.resume(Unit, null)
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
            val gatt = connectionStateMachine.bluetoothGatt
                ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                    IllegalStateException("BluetoothGatt is null")
                )

            gatt.readCharacteristic(characteristic)
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
        val gatt = connectionStateMachine.bluetoothGatt
            ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                IllegalStateException("BluetoothGatt is null")
            )

        characteristic.value = value

        if (!gatt.writeCharacteristic(characteristic)) {
            continuation.resumeWithException(RuntimeException("Failed to write characteristic"))
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
            val gatt = connectionStateMachine.bluetoothGatt
                ?: throw IllegalStateException("BluetoothGatt is null")

            gatt.setCharacteristicNotification(characteristic, true)

            // Configure descriptor for notifications
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            awaitClose {
                gatt.setCharacteristicNotification(characteristic, false)
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

    companion object {
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
