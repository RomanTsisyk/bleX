package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.github.romantsisyk.blex.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object DiscoveringServices : ConnectionState()
    data object Ready : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ConnectionStateMachine(
    private val context: Context,
    private val device: BluetoothDevice,
    private val bondManager: BondManager? = null,
    private val autoReconnect: Boolean = true
) {
    var bluetoothGatt: BluetoothGatt? = null
        private set

    private var reconnectJob: Job? = null

    private val _connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionStateFlow: StateFlow<ConnectionState> = _connectionStateFlow

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Callback Maps
    val readCharacteristicCallbacks: MutableMap<String, (Int, ByteArray) -> Unit> = HashMap()
    val writeCharacteristicCallbacks: MutableMap<String, (Int) -> Unit> = HashMap()
    val mtuChangeCallbacks: MutableMap<String, (Int, Int) -> Unit> = HashMap()
    val phyUpdateCallbacks: MutableMap<String, (Int, Int, Int) -> Unit> = HashMap()
    val notificationCallbacks: MutableMap<UUID, (ByteArray) -> Unit> = HashMap()

    private val callback = BleGattCallback(this, bondManager)

    /**
     * Initiates connection to the BLE device.
     */
    @SuppressLint("MissingPermission")
    fun connect() {
        if (_connectionStateFlow.value is ConnectionState.Connected ||
            _connectionStateFlow.value is ConnectionState.Connecting
        ) {
            Log.w(Constants.TAG, "Already connected or connecting to ${device.address}")
            return
        }
        transitionTo(ConnectionState.Connecting)
        bluetoothGatt = device.connectGatt(context, false, callback)
    }

    /**
     * Disconnects from the BLE device.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    /**
     * Closes the GATT connection and cleans up resources.
     */
    @SuppressLint("MissingPermission")
    fun close() {
        reconnectJob?.cancel()
        bluetoothGatt?.close()
        bluetoothGatt = null
        transitionTo(ConnectionState.Disconnected)
    }

    /**
     * Handles changes in connection state.
     */
    @SuppressLint("MissingPermission")
    internal fun handleConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            handleError("Connection failed with status: $status")
            return
        }

        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                transitionTo(ConnectionState.Connected)
                gatt.discoverServices()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                transitionTo(ConnectionState.Disconnected)
                if (autoReconnect) {
                    scheduleReconnect()
                }
            }
            else -> {
                // Handle other states if necessary
            }
        }
    }

    /**
     * Handles service discovery results.
     */
    internal fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            transitionTo(ConnectionState.Ready)
        } else {
            handleError("Service discovery failed with status: $status")
        }
    }

    /**
     * Handles MTU change results.
     */
    internal fun handleMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(Constants.TAG, "MTU successfully changed to $mtu")
            mtuChangeCallbacks["mtu_request"]?.invoke(mtu, status)
        } else {
            Log.e(Constants.TAG, "MTU change failed with status: $status")
            mtuChangeCallbacks["mtu_request"]?.invoke(mtu, status)
        }
    }

    /**
     * Handles PHY update results.
     */
    internal fun handlePhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(Constants.TAG, "PHY successfully updated to txPhy=$txPhy, rxPhy=$rxPhy")
            phyUpdateCallbacks["phy_update"]?.invoke(txPhy, rxPhy, status)
        } else {
            Log.e(Constants.TAG, "PHY update failed with status: $status")
            phyUpdateCallbacks["phy_update"]?.invoke(txPhy, rxPhy, status)
        }
    }

    /**
     * Handles characteristic read events.
     */
    internal fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = characteristic.uuid.toString()
        readCharacteristicCallbacks[key]?.invoke(status, characteristic.value)
        readCharacteristicCallbacks.remove(key)
    }

    /**
     * Handles characteristic write events.
     */
    internal fun handleCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = characteristic.uuid.toString()
        writeCharacteristicCallbacks[key]?.invoke(status)
        writeCharacteristicCallbacks.remove(key)
    }

    /**
     * Handles characteristic change events (notifications/indications).
     */
    internal fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        val callback = notificationCallbacks[characteristic.uuid]
        callback?.invoke(characteristic.value)
    }

    /**
     * Handles descriptor write events.
     */
    internal fun handleDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        // Implement descriptor write handling if needed
    }

    /**
     * Transitions the state machine to a new state.
     */
    private fun transitionTo(newState: ConnectionState) {
        Log.d(Constants.TAG, "Transitioning to $newState")
        _connectionStateFlow.value = newState
    }

    /**
     * Handles errors and transitions to the Error state.
     */
    private fun handleError(message: String) {
        Log.e(Constants.TAG, message)
        transitionTo(ConnectionState.Error(message))
        if (autoReconnect) {
            scheduleReconnect()
        }
    }

    /**
     * Schedules a reconnection attempt after a delay.
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return // Already scheduled

        reconnectJob = coroutineScope.launch {
            delay(Constants.RECONNECT_DELAY_MS)
            Log.d(Constants.TAG, "Attempting to reconnect to ${device.address}")
            connect()
        }
    }
}
