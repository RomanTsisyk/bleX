package io.github.romantsisyk.blex.scanner

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
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

/**
 * Unit tests for BleScanner.
 *
 * Note: Tests that require Android runtime (ScanSettings.Builder, etc.) have been
 * removed as they require Robolectric or instrumentation tests.
 * This test file contains only tests that can run with pure mocking.
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class BleScannerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothLeScanner: BluetoothLeScanner

    @Mock
    private lateinit var mockPendingIntent: PendingIntent

    @Mock
    private lateinit var mockScanSettings: ScanSettings

    @Mock
    private lateinit var mockScanFilter: ScanFilter

    private lateinit var bleScanner: BleScanner

    @Before
    fun setUp() {
        whenever(mockBluetoothAdapter.isEnabled).thenReturn(true)
        whenever(mockBluetoothAdapter.bluetoothLeScanner).thenReturn(mockBluetoothLeScanner)

        bleScanner = BleScanner(mockContext, mockBluetoothAdapter)
    }

    // ==================== startBackgroundScan Tests ====================

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

    // ==================== stopBackgroundScan Tests ====================

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

    // Note: scanDevices tests removed as they require Android runtime (ScanSettings.Builder)
}
