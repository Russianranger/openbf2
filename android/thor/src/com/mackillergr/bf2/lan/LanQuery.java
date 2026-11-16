package com.mackillergr.bf2.lan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GameSpy Protocol 00, subprotocol A: header-only BF2 server metadata query.
 * NO BF2 gameplay transport or authentication is implemented here.
 * Can run on Android or a standard Java runtime.
 */
public final class LanQuery {
    public static final int DEFAULT_GAME_PORT = 16567;
    public static final int DEFAULT_QUERY_PORT = 29900;
    private LanQuery() { }

    public static final class Result {
        public final String ip;
        public final int queryPort;
        public final int gamePort;
        public final String hostname;
        public final String version;
        public final String map;
        public final String players;
        public final String maxPlayers;
        public final Map<String, String> headers;

        Result(String ip, int queryPort, Map<String, String> h) {
            this.ip = ip;
            this.queryPort = queryPort;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(h));
            this.gamePort = safePort(h.get("hostport"), DEFAULT_GAME_PORT);
            this.hostname = field(h, "hostname", "(χωρίς όνομα)");
            this.version = field(h, "gamever", "");
            this.map = field(h, "mapname", "");
            this.players = field(h, "numplayers", "?");
            this.maxPlayers = field(h, "maxplayers", "?");
        }
        private static String field(Map<String, String> h, String key, String otherwise) {
            String s = h.get(key);
            return s == null || s.isEmpty() ? otherwise : s;
        }
    }

    public static int safePort(String text, int fallback) {
        if (text != null) {
            try {
                int p = Integer.parseInt(text);
                if (p >= 1 && p <= 65535) return p;
            } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    public static byte[] request(byte[] token) {
        if (token == null || token.length != 4) {
            throw new IllegalArgumentException("Token requires four bytes");
        }
        return new byte[] {
                (byte) 0xfe, (byte) 0xfd, 0,
                token[0], token[1], token[2], token[3],
                (byte) 0xff, 0, 0, 0
        };
    }

    public static Map<String, String> parseResponse(byte[] data, int length, byte[] token) {
        if (data == null || token == null || token.length != 4 || length > data.length || length < 6 ||
                data[0] != 0 || data[1] != token[0] || data[2] != token[1] ||
                data[3] != token[2] || data[4] != token[3]) {
            throw new IllegalArgumentException("Unrelated or invalid GameSpy response");
        }
        int pos = 5;
        byte[] split = "splitnum\0".getBytes(StandardCharsets.US_ASCII);
        if (length >= pos + split.length + 1) {
            boolean hasSplit = true;
            for (int i = 0; i < split.length; i++) {
                if (data[pos + i] != split[i]) { hasSplit = false; break; }
            }
            if (hasSplit) pos += split.length + 1; // one byte split index
        }
        while (pos < length && data[pos] == 0) pos++;
        Map<String, String> headers = new LinkedHashMap<>();
        while (pos < length) {
            int keyEnd = findZero(data, pos, length);
            if (keyEnd < 0 || keyEnd == pos) break;
            String key = new String(data, pos, keyEnd - pos, StandardCharsets.ISO_8859_1)
                    .trim().toLowerCase(java.util.Locale.ROOT);
            pos = keyEnd + 1;
            if (pos >= length) break;
            int valueEnd = findZero(data, pos, length);
            if (valueEnd < 0) break;
            String value = new String(data, pos, valueEnd - pos, StandardCharsets.ISO_8859_1).trim();
            pos = valueEnd + 1;
            if (!key.isEmpty() && key.length() <= 90 && printableAscii(key)) headers.put(key, value);
        }
        if (headers.isEmpty()) throw new IllegalArgumentException("No recognizable headers");
        return headers;
    }

    private static boolean printableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    private static int findZero(byte[] data, int start, int end) {
        for (int i = start; i < end; i++) if (data[i] == 0) return i;
        return -1;
    }

    /** Send a single metadata request to a server at an IPv4 address. */
    public static Result query(String ip, int queryPort, int timeoutMillis) throws IOException {
        if (queryPort < 1 || queryPort > 65535 || timeoutMillis < 50 || timeoutMillis > 30000) {
            throw new IllegalArgumentException("Invalid query port or timeout");
        }
        InetAddress target = InetAddress.getByName(ip);
        if (!(target instanceof Inet4Address)) throw new IllegalArgumentException("IPv4 required");
        byte[] token = new byte[4];
        new SecureRandom().nextBytes(token);
        byte[] tx = request(token);
        byte[] receiveBuffer = new byte[4096];
        long deadline = System.nanoTime() + (long) timeoutMillis * 1000000L;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(tx, tx.length, target, queryPort));
            while (true) {
                long remaining = (deadline - System.nanoTime()) / 1000000L;
                if (remaining <= 0) throw new SocketTimeoutException("No BF2 query reply");
                socket.setSoTimeout((int) Math.max(1, remaining));
                DatagramPacket rx = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(rx);
                if (!rx.getAddress().equals(target) || rx.getPort() != queryPort) continue;
                try {
                    Map<String, String> headers = parseResponse(rx.getData(), rx.getLength(), token);
                    return new Result(rx.getAddress().getHostAddress(), queryPort, headers);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unrelated or invalid datagrams. No handshake performed.
                }
            }
        }
    }
}
