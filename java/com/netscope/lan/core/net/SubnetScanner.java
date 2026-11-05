package com.netscope.lan.core.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SubnetScanner {
    public interface Listener {
        void onHost(Probe probe);
        void onProgress(int done, int total);
    }

    public static final class Probe {
        public final String ip;
        public final boolean alive;
        public final long latencyMs;
        public final List<Integer> openPorts;

        Probe(String ip, boolean alive, long latencyMs, List<Integer> openPorts) {
            this.ip = ip;
            this.alive = alive;
            this.latencyMs = latencyMs;
            this.openPorts = openPorts;
        }
    }

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public void scan(final String[] hosts, final int[] ports, final int timeoutMs,
                     int concurrency, final Listener listener) {
        final ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency), r -> {
            Thread t = new Thread(r, "netscope-probe");
            t.setDaemon(true);
            return t;
        });
        final AtomicInteger done = new AtomicInteger();
        final int total = hosts.length;
        try {
            for (final String host : hosts) {
                if (cancelled) break;
                pool.execute(() -> {
                    Probe probe = probe(host, ports, timeoutMs);
                    if (listener != null) {
                        listener.onHost(probe);
                        listener.onProgress(done.incrementAndGet(), total);
                    }
                });
            }
        } finally {
            pool.shutdown();
            try {
                if (cancelled) pool.shutdownNow();
                pool.awaitTermination(180, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Probe probe(String ip, int[] ports, int timeoutMs) {
        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (IOException e) {
            return new Probe(ip, false, -1, new ArrayList<>());
        }
        List<Integer> open = new ArrayList<>();
        long best = -1;
        for (int port : ports) {
            if (cancelled) break;
            Socket socket = new Socket();
            try {
                long start = System.nanoTime();
                socket.connect(new InetSocketAddress(address, port), timeoutMs);
                long rtt = (System.nanoTime() - start) / 1_000_000L;
                open.add(port);
                if (best < 0 || rtt < best) best = rtt;
            } catch (SocketTimeoutException e) {
                // filtrado: sin respuesta dentro del timeout
            } catch (IOException e) {
                // cerrado o inalcanzable
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
        return new Probe(ip, !open.isEmpty(), best, open);
    }
}
