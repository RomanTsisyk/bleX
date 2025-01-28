package io.github.romantsisyk.blex.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.romantsisyk.blex.models.BondState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BondManager(private val context: Context) {

    /**
     * Initiates bonding with the specified device.
     *
     * @param device BluetoothDevice to bond with.
     * @return Flow emitting BondState changes.
     */
    @SuppressLint("MissingPermission")
    fun bondDevice(device: BluetoothDevice): Flow<BondState> = callbackFlow {
        if (device.bondState == BluetoothDevice.BOND_NONE) {
            device.createBond()
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val receivedDevice: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (receivedDevice == null || receivedDevice.address != device.address) return

                val bondStateInt = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                )

                val bondState = BondState.fromInt(bondStateInt)
                trySend(bondState).isSuccess

                if (bondState == BondState.BOND_BONDED || bondState == BondState.BOND_NONE) {
                    // Close the flow if bonding is completed or failed
                    close()
                }
            }
        }

        context.registerReceiver(receiver, filter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}