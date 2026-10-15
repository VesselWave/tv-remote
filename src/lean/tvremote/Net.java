package lean.tvremote;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Blocking network helpers; call from a background thread. */
final class Net {
    private Net() {}

    /** A TV found by SSDP. */
    static final class Found {
        final String name;
        /** From {@code <bluetoothMac>} in the device description; the BLE wake-up target. May be null. */
        final String btMac;

        Found(String name, String btMac) {
            this.name = name;
            this.btMac = btMac;
        }
    }

    /**
     * Wake-on-LAN as ThinQ sends it: 6x 0xFF + 16x MAC, three times on UDP 9, to the TV's last
     * address (ConnectSdkHandler.powerOnWol) and to every broadcast address.
     */
    static void wake(List<String> macs, String lastHost) throws Exception {
        Set<InetAddress> targets = new LinkedHashSet<>();
        if (lastHost != null) targets.add(InetAddress.getByName(lastHost));
        for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nif.isUp() || nif.isLoopback()) continue;
            for (InterfaceAddress a : nif.getInterfaceAddresses()) {
                if (a.getBroadcast() != null) targets.add(a.getBroadcast());
            }
        }
        targets.add(InetAddress.getByName("255.255.255.255"));
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (String mac : macs) {
                byte[] packet = magicPacket(mac);
                for (int i = 0; i < 3; i++) {
                    for (InetAddress t : targets) socket.send(new DatagramPacket(packet, packet.length, t, 9));
                }
            }
        }
    }

    private static byte[] magicPacket(String mac) {
        String[] parts = mac.split("[:-]");
        byte[] m = new byte[6];
        for (int i = 0; i < 6; i++) m[i] = (byte) Integer.parseInt(parts[i], 16);
        byte[] p = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) p[i] = (byte) 0xFF;
        for (int i = 6; i < p.length; i += 6) System.arraycopy(m, 0, p, i, 6);
        return p;
    }

    /** SSDP search for webOS TVs, by IP address. */
    static Map<String, Found> discover(Context ctx, int timeoutMs) {
        return discover(ctx, timeoutMs, null);
    }

    /** Like {@link #discover(Context, int)}, also asking {@code host} directly in case multicast is filtered. */
    static Map<String, Found> discover(Context ctx, int timeoutMs, String host) {
        Map<String, Found> found = new LinkedHashMap<>();
        Map<String, String> locations = new LinkedHashMap<>();
        WifiManager wifi = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi.createMulticastLock("tvremote-ssdp");
        lock.acquire();
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] search = ("M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: urn:lge-com:service:webos-second-screen:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            for (int i = 0; i < 2; i++) {
                socket.send(new DatagramPacket(search, search.length, group, 1900));
                if (host != null) socket.send(new DatagramPacket(search, search.length, InetAddress.getByName(host), 1900));
            }
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[2048];
            while (true) {
                int left = (int) (end - System.currentTimeMillis());
                if (left <= 0) break;
                socket.setSoTimeout(left);
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String ip = p.getAddress().getHostAddress();
                String text = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII);
                Matcher loc = Pattern.compile("(?im)^location:\\s*(\\S+)").matcher(text);
                if (!locations.containsKey(ip)) locations.put(ip, loc.find() ? loc.group(1) : null);
            }
        } catch (Exception ignored) {
        } finally {
            lock.release();
        }
        for (Map.Entry<String, String> e : locations.entrySet()) {
            String xml = e.getValue() != null ? fetch(e.getValue()) : "";
            String name = tag(xml, "friendlyName");
            if (name != null && name.startsWith("[LG] ")) name = name.substring(5);
            String bt = tag(xml, "bluetoothMac");
            if (bt != null && !bt.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) bt = null;
            found.put(e.getKey(), new Found(name != null && !name.isEmpty() ? name : "LG TV", bt));
        }
        return found;
    }

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String fetch(String location) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(location).openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0 && out.size() < 65536) out.write(b, 0, n);
                return out.toString("UTF-8");
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
