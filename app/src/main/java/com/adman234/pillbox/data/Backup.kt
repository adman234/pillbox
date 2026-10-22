package com.adman234.pillbox.data

import android.content.Context
import android.net.Uri
import com.adman234.pillbox.app
import com.adman234.pillbox.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Local export and import, since there is no account to hold a cloud copy. */
object Backup {
    private const val VERSION = 1

    suspend fun exportJson(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val app = ctx.app
        val root = JSONObject().put("version", VERSION).put("app", "pillbox")
        root.put("medications", JSONArray().also { a -> app.db.meds().getAll().forEach { a.put(medToJson(it)) } })
        root.put("events", JSONArray().also { a -> app.db.doses().all().forEach { a.put(eventToJson(it)) } })
        root.put("settings", settingsToJson(app.settings.current()))
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
    }

    /** Replaces everything. Returns false if the file is not a Pillbox backup. */
    suspend fun importJson(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext false
        val root = try { JSONObject(text) } catch (_: Exception) { return@withContext false }
        if (root.optString("app") != "pillbox") return@withContext false
        val app = ctx.app
        val meds = root.optJSONArray("medications") ?: JSONArray()
        val events = root.optJSONArray("events") ?: JSONArray()
        // Parse everything before touching the database so a bad file cannot wipe it.
        val parsedMeds = (0 until meds.length()).map { jsonToMed(meds.getJSONObject(it)) }
        val parsedEvents = (0 until events.length()).map { jsonToEvent(events.getJSONObject(it)) }
        app.db.doses().all().forEach { app.scheduler.deleteEvent(it.id) }
        app.db.meds().clear()
        parsedMeds.forEach { app.db.meds().insert(it) }
        parsedEvents.forEach { app.db.doses().insertReplace(it) }
        root.optJSONObject("settings")?.let { j -> app.settings.update { jsonToSettings(j, it) } }
        app.scheduler.reconcile(rebuildFuture = true)
        true
    }

    suspend fun exportCsv(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val app = ctx.app
        val names = app.db.meds().getAll().associate { it.id to it.name }
        val zone = ZoneId.systemDefault()
        fun iso(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString() } ?: ""
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("medication,status,scheduled,acted,unscheduled\n")
        app.db.doses().all().filter { it.status != DoseStatus.PENDING }
            .sortedBy { it.actedAt ?: it.scheduledAt }
            .forEach { e ->
                sb.append(esc(names[e.medId] ?: "")).append(',').append(e.status).append(',')
                    .append(iso(e.scheduledAt)).append(',').append(iso(e.actedAt)).append(',').append(e.extra).append('\n')
            }
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(sb.toString().toByteArray()) }
    }

    private fun medToJson(m: Medication) = JSONObject()
        .put("id", m.id).put("name", m.name).put("description", m.description).put("amount", m.amount)
        .put("shape", m.shape.name).put("color", m.color).put("reminderEnabled", m.reminderEnabled)
        .put("freq", m.freq.name).put("timesMinutes", m.timesMinutes).put("intervalMinutes", m.intervalMinutes)
        .put("intervalStartMin", m.intervalStartMin).put("intervalEndMin", m.intervalEndMin)
        .put("dayRule", m.dayRule.name).put("weekdayMask", m.weekdayMask).put("everyN", m.everyN)
        .put("reminderType", m.reminderType.name).put("durationDays", m.durationDays)
        .put("startEpochDay", m.startEpochDay).put("reminderSince", m.reminderSince).put("createdAt", m.createdAt)

    private fun jsonToMed(j: JSONObject) = Medication(
        id = j.getLong("id"), name = j.getString("name"),
        description = j.optString("description"), amount = j.optString("amount"),
        shape = enumOr(j.optString("shape"), PillShape.ROUND), color = j.optInt("color"),
        reminderEnabled = j.optBoolean("reminderEnabled", true),
        freq = enumOr(j.optString("freq"), Freq.TIMES), timesMinutes = j.optString("timesMinutes", "480"),
        intervalMinutes = j.optInt("intervalMinutes", 480), intervalStartMin = j.optInt("intervalStartMin", 480),
        intervalEndMin = j.optInt("intervalEndMin", -1),
        dayRule = enumOr(j.optString("dayRule"), DayRule.EVERY_DAY), weekdayMask = j.optInt("weekdayMask", 127),
        everyN = j.optInt("everyN", 2), reminderType = enumOr(j.optString("reminderType"), ReminderType.NOTIFICATION),
        durationDays = j.optInt("durationDays", 0), startEpochDay = j.getLong("startEpochDay"),
        reminderSince = j.optLong("reminderSince", System.currentTimeMillis()),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun eventToJson(e: DoseEvent) = JSONObject()
        .put("id", e.id).put("medId", e.medId).put("scheduledAt", e.scheduledAt).put("status", e.status.name)
        .put("actedAt", e.actedAt ?: JSONObject.NULL).put("nextFireAt", if (e.nextFireAt == NEVER) -1L else e.nextFireAt)
        .put("fireCount", e.fireCount).put("extra", e.extra)

    private fun jsonToEvent(j: JSONObject): DoseEvent {
        val next = j.optLong("nextFireAt", -1)
        return DoseEvent(
            id = j.getLong("id"), medId = j.getLong("medId"), scheduledAt = j.getLong("scheduledAt"),
            status = enumOr(j.optString("status"), DoseStatus.MISSED),
            actedAt = if (j.isNull("actedAt")) null else j.getLong("actedAt"),
            nextFireAt = if (next < 0) NEVER else next,
            fireCount = j.optInt("fireCount"), extra = j.optBoolean("extra"),
        )
    }

    private fun settingsToJson(s: Settings) = JSONObject()
        .put("notifSound", s.notifSound).put("notifVibrate", s.notifVibrate)
        .put("alarmSound", s.alarmSound).put("alarmVibrate", s.alarmVibrate)
        .put("autoRepeat", s.autoRepeat).put("repeatMinutes", s.repeatMinutes).put("snoozeMinutes", s.snoozeMinutes)
        .put("dstAdjust", s.dstAdjust).put("showLastDose", s.showLastDose)

    private fun jsonToSettings(j: JSONObject, cur: Settings) = cur.copy(
        // Sound uris from another phone may not exist here; fall back to the default sound.
        notifSound = j.optString("notifSound").let { if (it.startsWith("content:")) SOUND_DEFAULT else it.ifEmpty { cur.notifSound } },
        notifVibrate = j.optBoolean("notifVibrate", cur.notifVibrate),
        alarmSound = j.optString("alarmSound").let { if (it.startsWith("content:")) SOUND_DEFAULT else it.ifEmpty { cur.alarmSound } },
        alarmVibrate = j.optBoolean("alarmVibrate", cur.alarmVibrate),
        autoRepeat = j.optBoolean("autoRepeat", cur.autoRepeat),
        repeatMinutes = j.optInt("repeatMinutes", cur.repeatMinutes),
        snoozeMinutes = j.optInt("snoozeMinutes", cur.snoozeMinutes),
        dstAdjust = j.optBoolean("dstAdjust", cur.dstAdjust),
        showLastDose = j.optBoolean("showLastDose", cur.showLastDose),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
