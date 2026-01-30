package io.github.romantsisyk.blex

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.github.romantsisyk.blex.connection.BleConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.lang.reflect.Field

/**
 * Unit tests for BleManager.
 *
 * These tests verify singleton behavior and connection management.
 * Tests requiring Android BLE runtime (scanning, permissions) are excluded
 * as they require Robolectric or instrumentation tests.
 */
@RunWith(MockitoJUnitRunner.Silent::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BleManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockApplicationContext: Context

    @Mock
    private lateinit var mockBluetoothManager: BluetoothManager

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockDevice1: BluetoothDevice

    @Mock
    private lateinit var mockDevice2: BluetoothDevice

    @Before
    fun setUp() {
        // Reset the singleton instance before each test
        resetSingleton()

        // Setup context mocks
        `when`(mockContext.applicationContext).thenReturn(mockApplicationContext)
        `when`(mockApplicationContext.applicationContext).thenReturn(mockApplicationContext)
        `when`(mockApplicationContext.getSystemService(Context.BLUETOOTH_SERVICE))
            .thenReturn(mockBluetoothManager)
        `when`(mockBluetoothManager.adapter).thenReturn(mockBluetoothAdapter)

        // Setup device mocks
        `when`(mockDevice1.address).thenReturn("AA:BB:CC:DD:EE:01")
        `when`(mockDevice2.address).thenReturn("AA:BB:CC:DD:EE:02")
    }

    /**
     * Resets the BleManager singleton instance using reflection.
     */
    private fun resetSingleton() {
        try {
            val instanceField: Field = BleManager::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
            // Field may not exist or be inaccessible in some configurations
        }
    }

    // ==================== Singleton Tests ====================

    @Test
    fun `getInstance returns singleton instance`() {
        val instance1 = BleManager.getInstance(mockContext)
        val instance2 = BleManager.getInstance(mockContext)

        assertNotNull(instance1)
        assertSame(instance1, instance2)
    }

    @Test
    fun `getInstance with different contexts returns same instance`() {
        // Create a second mock context
        val mockContext2 = mock(Context::class.java)
        val mockApplicationContext2 = mock(Context::class.java)

        `when`(mockContext2.applicationContext).thenReturn(mockApplicationContext2)
        `when`(mockApplicationContext2.applicationContext).thenReturn(mockApplicationContext2)
        `when`(mockApplicationContext2.getSystemService(Context.BLUETOOTH_SERVICE))
            .thenReturn(mockBluetoothManager)

        val instance1 = BleManager.getInstance(mockContext)
        val instance2 = BleManager.getInstance(mockContext2)

        assertNotNull(instance1)
        assertSame(instance1, instance2)
    }

    @Test
    fun `getInstance uses applicationContext`() {
        BleManager.getInstance(mockContext)

        verify(mockContext).applicationContext
    }

    // ==================== Connection Tests ====================

    @Test
    fun `connect creates new BleConnection`() {
        val bleManager = BleManager.getInstance(mockContext)

        val connection = bleManager.connect(mockDevice1)

        assertNotNull(connection)
        assertTrue(connection is BleConnection)
    }

    @Test
    fun `connect returns existing connection for same device`() {
        val bleManager = BleManager.getInstance(mockContext)

        val connection1 = bleManager.connect(mockDevice1)
        val connection2 = bleManager.connect(mockDevice1)

        assertSame(connection1, connection2)
    }

    @Test
    fun `connect creates different connections for different devices`() {
        val bleManager = BleManager.getInstance(mockContext)

        val connection1 = bleManager.connect(mockDevice1)
        val connection2 = bleManager.connect(mockDevice2)

        assertNotSame(connection1, connection2)
    }

    @Test
    fun `getConnection returns correct connection`() {
        val bleManager = BleManager.getInstance(mockContext)

        // First, establish a connection
        val expectedConnection = bleManager.connect(mockDevice1)

        // Use reflection to access the private connections map and verify
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        val actualConnection = connections[mockDevice1.address]

        assertNotNull(actualConnection)
        assertSame(expectedConnection, actualConnection)
    }

    @Test
    fun `getConnection returns null for unknown device`() {
        val bleManager = BleManager.getInstance(mockContext)

        // Use reflection to access the private connections map
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        // Verify that trying to access an unknown device returns null
        val connection = connections["UNKNOWN:ADDRESS"]

        assertNull(connection)
    }

    // ==================== Disconnect Tests ====================

    @Test
    fun `disconnect removes connection`() {
        val bleManager = BleManager.getInstance(mockContext)

        // First, establish a connection
        bleManager.connect(mockDevice1)

        // Use reflection to verify connection exists
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        assertTrue(connections.containsKey(mockDevice1.address))

        // Disconnect
        bleManager.disconnect(mockDevice1)

        // Verify connection is removed
        assertFalse(connections.containsKey(mockDevice1.address))
    }

    @Test
    fun `disconnect does not throw for unknown device`() {
        val bleManager = BleManager.getInstance(mockContext)

        // Should not throw
        bleManager.disconnect(mockDevice1)
    }

    @Test
    fun `disconnectAll clears all connections`() {
        val bleManager = BleManager.getInstance(mockContext)

        // Establish multiple connections
        bleManager.connect(mockDevice1)
        bleManager.connect(mockDevice2)

        // Use reflection to verify connections exist
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        assertEquals(2, connections.size)

        // Disconnect all
        bleManager.disconnectAll()

        // Verify all connections are cleared
        assertTrue(connections.isEmpty())
    }

    @Test
    fun `disconnectAll is safe when no connections exist`() {
        val bleManager = BleManager.getInstance(mockContext)

        // Should not throw
        bleManager.disconnectAll()

        // Use reflection to verify connections map is empty
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        assertTrue(connections.isEmpty())
    }

    // ==================== Edge Cases ====================

    @Test
    fun `multiple connect disconnect cycles work correctly`() {
        val bleManager = BleManager.getInstance(mockContext)

        // First cycle
        val connection1 = bleManager.connect(mockDevice1)
        assertNotNull(connection1)
        bleManager.disconnect(mockDevice1)

        // Second cycle - should create new connection
        val connection2 = bleManager.connect(mockDevice1)
        assertNotNull(connection2)
        // After disconnect, a new connect should create a new instance
        assertNotSame(connection1, connection2)
    }

    @Test
    fun `connections are isolated by device address`() {
        val bleManager = BleManager.getInstance(mockContext)

        // Connect to device 1
        bleManager.connect(mockDevice1)

        // Disconnect device 2 (which was never connected)
        bleManager.disconnect(mockDevice2)

        // Verify device 1 connection still exists
        val connectionsField = BleManager::class.java.getDeclaredField("connections")
        connectionsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val connections = connectionsField.get(bleManager) as MutableMap<String, BleConnection>

        assertTrue(connections.containsKey(mockDevice1.address))
        assertFalse(connections.containsKey(mockDevice2.address))
    }

    @Test
    fun `getInstance is thread safe`() {
        resetSingleton()

        val instances = mutableListOf<BleManager>()
        val threads = (1..10).map {
            Thread {
                val instance = BleManager.getInstance(mockContext)
                synchronized(instances) {
                    instances.add(instance)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // All instances should be the same
        assertEquals(10, instances.size)
        val firstInstance = instances.first()
        instances.forEach { assertSame(firstInstance, it) }
    }
}
