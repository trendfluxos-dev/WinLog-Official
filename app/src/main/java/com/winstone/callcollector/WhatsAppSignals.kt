package com.winstone.callcollector

/** Metadata only: a notification disappearing does not prove a call ended. */
data class WhatsAppSignal(val observedAtMs: Long, val removedAtMs: Long? = null)

class WhatsAppSignalTracker(initial: List<WhatsAppSignal> = emptyList()) {
 companion object {
  const val LIMIT = 20
  fun accepts(enabled: Boolean, packageName: String, category: String?): Boolean =
   enabled && packageName in setOf("com.whatsapp", "com.whatsapp.w4b") && category == "call"
 }
 private val active = mutableMapOf<String, WhatsAppSignal>()
 private val history = initial.takeLast(LIMIT).toMutableList()
 fun posted(key: String, at: Long) {
  if (at <= 0 || active.containsKey(key)) return
  val signal = WhatsAppSignal(at)
  active[key] = signal
  history.add(signal)
  while (history.size > LIMIT) {
   val dropped = history.removeAt(0)
   active.entries.removeAll { it.value === dropped }
  }
 }
 fun removed(key: String, at: Long) {
  val signal = active.remove(key) ?: return
  val i = history.indexOfFirst { it === signal }
  if (i >= 0 && at >= signal.observedAtMs) history[i] = signal.copy(removedAtMs = at)
 }
 fun snapshot(): List<WhatsAppSignal> = history.toList()
 fun clear() { active.clear(); history.clear() }
}
