package net.orbitratech.healthmate.navigation

import androidx.annotation.DrawableRes
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.util.DateUtils
import net.orbitratech.healthmate.feature.records.ui.form.RecordFormViewModel
import java.time.LocalDate

/**
 * Top-level destinations shown in the bottom navigation bar. [hasFab] marks
 * screens with a floating action button, so snackbars can be placed above it.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val hasFab: Boolean,
) {
    DASHBOARD("dashboard", "Dashboard", R.drawable.ic_dashboard, R.drawable.ic_dashboard_filled, hasFab = true),
    RECORDS("records", "Records", R.drawable.ic_list, R.drawable.ic_list_filled, hasFab = true),
    SETTINGS("settings", "Settings", R.drawable.ic_settings, R.drawable.ic_settings_filled, hasFab = false),
}

/** Add/Edit Record destination with optional `recordId` and `date` arguments. */
object RecordFormDestination {
    const val ROUTE =
        "record_form?${RecordFormViewModel.ARG_RECORD_ID}={${RecordFormViewModel.ARG_RECORD_ID}}" +
            "&${RecordFormViewModel.ARG_DATE}={${RecordFormViewModel.ARG_DATE}}"

    fun add(date: LocalDate? = null): String =
        "record_form?${RecordFormViewModel.ARG_DATE}=${date?.let(DateUtils::toDbString).orEmpty()}"

    fun edit(recordId: Long): String = "record_form?${RecordFormViewModel.ARG_RECORD_ID}=$recordId"
}
