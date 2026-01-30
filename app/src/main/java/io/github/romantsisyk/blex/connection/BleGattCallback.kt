package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import io.github.romantsisyk.blex.logging.BleLog
import io.github.romantsisyk.blex.util.Constants

/**
 * Custom implementation of [BluetoothGattCallback] that handles all GATT client events.
 *
 * This callback acts as a bridge between the Android Bluetooth stack and the
 * [ConnectionStateMachine], translating low-level GATT events into state machine
 * transitions and operations.
 *
 * ## Handled GATT Events
 * - **Connection State Changes**: Connect/disconnect events
 * - **Service Discovery**: When remote services are discovered
 * - **MTU Changes**: When MTU negotiation completes
 * - **PHY Updates**: When physical layer parameters change
 * - **Characteristic Operations**: Read, write, and notification events
 * - **Descriptor Operations**: Descriptor write completions
 *
 * ## Logging
 * All callbacks are logged using [Constants.TAG] for debugging purposes.
 * Log messages include the event type, relevant UUIDs, and status codes.
 *
 * ## API Compatibility
 * This class handles both legacy (pre-API 33) and modern (API 33+) callback methods
 * for characteristic change notifications. The appropriate method is called by the
 * system based on the Android version.
 *
 * ## Usage Example
 * ```kotlin
 * val stateMachine = ConnectionStateMachine(scope, ...)
 * val callback = BleGattCallback(stateMachine, bondManager)
 *
 * // Use the callback when connecting
 * device.connectGatt(context, false, callback)
 * ```
 *
 * @param connectionStateMachine The [ConnectionStateMachine] that processes GATT events
 *        and manages the connection lifecycle.
 * @param bondManager Optional [BondManager] for handling device bonding. Currently reserved
 *        for future use in automatic bonding scenarios.
 *
 * @see BluetoothGattCallback
 * @see ConnectionStateMachine
 */
class BleGattCallback(
    private val connectionStateMachine: ConnectionStateMachine,
    private val bondManager: BondManager? = null
) : BluetoothGattCallback() {

    /**
     * Called when the connection state changes (connected or disconnected).
     *
     * This callback is invoked when a connection attempt completes (either successfully
     * or with an error) or when an existing connection is terminated.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param status The status of the operation. [BluetoothGatt.GATT_SUCCESS] indicates
     *        the operation completed successfully. Other values indicate errors.
     * @param newState The new connection state:
     *        - [android.bluetooth.BluetoothProfile.STATE_CONNECTED]
     *        - [android.bluetooth.BluetoothProfile.STATE_DISCONNECTED]
     *
     * @see ConnectionStateMachine.handleConnectionStateChange
     */
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        BleLog.d(Constants.TAG, "onConnectionStateChange: status=$status, newState=$newState")
        connectionStateMachine.handleConnectionStateChange(gatt, status, newState)
    }

    /**
     * Called when remote device services have been discovered.
     *
     * This callback is invoked after [BluetoothGatt.discoverServices] completes.
     * On success, the discovered services and their characteristics are available
     * via [BluetoothGatt.getServices].
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param status [BluetoothGatt.GATT_SUCCESS] if services were discovered successfully,
     *        or an error code if the discovery failed.
     *
     * @see ConnectionStateMachine.handleServicesDiscovered
     * @see BluetoothGatt.discoverServices
     */
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        BleLog.d(Constants.TAG, "onServicesDiscovered: status=$status")
        connectionStateMachine.handleServicesDiscovered(gatt, status)
    }

    /**
     * Called when the MTU (Maximum Transmission Unit) for a connection has changed.
     *
     * This callback is invoked after [BluetoothGatt.requestMtu] completes. A larger MTU
     * allows for more data to be sent in a single BLE packet, improving throughput
     * for large data transfers.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param mtu The new MTU size in bytes. The effective payload size is MTU - 3 bytes
     *        due to ATT protocol overhead.
     * @param status [BluetoothGatt.GATT_SUCCESS] if the MTU was changed successfully,
     *        or an error code if the request failed.
     *
     * @see ConnectionStateMachine.handleMtuChanged
     * @see Constants.MAX_MTU
     * @see Constants.DEFAULT_MTU
     */
    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        BleLog.d(Constants.TAG, "onMtuChanged: mtu=$mtu, status=$status")
        connectionStateMachine.handleMtuChanged(gatt, mtu, status)
    }

    /**
     * Called when the PHY (Physical Layer) for a connection has been updated.
     *
     * PHY updates can change the data rate and range characteristics of the BLE connection.
     * This callback is invoked after [BluetoothGatt.setPreferredPhy] completes or when
     * the remote device initiates a PHY change.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param txPhy The transmitter PHY in use:
     *        - [android.bluetooth.BluetoothDevice.PHY_LE_1M] (1 Mbps)
     *        - [android.bluetooth.BluetoothDevice.PHY_LE_2M] (2 Mbps)
     *        - [android.bluetooth.BluetoothDevice.PHY_LE_CODED] (long range)
     * @param rxPhy The receiver PHY in use (same constants as txPhy).
     * @param status [BluetoothGatt.GATT_SUCCESS] if the PHY was updated successfully,
     *        or an error code if the update failed.
     *
     * @see ConnectionStateMachine.handlePhyUpdate
     */
    override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
        BleLog.d(Constants.TAG, "onPhyUpdate: txPhy=$txPhy, rxPhy=$rxPhy, status=$status")
        connectionStateMachine.handlePhyUpdate(gatt, txPhy, rxPhy, status)
    }

    /**
     * Called when a characteristic read operation completes.
     *
     * This callback is invoked after [BluetoothGatt.readCharacteristic] completes.
     * On success, the characteristic's value can be retrieved from the characteristic object.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param characteristic The [BluetoothGattCharacteristic] that was read. Contains
     *        the value if the read was successful.
     * @param status [BluetoothGatt.GATT_SUCCESS] if the read completed successfully,
     *        or an error code if the read failed.
     *
     * @see ConnectionStateMachine.handleCharacteristicRead
     */
    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        BleLog.d(Constants.TAG, "onCharacteristicRead: ${characteristic.uuid}, status=$status")
        connectionStateMachine.handleCharacteristicRead(gatt, characteristic, status)
    }

    /**
     * Called when a characteristic write operation completes.
     *
     * This callback is invoked after [BluetoothGatt.writeCharacteristic] completes.
     * Note: For write types [BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE],
     * this callback may still be invoked but with less reliability.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param characteristic The [BluetoothGattCharacteristic] that was written to.
     * @param status [BluetoothGatt.GATT_SUCCESS] if the write completed successfully,
     *        or an error code if the write failed.
     *
     * @see ConnectionStateMachine.handleCharacteristicWrite
     */
    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        BleLog.d(Constants.TAG, "onCharacteristicWrite: ${characteristic.uuid}, status=$status")
        connectionStateMachine.handleCharacteristicWrite(gatt, characteristic, status)
    }

    /**
     * Called when a characteristic value has changed due to a notification or indication (API < 33).
     *
     * This legacy callback is invoked on Android versions prior to API 33 (Android 13).
     * The characteristic value must be retrieved from the characteristic object itself.
     *
     * Note: This method is deprecated in favor of the API 33+ variant that passes the
     * value directly as a parameter, avoiding potential race conditions.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param characteristic The [BluetoothGattCharacteristic] whose value has changed.
     *        Call [BluetoothGattCharacteristic.getValue] to retrieve the new value.
     *
     * @see ConnectionStateMachine.handleCharacteristicChanged
     */
    @Deprecated(
        message = "Deprecated in API 33. Use onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, ByteArray) instead.",
        replaceWith = ReplaceWith("onCharacteristicChanged(gatt, characteristic, value)")
    )
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        BleLog.d(Constants.TAG, "onCharacteristicChanged (legacy): ${characteristic.uuid}")
        connectionStateMachine.handleCharacteristicChanged(gatt, characteristic)
    }

    /**
     * Called when a characteristic value has changed due to a notification or indication (API 33+).
     *
     * This modern callback is invoked on Android 13 (API 33) and later. The characteristic
     * value is passed directly as a parameter, which is safer than reading from the
     * characteristic object and avoids potential race conditions.
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param characteristic The [BluetoothGattCharacteristic] whose value has changed.
     * @param value The new value of the characteristic as a [ByteArray].
     *
     * @see ConnectionStateMachine.handleCharacteristicChanged
     */
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        BleLog.d(Constants.TAG, "onCharacteristicChanged (API 33+): ${characteristic.uuid}")
        connectionStateMachine.handleCharacteristicChanged(gatt, characteristic, value)
    }

    /**
     * Called when a descriptor write operation completes.
     *
     * This callback is commonly used to confirm that notifications or indications
     * have been enabled/disabled for a characteristic by writing to its
     * Client Characteristic Configuration Descriptor (CCCD).
     *
     * @param gatt The [BluetoothGatt] instance for the connection.
     * @param descriptor The [BluetoothGattDescriptor] that was written to.
     * @param status [BluetoothGatt.GATT_SUCCESS] if the write completed successfully,
     *        or an error code if the write failed.
     *
     * @see ConnectionStateMachine.handleDescriptorWrite
     */
    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        BleLog.d(Constants.TAG, "onDescriptorWrite: ${descriptor.uuid}, status=$status")
        connectionStateMachine.handleDescriptorWrite(gatt, descriptor, status)
    }
}
