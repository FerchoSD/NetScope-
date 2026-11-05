package com.netscope.lan.core.net;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MdnsDiscovery {
    public interface Listener {
        void onService(String ip, String name, String type, int port);
    }

    private static final String[] TYPES = {
            "_http._tcp.", "_https._tcp.", "_ssh._tcp.", "_smb._tcp.", "_ipp._tcp.",
            "_printer._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_raop._tcp.",
            "_workstation._tcp.", "_device-info._tcp."
    };

    private final Context context;
    private final Listener listener;
    private final List<NsdManager.DiscoveryListener> discoveryListeners = new ArrayList<>();
    private final ConcurrentLinkedQueue<NsdServiceInfo> pending = new ConcurrentLinkedQueue<>();
    private final Set<String> seen = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NsdManager nsd;
    private WifiManager.MulticastLock lock;
    private volatile boolean running;
    private boolean resolving;

    public MdnsDiscovery(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("netscope-mdns");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Exception ignored) {}
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return;
        for (final String type : TYPES) {
            if (!running) break;
            NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
                @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {}
                @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {}
                @Override public void onDiscoveryStarted(String serviceType) {}
                @Override public void onDiscoveryStopped(String serviceType) {}
                @Override public void onServiceLost(NsdServiceInfo serviceInfo) {}
                @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                    if (!running) return;
                    pending.add(serviceInfo);
                    pump();
                }
            };
            discoveryListeners.add(discovery);
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
            } catch (Exception ignored) {}
        }
    }

    private void pump() {
        main.post(() -> {
            if (!running || resolving) return;
            final NsdServiceInfo info = pending.poll();
            if (info == null || nsd == null) return;
            resolving = true;
            try {
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        resolving = false;
                        pump();
                    }
                    @Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        try {
                            InetAddress host = serviceInfo.getHost();
                            if (host != null && host.getHostAddress() != null && running) {
                                String ip = host.getHostAddress();
                                String key = ip + "|" + serviceInfo.getServiceName();
                                synchronized (seen) {
                                    if (seen.add(key)) {
                                        listener.onService(ip, serviceInfo.getServiceName(),
                                                serviceInfo.getServiceType(), serviceInfo.getPort());
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        } finally {
                            resolving = false;
                            pump();
                        }
                    }
                });
            } catch (Exception e) {
                resolving = false;
                pump();
            }
        });
    }

    private static void stopDiscovery(NsdManager nsd, NsdManager.DiscoveryListener discovery) {
        // stopServiceDiscovery es público desde API 34; en versiones anteriores solo existe oculto.
        try {
            NsdManager.class.getMethod("stopServiceDiscovery", NsdManager.DiscoveryListener.class)
                    .invoke(nsd, discovery);
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        running = false;
        if (nsd != null) {
            for (NsdManager.DiscoveryListener discovery : discoveryListeners) {
                stopDiscovery(nsd, discovery);
            }
        }
        discoveryListeners.clear();
        pending.clear();
        if (lock != null && lock.isHeld()) {
            try { lock.release(); } catch (Exception ignored) {}
        }
        lock = null;
    }
}
