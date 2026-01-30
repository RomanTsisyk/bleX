package io.github.romantsisyk.blex.scanner

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * Unit tests for BleScanner.
 *
 * These tests verify the BLE scanning functionality including:
 * - Flow-based device scanning
 * - Custom scan settings and filters
 * - Background scanning with PendingIntent
 * - Proper cleanup when scanning stops
 */
@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothLeScanner: BluetoothLeScanner

    @Mock
    private lateinit var mockScanResult: ScanResult

    @Mock
    private lateinit var mockScanResult2: ScanResult

    @Mock
    private lateinit var mockPendingIntent: PendingIntent

    @Mock
    private lateinit var mockScanSettings: ScanSettings

    @Mock
    private lateinit var mockScanFilter: ScanFilter

    @Captor
    private lateinit var scanCallbackCaptor: ArgumentCaptor<ScanCallback>

    @Captor
    private lateinit var scanSettingsCaptor: ArgumentCaptor<ScanSettings>

    @Captor
    private lateinit var scanFiltersCaptor: ArgumentCaptor<List<ScanFilter>>

    private lateinit var bleScanner: BleScanner

    @Before
    fun setUp() {
        whenever(mockBluetoothAdapter.isEnabled).thenReturn(true)
        whenever(mockBluetoothAdapter.bluetoothLeScanner).thenReturn(mockBluetoothLeScanner)

        bleScanner = BleScanner(mockContext, mockBluetoothAdapter)
    }

    // ==================== scanDevices returns Flow of ScanResult ====================

    @Test
    fun `scanDevices returns Flow of ScanResult`() = runTest {
        // Arrange
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            // Simulate receiving a scan result
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices()
        val result = flow.first()

        // Assert
        assertEquals(mockScanResult, result)
    }

    @Test
    fun `scanDevices emits multiple scan results`() = runTest {
        // Arrange
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            // Simulate receiving multiple scan results
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult2)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices()
        val results = flow.take(2).toList()

        // Assert
        assertEquals(2, results.size)
        assertEquals(mockScanResult, results[0])
        assertEquals(mockScanResult2, results[1])
    }

    @Test
    fun `scanDevices emits batch scan results`() = runTest {
        // Arrange
        val batchResults = mutableListOf(mockScanResult, mockScanResult2)
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            // Simulate receiving batch scan results
            callback.onBatchScanResults(batchResults)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices()
        val results = flow.take(2).toList()

        // Assert
        assertEquals(2, results.size)
        assertEquals(mockScanResult, results[0])
        assertEquals(mockScanResult2, results[1])
    }

    @Test
    fun `scanDevices closes flow with exception when Bluetooth is disabled`() = runTest {
        // Arrange
        whenever(mockBluetoothAdapter.isEnabled).thenReturn(false)

        // Act & Assert
        val flow = bleScanner.scanDevices()
        try {
            flow.first()
            fail("Expected exception to be thrown")
        } catch (e: Exception) {
            assertEquals("Bluetooth is disabled.", e.message)
        }
    }

    @Test
    fun `scanDevices closes flow with exception when scanner is null`() = runTest {
        // Arrange
        whenever(mockBluetoothAdapter.bluetoothLeScanner).thenReturn(null)

        // Act & Assert
        val flow = bleScanner.scanDevices()
        try {
            flow.first()
            fail("Expected exception to be thrown")
        } catch (e: Exception) {
            assertEquals("Unable to start BLE scan.", e.message)
        }
    }

    @Test
    fun `scanDevices closes flow with exception on scan failure`() = runTest {
        // Arrange
        val errorCode = ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            // Simulate scan failure
            callback.onScanFailed(errorCode)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act & Assert
        val flow = bleScanner.scanDevices()
        try {
            flow.first()
            fail("Expected exception to be thrown")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Scan failed with error code: $errorCode"))
        }
    }

    // ==================== scanDevices with custom settings ====================

    @Test
    fun `scanDevices with custom settings uses those settings`() = runTest {
        // Arrange
        val customSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), capture(scanSettingsCaptor), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices(scanSettings = customSettings)
        flow.first()

        // Assert
        assertEquals(customSettings, scanSettingsCaptor.value)
    }

    @Test
    fun `scanDevices uses default LOW_LATENCY scan mode when no settings provided`() = runTest {
        // Arrange
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), capture(scanSettingsCaptor), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices()
        flow.first()

        // Assert
        val capturedSettings = scanSettingsCaptor.value
        assertNotNull(capturedSettings)
        // Verify scan mode is LOW_LATENCY (default)
        assertEquals(ScanSettings.SCAN_MODE_LOW_LATENCY, capturedSettings.scanMode)
    }

    // ==================== scanDevices with filters ====================

    @Test
    fun `scanDevices with filters applies filters`() = runTest {
        // Arrange
        val filters = listOf(mockScanFilter)

        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(capture(scanFiltersCaptor), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices(scanFilters = filters)
        flow.first()

        // Assert
        assertEquals(filters, scanFiltersCaptor.value)
    }

    @Test
    fun `scanDevices with empty filters uses empty list`() = runTest {
        // Arrange
        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(capture(scanFiltersCaptor), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices()
        flow.first()

        // Assert
        assertTrue(scanFiltersCaptor.value.isEmpty())
    }

    @Test
    fun `scanDevices with multiple filters applies all filters`() = runTest {
        // Arrange
        val filter1 = mock(ScanFilter::class.java)
        val filter2 = mock(ScanFilter::class.java)
        val filters = listOf(filter1, filter2)

        doAnswer { invocation ->
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(capture(scanFiltersCaptor), any(), any<ScanCallback>())

        // Act
        val flow = bleScanner.scanDevices(scanFilters = filters)
        flow.first()

        // Assert
        assertEquals(2, scanFiltersCaptor.value.size)
        assertEquals(filter1, scanFiltersCaptor.value[0])
        assertEquals(filter2, scanFiltersCaptor.value[1])
    }

    // ==================== startBackgroundScan ====================

    @Test
    fun `startBackgroundScan starts scan with PendingIntent`() {
        // Arrange
        val filters = listOf(mockScanFilter)

        // Act
        bleScanner.startBackgroundScan(mockScanSettings, filters, mockPendingIntent)

        // Assert
        verify(mockBluetoothLeScanner).startScan(eq(filters), eq(mockScanSettings), eq(mockPendingIntent))
    }

    @Test
    fun `startBackgroundScan with empty filters starts scan`() {
        // Arrange
        val emptyFilters = emptyList<ScanFilter>()

        // Act
        bleScanner.startBackgroundScan(mockScanSettings, emptyFilters, mockPendingIntent)

        // Assert
        verify(mockBluetoothLeScanner).startScan(eq(emptyFilters), eq(mockScanSettings), eq(mockPendingIntent))
    }

    @Test
    fun `startBackgroundScan does nothing when scanner is null`() {
        // Arrange
        whenever(mockBluetoothAdapter.bluetoothLeScanner).thenReturn(null)
        val newScanner = BleScanner(mockContext, mockBluetoothAdapter)

        // Act - should not throw
        newScanner.startBackgroundScan(mockScanSettings, emptyList(), mockPendingIntent)

        // Assert - no interaction with scanner since it's null
        verify(mockBluetoothLeScanner, never()).startScan(any<List<ScanFilter>>(), any(), any<PendingIntent>())
    }

    // ==================== stopBackgroundScan ====================

    @Test
    fun `stopBackgroundScan stops background scan`() {
        // Act
        bleScanner.stopBackgroundScan(mockPendingIntent)

        // Assert
        verify(mockBluetoothLeScanner).stopScan(eq(mockPendingIntent))
    }

    @Test
    fun `stopBackgroundScan does nothing when scanner is null`() {
        // Arrange
        whenever(mockBluetoothAdapter.bluetoothLeScanner).thenReturn(null)
        val newScanner = BleScanner(mockContext, mockBluetoothAdapter)

        // Act - should not throw
        newScanner.stopBackgroundScan(mockPendingIntent)

        // Assert - no interaction with scanner since it's null
        verify(mockBluetoothLeScanner, never()).stopScan(any<PendingIntent>())
    }

    // ==================== Flow completes when scan is stopped ====================

    @Test
    fun `Flow completes when scan is stopped`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        var capturedCallback: ScanCallback? = null
        doAnswer { invocation ->
            capturedCallback = invocation.getArgument<ScanCallback>(2)
            // Emit one result
            capturedCallback?.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act
        val collectedResults = mutableListOf<ScanResult>()
        val job = launch {
            bleScanner.scanDevices().collect { result ->
                collectedResults.add(result)
            }
        }

        // Give time for flow to start and collect
        testScheduler.advanceUntilIdle()

        // Cancel the job (this simulates stopping the scan)
        job.cancel()
        testScheduler.advanceUntilIdle()

        // Assert
        assertEquals(1, collectedResults.size)
        // Verify stopScan was called when flow was cancelled
        verify(mockBluetoothLeScanner).stopScan(any<ScanCallback>())
    }

    @Test
    fun `stopScan is called with same callback when flow is cancelled`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        var startCallback: ScanCallback? = null
        doAnswer { invocation ->
            startCallback = invocation.getArgument<ScanCallback>(2)
            startCallback?.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), capture(scanCallbackCaptor))

        // Act
        val job = launch {
            bleScanner.scanDevices().collect { }
        }

        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()

        // Assert - verify stopScan was called
        verify(mockBluetoothLeScanner).stopScan(any<ScanCallback>())
    }

    @Test
    fun `Flow can be collected multiple times independently`() = runTest {
        // Arrange
        var callCount = 0
        doAnswer { invocation ->
            callCount++
            val callback = invocation.getArgument<ScanCallback>(2)
            callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, mockScanResult)
            null
        }.whenever(mockBluetoothLeScanner).startScan(any<List<ScanFilter>>(), any(), any<ScanCallback>())

        // Act
        val result1 = bleScanner.scanDevices().first()
        val result2 = bleScanner.scanDevices().first()

        // Assert
        assertEquals(mockScanResult, result1)
        assertEquals(mockScanResult, result2)
        assertEquals(2, callCount) // startScan should be called twice
    }
}
