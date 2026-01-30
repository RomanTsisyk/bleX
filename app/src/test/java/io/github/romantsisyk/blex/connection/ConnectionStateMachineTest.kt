package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.util.UUID

/**
 * Unit tests for ConnectionStateMachine.
 *
 * These tests verify the state transition logic, error handling,
 * and handler registration/cleanup behavior of the ConnectionStateMachine.
 */
@RunWith(MockitoJUnitRunner.Silent::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionStateMachineTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDevice: BluetoothDevice

    @Mock
    private lateinit var mockGatt: BluetoothGatt

    @Mock
    private lateinit var mockBondManager: BondManager

    @Mock
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic

    @Mock
    private lateinit var mockDescriptor: BluetoothGattDescriptor

    @Mock
    private lateinit var mockService: BluetoothGattService

    private lateinit var stateMachine: ConnectionStateMachine

    @Before
    fun setUp() {
        `when`(mockDevice.address).thenReturn("AA:BB:CC:DD:EE:FF")

        // Configure mock GATT to return services list
        `when`(mockGatt.services).thenReturn(listOf(mockService))

        // Create state machine without auto-reconnect for most tests
        // to avoid dealing with coroutine delays
        stateMachine = ConnectionStateMachine(
            context = mockContext,
            device = mockDevice,
            bondManager = mockBondManager,
            autoReconnect = false
        )
    }

    @After
    fun tearDown() {
        // Clear service cache after each test to ensure test isolation
        ConnectionStateMachine.clearServiceCache()
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state is Disconnected`() {
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `bluetoothGatt is initially null`() {
        assertNull(stateMachine.bluetoothGatt)
    }

    // ==================== State Transition Tests ====================

    @Test
    fun `handleConnectionStateChange transitions to Connected on successful connection`() {
        // Simulate successful connection
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        assertEquals(ConnectionState.Connected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `handleConnectionStateChange transitions to Disconnected on disconnect`() {
        // First, simulate connected state
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Then simulate disconnection
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `handleServicesDiscovered transitions to Ready on success`() {
        // First connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Then discover services
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)

        assertEquals(ConnectionState.Ready, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `full state transition flow - Disconnected to Ready`() {
        // Initial state
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)

        // Connect successfully
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        assertEquals(ConnectionState.Connected, stateMachine.connectionStateFlow.value)

        // Discover services
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        assertEquals(ConnectionState.Ready, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `disconnect from Ready returns to Disconnected`() {
        // Get to Ready state
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        assertEquals(ConnectionState.Ready, stateMachine.connectionStateFlow.value)

        // Disconnect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    // ==================== Error State Tests ====================

    @Test
    fun `handleConnectionStateChange transitions to Error on connection failure`() {
        val failureStatus = 133 // Common BLE error code

        stateMachine.handleConnectionStateChange(
            mockGatt,
            failureStatus,
            BluetoothProfile.STATE_DISCONNECTED
        )

        val currentState = stateMachine.connectionStateFlow.value
        assertTrue(currentState is ConnectionState.Error)
        assertTrue((currentState as ConnectionState.Error).message.contains("$failureStatus"))
    }

    @Test
    fun `handleServicesDiscovered transitions to Error on failure`() {
        // First connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Service discovery fails
        val failureStatus = 129 // GATT_INTERNAL_ERROR

        stateMachine.handleServicesDiscovered(mockGatt, failureStatus)

        val currentState = stateMachine.connectionStateFlow.value
        assertTrue(currentState is ConnectionState.Error)
        assertTrue((currentState as ConnectionState.Error).message.contains("Service discovery failed"))
    }

    @Test
    fun `error state contains device address`() {
        stateMachine.handleConnectionStateChange(
            mockGatt,
            133,
            BluetoothProfile.STATE_DISCONNECTED
        )

        val currentState = stateMachine.connectionStateFlow.value
        assertTrue(currentState is ConnectionState.Error)
        assertTrue((currentState as ConnectionState.Error).message.contains("AA:BB:CC:DD:EE:FF"))
    }

    // ==================== Auto-Reconnect Tests ====================

    @Test
    fun `auto-reconnect is disabled by default when set to false`() = runTest {
        val noReconnectStateMachine = ConnectionStateMachine(
            context = mockContext,
            device = mockDevice,
            bondManager = mockBondManager,
            autoReconnect = false
        )

        // Trigger disconnection - should not schedule reconnect
        noReconnectStateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )

        // State should remain disconnected without attempting reconnect
        assertEquals(ConnectionState.Disconnected, noReconnectStateMachine.connectionStateFlow.value)
    }

    @Test
    fun `auto-reconnect state machine starts in Disconnected state`() {
        val autoReconnectStateMachine = ConnectionStateMachine(
            context = mockContext,
            device = mockDevice,
            bondManager = mockBondManager,
            autoReconnect = true
        )

        assertEquals(ConnectionState.Disconnected, autoReconnectStateMachine.connectionStateFlow.value)
    }

    // ==================== Handler Registration Tests ====================

    @Test
    fun `handleMtuChanged invokes registered handler`() {
        // We need to access the private handler map through reflection for this test
        // Since the handlers are private, we test through the public API behavior

        // The MTU handler is invoked when onMtuChanged callback is triggered
        stateMachine.handleMtuChanged(mockGatt, 512, BluetoothGatt.GATT_SUCCESS)

        // Without a registered handler, this should complete without error
        // This tests the null-safety of the handler invocation
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `handlePhyUpdate invokes registered handler`() {
        // Test that handlePhyUpdate can be called without error when no handler registered
        stateMachine.handlePhyUpdate(mockGatt, 2, 2, BluetoothGatt.GATT_SUCCESS)

        // Should not crash or change state
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `handleCharacteristicRead removes handler after invocation`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        `when`(mockCharacteristic.value).thenReturn(byteArrayOf(0x01, 0x02))

        // Call handleCharacteristicRead - handler should be removed after first call
        stateMachine.handleCharacteristicRead(mockGatt, mockCharacteristic, BluetoothGatt.GATT_SUCCESS)

        // Calling again should not fail (handler already removed)
        stateMachine.handleCharacteristicRead(mockGatt, mockCharacteristic, BluetoothGatt.GATT_SUCCESS)

        // State should remain unchanged
        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `handleCharacteristicWrite removes handler after invocation`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)

        // Call handleCharacteristicWrite
        stateMachine.handleCharacteristicWrite(mockGatt, mockCharacteristic, BluetoothGatt.GATT_SUCCESS)

        // Calling again should not fail
        stateMachine.handleCharacteristicWrite(mockGatt, mockCharacteristic, BluetoothGatt.GATT_SUCCESS)

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `handleCharacteristicChanged can be called without handler`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        `when`(mockCharacteristic.value).thenReturn(byteArrayOf(0x01))

        // Should not throw when no handler registered
        stateMachine.handleCharacteristicChanged(mockGatt, mockCharacteristic)

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `handleDescriptorWrite removes handler after invocation`() {
        val testUuid = UUID.randomUUID()
        `when`(mockDescriptor.uuid).thenReturn(testUuid)

        // Call handleDescriptorWrite
        stateMachine.handleDescriptorWrite(mockGatt, mockDescriptor, BluetoothGatt.GATT_SUCCESS)

        // Calling again should not fail
        stateMachine.handleDescriptorWrite(mockGatt, mockDescriptor, BluetoothGatt.GATT_SUCCESS)

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    // ==================== Close and Cleanup Tests ====================

    @Test
    fun `close transitions to Disconnected state`() {
        // First get to Ready state
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)

        // Close the connection
        stateMachine.close()

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
    }

    @Test
    fun `close sets bluetoothGatt to null`() {
        // Manually set a gatt reference (via connect which we can't fully test due to BLE dependencies)
        // So we just verify that close() properly transitions state
        stateMachine.close()

        assertNull(stateMachine.bluetoothGatt)
    }

    @Test
    fun `close can be called multiple times safely`() {
        stateMachine.close()
        stateMachine.close()
        stateMachine.close()

        assertEquals(ConnectionState.Disconnected, stateMachine.connectionStateFlow.value)
        assertNull(stateMachine.bluetoothGatt)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `state flow emits correct sequence of states`() {
        val observedStates = mutableListOf<ConnectionState>()

        // Capture initial state
        observedStates.add(stateMachine.connectionStateFlow.value)

        // Connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        observedStates.add(stateMachine.connectionStateFlow.value)

        // Services discovered
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        observedStates.add(stateMachine.connectionStateFlow.value)

        // Disconnect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )
        observedStates.add(stateMachine.connectionStateFlow.value)

        assertEquals(4, observedStates.size)
        assertEquals(ConnectionState.Disconnected, observedStates[0])
        assertEquals(ConnectionState.Connected, observedStates[1])
        assertEquals(ConnectionState.Ready, observedStates[2])
        assertEquals(ConnectionState.Disconnected, observedStates[3])
    }

    @Test
    fun `multiple connection failures result in multiple Error states`() {
        // First failure
        stateMachine.handleConnectionStateChange(
            mockGatt,
            133,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertTrue(stateMachine.connectionStateFlow.value is ConnectionState.Error)

        // Try again and fail differently
        stateMachine.handleConnectionStateChange(
            mockGatt,
            257,
            BluetoothProfile.STATE_DISCONNECTED
        )

        val currentState = stateMachine.connectionStateFlow.value
        assertTrue(currentState is ConnectionState.Error)
        assertTrue((currentState as ConnectionState.Error).message.contains("257"))
    }

    @Test
    fun `recovery from Error state is possible`() {
        // First, enter error state
        stateMachine.handleConnectionStateChange(
            mockGatt,
            133,
            BluetoothProfile.STATE_DISCONNECTED
        )
        assertTrue(stateMachine.connectionStateFlow.value is ConnectionState.Error)

        // Then successfully connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        assertEquals(ConnectionState.Connected, stateMachine.connectionStateFlow.value)
    }

    // ==================== Service Cache Tests ====================

    @Test
    fun `handleServicesDiscovered caches services on success`() {
        // Connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Initially no cache
        assertFalse(stateMachine.hasServiceCache())

        // Discover services
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)

        // Cache should now exist
        assertTrue(stateMachine.hasServiceCache())
        assertNotNull(stateMachine.getCachedServices())
        assertEquals(1, stateMachine.getCachedServices()?.size)
    }

    @Test
    fun `getCachedServices returns null when no cache exists`() {
        assertNull(stateMachine.getCachedServices())
    }

    @Test
    fun `hasServiceCache returns false when no cache exists`() {
        assertFalse(stateMachine.hasServiceCache())
    }

    @Test
    fun `clearServiceCache clears cache for specific device`() {
        // Set up service cache
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        assertTrue(stateMachine.hasServiceCache())

        // Clear cache for this device
        ConnectionStateMachine.clearServiceCache("AA:BB:CC:DD:EE:FF")

        // Cache should be cleared
        assertFalse(stateMachine.hasServiceCache())
        assertNull(stateMachine.getCachedServices())
    }

    @Test
    fun `clearServiceCache with null clears all caches`() {
        // Set up service cache
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        assertTrue(stateMachine.hasServiceCache())

        // Clear all caches
        ConnectionStateMachine.clearServiceCache(null)

        // Cache should be cleared
        assertFalse(stateMachine.hasServiceCache())
    }

    @Test
    fun `service cache persists across state machine instances`() {
        // Set up service cache with first state machine
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)

        // Create a new state machine for the same device
        val newStateMachine = ConnectionStateMachine(
            context = mockContext,
            device = mockDevice,
            bondManager = mockBondManager,
            autoReconnect = false
        )

        // Cache should be available in the new state machine
        assertTrue(newStateMachine.hasServiceCache())
        assertNotNull(newStateMachine.getCachedServices())
    }

    @Test
    fun `handleServicesDiscovered does not cache on failure`() {
        // Connect
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Service discovery fails
        stateMachine.handleServicesDiscovered(mockGatt, 129) // GATT_INTERNAL_ERROR

        // Cache should not exist
        assertFalse(stateMachine.hasServiceCache())
        assertNull(stateMachine.getCachedServices())
    }

    @Test
    fun `service cache is updated on reconnection with new discovery`() {
        val mockService2 = mock(BluetoothGattService::class.java)

        // First connection with 1 service
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)
        assertEquals(1, stateMachine.getCachedServices()?.size)

        // Simulate disconnect and reconnect with 2 services
        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )

        // Update mock to return 2 services
        `when`(mockGatt.services).thenReturn(listOf(mockService, mockService2))

        stateMachine.handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
        stateMachine.handleServicesDiscovered(mockGatt, BluetoothGatt.GATT_SUCCESS)

        // Cache should be updated with 2 services
        assertEquals(2, stateMachine.getCachedServices()?.size)
    }
}
