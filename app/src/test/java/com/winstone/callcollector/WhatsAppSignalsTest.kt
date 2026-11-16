package com.winstone.callcollector

import org.junit.Assert.*
import org.junit.Test

class WhatsAppSignalsTest {
 @Test fun disabledDetectionRejectsWhatsApp() { assertFalse(WhatsAppSignalTracker.accepts(false, "com.whatsapp", "call")) }
 @Test fun onlyWhatsAppCallMetadataAccepted() {
  assertTrue(WhatsAppSignalTracker.accepts(true, "com.whatsapp", "call"))
  assertTrue(WhatsAppSignalTracker.accepts(true, "com.whatsapp.w4b", "call"))
  assertFalse(WhatsAppSignalTracker.accepts(true, "com.whatsapp", "msg"))
  assertFalse(WhatsAppSignalTracker.accepts(true, "com.whatsapp", null))
  assertFalse(WhatsAppSignalTracker.accepts(true, "com.other", "call"))
 }
 @Test fun updatesDoNotDuplicateNotification() {
  val tracker = WhatsAppSignalTracker(); tracker.posted("private-key", 100); tracker.posted("private-key", 200)
  assertEquals(listOf(WhatsAppSignal(100)), tracker.snapshot())
 }
 @Test fun removalIsNotVerifiedCallDuration() {
  val tracker = WhatsAppSignalTracker(); tracker.posted("k", 100); tracker.removed("k", 300)
  assertEquals(listOf(WhatsAppSignal(100, 300)), tracker.snapshot())
  val storedFields = WhatsAppSignal::class.java.declaredFields.filter {
   !java.lang.reflect.Modifier.isStatic(it.modifiers) && !it.isSynthetic
  }.map { it.name }.toSet()
  assertEquals(setOf("observedAtMs", "removedAtMs"), storedFields)
 }
 @Test fun historyIsBoundedAndClearErasesEverything() {
  val tracker = WhatsAppSignalTracker(); (1..25).forEach { tracker.posted("$it", it.toLong()) }
  assertEquals(20, tracker.snapshot().size); assertEquals(6L, tracker.snapshot().first().observedAtMs)
  tracker.clear(); tracker.removed("25", 30); assertTrue(tracker.snapshot().isEmpty())
 }
}
