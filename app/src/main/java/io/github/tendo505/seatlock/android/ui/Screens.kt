package io.github.tendo505.seatlock.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tendo505.seatlock.android.data.Event
import io.github.tendo505.seatlock.android.data.HoldStatus
import kotlinx.coroutines.delay
import java.time.Duration

// ---------- shared bits ----------

@Composable
private fun CenteredMessage(title: String, body: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
private fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun StatusPill(status: HoldStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        HoldStatus.HELD -> "On hold" to MaterialTheme.colorScheme.tertiary
        HoldStatus.CONFIRMED -> "Confirmed" to MaterialTheme.colorScheme.primary
        HoldStatus.RELEASED -> "Released" to MaterialTheme.colorScheme.onSurfaceVariant
        HoldStatus.EXPIRED -> "Expired" to MaterialTheme.colorScheme.onSurfaceVariant
        HoldStatus.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun IconLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- events ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(vm: EventsViewModel, onOpen: (String) -> Unit, onOpenSettings: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshQuietly() }
    Scaffold(topBar = { TopAppBar(title = { Text("Upcoming events") }) }) { padding ->
        PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                s.loading -> Loading()
                s.error != null && s.events.isEmpty() -> CenteredMessage("Couldn't load events", s.error) {
                    Button(onClick = vm::refresh) { Text("Try again") }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("events"),
                ) {
                    if (s.isDemo) item { DemoBanner(onOpenSettings) }
                    items(s.events, key = { it.id }) { EventCard(it, onClick = { onOpen(it.id) }) }
                }
            }
        }
    }
}

@Composable
private fun DemoBanner(onOpenSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Demo mode", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Bookings run on an offline backend with the same rules as the real API. Holds last 2 minutes.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            TextButton(onClick = onOpenSettings) { Text("Connect") }
        }
    }
}

@Composable
fun EventCard(event: Event, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = "Event ${event.name}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(event.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            IconLine(Icons.Filled.CalendarMonth, formatEventTime(event.startsAt))
            Spacer(Modifier.height(2.dp))
            IconLine(Icons.Filled.LocationOn, event.venue)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { event.soldFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = if (event.available <= 5) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                seatsLeftLabel(event.available, event.capacity),
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    event.soldOut -> MaterialTheme.colorScheme.error
                    event.available <= 5 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

// ---------- event detail ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(vm: EventDetailViewModel, onBack: () -> Unit, onHoldCreated: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (s.event != null) vm.refresh() }
    LaunchedEffect(s.createdHoldId) {
        s.createdHoldId?.let { onHoldCreated(it); vm.onNavigated() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Get tickets") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        val event = s.event
        when {
            s.loading -> Box(Modifier.padding(padding)) { Loading() }
            event == null -> Box(Modifier.padding(padding)) { CenteredMessage("Event not found", s.error) }
            else -> Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(event.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                IconLine(Icons.Filled.CalendarMonth, formatEventTime(event.startsAt))
                Spacer(Modifier.height(4.dp))
                IconLine(Icons.Filled.LocationOn, event.venue)
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(progress = { event.soldFraction }, modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round)
                Spacer(Modifier.height(6.dp))
                Text(seatsLeftLabel(event.available, event.capacity), modifier = Modifier.testTag("availability"),
                    style = MaterialTheme.typography.labelLarge)

                Spacer(Modifier.height(28.dp))
                if (event.soldOut) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Text("This event is sold out. Seats from expired holds come back automatically, so check again soon.",
                            Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                } else {
                    Text("Tickets", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { vm.setQuantity(s.quantity - 1) }, enabled = s.quantity > 1) {
                            Icon(Icons.Filled.Remove, contentDescription = "Fewer tickets")
                        }
                        Text("${s.quantity}", modifier = Modifier.width(56.dp).testTag("quantity"), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium)
                        FilledTonalIconButton(onClick = { vm.setQuantity(s.quantity + 1) }, enabled = s.quantity < s.maxQuantity) {
                            Icon(Icons.Filled.Add, contentDescription = "More tickets")
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("max ${s.maxQuantity}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = s.email,
                        onValueChange = vm::setEmail,
                        label = { Text("Email for your tickets") },
                        singleLine = true,
                        isError = s.email.isNotBlank() && !s.emailValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("email"),
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = vm::submit, enabled = s.canSubmit, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("hold")) {
                        if (s.submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Hold ${s.quantity} seat${if (s.quantity == 1) "" else "s"}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Seats are reserved while you confirm. Unconfirmed holds are released automatically.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                s.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("error"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- hold / booking ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldScreen(vm: HoldViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val hold = s.hold
    val now by produceState(vm.clock.instant(), hold?.status) {
        while (true) { value = vm.clock.instant(); delay(250) }
    }
    val remaining = hold?.let { Duration.between(now, it.expiresAt) } ?: Duration.ZERO
    val overdue = hold?.status == HoldStatus.HELD && (remaining.isNegative || remaining.isZero)

    // Once the deadline passes, poll until the backend reports the hold expired.
    LaunchedEffect(overdue) {
        while (overdue) { vm.refresh(); delay(3_000) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (hold?.status == HoldStatus.CONFIRMED) "Your tickets" else "Your hold") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        })
    }) { padding ->
        if (hold == null) {
            Box(Modifier.padding(padding)) { if (s.loading) Loading() else CenteredMessage("Hold not found", s.error) }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.event?.name ?: "Event", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            s.event?.let { Spacer(Modifier.height(4.dp)); Text(formatEventTime(it.startsAt), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(28.dp))

            when (hold.status) {
                HoldStatus.HELD -> {
                    val total = Duration.between(hold.createdAt, hold.expiresAt).toMillis().coerceAtLeast(1)
                    val fraction = (remaining.toMillis().toFloat() / total).coerceIn(0f, 1f)
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(200.dp),
                            strokeWidth = 10.dp,
                            color = if (remaining.seconds < 30) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatCountdown(remaining), fontSize = 44.sp, fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("countdown"))
                            Text(if (overdue) "releasing…" else "to confirm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("${hold.quantity} seat${if (hold.quantity == 1) "" else "s"} reserved for ${hold.email}",
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = vm::confirm, enabled = !s.busy && !overdue, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm")) {
                        Text("Confirm booking")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::cancel, enabled = !s.busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Release seats")
                    }
                }
                HoldStatus.CONFIRMED -> {
                    TicketCard(reference = bookingReference(hold.id), quantity = hold.quantity, email = hold.email)
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = vm::cancel, enabled = !s.busy) { Text("Cancel booking", color = MaterialTheme.colorScheme.error) }
                }
                else -> {
                    Icon(Icons.Filled.TimerOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    StatusPill(hold.status)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        when (hold.status) {
                            HoldStatus.EXPIRED -> "This hold expired before it was confirmed. The seats went back on sale."
                            HoldStatus.CANCELLED -> "This booking was cancelled and the seats were released."
                            else -> "You released these seats."
                        },
                        textAlign = TextAlign.Center, modifier = Modifier.testTag("final-message"),
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = onBack) { Text("Back to event") }
                }
            }
            s.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TicketCard(reference: String, quantity: Int, email: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("ticket"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Booking confirmed", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(20.dp))
            Text("REFERENCE", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Text(reference, color = MaterialTheme.colorScheme.onPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(16.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("SEATS", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text("$quantity", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(2f)) {
                    Text("SENT TO", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    Text(email, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- my tickets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsScreen(vm: TicketsViewModel, onOpen: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    Scaffold(topBar = { TopAppBar(title = { Text("My tickets") }) }) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                s.loading -> Loading()
                s.error != null -> CenteredMessage("Couldn't load your tickets", s.error)
                s.rows.isEmpty() -> CenteredMessage("No tickets yet", "Holds and bookings you make appear here.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.rows, key = { it.hold.id }) { row ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(row.hold.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.eventName ?: "Event", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${row.hold.quantity} seat${if (row.hold.quantity == 1) "" else "s"} · ${bookingReference(row.hold.id)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusPill(row.hold.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- settings ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Connect to a running SeatLock API, or leave blank to use the offline demo. " +
                "From the Android emulator, your computer's localhost is http://10.0.2.2:8080.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = s.serverUrl, onValueChange = vm::setUrl, singleLine = true,
                label = { Text("API base URL") }, placeholder = { Text("Demo mode") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::save) { Text("Save") }
                OutlinedButton(onClick = vm::testConnection, enabled = !s.testing) { Text(if (s.testing) "Testing…" else "Test connection") }
                TextButton(onClick = vm::useDemo) { Text("Use demo") }
            }
            if (s.saved) Text("Saved", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            s.testResult?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp),
                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("SeatLock is an Android client for the SeatLock booking API, a Kotlin + Spring Boot service that never oversells. " +
                "Source: github.com/Tendo505/seatlock-android",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Version ${io.github.tendo505.seatlock.android.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                color = Color.Gray)
        }
    }
}
