package com.winstone.callcollector

import java.time.Instant

enum class SyncOutcome { UPLOAD_ACCEPTED, NO_NEW_CALLS, MISSING_PERMISSION, SETUP_REQUIRED, HTTP_ERROR, UNKNOWN }
data class SyncDiagnostic(val completedAtMs: Long, val outcome: SyncOutcome)
data class DiagnosticSnapshot(
    val appVersion: String,
    val androidApi: Int,
    val callLogGranted: Boolean,
    val phoneStateGranted: Boolean,
    val cameraGranted: Boolean,
    val notificationsGranted: Boolean?,
    val batteryUnrestricted: Boolean,
    val locallyBound: Boolean,
    val receiverIsWinLog: Boolean,
    val recentSync: List<SyncDiagnostic>
)

object DiagnosticReport {
    const val HISTORY_LIMIT = 20
    fun outcome(status: String): SyncOutcome = when {
        status.startsWith("Synced ") -> SyncOutcome.UPLOAD_ACCEPTED
        status == "Up to date (0 new calls)" -> SyncOutcome.NO_NEW_CALLS
        status == "Missing READ_CALL_LOG permission" -> SyncOutcome.MISSING_PERMISSION
        status == "Set Employee ID and reinstall the configured WinLog app" -> SyncOutcome.SETUP_REQUIRED
        status.startsWith("HTTP ") -> SyncOutcome.HTTP_ERROR
        else -> SyncOutcome.UNKNOWN
    }
    fun append(history: List<SyncDiagnostic>, entry: SyncDiagnostic): List<SyncDiagnostic> =
        (history + entry).filter { it.completedAtMs > 0 }.takeLast(HISTORY_LIMIT)
    private fun timestamp(ms: Long): String = "\"${Instant.ofEpochMilli(ms)}\""
    private fun quote(value: String): String = "\"" + value.flatMap { c ->
        when (c) {
            '\\' -> "\\\\".toList()
            '"' -> "\\\"".toList()
            else -> if (c.code < 32) "\\u%04x".format(c.code).toList() else listOf(c)
        }
    }.joinToString("") + "\""
    fun render(snapshot: DiagnosticSnapshot, generatedAtMs: Long): String {
        val history = snapshot.recentSync.filter { it.completedAtMs > 0 }.takeLast(HISTORY_LIMIT)
        val lastAttempt = history.maxByOrNull { it.completedAtMs }?.completedAtMs
        val lastUpload = history.filter { it.outcome == SyncOutcome.UPLOAD_ACCEPTED }.maxByOrNull { it.completedAtMs }?.completedAtMs
        return """
            {
              "report_version": 1,
              "generated_at_utc": ${timestamp(generatedAtMs)},
              "app_version": ${quote(snapshot.appVersion)},
              "android_api": ${snapshot.androidApi},
              "permissions": {
                "read_call_log": ${snapshot.callLogGranted},
                "read_phone_state": ${snapshot.phoneStateGranted},
                "camera": ${snapshot.cameraGranted},
                "notifications": ${snapshot.notificationsGranted ?: "null"}
              },
              "battery_unrestricted": ${snapshot.batteryUnrestricted},
              "binding": {
                "locally_configured": ${snapshot.locallyBound},
                "receiver_is_winlog": ${snapshot.receiverIsWinLog},
                "server_binding_verified": null
              },
              "sync": {
                "history_limit": $HISTORY_LIMIT,
                "last_recorded_attempt_utc": ${lastAttempt?.let { timestamp(it) } ?: "null"},
                "last_upload_accepted_in_history_utc": ${lastUpload?.let { timestamp(it) } ?: "null"},
                "recent_outcomes": [${history.joinToString(",") { "{\"completed_at_utc\":${timestamp(it.completedAtMs)},\"outcome\":${quote(it.outcome.name.lowercase())}}" }}]
              }
            }
        """.trimIndent()
    }
}
