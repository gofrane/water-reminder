package com.waterreminder.app

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class ReminderPlanTest {
    @Test fun repeatedHourRemainsActiveUntilLaterEnd() {
        val now = at("2026-10-25T02:00:00+01:00[Europe/Berlin]")
        val p = prefs.copy(start = 60, end = 150, interval = 30)
        assertTrue(ReminderPlan.inWindow(now, p))
        assertEquals(now.toInstant(), ReminderPlan.next(now, p, false, now.minusMinutes(40).toInstant()))
        assertFalse(ReminderPlan.inWindow(now.withMinute(30), p))
    }

    @Test fun overlappingStartAndEndDefineOneContinuousWindow() {
        val p = prefs.copy(start = 135, end = 150, interval = 30)
        val first = at("2026-10-25T02:20:00+02:00[Europe/Berlin]")
        val second = at("2026-10-25T02:10:00+01:00[Europe/Berlin]")
        assertTrue(ReminderPlan.inWindow(first, p))
        assertTrue(ReminderPlan.inWindow(second, p))
        assertEquals(second.toInstant(), ReminderPlan.next(second, p, false, first.toInstant()))
    }

    @Test fun nonexistentStartClampsToFirstValidTime() {
        val now = at("2026-03-29T01:00:00+01:00[Europe/Berlin]")
        val p = prefs.copy(start = 150, end = 240, interval = 30)
        val expected = at("2026-03-29T03:00:00+02:00[Europe/Berlin]")
        assertEquals(expected.toInstant(), ReminderPlan.next(now, p, false, now.toInstant()))
        assertTrue(ReminderPlan.inWindow(expected, p))
    }

    @Test fun fullySkippedWindowMovesToNextDay() {
        val now = at("2026-03-29T01:00:00+01:00[Europe/Berlin]")
        val p = prefs.copy(start = 130, end = 150, interval = 30)
        assertEquals(at("2026-03-30T02:10:00+02:00[Europe/Berlin]").toInstant(),
            ReminderPlan.next(now, p, false, now.toInstant()))
        assertFalse(ReminderPlan.inWindow(now.withHour(3), p))
    }

    private val prefs = Preferences(remindersEnabled = true)
    private fun at(time: String) = ZonedDateTime.parse(time)
    private val morning = at("2026-10-20T09:00:00+02:00[Europe/Berlin]")

    @Test fun disabledHasNoAlarm() {
        assertNull(ReminderPlan.next(morning, prefs.copy(remindersEnabled = false), false, morning.toInstant()))
    }

    @Test fun drinkingRestartsInterval() {
        assertEquals(morning.plusHours(1).toInstant(), ReminderPlan.next(morning, prefs, false, morning.toInstant()))
        val afterDrink = morning.plusMinutes(25)
        assertEquals(afterDrink.plusHours(1).toInstant(), ReminderPlan.next(afterDrink, prefs, false, afterDrink.toInstant()))
    }

    @Test fun beforeWindowAndGoalReachedUseCorrectDay() {
        val early = morning.withHour(6)
        assertEquals(morning.withHour(8).toInstant(), ReminderPlan.next(early, prefs, false, early.toInstant()))
        assertEquals(morning.plusDays(1).withHour(8).toInstant(), ReminderPlan.next(morning, prefs, true, morning.toInstant()))
    }

    @Test fun endIsExclusiveAndLateAlarmsCannotNotifyAtNight() {
        val late = morning.withHour(21).withMinute(30)
        assertEquals(morning.plusDays(1).withHour(8).toInstant(), ReminderPlan.next(late, prefs, false, late.toInstant()))
        assertFalse(ReminderPlan.inWindow(morning.withHour(22), prefs))
        assertFalse(ReminderPlan.inWindow(morning.withHour(7), prefs))
        assertTrue(ReminderPlan.inWindow(morning.withHour(8), prefs))
    }

    @Test fun overdueReminderCanRunWithoutAnotherFullInterval() {
        assertEquals(morning.toInstant(), ReminderPlan.next(morning, prefs, false, morning.minusHours(2).toInstant()))
    }

    @Test fun nextMorningFollowsLocalTimeAcrossDaylightSaving() {
        val beforeClockChange = at("2026-10-24T22:30:00+02:00[Europe/Berlin]")
        val nextMorning = at("2026-10-25T08:00:00+01:00[Europe/Berlin]")
        assertEquals(nextMorning.toInstant(), ReminderPlan.next(beforeClockChange, prefs, false, beforeClockChange.toInstant()))
    }
}
