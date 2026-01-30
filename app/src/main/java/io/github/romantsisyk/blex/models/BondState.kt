package io.github.romantsisyk.blex.models

import android.bluetooth.BluetoothDevice

/**
 * Represents the bonding (pairing) state of a Bluetooth device.
 *
 * Bonding is the process of creating a trusted relationship between two Bluetooth
 * devices by exchanging and storing security keys. This enum mirrors the bonding
 * states defined in [BluetoothDevice] but provides a more Kotlin-idiomatic API.
 *
 * ## Bonding vs Pairing
 * While often used interchangeably, bonding specifically refers to the storage of
 * security keys for future connections, while pairing is the initial key exchange.
 * A bonded device can reconnect securely without requiring user interaction.
 *
 * ## Usage Example
 * ```kotlin
 * val bondState = BondState.fromInt(device.bondState)
 * when (bondState) {
 *     BondState.BOND_NONE -> initiateBonding()
 *     BondState.BOND_BONDING -> showBondingProgress()
 *     BondState.BOND_BONDED -> proceedWithConnection()
 * }
 * ```
 *
 * @see BluetoothDevice.BOND_NONE
 * @see BluetoothDevice.BOND_BONDING
 * @see BluetoothDevice.BOND_BONDED
 */
enum class BondState {

    /**
     * The device is not bonded (paired) with the local adapter.
     *
     * No security keys are stored for this device. If the device requires
     * encryption or authentication, a bonding process must be initiated
     * before secure communication can occur.
     *
     * This state corresponds to [BluetoothDevice.BOND_NONE].
     */
    BOND_NONE,

    /**
     * Bonding is currently in progress with the remote device.
     *
     * The devices are actively exchanging security keys. This process may
     * involve user interaction (such as PIN entry or passkey confirmation)
     * depending on the security requirements of both devices.
     *
     * This is a transient state that will resolve to either [BOND_BONDED]
     * (on success) or [BOND_NONE] (on failure or cancellation).
     *
     * This state corresponds to [BluetoothDevice.BOND_BONDING].
     */
    BOND_BONDING,

    /**
     * The device is bonded (paired) with the local adapter.
     *
     * Security keys have been exchanged and stored. The device can now
     * establish encrypted connections without requiring user interaction
     * for authentication (unless the bond is removed).
     *
     * This state corresponds to [BluetoothDevice.BOND_BONDED].
     */
    BOND_BONDED;

    companion object {
        /**
         * Converts an Android Bluetooth bond state integer to a [BondState] enum value.
         *
         * This method provides a safe conversion from the integer constants defined
         * in [BluetoothDevice] to the type-safe [BondState] enum.
         *
         * @param state The integer bond state from [BluetoothDevice.getBondState].
         *              Expected values are:
         *              - [BluetoothDevice.BOND_NONE] (10)
         *              - [BluetoothDevice.BOND_BONDING] (11)
         *              - [BluetoothDevice.BOND_BONDED] (12)
         *
         * @return The corresponding [BondState] enum value. Returns [BOND_NONE]
         *         for any unrecognized state values as a safe default.
         *
         * @sample
         * ```kotlin
         * val device: BluetoothDevice = ...
         * val bondState = BondState.fromInt(device.bondState)
         * ```
         */
        fun fromInt(state: Int): BondState {
            return when (state) {
                BluetoothDevice.BOND_NONE -> BOND_NONE
                BluetoothDevice.BOND_BONDING -> BOND_BONDING
                BluetoothDevice.BOND_BONDED -> BOND_BONDED
                else -> BOND_NONE
            }
        }
    }
}
