package net.orbitratech.healthmate.core.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Number formatting helpers for the UI. */
object Formatters {

    private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

    /** `7850` -> `7,850` */
    fun number(value: Int): String = integerFormat.format(value)

    /** `12500` -> `12.5K` (used where space is limited, e.g. chart axes). */
    fun compact(value: Int): String = when {
        value >= 1_000_000 -> trim(value / 1_000_000.0) + "M"
        value >= 1_000 -> trim(value / 1_000.0) + "K"
        else -> value.toString()
    }

    /** `0.734` -> `73%` */
    fun percent(fraction: Float): String = "${(fraction * 100).roundToInt().coerceIn(0, 999)}%"

    private fun trim(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
