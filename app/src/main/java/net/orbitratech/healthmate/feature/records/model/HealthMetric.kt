package net.orbitratech.healthmate.feature.records.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import net.orbitratech.healthmate.R
import net.orbitratech.healthmate.core.designsystem.theme.CaloriesRed
import net.orbitratech.healthmate.core.designsystem.theme.StepsGreen
import net.orbitratech.healthmate.core.designsystem.theme.WaterBlue

/**
 * The three activities tracked by HealthMate.
 *
 * Label, unit, icon and colour are defined once here so every screen presents
 * a metric consistently (e.g. water is always a blue drop measured in ml).
 */
enum class HealthMetric(
    val label: String,
    val shortLabel: String,
    val unit: String,
    @param:DrawableRes val icon: Int,
    val color: Color,
) {
    STEPS("Steps", "Steps", "steps", R.drawable.ic_steps, StepsGreen),
    CALORIES("Calories Burned", "Calories", "kcal", R.drawable.ic_calories, CaloriesRed),
    WATER("Water Intake", "Water", "ml", R.drawable.ic_water, WaterBlue),
}
