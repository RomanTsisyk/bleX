# BLE-X

**BLE-X** is a Bluetooth Low Energy (BLE) communication library for Android, designed to provide a structured and reliable way to interact with BLE devices.

> ⚠ **This project is actively in development. Features may change, and breaking updates are possible.**

## Features

- 📡 **Centralized BluetoothGatt management** – Ensures better control and maintainability.
- 🔄 **Optimized MTU & PHY handling** – More efficient communication with BLE devices.
- 🔗 **Improved characteristic read/write operations** – Streamlined API for interaction.
- 🛠 **Enhanced callback management** – Prevents memory leaks and ensures proper cleanup.

## Installation

_Coming soon..._

## Usage

### 1️⃣ **Initialize Connection**
```kotlin
val bleConnection = BleConnection(device)
bleConnection.connect()
```

### 2️⃣ **Read Characteristic**
```kotlin
val data: ByteArray = bleConnection.readCharacteristic(characteristic)
```

### 3️⃣ **Write Characteristic**
```kotlin
bleConnection.writeCharacteristic(characteristic, byteArrayOf(0x01, 0x02))
```

### 4️⃣ **Request MTU**
```kotlin
bleConnection.requestMtu(512)
```

## Roadmap

- [ ] Complete API documentation
- [ ] Improve error handling and reconnection strategies
- [ ] Add unit tests and sample applications
- [ ] Optimize performance for large-scale BLE operations

## Contributing

This project is in active development, and contributions are welcome! Feel free to open issues or submit pull requests.

---

🔧 **Maintainer:** [Roman Tsisyk](https://github.com/RomanTsisyk)  
📌 **License:** MIT  
