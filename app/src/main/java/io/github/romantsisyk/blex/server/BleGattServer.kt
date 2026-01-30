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
import io.github.romantsisyk.blex.connection.BleConnection.Companion.CLIENT_CHARACTERISTIC_CONFIG_UUID
import io.github.romantsisyk.blex.logging.BleLog
import io.github.romantsisyk.blex.util.Constants
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A Bluetooth Low Energy (BLE) GATT server implementation that allows an Android device
 * to act as a BLE peripheral.
 *
 * This class provides functionality to:
 * - Start and stop a GATT server with customizable services and characteristics
 * - Advertise the device to nearby BLE centrals (scanners)
 * - Handle read/write requests from connected central devices
 * - Send notifications to subscribed devices
 * - Manage characteristic values and notification subscriptions
 *
 * ## GATT Server Overview
 *
 * A GATT (Generic Attribute Profile) server hosts a hierarchy of services and characteristics
 * that can be discovered and interacted with by connected clients (centrals). This implementation
 * includes:
 * - Automatic handling of the Client Characteristic Configuration Descriptor (CCCD) for
 *   notification subscriptions
 * - Support for prepared (long) writes with offset handling
 * - Proper response handling for read and write requests
 *
 * ## Required Permissions
 *
 * The following permissions must be declared in AndroidManifest.xml:
 * - `android.permission.BLUETOOTH` (for API < 31)
 * - `android.permission.BLUETOOTH_ADMIN` (for API < 31)
 * - `android.permission.BLUETOOTH_ADVERTISE` (for API >= 31)
 * - `android.permission.BLUETOOTH_CONNECT` (for API >= 31)
 *
 * ## Usage Example
 *
 * Setting up a basic BLE peripheral with Heart Rate Service:
 *
 * ```kotlin
 * class MyPeripheralActivity : AppCompatActivity() {
 *
 *     private lateinit var gattServer: BleGattServer
 *     private val heartRateMeasurementUUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
 *         val bluetoothAdapter = bluetoothManager.adapter
 *
 *         // Initialize the GATT server
 *         gattServer = BleGattServer(this, bluetoothAdapter)
 *
 *         // Start the server (this also starts advertising)
 *         gattServer.startServer()
 *     }
 *
 *     // Send heart rate updates to subscribed clients
 *     private fun sendHeartRateUpdate(heartRate: Int) {
 *         val value = byteArrayOf(0x00, heartRate.toByte()) // Flags + Heart Rate
 *         gattServer.sendNotification(heartRateMeasurementUUID, value)
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         // Always stop the server when done
 *         gattServer.stopServer()
 *     }
 * }
 * ```
 *
 * ## Custom Services Example
 *
 * To create a peripheral with custom services, you can extend this class or modify
 * the [addServices] method:
 *
 * ```kotlin
 * // Define custom UUIDs
 * val CUSTOM_SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
 * val CUSTOM_CHAR_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd")
 *
 * // Create a service with read/write/notify capabilities
 * val service = BluetoothGattService(CUSTOM_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
 *
 * val characteristic = BluetoothGattCharacteristic(
 *     CUSTOM_CHAR_UUID,
 *     BluetoothGattCharacteristic.PROPERTY_READ or
 *         BluetoothGattCharacteristic.PROPERTY_WRITE or
 *         BluetoothGattCharacteristic.PROPERTY_NOTIFY,
 *     BluetoothGattCharacteristic.PERMISSION_READ or
 *         BluetoothGattCharacteristic.PERMISSION_WRITE
 * )
 *
 * // Add CCCD for notification support
 * val cccd = BluetoothGattDescriptor(
 *     CLIENT_CHARACTERISTIC_CONFIG_UUID,
 *     BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
 * )
 * characteristic.addDescriptor(cccd)
 * service.addCharacteristic(characteristic)
 * ```
 *
 * ## Thread Safety
 *
 * This class is not thread-safe. All method calls should be made from the main thread
 * or properly synchronized. The GATT server callbacks are delivered on an internal
 * Binder thread, which this implementation handles appropriately.
 *
 * @property context The Android context used to access system services
 * @property bluetoothAdapter The Bluetooth adapter used for advertising
 *
 * @see BluetoothGattService
 * @see BluetoothGattCharacteristic
 * @see BluetoothLeAdvertiser
 */
class BleGattServer(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) {

    /** The system Bluetooth manager for accessing GATT server functionality. */
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager

    /** The GATT server instance, null when the server is not running. */
    private var bluetoothGattServer: android.bluetooth.BluetoothGattServer? = null

    /** The BLE advertiser for making this peripheral discoverable. */
    private var advertiser: BluetoothLeAdvertiser? = null

    /**
     * Set of connected devices that have enabled notifications by writing to the
     * Client Characteristic Configuration Descriptor (CCCD).
     *
     * Devices are automatically added when they enable notifications and removed
     * when they disable notifications or disconnect.
     */
    private val notificationEnabledDevices: MutableSet<BluetoothDevice> = ConcurrentHashMap.newKeySet()

    /**
     * Storage for characteristic values keyed by characteristic UUID.
     *
     * Values are stored here when:
     * - A central writes to a characteristic
     * - The application sets a value via [setCharacteristicValue]
     * - A notification is sent via [sendNotification]
     *
     * Values are retrieved when:
     * - A central reads a characteristic
     * - The application queries via [getCharacteristicValue]
     */
    private val characteristicValues = ConcurrentHashMap<UUID, ByteArray>()

    /**
     * Initializes and starts the GATT server with predefined services and characteristics.
     *
     * This method performs three operations in sequence:
     * 1. Opens a GATT server with the [gattServerCallback] to handle client interactions
     * 2. Adds predefined services and characteristics via [addServices]
     * 3. Starts BLE advertising to make this peripheral discoverable via [startAdvertising]
     *
     * After calling this method, the device will be visible to nearby BLE scanners and
     * will accept incoming connections. Connected centrals can then discover services,
     * read/write characteristics, and subscribe to notifications.
     *
     * ## Prerequisites
     *
     * - Bluetooth must be enabled on the device
     * - Required runtime permissions must be granted (BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT for API 31+)
     * - The device must support BLE peripheral mode (check via [BluetoothAdapter.isMultipleAdvertisementSupported])
     *
     * ## Example
     *
     * ```kotlin
     * // Check prerequisites before starting
     * if (bluetoothAdapter.isEnabled && bluetoothAdapter.isMultipleAdvertisementSupported) {
     *     gattServer.startServer()
     *     Log.d(TAG, "GATT server started successfully")
     * } else {
     *     Log.e(TAG, "Device does not support BLE peripheral mode")
     * }
     * ```
     *
     * @throws SecurityException if required Bluetooth permissions are not granted
     *
     * @see stopServer
     * @see addServices
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
        addServices()
        startAdvertising()
    }

    /**
     * Stops the GATT server and BLE advertising.
     *
     * This method performs a clean shutdown of the GATT server:
     * 1. Closes the GATT server, which disconnects all connected devices
     * 2. Stops BLE advertising, making the device no longer discoverable
     * 3. Clears the server reference
     *
     * After calling this method:
     * - All connected central devices will be disconnected
     * - The device will no longer appear in BLE scans
     * - Any pending notifications will not be delivered
     * - The [notificationEnabledDevices] set is NOT automatically cleared
     *   (devices are removed individually via the disconnect callback)
     *
     * This method is safe to call multiple times and when the server is not running.
     *
     * ## Lifecycle Recommendation
     *
     * Always call this method in your Activity's `onDestroy()` or when your service
     * is stopped to ensure proper resource cleanup:
     *
     * ```kotlin
     * override fun onDestroy() {
     *     super.onDestroy()
     *     gattServer.stopServer()
     * }
     * ```
     *
     * @throws SecurityException if required Bluetooth permissions are not granted
     *
     * @see startServer
     */
    @SuppressLint("MissingPermission")
    fun stopServer() {
        bluetoothGattServer?.close()
        bluetoothGattServer = null
        stopAdvertising()
    }

    /**
     * Adds predefined GATT services and characteristics to the server.
     *
     * This implementation creates a standard Heart Rate Service (UUID: 0x180D) with
     * a Heart Rate Measurement characteristic (UUID: 0x2A37) that supports notifications.
     *
     * ## Service Structure
     *
     * ```
     * Heart Rate Service (0x180D)
     * └── Heart Rate Measurement Characteristic (0x2A37)
     *     ├── Properties: NOTIFY
     *     ├── Permissions: READ
     *     └── Client Characteristic Configuration Descriptor (0x2902)
     *         └── Permissions: WRITE
     * ```
     *
     * ## Customization
     *
     * To add custom services, you can either:
     * 1. Override this method in a subclass
     * 2. Call [bluetoothGattServer.addService()] after [startServer()]
     *
     * ### Example: Adding a Custom Service
     *
     * ```kotlin
     * // Define UUIDs for a custom temperature service
     * val TEMP_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
     * val TEMP_MEASUREMENT_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")
     *
     * val tempService = BluetoothGattService(
     *     TEMP_SERVICE_UUID,
     *     BluetoothGattService.SERVICE_TYPE_PRIMARY
     * )
     *
     * val tempCharacteristic = BluetoothGattCharacteristic(
     *     TEMP_MEASUREMENT_UUID,
     *     BluetoothGattCharacteristic.PROPERTY_READ or
     *         BluetoothGattCharacteristic.PROPERTY_NOTIFY,
     *     BluetoothGattCharacteristic.PERMISSION_READ
     * )
     *
     * // Add CCCD for notifications
     * tempCharacteristic.addDescriptor(BluetoothGattDescriptor(
     *     CLIENT_CHARACTERISTIC_CONFIG_UUID,
     *     BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
     * ))
     *
     * tempService.addCharacteristic(tempCharacteristic)
     * ```
     *
     * @throws SecurityException if required Bluetooth permissions are not granted
     *
     * @see BluetoothGattService
     * @see BluetoothGattCharacteristic
     * @see CLIENT_CHARACTERISTIC_CONFIG_UUID
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
     * Starts BLE advertising to make this peripheral discoverable by nearby central devices.
     *
     * This method configures and starts BLE advertising with the following settings:
     *
     * ## Advertising Settings
     *
     * | Setting | Value | Description |
     * |---------|-------|-------------|
     * | Mode | LOW_LATENCY | Fastest advertising interval (~100ms) for quick discovery |
     * | Connectable | true | Allows centrals to establish a connection |
     * | Timeout | 0 | Advertises indefinitely until [stopAdvertising] is called |
     * | TX Power | HIGH | Maximum transmission power for better range |
     *
     * ## Advertising Data
     *
     * The advertising packet includes:
     * - Device name (from Bluetooth settings)
     * - Heart Rate Service UUID (0x180D) for service discovery
     *
     * ## Power Considerations
     *
     * The LOW_LATENCY mode with HIGH TX power provides the best discoverability but
     * consumes more battery. For production applications, consider:
     * - Using ADVERTISE_MODE_LOW_POWER for background advertising
     * - Setting a reasonable timeout to stop advertising after a period
     * - Using ADVERTISE_TX_POWER_LOW when range is not critical
     *
     * ## Failure Handling
     *
     * If advertising fails to start (e.g., advertiser is null, Bluetooth is off, or
     * the device doesn't support peripheral mode), an error is logged via [advertiseCallback].
     * Common failure codes:
     * - ADVERTISE_FAILED_DATA_TOO_LARGE: Advertising data exceeds 31 bytes
     * - ADVERTISE_FAILED_TOO_MANY_ADVERTISERS: Device limit reached
     * - ADVERTISE_FAILED_ALREADY_STARTED: Advertising is already active
     * - ADVERTISE_FAILED_FEATURE_UNSUPPORTED: Device doesn't support BLE advertising
     *
     * @throws SecurityException if BLUETOOTH_ADVERTISE permission is not granted (API 31+)
     *
     * @see stopAdvertising
     * @see advertiseCallback
     * @see AdvertiseSettings
     */
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            BleLog.e(Constants.TAG, "Failed to get BluetoothLeAdvertiser")
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
     * Stops BLE advertising, making this peripheral no longer discoverable.
     *
     * After calling this method, the device will no longer appear in BLE scans performed
     * by central devices. However, existing connections remain active until either:
     * - The central disconnects
     * - [stopServer] is called
     * - The connection times out
     *
     * This method is safe to call when advertising is not active or when the advertiser
     * is null.
     *
     * @throws SecurityException if BLUETOOTH_ADVERTISE permission is not granted (API 31+)
     *
     * @see startAdvertising
     */
    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    /**
     * Sends a notification to all connected devices that have enabled notifications for the
     * specified characteristic.
     *
     * This method broadcasts a characteristic value change to all subscribed central devices.
     * A device is considered subscribed when it has written [BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE]
     * to the characteristic's Client Characteristic Configuration Descriptor (CCCD).
     *
     * ## How Notifications Work
     *
     * 1. The central device subscribes by writing to the CCCD
     * 2. The peripheral (this server) stores the subscription in [notificationEnabledDevices]
     * 3. When data changes, call this method to push the update to all subscribers
     * 4. Each subscribed device receives the new value without needing to poll
     *
     * ## Value Storage
     *
     * The provided value is automatically stored in [characteristicValues], so subsequent
     * read requests from centrals will return this same value.
     *
     * ## Example: Sending Heart Rate Updates
     *
     * ```kotlin
     * val heartRateMeasurementUUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
     *
     * // Heart Rate Measurement format: [Flags, Heart Rate Value]
     * // Flags = 0x00 means heart rate is in UINT8 format
     * fun sendHeartRate(bpm: Int) {
     *     val value = byteArrayOf(
     *         0x00,           // Flags: Heart Rate Value Format is UINT8
     *         bpm.toByte()    // Heart Rate Measurement Value
     *     )
     *
     *     val sent = gattServer.sendNotification(heartRateMeasurementUUID, value)
     *     if (sent) {
     *         Log.d(TAG, "Heart rate $bpm sent to subscribers")
     *     } else {
     *         Log.w(TAG, "No subscribers for heart rate notifications")
     *     }
     * }
     *
     * // Start a periodic update
     * lifecycleScope.launch {
     *     while (isActive) {
     *         sendHeartRate(Random.nextInt(60, 100))
     *         delay(1000) // Send every second
     *     }
     * }
     * ```
     *
     * ## Error Handling
     *
     * The method handles individual device failures gracefully:
     * - If one device fails, notifications are still attempted for other devices
     * - Exceptions are caught and logged, not propagated
     * - Returns true if at least one notification succeeded
     *
     * @param characteristicUUID The UUID of the characteristic whose value has changed.
     *        Must match a characteristic that exists in one of the server's services.
     * @param value The new characteristic value to send. Maximum length depends on the
     *        negotiated MTU (typically 20 bytes for default MTU of 23, or up to 512 bytes
     *        for larger MTUs).
     *
     * @return `true` if the notification was successfully sent to at least one device,
     *         `false` if:
     *         - The GATT server is not running
     *         - The characteristic UUID was not found in any service
     *         - No devices have enabled notifications for this characteristic
     *         - All notification attempts failed
     *
     * @throws SecurityException if BLUETOOTH_CONNECT permission is not granted (API 31+)
     *
     * @see setCharacteristicValue
     * @see notificationEnabledDevices
     */
    @SuppressLint("MissingPermission")
    fun sendNotification(characteristicUUID: UUID, value: ByteArray): Boolean {
        val server = bluetoothGattServer ?: return false

        val service = server.services.find { service ->
            service.getCharacteristic(characteristicUUID) != null
        } ?: return false

        val characteristic = service.getCharacteristic(characteristicUUID) ?: return false

        // Store the value
        characteristicValues[characteristicUUID] = value

        if (notificationEnabledDevices.isEmpty()) {
            BleLog.w(Constants.TAG, "No devices have enabled notifications for $characteristicUUID")
            return false
        }

        var sentToAnyDevice = false
        for (device in notificationEnabledDevices) {
            try {
                characteristic.value = value
                val notificationSent = server.notifyCharacteristicChanged(device, characteristic, false)
                if (notificationSent) {
                    BleLog.d(Constants.TAG, "Notification sent to ${device.address} for $characteristicUUID")
                    sentToAnyDevice = true
                } else {
                    BleLog.w(Constants.TAG, "Failed to send notification to ${device.address}")
                }
            } catch (e: Exception) {
                BleLog.e(Constants.TAG, "Error sending notification to ${device.address}: ${e.message}")
            }
        }

        return sentToAnyDevice
    }

    /**
     * Sets the value for a characteristic without sending a notification.
     *
     * Use this method to update a characteristic's value that will be returned when a
     * central device performs a read operation. Unlike [sendNotification], this method
     * does NOT push the update to subscribed devices - it only stores the value for
     * future read requests.
     *
     * ## When to Use
     *
     * - **Use [setCharacteristicValue]**: When you want to update a value that will be
     *   read on-demand by centrals (pull model)
     * - **Use [sendNotification]**: When you want to push updates to subscribed centrals
     *   immediately (push model)
     *
     * ## Example: Setting a Device Name Characteristic
     *
     * ```kotlin
     * val deviceNameUUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
     *
     * // Set the device name that centrals will read
     * gattServer.setCharacteristicValue(
     *     deviceNameUUID,
     *     "My BLE Device".toByteArray(Charsets.UTF_8)
     * )
     * ```
     *
     * @param characteristicUUID The UUID of the characteristic to update
     * @param value The new value for the characteristic
     *
     * @see getCharacteristicValue
     * @see sendNotification
     */
    fun setCharacteristicValue(characteristicUUID: UUID, value: ByteArray) {
        characteristicValues[characteristicUUID] = value
    }

    /**
     * Gets the current stored value of a characteristic.
     *
     * Returns the value that was last set via [setCharacteristicValue], [sendNotification],
     * or written by a connected central device. This is the same value that would be
     * returned to a central performing a read operation.
     *
     * ## Example: Reading Back a Stored Value
     *
     * ```kotlin
     * val batteryLevelUUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")
     *
     * // Check current stored battery level
     * val currentLevel = gattServer.getCharacteristicValue(batteryLevelUUID)
     * if (currentLevel != null) {
     *     val percentage = currentLevel[0].toInt() and 0xFF
     *     Log.d(TAG, "Current battery level: $percentage%")
     * } else {
     *     Log.d(TAG, "Battery level not yet set")
     * }
     * ```
     *
     * @param characteristicUUID The UUID of the characteristic to query
     * @return The current value as a [ByteArray], or `null` if no value has been set
     *         for this characteristic
     *
     * @see setCharacteristicValue
     */
    fun getCharacteristicValue(characteristicUUID: UUID): ByteArray? {
        return characteristicValues[characteristicUUID]
    }

    /**
     * Callback for BLE advertising events.
     *
     * This callback handles the results of advertising operations initiated by
     * [startAdvertising]. It provides feedback on whether advertising started
     * successfully or failed.
     *
     * ## Callback Methods
     *
     * - **[onStartSuccess]**: Called when advertising begins successfully. The
     *   `settingsInEffect` parameter contains the actual settings being used, which
     *   may differ from requested settings if the device doesn't support them.
     *
     * - **[onStartFailure]**: Called when advertising fails to start. Common error codes:
     *   - `ADVERTISE_FAILED_DATA_TOO_LARGE` (1): Data exceeds 31 bytes
     *   - `ADVERTISE_FAILED_TOO_MANY_ADVERTISERS` (2): Device limit reached
     *   - `ADVERTISE_FAILED_ALREADY_STARTED` (3): Already advertising
     *   - `ADVERTISE_FAILED_INTERNAL_ERROR` (4): Internal error
     *   - `ADVERTISE_FAILED_FEATURE_UNSUPPORTED` (5): Not supported
     *
     * ## Extending the Callback
     *
     * To add custom handling (e.g., notifying the UI), you can create a custom
     * callback or add listeners to this class.
     *
     * @see startAdvertising
     * @see AdvertiseCallback
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        /**
         * Called when advertising starts successfully.
         *
         * @param settingsInEffect The actual advertising settings in use, which may
         *        differ from requested settings based on device capabilities
         */
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            BleLog.d(Constants.TAG, "Advertising started successfully.")
        }

        /**
         * Called when advertising fails to start.
         *
         * @param errorCode The error code indicating why advertising failed.
         *        See [AdvertiseCallback] constants for possible values.
         */
        override fun onStartFailure(errorCode: Int) {
            BleLog.e(Constants.TAG, "Advertising failed with error code: $errorCode")
        }
    }

    /**
     * Callback for GATT server events.
     *
     * This callback handles all interactions between the GATT server and connected central
     * devices. It is the core of the peripheral's communication with centrals.
     *
     * ## Callback Overview
     *
     * | Callback | Purpose |
     * |----------|---------|
     * | [onConnectionStateChange] | Handles device connect/disconnect events |
     * | [onCharacteristicReadRequest] | Responds to read requests from centrals |
     * | [onCharacteristicWriteRequest] | Handles write requests, including prepared writes |
     * | [onExecuteWrite] | Commits or cancels prepared (queued) writes |
     * | [onDescriptorReadRequest] | Responds to descriptor read requests |
     * | [onDescriptorWriteRequest] | Handles CCCD writes for notification subscription |
     * | [onMtuChanged] | Notifies when MTU is negotiated |
     * | [onNotificationSent] | Confirms notification delivery |
     *
     * ## Important Notes
     *
     * - All callbacks are invoked on an internal Binder thread, not the main thread
     * - Read and write requests MUST be responded to via `sendResponse()` within 30 seconds
     * - Failing to respond will cause the central to timeout and possibly disconnect
     *
     * ## Thread Safety
     *
     * When accessing shared state (like [characteristicValues] or [notificationEnabledDevices])
     * from these callbacks, be aware that they execute on a Binder thread. This implementation
     * uses simple collections, so concurrent access from the main thread should be synchronized
     * in production applications.
     *
     * @see android.bluetooth.BluetoothGattServerCallback
     */
    private val gattServerCallback = object : android.bluetooth.BluetoothGattServerCallback() {

        /**
         * Called when a central device connects to or disconnects from the GATT server.
         *
         * This callback manages the connection lifecycle:
         * - On connect: The device can now discover services and interact with characteristics
         * - On disconnect: The device is removed from [notificationEnabledDevices] to prevent
         *   attempting to send notifications to a disconnected device
         *
         * ## Status Codes
         *
         * The `status` parameter indicates the reason for the state change:
         * - `BluetoothGatt.GATT_SUCCESS` (0): Normal operation
         * - Other values indicate an error condition
         *
         * ## State Values
         *
         * The `newState` parameter will be one of:
         * - `BluetoothProfile.STATE_CONNECTED` (2): Device has connected
         * - `BluetoothProfile.STATE_DISCONNECTED` (0): Device has disconnected
         *
         * @param device The remote central device that connected or disconnected
         * @param status The status of the operation (GATT_SUCCESS or error code)
         * @param newState The new connection state (STATE_CONNECTED or STATE_DISCONNECTED)
         */
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            BleLog.d(Constants.TAG, "GATT Server: Connection state changed for ${device.address}: newState=$newState, status=$status")

            when (newState) {
                android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                    BleLog.d(Constants.TAG, "GATT Server: Device ${device.address} connected")
                }
                android.bluetooth.BluetoothProfile.STATE_DISCONNECTED -> {
                    BleLog.d(Constants.TAG, "GATT Server: Device ${device.address} disconnected")
                    // Remove device from notification-enabled set on disconnect
                    notificationEnabledDevices.remove(device)
                }
            }
        }

        /**
         * Called when a central device requests to read a characteristic value.
         *
         * This callback retrieves the characteristic value from [characteristicValues]
         * (or falls back to the characteristic's internal value) and sends it to the
         * requesting device.
         *
         * ## Offset Handling
         *
         * The `offset` parameter supports reading long characteristics in multiple
         * operations (Long Read procedure):
         * - `offset = 0`: First read, returns data from the beginning
         * - `offset > 0`: Subsequent reads, returns data from the specified position
         * - `offset >= value.size`: Returns empty array (end of data)
         *
         * ## Response Requirement
         *
         * This callback MUST call `sendResponse()` to complete the read operation.
         * Failing to respond will cause the central to timeout.
         *
         * @param device The remote device that initiated the read request
         * @param requestId The unique ID for this request (used in sendResponse)
         * @param offset The offset into the characteristic value to start reading from
         * @param characteristic The characteristic being read
         */
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            BleLog.d(Constants.TAG, "GATT Server: Read request for ${characteristic.uuid} from ${device.address}")

            // Get the stored value or use the characteristic's current value
            val value = characteristicValues[characteristic.uuid] ?: characteristic.value ?: ByteArray(0)

            // Handle offset for partial reads
            val responseValue = if (offset > 0 && offset < value.size) {
                value.copyOfRange(offset, value.size)
            } else if (offset >= value.size) {
                ByteArray(0)
            } else {
                value
            }

            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                offset,
                responseValue
            )
        }

        /**
         * Called when a central device requests to write a characteristic value.
         *
         * This callback handles both regular writes and prepared (queued/long) writes.
         * The written value is stored in [characteristicValues] for later retrieval.
         *
         * ## Write Types
         *
         * ### Regular Write (`preparedWrite = false`)
         * Standard write operation that immediately stores the complete value.
         *
         * ### Prepared Write (`preparedWrite = true`)
         * Part of the "Prepare Write" / "Execute Write" procedure for writing
         * values larger than MTU-3 bytes:
         * 1. Central sends multiple Prepare Write requests with different offsets
         * 2. Server queues each fragment (handled here with offset support)
         * 3. Central sends Execute Write to commit or cancel all pending writes
         * 4. Server commits (see [onExecuteWrite]) and sends final response
         *
         * ## Response Handling
         *
         * When `responseNeeded = true`, this callback MUST call `sendResponse()`.
         * This is required for:
         * - Write Request (requires confirmation)
         * - Prepared Write Request (always requires response)
         *
         * When `responseNeeded = false` (Write Without Response), no response is sent.
         *
         * @param device The remote device that initiated the write request
         * @param requestId The unique ID for this request (used in sendResponse)
         * @param characteristic The characteristic being written
         * @param preparedWrite `true` if this is a prepared write (part of a long write)
         * @param responseNeeded `true` if the central expects a response
         * @param offset The offset into the characteristic value where writing should start
         * @param value The data to write, or null if no data provided
         */
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            BleLog.d(Constants.TAG, "GATT Server: Write request for ${characteristic.uuid} from ${device.address}, " +
                    "preparedWrite=$preparedWrite, responseNeeded=$responseNeeded, offset=$offset, " +
                    "valueLength=${value?.size ?: 0}")

            val writeValue = value ?: ByteArray(0)

            // Handle the write operation
            val status = if (preparedWrite) {
                // For prepared writes, we need to handle queued writes
                // Store the value with offset consideration
                val existingValue = characteristicValues[characteristic.uuid] ?: ByteArray(0)
                val newValue = if (offset > 0) {
                    // Append or insert at offset
                    val result = ByteArray(maxOf(existingValue.size, offset + writeValue.size))
                    existingValue.copyInto(result)
                    writeValue.copyInto(result, offset)
                    result
                } else {
                    writeValue
                }
                characteristicValues[characteristic.uuid] = newValue
                characteristic.value = newValue
                android.bluetooth.BluetoothGatt.GATT_SUCCESS
            } else {
                // Regular write
                characteristicValues[characteristic.uuid] = writeValue
                characteristic.value = writeValue
                android.bluetooth.BluetoothGatt.GATT_SUCCESS
            }

            if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    status,
                    offset,
                    writeValue
                )
            }

            BleLog.d(Constants.TAG, "GATT Server: Write completed for ${characteristic.uuid}, status=$status")
        }

        /**
         * Called when a central device requests to commit or cancel prepared writes.
         *
         * This is the final step of the "Prepare Write" / "Execute Write" procedure
         * for long writes. After one or more [onCharacteristicWriteRequest] calls
         * with `preparedWrite = true`, the central sends an Execute Write request.
         *
         * ## Execute vs Cancel
         *
         * - **`execute = true`**: Commit all prepared writes. The data has already
         *   been stored during the Prepare Write phase, so this is a confirmation.
         * - **`execute = false`**: Cancel all prepared writes. In a full implementation,
         *   this should revert any changes made during the Prepare Write phase.
         *
         * ## Current Implementation Note
         *
         * This basic implementation stores values immediately during [onCharacteristicWriteRequest],
         * so `execute = false` (cancel) is acknowledged but doesn't actually revert the
         * changes. A production implementation should use a separate prepared write buffer.
         *
         * @param device The remote device that initiated the execute write request
         * @param requestId The unique ID for this request (used in sendResponse)
         * @param execute `true` to commit prepared writes, `false` to cancel them
         */
        @SuppressLint("MissingPermission")
        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            BleLog.d(Constants.TAG, "GATT Server: Execute write from ${device.address}, execute=$execute")

            // If execute is false, we should cancel any prepared writes (not implemented in this basic version)
            // For now, just send success response

            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                0,
                null
            )
        }

        /**
         * Called when a central device requests to write a descriptor value.
         *
         * The most important use case is handling writes to the Client Characteristic
         * Configuration Descriptor (CCCD), which controls notification/indication
         * subscriptions.
         *
         * ## CCCD (UUID: 0x2902) Handling
         *
         * The CCCD value determines the central's subscription state:
         *
         * | Value | Constant | Meaning |
         * |-------|----------|---------|
         * | `[0x01, 0x00]` | ENABLE_NOTIFICATION_VALUE | Subscribe to notifications |
         * | `[0x02, 0x00]` | ENABLE_INDICATION_VALUE | Subscribe to indications |
         * | `[0x00, 0x00]` | DISABLE_NOTIFICATION_VALUE | Unsubscribe |
         *
         * When a CCCD write is received:
         * - **Enable**: The device is added to [notificationEnabledDevices]
         * - **Disable**: The device is removed from [notificationEnabledDevices]
         *
         * ## Notifications vs Indications
         *
         * - **Notifications**: Unacknowledged; faster but not guaranteed delivery
         * - **Indications**: Acknowledged; slower but confirmed delivery
         *
         * Both are treated the same in this implementation (device is added to
         * [notificationEnabledDevices]).
         *
         * @param device The remote device that initiated the write request
         * @param requestId The unique ID for this request (used in sendResponse)
         * @param descriptor The descriptor being written
         * @param preparedWrite `true` if this is a prepared write
         * @param responseNeeded `true` if the central expects a response
         * @param offset The offset for the write operation
         * @param value The descriptor value being written
         */
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
            BleLog.d(Constants.TAG, "GATT Server: Descriptor write request from ${device.address} for ${descriptor.uuid}")

            if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                // Handle Client Configuration Descriptor writes
                // Enable or disable notifications based on the value
                val notificationsEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val indicationsEnabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                val disabled = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

                when {
                    notificationsEnabled || indicationsEnabled -> {
                        notificationEnabledDevices.add(device)
                        BleLog.d(Constants.TAG, "Notifications enabled for ${device.address} on ${descriptor.characteristic?.uuid}")
                    }
                    disabled -> {
                        notificationEnabledDevices.remove(device)
                        BleLog.d(Constants.TAG, "Notifications disabled for ${device.address} on ${descriptor.characteristic?.uuid}")
                    }
                }
            }

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

        /**
         * Called when a central device requests to read a descriptor value.
         *
         * For the Client Characteristic Configuration Descriptor (CCCD), this returns
         * the current subscription state for the requesting device. For other descriptors,
         * it returns the stored descriptor value.
         *
         * ## CCCD Read Response
         *
         * The CCCD read returns the device's current subscription state:
         * - `[0x01, 0x00]`: Notifications enabled
         * - `[0x00, 0x00]`: Notifications disabled
         *
         * This allows centrals to query their subscription state, which is useful
         * after reconnecting to restore the previous state.
         *
         * @param device The remote device that initiated the read request
         * @param requestId The unique ID for this request (used in sendResponse)
         * @param offset The offset into the descriptor value to start reading from
         * @param descriptor The descriptor being read
         */
        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            BleLog.d(Constants.TAG, "GATT Server: Descriptor read request from ${device.address} for ${descriptor.uuid}")

            val value = if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                // Return whether notifications are enabled for this device
                if (notificationEnabledDevices.contains(device)) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            } else {
                descriptor.value ?: ByteArray(0)
            }

            bluetoothGattServer?.sendResponse(
                device,
                requestId,
                android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                offset,
                value
            )
        }

        /**
         * Called when the MTU (Maximum Transmission Unit) is negotiated with a central device.
         *
         * The MTU determines the maximum size of data that can be sent in a single
         * BLE packet. The default MTU is 23 bytes (20 bytes of payload for characteristics),
         * but both devices can negotiate a larger MTU for better throughput.
         *
         * ## MTU and Payload Size
         *
         * - ATT header: 3 bytes
         * - Maximum characteristic value: MTU - 3 bytes
         * - Default MTU (23): 20 bytes of characteristic data
         * - Maximum MTU (517): 512 bytes of characteristic data
         *
         * ## Usage Tip
         *
         * Store the negotiated MTU if you need to send large data efficiently:
         *
         * ```kotlin
         * private var currentMtu = 23 // Default MTU
         *
         * override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
         *     currentMtu = mtu
         *     val maxPayload = mtu - 3
         *     Log.d(TAG, "Can now send up to $maxPayload bytes per notification")
         * }
         * ```
         *
         * @param device The remote device that negotiated the MTU
         * @param mtu The negotiated MTU value
         */
        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            BleLog.d(Constants.TAG, "GATT Server: MTU changed to $mtu for ${device.address}")
        }

        /**
         * Called when a notification or indication has been sent to a remote device.
         *
         * This callback confirms that the notification was transmitted to the Bluetooth
         * controller. Note that for notifications (unacknowledged), this does NOT confirm
         * that the remote device received it - only that it was sent.
         *
         * For indications (acknowledged), a successful status means the remote device
         * confirmed receipt.
         *
         * ## Status Values
         *
         * - `BluetoothGatt.GATT_SUCCESS` (0): Notification sent successfully
         * - Other values: Error occurred during transmission
         *
         * ## Flow Control
         *
         * On some devices, you should wait for this callback before sending the next
         * notification to avoid overwhelming the Bluetooth stack:
         *
         * ```kotlin
         * private val notificationSent = Channel<Int>(Channel.CONFLATED)
         *
         * override fun onNotificationSent(device: BluetoothDevice, status: Int) {
         *     notificationSent.trySend(status)
         * }
         *
         * suspend fun sendNotificationWithFlowControl(uuid: UUID, value: ByteArray) {
         *     sendNotification(uuid, value)
         *     notificationSent.receive() // Wait for confirmation
         * }
         * ```
         *
         * @param device The remote device the notification was sent to
         * @param status GATT_SUCCESS if the notification was sent successfully
         */
        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            BleLog.d(Constants.TAG, "GATT Server: Notification sent to ${device.address}, status=$status")
        }

    }
}