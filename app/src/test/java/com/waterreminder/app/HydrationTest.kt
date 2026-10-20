package com.waterreminder.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

class HydrationTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun drink(amount: Int, time: String) = Drink(amount = amount, timestamp = Instant.parse(time).toEpochMilli())

    @Test fun midnightUsesLocalDay() {
        val drinks = listOf(drink(250, "2026-10-20T21:59:00Z"), drink(500, "2026-10-20T22:01:00Z"))
        assertEquals(250, Hydration.total(drinks, LocalDate.parse("2026-10-20"), zone))
        assertEquals(500, Hydration.total(drinks, LocalDate.parse("2026-10-21"), zone))
    }

    @Test fun averageExcludesDaysBeforeFirstUseButIncludesLaterEmptyDays() {
        val dates = (18..20).map { LocalDate.of(2026, 10, it) }
        val drinks = listOf(drink(1000, "2026-10-19T12:00:00Z"))
        assertEquals(500, Hydration.average(drinks, dates, dates[1], zone))
        assertEquals(0, Hydration.average(emptyList(), dates, null, zone))
    }

    @Test fun validatesInputsAndRejectsOvernightWindows() {
        assertFalse(Hydration.validAmount(null))
        assertFalse(Hydration.validAmount(0))
        assertTrue(Hydration.validAmount(5000))
        assertFalse(Hydration.validAmount(5001))
        assertFalse(Hydration.validGoal(249))
        assertTrue(Hydration.validGoal(10000))
        assertTrue(Hydration.validWindow(480, 1320))
        assertFalse(Hydration.validWindow(1320, 480))
        assertFalse(Hydration.validWindow(480, 480))
    }
}
