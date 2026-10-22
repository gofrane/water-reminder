package com.waterreminder.app

import java.time.Instant
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.ZoneId

/** End time is exclusive. Local calendar boundaries also handle daylight-saving changes. */
object ReminderPlan {
    // Repeated hours belong to one continuous window. Nonexistent times clamp to
    // the first valid instant after the gap, rather than shifting minutes forward.
    private fun boundary(day: LocalDate, minute: Int, zone: ZoneId, end: Boolean): Instant {
        val local = day.atTime(minute / 60, minute % 60)
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.isEmpty() -> zone.rules.getTransition(local)!!.instant
            else -> local.toInstant(if (end) offsets.last() else offsets.first())
        }
    }

    fun inWindow(now: ZonedDateTime, preferences: Preferences): Boolean {
        val instant = now.toInstant()
        return instant >= boundary(now.toLocalDate(), preferences.start, now.zone, false) &&
            instant < boundary(now.toLocalDate(), preferences.end, now.zone, true)
    }

    fun next(now: ZonedDateTime, preferences: Preferences, goalReached: Boolean, anchor: Instant): Instant? {
        if (!preferences.remindersEnabled) return null
        val day = now.toLocalDate()
        // Skip a day whose entire selected window disappears in a clock jump.
        var offset = if (goalReached) 1L else 0L
        while (true) {
            val date = day.plusDays(offset)
            val start = boundary(date, preferences.start, now.zone, false)
            val end = boundary(date, preferences.end, now.zone, true)
            val candidate = if (offset == 0L)
                maxOf(start, anchor.plusSeconds(preferences.interval * 60L), now.toInstant()) else start
            if (candidate < end && candidate >= now.toInstant()) return candidate
            offset++
        }
    }
}
