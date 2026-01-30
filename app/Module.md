# Module BLE-X

BLE-X is a Bluetooth Low Energy (BLE) communication library for Android, designed to provide a structured and reliable way to interact with BLE devices.

## Features

- Centralized BluetoothGatt management
- Optimized MTU and PHY handling
- Improved characteristic read/write operations
- Enhanced callback management
- Coroutines support with Kotlin Flow
- Lifecycle-aware connections
- Thread-safe operations
- Exponential backoff reconnection

## Quick Start

Initialize the BLE Manager:

```kotlin
val bleManager = BleManager.getInstance(context)
```

Scan for Devices:

```kotlin
bleManager.scanDevices().collect { device ->
    println("Found: ${device.name}")
}
```

Connect to a Device:

```kotlin
val connection = bleManager.connect(device)
    .bindToLifecycle(this)
```

Read/Write Characteristics:

```kotlin
val data = connection.readCharacteristic(serviceUuid, charUuid)
connection.writeCharacteristic(serviceUuid, charUuid, byteArrayOf(0x01))
```

## Requirements

- Android SDK 26+ (Android 8.0 Oreo)
- Kotlin 1.9+
