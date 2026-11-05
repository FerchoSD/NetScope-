package com.netscope.lan.core.net;

import com.netscope.lan.core.model.PortResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class PortScanner {
    public interface Listener {
        void onOpen(PortResult result);
        void onProgress(int done, int total);
    }

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public List<PortResult> scan(String ip, int[] ports, int timeoutMs, int concurrency,
                                 boolean banners, final Listener listener) {
        final List<PortResult> open = Collections.synchronizedList(new ArrayList<PortResult>());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrency), r -> {
            Thread t = new Thread(r, "netscope-port");
            t.setDaemon(true);
            return t;
        });
        final AtomicInteger done = new AtomicInteger();
        final int total = ports.length;
        try {
            for (final int port : ports) {
                if (cancelled) break;
                pool.execute(() -> {
                    if (cancelled) return;
                    PortResult result = scanPort(ip, port, timeoutMs, banners);
                    if (result.state == PortResult.STATE_OPEN) {
                        open.add(result);
                        if (listener != null) listener.onOpen(result);
                    }
                    if (listener != null) listener.onProgress(done.incrementAndGet(), total);
                });
            }
        } finally {
            pool.shutdown();
            try {
                if (cancelled) pool.shutdownNow();
                pool.awaitTermination(300, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (open) {
            Collections.sort(open, new Comparator<PortResult>() {
                @Override public int compare(PortResult a, PortResult b) {
                    return Integer.compare(a.port, b.port);
                }
            });
        }
        return open;
    }

    private PortResult scanPort(String ip, int port, int timeoutMs, boolean banners) {
        String service = ServiceCatalog.name(port);
        int risk = ServiceCatalog.risk(port);
        String riskNote = ServiceCatalog.riskNote(port);
        Socket socket = new Socket();
        try {
            long start = System.nanoTime();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            long rtt = (System.nanoTime() - start) / 1_000_000L;
            String banner = "";
            if (banners && (risk > 0 || !service.isEmpty() || ServiceCatalog.isHttp(port))) {
                banner = BannerGrabber.grab(ip, port, Math.max(400, timeoutMs));
            }
            return new PortResult(port, PortResult.STATE_OPEN, service, banner, rtt, risk, riskNote);
        } catch (SocketTimeoutException e) {
            return new PortResult(port, PortResult.STATE_FILTERED, service, "", -1, risk, riskNote);
        } catch (IOException e) {
            return new PortResult(port, PortResult.STATE_CLOSED, service, "", -1, risk, riskNote);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
