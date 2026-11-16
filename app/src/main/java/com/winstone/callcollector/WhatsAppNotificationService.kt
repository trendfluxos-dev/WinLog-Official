package com.winstone.callcollector

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

object WhatsAppSignalStore {
 private fun prefs(context: Context) = context.getSharedPreferences("winlog_whatsapp_signals", Context.MODE_PRIVATE)
 private var tracker: WhatsAppSignalTracker? = null
 private fun tracker(context: Context): WhatsAppSignalTracker {
  val existing = tracker
  if (existing != null) return existing
  return WhatsAppSignalTracker(history(context)).also { tracker = it }
 }
 fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)
 @Synchronized fun setEnabled(context: Context, enabled: Boolean) {
  // Opting out also erases local history and any active notification keys.
  if (!enabled) tracker?.clear()
  prefs(context).edit().putBoolean("enabled", enabled).apply()
  if (!enabled) prefs(context).edit().remove("signals").apply()
 }
 @Synchronized fun posted(context: Context, key: String, at: Long) {
  if (!enabled(context)) return
  tracker(context).posted(key, at); save(context)
 }
 @Synchronized fun removed(context: Context, key: String, at: Long) {
  if (!enabled(context)) return
  tracker(context).removed(key, at); save(context)
 }
 private fun save(context: Context) {
  val json = JSONArray()
  tracker(context).snapshot().forEach { signal ->
   json.put(JSONObject().put("observed_at_ms", signal.observedAtMs).apply {
    signal.removedAtMs?.let { put("notification_removed_at_ms", it) }
   })
  }
  prefs(context).edit().putString("signals", json.toString()).apply()
 }
 fun history(context: Context): List<WhatsAppSignal> = try {
  val json = JSONArray(prefs(context).getString("signals", "[]"))
  (0 until json.length()).map { i ->
   val row = json.getJSONObject(i)
   WhatsAppSignal(row.getLong("observed_at_ms"), if (row.has("notification_removed_at_ms")) row.getLong("notification_removed_at_ms") else null)
  }.takeLast(WhatsAppSignalTracker.LIMIT)
 } catch (_: Exception) { emptyList() }
}

class WhatsAppNotificationService : NotificationListenerService() {
 override fun onNotificationPosted(sbn: StatusBarNotification?) {
  if (sbn == null) return
  // Never read extras, titles, text, contacts, numbers, or actions.
  if (WhatsAppSignalTracker.accepts(WhatsAppSignalStore.enabled(this), sbn.packageName, sbn.notification.category)) {
   WhatsAppSignalStore.posted(this, sbn.key, System.currentTimeMillis())
  }
 }
 override fun onNotificationRemoved(sbn: StatusBarNotification?) {
  if (sbn == null || sbn.packageName !in setOf("com.whatsapp", "com.whatsapp.w4b")) return
  WhatsAppSignalStore.removed(this, sbn.key, System.currentTimeMillis())
 }
}
