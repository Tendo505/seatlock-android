package io.github.tendo505.seatlock.android.data

import kotlinx.serialization.Serializable
import java.time.Instant

data class Event(
    val id: String,
    val name: String,
    val venue: String,
    val startsAt: Instant,
    val capacity: Int,
    val available: Int,
) {
    val soldOut: Boolean get() = available == 0
    val soldFraction: Float get() = if (capacity == 0) 1f else (capacity - available).toFloat() / capacity
}

enum class HoldStatus { HELD, CONFIRMED, RELEASED, EXPIRED, CANCELLED }

data class Hold(
    val id: String,
    val eventId: String,
    val quantity: Int,
    val email: String,
    val status: HoldStatus,
    val expiresAt: Instant,
    val createdAt: Instant,
)

/** Everything that can go wrong, in terms the UI can explain to a person. */
sealed class BookingError(message: String) : Exception(message) {
    class NotEnoughSeats(val requested: Int, val available: Int) :
        BookingError(if (available == 0) "Sold out" else "Only $available seat${if (available == 1) "" else "s"} left")
    class HoldExpired : BookingError("Your hold expired and the seats were released")
    class NotActive : BookingError("This hold can no longer be changed")
    class NotFound : BookingError("That no longer exists")
    class Invalid(message: String) : BookingError(message)
    class Network(cause: Throwable) : BookingError("Can't reach the server. Check your connection and try again.") {
        init { initCause(cause) }
    }
    class Server(message: String) : BookingError(message)
}

// ---------- wire format (matches the SeatLock API) ----------

@Serializable
data class EventDto(
    val id: String,
    val name: String,
    val venue: String,
    val startsAt: String,
    val capacity: Int,
    val available: Int,
) {
    fun toDomain() = Event(id, name, venue, Instant.parse(startsAt), capacity, available)
}

@Serializable
data class HoldDto(
    val id: String,
    val eventId: String,
    val quantity: Int,
    val email: String,
    val status: String,
    val expiresAt: String,
    val createdAt: String,
) {
    fun toDomain() = Hold(id, eventId, quantity, email, HoldStatus.valueOf(status), Instant.parse(expiresAt), Instant.parse(createdAt))
}

@Serializable
data class CreateHoldBody(val quantity: Int, val email: String)

/** RFC 9457 problem details returned by the API on errors. */
@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val requested: Int? = null,
    val available: Int? = null,
    val errors: Map<String, String>? = null,
)
