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

/**
 * Central manager for Bluetooth Low Energy (BLE) operations.
 *
 * This singleton class provides a unified interface for scanning, connecting to,
 * and managing BLE devices. It handles the lifecycle of BLE connections and ensures
 * proper resource management across the application.
 *
 * Usage:
 * ```kotlin
 * val bleManager = BleManager.getInstance(context)
 * bleManager.scanDevices().collect { device ->
 *     // Handle discovered device
 * }
 * ```
 *
 * @property context Application context used for BLE operations. Must be application context
 *                   to prevent memory leaks.
 * @see BleConnection
 * @see BleScanner
 */
class BleManager private constructor(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter

    private val bleScanner = BleScanner(context, bluetoothAdapter)
    private val bondManager = BondManager(context)

    private val connections = mutableMapOf<String, BleConnection>() // Keyed by device MAC address

    /**
     * Companion object providing singleton access to [BleManager].
     *
     * Uses double-checked locking pattern to ensure thread-safe singleton instantiation
     * while maintaining good performance after initial creation.
     */
    companion object {
        @Volatile
        private var INSTANCE: BleManager? = null

        /**
         * Returns the singleton instance of [BleManager].
         *
         * This method is thread-safe and uses double-checked locking to ensure
         * only one instance is created. The provided context is converted to
         * application context to prevent memory leaks.
         *
         * @param context Any valid Android [Context]. Will be converted to application context internally.
         * @return The singleton [BleManager] instance.
         */
        fun getInstance(context: Context): BleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Scans for nearby BLE devices and emits discovered devices through a Flow.
     *
     * This method filters results to only include BLE devices (excluding Classic Bluetooth)
     * and removes duplicates based on device address. The scan continues until the Flow
     * is cancelled by the collector.
     *
     * Requires `BLUETOOTH_SCAN` permission on Android 12+ or `ACCESS_FINE_LOCATION` on earlier versions.
     *
     * @return A [Flow] emitting unique [BluetoothDevice] instances as they are discovered.
     * @throws SecurityException if required Bluetooth permissions are not granted.
     * @see BleScanner
     */
    @SuppressLint("MissingPermission")
    fun scanDevices(): Flow<BluetoothDevice> {
        return bleScanner.scanDevices()
            .map { it.device }
            .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE }
            .distinctUntilChanged { old, new -> old.address == new.address }
    }

    /**
     * Establishes a connection to the specified BLE device.
     *
     * If a connection to the device already exists, returns the existing [BleConnection]
     * instance. Otherwise, creates a new connection and initiates the connection process.
     * The connection is stored internally and can be retrieved for subsequent operations.
     *
     * @param device The [BluetoothDevice] to connect to.
     * @return A [BleConnection] instance representing the connection to the device.
     * @throws IllegalStateException if the connection map is in an inconsistent state.
     * @throws SecurityException if required Bluetooth permissions are not granted.
     * @see disconnect
     * @see disconnectAll
     */
    fun connect(device: BluetoothDevice): BleConnection {
        if (connections.containsKey(device.address)) {
            return connections[device.address]
                ?: throw IllegalStateException("No connection found for device ${device.address}")
        }
        val bleConnection = BleConnection(context, device, bondManager)
        connections[device.address] = bleConnection
        bleConnection.connect()
        return bleConnection
    }

    /**
     * Disconnects from the specified BLE device and removes it from active connections.
     *
     * This method gracefully closes the connection and releases associated resources.
     * If no connection exists for the specified device, this method does nothing.
     *
     * @param device The [BluetoothDevice] to disconnect from.
     * @see connect
     * @see disconnectAll
     */
    fun disconnect(device: BluetoothDevice) {
        connections[device.address]?.disconnect()
        connections.remove(device.address)
    }

    /**
     * Disconnects from all connected BLE devices and clears the connection registry.
     *
     * This method should be called during application cleanup or when BLE functionality
     * is no longer needed to ensure all resources are properly released.
     *
     * @see connect
     * @see disconnect
     */
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }

    /**
     * Checks if the application has all necessary permissions for BLE operations.
     *
     * This method verifies that scan, connect, and advertise permissions are all granted.
     * On Android 12 (API 31) and above, this includes `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`,
     * and `BLUETOOTH_ADVERTISE` permissions.
     *
     * This is a suspend function that runs on the IO dispatcher.
     *
     * @return `true` if all required BLE permissions are granted, `false` otherwise.
     * @see PermissionsHelper
     */
    @RequiresApi(Build.VERSION_CODES.S)
    suspend fun hasPermissions(): Boolean = withContext(Dispatchers.IO) {
        PermissionsHelper.hasScanPermissions(context) &&
                PermissionsHelper.hasConnectPermissions(context) &&
                PermissionsHelper.hasAdvertisePermissions(context)
    }
}
