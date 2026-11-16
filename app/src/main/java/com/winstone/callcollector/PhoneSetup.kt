package com.winstone.callcollector

import org.json.JSONObject

data class PhoneSetup(val employeeId: String, val receiver: String)

object PhoneSetupValidator {
 const val RECEIVER = "https://winlogofficial.winstonebd.com/api/public/ingest-call-logs"
 fun validate(employee: String, receiver: String): PhoneSetup {
  val id = employee.trim().uppercase(java.util.Locale.ROOT)
  require(id.length in 1..64 && id.matches(Regex("^[A-Z0-9_-]+$")))
  require(receiver.trim() == RECEIVER)
  return PhoneSetup(id, RECEIVER)
 }
 fun parseQr(contents: String): PhoneSetup {
  val payload = JSONObject(contents)
  require(payload.keys().asSequence().toSet() == setOf("type", "version", "employee_id", "ingest_url"))
  require(payload.get("type") == "winlog-phone-setup")
  require(payload.get("version") is Int && payload.getInt("version") == 1)
  val id = payload.get("employee_id")
  val url = payload.get("ingest_url")
  require(id is String && url is String)
  require(id.length in 1..64 && id.matches(Regex("^[A-Z0-9_-]+$")))
  require(url == RECEIVER)
  return validate(id, url)
 }
}
