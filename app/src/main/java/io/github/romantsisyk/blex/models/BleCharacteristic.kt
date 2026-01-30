package io.github.romantsisyk.blex.models

/**
 * Represents a Bluetooth Low Energy (BLE) GATT characteristic.
 *
 * A GATT characteristic is a data value transferred between a client and server,
 * such as a sensor reading or a control point. Characteristics are contained within
 * services and define the actual data and operations available on a BLE device.
 *
 * ## Properties Bitmask
 * The [properties] field is a bitmask that indicates supported operations:
 * - **PROPERTY_BROADCAST** (0x01): Characteristic can be broadcast
 * - **PROPERTY_READ** (0x02): Characteristic can be read
 * - **PROPERTY_WRITE_NO_RESPONSE** (0x04): Write without response
 * - **PROPERTY_WRITE** (0x08): Characteristic can be written with response
 * - **PROPERTY_NOTIFY** (0x10): Characteristic supports notifications
 * - **PROPERTY_INDICATE** (0x20): Characteristic supports indications
 * - **PROPERTY_SIGNED_WRITE** (0x40): Signed write supported
 * - **PROPERTY_EXTENDED_PROPS** (0x80): Extended properties available
 *
 * ## Permissions Bitmask
 * The [permissions] field is a bitmask that defines access permissions:
 * - **PERMISSION_READ** (0x01): Read permission
 * - **PERMISSION_READ_ENCRYPTED** (0x02): Encrypted read
 * - **PERMISSION_READ_ENCRYPTED_MITM** (0x04): MITM-protected read
 * - **PERMISSION_WRITE** (0x10): Write permission
 * - **PERMISSION_WRITE_ENCRYPTED** (0x20): Encrypted write
 * - **PERMISSION_WRITE_ENCRYPTED_MITM** (0x40): MITM-protected write
 * - **PERMISSION_WRITE_SIGNED** (0x80): Signed write permission
 * - **PERMISSION_WRITE_SIGNED_MITM** (0x100): MITM-protected signed write
 *
 * ## Usage Example
 * ```kotlin
 * val heartRateMeasurement = BleCharacteristic(
 *     uuid = "00002a37-0000-1000-8000-00805f9b34fb",
 *     properties = BluetoothGattCharacteristic.PROPERTY_NOTIFY,
 *     permissions = BluetoothGattCharacteristic.PERMISSION_READ
 * )
 *
 * // Check if characteristic supports notifications
 * val supportsNotify = heartRateMeasurement.properties and
 *     BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
 * ```
 *
 * @property uuid The unique identifier for this characteristic, represented as a string.
 *                Standard Bluetooth SIG characteristics use the format
 *                "0000XXXX-0000-1000-8000-00805f9b34fb" where XXXX is the 16-bit UUID.
 *                Custom characteristics use full 128-bit UUIDs.
 *
 * @property properties A bitmask describing the supported operations for this
 *                      characteristic. Use bitwise AND operations to check for
 *                      specific property support. These values correspond to
 *                      `BluetoothGattCharacteristic.PROPERTY_*` constants.
 *
 * @property permissions A bitmask describing the access permissions required to
 *                       interact with this characteristic. Permissions determine
 *                       whether encryption or authentication is required for
 *                       read/write operations. These values correspond to
 *                       `BluetoothGattCharacteristic.PERMISSION_*` constants.
 *
 * @see BleService
 * @see android.bluetooth.BluetoothGattCharacteristic
 */
data class BleCharacteristic(
    val uuid: String,
    val properties: Int,
    val permissions: Int
)
