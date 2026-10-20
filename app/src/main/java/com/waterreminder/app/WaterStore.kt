package com.waterreminder.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** One private snapshot keeps settings, goal history, and entries consistent. */
class WaterStore(context: Context) {
    private val storage = context.getSharedPreferences("hydration", Context.MODE_PRIVATE)
    private val data = JSONObject(storage.getString("snapshot", "{}") ?: "{}")
    var preferences = Preferences(
        goal = data.optInt("goal", 2000),
        quick = data.optJSONArray("quick")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: listOf(150, 250, 500),
        interval = data.optInt("interval", 60), start = data.optInt("start", 480), end = data.optInt("end", 1320)
    )
        private set
    var drinks = data.optJSONArray("drinks")?.let { a ->
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Drink(it.getString("id"), it.getInt("amount"), it.getLong("time")) } }
    } ?: emptyList()
        private set
    private val goals = data.optJSONObject("goals") ?: JSONObject()
    var firstDay: LocalDate? = data.optString("firstDay").takeIf { it.isNotEmpty() }?.let(LocalDate::parse)
        private set

    fun goalFor(day: LocalDate): Int {
        // The most recent earlier goal also covers days without a launch or drink.
        val key = goals.keys().asSequence().filter { it <= day.toString() }.maxOrNull()
        return key?.let { goals.getInt(it) } ?: 2000
    }

    fun refreshDay(day: LocalDate = LocalDate.now()) {
        if (!goals.has(day.toString())) { goals.put(day.toString(), preferences.goal); save() }
    }

    fun add(amount: Int): Drink {
        require(Hydration.validAmount(amount))
        refreshDay()
        val drink = Drink(amount = amount)
        drinks = drinks + drink
        if (firstDay == null) firstDay = drink.date()
        save()
        return drink
    }

    fun delete(id: String) { drinks = drinks.filterNot { it.id == id }; save() }

    fun update(next: Preferences) {
        require(Hydration.validGoal(next.goal) && next.quick.size == 3 && next.quick.all { Hydration.validAmount(it) })
        require(next.interval in listOf(30, 60, 90, 120) && Hydration.validWindow(next.start, next.end))
        preferences = next
        goals.put(LocalDate.now().toString(), next.goal)
        save()
    }

    private fun save() {
        val snapshot = JSONObject().put("goal", preferences.goal).put("quick", JSONArray(preferences.quick))
            .put("interval", preferences.interval).put("start", preferences.start).put("end", preferences.end)
            .put("goals", goals).put("firstDay", firstDay?.toString() ?: "")
            .put("drinks", JSONArray().apply { drinks.forEach { put(JSONObject().put("id", it.id).put("amount", it.amount).put("time", it.timestamp)) } })
        storage.edit().putString("snapshot", snapshot.toString()).apply()
    }
}
