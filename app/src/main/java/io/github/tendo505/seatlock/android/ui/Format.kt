package io.github.tendo505.seatlock.android.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "9:05" for a countdown; never negative. */
fun formatCountdown(remaining: Duration): String {
    val seconds = remaining.seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH)

fun formatEventTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormat.format(instant.atZone(zone))

/** Short human reference for a booking, e.g. "SL-3F9A2C". */
fun bookingReference(holdId: String): String = "SL-" + holdId.filter { it.isLetterOrDigit() }.take(6).uppercase()

fun seatsLeftLabel(available: Int, capacity: Int): String = when {
    available == 0 -> "Sold out"
    available <= capacity / 10 || available <= 5 -> "Only $available left"
    else -> "$available of $capacity available"
}
