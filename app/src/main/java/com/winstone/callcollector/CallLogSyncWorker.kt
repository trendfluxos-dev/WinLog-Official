package com.winstone.callcollector

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object CollectorPrefs {
    private const val PREFS_NAME = "winstone_collector_prefs"
    private const val KEY_EMPLOYEE_ID = "employee_id"
    private const val KEY_ENDPOINT_URL = "endpoint_url"
    private const val KEY_LAST_SYNCED_TS = "last_synced_call_ts"
    private const val KEY_LAST_SYNC_AT = "last_sync_at_ms"
    private const val KEY_LAST_SYNC_STATUS = "last_sync_status"

    const val DEFAULT_INGEST_URL = "https://winlogofficial.winstonebd.com/api/public/ingest-call-logs"
    val DEVICE_API_KEY: String = BuildConfig.DEVICE_API_KEY

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEmployeeId(context: Context): String =
        prefs(context).getString(KEY_EMPLOYEE_ID, "") ?: ""

    fun setEmployeeId(context: Context, employeeId: String) {
        prefs(context).edit().putString(KEY_EMPLOYEE_ID, employeeId.trim().uppercase()).apply()
    }

    fun getEndpointUrl(context: Context): String =
        prefs(context).getString(KEY_ENDPOINT_URL, DEFAULT_INGEST_URL) ?: DEFAULT_INGEST_URL

    fun setEndpointUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ENDPOINT_URL, url.trim()).apply()
    }

    fun getLastSyncedCallTs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNCED_TS, 0L)

    fun setLastSyncedCallTs(context: Context, timestampMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNCED_TS, timestampMs).apply()
    }

    fun getLastSyncAtMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_AT, 0L)

    fun getLastSyncStatus(context: Context): String =
        prefs(context).getString(KEY_LAST_SYNC_STATUS, "Never synced") ?: "Never synced"

    fun setSyncOutcome(context: Context, status: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .putString(KEY_LAST_SYNC_STATUS, status)
            .apply()
    }

    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
}

class CallLogSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val employeeId = CollectorPrefs.getEmployeeId(applicationContext)
        val endpointUrl = CollectorPrefs.getEndpointUrl(applicationContext)
        if (employeeId.isBlank() || endpointUrl.isBlank() || CollectorPrefs.DEVICE_API_KEY.isBlank()) {
            CollectorPrefs.setSyncOutcome(applicationContext, "Set Employee ID and reinstall the configured WinLog app")
            return@withContext Result.success()
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            CollectorPrefs.setSyncOutcome(applicationContext, "Missing READ_CALL_LOG permission")
            return@withContext Result.failure()
        }

        val lastSyncedTs = CollectorPrefs.getLastSyncedCallTs(applicationContext)
        val (callsArray, maxTimestampMs) = readDeltaCallLogs(lastSyncedTs)

        if (callsArray.length() == 0) {
            CollectorPrefs.setSyncOutcome(applicationContext, "Up to date (0 new calls)")
            return@withContext Result.success()
        }

        val payload = JSONObject().apply {
            put("android_id", CollectorPrefs.getAndroidId(applicationContext))
            put("employee_id", employeeId)
            put("app_version", "1.0.0")
            put("calls", callsArray)
        }

        val responseCode = postToWinLog(endpointUrl, payload.toString())
        if (responseCode in 200..299) {
            CollectorPrefs.setLastSyncedCallTs(applicationContext, maxTimestampMs)
            CollectorPrefs.setSyncOutcome(
                applicationContext,
                "Synced ${callsArray.length()} call(s)"
            )
            Result.success()
        } else {
            CollectorPrefs.setSyncOutcome(applicationContext, "HTTP $responseCode (retrying)")
            Result.retry()
        }
    }

    private fun readDeltaCallLogs(sinceTimestampMs: Long): Pair<JSONArray, Long> {
        val results = JSONArray()
        var maxTs = sinceTimestampMs

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(sinceTimestampMs.toString())
        val sortOrder = "${CallLog.Calls.DATE} ASC"

        applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val accountIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

            while (cursor.moveToNext()) {
                val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                val callType = if (typeIdx >= 0) cursor.getInt(typeIdx) else 0
                val callDateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                val durationSec = if (durationIdx >= 0) cursor.getInt(durationIdx) else 0
                val simAccountId = if (accountIdx >= 0) cursor.getString(accountIdx) ?: "" else ""

                if (rawNumber.isBlank() || callDateMs <= 0L) continue

                val item = JSONObject().apply {
                    put("phone_number", rawNumber)
                    put("normalized_phone", normalizeBdPhone(rawNumber))
                    put("call_type", callType)
                    put("duration_seconds", durationSec)
                    put("call_timestamp_ms", callDateMs)
                    put("sim_account_id", simAccountId)
                }
                results.put(item)
                if (callDateMs > maxTs) maxTs = callDateMs
            }
        }

        return Pair(results, maxTs)
    }

    private fun normalizeBdPhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9+]"), "")
        return when {
            digits.startsWith("+8801") && digits.length == 14 -> digits
            digits.startsWith("8801") && digits.length == 13 -> "+$digits"
            digits.startsWith("01") && digits.length == 11 -> "+88$digits"
            else -> digits
        }
    }

    private fun postToWinLog(endpointUrl: String, jsonBody: String): Int {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-device-api-key", CollectorPrefs.DEVICE_API_KEY)
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }
            connection.responseCode
        } catch (_: Exception) {
            -1
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "winstone_periodic_call_sync"
        private const val IMMEDIATE_WORK_NAME = "winstone_immediate_call_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CallLogSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateSync(context: Context, delaySeconds: Long = 3L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CallLogSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

class CallEndedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                    CallLogSyncWorker.enqueueImmediateSync(context, delaySeconds = 3L)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                CallLogSyncWorker.schedulePeriodicSync(context)
                CallLogSyncWorker.enqueueImmediateSync(context, delaySeconds = 10L)
            }
        }
    }
}
