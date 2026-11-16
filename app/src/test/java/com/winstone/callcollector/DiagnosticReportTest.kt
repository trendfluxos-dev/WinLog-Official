package com.winstone.callcollector
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class DiagnosticReportTest {
 private fun snapshot() = DiagnosticSnapshot("1.2.0",36,true,false,true,null,true,true,true,listOf(SyncDiagnostic(1000,SyncOutcome.UPLOAD_ACCEPTED),SyncDiagnostic(2000,SyncOutcome.NO_NEW_CALLS)))
 @Test fun exportsVersionPermissionsAndLocalBinding() {
  val report=JSONObject(DiagnosticReport.render(snapshot(),3000))
  assertEquals("1.2.0",report.getString("app_version"))
  assertEquals(36,report.getInt("android_api"))
  assertTrue(report.getJSONObject("permissions").getBoolean("read_call_log"))
  assertFalse(report.getJSONObject("permissions").getBoolean("read_phone_state"))
  assertTrue(report.getJSONObject("permissions").isNull("notifications"))
  assertTrue(report.getJSONObject("binding").getBoolean("locally_configured"))
  assertTrue(report.getJSONObject("binding").isNull("server_binding_verified"))
 }
 @Test fun noNewCallsDoesNotClaimASuccessfulUpload() {
  val sync=JSONObject(DiagnosticReport.render(snapshot(),3000)).getJSONObject("sync")
  assertEquals("1970-01-01T00:00:02Z",sync.getString("last_recorded_attempt_utc"))
  assertEquals("1970-01-01T00:00:01Z",sync.getString("last_upload_accepted_in_history_utc"))
  val empty=snapshot().copy(recentSync=listOf(SyncDiagnostic(2000,SyncOutcome.NO_NEW_CALLS)))
  assertTrue(JSONObject(DiagnosticReport.render(empty,3000)).getJSONObject("sync").isNull("last_upload_accepted_in_history_utc"))
 }
 @Test fun limitsRecentSyncHistory() {
  val history=(1L..25L).map { SyncDiagnostic(it,SyncOutcome.HTTP_ERROR) }
  val bounded=DiagnosticReport.append(history,SyncDiagnostic(26,SyncOutcome.NO_NEW_CALLS))
  assertEquals(20,bounded.size)
  assertEquals(7L,bounded.first().completedAtMs)
  assertEquals(26L,bounded.last().completedAtMs)
 }
 @Test fun excludesIdentifiersSecretsCallDataAndRawErrors() {
  val report=JSONObject(DiagnosticReport.render(snapshot(),3000))
  assertEquals(setOf("report_version","generated_at_utc","app_version","android_api","permissions","battery_unrestricted","binding","sync"),report.keys().asSequence().toSet())
  assertEquals(setOf("locally_configured","receiver_is_winlog","server_binding_verified"),report.getJSONObject("binding").keys().asSequence().toSet())
  val sync=report.getJSONObject("sync").getJSONArray("recent_outcomes")
  assertEquals(setOf("completed_at_utc","outcome"),sync.getJSONObject(0).keys().asSequence().toSet())
  assertEquals(SyncOutcome.UNKNOWN,DiagnosticReport.outcome("01712345678 token=private"))
  assertEquals(SyncOutcome.HTTP_ERROR,DiagnosticReport.outcome("HTTP 401 token=private"))
  assertFalse(report.toString().contains("phone_number"))
  assertFalse(report.toString().contains("device_api_key"))
 }
}
