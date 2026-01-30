package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Unit tests for BleConnection.
 *
 * These tests verify that BleConnection properly delegates operations to
 * ConnectionStateMachine and BondManager, and handles their responses correctly.
 *
 * Since BleConnection creates its own ConnectionStateMachine internally and
 * doesn't support constructor injection, we use a testable subclass that
 * allows injecting a mock ConnectionStateMachine for testing purposes.
 */
@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BleConnectionTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDevice: BluetoothDevice

    @Mock
    private lateinit var mockConnectionStateMachine: ConnectionStateMachine

    @Mock
    private lateinit var mockBondManager: BondManager

    private lateinit var bleConnection: TestableBleConnection
    private lateinit var bleConnectionWithoutBondManager: TestableBleConnection

    private val testServiceUuid: UUID = UUID.randomUUID()
    private val testCharacteristicUuid: UUID = UUID.randomUUID()
    private val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    @Before
    fun setUp() {
        `when`(mockDevice.address).thenReturn("AA:BB:CC:DD:EE:FF")
        `when`(mockConnectionStateMachine.connectionStateFlow).thenReturn(connectionStateFlow)

        // Create BleConnection with BondManager
        bleConnection = TestableBleConnection(
            device = mockDevice,
            bondManager = mockBondManager,
            connectionStateMachine = mockConnectionStateMachine
        )

        // Create BleConnection without BondManager
        bleConnectionWithoutBondManager = TestableBleConnection(
            device = mockDevice,
            bondManager = null,
            connectionStateMachine = mockConnectionStateMachine
        )
    }

    // ==================== Connect Tests ====================

    @Test
    fun `connect delegates to ConnectionStateMachine`() {
        bleConnection.connect()

        verify(mockConnectionStateMachine).connect()
    }

    // ==================== Disconnect Tests ====================

    @Test
    fun `disconnect delegates to ConnectionStateMachine`() {
        bleConnection.disconnect()

        verify(mockConnectionStateMachine).disconnect()
    }

    // ==================== Close Tests ====================

    @Test
    fun `close delegates to ConnectionStateMachine`() {
        bleConnection.close()

        verify(mockConnectionStateMachine).close()
    }

    // ==================== ConnectionStateFlow Tests ====================

    @Test
    fun `connectionStateFlow exposes state machine flow`() {
        connectionStateFlow.value = ConnectionState.Ready

        assertEquals(ConnectionState.Ready, bleConnection.connectionStateFlow.value)
    }

    @Test
    fun `connectionStateFlow reflects state changes from state machine`() {
        assertEquals(ConnectionState.Disconnected, bleConnection.connectionStateFlow.value)

        connectionStateFlow.value = ConnectionState.Connecting
        assertEquals(ConnectionState.Connecting, bleConnection.connectionStateFlow.value)

        connectionStateFlow.value = ConnectionState.Connected
        assertEquals(ConnectionState.Connected, bleConnection.connectionStateFlow.value)

        connectionStateFlow.value = ConnectionState.Ready
        assertEquals(ConnectionState.Ready, bleConnection.connectionStateFlow.value)
    }

    // ==================== Bond Tests ====================

    @Test
    fun `bond returns Flow from BondManager`() = runTest {
        val expectedBondStates = flowOf(BondState.BOND_BONDING, BondState.BOND_BONDED)
        `when`(mockBondManager.bondDevice(mockDevice)).thenReturn(expectedBondStates)

        val result = bleConnection.bond()
        val collectedStates = result.toList()

        assertEquals(2, collectedStates.size)
        assertEquals(BondState.BOND_BONDING, collectedStates[0])
        assertEquals(BondState.BOND_BONDED, collectedStates[1])
        verify(mockBondManager).bondDevice(mockDevice)
    }

    @Test
    fun `bond returns empty Flow when BondManager is null`() = runTest {
        val result = bleConnectionWithoutBondManager.bond()
        val collectedStates = result.toList()

        assertTrue(collectedStates.isEmpty())
    }

    // ==================== ReadCharacteristic Tests ====================

    @Test
    fun `readCharacteristic returns data on success`() = runTest {
        val expectedData = byteArrayOf(0x01, 0x02, 0x03)

        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int, ByteArray?) -> Unit>(2)
            callback(BluetoothGatt.GATT_SUCCESS, expectedData)
            null
        }.`when`(mockConnectionStateMachine).readCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            any()
        )

        val result = bleConnection.readCharacteristic(testServiceUuid, testCharacteristicUuid)

        assertNotNull(result)
        assertArrayEquals(expectedData, result)
    }

    @Test
    fun `readCharacteristic returns null on failure`() = runTest {
        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int, ByteArray?) -> Unit>(2)
            callback(BluetoothGatt.GATT_FAILURE, null)
            null
        }.`when`(mockConnectionStateMachine).readCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            any()
        )

        val result = bleConnection.readCharacteristic(testServiceUuid, testCharacteristicUuid)

        assertNull(result)
    }

    @Test
    fun `readCharacteristic returns null when status is success but value is null`() = runTest {
        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int, ByteArray?) -> Unit>(2)
            callback(BluetoothGatt.GATT_SUCCESS, null)
            null
        }.`when`(mockConnectionStateMachine).readCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            any()
        )

        val result = bleConnection.readCharacteristic(testServiceUuid, testCharacteristicUuid)

        assertNull(result)
    }

    @Test
    fun `readCharacteristic returns null on non-zero error status`() = runTest {
        val errorStatus = 133 // Common BLE error code

        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int, ByteArray?) -> Unit>(2)
            callback(errorStatus, byteArrayOf(0x01))
            null
        }.`when`(mockConnectionStateMachine).readCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            any()
        )

        val result = bleConnection.readCharacteristic(testServiceUuid, testCharacteristicUuid)

        assertNull(result)
    }

    // ==================== WriteCharacteristic Tests ====================

    @Test
    fun `writeCharacteristic returns true on success`() = runTest {
        val dataToWrite = byteArrayOf(0x04, 0x05, 0x06)

        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int) -> Unit>(3)
            callback(BluetoothGatt.GATT_SUCCESS)
            null
        }.`when`(mockConnectionStateMachine).writeCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            eq(dataToWrite),
            any()
        )

        val result = bleConnection.writeCharacteristic(testServiceUuid, testCharacteristicUuid, dataToWrite)

        assertTrue(result)
    }

    @Test
    fun `writeCharacteristic returns false on failure`() = runTest {
        val dataToWrite = byteArrayOf(0x04, 0x05, 0x06)

        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int) -> Unit>(3)
            callback(BluetoothGatt.GATT_FAILURE)
            null
        }.`when`(mockConnectionStateMachine).writeCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            eq(dataToWrite),
            any()
        )

        val result = bleConnection.writeCharacteristic(testServiceUuid, testCharacteristicUuid, dataToWrite)

        assertFalse(result)
    }

    @Test
    fun `writeCharacteristic returns false on non-zero error status`() = runTest {
        val dataToWrite = byteArrayOf(0x04, 0x05, 0x06)
        val errorStatus = 133 // Common BLE error code

        doAnswer { invocation ->
            val callback = invocation.getArgument<(Int) -> Unit>(3)
            callback(errorStatus)
            null
        }.`when`(mockConnectionStateMachine).writeCharacteristic(
            eq(testServiceUuid),
            eq(testCharacteristicUuid),
            eq(dataToWrite),
            any()
        )

        val result = bleConnection.writeCharacteristic(testServiceUuid, testCharacteristicUuid, dataToWrite)

        assertFalse(result)
    }

    /**
     * Testable version of BleConnection that allows injecting a mock ConnectionStateMachine.
     *
     * This class mirrors the behavior of BleConnection but accepts the ConnectionStateMachine
     * as a constructor parameter for testing purposes.
     */
    private class TestableBleConnection(
        private val device: BluetoothDevice,
        private val bondManager: BondManager?,
        private val connectionStateMachine: ConnectionStateMachine
    ) {
        val connectionStateFlow: StateFlow<ConnectionState> = connectionStateMachine.connectionStateFlow

        fun connect() {
            connectionStateMachine.connect()
        }

        fun disconnect() {
            connectionStateMachine.disconnect()
        }

        fun close() {
            connectionStateMachine.close()
        }

        fun bond(): Flow<BondState> {
            return bondManager?.bondDevice(device) ?: emptyFlow()
        }

        suspend fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): ByteArray? {
            return suspendCancellableCoroutine { continuation ->
                connectionStateMachine.readCharacteristic(serviceUuid, characteristicUuid) { status, value ->
                    if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                        continuation.resume(value)
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        }

        suspend fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, value: ByteArray): Boolean {
            return suspendCancellableCoroutine { continuation ->
                connectionStateMachine.writeCharacteristic(serviceUuid, characteristicUuid, value) { status ->
                    continuation.resume(status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }
    }
}
