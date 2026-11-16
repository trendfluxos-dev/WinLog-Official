package com.winstone.callcollector

import org.junit.Assert.*
import org.junit.Test

class PhoneSetupTest {
 private fun qr(version: String = "1", id: String = "\"WIN2604\"", extra: String = "") = """{"type":"winlog-phone-setup","version":$version,"employee_id":$id,"ingest_url":"${PhoneSetupValidator.RECEIVER}"$extra}"""
 private fun rejected(block: () -> Unit) { try { block(); fail("Invalid setup accepted") } catch (_: IllegalArgumentException) {} catch (_: org.json.JSONException) {} }
 @Test fun dashboardQrPreservesBinding() {
  assertEquals(PhoneSetup("WIN2604", "https://winlogofficial.winstonebd.com/api/public/ingest-call-logs"), PhoneSetupValidator.parseQr(qr()))
 }
 @Test fun manualEmployeeIsNormalized() { assertEquals("WIN2604", PhoneSetupValidator.validate(" win2604 ", PhoneSetupValidator.RECEIVER).employeeId) }
 @Test fun invalidManualEmployeesAreRejected() { listOf("", "WIN 2604", "A".repeat(65)).forEach { rejected { PhoneSetupValidator.validate(it, PhoneSetupValidator.RECEIVER) } } }
 @Test fun externalReceiverIsRejected() { rejected { PhoneSetupValidator.validate("WIN2604", "https://example.com/ingest") } }
 @Test fun qrVersionMustBeIntegerOne() { listOf("\"1\"", "1.5", "2").forEach { rejected { PhoneSetupValidator.parseQr(qr(version=it)) } } }
 @Test fun qrCannotContainSecrets() { rejected { PhoneSetupValidator.parseQr(qr(extra=",\"device_key\":\"unexpected\"")) } }
 @Test fun qrEmployeeMustBeAString() { rejected { PhoneSetupValidator.parseQr(qr(id="2604")) } }
}
