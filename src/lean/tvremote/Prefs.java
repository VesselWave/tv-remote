package lean.tvremote;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The paired TV (address, name, client-key, pinned certificate, wake addresses) and UI settings. */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE);
    }

    String host() { return sp.getString("host", null); }
    String name() { return sp.getString("name", null); }
    String clientKey() { return sp.getString("key", null); }
    String certPin() { return sp.getString("pin", null); }
    /** The TV's Bluetooth MAC, from its SSDP device description; the BLE wake-up target. */
    String btMac() { return sp.getString("btMac", null); }
    boolean wakeRegistered() { return sp.getBoolean("wakeRegistered", false); }
    boolean touchpad() { return sp.getBoolean("touchpad", false); }
    boolean naturalScrolling() { return sp.getBoolean("naturalScrolling", true); }

    /**
     * The address this phone presents for BLE wake-up. Like ThinQ (NetworkUtil.getOrCreateRandomizedMacAddress)
     * it is random and persistent, not the real Bluetooth MAC, which Android hides from apps.
     */
    String wakeAddress() {
        String a = sp.getString("wakeAddress", null);
        if (a != null) return a;
        byte[] b = new byte[6];
        new java.security.SecureRandom().nextBytes(b);
        b[0] = (byte) ((b[0] & 0xFC) | 0x02); // locally administered, unicast
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(sb.length() > 0 ? ":" : "").append(String.format("%02x", x));
        a = sb.toString();
        sp.edit().putString("wakeAddress", a).apply();
        return a;
    }

    List<String> macs() {
        String m = sp.getString("macs", "");
        return m.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(m.split(","));
    }

    /** Selecting a different TV drops the credentials of the previous one. */
    void setTv(String host, String name) {
        SharedPreferences.Editor e = sp.edit();
        if (!host.equals(host())) e.remove("key").remove("pin").remove("macs").remove("btMac").remove("wakeRegistered");
        e.putString("host", host).putString("name", name).apply();
    }

    void setClientKey(String key) { sp.edit().putString("key", key).apply(); }
    void setCertPin(String pin) { sp.edit().putString("pin", pin).apply(); }
    void setMacs(List<String> macs) { sp.edit().putString("macs", android.text.TextUtils.join(",", macs)).apply(); }
    void setName(String name) { sp.edit().putString("name", name).apply(); }
    void setBtMac(String mac) { sp.edit().putString("btMac", mac).apply(); }
    void setWakeRegistered(boolean on) { sp.edit().putBoolean("wakeRegistered", on).apply(); }
    void setTouchpad(boolean on) { sp.edit().putBoolean("touchpad", on).apply(); }
    void setNaturalScrolling(boolean on) { sp.edit().putBoolean("naturalScrolling", on).apply(); }

    /** Forgets the TV but keeps this phone's wake address and UI settings. */
    void forget() {
        sp.edit().remove("host").remove("name").remove("key").remove("pin").remove("macs")
                .remove("btMac").remove("wakeRegistered").apply();
    }
}
