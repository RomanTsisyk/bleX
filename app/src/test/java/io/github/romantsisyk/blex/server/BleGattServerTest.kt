package io.github.romantsisyk.blex.server

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import io.github.romantsisyk.blex.connection.BleConnection.Companion.CLIENT_CHARACTERISTIC_CONFIG_UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for BleGattServer.
 *
 * These tests verify the GATT server functionality including initialization,
 * characteristic value management, notification handling, and callback processing.
 */
@RunWith(MockitoJUnitRunner::class)
class BleGattServerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothManager: BluetoothManager

    @Mock
    private lateinit var mockBluetoothGattServer: BluetoothGattServer

    @Mock
    private lateinit var mockBluetoothLeAdvertiser: BluetoothLeAdvertiser

    @Mock
    private lateinit var mockBluetoothDevice: BluetoothDevice

    @Mock
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic

    @Mock
    private lateinit var mockDescriptor: BluetoothGattDescriptor

    @Mock
    private lateinit var mockService: BluetoothGattService

    private lateinit var bleGattServer: BleGattServer
    private lateinit var gattServerCallbackCaptor: ArgumentCaptor<BluetoothGattServerCallback>

    companion object {
        private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
        private val TEST_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
    }

    @Before
    fun setUp() {
        // Setup BluetoothManager mock
        whenever(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager)

        // Setup BluetoothLeAdvertiser mock
        whenever(mockBluetoothAdapter.bluetoothLeAdvertiser).thenReturn(mockBluetoothLeAdvertiser)

        // Setup device address
        whenever(mockBluetoothDevice.address).thenReturn(TEST_DEVICE_ADDRESS)

        // Capture the callback when openGattServer is called
        gattServerCallbackCaptor = ArgumentCaptor.forClass(BluetoothGattServerCallback::class.java)

        // Create the BleGattServer instance
        bleGattServer = BleGattServer(mockContext, mockBluetoothAdapter)
    }

    // ==================== Test 1: startServer initializes GATT server ====================

    @Test
    fun `startServer initializes GATT server`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)

        // Execute
        bleGattServer.startServer()

        // Verify
        verify(mockBluetoothManager).openGattServer(eq(mockContext), any())
        verify(mockBluetoothGattServer).addService(any())
        verify(mockBluetoothLeAdvertiser).startAdvertising(any(), any(), any())
    }

    @Test
    fun `startServer adds Heart Rate service to GATT server`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)

        val serviceCaptor = argumentCaptor<BluetoothGattService>()

        // Execute
        bleGattServer.startServer()

        // Verify
        verify(mockBluetoothGattServer).addService(serviceCaptor.capture())
        val capturedService = serviceCaptor.firstValue
        assertEquals(HEART_RATE_SERVICE_UUID, capturedService.uuid)
        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, capturedService.type)
    }

    // ==================== Test 2: stopServer closes GATT server ====================

    @Test
    fun `stopServer closes GATT server`() {
        // Setup - first start the server
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)
        bleGattServer.startServer()

        // Execute
        bleGattServer.stopServer()

        // Verify
        verify(mockBluetoothGattServer).close()
        verify(mockBluetoothLeAdvertiser).stopAdvertising(any())
    }

    @Test
    fun `stopServer does not crash when server was never started`() {
        // Execute - should not throw
        bleGattServer.stopServer()

        // Verify - no interactions with the server (it's null)
        verify(mockBluetoothManager, never()).openGattServer(any(), any())
    }

    @Test
    fun `stopServer can be called multiple times safely`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)
        bleGattServer.startServer()

        // Execute
        bleGattServer.stopServer()
        bleGattServer.stopServer()

        // Verify - close should only be called once (server is null after first stop)
        verify(mockBluetoothGattServer, times(1)).close()
    }

    // ==================== Test 3: addServices adds services to server ====================

    @Test
    fun `addServices adds characteristic with NOTIFY property`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)

        val serviceCaptor = argumentCaptor<BluetoothGattService>()

        // Execute
        bleGattServer.startServer()

        // Verify
        verify(mockBluetoothGattServer).addService(serviceCaptor.capture())
        val capturedService = serviceCaptor.firstValue
        val characteristic = capturedService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

        assertNotNull(characteristic)
        assertTrue((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)
    }

    @Test
    fun `addServices adds CCCD descriptor to characteristic`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)

        val serviceCaptor = argumentCaptor<BluetoothGattService>()

        // Execute
        bleGattServer.startServer()

        // Verify
        verify(mockBluetoothGattServer).addService(serviceCaptor.capture())
        val capturedService = serviceCaptor.firstValue
        val characteristic = capturedService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)
        val descriptor = characteristic?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)

        assertNotNull(descriptor)
    }

    // ==================== Test 4: setCharacteristicValue stores value ====================

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

    // ==================== Test 5: getCharacteristicValue retrieves stored value ====================

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

    // ==================== Test 6: sendNotification sends to subscribed devices ====================

    @Test
    fun `sendNotification sends to subscribed devices`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        // Simulate enabling notifications via descriptor write
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        val testValue = byteArrayOf(0x01, 0x02)

        // Execute
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)

        // Verify
        assertTrue(result)
        verify(mockBluetoothGattServer).notifyCharacteristicChanged(
            eq(mockBluetoothDevice),
            eq(mockCharacteristic),
            eq(false)
        )
    }

    @Test
    fun `sendNotification stores value before sending`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        // Simulate enabling notifications
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        val testValue = byteArrayOf(0x05, 0x06, 0x07)

        // Execute
        bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)

        // Verify - value should be stored
        assertArrayEquals(testValue, bleGattServer.getCharacteristicValue(HEART_RATE_MEASUREMENT_UUID))
    }

    // ==================== Test 7: sendNotification does nothing when no subscribers ====================

    @Test
    fun `sendNotification returns false when no subscribers`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        val testValue = byteArrayOf(0x01, 0x02)

        // Execute - no devices have enabled notifications
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)

        // Verify
        assertFalse(result)
        verify(mockBluetoothGattServer, never()).notifyCharacteristicChanged(any(), any(), anyBoolean())
    }

    @Test
    fun `sendNotification returns false when server not started`() {
        // Setup - server not started
        val testValue = byteArrayOf(0x01, 0x02)

        // Execute
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)

        // Verify
        assertFalse(result)
    }

    @Test
    fun `sendNotification returns false for unknown characteristic UUID`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), any()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(any())).thenReturn(null)

        bleGattServer.startServer()

        val unknownUUID = UUID.randomUUID()
        val testValue = byteArrayOf(0x01, 0x02)

        // Execute
        val result = bleGattServer.sendNotification(unknownUUID, testValue)

        // Verify
        assertFalse(result)
    }

    // ==================== Test 8: onCharacteristicReadRequest returns stored value ====================

    @Test
    fun `onCharacteristicReadRequest returns stored value`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        // Store a value
        val storedValue = byteArrayOf(0x10, 0x20, 0x30)
        bleGattServer.setCharacteristicValue(HEART_RATE_MEASUREMENT_UUID, storedValue)

        // Execute - simulate read request
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicReadRequest(mockBluetoothDevice, 1, 0, mockCharacteristic)

        // Verify - should send the stored value
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(storedValue)
        )
    }

    @Test
    fun `onCharacteristicReadRequest handles offset for partial reads`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        // Store a value
        val storedValue = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        bleGattServer.setCharacteristicValue(HEART_RATE_MEASUREMENT_UUID, storedValue)

        // Execute - simulate read request with offset
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicReadRequest(mockBluetoothDevice, 1, 2, mockCharacteristic)

        // Verify - should send value from offset
        val expectedValue = byteArrayOf(0x30, 0x40)
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(2),
            eq(expectedValue)
        )
    }

    @Test
    fun `onCharacteristicReadRequest returns empty array when offset exceeds value size`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        // Store a value
        val storedValue = byteArrayOf(0x10, 0x20)
        bleGattServer.setCharacteristicValue(HEART_RATE_MEASUREMENT_UUID, storedValue)

        // Execute - simulate read request with offset beyond value size
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicReadRequest(mockBluetoothDevice, 1, 10, mockCharacteristic)

        // Verify - should send empty array
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(10),
            eq(ByteArray(0))
        )
    }

    @Test
    fun `onCharacteristicReadRequest returns empty array when no value stored`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)
        whenever(mockCharacteristic.value).thenReturn(null)

        bleGattServer.startServer()

        // Execute - simulate read request without any stored value
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicReadRequest(mockBluetoothDevice, 1, 0, mockCharacteristic)

        // Verify - should send empty array
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(ByteArray(0))
        )
    }

    // ==================== Test 9: onCharacteristicWriteRequest stores value and responds ====================

    @Test
    fun `onCharacteristicWriteRequest stores value and sends response`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        val writeValue = byteArrayOf(0x50, 0x60, 0x70)

        // Execute - simulate write request
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicWriteRequest(
            mockBluetoothDevice,
            1,
            mockCharacteristic,
            false,  // preparedWrite
            true,   // responseNeeded
            0,      // offset
            writeValue
        )

        // Verify - value should be stored
        assertArrayEquals(writeValue, bleGattServer.getCharacteristicValue(HEART_RATE_MEASUREMENT_UUID))

        // Verify - response should be sent
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(writeValue)
        )
    }

    @Test
    fun `onCharacteristicWriteRequest does not send response when not needed`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        val writeValue = byteArrayOf(0x50, 0x60)

        // Execute - simulate write request with responseNeeded=false
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicWriteRequest(
            mockBluetoothDevice,
            1,
            mockCharacteristic,
            false,  // preparedWrite
            false,  // responseNeeded
            0,      // offset
            writeValue
        )

        // Verify - value should be stored
        assertArrayEquals(writeValue, bleGattServer.getCharacteristicValue(HEART_RATE_MEASUREMENT_UUID))

        // Verify - no response should be sent
        verify(mockBluetoothGattServer, never()).sendResponse(any(), anyInt(), anyInt(), anyInt(), any())
    }

    @Test
    fun `onCharacteristicWriteRequest handles prepared write with offset`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        // First write some initial data
        val initialValue = byteArrayOf(0x01, 0x02, 0x03)
        bleGattServer.setCharacteristicValue(HEART_RATE_MEASUREMENT_UUID, initialValue)

        val appendValue = byteArrayOf(0x04, 0x05)

        // Execute - simulate prepared write with offset
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicWriteRequest(
            mockBluetoothDevice,
            1,
            mockCharacteristic,
            true,   // preparedWrite
            true,   // responseNeeded
            3,      // offset
            appendValue
        )

        // Verify - value should be appended at offset
        val expectedValue = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        assertArrayEquals(expectedValue, bleGattServer.getCharacteristicValue(HEART_RATE_MEASUREMENT_UUID))
    }

    @Test
    fun `onCharacteristicWriteRequest handles null value`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockCharacteristic.uuid).thenReturn(HEART_RATE_MEASUREMENT_UUID)

        bleGattServer.startServer()

        // Execute - simulate write request with null value
        val callback = gattServerCallbackCaptor.value
        callback.onCharacteristicWriteRequest(
            mockBluetoothDevice,
            1,
            mockCharacteristic,
            false,
            true,
            0,
            null
        )

        // Verify - empty array should be stored
        assertArrayEquals(ByteArray(0), bleGattServer.getCharacteristicValue(HEART_RATE_MEASUREMENT_UUID))
    }

    // ==================== Test 10: onDescriptorWriteRequest enables/disables notifications ====================

    @Test
    fun `onDescriptorWriteRequest enables notifications`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        // Execute - enable notifications
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        // Verify - response should be sent
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        )

        // Verify - device should be subscribed (by sending a notification)
        val testValue = byteArrayOf(0x01)
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)
        assertTrue(result)
    }

    @Test
    fun `onDescriptorWriteRequest disables notifications`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        val callback = gattServerCallbackCaptor.value

        // First enable notifications
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        // Then disable notifications
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            2,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        )

        // Verify - device should be unsubscribed (notification should fail)
        val testValue = byteArrayOf(0x01)
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)
        assertFalse(result)
    }

    @Test
    fun `onDescriptorWriteRequest enables indications`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        // Execute - enable indications
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        )

        // Verify - device should be subscribed
        val testValue = byteArrayOf(0x01)
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)
        assertTrue(result)
    }

    @Test
    fun `onDescriptorWriteRequest does not send response when not needed`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        // Execute - enable notifications with responseNeeded=false
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            false,  // responseNeeded
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        // Verify - no response should be sent
        verify(mockBluetoothGattServer, never()).sendResponse(any(), anyInt(), anyInt(), anyInt(), any())
    }

    // ==================== Additional Edge Case Tests ====================

    @Test
    fun `device is removed from subscribers on disconnect`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockBluetoothGattServer.services).thenReturn(listOf(mockService))
        whenever(mockService.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)).thenReturn(mockCharacteristic)
        whenever(mockBluetoothGattServer.notifyCharacteristicChanged(any(), any(), eq(false)))
            .thenReturn(true)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        val callback = gattServerCallbackCaptor.value

        // Enable notifications
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        // Simulate device disconnection
        callback.onConnectionStateChange(
            mockBluetoothDevice,
            BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
        )

        // Verify - device should be removed from subscribers
        val testValue = byteArrayOf(0x01)
        val result = bleGattServer.sendNotification(HEART_RATE_MEASUREMENT_UUID, testValue)
        assertFalse(result)
    }

    @Test
    fun `onExecuteWrite sends success response`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)

        bleGattServer.startServer()

        // Execute
        val callback = gattServerCallbackCaptor.value
        callback.onExecuteWrite(mockBluetoothDevice, 1, true)

        // Verify
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(null)
        )
    }

    @Test
    fun `onDescriptorReadRequest returns notification state for CCCD`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        whenever(mockDescriptor.characteristic).thenReturn(mockCharacteristic)

        bleGattServer.startServer()

        val callback = gattServerCallbackCaptor.value

        // First enable notifications
        callback.onDescriptorWriteRequest(
            mockBluetoothDevice,
            1,
            mockDescriptor,
            false,
            true,
            0,
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        )

        // Execute - read descriptor
        callback.onDescriptorReadRequest(mockBluetoothDevice, 2, 0, mockDescriptor)

        // Verify - should return ENABLE_NOTIFICATION_VALUE
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(2),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        )
    }

    @Test
    fun `onDescriptorReadRequest returns disabled state for CCCD when not subscribed`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)
        whenever(mockDescriptor.uuid).thenReturn(CLIENT_CHARACTERISTIC_CONFIG_UUID)

        bleGattServer.startServer()

        // Execute - read descriptor without enabling notifications first
        val callback = gattServerCallbackCaptor.value
        callback.onDescriptorReadRequest(mockBluetoothDevice, 1, 0, mockDescriptor)

        // Verify - should return DISABLE_NOTIFICATION_VALUE
        verify(mockBluetoothGattServer).sendResponse(
            eq(mockBluetoothDevice),
            eq(1),
            eq(BluetoothGatt.GATT_SUCCESS),
            eq(0),
            eq(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        )
    }

    @Test
    fun `onMtuChanged callback is handled without error`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)

        bleGattServer.startServer()

        // Execute - should not throw
        val callback = gattServerCallbackCaptor.value
        callback.onMtuChanged(mockBluetoothDevice, 512)

        // No verification needed - just ensure no exception is thrown
    }

    @Test
    fun `onNotificationSent callback is handled without error`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)

        bleGattServer.startServer()

        // Execute - should not throw
        val callback = gattServerCallbackCaptor.value
        callback.onNotificationSent(mockBluetoothDevice, BluetoothGatt.GATT_SUCCESS)

        // No verification needed - just ensure no exception is thrown
    }

    @Test
    fun `onConnectionStateChange handles connected state`() {
        // Setup
        whenever(mockBluetoothManager.openGattServer(eq(mockContext), gattServerCallbackCaptor.capture()))
            .thenReturn(mockBluetoothGattServer)

        bleGattServer.startServer()

        // Execute - should not throw
        val callback = gattServerCallbackCaptor.value
        callback.onConnectionStateChange(
            mockBluetoothDevice,
            BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
        )

        // No verification needed - just ensure no exception is thrown
    }
}
