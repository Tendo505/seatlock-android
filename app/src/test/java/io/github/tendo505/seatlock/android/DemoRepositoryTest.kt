package io.github.tendo505.seatlock.android

import io.github.tendo505.seatlock.android.data.BookingError
import io.github.tendo505.seatlock.android.data.DemoBookingRepository
import io.github.tendo505.seatlock.android.data.HoldStatus
import io.github.tendo505.seatlock.android.data.problemToError
import io.github.tendo505.seatlock.android.ui.bookingReference
import io.github.tendo505.seatlock.android.ui.formatCountdown
import io.github.tendo505.seatlock.android.ui.seatsLeftLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(var now: Instant = Instant.parse("2026-11-01T10:00:00Z")) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
    try { block() } catch (e: Throwable) { if (e is T) return e; throw e }
    fail("expected ${T::class.simpleName}")
    error("unreachable")
}

class DemoRepositoryTest {
    private val clock = MutableClock()
    private val repo = DemoBookingRepository(clock, holdTtl = Duration.ofMinutes(2), latency = Duration.ZERO)

    @Test
    fun `holding seats reduces availability`() = runTest {
        val event = repo.events().first { it.available >= 3 }
        val hold = repo.hold(event.id, 3, "Ana@Example.com", "k1")
        assertEquals(HoldStatus.HELD, hold.status)
        assertEquals("ana@example.com", hold.email)
        assertEquals(event.available - 3, repo.event(event.id).available)
    }

    @Test
    fun `cannot oversell`() = runTest {
        val event = repo.events().first { it.available in 1..10 }
        val err = assertFailsWith<BookingError.NotEnoughSeats> { repo.hold(event.id, event.available + 1, "a@x.io", "k") }
        assertEquals(event.available, err.available)
    }

    @Test
    fun `sold out events reject holds`() = runTest {
        val soldOut = repo.events().first { it.soldOut }
        assertEquals("Sold out", assertFailsWith<BookingError.NotEnoughSeats> { repo.hold(soldOut.id, 1, "a@x.io", "k") }.message)
    }

    @Test
    fun `concurrent buyers never oversell`() = runBlocking {
        val event = repo.events().first { it.available in 5..30 }
        val results = (1..200).map { i ->
            async(Dispatchers.Default) { runCatching { repo.hold(event.id, 1, "fan$i@x.io", "key-$i") } }
        }.awaitAll()
        assertEquals(event.available, results.count { it.isSuccess })
        assertEquals(0, repo.event(event.id).available)
    }

    @Test
    fun `holds expire after the TTL and return their seats`() = runTest {
        val event = repo.events().first { it.available >= 2 }
        val hold = repo.hold(event.id, 2, "a@x.io", "k")
        clock.now = clock.now.plus(Duration.ofMinutes(2))
        assertEquals(HoldStatus.EXPIRED, repo.getHold(hold.id).status)
        assertEquals(event.available, repo.event(event.id).available)
        assertFailsWith<BookingError.HoldExpired> { repo.confirm(hold.id) }
    }

    @Test
    fun `confirm then cancel returns seats once`() = runTest {
        val event = repo.events().first { it.available >= 2 }
        val hold = repo.hold(event.id, 2, "a@x.io", "k")
        assertEquals(HoldStatus.CONFIRMED, repo.confirm(hold.id).status)
        assertEquals(HoldStatus.CONFIRMED, repo.confirm(hold.id).status) // idempotent
        assertEquals(HoldStatus.CANCELLED, repo.cancel(hold.id).status)
        assertEquals(HoldStatus.CANCELLED, repo.cancel(hold.id).status) // idempotent, no double credit
        assertEquals(event.available, repo.event(event.id).available)
    }

    @Test
    fun `the same idempotency key replays the original hold`() = runTest {
        val event = repo.events().first { it.available >= 4 }
        val first = repo.hold(event.id, 2, "a@x.io", "retry-key")
        val again = repo.hold(event.id, 2, "a@x.io", "retry-key")
        assertEquals(first.id, again.id)
        assertEquals(event.available - 2, repo.event(event.id).available)
        assertFailsWith<BookingError.Invalid> { repo.hold(event.id, 3, "a@x.io", "retry-key") }
    }
}

class ProblemMappingTest {
    @Test
    fun `not-enough-seats problem maps to a typed error`() {
        val body = """{"type":"https://github.com/Tendo505/seatlock#not-enough-seats","status":409,"detail":"x","requested":2,"available":1}"""
        val e = problemToError(409, body)
        assertTrue(e is BookingError.NotEnoughSeats)
        assertEquals("Only 1 seat left", e.message)
    }

    @Test
    fun `validation errors surface the first field message`() {
        val e = problemToError(400, """{"status":400,"detail":"Request validation failed","errors":{"email":"must be a well-formed email address"}}""")
        assertEquals("Must be a well-formed email address", e.message)
    }

    @Test
    fun `other statuses and garbage bodies degrade gracefully`() {
        assertTrue(problemToError(404, null) is BookingError.NotFound)
        assertTrue(problemToError(409, """{"type":"https://github.com/Tendo505/seatlock#hold-expired"}""") is BookingError.HoldExpired)
        assertEquals("Server error (502)", problemToError(502, "<html>bad gateway</html>").message)
    }
}

class FormatTest {
    @Test
    fun countdown() {
        assertEquals("2:00", formatCountdown(Duration.ofMinutes(2)))
        assertEquals("0:05", formatCountdown(Duration.ofSeconds(5)))
        assertEquals("10:01", formatCountdown(Duration.ofSeconds(601)))
        assertEquals("0:00", formatCountdown(Duration.ofSeconds(-3)))
    }

    @Test
    fun labels() {
        assertEquals("Sold out", seatsLeftLabel(0, 100))
        assertEquals("Only 4 left", seatsLeftLabel(4, 100))
        assertEquals("60 of 100 available", seatsLeftLabel(60, 100))
        assertEquals("SL-3F9A2C", bookingReference("3f9a2c11-aaaa-bbbb"))
    }
}
