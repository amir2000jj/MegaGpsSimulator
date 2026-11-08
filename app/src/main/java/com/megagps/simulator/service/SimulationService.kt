package com.megagps.simulator.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.megagps.simulator.R
import com.megagps.simulator.domain.*
import com.megagps.simulator.ui.MainActivity
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Foreground service (type "location") that owns the simulation loop. All state is touched only on
 * the main thread, so no locking is needed. If Android or the user kills the service the simulation
 * ends: it is never restarted automatically (START_NOT_STICKY) - the user must start it again.
 */
class SimulationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var mock: MockLocationProvider
    private var loop: Job? = null
    private var config: SimConfig? = null
    private var engine: MovementEngine? = null
    private var holdPoint: GeoPoint? = null
    private var paused = false
    private var failCount = 0
    private var lastNotify = 0L
    private var lastStatus: SimStatus? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        mock = MockLocationProvider(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START) {
            start()
            return START_NOT_STICKY
        }
        if (loop == null) { // a command for a simulation that is not running
            stopSelf()
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_STOP -> shutdown()
            ACTION_RESTART -> restart()
            ACTION_REVERSE -> { engine?.reverse(); tick(0.0) }
            ACTION_SPEED -> {
                val m = runCatching { MoveMode.valueOf(intent?.getStringExtra(EXTRA_MODE) ?: "") }.getOrNull()
                val kmh = intent?.getDoubleExtra(EXTRA_SPEED, 0.0) ?: 0.0
                if (m != null && kmh > 0) engine?.setSpeed(m, kmh)
            }
        }
        return START_NOT_STICKY
    }

    private fun start() {
        val cfg = SimulationHub.pending
        if (!enterForeground()) return
        if (cfg == null || cfg.points.isEmpty()) { shutdown(); return }
        if (!mock.start()) { shutdown(SimError.MOCK_NOT_ALLOWED); return }
        config = cfg
        paused = false
        failCount = 0
        buildEngine()
        acquireWakeLock()
        loop?.cancel()
        tick(0.0)
        loop = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(config?.intervalMs ?: 1000L)
                val now = SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000.0).coerceIn(0.0, 5.0)
                last = now
                tick(dt)
            }
        }
    }

    private fun buildEngine() {
        val c = config ?: return
        if (c.points.size >= 2) {
            engine = MovementEngine(RoutePath(c.points), c.mode, c.speedKmh, c.humanWalking, c.randomPauses)
            holdPoint = null
        } else {
            engine = null
            holdPoint = c.points.first()
        }
    }

    private fun restart() {
        paused = false
        buildEngine()
        tick(0.0)
    }

    private fun setPaused(p: Boolean) {
        paused = p
        tick(0.0)
        notifyNow()
    }

    /** One simulation step: advance, deliver to the mock providers, publish state. */
    private fun tick(dt: Double) {
        val e = engine
        val sample = if (e != null) (if (paused) e.snapshot() else e.step(dt)) else null
        val point = sample?.point ?: holdPoint ?: return
        val speed = if (paused) 0.0 else sample?.speedMs ?: 0.0
        val heading = sample?.headingDeg ?: 0.0

        if (mock.push(point, speed, heading)) failCount = 0
        else if (++failCount >= 3) { shutdown(SimError.MOCK_NOT_ALLOWED); return } // e.g. user changed mock app

        val status = when {
            sample?.completed == true -> SimStatus.COMPLETED
            paused -> SimStatus.PAUSED
            else -> SimStatus.RUNNING
        }
        val eta = if (e != null && sample != null && status == SimStatus.RUNNING)
            System.currentTimeMillis() + (sample.remainingM / max(0.3, e.speedKmh / 3.6) * 1000).toLong()
        else null
        SimulationHub.state.value = SimState(
            status = status, isRoute = e != null, lat = point.lat, lon = point.lon,
            speedKmh = speed * 3.6, headingDeg = heading,
            totalM = sample?.totalM ?: 0.0, traveledM = sample?.traveledM ?: 0.0,
            remainingM = sample?.remainingM ?: 0.0, etaEpochMs = eta, reversed = e?.direction == -1,
        )

        val now = SystemClock.elapsedRealtime()
        if (status != lastStatus || now - lastNotify > 3000) {
            lastStatus = status
            notifyNow()
        }
    }

    // ---- foreground / notification -------------------------------------------------------------

    private fun enterForeground(): Boolean = try {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIF_ID, n)
        true
    } catch (e: SecurityException) {
        failAndStop(SimError.PERMISSION); false
    } catch (e: Exception) {
        failAndStop(SimError.FOREGROUND_DENIED); false
    }

    private fun failAndStop(err: SimError) {
        SimulationHub.state.value = SimState(status = SimStatus.ERROR, error = err)
        stopSelf()
    }

    private fun notifyNow() {
        lastNotify = SystemClock.elapsedRealtime()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = SimulationHub.state.value
        val text = when {
            paused || s.status == SimStatus.PAUSED -> getString(R.string.notif_paused)
            s.status == SimStatus.COMPLETED -> getString(R.string.notif_completed)
            s.isRoute && s.totalM > 0 -> getString(
                R.string.notif_route, String.format("%.1f", s.speedKmh), ((s.traveledM / s.totalM) * 100).roundToInt().coerceIn(0, 100)
            )
            else -> getString(R.string.notif_point)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (engine != null) {
            if (paused) b.addAction(0, getString(R.string.resume), actionIntent(ACTION_RESUME))
            else b.addAction(0, getString(R.string.pause), actionIntent(ACTION_PAUSE))
        }
        b.addAction(0, getString(R.string.stop), actionIntent(ACTION_STOP))
        return b.build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, SimulationService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // ---- lifecycle -----------------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MegaGPS:simulation").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L) // safety cap: 10 h
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun shutdown(error: SimError = SimError.NONE) {
        loop?.cancel(); loop = null
        engine = null; holdPoint = null
        mock.stop()
        releaseWakeLock()
        SimulationHub.state.value =
            if (error == SimError.NONE) SimState() else SimState(status = SimStatus.ERROR, error = error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        mock.stop()
        releaseWakeLock()
        if (SimulationHub.state.value.status != SimStatus.ERROR) SimulationHub.state.value = SimState()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "simulation"
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.megagps.simulator.START"
        const val ACTION_PAUSE = "com.megagps.simulator.PAUSE"
        const val ACTION_RESUME = "com.megagps.simulator.RESUME"
        const val ACTION_STOP = "com.megagps.simulator.STOP"
        const val ACTION_RESTART = "com.megagps.simulator.RESTART"
        const val ACTION_REVERSE = "com.megagps.simulator.REVERSE"
        const val ACTION_SPEED = "com.megagps.simulator.SPEED"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SPEED = "speed"

        fun start(ctx: Context, cfg: SimConfig) {
            SimulationHub.pending = cfg
            ContextCompat.startForegroundService(ctx, Intent(ctx, SimulationService::class.java).setAction(ACTION_START))
        }

        fun command(ctx: Context, action: String, mode: MoveMode? = null, kmh: Double? = null) {
            val i = Intent(ctx, SimulationService::class.java).setAction(action)
            if (mode != null && kmh != null) i.putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_SPEED, kmh)
            ctx.startService(i)
        }
    }
}
