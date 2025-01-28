package io.github.romantsisyk.blex

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.romantsisyk.blex.connection.BleConnection
import io.github.romantsisyk.blex.connection.BondManager
import io.github.romantsisyk.blex.scanner.BleScanner
import io.github.romantsisyk.blex.util.PermissionsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class BleManager private constructor(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

    private val bleScanner = BleScanner(context, bluetoothAdapter)
    private val bondManager = BondManager(context)

    private val connections = mutableMapOf<String, BleConnection>() // Keyed by device MAC address

    companion object {
        @Volatile
        private var INSTANCE: BleManager? = null

        fun getInstance(context: Context): BleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Scans for BLE devices and emits discovered devices through a Flow.
     *
     * @return Flow emitting [BluetoothDevice].
     */
    @SuppressLint("MissingPermission")
    fun scanDevices(): Flow<BluetoothDevice> {
        return bleScanner.scanDevices()
            .map { it.device }
            .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE }
            .distinctUntilChanged { old, new -> old.address == new.address }
    }

    /**
     * Connects to a BLE device and returns a [BleConnection] instance.
     *
     * @param device BluetoothDevice to connect to.
     * @return [BleConnection] instance.
     */
    fun connect(device: BluetoothDevice): BleConnection {
        if (connections.containsKey(device.address)) {
            return connections[device.address]!!
        }
        val bleConnection = BleConnection(context, device, bondManager)
        connections[device.address] = bleConnection
        bleConnection.connect()
        return bleConnection
    }

    /**
     * Disconnects from a BLE device and removes it from active connections.
     *
     * @param device BluetoothDevice to disconnect from.
     */
    fun disconnect(device: BluetoothDevice) {
        connections[device.address]?.disconnect()
        connections.remove(device.address)
    }

    /**
     * Disconnects from all connected BLE devices.
     */
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }

    /**
     * Checks if the app has the necessary permissions to perform BLE operations.
     *
     * @return True if permissions are granted, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun hasPermissions(): Boolean = withContext(Dispatchers.IO) {
        PermissionsHelper.hasScanPermissions(context) &&
                PermissionsHelper.hasConnectPermissions(context) &&
                PermissionsHelper.hasAdvertisePermissions(context)
    }
}
