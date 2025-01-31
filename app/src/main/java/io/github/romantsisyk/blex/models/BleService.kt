package io.github.romantsisyk.blex.models

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object DiscoveringServices : ConnectionState()
    data object Ready : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
