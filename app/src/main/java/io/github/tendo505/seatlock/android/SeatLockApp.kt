package io.github.tendo505.seatlock.android

import android.app.Application
import io.github.tendo505.seatlock.android.data.BookingRepository
import io.github.tendo505.seatlock.android.data.DemoBookingRepository
import io.github.tendo505.seatlock.android.data.RemoteBookingRepository
import io.github.tendo505.seatlock.android.data.SettingsStore
import java.time.Clock

/** Hand-rolled dependency container: small enough that a DI framework would add more than it saves. */
open class AppContainer(val settings: SettingsStore, val clock: Clock = Clock.systemUTC()) {
    private val demo by lazy { DemoBookingRepository(clock) }
    private var remote: Pair<String, RemoteBookingRepository>? = null

    /** The demo backend when no server is configured, otherwise a client for that server (cached per URL). */
    @Synchronized
    open fun repositoryFor(serverUrl: String): BookingRepository {
        if (serverUrl.isBlank()) return demo
        remote?.let { (url, repo) -> if (url == serverUrl) return repo }
        return RemoteBookingRepository(serverUrl).also { remote = serverUrl to it }
    }

    suspend fun repository(): BookingRepository = repositoryFor(settings.current().serverUrl)
}

class SeatLockApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(SettingsStore(this))
    }
}
