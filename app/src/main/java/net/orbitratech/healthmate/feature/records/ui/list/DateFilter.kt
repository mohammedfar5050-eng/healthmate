package net.orbitratech.healthmate.feature.records.ui.list

import net.orbitratech.healthmate.core.util.DateUtils
import java.time.LocalDate

/**
 * Date filter applied to the records list. [bounds] converts it into the
 * inclusive `from`/`to` dates passed down to the SQL query.
 */
sealed interface DateFilter {

    /** Inclusive (from, to) bounds; `null` means unbounded. */
    fun bounds(today: LocalDate): Pair<LocalDate?, LocalDate?>

    /** Short label for chips, e.g. "Last 7 days". */
    val label: String

    /** Phrase for sentences, e.g. "No entries in the last 7 days". */
    val phrase: String

    val isActive: Boolean get() = this != All

    data object All : DateFilter {
        override fun bounds(today: LocalDate) = null to null
        override val label = "All"
        override val phrase = "yet"
    }

    data object Today : DateFilter {
        override fun bounds(today: LocalDate) = today to today
        override val label = "Today"
        override val phrase = "for today"
    }

    data object Last7Days : DateFilter {
        override fun bounds(today: LocalDate) = today.minusDays(6) to today
        override val label = "Last 7 days"
        override val phrase = "in the last 7 days"
    }

    data object Last30Days : DateFilter {
        override fun bounds(today: LocalDate) = today.minusDays(29) to today
        override val label = "Last 30 days"
        override val phrase = "in the last 30 days"
    }

    data class SingleDate(val date: LocalDate) : DateFilter {
        override fun bounds(today: LocalDate) = date to date
        override val label: String get() = DateUtils.formatMedium(date)
        override val phrase: String get() = "on ${DateUtils.formatMedium(date)}"
    }

    data class Range(val start: LocalDate, val end: LocalDate) : DateFilter {
        override fun bounds(today: LocalDate) = start to end
        override val label: String
            get() = "${DateUtils.formatShort(start)} – ${DateUtils.formatShort(end)} ${end.year}"
        override val phrase: String
            get() = "between ${DateUtils.formatShort(start)} and ${DateUtils.formatShort(end)} ${end.year}"
    }

    companion object {
        /**
         * Quick-filter chips. A getter (not a stored property) avoids a class
         * initialisation cycle between this companion and the nested objects.
         */
        val presets: List<DateFilter> get() = listOf(All, Today, Last7Days, Last30Days)

        /** Builds a range filter, normalising reversed dates and single days. */
        fun range(a: LocalDate, b: LocalDate): DateFilter {
            val start = minOf(a, b)
            val end = maxOf(a, b)
            return if (start == end) SingleDate(start) else Range(start, end)
        }
    }
}
