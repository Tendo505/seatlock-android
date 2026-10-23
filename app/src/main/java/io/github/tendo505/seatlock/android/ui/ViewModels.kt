package io.github.tendo505.seatlock.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tendo505.seatlock.android.AppContainer
import io.github.tendo505.seatlock.android.data.BookingError
import io.github.tendo505.seatlock.android.data.Event
import io.github.tendo505.seatlock.android.data.Hold
import io.github.tendo505.seatlock.android.data.HoldStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private fun Throwable.userMessage(): String = (this as? BookingError)?.message ?: "Something went wrong"

/** Run a repository call, turning failures into a user-facing message (cancellation still propagates). */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

// ---------- events list ----------

data class EventsState(
    val events: List<Event> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isDemo: Boolean = false,
)

class EventsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    init {
        // Reload whenever the server setting changes (including the first emission).
        viewModelScope.launch {
            container.settings.settings.map { it.serverUrl }.distinctUntilChanged().collect {
                _state.value = EventsState()
                load()
            }
        }
    }

    /** Pull-to-refresh: shows the refresh indicator. */
    fun refresh() = load(refreshing = true)

    /** Background refresh when the screen comes back into view: keeps the current list visible. */
    fun refreshQuietly() { if (!_state.value.loading) load() }

    private fun load(refreshing: Boolean = false) {
        _state.update { it.copy(refreshing = refreshing, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            attempt { repo.events() }
                .onSuccess { events -> _state.update { EventsState(events = events, loading = false, isDemo = repo.isDemo) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage(), isDemo = repo.isDemo) } }
        }
    }
}

// ---------- event detail / hold seats ----------

data class EventDetailState(
    val event: Event? = null,
    val quantity: Int = 1,
    val email: String = "",
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    /** Set once a hold is created; the screen navigates to it. */
    val createdHoldId: String? = null,
) {
    val maxQuantity: Int get() = minOf(10, event?.available ?: 0)
    val emailValid: Boolean get() = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    val canSubmit: Boolean get() = event != null && !event.soldOut && emailValid && quantity in 1..maxQuantity && !submitting
}

class EventDetailViewModel(private val container: AppContainer, private val eventId: String) : ViewModel() {
    private val _state = MutableStateFlow(EventDetailState())
    val state: StateFlow<EventDetailState> = _state.asStateFlow()

    /**
     * One key per purchase attempt. A retry after a network failure reuses it,
     * so if the first request actually reached the server, we get that same hold
     * back instead of holding seats twice. Changing the order starts a new attempt.
     */
    private var attemptKey: String? = null

    init {
        viewModelScope.launch {
            val email = container.settings.current().email
            _state.update { it.copy(email = email) }
            reload()
        }
    }

    /** Re-fetch availability, e.g. when returning to this screen after booking. */
    fun refresh() { viewModelScope.launch { reload() } }

    private suspend fun reload() {
        val repo = container.repository()
        attempt { repo.event(eventId) }
            .onSuccess { e -> _state.update { it.copy(event = e, loading = false, quantity = it.quantity.coerceIn(1, maxOf(1, minOf(10, e.available)))) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
    }

    fun setQuantity(q: Int) {
        attemptKey = null
        _state.update { it.copy(quantity = q.coerceIn(1, maxOf(1, it.maxQuantity)), error = null) }
    }

    fun setEmail(email: String) {
        attemptKey = null
        _state.update { it.copy(email = email, error = null) }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val key = attemptKey ?: UUID.randomUUID().toString().also { attemptKey = it }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            attempt { repo.hold(eventId, s.quantity, s.email.trim(), key) }
                .onSuccess { hold ->
                    attemptKey = null
                    container.settings.setEmail(s.email)
                    container.settings.rememberHold(hold.id)
                    _state.update { it.copy(submitting = false, createdHoldId = hold.id) }
                }
                .onFailure { e ->
                    // A definitive answer from the server ends this attempt; a network error keeps the key for retry.
                    if (e !is BookingError.Network) attemptKey = null
                    _state.update { it.copy(submitting = false, error = e.userMessage()) }
                    if (e is BookingError.NotEnoughSeats) reload()
                }
        }
    }

    fun onNavigated() = _state.update { it.copy(createdHoldId = null) }

    /** Exposed for tests. */
    internal fun currentAttemptKey(): String? = attemptKey
}

// ---------- hold / booking ----------

data class HoldState(
    val hold: Hold? = null,
    val event: Event? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

class HoldViewModel(private val container: AppContainer, private val holdId: String) : ViewModel() {
    private val _state = MutableStateFlow(HoldState())
    val state: StateFlow<HoldState> = _state.asStateFlow()
    val clock get() = container.clock

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val repo = container.repository()
            attempt {
                val hold = repo.getHold(holdId)
                hold to runCatching { repo.event(hold.eventId) }.getOrNull()
            }
                .onSuccess { (hold, event) -> _state.update { it.copy(hold = hold, event = event ?: it.event, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun confirm() = act { it.confirm(holdId) }
    fun cancel() = act { it.cancel(holdId) }

    private fun act(op: suspend (io.github.tendo505.seatlock.android.data.BookingRepository) -> Hold) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val repo = container.repository()
            attempt { op(repo) }
                .onSuccess { hold -> _state.update { it.copy(hold = hold, busy = false) } }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = e.userMessage()) }
                    if (e is BookingError.HoldExpired) refresh()
                }
        }
    }
}

// ---------- my tickets ----------

data class TicketRow(val hold: Hold, val eventName: String?)
data class TicketsState(val rows: List<TicketRow> = emptyList(), val loading: Boolean = true, val error: String? = null)

class TicketsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TicketsState())
    val state: StateFlow<TicketsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val repo = container.repository()
            val ids = container.settings.current().myHoldIds
            val result = attempt {
                val holds = ids.map { id -> async { runCatching { repo.getHold(id) }.getOrNull() } }.awaitAll().filterNotNull()
                val names = holds.map { it.eventId }.distinct()
                    .associateWith { id -> runCatching { repo.event(id).name }.getOrNull() }
                holds.map { TicketRow(it, names[it.eventId]) }
                    .sortedWith(compareBy<TicketRow> { it.hold.status != HoldStatus.CONFIRMED && it.hold.status != HoldStatus.HELD }.thenByDescending { it.hold.createdAt })
            }
            _state.value = result.fold(
                { TicketsState(rows = it, loading = false) },
                { TicketsState(loading = false, error = it.userMessage()) },
            )
        }
    }
}

// ---------- settings ----------

data class SettingsState(val serverUrl: String = "", val saved: Boolean = false, val testResult: String? = null, val testing: Boolean = false)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(serverUrl = container.settings.current().serverUrl) } }
    }

    fun setUrl(url: String) = _state.update { it.copy(serverUrl = url, saved = false, testResult = null) }

    fun save() {
        val url = _state.value.serverUrl.trim()
        if (url.isNotEmpty() && !Regex("^https?://[^\\s/]+.*$").matches(url)) {
            _state.update { it.copy(testResult = "Enter a URL starting with http:// or https://") }
            return
        }
        viewModelScope.launch {
            container.settings.setServerUrl(url)
            _state.update { it.copy(saved = true) }
        }
    }

    fun useDemo() {
        _state.update { it.copy(serverUrl = "") }
        save()
    }

    fun testConnection() {
        val url = _state.value.serverUrl.trim()
        _state.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            val repo = container.repositoryFor(url)
            val message = attempt { repo.events() }.fold(
                { "Connected: ${it.size} upcoming event${if (it.size == 1) "" else "s"}${if (repo.isDemo) " (demo)" else ""}" },
                { "Failed: ${it.userMessage()}" },
            )
            _state.update { it.copy(testing = false, testResult = message) }
        }
    }
}
