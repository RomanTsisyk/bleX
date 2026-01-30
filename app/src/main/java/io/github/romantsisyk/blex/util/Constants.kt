package io.github.romantsisyk.blex.util

/**
 * Central repository for constant values used throughout the BleX library.
 *
 * This object contains configuration constants for BLE operations including
 * logging tags, MTU (Maximum Transmission Unit) values, and timing parameters.
 *
 * ## MTU (Maximum Transmission Unit)
 * The MTU defines the maximum size of a single BLE packet. The effective payload
 * size is typically MTU - 3 bytes (due to ATT protocol overhead).
 *
 * - Default MTU: 23 bytes (20 bytes effective payload)
 * - Maximum MTU: 517 bytes (514 bytes effective payload, as per Bluetooth 4.2+)
 *
 * ## Usage Example
 * ```kotlin
 * // Using the tag for logging
 * Log.d(Constants.TAG, "BLE operation completed")
 *
 * // Requesting maximum MTU for faster data transfer
 * gatt.requestMtu(Constants.MAX_MTU)
 * ```
 */
object Constants {

    /**
     * Logging tag used for all BleX library log messages.
     *
     * Use this tag when filtering logcat output to see only BleX-related logs:
     * ```
     * adb logcat -s bleX
     * ```
     */
    const val TAG = "bleX"

    /**
     * Maximum supported MTU (Maximum Transmission Unit) size in bytes.
     *
     * This value (517 bytes) is the maximum allowed by the Bluetooth specification
     * starting from Bluetooth 4.2. The effective payload size after ATT protocol
     * overhead is 514 bytes.
     *
     * Note: Not all devices support the maximum MTU. The actual negotiated MTU
     * depends on both the central and peripheral device capabilities.
     *
     * @see DEFAULT_MTU
     */
    const val MAX_MTU = 517

    /**
     * Default MTU (Maximum Transmission Unit) size in bytes.
     *
     * This value (23 bytes) is the default BLE MTU as defined in the Bluetooth
     * Low Energy specification. The effective payload size after ATT protocol
     * overhead is 20 bytes.
     *
     * This is the guaranteed minimum MTU supported by all BLE devices.
     *
     * @see MAX_MTU
     */
    const val DEFAULT_MTU = 23

    /**
     * Default delay in milliseconds before attempting to reconnect after a disconnection.
     *
     * This delay (2000ms / 2 seconds) helps prevent rapid reconnection attempts
     * that could overwhelm the Bluetooth stack or drain battery unnecessarily.
     * It also gives the remote device time to stabilize after a disconnection.
     */
    const val RECONNECT_DELAY_MS = 2000L
}