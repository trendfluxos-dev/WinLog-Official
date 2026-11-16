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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  CallLogSyncWorker.schedulePeriodicSync(this)
  setContent { MaterialTheme(colorScheme=darkColorScheme()) { Surface(modifier=Modifier.fillMaxSize()) { CollectorStatusScreen() } } }
 }
}
@Composable
fun CollectorStatusScreen() {
 val context=androidx.compose.ui.platform.LocalContext.current
 var employee by remember { mutableStateOf(CollectorPrefs.getEmployeeId(context)) }
 var receiver by remember { mutableStateOf(CollectorPrefs.getEndpointUrl(context)) }
 var bound by remember { mutableStateOf(CollectorPrefs.getEmployeeId(context)) }
 var status by remember { mutableStateOf(CollectorPrefs.getLastSyncStatus(context)) }
 var lastSync by remember { mutableStateOf(CollectorPrefs.getLastSyncAtMs(context)) }
 var permitted by remember { mutableStateOf(checkRequiredPermissions(context)) }
 var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
 var pendingReport by remember { mutableStateOf<String?>(null) }
 val lifecycleOwner=androidx.compose.ui.platform.LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner) {
  val observer=LifecycleEventObserver { _, event ->
   if(event==Lifecycle.Event.ON_RESUME) {
    permitted=checkRequiredPermissions(context)
    unrestricted=isIgnoringBatteryOptimizations(context)
    bound=CollectorPrefs.getEmployeeId(context)
    status=CollectorPrefs.getLastSyncStatus(context)
    lastSync=CollectorPrefs.getLastSyncAtMs(context)
   }
  }
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
 }
 val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
  val report=pendingReport
  if(uri!=null && report!=null) {
   try {
    val stream=context.contentResolver.openOutputStream(uri)
    if(stream==null) status="Could not save diagnostic report"
    else { stream.bufferedWriter(Charsets.UTF_8).use { it.write(report) }; status="Diagnostic report saved" }
   } catch(_:Exception) { status="Could not save diagnostic report" }
  }
  pendingReport=null
 }
 val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
  permitted=checkRequiredPermissions(context)
  if(permitted && bound.isNotBlank()) CallLogSyncWorker.enqueueImmediateSync(context,1L)
 }
 val qrLauncher=rememberLauncherForActivityResult(ScanContract()) { result ->
  if(result.contents!=null) {
   try {
    val setup=PhoneSetupValidator.parseQr(result.contents)
    employee=setup.employeeId; receiver=setup.receiver
    status="QR ready — tap Bind Device & Sync Now to confirm"
   } catch(_:Exception) { status="Invalid WinLog setup QR" }
  }
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Text("WINLOG OFFICIAL",style=MaterialTheme.typography.titleLarge)
  Text("Company call telemetry",style=MaterialTheme.typography.bodySmall)
  StatusRow("Device ID",CollectorPrefs.getAndroidId(context))
  StatusRow("Bound Agent",bound.ifBlank { "Unassigned" })
  StatusRow("Call Log Access",if(permitted) "Granted" else "Missing")
  StatusRow("Battery Mode",if(unrestricted) "Unrestricted" else "Optimized (Fix)")
  StatusRow("Last Sync",if(lastSync>0) SimpleDateFormat("dd MMM, hh:mm a",Locale.getDefault()).format(Date(lastSync)) else "Never")
  StatusRow("Sync Result",status)
  PhoneHealthPanel(context)
  OutlinedButton(onClick={ qrLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scan the QR from WinLog Phone setup").setBeepEnabled(false).setOrientationLocked(false)) },modifier=Modifier.fillMaxWidth()) { Text("Scan Setup QR") }
  OutlinedTextField(value=employee,onValueChange={ employee=it.uppercase() },label={ Text("Employee ID (e.g. WIN2604)") },singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(value=receiver,onValueChange={ receiver=it.trim() },label={ Text("WinLog receiver address") },singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={
   try {
    val setup=PhoneSetupValidator.validate(employee,receiver)
    CollectorPrefs.setEmployeeId(context,setup.employeeId); CollectorPrefs.setEndpointUrl(context,setup.receiver); bound=setup.employeeId
    permitted=checkRequiredPermissions(context)
    CallLogSyncWorker.schedulePeriodicSync(context)
    if(permitted) { CallLogSyncWorker.enqueueImmediateSync(context,1L); status="Sync queued..." }
    else status="Bound locally — grant call permissions to sync"
   } catch(_:IllegalArgumentException) { status="Setup refused — use a valid employee ID and the official WinLog receiver" }
  },modifier=Modifier.fillMaxWidth()) { Text("Bind Device & Sync Now") }
  if(!permitted) OutlinedButton(onClick={
   val permissions=mutableListOf(Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE)
   if(Build.VERSION.SDK_INT>=33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
   permissionLauncher.launch(permissions.toTypedArray())
  },modifier=Modifier.fillMaxWidth()) { Text("Grant Call Log Permissions") }
  if(!unrestricted) OutlinedButton(onClick={ context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data=Uri.parse("package:${context.packageName}") }) },modifier=Modifier.fillMaxWidth()) { Text("Allow Unrestricted Background Battery") }
  OutlinedButton(onClick={ status=CollectorPrefs.getLastSyncStatus(context); lastSync=CollectorPrefs.getLastSyncAtMs(context); permitted=checkRequiredPermissions(context); unrestricted=isIgnoringBatteryOptimizations(context) },modifier=Modifier.fillMaxWidth()) { Text("Refresh Status") }
  OutlinedButton(onClick={
   fun granted(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
   pendingReport=DiagnosticReport.render(DiagnosticSnapshot(
    appVersion=BuildConfig.VERSION_NAME,androidApi=Build.VERSION.SDK_INT,
    callLogGranted=granted(Manifest.permission.READ_CALL_LOG),phoneStateGranted=granted(Manifest.permission.READ_PHONE_STATE),cameraGranted=granted(Manifest.permission.CAMERA),
    notificationsGranted=if(Build.VERSION.SDK_INT>=33) granted(Manifest.permission.POST_NOTIFICATIONS) else null,
    batteryUnrestricted=isIgnoringBatteryOptimizations(context),locallyBound=CollectorPrefs.getEmployeeId(context).isNotBlank(),
    receiverIsWinLog=CollectorPrefs.getEndpointUrl(context)==CollectorPrefs.DEFAULT_INGEST_URL,recentSync=CollectorPrefs.getSyncDiagnostics(context)
   ),System.currentTimeMillis())
   exportLauncher.launch("winlog-diagnostics.json")
  },modifier=Modifier.fillMaxWidth()) { Text("Export Privacy-safe Diagnostics") }
 }
}
@Composable
private fun StatusRow(label:String,value:String) {
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(label,modifier=Modifier.weight(0.4f),style=MaterialTheme.typography.bodySmall)
  Text(value,modifier=Modifier.weight(0.6f),style=MaterialTheme.typography.bodySmall)
 }
}
private fun checkRequiredPermissions(context:Context):Boolean = ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALL_LOG)==PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context,Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
private fun isIgnoringBatteryOptimizations(context:Context):Boolean = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
