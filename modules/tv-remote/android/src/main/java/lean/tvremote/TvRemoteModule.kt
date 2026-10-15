package lean.tvremote

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
class TvRemoteModule : Module() {
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
  private val reconnect = Runnable { if (foreground && wakeUntil > System.currentTimeMillis()) tv.connect() }
  private val wakeTimeout = Runnable {
    if (wakeUntil != 0L) {
      stopWake()
      detail = "The TV didn't wake up. Check Mobile TV On in the TV's settings."
      emit()
    }
  }
  private var held: HeldKey? = null
  private val scheduler = object : HeldKey.Scheduler {
    override fun now() = android.os.SystemClock.uptimeMillis()
    override fun post(task: Runnable, delayMs: Long) { main.postDelayed(task, delayMs) }
    override fun remove(task: Runnable) { main.removeCallbacks(task) }
  }

  companion object {
    private var active: WeakReference<TvRemoteModule>? = null
    internal fun pointer(action: (Tv) -> Unit) {
      val module = active?.get() ?: return
      if (module::tv.isInitialized && module.tv.isReady) action(module.tv)
    }
    @JvmStatic fun handleVolumeKey(event: KeyEvent): Boolean {
      val module = active?.get() ?: return false
      if (!module.foreground || !module::tv.isInitialized || !module.tv.isReady) return false
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
      override fun onState(state: Tv.State, detail: String?) { this@TvRemoteModule.onState(state, detail) }
      override fun onVolume(value: Int, muted: Boolean) { this@TvRemoteModule.onVolume(value, muted) }
      override fun onName(name: String) { this@TvRemoteModule.onName(name) }
    })
    ble = Ble(context)
    active = WeakReference(this)
  }

  override fun definition() = ModuleDefinition {
    Name("TvRemote")
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
      tv.connect()
    }.runOnQueue(Queues.MAIN)

    AsyncFunction("reconnect") { prepare(); stopWake(); tv.connect() }.runOnQueue(Queues.MAIN)
    AsyncFunction("disconnect") { prepare(); stopHeld(); stopWake(); tv.disconnect() }.runOnQueue(Queues.MAIN)
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
        if (::tv.isInitialized && prefs.host() != null && tv.state() == Tv.State.DISCONNECTED) tv.connect()
      }
    }
    OnActivityEntersBackground {
      main.post { foreground = false; if (::tv.isInitialized) { stopHeld(); stopWake(); tv.disconnect() } }
    }
    OnDestroy {
      main.post {
        generation++
        foreground = false
        if (::tv.isInitialized) { stopHeld(); stopWake(); tv.disconnect() }
        if (active?.get() === this@TvRemoteModule) active = null
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
    detail = info
    if (state != Tv.State.READY) stopHeld()
    if (state == Tv.State.READY) {
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
    }
    emit()
  }
  private fun onVolume(value: Int, mute: Boolean) { volume = value; muted = mute; emit() }
  private fun onName(name: String) { emit() }

  private fun snapshot(): Map<String, Any?> = mapOf(
    "host" to prefs.host(), "name" to prefs.name(), "paired" to (prefs.clientKey() != null),
    "state" to tv.state().name.lowercase(), "detail" to detail,
    "volume" to volume, "muted" to muted, "touchpad" to prefs.touchpad(),
    "naturalScrolling" to prefs.naturalScrolling(), "waking" to (wakeUntil != 0L),
    "canWakeBluetooth" to (prefs.btMac() != null && prefs.wakeRegistered())
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
