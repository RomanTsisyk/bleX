package io.github.romantsisyk.blex.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import io.github.romantsisyk.blex.connection.BleConnection.Companion.CLIENT_CHARACTERISTIC_CONFIG_UUID
import io.github.romantsisyk.blex.util.Constants
import java.util.*

class BleGattServer(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    private var bluetoothGattServer: android.bluetooth.BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    /**
     * Initializes and starts the GATT server with predefined services and characteristics.
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        addServices()
        startAdvertising()
    }

    /**
     * Stops the GATT server and advertising.
     */
    @SuppressLint("MissingPermission")
    fun stopServer() {
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        stopAdvertising()
    }

    /**
     * Adds predefined GATT services and characteristics to the server.
     */
    @SuppressLint("MissingPermission")
    private fun addServices() {
        val serviceUUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb") // Example: Heart Rate Service
        val characteristicUUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb") // Heart Rate Measurement

        val service = BluetoothGattService(serviceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val characteristic = BluetoothGattCharacteristic(
            characteristicUUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val descriptor = BluetoothGattDescriptor(
            CLIENT_CHARACTERISTIC_CONFIG_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)

        bluetoothGattServer?.addService(service)
    }

    /**
     * Starts BLE advertising to allow connections.
     */
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(Constants.TAG, "Failed to get BluetoothLeAdvertiser")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb"))) // Heart Rate Service
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Stops BLE advertising.
     */
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    /**
     * Callback for advertising events.
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(Constants.TAG, "Advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(Constants.TAG, "Advertising failed with error code: $errorCode")
        }
    }

    /**
     * Callback for GATT server events.
     */
    private val gattServerCallback = object : android.bluetooth.BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(Constants.TAG, "GATT Server: Connection state changed for ${device.address}: $newState")
            // Handle connection state changes
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(Constants.TAG, "GATT Server: Read request for ${characteristic.uuid} from ${device.address}")
            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic.value
            )
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(Constants.TAG, "GATT Server: Descriptor write request from ${device.address}")
            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

    }
}
