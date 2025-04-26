package com.example.blescanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    // Declare scanCallback as a class-level variable
    private var scanCallback: ScanCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var isScanning by remember { mutableStateOf(false) }
            var hasPermissions by remember { mutableStateOf(false) }
            var studentName by remember { mutableStateOf("") }
            var rollNumber by remember { mutableStateOf("") }
            var showConfirmationDialog by remember { mutableStateOf(false) }
            var detectedSubject by remember { mutableStateOf("") }
            var scanResults by remember { mutableStateOf<List<ScanResultWithText>>(emptyList()) }

            // Permission launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    hasPermissions = true
                } else {
                    Toast.makeText(context, "Permissions are required for BLE scanning", Toast.LENGTH_LONG).show()
                    hasPermissions = false
                }
            }

            // Check and request permissions
            LaunchedEffect(Unit) {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    listOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    listOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }

                val missingPermissions = permissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                } else {
                    hasPermissions = true
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Student Name Input
                TextField(
                    value = studentName,
                    onValueChange = { studentName = it },
                    label = { Text("Student Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                // Roll Number Input
                TextField(
                    value = rollNumber,
                    onValueChange = { rollNumber = it },
                    label = { Text("Roll Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Start/Stop Scanning Button
                Button(
                    onClick = {
                        if (studentName.isBlank() || rollNumber.isBlank()) {
                            Toast.makeText(context, "Please enter your name and roll number", Toast.LENGTH_SHORT).show()
                        } else if (hasPermissions) {
                            isScanning = !isScanning
                            if (isScanning) {
                                Log.d("BLE", "Starting scanning for ESP devices...")
                                // Clear previous scan results when starting a new scan
                                scanResults = emptyList()
                                startScanning { result ->
                                    // Process the result to check if it's an ESP device with a message
                                    val processedResult = processScanResult(result)

                                    // Only handle ESP devices
                                    if (processedResult.isEspDevice) {
                                        // Add the result to the list if it's not already there
                                        if (scanResults.none { it.result.device.address == result.device.address }) {
                                            scanResults = scanResults + processedResult
                                        } else {
                                            // Update existing result with new data
                                            scanResults = scanResults.map {
                                                if (it.result.device.address == result.device.address) {
                                                    processedResult
                                                } else {
                                                    it
                                                }
                                            }
                                        }

                                        // If we received a message, we can show a toast notification
                                        if (processedResult.hasTextData && processedResult.message.isNotBlank()) {
                                            Toast.makeText(
                                                context,
                                                "Received: ${processedResult.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            } else {
                                stopScanning()
                            }
                        } else {
                            Toast.makeText(context, "Permissions not granted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.width(256.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color(0xFFE57373) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = if (isScanning) "Stop Scanning" else "Start ESP Scanner",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Found ${scanResults.size} devices",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Devices with text content are highlighted",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                // Display only ESP devices in a LazyColumn
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Only display ESP devices with messages
                    val espDevices = scanResults.filter { it.isEspDevice }

                    items(espDevices) { resultWithText ->
                        val result = resultWithText.result
                        val hasMessage = resultWithText.hasTextData
                        val deviceName = result.device.name ?: "ESP Device"
                        val deviceAddress = result.device.address
                        val rssi = result.rssi // Signal strength
                        val message = resultWithText.message

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = deviceName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$rssi dBm",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }

                                Text(
                                    text = deviceAddress,
                                    fontSize = 14.sp,
                                    color = Color.DarkGray
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (hasMessage) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFE8F5E9))
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "Message:",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = Color.DarkGray
                                        )
                                        Text(
                                            text = message,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32) // Dark green
                                        )
                                    }

                                    // Use the message as the subject name for attendance
                                    Button(
                                        onClick = {
                                            // Mark attendance with the message as subject
                                            detectedSubject = message
                                            markAttendance(message, studentName, rollNumber)
                                            Toast.makeText(context,
                                                "Attendance marked for subject: $message",
                                                Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        )
                                    ) {
                                        Text("Mark Attendance for $message")
                                    }
                                } else {
                                    Text(
                                        text = "No message detected",
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }

                    // Show message if no ESP devices found
                    if (espDevices.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No ESP32 devices found",
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Make sure your ESP32 device is powered on and broadcasting",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Confirmation Dialog
            if (showConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmationDialog = false },
                    title = { Text("Attendance Confirmation") },
                    text = { Text("Do you want to mark attendance for $detectedSubject?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmationDialog = false
                                stopScanning() // Stop scanning automatically
                                markAttendance(detectedSubject, studentName, rollNumber)
                                Toast.makeText(context, "Attendance marked for $detectedSubject", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                showConfirmationDialog = false
                                stopScanning() // Stop scanning if the user cancels
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // ESP32 Service UUID
    private val ESP_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

    // Data class to hold scan result with text information
    data class ScanResultWithText(
        val result: ScanResult,
        val hasTextData: Boolean,
        val message: String,
        val isEspDevice: Boolean
    )

    // Process scan result to detect if it's from our ESP device and extract the message
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processScanResult(result: ScanResult): ScanResultWithText {
        val scanRecord = result.scanRecord
        var message = ""
        var isEspDevice = false

        // Check if this is an ESP device
        if (scanRecord != null) {
            val serviceUuids = scanRecord.serviceUuids

            // Check if device advertises our ESP service UUID
            isEspDevice = serviceUuids?.any { it.uuid.toString().equals(ESP_SERVICE_UUID, ignoreCase = true) } ?: false

            // If the device name contains ESP, also consider it an ESP device
            if (!isEspDevice && result.device.name?.contains("ESP", ignoreCase = true) == true) {
                isEspDevice = true
            }

            // For ESP devices, try multiple methods to extract the message
            if (isEspDevice) {
                Log.d("BLE_DATA", "Found ESP device: ${result.device.name}, Address: ${result.device.address}")

                // Method 1: Try to get the characteristic data directly
                // This would require connecting to the device, which we're not doing here

                // Method 2: Try reading from advertisement data
                val manufacturerData = scanRecord.manufacturerSpecificData
                if (manufacturerData != null && manufacturerData.size() > 0) {
                    for (i in 0 until manufacturerData.size()) {
                        val manData = manufacturerData.valueAt(i)
                        if (manData != null) {
                            val potentialMessage = String(manData, Charsets.UTF_8)
                            Log.d("BLE_DATA", "Manufacturer data found: $potentialMessage")
                            if (potentialMessage.isNotEmpty() && potentialMessage.any { it.isLetterOrDigit() }) {
                                message = potentialMessage.filter { it.isLetterOrDigit() || it.isWhitespace() }
                                break
                            }
                        }
                    }
                }

                // Method 3: Get service data
                if (message.isBlank()) {
                    val parcelUuid = ParcelUuid.fromString(ESP_SERVICE_UUID)
                    val serviceData = scanRecord.getServiceData(parcelUuid)
                    if (serviceData != null) {
                        message = String(serviceData, Charsets.UTF_8)
                        Log.d("BLE_DATA", "Service data found: $message")
                    }
                }

                // Method 4: Parse raw advertisement bytes to find the message
                if (message.isBlank() && scanRecord.bytes != null) {
                    val rawData = scanRecord.bytes
                    Log.d("BLE_DATA", "Raw bytes length: ${rawData.size}")

                    // Log raw bytes for debugging
                    val hexString = rawData.joinToString("") {
                        String.format("%02X ", it)
                    }
                    Log.d("BLE_DATA", "Raw advertisement bytes: $hexString")

                    // Look for text content in the advertisement - BLE advertisement packets are structured,
                    // so we need to look for the data section after service UUID

                    // Convert to string and look for patterns
                    val fullString = String(rawData, Charsets.UTF_8)
                    Log.d("BLE_DATA", "Full string from raw bytes: $fullString")

                    // Search for standard characteristic values
                    // The message may be in the parts of the byte array that aren't service UUIDs or headers

                    // Try to find alphabetical strings in the byte array - our message should be there
                    val alphabeticalRegex = "[A-Za-z]{2,}".toRegex()
                    val matches = alphabeticalRegex.findAll(fullString)
                    matches.forEach { match ->
                        val potentialMessage = match.value
                        // Don't consider common BLE terms
                        if (potentialMessage.length >= 2 &&
                            !potentialMessage.equals("ESP", ignoreCase = true) &&
                            !potentialMessage.contains("service", ignoreCase = true) &&
                            !potentialMessage.contains("uuid", ignoreCase = true) &&
                            !potentialMessage.contains("scan", ignoreCase = true)) {

                            Log.d("BLE_DATA", "Potential message from regex: $potentialMessage")
                            message = potentialMessage
                            return@forEach
                        }
                    }

                    // Last resort: check consecutive printable ASCII characters
                    // (this is less reliable but might catch the message)
                    if (message.isBlank()) {
                        var currentString = ""
                        var bestString = ""

                        for (byte in rawData) {
                            val char = byte.toInt().toChar()
                            if (char.isLetterOrDigit() || char.isWhitespace()) {
                                currentString += char
                            } else {
                                if (currentString.length > bestString.length && currentString.length >= 2) {
                                    bestString = currentString
                                }
                                currentString = ""
                            }
                        }

                        // Check final string
                        if (currentString.length > bestString.length && currentString.length >= 2) {
                            bestString = currentString
                        }

                        if (bestString.isNotEmpty() &&
                            !bestString.equals("ESP", ignoreCase = true) &&
                            !bestString.contains("Attendance", ignoreCase = true)) {
                            Log.d("BLE_DATA", "Found string in raw data: $bestString")
                            message = bestString
                        }
                    }
                }

                // If we still don't have a message, try direct extraction one more time
                if (message.isBlank()) {
                    try {
                        // Try to extract the message from the raw bytes using a different approach
                        // Look for specific patterns in BLE advertisement structure
                        val rawData = scanRecord.bytes

                        // In BLE advertisements, there are typically length + type + data fields
                        // For service data, the type is typically 0x16 (22 decimal)
                        var index = 0
                        while (index < rawData.size - 2) {
                            val length = rawData[index].toInt() and 0xFF
                            if (length == 0) break

                            val type = rawData[index + 1].toInt() and 0xFF

                            // Check for service data (type 0x16) or manufacturer data (type 0xFF)
                            if ((type == 0x16 || type == 0xFF) && index + length < rawData.size) {
                                // Extract the data portion
                                val dataBytes = ByteArray(length - 1)
                                System.arraycopy(rawData, index + 2, dataBytes, 0, length - 1)

                                // Convert to string and check if it contains our message
                                val extractedText = String(dataBytes, Charsets.UTF_8)
                                Log.d("BLE_DATA", "Extracted from type $type: $extractedText")

                                if (extractedText.length >= 2 && extractedText.any { it.isLetterOrDigit() }) {
                                    val filteredText = extractedText.filter { it.isLetterOrDigit() || it.isWhitespace() }
                                    if (filteredText.isNotEmpty() &&
                                        !filteredText.equals("ESP", ignoreCase = true) &&
                                        !filteredText.contains("Attendance", ignoreCase = true)) {
                                        message = filteredText
                                        break
                                    }
                                }
                            }

                            // Move to next field
                            index += length + 1
                        }
                    } catch (e: Exception) {
                        Log.e("BLE_DATA", "Error parsing raw advertisement: ${e.message}")
                    }
                }

                Log.d("BLE_DATA", "Final extracted message: '$message'")
            }
        }

        return ScanResultWithText(result, message.isNotBlank(), message, isEspDevice)
    }

    private fun startScanning(onDeviceDetected: (ScanResult) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Initialize scanCallback
        scanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)

                // Process the result
                val processedResult = processScanResult(result)

                // Log only ESP devices
                if (processedResult.isEspDevice) {
                    Log.d("BLE", "ESP Device detected: ${result.device.address}, Message: ${processedResult.message}")
                    // Call the onDeviceDetected callback
                    onDeviceDetected(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BLE", "Scan failed with error code: $errorCode")
            }
        }

        // Create a scan filter for the ESP service UUID
        val filters = mutableListOf<ScanFilter>()

        // Add filter for the specific ESP service UUID
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(ESP_SERVICE_UUID))
            .build()
        filters.add(scanFilter)

        // Also add a filter for devices with "ESP" in their name
        val nameFilter = ScanFilter.Builder()
            .setDeviceName("ESP32-Attendance")
            .build()
        filters.add(nameFilter)

        // Configure scan settings for low latency
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Start scanning with the filters
        bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
    }

    private fun stopScanning() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Stop scanning
        scanCallback?.let { callback ->
            bluetoothLeScanner?.stopScan(callback)
        }
    }

    private fun markAttendance(subjectName: String, studentName: String, rollNumber: String) {
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
            .addOnSuccessListener {
                Log.d("Firestore", "Attendance marked for $studentName in $subjectName")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error marking attendance", e)
                Toast.makeText(this, "Failed to mark attendance", Toast.LENGTH_SHORT).show()
            }
    }
}