package io.github.romantsisyk.blex.models

data class BleCharacteristic(
    val uuid: String,
    val properties: Int,
    val permissions: Int
)