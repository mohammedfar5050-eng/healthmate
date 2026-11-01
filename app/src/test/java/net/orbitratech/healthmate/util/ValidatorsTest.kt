package net.orbitratech.healthmate.util

import net.orbitratech.healthmate.core.database.SampleData
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.core.util.Formatters
import net.orbitratech.healthmate.core.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidatorsTest {

    private val today = LocalDate.of(2026, 8, 18)

    @Test fun emptyValue_isRejected() {
        assertEquals("Please enter Steps (use 0 if none)", Validators.steps(""))
        assertEquals("Please enter Water intake (use 0 if none)", Validators.water("  "))
    }

    @Test fun nonNumericValue_isRejected() {
        assertEquals("Steps must be a whole number", Validators.steps("abc"))
        assertEquals("Steps must be a whole number", Validators.steps("12.5"))
    }

    @Test fun outOfRangeValues_areRejected() {
        assertEquals("Steps cannot be less than 0", Validators.steps("-1"))
        assertEquals("Steps looks too high (max 100,000)", Validators.steps("100001"))
        assertEquals("Water intake looks too high (max 10,000)", Validators.water("10001"))
        assertEquals("Calories looks too high (max 10,000)", Validators.calories("99999999999999999999"))
    }

    @Test fun validValues_pass_includingThousandsSeparators() {
        assertNull(Validators.steps("7850"))
        assertNull(Validators.steps("7,850"))
        assertNull(Validators.calories("420"))
        assertNull(Validators.water("0"))
        assertEquals(7850, Validators.parse("7,850"))
    }

    @Test fun recordDate_cannotBeMissingOrInFuture() {
        assertEquals("Please select a date", Validators.recordDate(null, today))
        assertEquals("Date cannot be in the future", Validators.recordDate(today.plusDays(1), today))
        assertNull(Validators.recordDate(today, today))
    }

    @Test fun atLeastOneActivity_isRequired() {
        assertNotNull(Validators.atLeastOneActivity(0, 0, 0))
        assertNull(Validators.atLeastOneActivity(0, 0, 250))
    }

    @Test fun goals_mustBePositive() {
        assertEquals("Goal cannot be less than 1", Validators.goal("0", 100))
        assertNull(Validators.goal("50", 100))
    }

    @Test fun dateUtils_formatsAndParses() {
        assertEquals("2026-02-05", DateUtils.toDbString(LocalDate.of(2026, 2, 5)))
        assertEquals(LocalDate.of(2026, 2, 5), DateUtils.fromDbString("2026-02-05"))
        assertEquals("18 August 2026", DateUtils.formatLong(today))
        assertEquals("Today", DateUtils.friendlyLabel(today, today))
        assertEquals("Yesterday", DateUtils.friendlyLabel(today.minusDays(1), today))
        assertEquals("Mon, 10 Aug 2026", DateUtils.friendlyLabel(LocalDate.of(2026, 8, 10), today))
        assertEquals(today, DateUtils.fromPickerMillis(DateUtils.toPickerMillis(today)))
    }

    @Test fun formatters() {
        assertEquals("7,850", Formatters.number(7850))
        assertEquals("12.5K", Formatters.compact(12500))
        assertEquals("16K", Formatters.compact(16000))
        assertEquals("850", Formatters.compact(850))
        assertEquals("73%", Formatters.percent(0.734f))
    }

    @Test fun sampleData_isDeterministic_andNeverInFuture() {
        val a = SampleData.generate(today)
        assertEquals(a, SampleData.generate(today))
        assertTrue(a.all { !DateUtils.fromDbString(it.date).isAfter(today) })
        assertEquals(7850, a.filter { it.date == "2026-08-18" }.sumOf { it.steps })
    }
}
