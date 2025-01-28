package io.github.romantsisyk.blex.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BleScanner(private val context: Context, private val bluetoothAdapter: BluetoothAdapter) {

    private val bluetoothLeScanner: BluetoothLeScanner?
        get() = bluetoothAdapter.bluetoothLeScanner

    /**
     * Scans for BLE devices and emits [ScanResult] through a Flow.
     *
     * @param scanSettings Configuration for the scan.
     * @param scanFilters Filters to apply during scanning.
     * @return Flow emitting [ScanResult].
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
     * Starts background scanning using PendingIntent.
     * Requires additional setup, such as a BroadcastReceiver to handle scan results.
     *
     * @param scanSettings Configuration for the scan.
     * @param scanFilters Filters to apply during scanning.
     * @param pendingIntent PendingIntent to receive scan results.
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
     * Stops background scanning.
     *
     * @param pendingIntent The same PendingIntent used to start the scan.
     */
    @SuppressLint("MissingPermission")
    fun stopBackgroundScan(pendingIntent: android.app.PendingIntent) {
        bluetoothLeScanner?.stopScan(pendingIntent)
    }
}