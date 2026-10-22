package com.waterreminder.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class Drink(val id: String = UUID.randomUUID().toString(), val amount: Int, val timestamp: Long = System.currentTimeMillis()) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}

data class Preferences(
    val goal: Int = 2000,
    val quick: List<Int> = listOf(150, 250, 500),
    val interval: Int = 60,
    val start: Int = 8 * 60,
    val end: Int = 22 * 60,
    val remindersEnabled: Boolean = false
)

object Hydration {
    fun total(drinks: List<Drink>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        drinks.filter { it.date(zone) == date }.sumOf { it.amount }

    fun validAmount(value: Int?) = value != null && value in 10..5000
    fun validGoal(value: Int?) = value != null && value in 250..10000
    fun validWindow(start: Int, end: Int) = start in 0..1439 && end in 0..1439 && start < end

    fun average(drinks: List<Drink>, dates: List<LocalDate>, firstDay: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (firstDay == null) return 0
        val available = dates.filter { !it.isBefore(firstDay) }
        return if (available.isEmpty()) 0 else available.sumOf { total(drinks, it, zone) } / available.size
    }
}
