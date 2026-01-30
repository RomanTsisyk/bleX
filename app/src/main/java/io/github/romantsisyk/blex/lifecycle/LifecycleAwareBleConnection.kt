package io.github.romantsisyk.blex.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.romantsisyk.blex.connection.BleConnection
import io.github.romantsisyk.blex.models.BondState
import io.github.romantsisyk.blex.models.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * A lifecycle-aware wrapper for [BleConnection] that automatically manages the BLE connection
 * lifecycle in response to Android lifecycle events.
 *
 * This class implements [DefaultLifecycleObserver] to observe the lifecycle of an Android component
 * (such as an Activity or Fragment) and automatically disconnect from the BLE device when the
 * lifecycle owner is stopped, and close the connection when the lifecycle owner is destroyed.
 *
 * This helps prevent resource leaks and ensures that BLE connections are properly cleaned up
 * when the associated UI component is no longer active.
 *
 * ## Lifecycle Behavior
 *
 * - **onStop**: Automatically calls [disconnect] to gracefully disconnect from the BLE device
 *   while keeping resources allocated for potential reconnection.
 * - **onDestroy**: Automatically calls [close] to fully release all resources and removes
 *   this observer from the lifecycle.
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     private lateinit var connection: LifecycleAwareBleConnection
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         val bleConnection = BleConnection(applicationContext, bluetoothDevice)
 *         connection = bleConnection.bindToLifecycle(this)
 *
 *         // Use the connection - it will automatically be managed
 *         connection.connect()
 *
 *         // Observe connection state
 *         lifecycleScope.launch {
 *             connection.connectionStateFlow.collect { state ->
 *                 // Handle state changes
 *             }
 *         }
 *     }
 *     // No need to manually disconnect or close - handled automatically
 * }
 * ```
 *
 * ## Alternative Usage with Extension Function
 *
 * ```kotlin
 * val connection = BleConnection(context, device).bindToLifecycle(lifecycleOwner)
 * connection.connect()
 * ```
 *
 * @param connection The underlying [BleConnection] instance to wrap.
 * @param lifecycleOwner The [LifecycleOwner] whose lifecycle will control this connection.
 *                       Typically an Activity, Fragment, or other lifecycle-aware component.
 *
 * @see BleConnection
 * @see BleConnection.bindToLifecycle
 * @see DefaultLifecycleObserver
 */
class LifecycleAwareBleConnection(
    private val connection: BleConnection,
    lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * A [StateFlow] that emits the current connection state of the BLE device.
     *
     * This delegates to the underlying [BleConnection.connectionStateFlow].
     * Collectors will receive updates whenever the connection state changes.
     *
     * @see ConnectionState
     */
    val connectionStateFlow: StateFlow<ConnectionState>
        get() = connection.connectionStateFlow

    /**
     * Called when the [LifecycleOwner] is stopped.
     *
     * This automatically disconnects from the BLE device to conserve resources
     * while the app is in the background. The underlying [BluetoothGatt] resources
     * are retained, allowing for reconnection when the lifecycle resumes.
     *
     * @param owner The [LifecycleOwner] whose state changed.
     */
    override fun onStop(owner: LifecycleOwner) {
        connection.disconnect()
    }

    /**
     * Called when the [LifecycleOwner] is destroyed.
     *
     * This fully closes the BLE connection and releases all associated resources.
     * The observer is also removed from the lifecycle to prevent memory leaks.
     *
     * After this method is called, the connection should not be used again.
     * Create a new [LifecycleAwareBleConnection] if you need to reconnect.
     *
     * @param owner The [LifecycleOwner] whose state changed.
     */
    override fun onDestroy(owner: LifecycleOwner) {
        connection.close()
        owner.lifecycle.removeObserver(this)
    }

    /**
     * Initiates a connection to the BLE device.
     *
     * Delegates to [BleConnection.connect]. The connection state can be observed
     * through [connectionStateFlow].
     *
     * @see BleConnection.connect
     */
    fun connect() {
        connection.connect()
    }

    /**
     * Disconnects from the BLE device while keeping resources allocated.
     *
     * Delegates to [BleConnection.disconnect]. Use this for temporary disconnection
     * where reconnection is expected. For permanent disconnection, use [close].
     *
     * @see BleConnection.disconnect
     */
    fun disconnect() {
        connection.disconnect()
    }

    /**
     * Closes the BLE connection and releases all associated resources.
     *
     * Delegates to [BleConnection.close]. After calling this method, the connection
     * should not be reused.
     *
     * Note: This is automatically called when the lifecycle owner is destroyed,
     * so manual invocation is typically not necessary when using lifecycle-aware management.
     *
     * @see BleConnection.close
     */
    fun close() {
        connection.close()
    }

    /**
     * Initiates the bonding (pairing) process with the BLE device.
     *
     * Delegates to [BleConnection.bond].
     *
     * @return A [Flow] emitting [BondState] updates during the bonding process.
     *
     * @see BleConnection.bond
     * @see BondState
     */
    fun bond(): Flow<BondState> {
        return connection.bond()
    }

    /**
     * Reads the value of a characteristic from the connected BLE device.
     *
     * Delegates to [BleConnection.readCharacteristic].
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to read.
     * @return The characteristic value as a [ByteArray] if successful, or `null` on failure.
     *
     * @see BleConnection.readCharacteristic
     */
    suspend fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): ByteArray? {
        return connection.readCharacteristic(serviceUuid, characteristicUuid)
    }

    /**
     * Writes a value to a characteristic on the connected BLE device.
     *
     * Delegates to [BleConnection.writeCharacteristic].
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to write to.
     * @param value The [ByteArray] value to write.
     * @return `true` if the write was successful, `false` otherwise.
     *
     * @see BleConnection.writeCharacteristic
     */
    suspend fun writeCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        value: ByteArray
    ): Boolean {
        return connection.writeCharacteristic(serviceUuid, characteristicUuid, value)
    }

    /**
     * Enables notifications for a characteristic and registers a callback for received values.
     *
     * Delegates to [BleConnection.enableNotifications].
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to enable notifications for.
     * @param callback The callback function invoked with the [ByteArray] value each time
     *                 a notification is received.
     *
     * @see BleConnection.enableNotifications
     */
    fun enableNotifications(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        callback: (ByteArray) -> Unit
    ) {
        connection.enableNotifications(serviceUuid, characteristicUuid, callback)
    }

    /**
     * Disables notifications for a characteristic.
     *
     * Delegates to [BleConnection.disableNotifications].
     *
     * @param serviceUuid The [UUID] of the GATT service containing the characteristic.
     * @param characteristicUuid The [UUID] of the characteristic to disable notifications for.
     *
     * @see BleConnection.disableNotifications
     */
    fun disableNotifications(serviceUuid: UUID, characteristicUuid: UUID) {
        connection.disableNotifications(serviceUuid, characteristicUuid)
    }

    /**
     * Returns the underlying [BleConnection] instance.
     *
     * Use this when you need access to the raw connection for advanced operations
     * not exposed through this wrapper.
     *
     * @return The underlying [BleConnection] instance.
     */
    fun unwrap(): BleConnection = connection
}

/**
 * Binds this [BleConnection] to a [LifecycleOwner] for automatic lifecycle management.
 *
 * This extension function creates a [LifecycleAwareBleConnection] that wraps this connection
 * and automatically manages it based on the provided lifecycle owner's state:
 *
 * - When the lifecycle owner is stopped (e.g., Activity goes to background), the connection
 *   is automatically disconnected.
 * - When the lifecycle owner is destroyed (e.g., Activity is finished), the connection
 *   is automatically closed and all resources are released.
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         val connection = BleConnection(applicationContext, device)
 *             .bindToLifecycle(this)
 *
 *         connection.connect()
 *         // Connection will be automatically managed
 *     }
 * }
 * ```
 *
 * @param lifecycleOwner The [LifecycleOwner] to bind this connection to.
 *                       Typically an Activity, Fragment, or other lifecycle-aware component.
 * @return A [LifecycleAwareBleConnection] that wraps this connection with lifecycle awareness.
 *
 * @see LifecycleAwareBleConnection
 */
fun BleConnection.bindToLifecycle(lifecycleOwner: LifecycleOwner): LifecycleAwareBleConnection {
    return LifecycleAwareBleConnection(this, lifecycleOwner)
}
