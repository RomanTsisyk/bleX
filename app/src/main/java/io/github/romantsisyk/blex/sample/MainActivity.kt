package io.github.romantsisyk.blex.sample

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romantsisyk.blex.BleManager
import io.github.romantsisyk.blex.connection.BleConnection
import io.github.romantsisyk.blex.lifecycle.LifecycleAwareBleConnection
import io.github.romantsisyk.blex.lifecycle.bindToLifecycle
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Sample MainActivity demonstrating the complete BLE-X library usage.
 *
 * This activity showcases:
 * - Requesting BLE permissions (Android 12+ compatible)
 * - Scanning for nearby BLE devices
 * - Connecting to a selected device with lifecycle awareness
 * - Reading and writing GATT characteristics
 * - Enabling/disabling notifications
 * - Proper lifecycle handling and resource cleanup
 *
 * ## Setup Requirements
 *
 * 1. Add the following permissions to your AndroidManifest.xml:
 *    ```xml
 *    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
 *    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
 *    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
 *    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
 *    ```
 *
 * 2. Declare the activity in your manifest:
 *    ```xml
 *    <activity
 *        android:name=".sample.MainActivity"
 *        android:exported="true">
 *        <intent-filter>
 *            <action android:name="android.intent.action.MAIN" />
 *            <category android:name="android.intent.category.LAUNCHER" />
 *        </intent-filter>
 *    </activity>
 *    ```
 *
 * ## Usage Flow
 *
 * 1. User launches the app
 * 2. App requests necessary BLE permissions
 * 3. User taps "Scan" to discover nearby BLE devices
 * 4. User selects a device from the list to connect
 * 5. App establishes connection and discovers services
 * 6. User can read/write characteristics and enable notifications
 * 7. Connection is automatically cleaned up when activity is destroyed
 *
 * @see BleManager
 * @see LifecycleAwareBleConnection
 * @see ScanResultAdapter
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BleX-Sample"

        // Example service and characteristic UUIDs (Heart Rate Service)
        // Replace these with your actual device's UUIDs
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BODY_SENSOR_LOCATION_UUID: UUID = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb")

        // Generic Access Service (available on most BLE devices)
        val GENERIC_ACCESS_SERVICE_UUID: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val DEVICE_NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
    }

    // BLE Manager - singleton instance
    private lateinit var bleManager: BleManager

    // Current lifecycle-aware connection (automatically manages connect/disconnect)
    private var currentConnection: LifecycleAwareBleConnection? = null

    // Job for the scanning coroutine (allows cancellation)
    private var scanJob: Job? = null

    // Flag to track scanning state
    private var isScanning = false

    // List of discovered devices
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // UI Components (in a real app, use View Binding or Compose)
    private lateinit var statusTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var readButton: Button
    private lateinit var writeButton: Button
    private lateinit var notifyButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var deviceRecyclerView: RecyclerView
    private lateinit var deviceAdapter: ScanResultAdapter

    /**
     * Permission launcher for BLE permissions.
     *
     * On Android 12+, we need BLUETOOTH_SCAN and BLUETOOTH_CONNECT.
     * On older versions, we need ACCESS_FINE_LOCATION.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All BLE permissions granted")
            updateStatus("Permissions granted. Ready to scan.")
        } else {
            Log.w(TAG, "Some BLE permissions denied: $permissions")
            updateStatus("Permissions denied. BLE features unavailable.")
            Toast.makeText(
                this,
                "BLE permissions are required for this app to function",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // In a real app, use setContentView with your layout XML
        // For this sample, we'll create a simple programmatic layout
        // setContentView(R.layout.activity_main)
        setupProgrammaticLayout()

        // Initialize the BLE Manager singleton
        bleManager = BleManager.getInstance(this)

        // Setup RecyclerView adapter
        setupDeviceList()

        // Setup button click listeners
        setupClickListeners()

        // Check and request permissions
        checkAndRequestPermissions()
    }

    /**
     * Creates a simple programmatic layout for demonstration.
     * In production, use XML layouts or Jetpack Compose.
     */
    private fun setupProgrammaticLayout() {
        // This is a simplified example - in a real app, define layouts in XML
        val rootLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        statusTextView = TextView(this).apply {
            text = "BLE-X Sample App"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(statusTextView)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        rootLayout.addView(progressBar)

        // Button row
        val buttonLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }

        scanButton = Button(this).apply { text = "Scan" }
        disconnectButton = Button(this).apply {
            text = "Disconnect"
            isEnabled = false
        }
        buttonLayout.addView(scanButton)
        buttonLayout.addView(disconnectButton)
        rootLayout.addView(buttonLayout)

        // Operation buttons
        val opsLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }

        readButton = Button(this).apply {
            text = "Read"
            isEnabled = false
        }
        writeButton = Button(this).apply {
            text = "Write"
            isEnabled = false
        }
        notifyButton = Button(this).apply {
            text = "Notify"
            isEnabled = false
        }
        opsLayout.addView(readButton)
        opsLayout.addView(writeButton)
        opsLayout.addView(notifyButton)
        rootLayout.addView(opsLayout)

        // Device list
        deviceRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        rootLayout.addView(deviceRecyclerView)

        setContentView(rootLayout)
    }

    /**
     * Sets up the RecyclerView adapter for displaying discovered devices.
     */
    private fun setupDeviceList() {
        deviceAdapter = ScanResultAdapter(discoveredDevices) { device ->
            // User selected a device - connect to it
            connectToDevice(device)
        }
        deviceRecyclerView.adapter = deviceAdapter
    }

    /**
     * Sets up click listeners for all buttons.
     */
    private fun setupClickListeners() {
        scanButton.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }

        disconnectButton.setOnClickListener {
            disconnectCurrentDevice()
        }

        readButton.setOnClickListener {
            readCharacteristic()
        }

        writeButton.setOnClickListener {
            writeCharacteristic()
        }

        notifyButton.setOnClickListener {
            toggleNotifications()
        }
    }

    /**
     * Checks if BLE permissions are granted and requests them if needed.
     *
     * Android 12+ requires BLUETOOTH_SCAN and BLUETOOTH_CONNECT.
     * Older versions require ACCESS_FINE_LOCATION for BLE scanning.
     */
    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $missingPermissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All BLE permissions already granted")
            updateStatus("Ready to scan for devices.")
        }
    }

    /**
     * Starts scanning for nearby BLE devices.
     *
     * The scan uses the BleManager's scanDevices() flow which:
     * - Automatically starts scanning when collected
     * - Filters to only BLE devices (not Classic Bluetooth)
     * - Removes duplicates based on device address
     * - Stops scanning when the Job is cancelled
     */
    private fun startScanning() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "BLE permissions required", Toast.LENGTH_SHORT).show()
            checkAndRequestPermissions()
            return
        }

        // Clear previous results
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()

        isScanning = true
        scanButton.text = "Stop"
        progressBar.visibility = View.VISIBLE
        updateStatus("Scanning for BLE devices...")

        // Start scanning using a coroutine
        scanJob = bleManager.scanDevices()
            .catch { exception ->
                Log.e(TAG, "Scan error: ${exception.message}", exception)
                runOnUiThread {
                    updateStatus("Scan error: ${exception.message}")
                    stopScanning()
                }
            }
            .onEach { device ->
                // Add device to list if not already present
                if (discoveredDevices.none { it.address == device.address }) {
                    discoveredDevices.add(device)
                    runOnUiThread {
                        deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
                        updateStatus("Found ${discoveredDevices.size} device(s)")
                    }
                }
            }
            .launchIn(lifecycleScope)

        Log.d(TAG, "Started BLE scan")
    }

    /**
     * Stops the ongoing BLE scan.
     */
    private fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
        scanButton.text = "Scan"
        progressBar.visibility = View.GONE
        updateStatus("Scan stopped. Found ${discoveredDevices.size} device(s)")
        Log.d(TAG, "Stopped BLE scan")
    }

    /**
     * Connects to the specified BLE device.
     *
     * This method:
     * 1. Stops any ongoing scan
     * 2. Disconnects from any previously connected device
     * 3. Creates a new BleConnection
     * 4. Binds it to this Activity's lifecycle for automatic management
     * 5. Observes connection state changes
     */
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        // Stop scanning first
        if (isScanning) {
            stopScanning()
        }

        // Disconnect from any previous device
        disconnectCurrentDevice()

        updateStatus("Connecting to ${device.name ?: device.address}...")
        progressBar.visibility = View.VISIBLE

        try {
            // Create connection through BleManager and bind to lifecycle
            val connection = bleManager.connect(device)
                .bindToLifecycle(this)

            currentConnection = connection

            // Observe connection state changes
            observeConnectionState(connection)

            Log.d(TAG, "Initiated connection to ${device.address}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            updateStatus("Connection failed: ${e.message}")
            progressBar.visibility = View.GONE
        }
    }

    /**
     * Observes the connection state of the given BLE connection.
     *
     * Updates the UI based on the current connection state:
     * - Disconnected: Disable operation buttons
     * - Connecting: Show progress
     * - Connected: Show status
     * - DiscoveringServices: Show progress
     * - Ready: Enable operation buttons
     * - Error: Show error message
     */
    private fun observeConnectionState(connection: LifecycleAwareBleConnection) {
        lifecycleScope.launch {
            connection.connectionStateFlow.collect { state ->
                Log.d(TAG, "Connection state changed: $state")

                when (state) {
                    is ConnectionState.Disconnected -> {
                        updateStatus("Disconnected")
                        progressBar.visibility = View.GONE
                        setOperationButtonsEnabled(false)
                        disconnectButton.isEnabled = false
                    }

                    is ConnectionState.Connecting -> {
                        updateStatus("Connecting...")
                        progressBar.visibility = View.VISIBLE
                    }

                    is ConnectionState.Connected -> {
                        updateStatus("Connected, discovering services...")
                        disconnectButton.isEnabled = true
                    }

                    is ConnectionState.DiscoveringServices -> {
                        updateStatus("Discovering services...")
                    }

                    is ConnectionState.Ready -> {
                        updateStatus("Connected and ready!")
                        progressBar.visibility = View.GONE
                        setOperationButtonsEnabled(true)
                        Toast.makeText(
                            this@MainActivity,
                            "Device ready for operations",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is ConnectionState.Error -> {
                        updateStatus("Error: ${state.message}")
                        progressBar.visibility = View.GONE
                        setOperationButtonsEnabled(false)
                        Toast.makeText(
                            this@MainActivity,
                            "Connection error: ${state.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Disconnects from the currently connected device.
     */
    private fun disconnectCurrentDevice() {
        currentConnection?.let { connection ->
            Log.d(TAG, "Disconnecting from current device")
            connection.disconnect()
            // Note: close() is called automatically by lifecycle observer on destroy
        }
        currentConnection = null
        setOperationButtonsEnabled(false)
        disconnectButton.isEnabled = false
    }

    /**
     * Reads a characteristic from the connected device.
     *
     * This example reads the Device Name characteristic from the Generic Access Service,
     * which is available on most BLE devices.
     */
    private fun readCharacteristic() {
        val connection = currentConnection ?: run {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            updateStatus("Reading characteristic...")

            try {
                // Read the Device Name characteristic (available on most devices)
                val value = connection.readCharacteristic(
                    GENERIC_ACCESS_SERVICE_UUID,
                    DEVICE_NAME_CHARACTERISTIC_UUID
                )

                if (value != null) {
                    val deviceName = String(value, Charsets.UTF_8)
                    Log.d(TAG, "Read device name: $deviceName")
                    updateStatus("Device name: $deviceName")
                    Toast.makeText(
                        this@MainActivity,
                        "Device name: $deviceName",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.w(TAG, "Failed to read characteristic")
                    updateStatus("Read failed - characteristic may not exist")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Read error: ${e.message}", e)
                updateStatus("Read error: ${e.message}")
            }
        }
    }

    /**
     * Writes a value to a characteristic on the connected device.
     *
     * This is an example - you would need to use a writable characteristic
     * specific to your device.
     */
    private fun writeCharacteristic() {
        val connection = currentConnection ?: run {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            updateStatus("Writing characteristic...")

            try {
                // Example: Write to a custom characteristic
                // Replace with your device's writable characteristic UUID
                val dataToWrite = byteArrayOf(0x01, 0x02, 0x03)

                // Note: This will fail if the characteristic doesn't exist or isn't writable
                // This is just an example of how to use the API
                val success = connection.writeCharacteristic(
                    HEART_RATE_SERVICE_UUID,  // Replace with your service UUID
                    BODY_SENSOR_LOCATION_UUID, // Replace with your characteristic UUID
                    dataToWrite
                )

                if (success) {
                    Log.d(TAG, "Write successful")
                    updateStatus("Write successful!")
                    Toast.makeText(this@MainActivity, "Write successful", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Write failed")
                    updateStatus("Write failed - check characteristic properties")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Write error: ${e.message}", e)
                updateStatus("Write error: ${e.message}")
            }
        }
    }

    // Track notification state
    private var notificationsEnabled = false

    /**
     * Toggles notifications for a characteristic.
     *
     * This example uses the Heart Rate Measurement characteristic,
     * which supports notifications. Replace with your device's characteristic.
     */
    private fun toggleNotifications() {
        val connection = currentConnection ?: run {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        if (notificationsEnabled) {
            // Disable notifications
            connection.disableNotifications(
                HEART_RATE_SERVICE_UUID,
                HEART_RATE_MEASUREMENT_UUID
            )
            notificationsEnabled = false
            notifyButton.text = "Notify"
            updateStatus("Notifications disabled")
            Log.d(TAG, "Notifications disabled")

        } else {
            // Enable notifications
            connection.enableNotifications(
                HEART_RATE_SERVICE_UUID,
                HEART_RATE_MEASUREMENT_UUID
            ) { value ->
                // This callback is invoked whenever the device sends a notification
                runOnUiThread {
                    val hexValue = value.joinToString(" ") { String.format("%02X", it) }
                    Log.d(TAG, "Notification received: $hexValue")
                    updateStatus("Notification: $hexValue")
                }
            }
            notificationsEnabled = true
            notifyButton.text = "Stop Notify"
            updateStatus("Notifications enabled")
            Log.d(TAG, "Notifications enabled")
        }
    }

    /**
     * Checks if the app has the required BLE permissions.
     */
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Updates the status text view.
     */
    private fun updateStatus(message: String) {
        statusTextView.text = message
    }

    /**
     * Enables or disables the operation buttons (Read, Write, Notify).
     */
    private fun setOperationButtonsEnabled(enabled: Boolean) {
        readButton.isEnabled = enabled
        writeButton.isEnabled = enabled
        notifyButton.isEnabled = enabled
    }

    /**
     * Called when the activity is being destroyed.
     *
     * Note: With lifecycle-aware connections, cleanup is automatic.
     * The LifecycleAwareBleConnection will:
     * - Call disconnect() when the activity stops
     * - Call close() when the activity is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        // Stop scanning if still active
        scanJob?.cancel()

        // Note: No need to manually disconnect - LifecycleAwareBleConnection handles this!
        // If you weren't using lifecycle-aware connections, you would need:
        // currentConnection?.disconnect()
        // bleManager.disconnectAll()

        Log.d(TAG, "MainActivity destroyed")
    }
}
