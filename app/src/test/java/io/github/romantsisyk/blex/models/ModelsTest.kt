package io.github.romantsisyk.blex.models

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for model classes.
 *
 * These tests verify the behavior of data classes and sealed classes
 * in the models package.
 */
class ModelsTest {

    // ============ ConnectionState Tests ============

    @Test
    fun `ConnectionState Disconnected is correct type`() {
        val state: ConnectionState = ConnectionState.Disconnected
        assertTrue(state is ConnectionState.Disconnected)
    }

    @Test
    fun `ConnectionState Connecting is correct type`() {
        val state: ConnectionState = ConnectionState.Connecting
        assertTrue(state is ConnectionState.Connecting)
    }

    @Test
    fun `ConnectionState Connected is correct type`() {
        val state: ConnectionState = ConnectionState.Connected
        assertTrue(state is ConnectionState.Connected)
    }

    @Test
    fun `ConnectionState DiscoveringServices is correct type`() {
        val state: ConnectionState = ConnectionState.DiscoveringServices
        assertTrue(state is ConnectionState.DiscoveringServices)
    }

    @Test
    fun `ConnectionState Ready is correct type`() {
        val state: ConnectionState = ConnectionState.Ready
        assertTrue(state is ConnectionState.Ready)
    }

    @Test
    fun `ConnectionState Error contains message`() {
        val errorMessage = "Connection failed"
        val state = ConnectionState.Error(errorMessage)

        assertTrue(state is ConnectionState.Error)
        assertEquals(errorMessage, state.message)
    }

    // ============ BondState Tests ============

    // BluetoothDevice.BOND_NONE = 10
    // BluetoothDevice.BOND_BONDING = 11
    // BluetoothDevice.BOND_BONDED = 12

    @Test
    fun `fromInt returns BOND_NONE for 10`() {
        val result = BondState.fromInt(10)
        assertEquals(BondState.BOND_NONE, result)
    }

    @Test
    fun `fromInt returns BOND_BONDING for 11`() {
        val result = BondState.fromInt(11)
        assertEquals(BondState.BOND_BONDING, result)
    }

    @Test
    fun `fromInt returns BOND_BONDED for 12`() {
        val result = BondState.fromInt(12)
        assertEquals(BondState.BOND_BONDED, result)
    }

    @Test
    fun `fromInt returns BOND_NONE for unknown values`() {
        val result = BondState.fromInt(999)
        assertEquals(BondState.BOND_NONE, result)
    }

    // ============ BleService Tests ============

    @Test
    fun `BleService holds uuid and characteristics`() {
        val serviceUuid = UUID.randomUUID().toString()
        val characteristic1 = BleCharacteristic(
            uuid = UUID.randomUUID().toString(),
            properties = 1,
            permissions = 2
        )
        val characteristic2 = BleCharacteristic(
            uuid = UUID.randomUUID().toString(),
            properties = 3,
            permissions = 4
        )
        val characteristics = listOf(characteristic1, characteristic2)

        val bleService = BleService(
            uuid = serviceUuid,
            characteristics = characteristics
        )

        assertEquals(serviceUuid, bleService.uuid)
        assertEquals(2, bleService.characteristics.size)
        assertEquals(characteristic1, bleService.characteristics[0])
        assertEquals(characteristic2, bleService.characteristics[1])
    }

    // ============ BleCharacteristic Tests ============

    @Test
    fun `BleCharacteristic holds uuid, properties, permissions`() {
        val charUuid = UUID.randomUUID().toString()
        val properties = 5
        val permissions = 10

        val bleCharacteristic = BleCharacteristic(
            uuid = charUuid,
            properties = properties,
            permissions = permissions
        )

        assertEquals(charUuid, bleCharacteristic.uuid)
        assertEquals(properties, bleCharacteristic.properties)
        assertEquals(permissions, bleCharacteristic.permissions)
    }
}
