package io.github.romantsisyk.blex.connection

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.github.romantsisyk.blex.models.BondState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for BondManager.
 *
 * These tests verify the bonding flow, state emissions,
 * and proper cleanup of BroadcastReceivers.
 */
@RunWith(MockitoJUnitRunner.Silent::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BondManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockDevice: BluetoothDevice

    @Mock
    private lateinit var mockIntent: Intent

    private lateinit var bondManager: BondManager
    private lateinit var receiverCaptor: ArgumentCaptor<BroadcastReceiver>

    private val testDeviceAddress = "AA:BB:CC:DD:EE:FF"

    @Before
    fun setUp() {
        `when`(mockDevice.address).thenReturn(testDeviceAddress)
        receiverCaptor = ArgumentCaptor.forClass(BroadcastReceiver::class.java)
        bondManager = BondManager(mockContext)
    }

    // ==================== Test 1: bondDevice creates bond for unbonded device ====================

    @Test
    fun `bondDevice creates bond for unbonded device`() = runTest {
        // Given: device is not bonded
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        // When: bondDevice is called
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { }
        }

        // Then: createBond should be called
        verify(mockDevice).createBond()

        // Cleanup
        job.cancel()
    }

    @Test
    fun `bondDevice does not create bond for already bonded device`() = runTest {
        // Given: device is already bonded
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_BONDED)

        // When: bondDevice is called
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { }
        }

        // Then: createBond should NOT be called
        verify(mockDevice, never()).createBond()

        // Cleanup
        job.cancel()
    }

    @Test
    fun `bondDevice does not create bond for device currently bonding`() = runTest {
        // Given: device is currently bonding
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_BONDING)

        // When: bondDevice is called
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { }
        }

        // Then: createBond should NOT be called
        verify(mockDevice, never()).createBond()

        // Cleanup
        job.cancel()
    }

    // ==================== Test 2: bondDevice emits BondState changes ====================

    @Test
    fun `bondDevice emits BondState changes`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When: start collecting and simulate bond state change
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        // Capture the registered receiver
        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate BOND_BONDING broadcast
        val bondingIntent = createBondStateIntent(BluetoothDevice.BOND_BONDING)
        capturedReceiver.onReceive(mockContext, bondingIntent)

        // Simulate BOND_BONDED broadcast (this will close the flow)
        val bondedIntent = createBondStateIntent(BluetoothDevice.BOND_BONDED)
        capturedReceiver.onReceive(mockContext, bondedIntent)

        // Then: states should be emitted
        assertEquals(2, emittedStates.size)
        assertEquals(BondState.BOND_BONDING, emittedStates[0])
        assertEquals(BondState.BOND_BONDED, emittedStates[1])

        job.cancel()
    }

    // ==================== Test 3: bondDevice emits BOND_BONDING during process ====================

    @Test
    fun `bondDevice emits BOND_BONDING during process`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate BOND_BONDING broadcast
        val bondingIntent = createBondStateIntent(BluetoothDevice.BOND_BONDING)
        capturedReceiver.onReceive(mockContext, bondingIntent)

        // Then: BOND_BONDING should be emitted
        assertTrue(emittedStates.contains(BondState.BOND_BONDING))
        assertEquals(BondState.BOND_BONDING, emittedStates.first())

        job.cancel()
    }

    // ==================== Test 4: bondDevice emits BOND_BONDED on success ====================

    @Test
    fun `bondDevice emits BOND_BONDED on success`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate successful bonding sequence
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDING))
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDED))

        // Then: BOND_BONDED should be the last state emitted
        assertTrue(emittedStates.contains(BondState.BOND_BONDED))
        assertEquals(BondState.BOND_BONDED, emittedStates.last())

        job.cancel()
    }

    // ==================== Test 5: bondDevice emits BOND_NONE on failure ====================

    @Test
    fun `bondDevice emits BOND_NONE on failure`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate failed bonding - goes from BONDING to NONE
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDING))
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_NONE))

        // Then: BOND_NONE should be emitted (indicating failure)
        assertEquals(2, emittedStates.size)
        assertEquals(BondState.BOND_BONDING, emittedStates[0])
        assertEquals(BondState.BOND_NONE, emittedStates[1])

        job.cancel()
    }

    // ==================== Test 6: Flow closes after BOND_BONDED ====================

    @Test
    fun `flow closes after BOND_BONDED`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()
        var flowCompleted = false

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
            flowCompleted = true
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate successful bonding
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDED))

        // Allow coroutine to complete
        job.join()

        // Then: flow should be completed
        assertTrue(flowCompleted)
        assertEquals(1, emittedStates.size)
        assertEquals(BondState.BOND_BONDED, emittedStates.last())
    }

    // ==================== Test 7: Flow closes after BOND_NONE (failed) ====================

    @Test
    fun `flow closes after BOND_NONE when bonding fails`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()
        var flowCompleted = false

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
            flowCompleted = true
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate failed bonding - BOND_NONE after BOND_BONDING
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDING))
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_NONE))

        // Allow coroutine to complete
        job.join()

        // Then: flow should be completed after receiving BOND_NONE
        assertTrue(flowCompleted)
        assertEquals(BondState.BOND_NONE, emittedStates.last())
    }

    // ==================== Test 8: BroadcastReceiver properly unregistered on flow completion ====================

    @Test
    fun `BroadcastReceiver properly unregistered on flow completion`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate successful bonding to close flow
        capturedReceiver.onReceive(mockContext, createBondStateIntent(BluetoothDevice.BOND_BONDED))

        // Allow coroutine to complete
        job.join()

        // Then: unregisterReceiver should be called with the same receiver
        verify(mockContext).unregisterReceiver(capturedReceiver)
    }

    @Test
    fun `BroadcastReceiver unregistered when flow is cancelled`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Cancel the flow
        job.cancel()
        job.join()

        // Then: unregisterReceiver should be called
        verify(mockContext).unregisterReceiver(capturedReceiver)
    }

    // ==================== Additional Edge Cases ====================

    @Suppress("DEPRECATION")
    @Test
    fun `bondDevice ignores broadcasts from different devices`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Create a different device with different address
        val differentDevice = mock(BluetoothDevice::class.java)
        `when`(differentDevice.address).thenReturn("11:22:33:44:55:66")

        // Simulate broadcast from different device
        val differentDeviceIntent = mock(Intent::class.java)
        `when`(differentDeviceIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE))
            .thenReturn(differentDevice)
        `when`(differentDeviceIntent.getIntExtra(eq(BluetoothDevice.EXTRA_BOND_STATE), anyInt()))
            .thenReturn(BluetoothDevice.BOND_BONDED)

        capturedReceiver.onReceive(mockContext, differentDeviceIntent)

        // Then: no state should be emitted for different device
        assertTrue(emittedStates.isEmpty())

        job.cancel()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `bondDevice handles null device in broadcast gracefully`() = runTest {
        // Given
        `when`(mockDevice.bondState).thenReturn(BluetoothDevice.BOND_NONE)
        `when`(mockDevice.createBond()).thenReturn(true)

        val emittedStates = mutableListOf<BondState>()

        // When
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bondManager.bondDevice(mockDevice).collect { state ->
                emittedStates.add(state)
            }
        }

        verify(mockContext).registerReceiver(
            receiverCaptor.capture(),
            any(IntentFilter::class.java)
        )

        val capturedReceiver = receiverCaptor.value

        // Simulate broadcast with null device
        val nullDeviceIntent = mock(Intent::class.java)
        `when`(nullDeviceIntent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE))
            .thenReturn(null)

        capturedReceiver.onReceive(mockContext, nullDeviceIntent)

        // Then: no state should be emitted
        assertTrue(emittedStates.isEmpty())

        job.cancel()
    }

    // Note: Test for IntentFilter.hasAction() removed as it requires Android runtime

    // ==================== Helper Methods ====================

    @Suppress("DEPRECATION")
    private fun createBondStateIntent(bondState: Int): Intent {
        val intent = mock(Intent::class.java)
        `when`(intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE))
            .thenReturn(mockDevice)
        `when`(intent.getIntExtra(eq(BluetoothDevice.EXTRA_BOND_STATE), anyInt()))
            .thenReturn(bondState)
        return intent
    }
}
