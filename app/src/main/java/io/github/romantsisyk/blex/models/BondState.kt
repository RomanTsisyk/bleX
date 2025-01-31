package io.github.romantsisyk.blex.models

import android.bluetooth.BluetoothDevice

enum class BondState {
    BOND_NONE,
    BOND_BONDING,
    BOND_BONDED;

    companion object {
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