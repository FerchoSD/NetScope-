package com.netscope.lan.core.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.netscope.lan.core.db.ScanRepository;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanConfig;
import com.netscope.lan.core.model.ScanReport;
import com.netscope.lan.service.ScanService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScanController {
    public interface PortScanListener {
        void onOpen(PortResult result);
        void onProgress(int done, int total);
        void onFinished(List<PortResult> openPorts, long durationMs);
        void onError(String message);
    }

    private static volatile ScanController instance;

    private final Context app;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "netscope-scan");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private volatile boolean busy;
    private volatile boolean cancelRequested;
    private volatile ScanEngine engine;
    private volatile PortScanner portScanner;
    private volatile ScanReport lastReport;

    private ScanController(Context context) {
        this.app = context.getApplicationContext();
    }

    public static ScanController get(Context context) {
        if (instance == null) {
            synchronized (ScanController.class) {
                if (instance == null) instance = new ScanController(context);
            }
        }
        return instance;
    }

    public boolean isBusy() {
        return busy;
    }

    public ScanReport getLastReport() {
        return lastReport;
    }

    public void setLastReport(ScanReport report) {
        this.lastReport = report;
    }

    public void startNetworkScan(ScanConfig config, final ScanEngine.Listener listener) {
        synchronized (lock) {
            if (busy) {
                fail(listener, "Ya hay un escaneo en curso");
                return;
            }
            busy = true;
            cancelRequested = false;
        }
        ScanService.start(app, "Escaneando la red local…");
        executor.execute(() -> {
            ScanEngine scan = new ScanEngine(app, config, new ScanEngine.Listener() {
                @Override public void onStage(final String stage) {
                    main.post(() -> listener.onStage(stage));
                }
                @Override public void onProgress(final int done, final int total) {
                    main.post(() -> listener.onProgress(done, total));
                }
                @Override public void onDevice(final Device device) {
                    main.post(() -> listener.onDevice(device));
                }
                @Override public void onFinished(final ScanReport report) {
                    lastReport = report;
                    busy = false;
                    main.post(() -> listener.onFinished(report));
                    ScanService.stop(app);
                }
                @Override public void onError(final String message) {
                    busy = false;
                    main.post(() -> listener.onError(message));
                    ScanService.stop(app);
                }
            });
            engine = scan;
            if (cancelRequested) scan.cancel();
            try {
                scan.run();
            } catch (Throwable t) {
                busy = false;
                final String message = "Error inesperado: " + t.getClass().getSimpleName();
                main.post(() -> listener.onError(message));
                ScanService.stop(app);
            } finally {
                engine = null;
            }
        });
    }

    public void startPortScan(final Device device, final int[] ports, final int timeoutMs,
                              final int concurrency, final boolean banners,
                              final PortScanListener listener) {
        synchronized (lock) {
            if (busy) {
                failPort(listener, "Ya hay un escaneo en curso");
                return;
            }
            busy = true;
            cancelRequested = false;
        }
        ScanService.start(app, "Escaneando puertos de " + device.ip + "…");
        device.resetPortScan();
        final long start = System.currentTimeMillis();
        executor.execute(() -> {
            final PortScanner scanner = new PortScanner();
            portScanner = scanner;
            if (cancelRequested) scanner.cancel();
            final AtomicInteger lastPercent = new AtomicInteger(-1);
            try {
                List<PortResult> open = scanner.scan(device.ip, ports, timeoutMs, concurrency, banners,
                        new PortScanner.Listener() {
                            @Override public void onOpen(final PortResult result) {
                                device.addPortResult(result);
                                main.post(() -> listener.onOpen(result));
                            }
                            @Override public void onProgress(final int done, final int total) {
                                int percent = total == 0 ? 0 : (int) (done * 100L / total);
                                if (percent != lastPercent.get() && (percent % 2 == 0 || percent == 100)) {
                                    lastPercent.set(percent);
                                    main.post(() -> listener.onProgress(done, total));
                                }
                            }
                        });
                final long duration = System.currentTimeMillis() - start;
                if (cancelRequested) {
                    main.post(() -> listener.onError("Escaneo cancelado"));
                } else {
                    persistSnapshot();
                    main.post(() -> listener.onFinished(open, duration));
                }
            } catch (Throwable t) {
                final String message = "Error al escanear puertos: " + t.getClass().getSimpleName();
                main.post(() -> listener.onError(message));
            } finally {
                busy = false;
                portScanner = null;
                main.post(() -> ScanService.stop(app));
            }
        });
    }

    public void cancel() {
        cancelRequested = true;
        ScanEngine current = engine;
        if (current != null) current.cancel();
        PortScanner scanner = portScanner;
        if (scanner != null) scanner.cancel();
    }

    private void persistSnapshot() {
        try {
            ScanReport report = lastReport;
            if (report == null) return;
            report.timestamp = System.currentTimeMillis();
            new ScanRepository(app).saveReport(report);
        } catch (Exception ignored) {
        }
    }

    private void fail(final ScanEngine.Listener listener, final String message) {
        main.post(() -> listener.onError(message));
    }

    private void failPort(final PortScanListener listener, final String message) {
        main.post(() -> listener.onError(message));
    }
}
