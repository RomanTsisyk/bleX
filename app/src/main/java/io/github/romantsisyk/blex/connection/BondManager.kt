package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.github.romantsisyk.blex.models.BondState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages Bluetooth device bonding (pairing) operations.
 *
 * Bonding is the process of establishing a trusted relationship between two Bluetooth
 * devices. Once bonded, devices can automatically reconnect and communicate securely
 * without requiring user interaction for each connection.
 *
 * This class provides a reactive approach to bonding using Kotlin coroutines [Flow],
 * allowing callers to observe bond state changes in real-time.
 *
 * ## Bond States
 * - [BondState.BOND_NONE] - No bond exists; devices are not paired
 * - [BondState.BOND_BONDING] - Bonding is in progress; user may need to confirm pairing
 * - [BondState.BOND_BONDED] - Devices are successfully bonded/paired
 *
 * ## Usage Example
 * ```kotlin
 * val bondManager = BondManager(context)
 *
 * bondManager.bondDevice(bluetoothDevice)
 *     .collect { bondState ->
 *         when (bondState) {
 *             BondState.BOND_BONDING -> showPairingDialog()
 *             BondState.BOND_BONDED -> onPairingSuccessful()
 *             BondState.BOND_NONE -> onPairingFailed()
 *         }
 *     }
 * ```
 *
 * ## Permissions
 * Requires [android.Manifest.permission.BLUETOOTH_CONNECT] on Android 12 (API 31) and above.
 *
 * @param context The Android [Context] used to register broadcast receivers for bond state changes.
 *
 * @see BondState
 * @see BluetoothDevice.createBond
 */
class BondManager(private val context: Context) {

    /**
     * Initiates bonding with the specified Bluetooth device and returns a [Flow] of bond state updates.
     *
     * If the device is not already bonded ([BluetoothDevice.BOND_NONE]), this method will
     * initiate the bonding process by calling [BluetoothDevice.createBond]. The returned
     * [Flow] will emit [BondState] updates as the bonding progresses.
     *
     * The [Flow] will automatically complete (close) when bonding reaches a terminal state:
     * - [BondState.BOND_BONDED] - Bonding succeeded
     * - [BondState.BOND_NONE] - Bonding failed or was cancelled
     *
     * ## Important Notes
     * - The bonding process may trigger a system pairing dialog for user confirmation
     * - The [Flow] uses a [BroadcastReceiver] internally, which is automatically
     *   unregistered when the [Flow] collection is cancelled or completes
     * - Only one bonding operation should be active per device at a time
     *
     * @param device The [BluetoothDevice] to bond with.
     * @return A [Flow] emitting [BondState] updates. The flow completes when bonding
     *         reaches a terminal state (bonded or failed).
     *
     * @throws SecurityException if [android.Manifest.permission.BLUETOOTH_CONNECT]
     *         is not granted on Android 12+.
     *
     * @see BondState
     * @see BluetoothDevice.getBondState
     */
    @SuppressLint("MissingPermission")
    fun bondDevice(device: BluetoothDevice): Flow<BondState> = callbackFlow {
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            device.createBond()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val receivedDevice: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                receivedDevice?.takeIf { it.address == device.address }?.let {
                    val bondState = BondState.fromInt(
                        intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    )
                    trySend(bondState).isSuccess

                    if (bondState == BondState.BOND_BONDED || bondState == BondState.BOND_NONE) {
                        close()
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}