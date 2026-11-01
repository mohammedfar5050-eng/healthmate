package net.orbitratech.healthmate.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date helpers shared by the data and UI layers. */
object DateUtils {

    private val dbFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd
    private val longFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val mediumFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val shortFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    /** Earliest date a record may be logged for. */
    val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)

    fun toDbString(date: LocalDate): String = date.format(dbFormat)

    fun fromDbString(value: String): LocalDate = LocalDate.parse(value, dbFormat)

    /** `18 August 2026` */
    fun formatLong(date: LocalDate): String = date.format(longFormat)

    /** `Tue, 18 Aug 2026` */
    fun formatMedium(date: LocalDate): String = date.format(mediumFormat)

    /** `18 Aug` */
    fun formatShort(date: LocalDate): String = date.format(shortFormat)

    /** `Tue` */
    fun formatWeekday(date: LocalDate): String = date.format(weekdayFormat)

    /** "Today", "Yesterday" or a medium date. */
    fun friendlyLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> formatMedium(date)
    }

    fun greeting(time: LocalTime): String = when {
        time.hour < 12 -> "Good morning"
        time.hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    /** Material date pickers work with UTC epoch milliseconds. */
    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
