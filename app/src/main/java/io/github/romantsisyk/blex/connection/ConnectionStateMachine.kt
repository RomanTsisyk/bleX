package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import io.github.romantsisyk.blex.models.ConnectionState
import io.github.romantsisyk.blex.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

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

    private val mtuChangeHandler = mutableMapOf<String, (Int, Int) -> Unit>()
    private val phyUpdateHandler = mutableMapOf<String, (Int, Int, Int) -> Unit>()
    private val readCharacteristicHandler = mutableMapOf<String, (Int, ByteArray) -> Unit>()
    private val writeCharacteristicHandler = mutableMapOf<String, (Int) -> Unit>()
    private val notificationHandler = mutableMapOf<UUID, (ByteArray) -> Unit>()
    private val descriptorWriteHandler = mutableMapOf<String, (Int) -> Unit>()

    private val callback = BleGattCallback(this, bondManager)

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

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    @SuppressLint("MissingPermission")
    fun close() {
        reconnectJob?.cancel()
        bluetoothGatt?.close()
        bluetoothGatt = null
        transitionTo(ConnectionState.Disconnected)
    }

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
        }
    }

    internal fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            transitionTo(ConnectionState.Ready)
        } else {
            handleError("Service discovery failed with status: $status")
        }
    }

    internal fun handleMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        mtuChangeHandler["mtu_request"]?.invoke(mtu, status)
    }

    internal fun handlePhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        phyUpdateHandler["phy_update"]?.invoke(txPhy, rxPhy, status)
    }

    internal fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = characteristic.uuid.toString()
        readCharacteristicHandler[key]?.invoke(status, characteristic.value)
        readCharacteristicHandler.remove(key)
    }

    internal fun handleCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val key = characteristic.uuid.toString()
        writeCharacteristicHandler[key]?.invoke(status)
        writeCharacteristicHandler.remove(key)
    }

    internal fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        notificationHandler[characteristic.uuid]?.invoke(characteristic.value)
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

    private fun transitionTo(newState: ConnectionState) {
        Log.d(Constants.TAG, "Transitioning to $newState")
        _connectionStateFlow.value = newState
    }

    private fun handleError(message: String) {
        Log.e(Constants.TAG, "Error on device ${device.address}: $message")
        transitionTo(ConnectionState.Error("Device ${device.address}: $message"))
        if (autoReconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = coroutineScope.launch {
            delay(Constants.RECONNECT_DELAY_MS)
            Log.d(Constants.TAG, "Attempting to reconnect to ${device.address}")
            connect()
        }
    }
}