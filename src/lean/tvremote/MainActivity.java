package lean.tvremote;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity implements Tv.Listener {
    private static final int BG = 0xFF0F1115;
    private static final int SURFACE = 0xFF1E222A;
    private static final int SURFACE_HI = 0xFF2A2F3A;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int MUTED = 0xFF8A93A6;
    private static final int ACCENT = 0xFF3D7BFF;
    private static final int POWER = 0xFFFF5A5F;
    /** How long to keep trying to reach a TV that is being woken. */
    private static final int WAKE_SECONDS = 40;
    private static final int REQUEST_BLE = 1;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HeldKey.Scheduler keyScheduler = new HeldKey.Scheduler() {
        public long now() { return android.os.SystemClock.uptimeMillis(); }
        public void post(Runnable task, long delayMs) { main.postDelayed(task, delayMs); }
        public void remove(Runnable task) { main.removeCallbacks(task); }
    };
    private HeldKey heldKey;
    private View heldView;
    private Prefs prefs;
    private Tv tv;
    private Ble ble;

    private boolean remoteShown;
    private TextView title;
    private TextView status;
    private TextView muteButton;
    private FrameLayout padArea;
    private TextView modeButtons, modeTouchpad;
    private int volume = -1;
    private boolean muted;
    /** While waking: when to give up reconnecting. 0 when not waking. */
    private long wakeUntil;
    private String wakeNote;

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = new Prefs(this);
        tv = new Tv(prefs, this);
        ble = new Ble(this);
        if (prefs.host() != null && prefs.clientKey() != null) showRemote();
        else showSetup();
        debugIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        debugIntent(intent);
    }

    /** Debug builds only: {@code adb shell am start -n lean.tvremote/.MainActivity --es wake wol|ble} tests one wake path. */
    private void debugIntent(Intent intent) {
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 || intent == null) return;
        String w = intent.getStringExtra("wake");
        if (w != null && remoteShown) main.post(() -> wake(!"ble".equals(w), !"wol".equals(w)));
    }

    @Override protected void onStart() {
        super.onStart();
        if (prefs.host() != null && tv.state() == Tv.State.DISCONNECTED) tv.connect();
    }

    @Override protected void onStop() {
        super.onStop();
        stopHeldKey();
        stopWaking();
        main.removeCallbacksAndMessages(null);
        tv.disconnect();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (remoteShown && tv.isReady() && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                tv.request(code == KeyEvent.KEYCODE_VOLUME_UP ? "ssap://audio/volumeUp" : "ssap://audio/volumeDown");
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- Tv.Listener --------------------------------------------------------------------------

    @Override public void onState(Tv.State state, String detail) {
        if (state != Tv.State.READY) stopHeldKey();
        if (!remoteShown) {
            if (state == Tv.State.READY) {
                showRemote();
                return;
            }
            if (status != null) status.setText(setupStatus(state, detail));
            return;
        }
        if (wakeUntil != 0 && state != Tv.State.READY) {
            if (state == Tv.State.DISCONNECTED) {
                if (System.currentTimeMillis() < wakeUntil) {
                    main.postDelayed(tv::connect, 2000);
                } else {
                    stopWaking();
                    renderStatus(state, "The TV didn't wake up");
                    return;
                }
            }
            status.setText(wakeNote != null ? "Waking TV… " + wakeNote : "Waking TV…");
            status.setTextColor(TEXT);
            return;
        }
        if (state == Tv.State.READY) {
            stopWaking();
            if (prefs.btMac() == null) learnBluetoothMac();
        }
        renderStatus(state, detail);
    }

    @Override public void onName(String name) {
        if (title != null) title.setText(name);
    }

    @Override public void onVolume(int volume, boolean muted) {
        this.volume = volume;
        this.muted = muted;
        if (remoteShown && tv.isReady()) renderStatus(Tv.State.READY, null);
    }

    private String setupStatus(Tv.State state, String detail) {
        switch (state) {
            case CONNECTING: return "Connecting…";
            case PAIRING: return "Accept the connection request on your TV, using the TV's own remote.";
            default: return detail != null ? detail : "";
        }
    }

    private void renderStatus(Tv.State state, String detail) {
        if (title == null) return;
        title.setText(prefs.name() != null ? prefs.name() : "LG TV");
        String s;
        switch (state) {
            case READY:
                s = volume < 0 ? "Connected" : muted ? "Connected · Muted" : "Connected · Volume " + volume;
                break;
            case CONNECTING: s = "Connecting…"; break;
            case PAIRING: s = "Accept the request on your TV"; break;
            default:
                s = "TV is off".equals(detail) ? "TV is off · Power turns it on"
                        : (detail != null ? detail : "Disconnected") + " · tap to retry";
        }
        status.setText(s);
        status.setTextColor(state == Tv.State.READY ? MUTED : state == Tv.State.DISCONNECTED ? POWER : TEXT);
        if (muteButton != null) muteButton.setTextColor(muted ? POWER : TEXT);
    }

    // ---- setup screen -------------------------------------------------------------------------

    private void showSetup() {
        remoteShown = false;
        title = null;
        muteButton = null;

        LinearLayout root = column();
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        root.addView(text("Pick your TV", 28, TEXT, true));
        TextView hint = text("Your phone and TV need to be on the same Wi-Fi network.", 15, MUTED, false);
        hint.setPadding(0, dp(8), 0, dp(20));
        root.addView(hint);

        final LinearLayout found = column();
        root.addView(found);

        TextView again = pill("Search again", () -> search(found));
        root.addView(again, lp(-1, 52, 0, 12, 0, 0));

        TextView or = text("Or enter its IP address (TV Settings → Network)", 14, MUTED, false);
        or.setPadding(0, dp(28), 0, dp(8));
        root.addView(or);

        final EditText ip = new EditText(this);
        ip.setHint("192.168.1.50");
        ip.setTextColor(TEXT);
        ip.setHintTextColor(0xFF5A6273);
        ip.setSingleLine();
        ip.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        ip.setImeOptions(EditorInfo.IME_ACTION_GO);
        if (prefs.host() != null) ip.setText(prefs.host());
        root.addView(ip, lp(-1, -2, 0, 0, 0, 0));

        Runnable connect = () -> {
            String host = ip.getText().toString().trim();
            if (!host.matches("[0-9a-zA-Z.:-]+")) {
                toast("Enter the TV's IP address");
                return;
            }
            choose(host, null);
        };
        ip.setOnEditorActionListener((v, actionId, ev) -> {
            connect.run();
            return true;
        });
        root.addView(pill("Connect", connect), lp(-1, 52, 0, 12, 0, 0));

        status = text("", 15, TEXT, false);
        status.setPadding(0, dp(24), 0, 0);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContent(scroll);
        search(found);
    }

    private void search(final LinearLayout into) {
        into.removeAllViews();
        into.addView(text("Searching…", 16, MUTED, false), lp(-1, -2, 0, 0, 0, 8));
        new Thread(() -> {
            final Map<String, Net.Found> tvs = Net.discover(this, 3000);
            runOnUiThread(() -> {
                if (remoteShown) return;
                into.removeAllViews();
                if (tvs.isEmpty()) {
                    into.addView(text("No TVs found. Make sure the TV is on.", 16, MUTED, false), lp(-1, -2, 0, 0, 0, 8));
                    return;
                }
                for (Map.Entry<String, Net.Found> e : tvs.entrySet()) {
                    final String ip = e.getKey(), name = e.getValue().name, bt = e.getValue().btMac;
                    TextView b = pill(name + "\n" + ip, () -> {
                        choose(ip, name);
                        if (bt != null) prefs.setBtMac(bt);
                    });
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(dp(20), dp(12), dp(20), dp(12));
                    into.addView(b, lp(-1, -2, 0, 0, 0, 10));
                }
            });
        }).start();
    }

    private void choose(String host, String name) {
        prefs.setTv(host, name != null ? name : prefs.name() != null && host.equals(prefs.host()) ? prefs.name() : "LG TV");
        tv.connect();
    }

    // ---- remote screen ------------------------------------------------------------------------

    private void showRemote() {
        remoteShown = true;
        LinearLayout root = column();
        root.setPadding(dp(16), dp(8), dp(16), dp(12));

        LinearLayout header = row();
        LinearLayout titles = column();
        title = text("", 19, TEXT, true);
        title.setSingleLine();
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status = text("", 12, MUTED, false);
        status.setSingleLine();
        titles.addView(title);
        titles.addView(status);
        titles.setOnClickListener(v -> {
            if (tv.state() == Tv.State.DISCONNECTED) tv.connect();
        });
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("⋯", 22, SURFACE, this::showMenu), lp(48, 48, 8, 0, 0, 0));
        root.addView(header, lp(-1, 48, 0, 0, 0, 12));

        LinearLayout top = row();
        top.addView(icon(R.drawable.ic_power, POWER, "Power", this::power), weighted(48));
        top.addView(button("Inputs", 15, SURFACE, this::inputs), weighted(48));
        muteButton = button("Mute", 15, SURFACE, this::toggleMute);
        top.addView(muteButton, weighted(48));
        root.addView(top, lp(-1, 48, 0, 0, 0, 12));

        LinearLayout controls = row();
        controls.addView(rocker("VOL", "ssap://audio/volumeUp", "ssap://audio/volumeDown"), lp(64, 144, 0, 0, 0, 0));
        LinearLayout shortcuts = column();
        shortcuts.addView(button("Apps", 15, SURFACE, this::apps), lp(-1, 66, 0, 0, 0, 12));
        shortcuts.addView(button("Keyboard", 15, SURFACE, this::keyboard), lp(-1, 66, 0, 0, 0, 0));
        LinearLayout.LayoutParams shortcutLayout = new LinearLayout.LayoutParams(0, dp(144), 1);
        shortcutLayout.setMargins(dp(12), 0, dp(12), 0);
        controls.addView(shortcuts, shortcutLayout);
        controls.addView(rocker("CH", "ssap://tv/channelUp", "ssap://tv/channelDown"), lp(64, 144, 0, 0, 0, 0));
        root.addView(controls, lp(-1, 144, 0, 0, 0, 12));

        LinearLayout media = row();
        media.addView(icon(R.drawable.ic_rewind, TEXT, "Rewind", () -> tv.request("ssap://media.controls/rewind")), weighted(48));
        media.addView(icon(R.drawable.ic_play, TEXT, "Play", () -> tv.request("ssap://media.controls/play")), weighted(48));
        media.addView(icon(R.drawable.ic_pause, TEXT, "Pause", () -> tv.request("ssap://media.controls/pause")), weighted(48));
        media.addView(icon(R.drawable.ic_forward, TEXT, "Fast forward", () -> tv.request("ssap://media.controls/fastForward")), weighted(48));
        root.addView(media, lp(-1, 48, 0, 0, 0, 12));

        LinearLayout modes = row();
        modes.setBackground(rounded(SURFACE, 24));
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modeButtons = button("Buttons", 14, SURFACE, () -> setControlMode(false));
        modeTouchpad = button("Touchpad", 14, SURFACE, () -> setControlMode(true));
        modes.addView(modeButtons, new LinearLayout.LayoutParams(0, dp(40), 1));
        modes.addView(modeTouchpad, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams modeLayout = lp(240, 48, 0, 0, 0, 12);
        modeLayout.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(modes, modeLayout);
        padArea = new FrameLayout(this);
        LinearLayout.LayoutParams padLayout = new LinearLayout.LayoutParams(-1, 0, 1);
        padLayout.bottomMargin = dp(72);
        root.addView(padArea, padLayout);
        refreshControls();

        // Keep navigation below a noninteractive landing zone for stray finger taps.
        LinearLayout nav = row();
        nav.addView(button("Back", 16, SURFACE_HI, () -> tv.button("BACK")), weighted(52));
        nav.addView(button("Home", 16, SURFACE_HI, () -> tv.button("HOME")), weighted(52));
        nav.addView(button("Settings", 16, SURFACE_HI, () -> tv.button("MENU")), weighted(52));
        root.addView(nav, lp(-1, 52, 0, 0, 0, 0));

        FrameLayout screen = new FrameLayout(this);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContent(screen);
        renderStatus(tv.state(), null);
    }

    private void refreshControls() {
        stopHeldKey();
        boolean touchpad = prefs.touchpad();
        modeButtons.setBackground(ripple(touchpad ? SURFACE : SURFACE_HI, 20));
        modeTouchpad.setBackground(ripple(touchpad ? SURFACE_HI : SURFACE, 20));
        modeButtons.setTextColor(touchpad ? MUTED : TEXT);
        modeTouchpad.setTextColor(touchpad ? TEXT : MUTED);
        modeButtons.setSelected(!touchpad);
        modeTouchpad.setSelected(touchpad);
        padArea.removeAllViews();
        View pad = touchpad ? new Touchpad(this, SURFACE, MUTED, prefs.naturalScrolling(), new Touchpad.Pointer() {
            @Override public void move(int dx, int dy) { tv.move(dx, dy); }
            @Override public void click() { tv.click(); }
            @Override public void scroll(int dx, int dy) { tv.scroll(dx, dy); }
        }) : dpad();
        padArea.addView(pad, new FrameLayout.LayoutParams(touchpad ? -1 : -2,
                touchpad ? -1 : -2, Gravity.CENTER));
    }

    private View dpad() {
        LinearLayout pad = column();
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        int s = 84;
        String[][] keys = {{null, "UP", null}, {"LEFT", "ENTER", "RIGHT"}, {null, "DOWN", null}};
        String[][] labels = {{null, "▲", null}, {"◀", "OK", "▶"}, {null, "▼", null}};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER);
            for (int c = 0; c < 3; c++) {
                final String key = keys[r][c];
                if (key == null) {
                    row.addView(new View(this), lp(s, s, 4, 4, 4, 4));
                    continue;
                }
                boolean ok = "ENTER".equals(key);
                TextView b = button(labels[r][c], ok ? 20 : 18, ok ? ACCENT : SURFACE, null);
                b.setBackground(ripple(ok ? ACCENT : SURFACE, s / 2));
                if (ok) b.setOnClickListener(v -> tap(v, () -> tv.button(key)));
                else repeating(b, () -> tv.button(key), true);
                row.addView(b, lp(s, s, 4, 4, 4, 4));
            }
            pad.addView(row);
        }
        return pad;
    }

    private View rocker(String label, final String up, final String down) {
        LinearLayout col = column();
        col.setBackground(rounded(SURFACE, 42));
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView plus = button("+", 26, SURFACE, null);
        plus.setBackground(ripple(SURFACE, 42));
        repeating(plus, () -> tv.request(up));
        TextView minus = button("−", 26, SURFACE, null);
        minus.setBackground(ripple(SURFACE, 42));
        repeating(minus, () -> tv.request(down));
        TextView name = text(label, 12, MUTED, true);
        name.setGravity(Gravity.CENTER);
        col.addView(plus, new LinearLayout.LayoutParams(-1, 0, 1));
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        col.addView(minus, new LinearLayout.LayoutParams(-1, 0, 1));
        return col;
    }

    // ---- actions ------------------------------------------------------------------------------

    private void power() {
        if (tv.isReady()) {
            tv.powerOff();
            return;
        }
        wake(true, true);
    }

    /**
     * Turns the TV on the two ways ThinQ does (ConnectSdkHandler.control, feature.power ON):
     * Wake-on-LAN to its learned MACs, and a Bluetooth LE wake-up advertisement. Then keeps
     * reconnecting until it answers.
     */
    private void wake(boolean wol, boolean bluetooth) {
        final List<String> macs = prefs.macs();
        boolean canBle = prefs.btMac() != null && prefs.wakeRegistered();
        if (macs.isEmpty() && !canBle) {
            toast("Turn the TV on with its own remote once. After that this button can wake it.");
            return;
        }
        wakeNote = null;
        if (wol && !macs.isEmpty()) {
            final String host = prefs.host();
            new Thread(() -> {
                try {
                    Net.wake(macs, host);
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Wake-on-LAN failed: " + e.getMessage()));
                }
            }).start();
        }
        if (bluetooth && canBle) {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.BLUETOOTH_ADVERTISE}, REQUEST_BLE);
            } else {
                startBle();
            }
        }
        wakeUntil = System.currentTimeMillis() + WAKE_SECONDS * 1000L;
        status.setText("Waking TV…");
        status.setTextColor(TEXT);
        main.postDelayed(() -> {
            if (wakeUntil != 0 && tv.state() == Tv.State.DISCONNECTED) tv.connect();
        }, 1500);
    }

    private void startBle() {
        String err = ble.start(prefs.wakeAddress(), prefs.btMac());
        if (err != null) {
            wakeNote = "(" + err + ")";
            if (wakeUntil != 0) status.setText("Waking TV… " + wakeNote);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != REQUEST_BLE) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (wakeUntil != 0) startBle();
        } else {
            wakeNote = "(Bluetooth wake-up needs the Nearby devices permission)";
            if (wakeUntil != 0) status.setText("Waking TV… " + wakeNote);
        }
    }

    private void stopWaking() {
        wakeUntil = 0;
        wakeNote = null;
        ble.stop();
    }

    /** The TV's Bluetooth MAC is only in its SSDP description; fetch it once for BLE wake-up. */
    private void learnBluetoothMac() {
        final String host = prefs.host();
        new Thread(() -> {
            Net.Found f = Net.discover(this, 2500, host).get(host);
            if (f != null && f.btMac != null) runOnUiThread(() -> {
                if (host.equals(prefs.host())) prefs.setBtMac(f.btMac);
            });
        }).start();
    }

    private void toggleMute() {
        try {
            tv.request("ssap://audio/setMute", new JSONObject().put("mute", !muted), this::reportError);
        } catch (JSONException ignored) {
        }
    }

    private void inputs() {
        tv.request("ssap://tv/getExternalInputList", null, (p, e) -> {
            if (e != null) {
                toast(e);
                return;
            }
            JSONArray devices = p.optJSONArray("devices");
            if (devices == null || devices.length() == 0) {
                toast("No inputs reported");
                return;
            }
            final List<String> ids = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < devices.length(); i++) {
                JSONObject d = devices.optJSONObject(i);
                if (d == null) continue;
                ids.add(d.optString("id"));
                names.add(d.optString("label", d.optString("id")) + (d.optBoolean("connected", true) ? "" : "  (nothing connected)"));
            }
            pick("Input", names, i -> {
                try {
                    tv.request("ssap://tv/switchInput", new JSONObject().put("inputId", ids.get(i)), this::reportError);
                } catch (JSONException ignored) {
                }
            });
        });
    }

    private void apps() {
        tv.request("ssap://com.webos.applicationManager/listLaunchPoints", null, (p, e) -> {
            if (e != null) {
                toast(e);
                return;
            }
            JSONArray points = p.optJSONArray("launchPoints");
            if (points == null || points.length() == 0) {
                toast("No apps reported");
                return;
            }
            final List<JSONObject> apps = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < points.length(); i++) {
                JSONObject a = points.optJSONObject(i);
                if (a == null || a.optString("id").isEmpty()) continue;
                apps.add(a);
                names.add(a.optString("title", a.optString("id")));
            }
            pick("Apps", names, i -> {
                JSONObject a = apps.get(i);
                try {
                    JSONObject payload = new JSONObject().put("id", a.optString("id"));
                    if (a.optJSONObject("params") != null) payload.put("params", a.optJSONObject("params"));
                    tv.request("ssap://system.launcher/launch", payload, this::reportError);
                } catch (JSONException ignored) {
                }
            });
        });
    }

    private void keyboard() {
        if (!tv.isReady()) return;
        final EditText field = new EditText(this);
        field.setSingleLine();
        field.setHint("Type into the focused field on the TV");
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(field);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Keyboard")
                .setView(box)
                .setPositiveButton("Send + Enter", (d, w) -> sendText(field.getText().toString(), true))
                .setNeutralButton("Send", (d, w) -> sendText(field.getText().toString(), false))
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> field.requestFocus());
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }

    private void sendText(String s, boolean enter) {
        try {
            if (!s.isEmpty()) {
                tv.request("ssap://com.webos.service.ime/insertText", new JSONObject().put("text", s).put("replace", 0), this::reportError);
            }
            if (enter) tv.request("ssap://com.webos.service.ime/sendEnterKey");
        } catch (JSONException ignored) {
        }
    }

    private void showMenu() {
        new AlertDialog.Builder(this)
                .setTitle(prefs.name() != null ? prefs.name() : "TV")
                .setItems(new String[] {"Scroll direction", "Reconnect", "Forget this TV"}, (d, which) -> {
                    if (which == 0) {
                        showScrollDirection();
                    } else if (which == 1) {
                        tv.connect();
                    } else {
                        tv.disconnect();
                        prefs.forget();
                        showSetup();
                    }
                })
                .show();
    }

    private void setControlMode(boolean touchpad) {
        prefs.setTouchpad(touchpad);
        refreshControls();
    }

    private void showScrollDirection() {
        new AlertDialog.Builder(this)
                .setTitle("Scroll direction")
                .setSingleChoiceItems(new String[] {
                        "Natural (default)\nContent follows your fingers",
                        "Traditional\nContent moves opposite to your fingers"
                }, prefs.naturalScrolling() ? 0 : 1, (dialog, which) -> {
                    prefs.setNaturalScrolling(which == 0);
                    refreshControls();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface Picked { void at(int index); }

    private void pick(String heading, List<String> names, final Picked picked) {
        new AlertDialog.Builder(this)
                .setTitle(heading)
                .setItems(names.toArray(new String[0]), (d, which) -> picked.at(which))
                .show();
    }

    private void reportError(JSONObject payload, String error) {
        if (error != null) toast(error);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---- view helpers -------------------------------------------------------------------------

    private void setContent(View v) {
        stopHeldKey();
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(l, t, r, b);
            return insets;
        });
        setContentView(v);
        v.requestApplyInsets();
    }

    private void tap(View v, Runnable action) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        action.run();
    }

    /** Fires on press, then repeats while held. */
    private void repeating(final View v, final Runnable action) {
        repeating(v, action, false);
    }

    private void stopHeldKey() {
        if (heldKey != null) heldKey.stop();
        if (heldView != null) heldView.setPressed(false);
        heldKey = null;
        heldView = null;
    }

    private void repeating(final View v, final Runnable action, boolean accelerate) {
        final HeldKey key = new HeldKey(keyScheduler, action, accelerate);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopHeldKey();
                    heldKey = key;
                    heldView = view;
                    view.getBackground().setHotspot(e.getX(), e.getY());
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    key.start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getX() < 0 || e.getX() >= view.getWidth()
                            || e.getY() < 0 || e.getY() >= view.getHeight()) {
                        key.stop();
                        if (heldKey == key) stopHeldKey();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    key.stop();
                    if (heldKey == key) stopHeldKey();
                    return true;
                default:
                    return true;
            }
        });
    }

    private TextView button(String label, int sp, int color, final Runnable action) {
        TextView b = text(label, sp, TEXT, false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(color, 18));
        b.setClickable(true);
        if (action != null) b.setOnClickListener(v -> tap(v, action));
        return b;
    }

    private ImageView icon(int drawable, int tint, String description, final Runnable action) {
        ImageView v = new ImageView(this);
        v.setImageResource(drawable);
        v.setImageTintList(ColorStateList.valueOf(tint));
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setContentDescription(description);
        v.setBackground(ripple(SURFACE, 18));
        v.setOnClickListener(x -> tap(x, action));
        return v;
    }

    private TextView pill(String label, Runnable action) {
        TextView b = button(label, 16, SURFACE_HI, action);
        b.setBackground(ripple(SURFACE_HI, 26));
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private RippleDrawable ripple(int color, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(color, radiusDp), rounded(Color.WHITE, radiusDp));
    }

    private LinearLayout.LayoutParams weighted(int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        p.setMargins(dp(5), 0, dp(5), 0);
        return p;
    }

    /** Sizes in dp; -1 / -2 mean MATCH_PARENT / WRAP_CONTENT. */
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
