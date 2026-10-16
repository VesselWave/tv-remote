package me.vwave.remote;
import java.lang.reflect.Method;
public final class WolTest {
    public static void main(String[] a) throws Exception {
        Method m = Net.class.getDeclaredMethod("magicPacket", String.class);
        m.setAccessible(true);
        byte[] p = (byte[]) m.invoke(null, "a8:23:FE:00:00:01");
        boolean ok = p.length == 102;
        for (int i = 0; i < 6; i++) ok &= p[i] == (byte) 0xFF;
        byte[] mac = {(byte) 0xa8, 0x23, (byte) 0xfe, 0, 0, 1};
        for (int r = 0; r < 16; r++) for (int i = 0; i < 6; i++) ok &= p[6 + r * 6 + i] == mac[i];
        System.out.println(ok ? "ok   WOL magic packet (102 bytes, 6xFF + 16xMAC)" : "FAIL WOL packet");
        if (!ok) throw new AssertionError("Invalid wake packet");
        java.util.Map<String, Net.Found> tvs = Net.discover(new android.content.Context(), 2500, a.length > 0 ? a[0] : "127.0.0.1");
        boolean found = false;
        for (Net.Found f : tvs.values()) found |= "Mock LG TV".equals(f.name) && "a8:23:fe:00:00:03".equals(f.btMac);
        System.out.println((found ? "ok   " : "FAIL ") + "SSDP discovery (name + bluetoothMac) " + tvs.keySet());
        if (!found) throw new AssertionError("Mock SSDP discovery failed");

        // A device description is only fetched from the device that answered, without redirects.
        String host = a.length > 0 ? a[0] : "127.0.0.1";
        com.sun.net.httpserver.HttpServer redirect = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(host, 0), 0);
        redirect.createContext("/", x -> {
            x.getResponseHeaders().add("Location", "http://" + host + ":1990/desc.xml");
            x.sendResponseHeaders(302, -1);
            x.close();
        });
        redirect.start();
        try {
            boolean scoped = Net.fetch(host, "http://" + host + ":1990/desc.xml").contains("friendlyName")
                    && Net.fetch("192.0.2.1", "http://" + host + ":1990/desc.xml").isEmpty()
                    && Net.fetch(host, "file:///etc/hosts").isEmpty()
                    && Net.fetch(host, "http://" + host + ":" + redirect.getAddress().getPort() + "/").isEmpty();
            System.out.println((scoped ? "ok   " : "FAIL ") + "SSDP description only from the answering IP, no redirects");
            if (!scoped) throw new AssertionError("SSDP fetch not scoped");
        } finally {
            redirect.stop(0);
        }
    }
}
