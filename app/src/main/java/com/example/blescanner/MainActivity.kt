package com.example.blescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    // Create an instance of the BLEScannerLogic class
    private val bleLogic = BLEScannerLogic()
    private var scanJob: Job? = null
    private var attendanceJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLE components
        bleLogic.initialize(this)

        setContent {
            BLEScannerApp(bleLogic)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleLogic.cleanup()
        scanJob?.cancel()
        attendanceJob?.cancel()
    }

    // Handle attendance marking in background
    private fun markAttendanceInBackground(
        subject: String,
        studentName: String,
        rollNumber: String,
        onComplete: (Boolean) -> Unit
    ) {
        attendanceJob?.cancel()
        attendanceJob = lifecycleScope.launch {
            try {
                val success = bleLogic.markAttendanceAsync(subject, studentName, rollNumber)
                onComplete(success)
            } catch (e: Exception) {
                Log.e("Attendance", "Error marking attendance", e)
                onComplete(false)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BLEScannerApp(bleLogic: BLEScannerLogic) {
    val context = LocalContext.current
    var studentName by remember { mutableStateOf("Alex Johnson") }
    var rollNumber by remember { mutableStateOf("20230045") }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var detectedSubject by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<BLEScannerLogic.ScanResultWithText>>(emptyList()) }
    var isAttendanceMarked by remember { mutableStateOf(false) }
    var isMarkingAttendance by remember { mutableStateOf(false) }

    // Coroutine scope for this composable
    val coroutineScope = rememberCoroutineScope()

    // Refined subtle animation with lower intensity for better performance
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f, // Reduced animation range
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad), // Slower animation
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )

    // Start continuous scanning - no need to check permissions as BLE Made Easy handles this
    LaunchedEffect(Unit) {
        bleLogic.clearDetectedDevices()
        scanResults = emptyList()

        Log.d("BLE", "Starting continuous BLE scanning")
        bleLogic.startScanning { processedResult ->
            // Update scan results with new device or update existing one
            scanResults = scanResults.toMutableList().apply {
                val existingIndex = indexOfFirst { it.deviceAddress == processedResult.deviceAddress }
                if (existingIndex >= 0) {
                    this[existingIndex] = processedResult
                } else {
                    add(processedResult)
                }
            }

            if (processedResult.hasTextData &&
                processedResult.message.isNotBlank() &&
                !isAttendanceMarked &&
                !isMarkingAttendance &&
                !showConfirmationDialog
            ) {
                detectedSubject = processedResult.message
                showConfirmationDialog = true
            }
        }
    }

    // More professional gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A2151),  // Dark navy blue
                        Color(0xFF263464)   // Slightly lighter navy blue
                    )
                )
            )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // More professional header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Attendance",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp))

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Attendance Management",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.2f),
                                offset = Offset(1f, 1f),
                                blurRadius = 2f
                            ),
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            // Status card with optimized animation
            StatusCard(
                isAttendanceMarked = isAttendanceMarked,
                isMarkingAttendance = isMarkingAttendance,
                pulse = pulse
            )

            // Student info section with elegant design
            StudentInfoCard(
                studentName = studentName,
                rollNumber = rollNumber,
                isAttendanceMarked = isAttendanceMarked,
                onStudentNameChange = { studentName = it },
                onRollNumberChange = { rollNumber = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reset button with refined animation
            AnimatedVisibility(
                visible = isAttendanceMarked,
                enter = scaleIn(tween(300, easing = EaseOutQuart)) + fadeIn(tween(300)),
                exit = scaleOut() + fadeOut()
            ) {
                Button(
                    onClick = {
                        isAttendanceMarked = false
                        bleLogic.clearDetectedDevices()
                        scanResults = emptyList()
                    },
                    modifier = Modifier
                        .width(240.dp)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D47A1),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = "Reset for Next Session",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isAttendanceMarked) {
                // Scanning status with refined presentation
                ScanningStatusBar(scanResults.size, bleLogic.targetEspName)
            }

            // Device list with optimized animations
            DeviceList(
                scanResults = scanResults.filter { it.isEspDevice },
                isAttendanceMarked = isAttendanceMarked
            )
        }

        // Confirmation dialog with improved responsiveness
        if (showConfirmationDialog) {
            AttendanceConfirmationDialog(
                subject = detectedSubject,
                studentName = studentName,
                rollNumber = rollNumber,
                onDismiss = {
                    showConfirmationDialog = false
                },
                onConfirm = {
                    showConfirmationDialog = false
                    isMarkingAttendance = true

                    // Use coroutineScope to handle attendance marking
                    coroutineScope.launch {
                        try {
                            val success = bleLogic.markAttendanceAsync(detectedSubject, studentName, rollNumber)
                            if (success) {
                                isAttendanceMarked = true
                                Toast.makeText(context, "Attendance recorded for $detectedSubject", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to mark attendance", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isMarkingAttendance = false
                        }
                    }
                }
            )
        }
    }
}

// The rest of your composable functions remain the same as in the original code
// Only DeviceCard needs to be updated since we changed ScanResultWithText

@Composable
fun DeviceCard(
    result: BLEScannerLogic.ScanResultWithText,
    isAttendanceMarked: Boolean
) {
    val deviceName = result.deviceName ?: "ESP Device"
    val deviceAddress = result.deviceAddress
    val hasMessage = result.hasTextData
    val message = result.message

    // Use key for better animation handling
    key(deviceAddress) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(400)) + expandVertically(tween(400, easing = EaseOutQuint)),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1A2151)
                        )

                        // Signal strength indicator - no RSSI with BLE Made Easy
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NetworkCell,
                                contentDescription = "Signal",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Connected",
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                        }
                    }

                    Text(
                        text = deviceAddress,
                        fontSize = 13.sp,
                        color = Color.DarkGray.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (hasMessage) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9))
                                .padding(8.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(4.dp)
                                )
                        ) {
                            Text(
                                text = "Class Identifier:",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = message,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isAttendanceMarked) {
                            Text(
                                text = "Attendance recorded for this session",
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "No class identifier detected",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

// Retain other composable functions from the original code (StatusCard, StudentInfoCard, etc.)

@Composable
fun StatusCard(isAttendanceMarked: Boolean, isMarkingAttendance: Boolean, pulse: Float) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(500)) + expandVertically(tween(500, easing = EaseOutQuart)),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .scale(if (isMarkingAttendance) pulse else 1f),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isAttendanceMarked -> Color(0xFF2E7D32).copy(alpha = 0.9f)
                    isMarkingAttendance -> Color(0xFFFFA000).copy(alpha = 0.9f)
                    else -> Color(0xFF0D47A1).copy(alpha = 0.9f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        isAttendanceMarked -> Icons.Default.CheckCircle
                        isMarkingAttendance -> Icons.Default.Refresh
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = "Status",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp))

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isAttendanceMarked -> "Attendance Recorded"
                        isMarkingAttendance -> "Recording Attendance..."
                        else -> "Awaiting Class Signal"
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = Color.White
                )

                if (!isAttendanceMarked && !isMarkingAttendance) {
                    Text(
                        text = "Please ensure Bluetooth is activated and you are within proximity of the classroom",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StudentInfoCard(
    studentName: String,
    rollNumber: String,
    isAttendanceMarked: Boolean,
    onStudentNameChange: (String) -> Unit,
    onRollNumberChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFECEFF1).copy(alpha = 0.9f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Student Information",
                color = Color(0xFF1A2151),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Name field with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Name",
                    tint = Color(0xFF1A2151),
                    modifier = Modifier.padding(end = 8.dp))

                TextField(
                    value = studentName,
                    onValueChange = onStudentNameChange,
                    label = { Text("Student Name", color = Color(0xFF1A2151).copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAttendanceMarked,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF1A2151),
                        unfocusedIndicatorColor = Color(0xFF1A2151).copy(alpha = 0.5f),
                        cursorColor = Color(0xFF1A2151),
                        focusedTextColor = Color(0xFF1A2151),
                        unfocusedTextColor = Color(0xFF1A2151).copy(alpha = 0.9f)
                    )
                )
            }

            // Roll number field with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Numbers,
                    contentDescription = "Roll Number",
                    tint = Color(0xFF1A2151),
                    modifier = Modifier.padding(end = 8.dp))

                TextField(
                    value = rollNumber,
                    onValueChange = onRollNumberChange,
                    label = { Text("Roll Number", color = Color(0xFF1A2151).copy(alpha = 0.7f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAttendanceMarked,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color(0xFF1A2151),
                        unfocusedIndicatorColor = Color(0xFF1A2151).copy(alpha = 0.5f),
                        cursorColor = Color(0xFF1A2151),
                        focusedTextColor = Color(0xFF1A2151),
                        unfocusedTextColor = Color(0xFF1A2151).copy(alpha = 0.9f)
                    )
                )
            }
        }
    }
}

@Composable
fun ScanningStatusBar(deviceCount: Int, targetName: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0D47A1).copy(alpha = 0.15f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = "Scanning",
                tint = Color.White,
                modifier = Modifier.size(24.dp))

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Devices Detected: $deviceCount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Scanning for '$targetName' devices",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun DeviceList(
    scanResults: List<BLEScannerLogic.ScanResultWithText>,
    isAttendanceMarked: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)

    ) {
        items(scanResults) { resultWithText ->
            DeviceCard(
                result = resultWithText,
                isAttendanceMarked = isAttendanceMarked
            )
        }

        if (scanResults.isEmpty()) {
            item {
                EmptyDeviceList()
            }
        }
    }
}


@Composable
fun EmptyDeviceList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = "No devices",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No ESP Devices Detected",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = "Please ensure your ESP32 device is powered on and broadcasting",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AttendanceConfirmationDialog(
    subject: String,
    studentName: String,
    rollNumber: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Class,
                    contentDescription = "Class",
                    tint = Color(0xFF1A2151),
                    modifier = Modifier.size(40.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Class Session Detected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1A2151)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Class info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        InfoRow(icon = Icons.Default.Book, label = "Course:", value = subject)
                        InfoRow(icon = Icons.Default.Person, label = "Student:", value = studentName)
                        InfoRow(icon = Icons.Default.Numbers, label = "ID:", value = rollNumber)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Would you like to record your attendance?",
                    fontSize = 15.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, Color(0xFF1A2151)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Cancel",
                            color = Color(0xFF1A2151)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A2151),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm",
                                modifier = Modifier.padding(end = 8.dp))
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF1A2151).copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp))

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$label ",
            color = Color.DarkGray,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}