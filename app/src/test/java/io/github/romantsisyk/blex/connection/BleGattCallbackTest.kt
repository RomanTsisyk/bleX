package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.util.UUID

/**
 * Unit tests for BleGattCallback.
 *
 * These tests verify that BleGattCallback properly delegates all callback events
 * to the ConnectionStateMachine.
 */
@RunWith(MockitoJUnitRunner::class)
class BleGattCallbackTest {

    @Mock
    private lateinit var mockStateMachine: ConnectionStateMachine

    @Mock
    private lateinit var mockBondManager: BondManager

    @Mock
    private lateinit var mockGatt: BluetoothGatt

    @Mock
    private lateinit var mockCharacteristic: BluetoothGattCharacteristic

    @Mock
    private lateinit var mockDescriptor: BluetoothGattDescriptor

    private lateinit var callback: BleGattCallback

    @Before
    fun setUp() {
        callback = BleGattCallback(mockStateMachine, mockBondManager)
    }

    // ==================== onConnectionStateChange Tests ====================

    @Test
    fun `onConnectionStateChange delegates to state machine with connected state`() {
        val status = BluetoothGatt.GATT_SUCCESS
        val newState = BluetoothProfile.STATE_CONNECTED

        callback.onConnectionStateChange(mockGatt, status, newState)

        verify(mockStateMachine).handleConnectionStateChange(mockGatt, status, newState)
    }

    @Test
    fun `onConnectionStateChange delegates to state machine with disconnected state`() {
        val status = BluetoothGatt.GATT_SUCCESS
        val newState = BluetoothProfile.STATE_DISCONNECTED

        callback.onConnectionStateChange(mockGatt, status, newState)

        verify(mockStateMachine).handleConnectionStateChange(mockGatt, status, newState)
    }

    @Test
    fun `onConnectionStateChange delegates to state machine with failure status`() {
        val status = 133 // Common BLE error code
        val newState = BluetoothProfile.STATE_DISCONNECTED

        callback.onConnectionStateChange(mockGatt, status, newState)

        verify(mockStateMachine).handleConnectionStateChange(mockGatt, status, newState)
    }

    // ==================== onServicesDiscovered Tests ====================

    @Test
    fun `onServicesDiscovered delegates to state machine with success status`() {
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onServicesDiscovered(mockGatt, status)

        verify(mockStateMachine).handleServicesDiscovered(mockGatt, status)
    }

    @Test
    fun `onServicesDiscovered delegates to state machine with failure status`() {
        val status = BluetoothGatt.GATT_FAILURE

        callback.onServicesDiscovered(mockGatt, status)

        verify(mockStateMachine).handleServicesDiscovered(mockGatt, status)
    }

    // ==================== onMtuChanged Tests ====================

    @Test
    fun `onMtuChanged delegates to state machine with success status`() {
        val mtu = 512
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onMtuChanged(mockGatt, mtu, status)

        verify(mockStateMachine).handleMtuChanged(mockGatt, mtu, status)
    }

    @Test
    fun `onMtuChanged delegates to state machine with failure status`() {
        val mtu = 23 // Default MTU
        val status = BluetoothGatt.GATT_FAILURE

        callback.onMtuChanged(mockGatt, mtu, status)

        verify(mockStateMachine).handleMtuChanged(mockGatt, mtu, status)
    }

    @Test
    fun `onMtuChanged delegates to state machine with various MTU values`() {
        val mtu = 247 // Common negotiated MTU
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onMtuChanged(mockGatt, mtu, status)

        verify(mockStateMachine).handleMtuChanged(mockGatt, mtu, status)
    }

    // ==================== onPhyUpdate Tests ====================

    @Test
    fun `onPhyUpdate delegates to state machine with success status`() {
        val txPhy = 2 // PHY_LE_2M
        val rxPhy = 2
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onPhyUpdate(mockGatt, txPhy, rxPhy, status)

        verify(mockStateMachine).handlePhyUpdate(mockGatt, txPhy, rxPhy, status)
    }

    @Test
    fun `onPhyUpdate delegates to state machine with failure status`() {
        val txPhy = 1 // PHY_LE_1M
        val rxPhy = 1
        val status = BluetoothGatt.GATT_FAILURE

        callback.onPhyUpdate(mockGatt, txPhy, rxPhy, status)

        verify(mockStateMachine).handlePhyUpdate(mockGatt, txPhy, rxPhy, status)
    }

    @Test
    fun `onPhyUpdate delegates to state machine with coded PHY values`() {
        val txPhy = 3 // PHY_LE_CODED
        val rxPhy = 3
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onPhyUpdate(mockGatt, txPhy, rxPhy, status)

        verify(mockStateMachine).handlePhyUpdate(mockGatt, txPhy, rxPhy, status)
    }

    // ==================== onCharacteristicRead Tests ====================

    @Test
    fun `onCharacteristicRead delegates to state machine with success status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_SUCCESS

        @Suppress("DEPRECATION")
        callback.onCharacteristicRead(mockGatt, mockCharacteristic, status)

        verify(mockStateMachine).handleCharacteristicRead(mockGatt, mockCharacteristic, status)
    }

    @Test
    fun `onCharacteristicRead delegates to state machine with failure status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_FAILURE

        @Suppress("DEPRECATION")
        callback.onCharacteristicRead(mockGatt, mockCharacteristic, status)

        verify(mockStateMachine).handleCharacteristicRead(mockGatt, mockCharacteristic, status)
    }

    // ==================== onCharacteristicWrite Tests ====================

    @Test
    fun `onCharacteristicWrite delegates to state machine with success status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onCharacteristicWrite(mockGatt, mockCharacteristic, status)

        verify(mockStateMachine).handleCharacteristicWrite(mockGatt, mockCharacteristic, status)
    }

    @Test
    fun `onCharacteristicWrite delegates to state machine with failure status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_FAILURE

        callback.onCharacteristicWrite(mockGatt, mockCharacteristic, status)

        verify(mockStateMachine).handleCharacteristicWrite(mockGatt, mockCharacteristic, status)
    }

    // ==================== onCharacteristicChanged (Legacy) Tests ====================

    @Test
    fun `onCharacteristicChanged legacy delegates to state machine`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)

        @Suppress("DEPRECATION")
        callback.onCharacteristicChanged(mockGatt, mockCharacteristic)

        verify(mockStateMachine).handleCharacteristicChanged(mockGatt, mockCharacteristic)
    }

    @Test
    fun `onCharacteristicChanged legacy delegates characteristic with specific UUID`() {
        val specificUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb") // Heart Rate Measurement
        `when`(mockCharacteristic.uuid).thenReturn(specificUuid)

        @Suppress("DEPRECATION")
        callback.onCharacteristicChanged(mockGatt, mockCharacteristic)

        verify(mockStateMachine).handleCharacteristicChanged(mockGatt, mockCharacteristic)
    }

    // ==================== onCharacteristicChanged (API 33+) Tests ====================

    @Test
    fun `onCharacteristicChanged API 33+ delegates to state machine with value`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val value = byteArrayOf(0x01, 0x02, 0x03)

        callback.onCharacteristicChanged(mockGatt, mockCharacteristic, value)

        verify(mockStateMachine).handleCharacteristicChanged(mockGatt, mockCharacteristic, value)
    }

    @Test
    fun `onCharacteristicChanged API 33+ delegates to state machine with empty value`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val value = byteArrayOf()

        callback.onCharacteristicChanged(mockGatt, mockCharacteristic, value)

        verify(mockStateMachine).handleCharacteristicChanged(mockGatt, mockCharacteristic, value)
    }

    @Test
    fun `onCharacteristicChanged API 33+ delegates to state machine with large value`() {
        val testUuid = UUID.randomUUID()
        `when`(mockCharacteristic.uuid).thenReturn(testUuid)
        val value = ByteArray(512) { it.toByte() } // Large payload

        callback.onCharacteristicChanged(mockGatt, mockCharacteristic, value)

        verify(mockStateMachine).handleCharacteristicChanged(mockGatt, mockCharacteristic, value)
    }

    // ==================== onDescriptorWrite Tests ====================

    @Test
    fun `onDescriptorWrite delegates to state machine with success status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockDescriptor.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onDescriptorWrite(mockGatt, mockDescriptor, status)

        verify(mockStateMachine).handleDescriptorWrite(mockGatt, mockDescriptor, status)
    }

    @Test
    fun `onDescriptorWrite delegates to state machine with failure status`() {
        val testUuid = UUID.randomUUID()
        `when`(mockDescriptor.uuid).thenReturn(testUuid)
        val status = BluetoothGatt.GATT_FAILURE

        callback.onDescriptorWrite(mockGatt, mockDescriptor, status)

        verify(mockStateMachine).handleDescriptorWrite(mockGatt, mockDescriptor, status)
    }

    @Test
    fun `onDescriptorWrite delegates CCCD descriptor correctly`() {
        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        `when`(mockDescriptor.uuid).thenReturn(cccdUuid)
        val status = BluetoothGatt.GATT_SUCCESS

        callback.onDescriptorWrite(mockGatt, mockDescriptor, status)

        verify(mockStateMachine).handleDescriptorWrite(mockGatt, mockDescriptor, status)
    }

    // ==================== Constructor Tests ====================

    @Test
    fun `callback can be created without bond manager`() {
        val callbackWithoutBondManager = BleGattCallback(mockStateMachine)

        // Should be able to call methods without error
        callbackWithoutBondManager.onConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        verify(mockStateMachine).handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
    }

    @Test
    fun `callback can be created with bond manager`() {
        val callbackWithBondManager = BleGattCallback(mockStateMachine, mockBondManager)

        callbackWithBondManager.onConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        verify(mockStateMachine).handleConnectionStateChange(
            mockGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )
    }
}
