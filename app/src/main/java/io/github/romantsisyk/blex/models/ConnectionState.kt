package io.github.romantsisyk.blex.models

/**
 * Represents the various states of a Bluetooth Low Energy (BLE) connection.
 *
 * This sealed class provides a type-safe way to handle different connection states
 * during the BLE connection lifecycle. Each state represents a distinct phase
 * in the connection process, from initial disconnection through to a fully
 * operational connection.
 *
 * ## Usage Example
 * ```kotlin
 * when (connectionState) {
 *     is ConnectionState.Disconnected -> handleDisconnected()
 *     is ConnectionState.Connecting -> showConnectingIndicator()
 *     is ConnectionState.Connected -> handleConnected()
 *     is ConnectionState.DiscoveringServices -> showDiscoveringServices()
 *     is ConnectionState.Ready -> enableOperations()
 *     is ConnectionState.Error -> showError(connectionState.message)
 * }
 * ```
 *
 * @see BleService
 * @see BleCharacteristic
 */
sealed class ConnectionState {

    /**
     * Represents a disconnected state where no BLE connection exists.
     *
     * This is the initial state before any connection attempt, or the state
     * after a connection has been terminated (either intentionally or due to
     * connection loss).
     */
    data object Disconnected : ConnectionState()

    /**
     * Represents an ongoing connection attempt to a BLE device.
     *
     * The connection process has been initiated but not yet completed.
     * This state typically occurs after calling `connect()` and before
     * receiving a connection callback from the system.
     */
    data object Connecting : ConnectionState()

    /**
     * Represents a successfully established BLE connection.
     *
     * At this stage, the GATT connection is established, but service discovery
     * has not yet been performed. The connection exists at the transport layer,
     * but the device's services and characteristics are not yet known.
     */
    data object Connected : ConnectionState()

    /**
     * Represents the service discovery phase of the connection.
     *
     * After establishing a connection, the BLE stack is actively querying
     * the remote device to discover its available services and characteristics.
     * This phase must complete before any read/write operations can be performed.
     */
    data object DiscoveringServices : ConnectionState()

    /**
     * Represents a fully operational BLE connection.
     *
     * The connection is established and service discovery has completed successfully.
     * At this point, all services and characteristics are known, and the connection
     * is ready for read, write, and notification operations.
     *
     * This is the final successful state in the connection lifecycle.
     */
    data object Ready : ConnectionState()

    /**
     * Represents an error state that occurred during the connection process.
     *
     * This state indicates that something went wrong during connection establishment,
     * service discovery, or during an active connection. The connection should be
     * considered invalid when in this state.
     *
     * @property message A human-readable description of the error that occurred.
     *                   This message can be displayed to users or logged for debugging.
     */
    data class Error(val message: String) : ConnectionState()
}
