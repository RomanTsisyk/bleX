package io.github.romantsisyk.blex.sample

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romantsisyk.blex.BleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Fragment demonstrating BLE device scanning with BLE-X library.
 *
 * This fragment shows the recommended pattern for scanning BLE devices
 * in a Fragment-based architecture. It handles:
 * - Permission requests
 * - Starting/stopping scans
 * - Displaying discovered devices
 * - Communicating selection to parent Activity/Fragment
 *
 * ## Usage
 *
 * ```kotlin
 * // In your Activity or parent Fragment:
 * class MyActivity : AppCompatActivity(), ScanFragment.OnDeviceSelectedListener {
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)
 *
 *         if (savedInstanceState == null) {
 *             supportFragmentManager.beginTransaction()
 *                 .replace(R.id.container, ScanFragment.newInstance())
 *                 .commit()
 *         }
 *     }
 *
 *     override fun onDeviceSelected(device: BluetoothDevice) {
 *         // Navigate to DeviceFragment or handle connection
 *         supportFragmentManager.beginTransaction()
 *             .replace(R.id.container, DeviceFragment.newInstance(device.address))
 *             .addToBackStack(null)
 *             .commit()
 *     }
 * }
 * ```
 *
 * ## Lifecycle Awareness
 *
 * The scan automatically stops when the Fragment is stopped (using lifecycleScope).
 * This prevents battery drain and ensures proper resource cleanup.
 *
 * @see DeviceFragment
 * @see BleManager
 */
class ScanFragment : Fragment() {

    companion object {
        private const val TAG = "BleX-ScanFragment"

        /**
         * Creates a new instance of ScanFragment.
         *
         * @return A new ScanFragment instance.
         */
        fun newInstance(): ScanFragment {
            return ScanFragment()
        }
    }

    /**
     * Interface for communicating device selection to the parent.
     */
    interface OnDeviceSelectedListener {
        /**
         * Called when a user selects a device from the scan results.
         *
         * @param device The selected BluetoothDevice.
         */
        fun onDeviceSelected(device: BluetoothDevice)
    }

    private var deviceSelectedListener: OnDeviceSelectedListener? = null

    private lateinit var bleManager: BleManager
    private var scanJob: Job? = null
    private var isScanning = false

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: ScanResultAdapter

    // UI Components
    private lateinit var statusTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView

    /**
     * Permission launcher using the new Activity Result API.
     *
     * This is the recommended way to request permissions in modern Android development.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All BLE permissions granted")
            updateStatus("Ready to scan")
            // Automatically start scanning after permissions are granted
            startScanning()
        } else {
            Log.w(TAG, "Some BLE permissions were denied")
            updateStatus("Permissions required for scanning")
            Toast.makeText(
                requireContext(),
                "BLE permissions are required to scan for devices",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // In production, use: inflater.inflate(R.layout.fragment_scan, container, false)
        return createProgrammaticLayout()
    }

    /**
     * Creates a programmatic layout for demonstration.
     * In production, define this in res/layout/fragment_scan.xml
     */
    private fun createProgrammaticLayout(): View {
        val rootLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        rootLayout.addView(TextView(requireContext()).apply {
            text = "Scan for BLE Devices"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        // Status text
        statusTextView = TextView(requireContext()).apply {
            text = "Tap Scan to find nearby devices"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(statusTextView)

        // Progress bar
        progressBar = ProgressBar(
            requireContext(),
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        rootLayout.addView(progressBar)

        // Scan button
        scanButton = Button(requireContext()).apply {
            text = "Scan"
        }
        rootLayout.addView(scanButton)

        // RecyclerView for devices
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f // Take remaining space
            )
        }
        rootLayout.addView(recyclerView)

        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize BleManager
        bleManager = BleManager.getInstance(requireContext())

        // Setup RecyclerView
        deviceAdapter = ScanResultAdapter(discoveredDevices) { device ->
            onDeviceClicked(device)
        }
        recyclerView.adapter = deviceAdapter

        // Setup scan button
        scanButton.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                checkPermissionsAndScan()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Try to attach listener to parent
        deviceSelectedListener = when {
            parentFragment is OnDeviceSelectedListener -> parentFragment as OnDeviceSelectedListener
            activity is OnDeviceSelectedListener -> activity as OnDeviceSelectedListener
            else -> null
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop scanning when fragment is stopped
        stopScanning()
        deviceSelectedListener = null
    }

    /**
     * Checks for required permissions and starts scanning if granted.
     */
    private fun checkPermissionsAndScan() {
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(),
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startScanning()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    /**
     * Returns the list of permissions required for BLE scanning.
     */
    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Starts scanning for BLE devices.
     *
     * The scan is launched in the Fragment's lifecycleScope, which automatically
     * cancels the coroutine when the Fragment is stopped.
     */
    private fun startScanning() {
        if (isScanning) return

        // Clear previous results
        discoveredDevices.clear()
        deviceAdapter.notifyDataSetChanged()

        isScanning = true
        scanButton.text = "Stop"
        progressBar.visibility = View.VISIBLE
        updateStatus("Scanning...")

        // Start scan using lifecycleScope - automatically cancelled when fragment stops
        scanJob = bleManager.scanDevices()
            .catch { e ->
                Log.e(TAG, "Scan error: ${e.message}", e)
                activity?.runOnUiThread {
                    updateStatus("Scan error: ${e.message}")
                    stopScanning()
                }
            }
            .onEach { device ->
                if (discoveredDevices.none { it.address == device.address }) {
                    discoveredDevices.add(device)
                    activity?.runOnUiThread {
                        deviceAdapter.notifyItemInserted(discoveredDevices.size - 1)
                        updateStatus("Found ${discoveredDevices.size} device(s)")
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        Log.d(TAG, "Started BLE scan")
    }

    /**
     * Stops the ongoing BLE scan.
     */
    private fun stopScanning() {
        if (!isScanning) return

        scanJob?.cancel()
        scanJob = null
        isScanning = false
        scanButton.text = "Scan"
        progressBar.visibility = View.GONE
        updateStatus("Found ${discoveredDevices.size} device(s)")

        Log.d(TAG, "Stopped BLE scan")
    }

    /**
     * Called when a device is clicked in the list.
     */
    private fun onDeviceClicked(device: BluetoothDevice) {
        stopScanning()

        // Notify parent about the selection
        deviceSelectedListener?.onDeviceSelected(device)
            ?: Log.w(TAG, "No OnDeviceSelectedListener attached")
    }

    /**
     * Updates the status text.
     */
    private fun updateStatus(message: String) {
        statusTextView.text = message
    }
}
