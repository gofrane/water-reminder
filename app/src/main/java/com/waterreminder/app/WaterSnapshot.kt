package com.waterreminder.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Versioned persistence codec, independent of Android for recovery tests. */
data class WaterSnapshot(
    val preferences: Preferences = Preferences(),
    val drinks: List<Drink> = emptyList(),
    val goals: Map<LocalDate, Int> = emptyMap(),
    val trackingStartedAt: Long? = null,
    val recovered: Boolean = false
) {
    fun firstDay(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        trackingStartedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    fun encode(): String = JSONObject().put("version", 2)
        .put("goal", preferences.goal).put("quick", JSONArray(preferences.quick))
        .put("interval", preferences.interval).put("start", preferences.start).put("end", preferences.end)
        .put("remindersEnabled", preferences.remindersEnabled)
        .put("trackingStartedAt", trackingStartedAt ?: JSONObject.NULL)
        .put("goals", JSONObject().apply { goals.forEach { (day, goal) -> put(day.toString(), goal) } })
        .put("drinks", JSONArray().apply { drinks.forEach {
            put(JSONObject().put("id", it.id).put("amount", it.amount).put("time", it.timestamp))
        } }).toString()

    companion object {
        fun decode(raw: String, zone: ZoneId = ZoneId.systemDefault()): WaterSnapshot {
            val data = try { JSONObject(raw) } catch (_: Exception) { return WaterSnapshot(recovered = true) }
            var recovered = false
            fun <T> read(fallback: T, block: () -> T): T = try { block() } catch (_: Exception) {
                recovered = true; fallback
            }
            fun integer(value: Any): Int {
                val n = value as Number
                return n.toInt().also { require(n.toDouble() == it.toDouble()) }
            }
            fun timestamp(value: Any): Long {
                val n = value as Number
                return n.toLong().also {
                    require(n.toDouble() == it.toDouble() && it >= 0)
                    require(Instant.ofEpochMilli(it).atZone(zone).year in 1970..9999)
                }
            }
            fun setting(key: String, default: Int, valid: (Int) -> Boolean): Int =
                if (!data.has(key)) default else read(default) { integer(data.get(key)).also { require(valid(it)) } }
            val version = setting("version", 1) { it in 1..2 }
            val goal = setting("goal", 2000) { Hydration.validGoal(it) }
            val quick = if (!data.has("quick")) listOf(150, 250, 500) else read(listOf(150, 250, 500)) {
                val array = data.getJSONArray("quick")
                require(array.length() == 3)
                (0..2).map { integer(array.get(it)).also { value -> require(Hydration.validAmount(value)) } }
            }
            val interval = setting("interval", 60) { it in listOf(30, 60, 90, 120) }
            val window = read(480 to 1320) {
                val start = if (data.has("start")) integer(data.get("start")) else 480
                val end = if (data.has("end")) integer(data.get("end")) else 1320
                require(Hydration.validWindow(start, end)); start to end
            }
            val enabled = if (!data.has("remindersEnabled")) false else read(false) { data.get("remindersEnabled") as Boolean }
            val drinks = mutableListOf<Drink>()
            val ids = mutableSetOf<String>()
            val entries = if (!data.has("drinks")) JSONArray() else read(JSONArray()) { data.getJSONArray("drinks") }
            for (i in 0 until entries.length()) {
                val drink = read<Drink?>(null) {
                    val entry = entries.getJSONObject(i)
                    val id = entry.get("id") as String
                    val amount = integer(entry.get("amount"))
                    require(id.isNotBlank() && id !in ids && Hydration.validAmount(amount))
                    Drink(id, amount, timestamp(entry.get("time")))
                }
                if (drink != null) { ids.add(drink.id); drinks.add(drink) }
            }
            val goals = mutableMapOf<LocalDate, Int>()
            val goalData = if (!data.has("goals")) JSONObject() else read(JSONObject()) { data.getJSONObject("goals") }
            goalData.keys().forEach { key ->
                val entry = read<Pair<LocalDate, Int>?>(null) {
                    val day = LocalDate.parse(key)
                    val value = integer(goalData.get(key))
                    require(Hydration.validGoal(value)); day to value
                }
                if (entry != null) goals[entry.first] = entry.second
            }
            var first = if (data.has("trackingStartedAt") && !data.isNull("trackingStartedAt"))
                read<Long?>(null) { timestamp(data.get("trackingStartedAt")) } else null
            val earliest = drinks.minOfOrNull { it.timestamp }
            if (first == null) {
                // Use surviving exact timestamps when possible. If older drinks
                // were deleted, approximate the legacy day in the current zone.
                val legacy = data.optString("firstDay").takeIf { it.isNotEmpty() }?.let {
                    read<LocalDate?>(null) { LocalDate.parse(it).also { d -> require(d.year in 1970..9999) } }
                }
                first = if (legacy != null && (earliest == null || legacy < Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate()))
                    legacy.atStartOfDay(zone).toInstant().toEpochMilli() else earliest
                if (version == 2 && earliest != null) recovered = true
            }
            if (first != null && earliest != null && first > earliest) { recovered = true; first = earliest }
            return WaterSnapshot(Preferences(goal, quick, interval, window.first, window.second, enabled), drinks, goals, first, recovered)
        }
    }
}
