# BLE-X

[![CI](https://github.com/RomanTsisyk/bleX/workflows/CI/badge.svg)](https://github.com/RomanTsisyk/bleX/actions)
[![](https://jitpack.io/v/RomanTsisyk/bleX.svg)](https://jitpack.io/#RomanTsisyk/bleX)
[![License](https://img.shields.io/github/license/RomanTsisyk/bleX)](LICENSE)

A lightweight, **Kotlin-first** BLE library using **Coroutines** and **Flow**.

## Features

- **Kotlin Flow API** — Reactive scanning and state observation
- **Lifecycle-aware** — Auto-disconnect on Activity/Fragment destroy
- **Thread-safe** — ConcurrentHashMap for all handlers
- **Smart reconnection** — Exponential backoff with jitter
- **Long write support** — Automatic chunking for >MTU data
- **Result wrapper** — Type-safe error handling with `BleResult<T>`
- **Configurable logging** — Enable/disable, set log levels
- **Service caching** — Avoid re-discovery on reconnection

## Installation

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        // ...
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.RomanTsisyk:bleX:v0.9.0-beta")
}
```

## Quick Start

### Initialize

```kotlin
val bleManager = BleManager.getInstance(context)
```

### Scan for devices

```kotlin
lifecycleScope.launch {
    bleManager.scanDevices()
        .take(10)
        .collect { device ->
            println("Found: ${device.name} - ${device.address}")
        }
}
```

### Connect with lifecycle awareness

```kotlin
val connection = bleManager.connect(device)
    .bindToLifecycle(this) // Auto-disconnect on destroy

// Observe connection state
connection.connectionStateFlow.collect { state ->
    when (state) {
        is ConnectionState.Ready -> println("Connected!")
        is ConnectionState.Error -> println("Error: ${state.message}")
        else -> {}
    }
}
```

### Read/Write characteristics

```kotlin
// Simple API
val data = connection.readCharacteristic(serviceUuid, charUuid)
val success = connection.writeCharacteristic(serviceUuid, charUuid, byteArrayOf(0x01))

// Result-based API (recommended)
connection.readCharacteristicResult(serviceUuid, charUuid)
    .onSuccess { data -> processData(data) }
    .onFailure { error -> showError(error.message) }
```

### Long write (>MTU)

```kotlin
val largeData = ByteArray(512) // Larger than MTU
connection.writeLongCharacteristic(serviceUuid, charUuid, largeData)
```

### Notifications

```kotlin
connection.enableNotifications(serviceUuid, charUuid) { value ->
    println("Received: ${value.toHexString()}")
}
```

### Configure logging

```kotlin
// Set log level
BleLog.setLevel(LogLevel.WARN)

// Disable logging completely
BleLog.disable()
```

## Requirements

- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Kotlin:** 1.9+

## Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

## Documentation

- [API Documentation](https://romantsisyk.github.io/bleX/) (Dokka)
- [Sample Code](SAMPLE.md)

## License

```
MIT License

Copyright (c) 2024 Roman Tsisyk

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
