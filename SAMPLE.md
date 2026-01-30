# BLE-X Sample Application Guide

This guide demonstrates how to use the BLE-X library in your Android application. The sample code covers all major features including scanning, connecting, reading/writing characteristics, and handling notifications.

## Table of Contents

- [Quick Start](#quick-start)
- [Setup](#setup)
- [Permissions](#permissions)
- [Scanning for Devices](#scanning-for-devices)
- [Connecting to a Device](#connecting-to-a-device)
- [Lifecycle-Aware Connections](#lifecycle-aware-connections)
- [Reading Characteristics](#reading-characteristics)
- [Writing Characteristics](#writing-characteristics)
- [Notifications](#notifications)
- [Bonding/Pairing](#bondingpairing)
- [Connection States](#connection-states)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Complete Activity Example](#complete-activity-example)
- [Fragment-Based Architecture](#fragment-based-architecture)

## Quick Start

```kotlin
class MyActivity : AppCompatActivity() {
    private lateinit var bleManager: BleManager
    private var connection: LifecycleAwareBleConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLE Manager
        bleManager = BleManager.getInstance(this)

        // Scan and connect to first device found
        lifecycleScope.launch {
            bleManager.scanDevices()
                .take(1)
                .collect { device ->
                    connection = bleManager.connect(device)
                        .bindToLifecycle(this@MyActivity)
                }
        }
    }
}
```

## Setup

### 1. Add the Dependency

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation("io.github.romantsisyk:blex:1.0.0")
}
```

### 2. Configure AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required for Android 12+ (API 31+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

    <!-- Required for Android 11 and below -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Declare BLE feature requirement -->
    <uses-feature
        android:name="android.hardware.bluetooth_le"
        android:required="true" />

    <application
        android:name=".MyApplication"
        ...>

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

## Permissions

### Requesting Permissions (Android 12+)

```kotlin
class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Ready to use BLE
            startScanning()
        } else {
            // Show rationale or handle denial
            showPermissionRationale()
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startScanning()
        }
    }
}
```

## Scanning for Devices

### Basic Scanning

```kotlin
val bleManager = BleManager.getInstance(context)

// Scan for devices using Flow
lifecycleScope.launch {
    bleManager.scanDevices()
        .catch { e ->
            Log.e("BLE", "Scan error: ${e.message}")
        }
        .collect { device ->
            Log.d("BLE", "Found: ${device.name} - ${device.address}")
        }
}
```

### Scanning with Timeout

```kotlin
lifecycleScope.launch {
    // Scan for 10 seconds
    withTimeoutOrNull(10_000) {
        bleManager.scanDevices().collect { device ->
            deviceList.add(device)
        }
    }
    // Scan automatically stops after timeout
}
```

### Scanning with Custom Filters

```kotlin
val bleScanner = BleScanner(context, bluetoothAdapter)

val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
    .setReportDelay(500)
    .build()

val scanFilters = listOf(
    ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(MY_SERVICE_UUID))
        .build()
)

lifecycleScope.launch {
    bleScanner.scanDevices(scanSettings, scanFilters)
        .collect { scanResult ->
            val device = scanResult.device
            val rssi = scanResult.rssi
            Log.d("BLE", "Found: ${device.name} RSSI: $rssi")
        }
}
```

### Background Scanning

```kotlin
// Create PendingIntent for BroadcastReceiver
val intent = Intent(context, BleScanReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(
    context,
    REQUEST_CODE,
    intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
)

val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
    .build()

// Start background scan
bleScanner.startBackgroundScan(scanSettings, emptyList(), pendingIntent)

// Stop background scan when done
bleScanner.stopBackgroundScan(pendingIntent)
```

## Connecting to a Device

### Basic Connection

```kotlin
val bleManager = BleManager.getInstance(context)
val connection = bleManager.connect(device)

// Observe connection state
lifecycleScope.launch {
    connection.connectionStateFlow.collect { state ->
        when (state) {
            is ConnectionState.Disconnected -> handleDisconnected()
            is ConnectionState.Connecting -> showLoading()
            is ConnectionState.Connected -> onConnected()
            is ConnectionState.DiscoveringServices -> showDiscovering()
            is ConnectionState.Ready -> enableOperations()
            is ConnectionState.Error -> showError(state.message)
        }
    }
}
```

### Disconnecting

```kotlin
// Disconnect but keep resources (can reconnect)
connection.disconnect()

// Or fully close and release resources
connection.close()

// Disconnect all devices
bleManager.disconnectAll()
```

## Lifecycle-Aware Connections

The `LifecycleAwareBleConnection` automatically manages connection lifecycle:

- **onStop()**: Automatically disconnects
- **onDestroy()**: Automatically closes and releases resources

```kotlin
class MyActivity : AppCompatActivity() {
    private var connection: LifecycleAwareBleConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bleManager = BleManager.getInstance(this)

        // Connection is automatically managed!
        connection = bleManager.connect(device)
            .bindToLifecycle(this)

        // No need to manually disconnect in onDestroy
    }
}
```

### Using with Fragments

```kotlin
class DeviceFragment : Fragment() {
    private var connection: LifecycleAwareBleConnection? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bleManager = BleManager.getInstance(requireContext())

        // Bind to viewLifecycleOwner for proper Fragment lifecycle handling
        connection = bleManager.connect(device)
            .bindToLifecycle(viewLifecycleOwner)
    }
}
```

## Reading Characteristics

```kotlin
// Define your UUIDs
val SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

// Read a characteristic (suspend function)
lifecycleScope.launch {
    val value = connection.readCharacteristic(SERVICE_UUID, CHAR_UUID)

    if (value != null) {
        // Process the data
        val stringValue = String(value, Charsets.UTF_8)
        Log.d("BLE", "Read value: $stringValue")
    } else {
        Log.e("BLE", "Read failed")
    }
}
```

### Reading Common Characteristics

```kotlin
// Device Name (Generic Access Service)
val GENERIC_ACCESS = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
val DEVICE_NAME = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

lifecycleScope.launch {
    val nameBytes = connection.readCharacteristic(GENERIC_ACCESS, DEVICE_NAME)
    val deviceName = nameBytes?.let { String(it, Charsets.UTF_8) } ?: "Unknown"
}

// Battery Level (Battery Service)
val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
val BATTERY_LEVEL = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

lifecycleScope.launch {
    val batteryBytes = connection.readCharacteristic(BATTERY_SERVICE, BATTERY_LEVEL)
    val batteryLevel = batteryBytes?.get(0)?.toInt()?.and(0xFF) ?: -1
    Log.d("BLE", "Battery: $batteryLevel%")
}
```

## Writing Characteristics

```kotlin
val SERVICE_UUID = UUID.fromString("your-service-uuid")
val CHAR_UUID = UUID.fromString("your-characteristic-uuid")

lifecycleScope.launch {
    // Write bytes to characteristic
    val dataToWrite = byteArrayOf(0x01, 0x02, 0x03)
    val success = connection.writeCharacteristic(SERVICE_UUID, CHAR_UUID, dataToWrite)

    if (success) {
        Log.d("BLE", "Write successful")
    } else {
        Log.e("BLE", "Write failed")
    }
}

// Writing a string
lifecycleScope.launch {
    val message = "Hello BLE"
    val success = connection.writeCharacteristic(
        SERVICE_UUID,
        CHAR_UUID,
        message.toByteArray(Charsets.UTF_8)
    )
}
```

## Notifications

### Enabling Notifications

```kotlin
val HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

connection.enableNotifications(
    HEART_RATE_SERVICE,
    HEART_RATE_MEASUREMENT
) { data ->
    // Called each time the device sends a notification
    val heartRate = parseHeartRate(data)
    runOnUiThread {
        heartRateTextView.text = "$heartRate BPM"
    }
}

// Parse heart rate data according to BLE specification
fun parseHeartRate(data: ByteArray): Int {
    if (data.isEmpty()) return -1

    val flags = data[0].toInt() and 0xFF
    val is16Bit = (flags and 0x01) != 0

    return if (is16Bit && data.size >= 3) {
        ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    } else if (data.size >= 2) {
        data[1].toInt() and 0xFF
    } else {
        -1
    }
}
```

### Disabling Notifications

```kotlin
connection.disableNotifications(HEART_RATE_SERVICE, HEART_RATE_MEASUREMENT)
```

## Bonding/Pairing

```kotlin
lifecycleScope.launch {
    connection.bond()
        .catch { e ->
            Log.e("BLE", "Bond error: ${e.message}")
        }
        .collect { bondState ->
            when (bondState) {
                BondState.BOND_BONDING -> showProgress("Pairing...")
                BondState.BOND_BONDED -> showSuccess("Paired!")
                BondState.BOND_NONE -> showError("Not paired")
            }
        }
}
```

## Connection States

```kotlin
sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object DiscoveringServices : ConnectionState()
    object Ready : ConnectionState()  // Fully ready for operations
    data class Error(val message: String) : ConnectionState()
}
```

### State Flow Diagram

```
Disconnected -> Connecting -> Connected -> DiscoveringServices -> Ready
      ^                                                            |
      |____________________________________________________________|
                            (on disconnect)

Any State -> Error (on error)
```

## Error Handling

```kotlin
lifecycleScope.launch {
    try {
        // Wrap BLE operations in try-catch
        bleManager.scanDevices()
            .catch { e ->
                when (e) {
                    is SecurityException -> requestPermissions()
                    else -> showError("Scan failed: ${e.message}")
                }
            }
            .collect { device -> /* ... */ }
    } catch (e: Exception) {
        Log.e("BLE", "Unexpected error", e)
    }
}

// Handle connection errors via state flow
connection.connectionStateFlow.collect { state ->
    if (state is ConnectionState.Error) {
        when {
            state.message.contains("133") -> {
                // GATT error 133 - try reconnecting
                delay(1000)
                connection.connect()
            }
            state.message.contains("timeout") -> {
                showError("Connection timed out")
            }
            else -> {
                showError(state.message)
            }
        }
    }
}
```

## Best Practices

### 1. Always Use Lifecycle-Aware Connections

```kotlin
// Good - automatic cleanup
val connection = bleManager.connect(device).bindToLifecycle(this)

// Avoid - manual cleanup required
val connection = bleManager.connect(device)
// Must remember to call close() in onDestroy()
```

### 2. Handle Permissions Properly

```kotlin
// Check permissions before every BLE operation
if (!hasRequiredPermissions()) {
    requestPermissions()
    return
}
startScanning()
```

### 3. Use Coroutines for BLE Operations

```kotlin
// Good - non-blocking, cancelable
lifecycleScope.launch {
    val value = connection.readCharacteristic(serviceUuid, charUuid)
}

// Avoid - blocking the main thread
// val value = runBlocking { connection.readCharacteristic(...) }
```

### 4. Scan Responsibly

```kotlin
// Good - limited scan duration
withTimeoutOrNull(10_000) {
    bleManager.scanDevices().collect { /* ... */ }
}

// Avoid - scanning indefinitely
// bleManager.scanDevices().collect { /* ... */ }  // Never stops!
```

### 5. Handle Configuration Changes

```kotlin
class MyViewModel : ViewModel() {
    // Keep BLE state in ViewModel to survive configuration changes
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices
}
```

## Complete Activity Example

See the full implementation at:
`app/src/main/java/io/github/romantsisyk/blex/sample/MainActivity.kt`

## Fragment-Based Architecture

For a complete Fragment-based implementation with scanning and device detail screens, see:
- `app/src/main/java/io/github/romantsisyk/blex/sample/ScanFragment.kt`
- `app/src/main/java/io/github/romantsisyk/blex/sample/DeviceFragment.kt`

### Navigation Pattern

```kotlin
class MainActivity : AppCompatActivity(), ScanFragment.OnDeviceSelectedListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ScanFragment.newInstance())
                .commit()
        }
    }

    override fun onDeviceSelected(device: BluetoothDevice) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, DeviceFragment.newInstance(device.address))
            .addToBackStack(null)
            .commit()
    }
}
```

## Additional Resources

- [Android BLE Guide](https://developer.android.com/develop/connectivity/bluetooth/ble)
- [Bluetooth GATT Specifications](https://www.bluetooth.com/specifications/specs/)
- [BLE-X API Documentation](./app/build/dokka/index.html)

## Support

For issues and feature requests, please visit:
https://github.com/romantsisyk/blex/issues
