package io.github.romantsisyk.blex.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Utility object for checking Bluetooth Low Energy (BLE) related permissions.
 *
 * This helper simplifies the complexity of BLE permission handling across different
 * Android versions. Starting from Android 12 (API 31, codename "S"), new granular
 * Bluetooth permissions were introduced:
 * - [Manifest.permission.BLUETOOTH_SCAN] - Required for scanning BLE devices
 * - [Manifest.permission.BLUETOOTH_CONNECT] - Required for connecting to BLE devices
 * - [Manifest.permission.BLUETOOTH_ADVERTISE] - Required for advertising as a BLE peripheral
 *
 * On older Android versions (below API 31), [Manifest.permission.ACCESS_FINE_LOCATION]
 * is typically required for BLE scanning operations.
 *
 * ## Usage Example
 * ```kotlin
 * if (PermissionsHelper.hasScanPermissions(context)) {
 *     // Safe to start BLE scanning
 *     bleScanner.startScan()
 * } else {
 *     // Request BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission
 * }
 * ```
 *
 * @see <a href="https://developer.android.com/develop/connectivity/bluetooth/bt-permissions">Android Bluetooth Permissions</a>
 */
object PermissionsHelper {

    /**
     * Checks if the required permission is granted based on the SDK version.
     *
     * @param context The application context.
     * @param permissionForS The permission required for Android S (API 31) and above.
     * @param permissionBelowS The permission required for Android versions below S.
     * @return True if the required permission is granted, false otherwise.
     */
    private fun hasPermission(
        context: Context,
        permissionForS: String,
        permissionBelowS: String? = null
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, permissionForS) == PackageManager.PERMISSION_GRANTED
        } else {
            permissionBelowS?.let {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            } ?: true
        }
    }

    /**
     * Checks if the app has the necessary permissions to perform BLE scanning.
     *
     * @param context The application context.
     * @return True if the scan permission is granted, false otherwise.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasScanPermissions(context: Context): Boolean {
        return hasPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    /**
     * Checks if the app has the necessary permissions to perform BLE connections.
     *
     * @param context The application context.
     * @return True if the connect permission is granted, or if running on older Android versions.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasConnectPermissions(context: Context): Boolean {
        return hasPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    /**
     * Checks if the app has the necessary permissions to perform BLE advertising.
     *
     * @param context The application context.
     * @return True if the advertise permission is granted, or if running on older Android versions.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasAdvertisePermissions(context: Context): Boolean {
        return hasPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    }
}