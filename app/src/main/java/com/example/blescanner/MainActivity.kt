package com.example.blescanner


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCell
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
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {
    // Create an instance of the BLEScannerLogic class
    private val bleLogic = BLEScannerLogic()

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
        bleLogic.stopScanning()
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BLEScannerApp(bleLogic: BLEScannerLogic) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("Alex Johnson") }
    var rollNumber by remember { mutableStateOf("20230045") }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var detectedSubject by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<BLEScannerLogic.ScanResultWithText>>(emptyList()) }
    var isAttendanceMarked by remember { mutableStateOf(false) }

    // Refined subtle animation
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Permission launcher (unchanged logic)
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

    // Check and request permissions (unchanged logic)
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

    // Start scanning automatically (unchanged logic)
    LaunchedEffect(hasPermissions, studentName, rollNumber) {
        if (hasPermissions && studentName.isNotBlank() && rollNumber.isNotBlank() && !isAttendanceMarked) {
            bleLogic.clearDetectedDevices()
            scanResults = emptyList()

            Log.d("BLE", "Auto-starting BLE scanning")
            bleLogic.startScanning { result ->
                val processedResult = bleLogic.processScanResult(result)

                if (processedResult.isEspDevice) {
                    if (scanResults.none { it.result.device.address == result.device.address }) {
                        scanResults = scanResults + processedResult
                    } else {
                        scanResults = scanResults.map {
                            if (it.result.device.address == result.device.address) {
                                processedResult
                            } else {
                                it
                            }
                        }
                    }

                    if (processedResult.hasTextData && processedResult.message.isNotBlank()) {
                        detectedSubject = processedResult.message
                        showConfirmationDialog = true
                    }
                }
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

            // Status card with refined animation
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(700)) + expandVertically(tween(700, easing = EaseOutQuart)),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .scale(pulse),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAttendanceMarked)
                            Color(0xFF2E7D32).copy(alpha = 0.9f)
                        else
                            Color(0xFF0D47A1).copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isAttendanceMarked) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = "Status",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp))

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isAttendanceMarked)
                                "Attendance Recorded"
                            else
                                "Awaiting Class Signal",
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        if (!isAttendanceMarked) {
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

            // Student info section with elegant design
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
                            onValueChange = { studentName = it },
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
                            onValueChange = { rollNumber = it },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Reset button with refined animation
            AnimatedVisibility(
                visible = isAttendanceMarked,
                enter = scaleIn(tween(500, easing = EaseOutQuart)) + fadeIn(tween(500)),
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
                                text = "Devices Detected: ${scanResults.size}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Scanning for '${bleLogic.targetEspName}' devices",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Device list with elegant animations
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .weight(1f)
            ) {
                val espDevices = scanResults.filter { it.isEspDevice }

                items(espDevices) { resultWithText ->
                    val result = resultWithText.result
                    val hasMessage = resultWithText.hasTextData
                    val deviceName = result.device.name ?: "ESP Device"
                    val deviceAddress = result.device.address
                    val rssi = result.rssi
                    val message = resultWithText.message

                    // Animate each device card entry with refined animation
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(600)) + expandVertically(tween(600, easing = EaseOutQuint)),
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

                                    // Signal strength indicator
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NetworkCell,
                                            contentDescription = "Signal",
                                            tint = when {
                                                rssi > -50 -> Color(0xFF2E7D32)
                                                rssi > -70 -> Color(0xFFFFA000)
                                                else -> Color(0xFFC62828)
                                            },
                                            modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "$rssi dBm",
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

                                    if (!isAttendanceMarked) {
                                        Text(
                                            text = "Class detected. Preparing confirmation...",
                                            fontSize = 13.sp,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else {
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
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                }

                if (espDevices.isEmpty()) {
                    item {
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
                }
            }
        }

        // Professional confirmation dialog
        if (showConfirmationDialog && !isAttendanceMarked) {
            Dialog(
                onDismissRequest = { showConfirmationDialog = false }
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
                                InfoRow(icon = Icons.Default.Book, label = "Course:", value = detectedSubject)
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
                                onClick = { showConfirmationDialog = false },
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
                                onClick = {
                                    showConfirmationDialog = false
                                    bleLogic.markAttendance(detectedSubject, studentName, rollNumber) {
                                        isAttendanceMarked = true
                                    }
                                    Toast.makeText(context, "Attendance recorded for $detectedSubject", Toast.LENGTH_SHORT).show()
                                },
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