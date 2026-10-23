package io.github.tendo505.seatlock.android

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import io.github.tendo505.seatlock.android.data.BookingError
import io.github.tendo505.seatlock.android.data.BookingRepository
import io.github.tendo505.seatlock.android.data.DemoBookingRepository
import io.github.tendo505.seatlock.android.data.Hold
import io.github.tendo505.seatlock.android.data.SettingsStore
import io.github.tendo505.seatlock.android.ui.EventDetailViewModel
import io.github.tendo505.seatlock.android.ui.SeatLockTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import java.time.Duration

/** End-to-end UI flows on the JVM with Robolectric, driving the real navigation graph against the demo backend. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SeatLockApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class UiTest {
    @get:Rule val compose = createComposeRule()

    private val clock = MutableClock()

    private fun launch(repo: BookingRepository = DemoBookingRepository(clock, latency = Duration.ZERO)) {
        val container = TestContainer(SettingsStore(ApplicationProvider.getApplicationContext()), clock, repo)
        compose.setContent { SeatLockTheme { SeatLockNavHost(container) } }
    }

    @Test
    fun `events list shows availability, low stock and sold out`() {
        launch()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("events") }
        compose.onNodeWithText("Demo mode").assertExists()
        compose.onNodeWithTag("events").performScrollToNode(hasText("Coldplay: Music of the Spheres"))
        compose.onNodeWithText("Coldplay: Music of the Spheres").assertExists()
        compose.onNodeWithText("Only 7 left").assertExists()
    }

    @Test
    fun `hold seats, watch the countdown, then confirm the booking`() {
        launch()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("events") }
        compose.onNodeWithContentDescription("Event Jazz Night at No Black Tie").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("hold") }

        compose.onNodeWithTag("email").performTextReplacement("")
        compose.onNodeWithTag("hold").assertIsNotEnabled() // email required
        compose.onNodeWithTag("email").performTextReplacement("amirul@example.com")
        compose.onNodeWithContentDescription("More tickets").performClick()
        compose.onNodeWithTag("quantity").assertTextEquals("2")
        compose.onNodeWithTag("hold").assertIsEnabled().performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("countdown") }
        compose.onNodeWithTag("countdown").assertTextEquals("2:00")
        compose.onNodeWithText("2 seats reserved for amirul@example.com").assertExists()

        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ticket") }
        compose.onNodeWithText("Booking confirmed").assertExists()
    }

    @Test
    fun `after booking, going back shows the updated availability`() {
        launch()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("events") }
        compose.onNodeWithText("22 of 40 available").assertExists()
        compose.onNodeWithContentDescription("Event Jazz Night at No Black Tie").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("hold") }
        compose.onNodeWithTag("email").performTextReplacement("a@example.com")
        compose.onNodeWithContentDescription("More tickets").performClick()
        compose.onNodeWithTag("hold").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("confirm") }
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("ticket") }

        compose.onNodeWithContentDescription("Back").performClick() // to the event
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("20 of 40 available")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick() // to the list
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("events") }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("20 of 40 available")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `an expired hold shows that the seats were released`() {
        launch()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("events") }
        compose.onNodeWithContentDescription("Event Jazz Night at No Black Tie").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("hold") }
        compose.onNodeWithTag("email").performTextReplacement("a@example.com")
        compose.onNodeWithTag("hold").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("countdown") }

        clock.now = clock.now.plus(Duration.ofMinutes(3))
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitUntil(5_000) { compose.onAllNodesWithTagExists("final-message") }
        compose.onNodeWithText("Expired").assertExists()
    }

    @Test
    fun `a network failure keeps the idempotency key so the retry cannot double-book`() {
        val flaky = FlakyRepository(DemoBookingRepository(clock, latency = Duration.ZERO))
        val container = TestContainer(SettingsStore(ApplicationProvider.getApplicationContext()), clock, flaky)
        runBlocking { container.settings.setEmail("a@example.com") }
        val vm = EventDetailViewModel(container, "demo-2")
        awaitOnMainLooper { vm.state.value.event != null && vm.state.value.email.isNotEmpty() }

        vm.submit()
        awaitOnMainLooper { vm.state.value.error != null }
        val keyAfterFailure = vm.currentAttemptKey()
        assertNotNull("a network error must keep the attempt key", keyAfterFailure)

        vm.submit()
        awaitOnMainLooper { vm.state.value.createdHoldId != null }
        assertEquals(listOf(keyAfterFailure, keyAfterFailure), flaky.keysSeen)
        assertNull("a successful attempt clears the key", vm.currentAttemptKey())
    }

    /** ViewModels run on the main looper, which Robolectric only advances when asked. */
    private fun awaitOnMainLooper(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met within ${timeoutMs}ms" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

/** Routes every repository lookup to [repo], whatever the saved server URL is. */
class TestContainer(settings: SettingsStore, clock: java.time.Clock, private val repo: BookingRepository) : AppContainer(settings, clock) {
    override fun repositoryFor(serverUrl: String): BookingRepository = repo
}

/** Fails the first hold request as if the network dropped after the server processed it. */
class FlakyRepository(private val inner: BookingRepository) : BookingRepository by inner {
    val keysSeen = mutableListOf<String>()
    private var failed = false
    override suspend fun hold(eventId: String, quantity: Int, email: String, idempotencyKey: String): Hold {
        keysSeen += idempotencyKey
        val result = inner.hold(eventId, quantity, email, idempotencyKey)
        if (!failed) { failed = true; throw BookingError.Network(java.io.IOException("connection reset")) }
        return result
    }
}
