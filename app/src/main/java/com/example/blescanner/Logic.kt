package com.example.blescanner

import android.Manifest
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import quevedo.soares.leandro.blemadeeasy.BLE

class BLEScannerLogic {
    private var context: Context? = null
    private val scannerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Set to store already detected device addresses to prevent duplicate processing
    private val detectedDevices = mutableSetOf<String>()

    // The target ESP device name (can be partial match)
    val targetEspName = "Humble Coders"

    // BLE Made Easy library instance
    private var ble: BLE? = null
    private var isScanning = false

    // Data class to hold scan result with text information
    data class ScanResultWithText(
        val result: ScanResult?,
        val hasTextData: Boolean,
        val message: String,
        val isEspDevice: Boolean,
        val deviceAddress: String,
        val deviceName: String?
    )

    fun initialize(context: Context) {
        this.context = context

        // Initialize BLE Made Easy with the appropriate context type
        if (context is AppCompatActivity) {
            ble = BLE(activity = context)
        } else {
            Log.e("BLE", "Context must be an AppCompatActivity for BLE initialization")
        }
    }
    fun startScanning(onDeviceDetected: (ScanResultWithText) -> Unit) {
        context?.let { ctx ->
            if (isScanning) {
                // Already scanning, don't start again
                return
            }

            // Check if we have necessary permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("BLE", "BLUETOOTH_SCAN permission not granted")
                    return
                }
            } else {
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("BLE", "ACCESS_FINE_LOCATION permission not granted")
                    return
                }
            }

            isScanning = true
            Log.d("BLE", "Started BLE scan using BLE Made Easy library")

            // Use BLE Made Easy library to handle scanning
            ble?.let { ble ->
                scannerScope.launch {
                    try {
                        // Verify permissions using the library
                        val permissionsGranted = ble.verifyPermissions()
                        if (!permissionsGranted) {
                            Log.e("BLE", "Required permissions not granted")
                            isScanning = false
                            return@launch
                        }

                        // Verify Bluetooth adapter state
                        val bluetoothActive = ble.verifyBluetoothAdapterState()
                        if (!bluetoothActive) {
                            Log.e("BLE", "Bluetooth not active")
                            isScanning = false
                            return@launch
                        }

                        // Verify location state (important for many devices including Xiaomi)
                        val locationActive = ble.verifyLocationState()
                        if (!locationActive) {
                            Log.e("BLE", "Location not active")
                            isScanning = false
                            return@launch
                        }

                        // Start scanning using the library's async API
                        ble.scanAsync(
                            duration = 10000, // 10 seconds scan duration
                            onDiscover = { device ->
                                // Process the discovered device
                                processDevice(device, onDeviceDetected)
                            },
                            onFinish = { devices ->
                                // Scan finished
                                Log.d("BLE", "Scan finished, found ${devices.size} devices")
                                // Schedule restart
                                mainHandler.postDelayed({
                                    isScanning = false
                                    startScanning(onDeviceDetected)
                                }, 100)
                            },
                            onError = { errorCode ->
                                Log.e("BLE", "Scan error: $errorCode")
                                isScanning = false
                            }
                        )
                    } catch (e:SecurityException) {
                        Log.e("BLE", "Security exception during BLE operation: ${e.message}")
                        isScanning = false
                    }
                }
            }
        }
    }

    private fun processDevice(
        device: Any, // Using Any type to avoid reference errors
        onDeviceDetected: (ScanResultWithText) -> Unit
    ) {
        // Extract device information using reflection to avoid direct class reference issues
        val deviceName = try {
            device.javaClass.getMethod("getName").invoke(device) as? String ?: ""
        } catch (e: Exception) {
            Log.e("BLE", "Error getting device name: ${e.message}")
            ""
        }

        val deviceAddress = try {
            device.javaClass.getMethod("getMacAddress").invoke(device) as? String ?: ""
        } catch (e: Exception) {
            Log.e("BLE", "Error getting device address: ${e.message}")
            ""
        }

        // Check if device name matches our target
        if (deviceName.contains(targetEspName, ignoreCase = true) ||
            deviceName.contains("ESP", ignoreCase = true)
        ) {
            // Check if we've already processed this device
            val deviceKey = "${deviceAddress}_${System.currentTimeMillis() / 10000}" // Group by ~10 seconds
            if (!detectedDevices.contains(deviceKey)) {
                // Add to our set of detected devices
                detectedDevices.add(deviceKey)

                Log.d("BLE", "NEW ESP Device detected: $deviceAddress, Name: $deviceName")

                // Create a custom result object with the detected information
                val resultWithText = ScanResultWithText(
                    result = null, // We don't have the native ScanResult object with BLE Made Easy
                    hasTextData = true,
                    message = "Device: $deviceName",
                    isEspDevice = true,
                    deviceAddress = deviceAddress,
                    deviceName = deviceName
                )

                // Call the onDeviceDetected callback on main thread
                mainHandler.post {
                    onDeviceDetected(resultWithText)
                }
            }
        }
    }

    fun stopScanning() {
        ble?.stopScan()
        isScanning = false
        mainHandler.removeCallbacksAndMessages(null)
        Log.d("BLE", "Stopped BLE scan")
    }

    // Process scan result has been simplified as we get more limited data from BLE Made Easy
    fun processScanResult(result: ScanResultWithText): ScanResultWithText {
        // With BLE Made Easy, we already have processed data
        return result
    }

    suspend fun markAttendanceAsync(subjectName: String, studentName: String, rollNumber: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = Firebase.firestore

                // Get today's date in the format "dd-MM-yyyy"
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val todayDate = dateFormat.format(Date())

                // Create or update the Firestore document
                db.collection("Subjects")
                    .document(subjectName)
                    .collection(todayDate)
                    .document(rollNumber)
                    .set(mapOf("name" to studentName))
                    .await()

                Log.d("Firestore", "Attendance marked for $studentName in $subjectName")
                true
            } catch (e: Exception) {
                Log.e("Firestore", "Error marking attendance", e)
                false
            }
        }
    }

    // Original markAttendance method kept for backward compatibility
    fun markAttendance(subjectName: String, studentName: String, rollNumber: String, onSuccess: () -> Unit = {}) {
        val db = Firebase.firestore

        scannerScope.launch {
            try {
                // Get today's date in the format "dd-MM-yyyy"
                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val todayDate = dateFormat.format(Date())

                // Create or update the Firestore document
                db.collection("Subjects")
                    .document(subjectName)
                    .collection(todayDate)
                    .document(rollNumber)
                    .set(mapOf("name" to studentName))
                    .await()

                Log.d("Firestore", "Attendance marked for $studentName in $subjectName")

                // Call success callback on the main thread
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error marking attendance", e)
                withContext(Dispatchers.Main) {
                    context?.let {
                        android.widget.Toast.makeText(it, "Failed to mark attendance", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Method to clear detected devices (useful when restarting scan)
    fun clearDetectedDevices() {
        detectedDevices.clear()
    }

    // Clean up resources
    fun cleanup() {
        stopScanning()
        mainHandler.removeCallbacksAndMessages(null)
        scannerScope.cancel()
    }
}