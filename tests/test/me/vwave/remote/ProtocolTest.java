package me.vwave.remote;

import android.content.Context;
import org.json.JSONObject;

import java.util.concurrent.*;

/** Drives the app's real Tv/WebSocket/Prefs classes against scripts/mock_tv.py. */
public final class ProtocolTest {
    static final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    static int failures;

    public static void main(String[] a) throws Exception {
        String host = a.length > 0 ? a[0] : "127.0.0.1";
        Context ctx = new Context();
        Prefs prefs = new Prefs(ctx);
        Tv tv = new Tv(prefs, new Tv.Listener() {
            public void onState(Tv.State s, String d) { events.add("state:" + s + (d != null ? ":" + d : "")); }
            public void onVolume(int v, boolean m) { events.add("volume:" + v + ":" + m); }
            public void onName(String n) { events.add("name:" + n); }
        });
        java.nio.file.Path log = java.nio.file.Paths.get(a.length > 1 ? a[1] : "../mock_tv.log");
        long logStart = java.nio.file.Files.size(log);
        prefs.setTv(host, "Mock");

        // 1. First pairing: prompt, then registered with a client-key; cert pinned.
        main(tv::connect);
        expect("state:CONNECTING");
        expect("state:PAIRING:PROMPT");
        expect("state:READY");
        expect("volume:12:false");
        Thread.sleep(300);
        check("client-key stored", "mock-client-key-0001".equals(prefs.clientKey()));
        check("cert pinned", prefs.certPin() != null && prefs.certPin().length() == 64);
        check("MACs stored", prefs.macs().size() == 2);
        expect("name:Mock LG TV");
        check("TV name stored without [LG] prefix", "Mock LG TV".equals(prefs.name()));
        check("BLE wake registered", prefs.wakeRegistered());
        check("wake address is a locally administered unicast MAC",
                prefs.wakeAddress().matches("[0-9a-f][26ae](:[0-9a-f]{2}){5}") && prefs.wakeAddress().equals(prefs.wakeAddress()));

        // 2. Requests with callbacks + subscription updates.
        main(() -> tv.request("ssap://audio/volumeUp"));
        expect("volume:13:false");
        CompletableFuture<JSONObject> inputs = new CompletableFuture<>();
        main(() -> tv.request("ssap://tv/getExternalInputList", null, (p, e) -> inputs.complete(p)));
        check("input list", inputs.get(3, TimeUnit.SECONDS).getJSONArray("devices").length() == 3);

        // 3. Pointer socket: first button queues until the socket opens, later ones go direct.
        main(() -> { tv.button("UP"); tv.button("ENTER"); });
        Thread.sleep(1500);
        main(() -> tv.button("BACK"));
        main(() -> { tv.move(40, -25); tv.click(); tv.scroll(0, 12); });
        Thread.sleep(800);
        String mock = new String(java.nio.file.Files.readAllBytes(log)).substring((int) logStart);
        for (String f : new String[] {"type:button\\nname:UP", "type:button\\nname:ENTER", "type:button\\nname:BACK",
                "type:move\\ndx:40\\ndy:-25\\ndown:0", "type:click", "type:scroll\\ndx:0\\ndy:12"}) {
            check("pointer frame " + f.replace("\\n", " "), mock.contains(f));
        }
        check("registerDevice sent {type:ThinQ, address}", mock.contains("asc/registerDevice")
                && mock.contains("\"address\": \"" + prefs.wakeAddress() + "\"") && mock.contains("\"type\": \"ThinQ\""));

        // Power-state messages recorded from a webOS 24 TV during turnOff.
        check("power off detected early", Tv.isPoweringOff("Active", "Request Power Off")
                && Tv.isPoweringOff("Active", "Request Power Off Logo") && Tv.isPoweringOff("Suspend", "")
                && !Tv.isPoweringOff("Active", "") && !Tv.isPoweringOff("Screen Off", ""));

        // 4. Power off closes the socket.
        main(tv::powerOff);
        expect("state:DISCONNECTED:TV is off");

        // 5. Reconnect with stored key: no prompt this time.
        main(tv::connect);
        expect("state:CONNECTING");
        expect("state:READY");
        main(tv::disconnect);
        expect("state:DISCONNECTED");

        // 6. Wrong pinned certificate must be refused.
        prefs.setCertPin("00".repeat(32));
        main(tv::connect);
        expect("state:CONNECTING");
        String ev = events.poll(5, TimeUnit.SECONDS);
        check("pin mismatch refused (" + ev + ")", ev != null && ev.startsWith("state:DISCONNECTED") && ev.contains("certificate"));

        // 7. The reconnect probe sees the mock's TLS port and a closed one.
        check("probe finds a listening TV", Net.reachable(host, Tv.SECURE_PORT, 1500));
        check("probe reports a closed port", !Net.reachable("127.0.0.2", Tv.SECURE_PORT, 1500));

        // 8. A refused 3001 falls back to plain 3000 only for a TV that never pinned a certificate.
        String legacyHost = "127.0.0.2";
        try (java.net.ServerSocket legacy = new java.net.ServerSocket(Tv.LEGACY_PORT, 1, java.net.InetAddress.getByName(legacyHost))) {
            legacy.setSoTimeout(3000);
            prefs.setTv(legacyHost, "Legacy");
            prefs.setClientKey("legacy-key");
            prefs.setCertPin("11".repeat(32));
            main(tv::connect);
            expect("state:CONNECTING");
            ev = events.poll(5, TimeUnit.SECONDS);
            boolean refused = ev != null && ev.startsWith("state:DISCONNECTED");
            try (java.net.Socket s = legacy.accept()) {
                refused = false;
            } catch (java.net.SocketTimeoutException e) {
                // Nothing reached port 3000.
            }
            check("pinned TV never downgrades to plain ws (" + ev + ")", refused);

            prefs.setCertPin(null);
            main(tv::connect);
            expect("state:CONNECTING");
            boolean fellBack;
            try (java.net.Socket s = legacy.accept()) {
                fellBack = true;
            } catch (java.net.SocketTimeoutException e) {
                fellBack = false;
            }
            check("unpinned TV still falls back to plain ws://:3000", fellBack);
            main(tv::disconnect);
        }

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void main(Runnable r) { new android.os.Handler(null).post(r); }

    static void expect(String want) throws InterruptedException {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            String ev = events.poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (ev == null) break;
            if (ev.equals(want)) { System.out.println("ok   " + want); return; }
            System.out.println("     (saw " + ev + ")");
        }
        check("event " + want, false);
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + what);
        if (!ok) failures++;
    }
}
