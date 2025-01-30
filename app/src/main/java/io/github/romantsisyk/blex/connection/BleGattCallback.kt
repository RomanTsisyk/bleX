package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import io.github.romantsisyk.blex.util.Constants

class BleGattCallback(
    private val connectionStateMachine: ConnectionStateMachine,
    private val bondManager: BondManager? = null
) : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.d(Constants.TAG, "onConnectionStateChange: status=$status, newState=$newState")
        connectionStateMachine.handleConnectionStateChange(gatt, status, newState)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(Constants.TAG, "onServicesDiscovered: status=$status")
        connectionStateMachine.handleServicesDiscovered(gatt, status)
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(Constants.TAG, "onMtuChanged: mtu=$mtu, status=$status")
        connectionStateMachine.handleMtuChanged(gatt, mtu, status)
    }

    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        Log.d(Constants.TAG, "onPhyUpdate: txPhy=$txPhy, rxPhy=$rxPhy, status=$status")
        connectionStateMachine.handlePhyUpdate(gatt, txPhy, rxPhy, status)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(Constants.TAG, "onCharacteristicRead: ${characteristic.uuid}, status=$status")
        connectionStateMachine.handleCharacteristicRead(gatt, characteristic, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        Log.d(Constants.TAG, "onCharacteristicWrite: ${characteristic.uuid}, status=$status")
        connectionStateMachine.handleCharacteristicWrite(gatt, characteristic, status)
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        Log.d(Constants.TAG, "onCharacteristicChanged: ${characteristic.uuid}")
        connectionStateMachine.handleCharacteristicChanged(gatt, characteristic)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        Log.d(Constants.TAG, "onDescriptorWrite: ${descriptor.uuid}, status=$status")
        connectionStateMachine.handleDescriptorWrite(gatt, descriptor, status)
    }
}