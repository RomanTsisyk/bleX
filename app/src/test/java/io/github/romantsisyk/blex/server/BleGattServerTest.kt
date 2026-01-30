package io.github.romantsisyk.blex.server

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for BleGattServer.
 *
 * Note: Tests that require Android runtime (BluetoothGattService constructor, etc.) have been
 * removed as they require Robolectric or instrumentation tests.
 * This test file contains only tests that can run with pure mocking.
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class BleGattServerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothManager: BluetoothManager

    @Mock
    private lateinit var mockBluetoothLeAdvertiser: BluetoothLeAdvertiser

    private lateinit var bleGattServer: BleGattServer

    @Before
    fun setUp() {
        // Setup BluetoothManager mock
        whenever(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager)

        // Setup BluetoothLeAdvertiser mock
        whenever(mockBluetoothAdapter.bluetoothLeAdvertiser).thenReturn(mockBluetoothLeAdvertiser)

        // Create the BleGattServer instance
        bleGattServer = BleGattServer(mockContext, mockBluetoothAdapter)
    }

    // ==================== Characteristic Value Tests ====================

    @Test
    fun `setCharacteristicValue stores value for characteristic`() {
        // Setup
        val testUUID = UUID.randomUUID()
        val testValue = byteArrayOf(0x01, 0x02, 0x03)

        // Execute
        bleGattServer.setCharacteristicValue(testUUID, testValue)

        // Verify
        assertArrayEquals(testValue, bleGattServer.getCharacteristicValue(testUUID))
    }

    @Test
    fun `setCharacteristicValue overwrites existing value`() {
        // Setup
        val testUUID = UUID.randomUUID()
        val firstValue = byteArrayOf(0x01, 0x02)
        val secondValue = byteArrayOf(0x03, 0x04, 0x05)

        // Execute
        bleGattServer.setCharacteristicValue(testUUID, firstValue)
        bleGattServer.setCharacteristicValue(testUUID, secondValue)

        // Verify
        assertArrayEquals(secondValue, bleGattServer.getCharacteristicValue(testUUID))
    }

    @Test
    fun `setCharacteristicValue stores empty array`() {
        // Setup
        val testUUID = UUID.randomUUID()
        val emptyValue = byteArrayOf()

        // Execute
        bleGattServer.setCharacteristicValue(testUUID, emptyValue)

        // Verify
        assertArrayEquals(emptyValue, bleGattServer.getCharacteristicValue(testUUID))
    }

    // ==================== getCharacteristicValue Tests ====================

    @Test
    fun `getCharacteristicValue retrieves stored value`() {
        // Setup
        val testUUID = UUID.randomUUID()
        val testValue = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        bleGattServer.setCharacteristicValue(testUUID, testValue)

        // Execute
        val retrievedValue = bleGattServer.getCharacteristicValue(testUUID)

        // Verify
        assertArrayEquals(testValue, retrievedValue)
    }

    @Test
    fun `getCharacteristicValue returns null for non-existent UUID`() {
        // Setup
        val testUUID = UUID.randomUUID()

        // Execute
        val retrievedValue = bleGattServer.getCharacteristicValue(testUUID)

        // Verify
        assertNull(retrievedValue)
    }

    @Test
    fun `getCharacteristicValue returns different values for different UUIDs`() {
        // Setup
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val value1 = byteArrayOf(0x01)
        val value2 = byteArrayOf(0x02)

        bleGattServer.setCharacteristicValue(uuid1, value1)
        bleGattServer.setCharacteristicValue(uuid2, value2)

        // Execute & Verify
        assertArrayEquals(value1, bleGattServer.getCharacteristicValue(uuid1))
        assertArrayEquals(value2, bleGattServer.getCharacteristicValue(uuid2))
    }

    // ==================== sendNotification Tests ====================

    @Test
    fun `sendNotification returns false when server not started`() {
        // Setup - server not started
        val testUUID = UUID.randomUUID()
        val testValue = byteArrayOf(0x01, 0x02)

        // Execute
        val result = bleGattServer.sendNotification(testUUID, testValue)

        // Verify
        assertFalse(result)
    }

    // ==================== stopServer Tests ====================

    @Test
    fun `stopServer does not crash when server was never started`() {
        // Execute - should not throw
        bleGattServer.stopServer()

        // No exception means test passed
        assertTrue(true)
    }
}
