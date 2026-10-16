package me.vwave.remote

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import expo.modules.kotlin.Promise
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** Expo boundary only: pairing, pins, SSAP and wake packets remain in the tested Java engine. */
class WaveRemoteModule : Module() {
  private val main = Handler(Looper.getMainLooper())
  private val network = Executors.newSingleThreadExecutor()
  private lateinit var prefs: Prefs
  private lateinit var tv: Tv
  private lateinit var ble: Ble
  private var volume = -1
  private var muted = false
  private var detail: String? = null
  private var wakeUntil = 0L
  private var generation = 0
  private var foreground = true
  /** When the TV last went off; the probe holds back meanwhile so it doesn't catch the TV mid-shutdown. */
  private var offAt = 0L
  private var probing = false
  /** An automatic attempt is open but not yet trusted; the UI keeps showing the TV as disconnected. */
  private var quietAttempt = false
  private var failedAttempts = 0
  /** The user disconnected on purpose (to add another TV); stay disconnected until they connect again. */
  private var paused = false
  private val confirm = Runnable {
    if (quietAttempt && tv.isReady) { quietAttempt = false; onState(Tv.State.READY, null) }
  }
  private val reconnect = Runnable { if (foreground && wakeUntil > System.currentTimeMillis()) tv.connect() }
  /** Reconnects on its own once a paired TV is back, e.g. after it was turned on with its own remote. */
  private val watch = Runnable { probe() }
  private val wakeTimeout = Runnable {
    if (wakeUntil != 0L) {
      stopWake()
      detail = "The TV didn't wake up. Check Mobile TV On in the TV's settings."
      emit()
      watchSoon()
    }
  }
  private var held: HeldKey? = null
  private val scheduler = object : HeldKey.Scheduler {
    override fun now() = android.os.SystemClock.uptimeMillis()
    override fun post(task: Runnable, delayMs: Long) { main.postDelayed(task, delayMs) }
    override fun remove(task: Runnable) { main.removeCallbacks(task) }
  }

  companion object {
    private const val WATCH_MS = 3000L
    /** A TV in standby can keep its port open; probing it this often is still cheap. */
    private const val MAX_WATCH_MS = 10000L
    /** How long an automatic connection must hold before the app shows it; covers the power-state reply. */
    private const val CONFIRM_MS = 1500L
    private const val PROBE_TIMEOUT_MS = 1500
    /** webOS 24 keeps its socket open for about 6 s after "Request Power Off". */
    private const val OFF_QUIET_MS = 12000L
    private var active: WeakReference<WaveRemoteModule>? = null
    internal fun pointer(action: (Tv) -> Unit) {
      val module = active?.get() ?: return
      if (module::tv.isInitialized && module.tv.isReady) action(module.tv)
    }
    @JvmStatic fun handleVolumeKey(event: KeyEvent): Boolean {
      val module = active?.get() ?: return false
      if (!module.foreground || module.quietAttempt || !module::tv.isInitialized || !module.tv.isReady) return false
      val uri = when (event.keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> "ssap://audio/volumeUp"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "ssap://audio/volumeDown"
        else -> return false
      }
      if (event.action == KeyEvent.ACTION_DOWN) module.tv.request(uri)
      return true
    }
  }

  private fun prepare() {
    if (::tv.isInitialized) return
    val context = requireNotNull(appContext.reactContext)
    prefs = Prefs(context)
    tv = Tv(prefs, object : Tv.Listener {
      override fun onState(state: Tv.State, detail: String?) { this@WaveRemoteModule.onState(state, detail) }
      override fun onVolume(value: Int, muted: Boolean) { this@WaveRemoteModule.onVolume(value, muted) }
      override fun onName(name: String) { this@WaveRemoteModule.onName(name) }
    })
    ble = Ble(context)
    active = WeakReference(this)
  }

  override fun definition() = ModuleDefinition {
    Name("WaveRemote")
    Events("onChange")
    View(TvTouchpadView::class) {
      Prop("naturalScrolling") { view: TvTouchpadView, natural: Boolean -> view.setNaturalScrolling(natural) }
    }

    AsyncFunction("snapshot") {
      prepare()
      snapshot()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("connect") { host: String, name: String, btMac: String? ->
      prepare()
      require(host.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) && host.split('.').all { it.toInt() in 0..255 }) { "Enter a valid IPv4 address" }
      generation++
      stopWake()
      stopHeld()
      volume = -1
      muted = false
      prefs.setTv(host, name)
      if (btMac != null && btMac.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) prefs.setBtMac(btMac)
      userConnect()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("rename") { host: String, label: String ->
      prepare()
      require(label.trim().length <= 40) { "Use a name of 40 characters or fewer" }
      prefs.setLabel(host, label)
      emit()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("reconnect") { prepare(); stopWake(); userConnect() }.runOnQueue(Queues.MAIN)
    AsyncFunction("disconnect") { prepare(); paused = true; quietAttempt = false; stopHeld(); stopWake(); tv.disconnect() }.runOnQueue(Queues.MAIN)
    AsyncFunction("forget") {
      prepare()
      generation++
      stopHeld(); stopWake(); tv.disconnect(); prefs.forget()
      volume = -1; muted = false; detail = null
      emit()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("discover") {
      val context = requireNotNull(appContext.reactContext)
      Net.discover(context, 2500).map { (host, found) ->
        mapOf("host" to host, "name" to found.name, "btMac" to found.btMac)
      }
    }

    AsyncFunction("request") { uri: String, payload: Map<String, Any?>?, promise: Promise ->
      prepare()
      val timeout = Runnable { promise.reject("TIMEOUT", "The TV didn't respond. Try again.", null) }
      var completed = false
      val guardedTimeout = Runnable { if (!completed) { completed = true; timeout.run() } }
      main.postDelayed(guardedTimeout, 12000)
      tv.request(uri, payload?.let { JSONObject(it) }) { result, error ->
        if (!completed) {
          completed = true
          main.removeCallbacks(guardedTimeout)
          if (error != null) promise.reject("TV_REQUEST", error, null)
          else promise.resolve(jsonMap(result ?: JSONObject()))
        }
      }
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("button") { key: String -> prepare(); tv.button(key) }.runOnQueue(Queues.MAIN)
    AsyncFunction("move") { dx: Int, dy: Int -> prepare(); tv.move(dx, dy) }.runOnQueue(Queues.MAIN)
    AsyncFunction("scroll") { dx: Int, dy: Int -> prepare(); tv.scroll(dx, dy) }.runOnQueue(Queues.MAIN)
    AsyncFunction("click") { prepare(); tv.click() }.runOnQueue(Queues.MAIN)
    AsyncFunction("startHeld") { key: String, pointer: Boolean ->
      prepare(); stopHeld()
      if (tv.isReady) {
        held = HeldKey(scheduler, { if (pointer) tv.button(key) else tv.request(key) }, pointer)
        held?.start()
      }
    }.runOnQueue(Queues.MAIN)
    AsyncFunction("stopHeld") { stopHeld() }.runOnQueue(Queues.MAIN)
    AsyncFunction("preferences") { touchpad: Boolean, natural: Boolean ->
      prepare(); stopHeld(); prefs.setTouchpad(touchpad); prefs.setNaturalScrolling(natural); emit()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("power") { bluetooth: Boolean, promise: Promise ->
      prepare()
      paused = false
      if (tv.isReady) {
        tv.powerOff()
        promise.resolve()
      } else {
        val macs = prefs.macs()
        val canBle = prefs.btMac() != null && prefs.wakeRegistered()
        if (macs.isEmpty() && !canBle) {
          promise.reject("WAKE_UNAVAILABLE", "Turn on and connect to the TV once to learn its wake-up addresses.", null)
        } else {
          stopWake()
          wakeUntil = System.currentTimeMillis() + 40000
          detail = null
          if (bluetooth && canBle) detail = ble.start(prefs.wakeAddress(), prefs.btMac())
          val host = prefs.host()
          val token = generation
          network.execute {
            try { if (macs.isNotEmpty()) Net.wake(macs, host) }
            catch (error: Exception) {
              main.post { if (token == generation && wakeUntil != 0L) { detail = "Wake-on-LAN failed: ${error.message}"; emit() } }
            }
          }
          main.postDelayed(reconnect, 1500)
          main.postDelayed(wakeTimeout, 40000)
          emit()
          promise.resolve()
        }
      }
    }.runOnQueue(Queues.MAIN)

    OnActivityEntersForeground {
      main.post {
        foreground = true
        if (::tv.isInitialized && prefs.host() != null && tv.state() == Tv.State.DISCONNECTED) userConnect()
      }
    }
    OnActivityEntersBackground {
      main.post { foreground = false; main.removeCallbacks(watch); if (::tv.isInitialized) { stopHeld(); stopWake(); tv.disconnect() } }
    }
    OnDestroy {
      main.post {
        generation++
        foreground = false
        main.removeCallbacks(watch)
        if (::tv.isInitialized) { stopHeld(); stopWake(); tv.disconnect() }
        if (active?.get() === this@WaveRemoteModule) active = null
        network.shutdownNow()
      }
    }
  }

  private fun stopHeld() { held?.stop(); held = null }
  private fun stopWake() {
    wakeUntil = 0
    main.removeCallbacks(reconnect)
    main.removeCallbacks(wakeTimeout)
    if (::ble.isInitialized) ble.stop()
  }

  private fun onState(state: Tv.State, info: String?) {
    if (quietAttempt) {
      when (state) {
        Tv.State.READY -> { main.removeCallbacks(confirm); main.postDelayed(confirm, CONFIRM_MS); return }
        Tv.State.CONNECTING -> return
        // The TV wants its prompt accepted; show that as usual.
        Tv.State.PAIRING -> quietAttempt = false
        Tv.State.DISCONNECTED -> {
          quietAttempt = false
          main.removeCallbacks(confirm)
          // Didn't hold: the TV is in standby or still shutting down. Keep showing what was there
          // and back off, unless the attempt found something only the user can fix.
          if (info?.contains("certificate") != true) { failedAttempts++; watchSoon(); return }
        }
      }
    }
    detail = info
    if (state != Tv.State.READY) stopHeld()
    if (state == Tv.State.READY) {
      failedAttempts = 0
      stopWake()
      if (prefs.btMac() == null) {
        val host = prefs.host()
        val token = generation
        network.execute {
          val found = Net.discover(requireNotNull(appContext.reactContext), 2500, host)[host]
          main.post { if (token == generation && host == prefs.host() && found?.btMac != null) { prefs.setBtMac(found.btMac); emit() } }
        }
      }
    } else if (state == Tv.State.DISCONNECTED && wakeUntil > System.currentTimeMillis()) {
      main.removeCallbacks(reconnect)
      main.postDelayed(reconnect, 2000)
    } else if (state == Tv.State.DISCONNECTED) {
      if (info == "TV is off") offAt = android.os.SystemClock.uptimeMillis()
      // A changed certificate needs the user; retrying can't fix it.
      if (info?.contains("certificate") != true) watchSoon()
    }
    emit()
  }

  private fun userConnect() {
    paused = false
    quietAttempt = false
    failedAttempts = 0
    main.removeCallbacks(confirm)
    tv.connect()
  }

  private fun watchSoon() {
    main.removeCallbacks(watch)
    val backoff = minOf(WATCH_MS shl minOf(failedAttempts, 4), MAX_WATCH_MS)
    val quiet = offAt + OFF_QUIET_MS - android.os.SystemClock.uptimeMillis()
    main.postDelayed(watch, maxOf(backoff, quiet))
  }

  private fun probe() {
    if (!foreground || paused || probing || !::tv.isInitialized || tv.state() != Tv.State.DISCONNECTED || wakeUntil != 0L) return
    val host = prefs.host() ?: return
    if (prefs.clientKey() == null) return // Pairing needs the user at the TV; don't start it unasked.
    val legacy = prefs.certPin() == null
    val token = generation
    probing = true
    network.execute {
      val up = Net.reachable(host, Tv.SECURE_PORT, PROBE_TIMEOUT_MS) || (legacy && Net.reachable(host, Tv.LEGACY_PORT, PROBE_TIMEOUT_MS))
      main.post {
        probing = false
        if (token != generation || host != prefs.host() || !foreground) return@post
        if (tv.state() != Tv.State.DISCONNECTED || wakeUntil != 0L) return@post
        if (up) { quietAttempt = true; tv.connect() } else watchSoon()
      }
    }
  }
  private fun onVolume(value: Int, mute: Boolean) { volume = value; muted = mute; emit() }
  private fun onName(name: String) { emit() }

  private fun snapshot(): Map<String, Any?> = mapOf(
    "host" to prefs.host(), "name" to (prefs.host()?.let { prefs.label(it) } ?: prefs.name()), "paired" to (prefs.clientKey() != null),
    "state" to (if (quietAttempt) "disconnected" else tv.state().name.lowercase()), "detail" to detail,
    "volume" to volume, "muted" to muted, "touchpad" to prefs.touchpad(),
    "naturalScrolling" to prefs.naturalScrolling(), "waking" to (wakeUntil != 0L),
    "canWakeBluetooth" to (prefs.btMac() != null && prefs.wakeRegistered()),
    "tvs" to prefs.hosts().filter { prefs.paired(it) || it == prefs.host() }
      .map { mapOf("host" to it, "name" to (prefs.label(it) ?: prefs.name(it)), "tvName" to prefs.name(it),
        "label" to prefs.label(it), "paired" to prefs.paired(it)) }
  )
  private fun emit() { sendEvent("onChange", snapshot()) }
  private fun jsonMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { jsonValue(value.opt(it)) }
  private fun jsonValue(value: Any?): Any? = when (value) {
    JSONObject.NULL -> null
    is JSONObject -> jsonMap(value)
    is JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
    else -> value
  }
}
