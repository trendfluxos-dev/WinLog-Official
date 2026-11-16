package com.winstone.callcollector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MatteBlack = Color(0xFF0D0E11)
private val CharcoalSurface = Color(0xFF17191E)
private val ChampagneGold = Color(0xFFD4AF37)
private val MutedText = Color(0xFF9AA0A6)

class StatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CallLogSyncWorker.schedulePeriodicSync(this)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MatteBlack) {
                CollectorStatusScreen()
            }
        }
    }
}

@Composable
fun CollectorStatusScreen() {
    val context = LocalContext.current

    var employeeIdInput by remember { mutableStateOf(CollectorPrefs.getEmployeeId(context)) }
    var endpointUrlInput by remember { mutableStateOf(CollectorPrefs.getEndpointUrl(context)) }
    var boundEmployeeId by remember { mutableStateOf(CollectorPrefs.getEmployeeId(context)) }
    var lastStatus by remember { mutableStateOf(CollectorPrefs.getLastSyncStatus(context)) }
    var lastSyncAtMs by remember { mutableStateOf(CollectorPrefs.getLastSyncAtMs(context)) }
    var hasCallPermissions by remember { mutableStateOf(checkRequiredPermissions(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasCallPermissions = checkRequiredPermissions(context)
        if (hasCallPermissions && boundEmployeeId.isNotBlank()) {
            CallLogSyncWorker.enqueueImmediateSync(context, delaySeconds = 1L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteBlack)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "WINLOG OFFICIAL",
            color = ChampagneGold,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Company call telemetry",
            color = MutedText,
            fontSize = 13.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CharcoalSurface, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFF2B2E36), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusRow("Device ID", CollectorPrefs.getAndroidId(context))
            StatusRow("Bound Agent", boundEmployeeId.ifBlank { "Unassigned" })
            StatusRow("Call Log Access", if (hasCallPermissions) "Granted" else "Missing")
            StatusRow("Battery Mode", if (isBatteryUnrestricted) "Unrestricted" else "Optimized (Fix)")
            StatusRow("Last Sync", formatTimestamp(lastSyncAtMs))
            StatusRow("Sync Result", lastStatus)
        }

        OutlinedTextField(
            value = employeeIdInput,
            onValueChange = { employeeIdInput = it.uppercase() },
            label = { Text("Employee ID (e.g. WIN2604)", color = MutedText) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ChampagneGold,
                unfocusedBorderColor = Color(0xFF3A3E48),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = endpointUrlInput,
            onValueChange = { endpointUrlInput = it.trim() },
            label = { Text("WinLog receiver address", color = MutedText) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ChampagneGold,
                unfocusedBorderColor = Color(0xFF3A3E48),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val cleanedId = employeeIdInput.trim().uppercase()
                val cleanedUrl = endpointUrlInput.trim()
                if (cleanedId.isNotBlank() && cleanedUrl.isNotBlank()) {
                    CollectorPrefs.setEmployeeId(context, cleanedId)
                    CollectorPrefs.setEndpointUrl(context, cleanedUrl)
                    boundEmployeeId = cleanedId
                    CallLogSyncWorker.schedulePeriodicSync(context)
                    if (hasCallPermissions) {
                        CallLogSyncWorker.enqueueImmediateSync(context, delaySeconds = 1L)
                        lastStatus = "Sync queued..."
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = MatteBlack),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Bind Device & Sync Now", fontWeight = FontWeight.SemiBold)
        }

        if (!hasCallPermissions) {
            OutlinedButton(
                onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_PHONE_STATE
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Call Log Permissions", color = ChampagneGold)
            }
        }

        if (!isBatteryUnrestricted) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    isBatteryUnrestricted = isIgnoringBatteryOptimizations(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Allow Unrestricted Background Battery", color = ChampagneGold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = {
                lastStatus = CollectorPrefs.getLastSyncStatus(context)
                lastSyncAtMs = CollectorPrefs.getLastSyncAtMs(context)
                hasCallPermissions = checkRequiredPermissions(context)
                isBatteryUnrestricted = isIgnoringBatteryOptimizations(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Refresh Status", color = MutedText)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MutedText, fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun checkRequiredPermissions(context: Context): Boolean {
    val callLogGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED
    val phoneStateGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
    return callLogGranted && phoneStateGranted
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "Never"
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return formatter.format(Date(ms))
}
