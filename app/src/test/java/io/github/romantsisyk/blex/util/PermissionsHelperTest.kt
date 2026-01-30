package io.github.romantsisyk.blex.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for PermissionsHelper.
 *
 * These tests verify that permission checks correctly handle:
 * - API 31+ (Android S) requiring new Bluetooth permissions
 * - Pre-API 31 devices using legacy permissions or no permission checks
 * - Permission granted and denied scenarios
 *
 * Note: SDK version mocking uses reflection which requires --add-opens JVM args
 * for Java 17+. Alternatively, consider using Robolectric with @Config(sdk = [X])
 * for more robust SDK version testing.
 */
@RunWith(MockitoJUnitRunner::class)
class PermissionsHelperTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var contextCompatMock: MockedStatic<ContextCompat>
    private var originalSdkInt: Int = 0

    @Before
    fun setUp() {
        contextCompatMock = mockStatic(ContextCompat::class.java)
        originalSdkInt = Build.VERSION.SDK_INT
    }

    @After
    fun tearDown() {
        contextCompatMock.close()
        // Restore original SDK_INT
        setSdkInt(originalSdkInt)
    }

    /**
     * Helper function to set the SDK_INT value using reflection.
     * This is necessary because Build.VERSION.SDK_INT is a static final field.
     *
     * For Java 17+, run tests with:
     * --add-opens java.base/java.lang.reflect=ALL-UNNAMED
     */
    private fun setSdkInt(sdkInt: Int) {
        try {
            val field = Build.VERSION::class.java.getField("SDK_INT")
            field.isAccessible = true

            // For Java 9+ we need to handle final fields differently
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
            unsafeField.isAccessible = true
            val unsafe = unsafeField.get(null)

            val staticFieldBase = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java)
            val staticFieldOffset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
            val putInt = unsafeClass.getMethod("putInt", Any::class.java, Long::class.java, Int::class.java)

            val base = staticFieldBase.invoke(unsafe, field)
            val offset = staticFieldOffset.invoke(unsafe, field) as Long
            putInt.invoke(unsafe, base, offset, sdkInt)
        } catch (e: Exception) {
            // Fallback: try the simpler reflection approach for older JVMs
            try {
                val field = Build.VERSION::class.java.getField("SDK_INT")
                field.isAccessible = true

                val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and java.lang.reflect.Modifier.FINAL.inv())

                field.setInt(null, sdkInt)
            } catch (fallbackError: Exception) {
                // If all reflection fails, log and continue - tests may not work correctly
                // but will at least use the current SDK_INT
                System.err.println("Warning: Could not set SDK_INT via reflection: ${fallbackError.message}")
            }
        }
    }

    // ==================== hasScanPermissions Tests ====================

    @Test
    fun `hasScanPermissions returns true when BLUETOOTH_SCAN granted on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_SCAN)
        }.thenReturn(PackageManager.PERMISSION_GRANTED)

        val result = PermissionsHelper.hasScanPermissions(mockContext)

        assertTrue("hasScanPermissions should return true when BLUETOOTH_SCAN is granted on API 31+", result)
    }

    @Test
    fun `hasScanPermissions returns true on older APIs when ACCESS_FINE_LOCATION granted`() {
        setSdkInt(Build.VERSION_CODES.R) // API 30

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.ACCESS_FINE_LOCATION)
        }.thenReturn(PackageManager.PERMISSION_GRANTED)

        val result = PermissionsHelper.hasScanPermissions(mockContext)

        assertTrue("hasScanPermissions should return true on API < 31 when ACCESS_FINE_LOCATION is granted", result)
    }

    @Test
    fun `hasScanPermissions returns false when BLUETOOTH_SCAN denied on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_SCAN)
        }.thenReturn(PackageManager.PERMISSION_DENIED)

        val result = PermissionsHelper.hasScanPermissions(mockContext)

        assertFalse("hasScanPermissions should return false when BLUETOOTH_SCAN is denied on API 31+", result)
    }

    @Test
    fun `hasScanPermissions returns false on older APIs when ACCESS_FINE_LOCATION denied`() {
        setSdkInt(Build.VERSION_CODES.R) // API 30

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.ACCESS_FINE_LOCATION)
        }.thenReturn(PackageManager.PERMISSION_DENIED)

        val result = PermissionsHelper.hasScanPermissions(mockContext)

        assertFalse("hasScanPermissions should return false on API < 31 when ACCESS_FINE_LOCATION is denied", result)
    }

    // ==================== hasConnectPermissions Tests ====================

    @Test
    fun `hasConnectPermissions returns true when BLUETOOTH_CONNECT granted on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT)
        }.thenReturn(PackageManager.PERMISSION_GRANTED)

        val result = PermissionsHelper.hasConnectPermissions(mockContext)

        assertTrue("hasConnectPermissions should return true when BLUETOOTH_CONNECT is granted on API 31+", result)
    }

    @Test
    fun `hasConnectPermissions returns true on older APIs without permission check`() {
        setSdkInt(Build.VERSION_CODES.R) // API 30

        // No permission check should happen for connect on older APIs
        // The helper returns true when permissionBelowS is null

        val result = PermissionsHelper.hasConnectPermissions(mockContext)

        assertTrue("hasConnectPermissions should return true on API < 31 (no permission required)", result)
    }

    @Test
    fun `hasConnectPermissions returns false when BLUETOOTH_CONNECT denied on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_CONNECT)
        }.thenReturn(PackageManager.PERMISSION_DENIED)

        val result = PermissionsHelper.hasConnectPermissions(mockContext)

        assertFalse("hasConnectPermissions should return false when BLUETOOTH_CONNECT is denied on API 31+", result)
    }

    // ==================== hasAdvertisePermissions Tests ====================

    @Test
    fun `hasAdvertisePermissions returns true when BLUETOOTH_ADVERTISE granted on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_ADVERTISE)
        }.thenReturn(PackageManager.PERMISSION_GRANTED)

        val result = PermissionsHelper.hasAdvertisePermissions(mockContext)

        assertTrue("hasAdvertisePermissions should return true when BLUETOOTH_ADVERTISE is granted on API 31+", result)
    }

    @Test
    fun `hasAdvertisePermissions returns true on older APIs without permission check`() {
        setSdkInt(Build.VERSION_CODES.R) // API 30

        // No permission check should happen for advertise on older APIs
        // The helper returns true when permissionBelowS is null

        val result = PermissionsHelper.hasAdvertisePermissions(mockContext)

        assertTrue("hasAdvertisePermissions should return true on API < 31 (no permission required)", result)
    }

    @Test
    fun `hasAdvertisePermissions returns false when BLUETOOTH_ADVERTISE denied on API 31+`() {
        setSdkInt(Build.VERSION_CODES.S) // API 31

        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(mockContext, Manifest.permission.BLUETOOTH_ADVERTISE)
        }.thenReturn(PackageManager.PERMISSION_DENIED)

        val result = PermissionsHelper.hasAdvertisePermissions(mockContext)

        assertFalse("hasAdvertisePermissions should return false when BLUETOOTH_ADVERTISE is denied on API 31+", result)
    }
}
