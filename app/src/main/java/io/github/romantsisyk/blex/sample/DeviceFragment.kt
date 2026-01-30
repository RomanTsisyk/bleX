package io.github.romantsisyk.blex.sample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.romantsisyk.blex.BleManager
import io.github.romantsisyk.blex.connection.BleConnection
import io.github.romantsisyk.blex.lifecycle.LifecycleAwareBleConnection
import io.github.romantsisyk.blex.lifecycle.bindToLifecycle
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Fragment demonstrating BLE device connection and operations with BLE-X library.
 *
 * This fragment shows how to:
 * - Connect to a BLE device with lifecycle awareness
 * - Monitor connection state changes
 * - Perform GATT operations (read, write, notifications)
 * - Handle device bonding/pairing
 * - Properly manage resources and cleanup
 *
 * ## Architecture Pattern
 *
 * This fragment follows the recommended pattern for BLE operations:
 *
 * 1. **Lifecycle-Aware Connection**: Uses [LifecycleAwareBleConnection] which automatically
 *    disconnects when the fragment stops and closes when destroyed.
 *
 * 2. **State Observation**: Observes [ConnectionState] flow to update UI accordingly.
 *
 * 3. **Suspend Functions**: Uses Kotlin coroutines for read/write operations.
 *
 * ## Usage
 *
 * ```kotlin
 * // Navigate to this fragment with a device address:
 * val fragment = DeviceFragment.newInstance("AA:BB:CC:DD:EE:FF")
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.container, fragment)
 *     .addToBackStack(null)
 *     .commit()
 * ```
 *
 * ## Lifecycle Handling
 *
 * The fragment automatically handles:
 * - **onViewCreated**: Initiates connection
 * - **onStop**: Connection is automatically disconnected (via LifecycleAwareBleConnection)
 * - **onDestroyView**: Connection is automatically closed (via LifecycleAwareBleConnection)
 *
 * @see ScanFragment
 * @see LifecycleAwareBleConnection
 * @see ConnectionState
 */
class DeviceFragment : Fragment() {

    companion object {
        private const val TAG = "BleX-DeviceFragment"
        private const val ARG_DEVICE_ADDRESS = "device_address"

        // Standard BLE UUIDs for demonstration
        val GENERIC_ACCESS_SERVICE: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val DEVICE_NAME_CHAR: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        val APPEARANCE_CHAR: UUID = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb")

        val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_CHAR: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_CHAR: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_CHAR: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")

        // Heart Rate Service (common for fitness devices)
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        // Battery Service
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        /**
         * Creates a new instance of DeviceFragment for the given device address.
         *
         * @param deviceAddress The MAC address of the BLE device to connect to.
         * @return A new DeviceFragment instance.
         */
        fun newInstance(deviceAddress: String): DeviceFragment {
            return DeviceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_ADDRESS, deviceAddress)
                }
            }
        }
    }

    private var deviceAddress: String? = null
    private var connection: LifecycleAwareBleConnection? = null
    private var notificationsEnabled = false

    // UI Components
    private lateinit var deviceNameTextView: TextView
    private lateinit var deviceAddressTextView: TextView
    private lateinit var connectionStateTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView

    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var bondButton: Button
    private lateinit var readDeviceInfoButton: Button
    private lateinit var readBatteryButton: Button
    private lateinit var toggleNotificationsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceAddress = arguments?.getString(ARG_DEVICE_ADDRESS)

        if (deviceAddress == null) {
            Log.e(TAG, "No device address provided")
            Toast.makeText(requireContext(), "No device address provided", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // In production, use: inflater.inflate(R.layout.fragment_device, container, false)
        return createProgrammaticLayout()
    }

    /**
     * Creates a programmatic layout for demonstration.
     * In production, define this in res/layout/fragment_device.xml
     */
    private fun createProgrammaticLayout(): View {
        scrollView = ScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Device info section
        deviceNameTextView = TextView(requireContext()).apply {
            text = "Device: Unknown"
            textSize = 18f
        }
        rootLayout.addView(deviceNameTextView)

        deviceAddressTextView = TextView(requireContext()).apply {
            text = "Address: ${deviceAddress ?: "N/A"}"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
        }
        rootLayout.addView(deviceAddressTextView)

        // Connection state
        connectionStateTextView = TextView(requireContext()).apply {
            text = "State: Disconnected"
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        rootLayout.addView(connectionStateTextView)

        // Progress bar
        progressBar = ProgressBar(
            requireContext(),
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        rootLayout.addView(progressBar)

        // Connection buttons row
        val connectionButtonsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 8)
        }

        connectButton = Button(requireContext()).apply {
            text = "Connect"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        connectionButtonsLayout.addView(connectButton)

        disconnectButton = Button(requireContext()).apply {
            text = "Disconnect"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        connectionButtonsLayout.addView(disconnectButton)

        bondButton = Button(requireContext()).apply {
            text = "Bond"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        connectionButtonsLayout.addView(bondButton)

        rootLayout.addView(connectionButtonsLayout)

        // Operation buttons row
        val operationButtonsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 16)
        }

        readDeviceInfoButton = Button(requireContext()).apply {
            text = "Device Info"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        operationButtonsLayout.addView(readDeviceInfoButton)

        readBatteryButton = Button(requireContext()).apply {
            text = "Battery"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        operationButtonsLayout.addView(readBatteryButton)

        toggleNotificationsButton = Button(requireContext()).apply {
            text = "Heart Rate"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        operationButtonsLayout.addView(toggleNotificationsButton)

        rootLayout.addView(operationButtonsLayout)

        // Log section
        rootLayout.addView(TextView(requireContext()).apply {
            text = "Log:"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        })

        logTextView = TextView(requireContext()).apply {
            text = ""
            textSize = 12f
            setTextColor(0xFF333333.toInt())
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(16, 16, 16, 16)
            minHeight = 400
        }
        rootLayout.addView(logTextView)

        scrollView.addView(rootLayout)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()

        // Try to get the device and update UI
        getBluetoothDevice()?.let { device ->
            updateDeviceName(device)
        }

        appendLog("Ready to connect to $deviceAddress")
    }

    @SuppressLint("MissingPermission")
    private fun updateDeviceName(device: BluetoothDevice) {
        try {
            val name = device.name ?: "Unknown Device"
            deviceNameTextView.text = "Device: $name"
        } catch (e: SecurityException) {
            deviceNameTextView.text = "Device: Unknown (permission denied)"
        }
    }

    /**
     * Sets up click listeners for all buttons.
     */
    private fun setupClickListeners() {
        connectButton.setOnClickListener {
            connectToDevice()
        }

        disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }

        bondButton.setOnClickListener {
            bondWithDevice()
        }

        readDeviceInfoButton.setOnClickListener {
            readDeviceInformation()
        }

        readBatteryButton.setOnClickListener {
            readBatteryLevel()
        }

        toggleNotificationsButton.setOnClickListener {
            toggleHeartRateNotifications()
        }
    }

    /**
     * Gets the BluetoothDevice from the address.
     */
    @SuppressLint("MissingPermission")
    private fun getBluetoothDevice(): BluetoothDevice? {
        val address = deviceAddress ?: return null

        return try {
            val bluetoothManager =
                requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter = bluetoothManager.adapter
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get BluetoothDevice: ${e.message}", e)
            null
        }
    }

    /**
     * Connects to the BLE device.
     *
     * Creates a lifecycle-aware connection that automatically handles
     * disconnect on stop and close on destroy.
     */
    private fun connectToDevice() {
        val device = getBluetoothDevice() ?: run {
            appendLog("ERROR: Could not get BluetoothDevice")
            return
        }

        appendLog("Connecting to ${device.address}...")
        progressBar.visibility = View.VISIBLE
        connectButton.isEnabled = false

        try {
            // Get BleManager and create connection
            val bleManager = BleManager.getInstance(requireContext())
            val bleConnection = bleManager.connect(device)

            // Bind to this fragment's lifecycle for automatic management
            connection = bleConnection.bindToLifecycle(viewLifecycleOwner)

            // Observe connection state
            observeConnectionState()

            appendLog("Connection initiated")

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            appendLog("ERROR: Connection failed - ${e.message}")
            progressBar.visibility = View.GONE
            connectButton.isEnabled = true
        }
    }

    /**
     * Observes the connection state flow and updates UI accordingly.
     */
    private fun observeConnectionState() {
        connection?.connectionStateFlow
            ?.onEach { state ->
                Log.d(TAG, "Connection state: $state")
                handleConnectionState(state)
            }
            ?.catch { e ->
                Log.e(TAG, "Error observing connection state: ${e.message}", e)
                appendLog("ERROR: ${e.message}")
            }
            ?.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Handles connection state changes and updates the UI.
     */
    private fun handleConnectionState(state: ConnectionState) {
        activity?.runOnUiThread {
            connectionStateTextView.text = "State: ${state::class.simpleName}"

            when (state) {
                is ConnectionState.Disconnected -> {
                    appendLog("Disconnected")
                    progressBar.visibility = View.GONE
                    setOperationButtonsEnabled(false)
                    connectButton.isEnabled = true
                    disconnectButton.isEnabled = false
                    bondButton.isEnabled = false
                    notificationsEnabled = false
                    toggleNotificationsButton.text = "Heart Rate"
                }

                is ConnectionState.Connecting -> {
                    appendLog("Connecting...")
                    progressBar.visibility = View.VISIBLE
                }

                is ConnectionState.Connected -> {
                    appendLog("Connected, discovering services...")
                    disconnectButton.isEnabled = true
                    bondButton.isEnabled = true
                }

                is ConnectionState.DiscoveringServices -> {
                    appendLog("Discovering services...")
                }

                is ConnectionState.Ready -> {
                    appendLog("Ready for operations!")
                    progressBar.visibility = View.GONE
                    setOperationButtonsEnabled(true)
                    connectButton.isEnabled = false

                    Toast.makeText(
                        requireContext(),
                        "Device ready",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is ConnectionState.Error -> {
                    appendLog("ERROR: ${state.message}")
                    progressBar.visibility = View.GONE
                    setOperationButtonsEnabled(false)
                    connectButton.isEnabled = true
                    disconnectButton.isEnabled = false

                    Toast.makeText(
                        requireContext(),
                        "Error: ${state.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Disconnects from the current device.
     */
    private fun disconnectFromDevice() {
        appendLog("Disconnecting...")
        connection?.disconnect()
    }

    /**
     * Initiates bonding (pairing) with the device.
     */
    private fun bondWithDevice() {
        val conn = connection ?: run {
            appendLog("Not connected")
            return
        }

        appendLog("Initiating bond...")

        conn.bond()
            .onEach { bondState ->
                activity?.runOnUiThread {
                    when (bondState) {
                        BondState.BOND_BONDING -> appendLog("Bonding in progress...")
                        BondState.BOND_BONDED -> appendLog("Bonded successfully!")
                        BondState.BOND_NONE -> appendLog("Not bonded")
                    }
                }
            }
            .catch { e ->
                Log.e(TAG, "Bond error: ${e.message}", e)
                activity?.runOnUiThread {
                    appendLog("Bond error: ${e.message}")
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /**
     * Reads device information characteristics.
     *
     * Demonstrates reading multiple characteristics from standard
     * Device Information Service (0x180A).
     */
    private fun readDeviceInformation() {
        val conn = connection ?: run {
            appendLog("Not connected")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appendLog("Reading device information...")

            // Read Device Name from Generic Access Service
            val deviceName = conn.readCharacteristic(GENERIC_ACCESS_SERVICE, DEVICE_NAME_CHAR)
            if (deviceName != null) {
                appendLog("Device Name: ${String(deviceName, Charsets.UTF_8)}")
            } else {
                appendLog("Device Name: (not available)")
            }

            // Read Manufacturer Name from Device Information Service
            val manufacturer = conn.readCharacteristic(DEVICE_INFO_SERVICE, MANUFACTURER_NAME_CHAR)
            if (manufacturer != null) {
                appendLog("Manufacturer: ${String(manufacturer, Charsets.UTF_8)}")
            } else {
                appendLog("Manufacturer: (not available)")
            }

            // Read Model Number
            val model = conn.readCharacteristic(DEVICE_INFO_SERVICE, MODEL_NUMBER_CHAR)
            if (model != null) {
                appendLog("Model: ${String(model, Charsets.UTF_8)}")
            } else {
                appendLog("Model: (not available)")
            }

            // Read Firmware Revision
            val firmware = conn.readCharacteristic(DEVICE_INFO_SERVICE, FIRMWARE_REVISION_CHAR)
            if (firmware != null) {
                appendLog("Firmware: ${String(firmware, Charsets.UTF_8)}")
            } else {
                appendLog("Firmware: (not available)")
            }

            appendLog("Device info read complete")
        }
    }

    /**
     * Reads the battery level from the Battery Service.
     *
     * The battery level characteristic returns a single byte representing
     * the battery percentage (0-100).
     */
    private fun readBatteryLevel() {
        val conn = connection ?: run {
            appendLog("Not connected")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appendLog("Reading battery level...")

            val batteryLevel = conn.readCharacteristic(BATTERY_SERVICE, BATTERY_LEVEL_CHAR)

            if (batteryLevel != null && batteryLevel.isNotEmpty()) {
                val percentage = batteryLevel[0].toInt() and 0xFF
                appendLog("Battery Level: $percentage%")
                Toast.makeText(
                    requireContext(),
                    "Battery: $percentage%",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                appendLog("Battery level not available (service may not exist)")
            }
        }
    }

    /**
     * Toggles heart rate notifications on/off.
     *
     * Demonstrates enabling/disabling notifications for a characteristic.
     * The Heart Rate Measurement characteristic sends notifications with
     * heart rate data.
     */
    private fun toggleHeartRateNotifications() {
        val conn = connection ?: run {
            appendLog("Not connected")
            return
        }

        if (notificationsEnabled) {
            // Disable notifications
            conn.disableNotifications(HEART_RATE_SERVICE, HEART_RATE_MEASUREMENT_CHAR)
            notificationsEnabled = false
            toggleNotificationsButton.text = "Heart Rate"
            appendLog("Heart rate notifications disabled")
        } else {
            // Enable notifications
            conn.enableNotifications(
                HEART_RATE_SERVICE,
                HEART_RATE_MEASUREMENT_CHAR
            ) { data ->
                // Parse heart rate data
                // Format: Flags (1 byte) + Heart Rate Value (1 or 2 bytes) + optional fields
                val heartRate = if (data.isNotEmpty()) {
                    val flags = data[0].toInt() and 0xFF
                    val isHeartRate16Bit = (flags and 0x01) != 0

                    if (isHeartRate16Bit && data.size >= 3) {
                        // 16-bit heart rate value
                        ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    } else if (data.size >= 2) {
                        // 8-bit heart rate value
                        data[1].toInt() and 0xFF
                    } else {
                        -1
                    }
                } else {
                    -1
                }

                activity?.runOnUiThread {
                    if (heartRate >= 0) {
                        appendLog("Heart Rate: $heartRate BPM")
                    } else {
                        val hex = data.joinToString(" ") { String.format("%02X", it) }
                        appendLog("Heart Rate data: $hex")
                    }
                }
            }
            notificationsEnabled = true
            toggleNotificationsButton.text = "Stop HR"
            appendLog("Heart rate notifications enabled")
        }
    }

    /**
     * Enables or disables operation buttons.
     */
    private fun setOperationButtonsEnabled(enabled: Boolean) {
        readDeviceInfoButton.isEnabled = enabled
        readBatteryButton.isEnabled = enabled
        toggleNotificationsButton.isEnabled = enabled
    }

    /**
     * Appends a message to the log text view.
     */
    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

        activity?.runOnUiThread {
            val currentText = logTextView.text.toString()
            logTextView.text = "$currentText\n[$timestamp] $message"

            // Scroll to bottom
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }

        Log.d(TAG, message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Note: Connection cleanup is automatic with LifecycleAwareBleConnection
        // The connection will be closed when the viewLifecycleOwner is destroyed
        Log.d(TAG, "DeviceFragment view destroyed")
    }
}
