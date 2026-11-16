package com.winstone.callcollector
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PhoneHealth(val permissions:String,val binding:String,val connection:String)
object PhoneHealthCheck {
 suspend fun run(context:Context):PhoneHealth=withContext(Dispatchers.IO) {
  val allowed=listOf(Manifest.permission.READ_CALL_LOG,Manifest.permission.READ_PHONE_STATE).all { ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED }
  val permissions=if(allowed) "PASS — Call access granted" else "FAIL — Grant call log and phone permissions"
  val employee=CollectorPrefs.getEmployeeId(context)
  val endpoint=CollectorPrefs.getEndpointUrl(context)
  if(endpoint!=CollectorPrefs.DEFAULT_INGEST_URL) return@withContext PhoneHealth(permissions,"FAIL — Use the WinLog receiver and bind","FAIL — Receiver address is not WinLog")
  if(CollectorPrefs.DEVICE_API_KEY.isBlank()) return@withContext PhoneHealth(permissions,"FAIL — Cannot verify binding","FAIL — Install the configured APK")
  var connection:HttpURLConnection?=null
  try {
   connection=URL(endpoint).openConnection() as HttpURLConnection
   connection.requestMethod="POST"; connection.connectTimeout=10000; connection.readTimeout=10000
   connection.instanceFollowRedirects=false; connection.doOutput=true
   connection.setRequestProperty("Content-Type","application/json")
   connection.setRequestProperty("x-device-api-key",CollectorPrefs.DEVICE_API_KEY)
   val payload=JSONObject().put("action","health_check").put("android_id",CollectorPrefs.getAndroidId(context)).put("employee_id",employee.ifBlank { "unassigned" })
   connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
   val code=connection.responseCode
   if(code!=200) {
    val message=when(code) {
     401 -> "FAIL — Device key refused; install the configured APK"
     400,404 -> "FAIL — Publish the health-check update"
     else -> "FAIL — Sync service unavailable; try again"
    }
    return@withContext PhoneHealth(permissions,"FAIL — Binding not verified",message)
   }
   val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
   if(response.optString("connection")!="pass") return@withContext PhoneHealth(permissions,"FAIL — Binding not verified","FAIL — Publish the health-check update")
   val binding=if(employee.isBlank()) "FAIL — Bind an employee first" else when(response.optString("reason")) {
    "verified" -> if(response.optString("binding")=="pass") "PASS — Active binding verified" else "FAIL — Binding not verified"
    "device_inactive" -> "FAIL — Manager must enable this device"
    "binding_mismatch" -> "FAIL — Saved employee does not match WinLog"
    "not_registered" -> "FAIL — Complete the first call sync to register"
    else -> "FAIL — Binding not verified"
   }
   PhoneHealth(permissions,binding,"PASS — WinLog accepted the health probe")
  } catch(_:Exception) { PhoneHealth(permissions,"FAIL — Binding not verified","FAIL — Cannot reach WinLog; check internet") }
  finally { connection?.disconnect() }
 }
}
@Composable
fun PhoneHealthPanel(context:Context) {
 val scope=rememberCoroutineScope()
 var running by remember { mutableStateOf(false) }
 var result by remember { mutableStateOf<PhoneHealth?>(null) }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  OutlinedButton(enabled=!running,onClick={
   running=true; result=null
   scope.launch { try { result=PhoneHealthCheck.run(context) } finally { running=false } }
  },modifier=Modifier.fillMaxWidth()) { Text(if(running) "Checking…" else "Run Phone Health Check") }
  result?.let { Text("Call permissions: ${it.permissions}"); Text("Device binding: ${it.binding}"); Text("Sync connection: ${it.connection}") }
 }
}
