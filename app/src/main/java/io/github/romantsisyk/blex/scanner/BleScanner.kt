package io.github.romantsisyk.blex.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A Bluetooth Low Energy (BLE) scanner that provides reactive scanning capabilities using Kotlin Flows.
 *
 * This class wraps the Android [BluetoothLeScanner] API and exposes BLE scanning functionality
 * through a coroutine-based [Flow] interface, making it easy to collect scan results in a
 * lifecycle-aware manner. It also supports background scanning using [android.app.PendingIntent]
 * for scenarios where the app needs to receive scan results even when not in the foreground.
 *
 * ## Permissions
 *
 * Before using this scanner, ensure the following permissions are granted:
 * - `android.permission.BLUETOOTH_SCAN` (Android 12+)
 * - `android.permission.BLUETOOTH` and `android.permission.BLUETOOTH_ADMIN` (Android 11 and below)
 * - `android.permission.ACCESS_FINE_LOCATION` (required for BLE scanning on most Android versions)
 *
 * ## Flow Lifecycle
 *
 * The [scanDevices] method returns a cold [Flow] that:
 * - Starts scanning when the Flow is collected
 * - Emits [ScanResult] objects as BLE devices are discovered
 * - Automatically stops scanning when the collector cancels or the coroutine scope is cancelled
 * - Handles cleanup via [awaitClose] to ensure the scan is properly stopped
 *
 * This makes it inherently lifecycle-safe when used with lifecycle-aware coroutine scopes
 * such as `viewModelScope` or `lifecycleScope`.
 *
 * ## Usage Examples
 *
 * ### Basic Scanning
 *
 * ```kotlin
 * val bleScanner = BleScanner(context, bluetoothAdapter)
 *
 * // Collect scan results in a coroutine scope
 * viewModelScope.launch {
 *     bleScanner.scanDevices()
 *         .catch { e -> Log.e("BLE", "Scan error: ${e.message}") }
 *         .collect { scanResult ->
 *             val device = scanResult.device
 *             Log.d("BLE", "Found device: ${device.name} - ${device.address}")
 *         }
 * }
 * ```
 *
 * ### Scanning with Custom Settings and Filters
 *
 * ```kotlin
 * val scanSettings = ScanSettings.Builder()
 *     .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
 *     .setReportDelay(1000) // Batch results every 1 second
 *     .build()
 *
 * val scanFilters = listOf(
 *     ScanFilter.Builder()
 *         .setServiceUuid(ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")))
 *         .build()
 * )
 *
 * viewModelScope.launch {
 *     bleScanner.scanDevices(scanSettings, scanFilters)
 *         .collect { scanResult ->
 *             // Only receives devices matching the filter
 *         }
 * }
 * ```
 *
 * ### Timeout-Based Scanning
 *
 * ```kotlin
 * viewModelScope.launch {
 *     withTimeoutOrNull(10_000) { // Scan for 10 seconds
 *         bleScanner.scanDevices()
 *             .collect { scanResult ->
 *                 processDevice(scanResult)
 *             }
 *     }
 *     // Scan automatically stops after timeout
 * }
 * ```
 *
 * ### Background Scanning
 *
 * ```kotlin
 * // Create a PendingIntent for your BroadcastReceiver
 * val intent = Intent(context, BleScanReceiver::class.java)
 * val pendingIntent = PendingIntent.getBroadcast(
 *     context,
 *     REQUEST_CODE,
 *     intent,
 *     PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
 * )
 *
 * val scanSettings = ScanSettings.Builder()
 *     .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
 *     .build()
 *
 * // Start background scan
 * bleScanner.startBackgroundScan(scanSettings, emptyList(), pendingIntent)
 *
 * // Later, stop the background scan
 * bleScanner.stopBackgroundScan(pendingIntent)
 * ```
 *
 * @property context The application or activity context used for BLE operations.
 * @property bluetoothAdapter The [BluetoothAdapter] instance to use for scanning.
 *
 * @constructor Creates a new BleScanner instance.
 *
 * @see BluetoothLeScanner
 * @see ScanResult
 * @see ScanSettings
 * @see ScanFilter
 */
class BleScanner(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {

    /**
     * Lazily retrieves the [BluetoothLeScanner] from the [BluetoothAdapter].
     *
     * This property may return `null` if Bluetooth is disabled or the device
     * does not support BLE scanning.
     */
    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    /**
     * Scans for nearby BLE devices and emits discovered devices through a reactive [Flow].
     *
     * This method creates a cold [Flow] that starts BLE scanning when collected and automatically
     * stops scanning when the collection is cancelled. The Flow is built using [callbackFlow],
     * which bridges the callback-based Android BLE API to Kotlin's coroutine-based Flow.
     *
     * ## Flow Behavior
     *
     * - **Cold Flow**: Scanning only begins when the Flow is actively collected. Multiple collectors
     *   will start separate scan sessions.
     * - **Automatic Cleanup**: When the collector's coroutine is cancelled (e.g., when a ViewModel
     *   is cleared or a lifecycle scope ends), the scan is automatically stopped via [awaitClose].
     * - **Error Handling**: Scan failures are propagated as exceptions that can be caught using
     *   the Flow's [catch][kotlinx.coroutines.flow.catch] operator.
     *
     * ## Scan Modes
     *
     * The [scanSettings] parameter controls how aggressively the scanner searches for devices:
     * - [ScanSettings.SCAN_MODE_LOW_POWER]: Minimal battery impact, slower discovery
     * - [ScanSettings.SCAN_MODE_BALANCED]: Balanced between power and latency
     * - [ScanSettings.SCAN_MODE_LOW_LATENCY]: Fastest discovery, highest battery usage (default)
     *
     * ## Thread Safety
     *
     * This method is safe to call from any coroutine context. The Flow will emit results
     * on the dispatcher of the collecting coroutine.
     *
     * ## Error Conditions
     *
     * The Flow will close with an exception in the following cases:
     * - Bluetooth is disabled on the device
     * - [BluetoothLeScanner] is unavailable (null)
     * - The underlying scan fails (error code provided in exception message)
     *
     * @param scanSettings Configuration for the BLE scan behavior, including scan mode,
     *                     report delay, and other parameters. Defaults to low-latency mode
     *                     for fastest device discovery.
     * @param scanFilters Optional list of [ScanFilter] objects to limit scan results to
     *                    specific devices or services. An empty list (default) returns
     *                    all discovered devices. Filters can match by device name, address,
     *                    service UUID, manufacturer data, and more.
     *
     * @return A [Flow] that emits [ScanResult] objects for each discovered BLE device.
     *         Each [ScanResult] contains the [android.bluetooth.BluetoothDevice], RSSI,
     *         scan record data, and other metadata about the discovered device.
     *
     * @throws Exception When Bluetooth is disabled, scanner is unavailable, or scan fails.
     *
     * @see ScanSettings
     * @see ScanFilter
     * @see ScanResult
     * @see ScanCallback
     */
    @SuppressLint("MissingPermission")
    fun scanDevices(
        scanSettings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build(),
        scanFilters: List<ScanFilter> = emptyList()
    ): Flow<ScanResult> = callbackFlow {
        if (!bluetoothAdapter.isEnabled) {
            close(Exception("Bluetooth is disabled."))
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result).isSuccess
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result ->
                    trySend(result).isSuccess
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("Scan failed with error code: $errorCode"))
            }
        }

        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            ?: run {
                close(Exception("Unable to start BLE scan."))
                return@callbackFlow
            }

        awaitClose {
            bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    /**
     * Starts a background BLE scan that delivers results via a [android.app.PendingIntent].
     *
     * Unlike [scanDevices], this method allows BLE scanning to continue even when your app
     * is not in the foreground. Scan results are delivered to a [android.content.BroadcastReceiver]
     * specified by the [pendingIntent], enabling your app to react to nearby BLE devices
     * without maintaining an active Activity or Service.
     *
     * ## Setup Requirements
     *
     * To use background scanning, you must:
     *
     * 1. **Create a BroadcastReceiver** to handle incoming scan results:
     *    ```kotlin
     *    class BleScanReceiver : BroadcastReceiver() {
     *        override fun onReceive(context: Context, intent: Intent) {
     *            val results = intent.getParcelableArrayListExtra<ScanResult>(
     *                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
     *            )
     *            results?.forEach { result ->
     *                // Process discovered device
     *            }
     *        }
     *    }
     *    ```
     *
     * 2. **Register the receiver** in your AndroidManifest.xml:
     *    ```xml
     *    <receiver android:name=".BleScanReceiver"
     *              android:exported="false" />
     *    ```
     *
     * 3. **Create a PendingIntent** targeting your receiver:
     *    ```kotlin
     *    val intent = Intent(context, BleScanReceiver::class.java)
     *    val pendingIntent = PendingIntent.getBroadcast(
     *        context, REQUEST_CODE, intent,
     *        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
     *    )
     *    ```
     *
     * ## Battery Considerations
     *
     * Background scanning can significantly impact battery life. Consider using:
     * - [ScanSettings.SCAN_MODE_LOW_POWER] or [ScanSettings.SCAN_MODE_OPPORTUNISTIC]
     * - Specific [ScanFilter] entries to reduce wake-ups
     * - Appropriate scan intervals based on your use case
     *
     * ## Platform Limitations
     *
     * - On Android 8.0+, background scans without filters may be throttled or ignored
     * - Some devices may limit the number of concurrent background scans
     * - Results may be batched and delivered with delays to conserve battery
     *
     * @param scanSettings Configuration for the scan, including scan mode and report delay.
     *                     Use [ScanSettings.SCAN_MODE_LOW_POWER] for battery-efficient scanning.
     * @param scanFilters List of [ScanFilter] objects to limit results. On Android 8.0+,
     *                    providing at least one filter is strongly recommended for reliable
     *                    background scanning.
     * @param pendingIntent The [android.app.PendingIntent] that will be broadcast when scan
     *                      results are available. Must target a registered BroadcastReceiver.
     *                      Use the same PendingIntent instance with [stopBackgroundScan] to
     *                      stop scanning.
     *
     * @see stopBackgroundScan
     * @see BluetoothLeScanner.startScan
     */
    @SuppressLint("MissingPermission")
    fun startBackgroundScan(
        scanSettings: ScanSettings,
        scanFilters: List<ScanFilter>,
        pendingIntent: android.app.PendingIntent
    ) {
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, pendingIntent)
    }

    /**
     * Stops an ongoing background BLE scan that was previously started with [startBackgroundScan].
     *
     * This method should be called when you no longer need to receive background scan results,
     * such as when:
     * - The user disables a feature that requires BLE discovery
     * - The app has found the target device
     * - The app is being uninstalled or reset
     *
     * ## Important
     *
     * - You must pass the **exact same [android.app.PendingIntent]** instance (or an equivalent one
     *   with the same request code, intent action, and flags) that was used to start the scan.
     * - If the PendingIntent does not match, the scan will continue running.
     * - Calling this method when no background scan is active is safe and has no effect.
     *
     * ## Resource Cleanup
     *
     * Failing to stop background scans can lead to:
     * - Excessive battery drain
     * - Unnecessary BroadcastReceiver invocations
     * - Potential throttling by the system on Android 8.0+
     *
     * @param pendingIntent The same [android.app.PendingIntent] that was passed to
     *                      [startBackgroundScan]. This identifies which scan session to stop.
     *
     * @see startBackgroundScan
     * @see BluetoothLeScanner.stopScan
     */
    @SuppressLint("MissingPermission")
    fun stopBackgroundScan(pendingIntent: android.app.PendingIntent) {
        bluetoothLeScanner?.stopScan(pendingIntent)
    }
}