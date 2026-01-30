# Module BLE-X

BLE-X is a Bluetooth Low Energy (BLE) communication library for Android, designed to provide a structured and reliable way to interact with BLE devices.

## Overview

BLE-X simplifies BLE operations on Android by providing:

- **Centralized BluetoothGatt management** - Ensures better control and maintainability
- **Optimized MTU & PHY handling** - More efficient communication with BLE devices
- **Improved characteristic read/write operations** - Streamlined API for interaction
- **Enhanced callback management** - Prevents memory leaks and ensures proper cleanup
- **Coroutines support** - Modern async/await style API using Kotlin Flow

## Getting Started

### Initialize the BLE Manager

```kotlin
val bleManager = BleManager.getInstance(context)
```

### Scan for Devices

```kotlin
bleManager.scanDevices().collect { device ->
    // Handle discovered BLE device
}
```

### Connect to a Device

```kotlin
val connection = bleManager.connect(device)
```

### Read/Write Characteristics

```kotlin
// Read characteristic
val data: ByteArray = connection.readCharacteristic(characteristic)

// Write characteristic
connection.writeCharacteristic(characteristic, byteArrayOf(0x01, 0x02))
```

### Request MTU

```kotlin
connection.requestMtu(512)
```

## Package Structure

| Package | Description |
|---------|-------------|
| `io.github.romantsisyk.blex` | Main entry point with BleManager |
| `io.github.romantsisyk.blex.connection` | Connection handling and state management |
| `io.github.romantsisyk.blex.scanner` | BLE device scanning functionality |
| `io.github.romantsisyk.blex.server` | BLE GATT server implementation |
| `io.github.romantsisyk.blex.models` | Data models for BLE operations |
| `io.github.romantsisyk.blex.util` | Utility classes and helpers |

## Key Classes

- [BleManager][io.github.romantsisyk.blex.BleManager] - Central manager for all BLE operations
- [BleConnection][io.github.romantsisyk.blex.connection.BleConnection] - Manages individual device connections
- [BleScanner][io.github.romantsisyk.blex.scanner.BleScanner] - Handles BLE device discovery
- [BleGattServer][io.github.romantsisyk.blex.server.BleGattServer] - GATT server implementation

## Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Kotlin 1.9+
- Required permissions:
  - `BLUETOOTH_SCAN` (Android 12+)
  - `BLUETOOTH_CONNECT` (Android 12+)
  - `ACCESS_FINE_LOCATION` (Android 11 and below)
