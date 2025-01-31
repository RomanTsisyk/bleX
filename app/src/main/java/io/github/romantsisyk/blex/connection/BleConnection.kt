package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.flow.*

class BleConnection(
    context: Context,
    private val device: BluetoothDevice,
    private val bondManager: BondManager? = null
) {

    private val connectionStateMachine = ConnectionStateMachine(context, device, bondManager)
    val connectionStateFlow: StateFlow<ConnectionState> = connectionStateMachine.connectionStateFlow

    fun connect() {
        connectionStateMachine.connect()
    }

    fun disconnect() {
        connectionStateMachine.disconnect()
    }

    fun close() {
        connectionStateMachine.close()
    }

    private fun getGattOrThrow(): BluetoothGatt {
        return connectionStateMachine.bluetoothGatt
            ?: throw IllegalStateException("BluetoothGatt is null for device ${device.address}")
    }

    fun bond(): Flow<BondState> {
        return bondManager?.bondDevice(device) ?: flow { }
    }
}
