package com.mackillergr.bf2.lan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GameSpy query broadcast discovery, not a BF2 game/handshake implementation. */
public final class LanDiscovery {
    private LanDiscovery() {}

    /** Enumerate local interface-directed broadcasts; avoid cellular / remote network scans. */
    public static List<InetAddress> localBroadcasts() throws IOException {
        Set<InetAddress> targets = new LinkedHashSet<>();
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        if (nics == null) return Collections.emptyList();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (!nic.isUp() || nic.isLoopback() || nic.isPointToPoint()) continue;
            for (InterfaceAddress iface : nic.getInterfaceAddresses()) {
                if (iface.getAddress() instanceof Inet4Address &&
                        iface.getBroadcast() instanceof Inet4Address) {
                    targets.add(iface.getBroadcast());
                }
            }
        }
        return new ArrayList<>(targets);
    }

    public static List<LanQuery.Result> scan(int queryPort, int timeoutMillis) throws IOException {
        List<InetAddress> targets = localBroadcasts();
        if (targets.isEmpty()) return Collections.emptyList();
        return scanTargets(targets, queryPort, timeoutMillis);
    }

    /** Public for deterministic loopback unit tests and directed-broadcast fallbacks. */
    public static List<LanQuery.Result> scanTargets(List<InetAddress> targets,
                                                      int queryPort, int timeoutMillis) throws IOException {
        if (queryPort < 1 || queryPort > 65535 || timeoutMillis < 100 || timeoutMillis > 15000) {
            throw new IllegalArgumentException("Invalid query port or timeout");
        }
        if (targets == null || targets.isEmpty()) return Collections.emptyList();
        byte[] token = new byte[4];
        new SecureRandom().nextBytes(token);
        byte[] request = LanQuery.request(token);
        byte[] buffer = new byte[4096];
        Map<String, LanQuery.Result> found = new LinkedHashMap<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            boolean didSend = false;
            for (InetAddress target : targets) {
                if (!(target instanceof Inet4Address)) continue;
                try {
                    socket.send(new DatagramPacket(request, request.length, target, queryPort));
                    didSend = true;
                } catch (IOException ignored) {
                    // Some interfaces block broadcast. Continue to reachable targets.
                }
            }
            if (!didSend) throw new IOException("No reachable IPv4 query targets");
            long deadline = System.nanoTime() + 1_000_000L * timeoutMillis;
            while (System.nanoTime() < deadline && found.size() < 50) {
                int remainingMs = (int) Math.max(1, (deadline - System.nanoTime()) / 1_000_000L);
                socket.setSoTimeout(remainingMs);
                DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                try { socket.receive(reply); }
                catch (SocketTimeoutException timeout) { break; }
                if (!(reply.getAddress() instanceof Inet4Address) || reply.getPort() != queryPort) continue;
                try {
                    Map<String, String> headers = LanQuery.parseResponse(
                            reply.getData(), reply.getLength(), token);
                    LanQuery.Result info = new LanQuery.Result(
                            reply.getAddress().getHostAddress(), queryPort, headers);
                    found.put(info.ip + ":" + info.queryPort, info);
                } catch (IllegalArgumentException ignored) {
                    // Not our request, truncated or unsolicited packet.
                }
            }
        }
        List<LanQuery.Result> result = new ArrayList<>(found.values());
        result.sort(Comparator.comparing(a -> a.ip));
        return result;
    }
}
