package lean.tvremote;

import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

/** Minimal RFC 6455 client: text messages, ping/pong, close. One reader thread, one writer thread. */
final class WebSocket {
    interface Listener {
        void onOpen();
        void onMessage(String text);
        /** Called once, from the reader thread. {@code cause} is null on a clean or requested close. */
        void onClose(Exception cause);
    }

    private static final int MAX_FRAME = 16 * 1024 * 1024;

    private final String host;
    private final int port;
    private final String path;
    private final SocketFactory factory;
    private final Listener listener;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final SecureRandom random = new SecureRandom();

    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean closed;
    private volatile boolean open;
    private volatile long lastHeard;

    WebSocket(String host, int port, String path, SocketFactory factory, Listener listener) {
        this.host = host;
        this.port = port;
        this.path = path.isEmpty() ? "/" : path;
        this.factory = factory;
        this.listener = listener;
    }

    void connect() {
        new Thread(this::run, "ws-" + host + ":" + port).start();
    }

    boolean isOpen() {
        return open && !closed;
    }

    /** System.currentTimeMillis() of the last frame received, of any kind. */
    long lastHeard() {
        return lastHeard;
    }

    void ping() {
        try {
            writer.execute(() -> {
                try {
                    writeFrame(0x9, new byte[0]);
                } catch (IOException e) {
                    shutdownSocket();
                }
            });
        } catch (RuntimeException ignored) {
            // writer already shut down
        }
    }

    void send(String text) {
        final byte[] data = text.getBytes(StandardCharsets.UTF_8);
        try {
            writer.execute(() -> {
                try {
                    writeFrame(0x1, data);
                } catch (IOException e) {
                    shutdownSocket();
                }
            });
        } catch (RuntimeException ignored) {
            // writer already shut down
        }
    }

    void close() {
        if (closed) return;
        closed = true;
        try {
            writer.execute(() -> {
                try {
                    if (open) writeFrame(0x8, new byte[0]);
                } catch (IOException ignored) {
                }
                shutdownSocket();
            });
        } catch (RuntimeException e) {
            shutdownSocket();
        }
        writer.shutdown();
    }

    private void run() {
        Exception cause = null;
        try {
            Socket s = factory.createSocket();
            socket = s;
            if (closed) return;
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setTcpNoDelay(true);
            if (s instanceof SSLSocket) ((SSLSocket) s).startHandshake();
            InputStream in = new BufferedInputStream(s.getInputStream());
            out = s.getOutputStream();

            byte[] key = new byte[16];
            random.nextBytes(key);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + ":" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + Base64.encodeToString(key, Base64.NO_WRAP) + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n\r\n";
            synchronized (this) {
                out.write(request.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            String status = readLine(in);
            if (!status.startsWith("HTTP/1.1 101")) throw new IOException("Handshake refused: " + status);
            while (!readLine(in).isEmpty()) {
                // skip response headers
            }
            open = true;
            lastHeard = System.currentTimeMillis();
            if (closed) return;
            listener.onOpen();
            readLoop(in);
        } catch (Exception e) {
            if (!closed) cause = e;
        } finally {
            open = false;
            shutdownSocket();
            writer.shutdown();
            listener.onClose(cause);
        }
    }

    private void readLoop(InputStream in) throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        int messageOp = 0;
        while (!closed) {
            int b0 = in.read();
            int b1 = in.read();
            if (b1 < 0) throw new EOFException("Connection closed by TV");
            boolean fin = (b0 & 0x80) != 0;
            int op = b0 & 0x0F;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = (readByte(in) << 8) | readByte(in);
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) len = (len << 8) | readByte(in);
            }
            if (len < 0 || len > MAX_FRAME) throw new IOException("Frame too large");
            byte[] mask = null;
            if ((b1 & 0x80) != 0) {
                mask = new byte[4];
                readFully(in, mask);
            }
            byte[] payload = new byte[(int) len];
            readFully(in, payload);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
            }
            lastHeard = System.currentTimeMillis();

            switch (op) {
                case 0x0: // continuation
                case 0x1: // text
                case 0x2: // binary
                    if (op != 0) {
                        messageOp = op;
                        message.reset();
                    }
                    message.write(payload);
                    if (fin) {
                        if (messageOp == 0x1) listener.onMessage(new String(message.toByteArray(), StandardCharsets.UTF_8));
                        message.reset();
                    }
                    break;
                case 0x8:
                    return;
                case 0x9:
                    final byte[] pong = payload;
                    writer.execute(() -> {
                        try {
                            writeFrame(0xA, pong);
                        } catch (IOException ignored) {
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized void writeFrame(int op, byte[] payload) throws IOException {
        OutputStream o = out;
        if (o == null) throw new IOException("Not connected");
        int len = payload.length;
        int headerLen = len < 126 ? 2 : len <= 0xFFFF ? 4 : 10;
        byte[] frame = new byte[headerLen + 4 + len];
        frame[0] = (byte) (0x80 | op);
        if (len < 126) {
            frame[1] = (byte) (0x80 | len);
        } else if (len <= 0xFFFF) {
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) (len >>> 8);
            frame[3] = (byte) len;
        } else {
            frame[1] = (byte) (0x80 | 127);
            for (int i = 0; i < 8; i++) frame[2 + i] = (byte) ((long) len >>> (8 * (7 - i)));
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        System.arraycopy(mask, 0, frame, headerLen, 4);
        for (int i = 0; i < len; i++) frame[headerLen + 4 + i] = (byte) (payload[i] ^ mask[i & 3]);
        o.write(frame);
        o.flush();
    }

    private void shutdownSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) throw new EOFException("Connection closed during handshake");
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("Header line too long");
        }
        return sb.toString();
    }

    private static long readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new EOFException();
            off += n;
        }
    }
}
