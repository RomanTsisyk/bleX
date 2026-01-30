package io.github.romantsisyk.blex.models

/**
 * Represents a Bluetooth Low Energy (BLE) GATT service.
 *
 * A GATT service is a collection of characteristics and relationships to other
 * services that encapsulate the behavior of a part of a device. Services are
 * identified by UUIDs, with standard services defined by the Bluetooth SIG
 * having 16-bit UUIDs, while custom services use full 128-bit UUIDs.
 *
 * ## Standard Services
 * Common standard services include:
 * - **Generic Access** (0x1800): Device name and appearance
 * - **Generic Attribute** (0x1801): Service change indications
 * - **Battery Service** (0x180F): Battery level information
 * - **Heart Rate** (0x180D): Heart rate measurements
 * - **Device Information** (0x180A): Manufacturer and device details
 *
 * ## Usage Example
 * ```kotlin
 * val heartRateService = BleService(
 *     uuid = "0000180d-0000-1000-8000-00805f9b34fb",
 *     characteristics = listOf(
 *         BleCharacteristic(
 *             uuid = "00002a37-0000-1000-8000-00805f9b34fb",
 *             properties = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
 *             permissions = 0
 *         )
 *     )
 * )
 * ```
 *
 * @property uuid The unique identifier for this service, represented as a string.
 *                Standard Bluetooth SIG services use the format
 *                "0000XXXX-0000-1000-8000-00805f9b34fb" where XXXX is the 16-bit UUID.
 *                Custom services use full 128-bit UUIDs.
 *
 * @property characteristics The list of [BleCharacteristic] objects that belong to
 *                           this service. Each characteristic represents a data point
 *                           or control point that can be read, written, or subscribed to.
 *
 * @see BleCharacteristic
 * @see ConnectionState.Ready
 */
data class BleService(
    val uuid: String,
    val characteristics: List<BleCharacteristic>
)
