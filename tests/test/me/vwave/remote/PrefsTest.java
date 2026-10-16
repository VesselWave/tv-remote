package me.vwave.remote;

import android.content.Context;

import java.util.Arrays;

/** Saved TVs: migration from the single-TV layout, switching, and forgetting. */
public final class PrefsTest {
    static int failures;

    public static void main(String[] a) {
        // A 0.3 install: one TV in flat keys.
        Context ctx = new Context();
        ctx.store.put("host", "10.0.0.5");
        ctx.store.put("name", "Living room");
        ctx.store.put("key", "key-a");
        ctx.store.put("pin", "pin-a");
        ctx.store.put("macs", "aa:bb:cc:dd:ee:ff");
        ctx.store.put("btMac", "11:22:33:44:55:66");
        ctx.store.put("wakeRegistered", "true");
        ctx.store.put("wakeAddress", "02:00:00:00:00:01");
        ctx.store.put("touchpad", "true");
        Prefs prefs = new Prefs(ctx);
        check("migrated TV selected", "10.0.0.5".equals(prefs.host()) && prefs.hosts().equals(Arrays.asList("10.0.0.5")));
        check("migrated pairing", "key-a".equals(prefs.clientKey()) && "pin-a".equals(prefs.certPin()) && "Living room".equals(prefs.name()));
        check("migrated wake data", prefs.macs().size() == 1 && "11:22:33:44:55:66".equals(prefs.btMac()) && prefs.wakeRegistered());
        check("flat keys removed", !ctx.store.containsKey("key") && !ctx.store.containsKey("pin") && !ctx.store.containsKey("wakeRegistered"));
        check("phone settings kept", prefs.touchpad() && "02:00:00:00:00:01".equals(prefs.wakeAddress()));
        check("migration runs once", "key-a".equals(new Prefs(ctx).clientKey()));

        // Adding a second TV keeps the first one's pairing.
        prefs.setTv("10.0.0.6", "Bedroom");
        check("new TV selected and unpaired", "10.0.0.6".equals(prefs.host()) && prefs.clientKey() == null && prefs.macs().isEmpty()
                && !prefs.wakeRegistered() && prefs.btMac() == null);
        prefs.setClientKey("key-b");
        prefs.setCertPin("pin-b");
        prefs.setWakeRegistered(true);
        check("both saved", prefs.hosts().equals(Arrays.asList("10.0.0.5", "10.0.0.6")) && prefs.paired("10.0.0.5") && prefs.paired("10.0.0.6"));

        // Switching back restores the first TV; a discovery name doesn't replace a learned one.
        prefs.setTv("10.0.0.5", "LG TV");
        check("switched back", "key-a".equals(prefs.clientKey()) && "pin-a".equals(prefs.certPin()) && "Living room".equals(prefs.name()));
        check("other TV's name readable", "Bedroom".equals(prefs.name("10.0.0.6")));

        // A TV that never paired is dropped when another is selected.
        prefs.setTv("10.0.0.7", "Kitchen");
        prefs.setTv("10.0.0.6", null);
        check("unpaired TV dropped", prefs.hosts().equals(Arrays.asList("10.0.0.5", "10.0.0.6")) && prefs.name("10.0.0.7") == null);
        check("second TV intact", "key-b".equals(prefs.clientKey()) && prefs.wakeRegistered());

        // Forgetting removes only the selected TV.
        prefs.forget();
        check("forgotten TV gone, none selected", prefs.host() == null && prefs.hosts().equals(Arrays.asList("10.0.0.5"))
                && !ctx.store.containsKey("tv.10.0.0.6.key") && prefs.clientKey() == null);
        check("other TV still paired", prefs.paired("10.0.0.5"));

        // Fresh install.
        Prefs empty = new Prefs(new Context());
        check("fresh install has no TVs", empty.host() == null && empty.hosts().isEmpty() && empty.clientKey() == null);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }
}
