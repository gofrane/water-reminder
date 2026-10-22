package com.waterreminder.app

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WaterSnapshotTest {
    private val utc = ZoneId.of("UTC")
    private val time = Instant.parse("2026-10-20T00:30:00Z").toEpochMilli()
    private val drink = Drink("one", 1000, time)

    @Test fun timezoneChangesKeepAverageAndFirstDayConsistent() {
        val snapshot = WaterSnapshot(drinks = listOf(drink), trackingStartedAt = time)
        for (zoneName in listOf("America/Los_Angeles", "Asia/Tokyo")) {
            val zone = ZoneId.of(zoneName)
            val day = drink.date(zone)
            assertEquals(day, snapshot.firstDay(zone))
            assertEquals(1000, Hydration.average(snapshot.drinks, listOf(day), snapshot.firstDay(zone), zone))
        }
    }

    @Test fun deletedFirstDrinkDoesNotResetTrackingStart() {
        val snapshot = WaterSnapshot(drinks = listOf(drink), trackingStartedAt = time)
        val decoded = WaterSnapshot.decode(snapshot.copy(drinks = emptyList()).encode(), utc)
        assertEquals(time, decoded.trackingStartedAt)
        assertEquals(LocalDate.parse("2026-10-20"), decoded.firstDay(utc))
        assertFalse(decoded.recovered)
    }

    @Test fun legacySnapshotMigratesUsingExactFirstDrink() {
        val decoded = WaterSnapshot.decode("""{"firstDay":"2026-10-20","drinks":[{"id":"one","amount":1000,"time":$time}]}""", utc)
        assertEquals(time, decoded.trackingStartedAt)
        assertFalse(decoded.recovered)
        assertEquals(decoded, WaterSnapshot.decode(decoded.encode(), utc))
    }

    @Test fun legacyDeletedHistoryPreservesEarlierStartDay() {
        val decoded = WaterSnapshot.decode("""{"firstDay":"2026-10-18","drinks":[{"id":"one","amount":1000,"time":$time}]}""", utc)
        assertEquals(LocalDate.parse("2026-10-18"), decoded.firstDay(utc))
    }

    @Test fun malformedSnapshotHasExplicitRecoveryFlag() {
        assertTrue(WaterSnapshot.decode("{broken", utc).recovered)
        assertFalse(WaterSnapshot.decode("{}", utc).recovered)
    }

    @Test fun invalidSettingsAreRepairedWithoutLosingValidDrinks() {
        val decoded = WaterSnapshot.decode("""{"goal":0,"quick":[10,0,500],"interval":-1,"start":1300,"end":100,
            "firstDay":"bad-date","drinks":[{"id":"one","amount":1000,"time":$time},
            {"id":"bad","amount":-1,"time":$time},{"id":"one","amount":100,"time":$time}],
            "goals":{"bad":2000,"2026-10-19":0,"2026-10-20":2500}}""", utc)
        assertTrue(decoded.recovered)
        assertEquals(Preferences(), decoded.preferences)
        assertEquals(listOf(drink), decoded.drinks)
        assertEquals(mapOf(LocalDate.parse("2026-10-20") to 2500), decoded.goals)
        assertEquals(time, decoded.trackingStartedAt)
    }

    @Test fun wrongTypesAndFractionalValuesAreNotAccepted() {
        val decoded = WaterSnapshot.decode("""{"goal":2500.5,"quick":"bad","remindersEnabled":"true",
            "drinks":{},"goals":[],"trackingStartedAt":"bad"}""", utc)
        assertTrue(decoded.recovered)
        assertEquals(Preferences(), decoded.preferences)
        assertTrue(decoded.drinks.isEmpty())
    }

    @Test fun completeSnapshotRoundTripsWithHistoricalGoals() {
        val snapshot = WaterSnapshot(Preferences(goal = 2500, remindersEnabled = true), listOf(drink),
            mapOf(LocalDate.parse("2026-10-19") to 2000, LocalDate.parse("2026-10-20") to 2500), time)
        assertEquals(snapshot, WaterSnapshot.decode(snapshot.encode(), utc))
    }
}
