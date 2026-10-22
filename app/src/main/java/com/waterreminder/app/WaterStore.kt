package com.waterreminder.app

import android.content.Context
import java.time.LocalDate

/** One private snapshot keeps settings, goal history, and entries consistent. */
class WaterStore(context: Context) {
    private val storage = context.getSharedPreferences("hydration", Context.MODE_PRIVATE)
    private val raw = storage.getString("snapshot", "{}") ?: "{}"
    private var snapshot = WaterSnapshot.decode(raw)
    val preferences get() = snapshot.preferences
    val drinks get() = snapshot.drinks
    val firstDay get() = snapshot.firstDay()
    val recoveryNotice: Boolean
    private var canSave = true
    val recoveryMessage get() = if (canSave)
        "Some saved data could not be read. Valid entries were recovered and the original was kept on this phone."
        else "Saved data could not be repaired. Changes cannot be saved right now; the original data has been kept."

    init {
        if (snapshot.recovered) {
            // Preserve the first damaged original before writing repaired data.
            val edit = storage.edit().putBoolean("recovery_notice", true)
            if (!storage.contains("recovery_snapshot")) edit.putString("recovery_snapshot", raw)
            canSave = edit.commit()
        }
        recoveryNotice = snapshot.recovered || storage.getBoolean("recovery_notice", false)
        // Persist migration immediately, even when today's goal already exists.
        save()
    }

    fun goalFor(day: LocalDate): Int = snapshot.goals.keys.filter { it <= day }.maxOrNull()
        ?.let { snapshot.goals.getValue(it) } ?: 2000

    fun refreshDay(day: LocalDate = LocalDate.now()) {
        if (day !in snapshot.goals) {
            snapshot = snapshot.copy(goals = snapshot.goals + (day to preferences.goal))
            save()
        }
    }

    fun add(amount: Int): Drink {
        require(Hydration.validAmount(amount))
        refreshDay()
        val drink = Drink(amount = amount)
        snapshot = snapshot.copy(drinks = drinks + drink,
            trackingStartedAt = snapshot.trackingStartedAt?.coerceAtMost(drink.timestamp) ?: drink.timestamp)
        save()
        return drink
    }

    fun delete(id: String) { snapshot = snapshot.copy(drinks = drinks.filterNot { it.id == id }); save() }

    fun update(next: Preferences) {
        require(Hydration.validGoal(next.goal) && next.quick.size == 3 && next.quick.all { Hydration.validAmount(it) })
        require(next.interval in listOf(30, 60, 90, 120) && Hydration.validWindow(next.start, next.end))
        snapshot = snapshot.copy(preferences = next, goals = snapshot.goals + (LocalDate.now() to next.goal))
        save()
    }

    private fun save() {
        if (canSave) storage.edit().putString("snapshot", snapshot.encode()).apply()
    }
}
