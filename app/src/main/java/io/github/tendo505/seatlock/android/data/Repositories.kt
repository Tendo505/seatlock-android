package io.github.tendo505.seatlock.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The booking operations the app needs; backed by the real API or the offline demo. */
interface BookingRepository {
    val isDemo: Boolean
    suspend fun events(): List<Event>
    suspend fun event(id: String): Event
    /** [idempotencyKey] must stay the same across retries of one purchase attempt. */
    suspend fun hold(eventId: String, quantity: Int, email: String, idempotencyKey: String): Hold
    suspend fun getHold(id: String): Hold
    suspend fun confirm(holdId: String): Hold
    suspend fun cancel(holdId: String): Hold
}

// ---------- real API ----------

interface SeatLockApi {
    @GET("api/events") suspend fun events(@Query("upcoming") upcoming: Boolean = true): List<EventDto>
    @GET("api/events/{id}") suspend fun event(@Path("id") id: String): EventDto
    @POST("api/events/{id}/holds")
    suspend fun hold(@Path("id") id: String, @Header("Idempotency-Key") key: String, @Body body: CreateHoldBody): HoldDto
    @GET("api/holds/{id}") suspend fun getHold(@Path("id") id: String): HoldDto
    @POST("api/holds/{id}/confirm") suspend fun confirm(@Path("id") id: String): HoldDto
    @DELETE("api/holds/{id}") suspend fun cancel(@Path("id") id: String): HoldDto
}

val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

class RemoteBookingRepository(baseUrl: String) : BookingRepository {
    override val isDemo = false

    private val api: SeatLockApi = Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SeatLockApi::class.java)

    override suspend fun events() = call { api.events().map { it.toDomain() } }
    override suspend fun event(id: String) = call { api.event(id).toDomain() }
    override suspend fun hold(eventId: String, quantity: Int, email: String, idempotencyKey: String) =
        call { api.hold(eventId, idempotencyKey, CreateHoldBody(quantity, email)).toDomain() }
    override suspend fun getHold(id: String) = call { api.getHold(id).toDomain() }
    override suspend fun confirm(holdId: String) = call { api.confirm(holdId).toDomain() }
    override suspend fun cancel(holdId: String) = call { api.cancel(holdId).toDomain() }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw problemToError(e.code(), e.response()?.errorBody()?.string())
    } catch (e: IOException) {
        throw BookingError.Network(e)
    }
}

/** Translate the API's problem+json responses into typed errors. */
fun problemToError(status: Int, body: String?): BookingError {
    val problem = runCatching { json.decodeFromString(ProblemDto.serializer(), body ?: "") }.getOrNull()
    val slug = problem?.type?.substringAfter('#', "")
    return when {
        slug == "not-enough-seats" -> BookingError.NotEnoughSeats(problem.requested ?: 0, problem.available ?: 0)
        slug == "hold-expired" -> BookingError.HoldExpired()
        slug == "hold-not-active" -> BookingError.NotActive()
        status == 404 -> BookingError.NotFound()
        status == 400 || status == 422 -> BookingError.Invalid(
            problem?.errors?.values?.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: problem?.detail ?: "Invalid request",
        )
        else -> BookingError.Server(problem?.detail ?: "Server error ($status)")
    }
}

// ---------- offline demo ----------

/**
 * An in-memory backend with the same rules as the real API (holds, expiry,
 * idempotency, never overselling), so the app is fully usable without a server.
 * A [Mutex] plays the role of the database's row lock.
 */
class DemoBookingRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val holdTtl: Duration = Duration.ofMinutes(2),
    private val latency: Duration = Duration.ofMillis(350),
) : BookingRepository {
    override val isDemo = true

    private val lock = Mutex()
    private val events = LinkedHashMap<String, Event>()
    private val holds = HashMap<String, Hold>()
    private val idempotency = HashMap<String, Pair<String, String>>() // key -> (request fingerprint, hold id)

    init {
        val now = clock.instant().truncatedTo(ChronoUnit.HOURS)
        fun add(id: String, name: String, venue: String, days: Long, capacity: Int, available: Int) {
            events[id] = Event(id, name, venue, now.plus(Duration.ofDays(days)).plus(Duration.ofHours(12)), capacity, available)
        }
        add("demo-1", "Coldplay: Music of the Spheres", "Bukit Jalil National Stadium", 12, 80, 7)
        add("demo-2", "Jazz Night at No Black Tie", "No Black Tie, Kuala Lumpur", 3, 40, 22)
        add("demo-3", "Malaysia Tech Week Keynote", "KLCC Convention Centre", 21, 500, 312)
        add("demo-4", "Sheila Majid Live", "Dewan Filharmonik Petronas", 30, 120, 0)
        add("demo-5", "Indie Film Screening: Abang Adik", "GSC Pavilion KL", 6, 60, 41)
    }

    private suspend fun pause() { if (!latency.isZero) delay(latency.toMillis()) }
    private fun now(): Instant = clock.instant()

    /** Lazily expire overdue holds, as the server's sweep would. Call with the lock held. */
    private fun sweep() {
        val now = now()
        holds.values.filter { it.status == HoldStatus.HELD && !it.expiresAt.isAfter(now) }.forEach { h ->
            holds[h.id] = h.copy(status = HoldStatus.EXPIRED)
            returnSeats(h)
        }
    }

    private fun returnSeats(h: Hold) {
        val e = events.getValue(h.eventId)
        events[h.eventId] = e.copy(available = e.available + h.quantity)
    }

    override suspend fun events(): List<Event> { pause(); return lock.withLock { sweep(); events.values.sortedBy { it.startsAt } } }

    override suspend fun event(id: String): Event { pause(); return lock.withLock { sweep(); events[id] ?: throw BookingError.NotFound() } }

    override suspend fun hold(eventId: String, quantity: Int, email: String, idempotencyKey: String): Hold {
        pause()
        if (quantity !in 1..10) throw BookingError.Invalid("Choose between 1 and 10 seats")
        if (!email.contains('@')) throw BookingError.Invalid("Enter a valid email address")
        val fingerprint = "$eventId|$quantity|${email.trim().lowercase()}"
        return lock.withLock {
            sweep()
            idempotency[idempotencyKey]?.let { (fp, holdId) ->
                if (fp != fingerprint) throw BookingError.Invalid("This purchase attempt was already used for a different order")
                return@withLock holds.getValue(holdId)
            }
            val event = events[eventId] ?: throw BookingError.NotFound()
            if (event.available < quantity) throw BookingError.NotEnoughSeats(quantity, event.available)
            events[eventId] = event.copy(available = event.available - quantity)
            val now = now()
            val hold = Hold(UUID.randomUUID().toString(), eventId, quantity, email.trim().lowercase(), HoldStatus.HELD, now.plus(holdTtl), now)
            holds[hold.id] = hold
            idempotency[idempotencyKey] = fingerprint to hold.id
            hold
        }
    }

    override suspend fun getHold(id: String): Hold { pause(); return lock.withLock { sweep(); holds[id] ?: throw BookingError.NotFound() } }

    override suspend fun confirm(holdId: String): Hold {
        pause()
        return lock.withLock {
            sweep()
            val h = holds[holdId] ?: throw BookingError.NotFound()
            when (h.status) {
                HoldStatus.HELD -> h.copy(status = HoldStatus.CONFIRMED).also { holds[holdId] = it }
                HoldStatus.CONFIRMED -> h
                HoldStatus.EXPIRED -> throw BookingError.HoldExpired()
                else -> throw BookingError.NotActive()
            }
        }
    }

    override suspend fun cancel(holdId: String): Hold {
        pause()
        return lock.withLock {
            sweep()
            val h = holds[holdId] ?: throw BookingError.NotFound()
            val next = when (h.status) {
                HoldStatus.HELD -> HoldStatus.RELEASED
                HoldStatus.CONFIRMED -> HoldStatus.CANCELLED
                else -> return@withLock h
            }
            returnSeats(h)
            h.copy(status = next).also { holds[holdId] = it }
        }
    }
}
