package me.vwave.remote;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Saved TVs and UI settings. Each TV (name, client-key, pinned certificate, wake addresses) is stored
 * under "tv.<host>.*"; "host" is the selected one, and the per-TV getters and setters act on it.
 */
final class Prefs {
    private static final String[] TV_FIELDS = {"name", "key", "pin", "macs", "btMac", "wakeRegistered"};
    private final SharedPreferences sp;

    Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE);
        migrate();
    }

    /** Before 0.4 the one TV lived in flat keys ("key", "pin", ...); move it under its host. */
    private void migrate() {
        if (sp.getString("hosts", null) != null) return;
        SharedPreferences.Editor e = sp.edit();
        String host = sp.getString("host", null);
        if (host != null) {
            for (String f : new String[] {"name", "key", "pin", "macs", "btMac"}) {
                String v = sp.getString(f, null);
                if (v != null) e.putString(field(host, f), v);
            }
            if (sp.getBoolean("wakeRegistered", false)) e.putBoolean(field(host, "wakeRegistered"), true);
        }
        for (String f : TV_FIELDS) e.remove(f);
        e.putString("hosts", host != null ? host : "").apply();
    }

    private static String field(String host, String name) { return "tv." + host + "." + name; }
    private String current(String name) { String h = host(); return h != null ? field(h, name) : null; }
    private String get(String name) { String k = current(name); return k != null ? sp.getString(k, null) : null; }
    private void put(String name, String value) {
        String k = current(name);
        if (k != null) sp.edit().putString(k, value).apply();
    }

    String host() { return sp.getString("host", null); }
    String name() { return get("name"); }
    String clientKey() { return get("key"); }
    String certPin() { return get("pin"); }
    /** The TV's Bluetooth MAC, from its SSDP device description; the BLE wake-up target. */
    String btMac() { return get("btMac"); }
    boolean wakeRegistered() { String k = current("wakeRegistered"); return k != null && sp.getBoolean(k, false); }
    boolean touchpad() { return sp.getBoolean("touchpad", false); }
    boolean naturalScrolling() { return sp.getBoolean("naturalScrolling", true); }

    /** Saved TVs in the order they were added. */
    List<String> hosts() {
        String h = sp.getString("hosts", "");
        return h.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(h.split(","));
    }
    String name(String host) { return sp.getString(field(host, "name"), null); }
    boolean paired(String host) { return sp.getString(field(host, "key"), null) != null; }

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
        String m = get("macs");
        return m == null || m.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(m.split(","));
    }

    /**
     * Selects {@code host}, adding it if new; other saved TVs keep their pairing. A TV that never
     * paired is dropped when another one is selected. {@code name} only fills in a missing name.
     */
    void setTv(String host, String name) {
        String previous = host();
        if (previous != null && !previous.equals(host) && !paired(previous)) remove(previous);
        List<String> hosts = new ArrayList<>(hosts());
        SharedPreferences.Editor e = sp.edit();
        if (!hosts.contains(host)) {
            hosts.add(host);
            e.putString("hosts", android.text.TextUtils.join(",", hosts));
        }
        if (name != null && name(host) == null) e.putString(field(host, "name"), name);
        e.putString("host", host).apply();
    }

    void setClientKey(String key) { put("key", key); }
    void setCertPin(String pin) { put("pin", pin); }
    void setMacs(List<String> macs) { put("macs", android.text.TextUtils.join(",", macs)); }
    void setName(String name) { put("name", name); }
    void setBtMac(String mac) { put("btMac", mac); }
    void setWakeRegistered(boolean on) {
        String k = current("wakeRegistered");
        if (k != null) sp.edit().putBoolean(k, on).apply();
    }
    void setTouchpad(boolean on) { sp.edit().putBoolean("touchpad", on).apply(); }
    void setNaturalScrolling(boolean on) { sp.edit().putBoolean("naturalScrolling", on).apply(); }

    /** Forgets the selected TV, leaving none selected; other TVs, the wake address and UI settings stay. */
    void forget() {
        String h = host();
        if (h != null) remove(h);
    }

    private void remove(String host) {
        List<String> hosts = new ArrayList<>(hosts());
        hosts.remove(host);
        SharedPreferences.Editor e = sp.edit();
        for (String f : TV_FIELDS) e.remove(field(host, f));
        e.putString("hosts", android.text.TextUtils.join(",", hosts));
        if (host.equals(host())) e.remove("host");
        e.apply();
    }
}
