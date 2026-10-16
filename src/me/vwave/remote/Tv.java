package me.vwave.remote;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.ConnectException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * webOS "second screen" (SSAP) client, modelled on the flow observed in LG ThinQ 5.2.12500
 * (com.connectsdk3.service.webos.WebOSTVServiceSocketClient). See NOTICE.md for research provenance.
 */
final class Tv {
    enum State { DISCONNECTED, CONNECTING, PAIRING, READY }

    interface Listener {
        void onState(State state, String detail);
        void onVolume(int volume, boolean muted);
        /** The TV's own name (Settings → General → Devices → TV name), learned after connecting. */
        void onName(String name);
    }

    interface Callback {
        /** Runs on the main thread. Exactly one of payload / error is non-null. */
        void done(JSONObject payload, String error);
    }

    static final int SECURE_PORT = 3001;
    static final int LEGACY_PORT = 3000;
    /** A TV going to standby doesn't always close the socket, so ping it and give up after silence. */
    static final int PING_MS = 10_000;
    static final int SILENCE_MS = 25_000;

    private final Prefs prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger ids = new AtomicInteger(1);
    private final Map<Integer, Callback> pending = new ConcurrentHashMap<>();
    private final Set<Integer> subscriptions = ConcurrentHashMap.newKeySet();
    private final List<String> queuedFrames = new ArrayList<>();

    private WebSocket ws;
    private WebSocket pointer;
    private boolean pointerRequested;
    private PinningTrust trust;
    private State state = State.DISCONNECTED;
    private int registerId;
    private boolean turningOff;

    private final Runnable keepalive = new Runnable() {
        @Override public void run() {
            if (ws == null || !isReady()) return;
            if (System.currentTimeMillis() - ws.lastHeard() > SILENCE_MS) {
                drop(turningOff ? "TV is off" : "TV stopped responding");
                return;
            }
            ws.ping();
            main.postDelayed(this, PING_MS);
        }
    };

    Tv(Prefs prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
    }

    State state() {
        return state;
    }

    boolean isReady() {
        return state == State.READY;
    }

    // ---- connection ---------------------------------------------------------------------------

    void connect() {
        String host = prefs.host();
        if (host == null) return;
        disconnect();
        turningOff = false;
        setState(State.CONNECTING, null);
        open(host, SECURE_PORT);
    }

    void disconnect() {
        drop(null);
    }

    /** Closes our side and reports DISCONNECTED with {@code why}; a later onClose of the old socket is ignored. */
    private void drop(String why) {
        main.removeCallbacks(keepalive);
        if (ws != null) ws.close();
        ws = null;
        closePointer();
        failPending("Disconnected");
        if (state != State.DISCONNECTED) setState(State.DISCONNECTED, why);
    }

    private void open(String host, int port) {
        trust = new PinningTrust(prefs.certPin());
        SocketFactory factory = port == SECURE_PORT ? tlsFactory(trust) : SocketFactory.getDefault();
        final WebSocket[] self = new WebSocket[1];
        self[0] = new WebSocket(host, port, "/", factory, new WebSocket.Listener() {
            @Override public void onOpen() {
                main.post(() -> {
                    if (ws == self[0]) sendRegister();
                });
            }

            @Override public void onMessage(String text) {
                main.post(() -> {
                    if (ws == self[0]) handle(text);
                });
            }

            @Override public void onClose(Exception cause) {
                main.post(() -> {
                    if (ws != self[0]) return;
                    ws = null;
                    main.removeCallbacks(keepalive);
                    closePointer();
                    failPending("Disconnected");
                    // TVs from before webOS 4 only listen on plain ws://:3000.
                    if (port == SECURE_PORT && state == State.CONNECTING && cause instanceof ConnectException) {
                        open(host, LEGACY_PORT);
                        return;
                    }
                    setState(State.DISCONNECTED, turningOff ? "TV is off" : describe(cause));
                });
            }
        });
        ws = self[0];
        ws.connect();
    }

    private void sendRegister() {
        registerId = ids.getAndIncrement();
        JSONObject payload = new JSONObject();
        try {
            String key = prefs.clientKey();
            if (key != null) payload.put("client-key", key);
            payload.put("manifest", Manifest.lgMobile());
            ws.send(new JSONObject().put("type", "register").put("id", registerId).put("payload", payload).toString());
        } catch (JSONException ignored) {
        }
    }

    private void handle(String text) {
        JSONObject msg;
        try {
            msg = new JSONObject(text);
        } catch (JSONException e) {
            return;
        }
        String type = msg.optString("type");
        int id = msg.optInt("id", -1);
        JSONObject payload = msg.optJSONObject("payload");

        if (id == registerId && registerId != 0) {
            if ("registered".equals(type)) {
                onRegistered(payload);
            } else if ("response".equals(type) && payload != null && payload.has("pairingType")) {
                setState(State.PAIRING, payload.optString("pairingType"));
            } else if ("error".equals(type)) {
                String err = msg.optString("error", "Pairing failed");
                // A stale client-key gets rejected; forget it so the next attempt prompts again.
                prefs.setClientKey(null);
                if (ws != null) ws.close();
                ws = null;
                setState(State.DISCONNECTED, err);
            }
            return;
        }

        Callback cb = subscriptions.contains(id) ? pending.get(id) : pending.remove(id);
        if (cb == null) return;
        if ("error".equals(type)) {
            subscriptions.remove(id);
            pending.remove(id);
            cb.done(null, msg.optString("error", "Error"));
        } else if (payload != null && payload.has("returnValue") && !payload.optBoolean("returnValue")) {
            cb.done(null, payload.optString("errorText", "Request failed"));
        } else {
            cb.done(payload != null ? payload : new JSONObject(), null);
        }
    }

    private void onRegistered(JSONObject payload) {
        if (payload != null && payload.has("client-key")) prefs.setClientKey(payload.optString("client-key"));
        if (prefs.certPin() == null && trust != null && trust.seen != null) prefs.setCertPin(trust.seen);
        setState(State.READY, null);
        main.removeCallbacks(keepalive);
        main.postDelayed(keepalive, PING_MS);

        // Notice the TV being switched off by its own remote straight away. webOS 24 reports
        // processing "Request Power Off" at once, while state stays "Active" until "Suspend" ~6 s later.
        subscribe("ssap://com.webos.service.tvpower/power/getPowerState", (p, e) -> {
            if (p != null && isPoweringOff(p.optString("state"), p.optString("processing"))) drop("TV is off");
        });
        subscribe("ssap://audio/getVolume", (p, e) -> {
            if (p == null) return;
            JSONObject status = p.optJSONObject("volumeStatus");
            if (status != null) {
                listener.onVolume(status.optInt("volume", -1), status.optBoolean("muteStatus"));
            } else {
                listener.onVolume(p.optInt("volume", -1), p.optBoolean("muted"));
            }
        });
        // Remember MAC addresses so the power button can wake the TV later.
        request("ssap://com.webos.service.connectionmanager/getinfo", null, (p, e) -> {
            if (p == null) return;
            List<String> macs = new ArrayList<>();
            for (String k : new String[] {"wiredInfo", "wifiInfo"}) {
                JSONObject info = p.optJSONObject(k);
                String mac = info != null ? info.optString("macAddress") : "";
                if (mac.matches("(?i)([0-9a-f]{2}[:-]){5}[0-9a-f]{2}")) macs.add(mac);
            }
            if (!macs.isEmpty()) prefs.setMacs(macs);
        });
        try {
            request("ssap://settings/getSystemSettings",
                    new JSONObject().put("category", "network").put("keys", new JSONArray().put("deviceName")), (p, e) -> {
                        JSONObject settings = p != null ? p.optJSONObject("settings") : null;
                        String name = settings != null ? settings.optString("deviceName").trim() : "";
                        if (name.startsWith("[LG] ")) name = name.substring(5);
                        if (name.isEmpty()) return;
                        prefs.setName(name);
                        listener.onName(name);
                    });
            // Register this phone for Bluetooth wake-up, as ThinQ's wifiWakeupManager.registerDevice does.
            if (!prefs.wakeRegistered()) {
                request("ssap://com.webos.service.asc/registerDevice",
                        new JSONObject().put("type", "ThinQ").put("address", prefs.wakeAddress()), (p, e) -> {
                            if (p != null) prefs.setWakeRegistered(true);
                        });
            }
        } catch (JSONException ignored) {
        }
    }

    // ---- requests -----------------------------------------------------------------------------

    void powerOff() {
        if (!isReady()) return;
        turningOff = true;
        request("ssap://system/turnOff", null, (p, e) -> {
            if (p != null && turningOff) drop("TV is off");
        });
    }

    void request(String uri) {
        request(uri, null, null);
    }

    void request(String uri, JSONObject payload, Callback cb) {
        send("request", uri, payload, cb);
    }

    void subscribe(String uri, Callback cb) {
        Integer id = send("subscribe", uri, null, cb);
        if (id != null) subscriptions.add(id);
    }

    private Integer send(String type, String uri, JSONObject payload, Callback cb) {
        if (!isReady() || ws == null) {
            if (cb != null) cb.done(null, "Not connected");
            return null;
        }
        int id = ids.getAndIncrement();
        try {
            JSONObject msg = new JSONObject().put("type", type).put("id", id).put("uri", uri);
            if (payload != null) msg.put("payload", payload);
            if (cb != null) pending.put(id, cb);
            ws.send(msg.toString());
        } catch (JSONException ignored) {
        }
        return id;
    }

    private void failPending(String why) {
        List<Callback> cbs = new ArrayList<>(pending.values());
        pending.clear();
        subscriptions.clear();
        for (Callback cb : cbs) cb.done(null, why);
    }

    // ---- pointer socket (remote keys, touchpad) -----------------------------------------------

    /** Remote-control key, e.g. UP, DOWN, LEFT, RIGHT, ENTER, BACK, HOME, MENU, EXIT. */
    void button(String name) {
        pointerSend("type:button\nname:" + name + "\n\n");
    }

    /** Moves the TV's pointer by a relative amount, roughly in TV screen pixels. */
    void move(int dx, int dy) {
        pointerSend("type:move\ndx:" + dx + "\ndy:" + dy + "\ndown:0\n\n");
    }

    /** Clicks at the pointer position. */
    void click() {
        pointerSend("type:click\n\n");
    }

    void scroll(int dx, int dy) {
        pointerSend("type:scroll\ndx:" + dx + "\ndy:" + dy + "\n\n");
    }

    private void pointerSend(String frame) {
        if (!isReady()) return;
        if (pointer != null && pointer.isOpen()) {
            pointer.send(frame);
            return;
        }
        queuedFrames.add(frame);
        if (pointerRequested) return;
        pointerRequested = true;
        request("ssap://com.webos.service.networkinput/getPointerInputSocket", null, (p, e) -> {
            String path = p != null ? p.optString("socketPath") : "";
            if (path.isEmpty()) {
                pointerRequested = false;
                queuedFrames.clear();
                return;
            }
            openPointer(path);
        });
    }

    private void openPointer(String socketPath) {
        URI uri = URI.create(socketPath);
        boolean secure = "wss".equals(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : secure ? SECURE_PORT : LEGACY_PORT;
        String path = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        // Same TV, same certificate as the control socket.
        SocketFactory factory = secure ? tlsFactory(new PinningTrust(prefs.certPin())) : SocketFactory.getDefault();
        final WebSocket[] self = new WebSocket[1];
        self[0] = new WebSocket(uri.getHost(), port, path, factory, new WebSocket.Listener() {
            @Override public void onOpen() {
                main.post(() -> {
                    if (pointer != self[0]) return;
                    for (String f : queuedFrames) pointer.send(f);
                    queuedFrames.clear();
                });
            }

            @Override public void onMessage(String text) {
            }

            @Override public void onClose(Exception cause) {
                main.post(() -> {
                    if (pointer != self[0]) return;
                    pointer = null;
                    pointerRequested = false;
                });
            }
        });
        pointer = self[0];
        pointer.connect();
    }

    private void closePointer() {
        if (pointer != null) pointer.close();
        pointer = null;
        pointerRequested = false;
        queuedFrames.clear();
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void setState(State s, String detail) {
        state = s;
        listener.onState(s, detail);
    }

    static boolean isPoweringOff(String state, String processing) {
        return state.equals("Suspend") || state.equals("Active Standby") || state.equals("Power Off")
                || processing.startsWith("Request Power Off") || processing.equals("Request Suspend")
                || processing.equals("Request Active Standby") || processing.equals("Prepare Suspend");
    }

    private static String describe(Exception e) {
        if (e == null) return null;
        if (e instanceof ConnectException) return "TV not reachable (is it on?)";
        if (e.getCause() instanceof CertificateException || e instanceof javax.net.ssl.SSLHandshakeException) {
            return "TV certificate changed. Forget the TV and pair again.";
        }
        String m = e.getMessage();
        return m != null ? m : e.getClass().getSimpleName();
    }

    private static SocketFactory tlsFactory(X509TrustManager tm) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {tm}, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * webOS TVs present a self-signed certificate. Like ThinQ, trust it on first use and pin
     * its SHA-256 afterwards.
     */
    static final class PinningTrust implements X509TrustManager {
        private final String expected;
        volatile String seen;

        PinningTrust(String expected) {
            this.expected = expected;
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("No TV certificate");
            seen = sha256(chain[0].getEncoded());
            if (expected != null && !expected.equals(seen)) throw new CertificateException("TV certificate changed");
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Not a server");
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        private static String sha256(byte[] data) throws CertificateException {
            try {
                StringBuilder sb = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                throw new CertificateException(e);
            }
        }
    }

    /** Signed "LG Mobile" manifest, copied field-for-field from ThinQ's setLGMobileManifest(). */
    static final class Manifest {
        private static final String SIGNATURE =
                "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9."
                + "HWKCin7TZ40rKXxZeqq1O++3mTrs9OwE06f5/fNans0/mK8uPmIl4H/ADmal/ymoY8WjIMucwiKCqaPiJoU5wnRu+8rdC/iA+S76"
                + "ap+KYfRjg7uD55jCvAfYieal0NopEJmVTts5B8bXluZUlLFq53N7VfNGAt5VhjcgwUV6fmOIHIh7md/Y55I5lZTdMIGkuXC7AzCG"
                + "c0swNWupbblMwAn5aG4ljSXh4paUDFkRoq/g0cHN/jNHZrbHW2e0b5yYAtA8iQMdjqxlD9q1OqYxiiO4zWefX0004CFNtgPv/fzd"
                + "E6Eip53W6MCyX6l2+Yq9U9huh9qawI1QuSKx5cI14A==";

        private static final String[] SIGNED_PERMISSIONS = {
            "TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "READ_LGE_SDX",
            "READ_NOTIFICATIONS", "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT", "CONTROL_POWER",
            "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_UPDATE_INFO", "UPDATE_FROM_REMOTE_APP",
            "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME", "CONTROL_BLUETOOTH", "CHECK_BLUETOOTH_DEVICE",
            "CONTROL_VOICE", "ACCESS_STORAGE", "SECURE_ACCESS_PUSH", "SECURE_APP_TO_APP", "SECURE_CONTROL_CHANNEL_BLOCK",
            "SECURE_CONTROL_CHANNEL_GROUP", "SECURE_CONTROL_DISPLAY", "SECURE_CONTROL_FAVORITE_GROUP",
            "SECURE_CONTROL_POWER", "SECURE_CONTROL_TV_SCREEN", "SECURE_DELETE_SELECT_CHANNEL", "SECURE_LAUNCH",
            "SECURE_READ_INPUT_DEVICE_LIST", "SECURE_READ_INSTALLED_APPS", "SECURE_READ_NETWORK_STATE",
            "SECURE_READ_RUNNING_APPS", "SECURE_READ_STORAGE_DEVICE_LIST", "SECURE_READ_TV_CHANNEL_LIST",
            "SECURE_READ_TV_PROGRAM_INFO", "SECURE_RELEASE_CHANNEL_SKIP", "SECURE_SCAN_TV_CHANNELS",
            "SECURE_SET_CHANNEL_SKIP", "SECURE_WRITE_RECORDING_SCHEDULE", "CONTROL_DAFC_CALIBRATION",
        };

        private static final String[] PERMISSIONS = {
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_INPUT_MEDIA_PLAYBACK",
            "UPDATE_FROM_REMOTE_APP", "CONTROL_POWER", "READ_INSTALLED_APPS", "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_TV", "READ_INPUT_DEVICE_LIST",
            "READ_NETWORK_STATE", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "CONTROL_BLUETOOTH",
            "CHECK_BLUETOOTH_DEVICE", "CONTROL_USER_INFO", "CONTROL_TIMER_INFO", "READ_SETTINGS", "CONTROL_TV_SCREEN",
            "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS",
            "READ_TV_PROGRAM_INFO", "READ_STORAGE_DEVICE_LIST",
        };

        static JSONObject lgMobile() throws JSONException {
            JSONObject names = new JSONObject().put("", "LG Mobile").put("ko-KR", "LG Mobile").put("zxx-XX", "LG Mobile");
            JSONObject signed = new JSONObject()
                    .put("created", "20150330")
                    .put("appId", "com.lge.test")
                    .put("vendorId", "com.lge")
                    .put("localizedAppNames", names)
                    .put("localizedVendorNames", new JSONObject().put("", "LG Electronics"))
                    .put("permissions", new JSONArray(java.util.Arrays.asList(SIGNED_PERMISSIONS)))
                    .put("serial", "49d0233e26a3fc4d3113ddbc22a8686a");
            JSONArray signatures = new JSONArray()
                    .put(new JSONObject().put("signatureVersion", 1).put("signature", SIGNATURE));
            return new JSONObject()
                    .put("manifestVersion", 1)
                    .put("appVersion", "1.0")
                    .put("deviceName", "Wave Remote")
                    .put("allowFullPagePopup", true)
                    .put("allowFirstuseConnection", true)
                    .put("signed", signed)
                    .put("permissions", new JSONArray(java.util.Arrays.asList(PERMISSIONS)))
                    .put("signatures", signatures);
        }
    }
}
