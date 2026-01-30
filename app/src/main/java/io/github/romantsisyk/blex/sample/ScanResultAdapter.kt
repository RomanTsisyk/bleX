package io.github.romantsisyk.blex.sample

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying discovered BLE devices.
 *
 * This adapter shows a list of [BluetoothDevice] objects discovered during BLE scanning.
 * Each item displays the device name (or "Unknown Device" if not available) and
 * the device's MAC address. Clicking on an item triggers the [onDeviceClick] callback.
 *
 * ## Features
 *
 * - Displays device name and MAC address
 * - Handles unnamed devices gracefully
 * - Click listener for device selection
 * - Efficient view recycling
 *
 * ## Usage Example
 *
 * ```kotlin
 * val devices = mutableListOf<BluetoothDevice>()
 * val adapter = ScanResultAdapter(devices) { device ->
 *     // Handle device selection
 *     connectToDevice(device)
 * }
 * recyclerView.adapter = adapter
 *
 * // When new device is discovered:
 * devices.add(discoveredDevice)
 * adapter.notifyItemInserted(devices.size - 1)
 * ```
 *
 * ## Permissions Note
 *
 * Accessing [BluetoothDevice.getName] requires `BLUETOOTH_CONNECT` permission on
 * Android 12 (API 31) and above. Make sure this permission is granted before
 * using this adapter.
 *
 * @param devices The mutable list of discovered devices to display.
 * @param onDeviceClick Callback invoked when a device item is clicked.
 *
 * @see BluetoothDevice
 * @see RecyclerView.Adapter
 */
class ScanResultAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<ScanResultAdapter.DeviceViewHolder>() {

    /**
     * ViewHolder for a single device item.
     *
     * Contains text views for displaying the device name and address,
     * and handles click events on the item.
     */
    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView
        val addressTextView: TextView
        val container: View

        init {
            // If using programmatic layouts, create text views here
            // In production, use view binding: val binding = ItemDeviceBinding.bind(view)
            nameTextView = view.findViewWithTag("name") ?: TextView(view.context).apply {
                tag = "name"
            }
            addressTextView = view.findViewWithTag("address") ?: TextView(view.context).apply {
                tag = "address"
            }
            container = view
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        // Create a simple programmatic layout for each item
        // In production, use: LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        val itemView = createItemView(parent)
        return DeviceViewHolder(itemView)
    }

    /**
     * Creates a programmatic layout for a device item.
     * In production apps, replace this with an XML layout.
     */
    private fun createItemView(parent: ViewGroup): View {
        return LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // Device name
            addView(TextView(context).apply {
                tag = "name"
                textSize = 16f
                setTextColor(0xFF000000.toInt())
            })

            // Device address
            addView(TextView(context).apply {
                tag = "address"
                textSize = 12f
                setTextColor(0xFF666666.toInt())
            })

            // Add ripple effect for touch feedback
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        // Display device name (may require BLUETOOTH_CONNECT permission on Android 12+)
        // Using try-catch to gracefully handle permission issues
        val deviceName = try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device (permission denied)"
        }

        holder.nameTextView.text = deviceName
        holder.addressTextView.text = device.address

        // Handle click on the item
        holder.container.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount(): Int = devices.size

    /**
     * Clears all devices from the list and notifies the adapter.
     */
    fun clearDevices() {
        val size = devices.size
        devices.clear()
        notifyItemRangeRemoved(0, size)
    }

    /**
     * Adds a device to the list if it's not already present.
     *
     * @param device The device to add.
     * @return `true` if the device was added, `false` if it was already in the list.
     */
    fun addDeviceIfNotPresent(device: BluetoothDevice): Boolean {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
            return true
        }
        return false
    }
}
