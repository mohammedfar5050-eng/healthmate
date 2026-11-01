package net.orbitratech.healthmate.core.util

import java.time.LocalDate

/**
 * Pure validation functions used by the forms.
 *
 * Each function returns `null` when the input is valid, otherwise a
 * user-facing error message. Being free of Android dependencies, they are
 * covered by fast JVM unit tests.
 */
object Validators {

    const val MAX_STEPS = 100_000
    const val MAX_CALORIES = 10_000
    const val MAX_WATER_ML = 10_000

    /** Validates a required whole number within [min]..[max]. */
    fun wholeNumber(value: String, fieldName: String, max: Int, min: Int = 0): String? {
        val text = value.trim().replace(",", "")
        if (text.isEmpty()) return "Please enter $fieldName (use 0 if none)"
        if (!text.all { it.isDigit() } && !(text.startsWith("-") && text.drop(1).all { it.isDigit() })) {
            return "$fieldName must be a whole number"
        }
        val number = text.toLongOrNull() ?: return "$fieldName looks too high (max ${Formatters.number(max)})"
        if (number < min) return "$fieldName cannot be less than $min"
        if (number > max) return "$fieldName looks too high (max ${Formatters.number(max)})"
        return null
    }

    fun steps(value: String) = wholeNumber(value, "Steps", MAX_STEPS)

    fun calories(value: String) = wholeNumber(value, "Calories", MAX_CALORIES)

    fun water(value: String) = wholeNumber(value, "Water intake", MAX_WATER_ML)

    /** A record date is required, cannot be in the future or before 2000. */
    fun recordDate(date: LocalDate?, today: LocalDate): String? = when {
        date == null -> "Please select a date"
        date.isAfter(today) -> "Date cannot be in the future"
        date.isBefore(DateUtils.EARLIEST_DATE) -> "Date is too far in the past"
        else -> null
    }

    /** Cross-field rule: an entry must contain at least one non-zero value. */
    fun atLeastOneActivity(steps: Int, calories: Int, water: Int): String? =
        if (steps == 0 && calories == 0 && water == 0) "Enter at least one value greater than 0" else null

    /** Daily goals must be at least 1 and within the metric's limit. */
    fun goal(value: String, max: Int): String? = wholeNumber(value, "Goal", max = max, min = 1)

    /** Parses an already-validated field (tolerates thousands separators). */
    fun parse(value: String): Int = value.trim().replace(",", "").toIntOrNull() ?: 0
}
