package com.winstone.callcollector

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WhatsAppPanel(context: Context) {
 var enabled by remember { mutableStateOf(WhatsAppSignalStore.enabled(context)) }
 var access by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) }
 var history by remember { mutableStateOf(WhatsAppSignalStore.history(context)) }
 var consent by remember { mutableStateOf(false) }
 val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
 fun refresh() {
  enabled = WhatsAppSignalStore.enabled(context)
  access = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
  history = WhatsAppSignalStore.history(context)
 }
 DisposableEffect(owner) {
  val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
  owner.lifecycle.addObserver(observer)
  onDispose { owner.lifecycle.removeObserver(observer) }
 }
 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
  Text("WhatsApp • Experimental", style = MaterialTheme.typography.titleMedium)
  Text("Local notification signals only — not verified calls. No names, numbers, messages, direction, or call duration. Not uploaded to the dashboard.", style = MaterialTheme.typography.bodySmall)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("Detect call notifications", modifier = Modifier.weight(1f))
   Switch(checked = enabled, onCheckedChange = { value ->
    if (value) consent = true
    else { WhatsAppSignalStore.setEnabled(context, false); refresh() }
   })
  }
  Text(if (!enabled) "OFF — Local history erased" else if (access) "READY — Notification access granted" else "BLOCKED — Notification access required")
  if (enabled && !access) OutlinedButton(onClick = {
   try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { access = false }
  }, modifier = Modifier.fillMaxWidth()) { Text("Open Notification Access") }
  OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh WhatsApp Signals") }
  Text("Recent call-notification signals: ${history.size}")
  history.takeLast(5).reversed().forEach { signal ->
   val date = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(signal.observedAtMs))
   Text("$date — ${if (signal.removedAtMs == null) "notification observed" else "notification removed (call outcome unknown)"}", style = MaterialTheme.typography.bodySmall)
  }
 }
 if (consent) AlertDialog(onDismissRequest = { consent = false },
  title = { Text("Enable WhatsApp detection?") },
  text = { Text("Only enable on a company-authorized phone with the phone user's informed consent. Android notification access is broad; WinLog filters only WhatsApp call-category metadata and does not read message content. Signals can be missed or duplicated and cannot prove a call connected. Turning this off erases local history. Android may require Allow restricted settings for a sideloaded app; do not bypass a security warning you do not understand.") },
  confirmButton = { TextButton(onClick = { WhatsAppSignalStore.setEnabled(context, true); refresh(); consent = false }) { Text("I consent — Enable") } },
  dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
}
