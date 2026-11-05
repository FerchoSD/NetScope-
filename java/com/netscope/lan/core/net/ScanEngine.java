package com.netscope.lan.core.net;

import android.content.Context;

import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanConfig;
import com.netscope.lan.core.model.ScanReport;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanEngine {
    public interface Listener {
        void onStage(String stage);
        void onProgress(int done, int total);
        void onDevice(Device device);
        void onFinished(ScanReport report);
        void onError(String message);
    }

    private final Context context;
    private final ScanConfig config;
    private final Listener listener;
    private final Object lock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private ScanReport report;
    private NetworkInfoProvider.Snapshot network;
    private SubnetScanner subnetScanner;
    private MdnsDiscovery mdns;
    private SsdpDiscovery ssdp;
    private ExecutorService netbiosPool;
    private ExecutorService dnsPool;
    private ExecutorService probePool;

    public ScanEngine(Context context, ScanConfig config, Listener listener) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.listener = listener;
    }

    public void cancel() {
        cancelled.set(true);
        if (subnetScanner != null) subnetScanner.cancel();
        stopDiscovery();
        if (netbiosPool != null) netbiosPool.shutdownNow();
        if (dnsPool != null) dnsPool.shutdownNow();
        if (probePool != null) probePool.shutdownNow();
    }

    public void run() {
        long start = System.currentTimeMillis();
        report = new ScanReport();
        report.timestamp = start;
        try {
            network = NetworkInfoProvider.get(context);
            if (!network.scannable) {
                listener.onError("Conéctate a una red Wi-Fi o Ethernet para escanear dispositivos.");
                return;
            }
            report.ssid = network.ssid;
            report.subnet = network.cidr();
            report.gateway = network.gateway;
            report.localIp = network.localIp;
            report.iface = network.iface;
            report.dns = network.dns;
            report.prefix = network.prefix;

            listener.onStage("Analizando la red…");
            Device self = merge(network.localIp, "local");
            self.self = true;
            emit(self);
            if (!network.gateway.isEmpty()) {
                Device gateway = merge(network.gateway, "gateway");
                gateway.gateway = true;
                emit(gateway);
            }

            String[] hosts = SubnetMath.usableHosts(network.localIp, network.prefix, config.maxHosts);
            startDiscovery(hosts);

            if (cancelled.get()) {
                finish(start);
                return;
            }

            listener.onStage("Buscando dispositivos…");
            subnetScanner = new SubnetScanner();
            if (cancelled.get()) subnetScanner.cancel();
            subnetScanner.scan(hosts, config.livenessPorts, config.timeoutMs, config.concurrency,
                    new SubnetScanner.Listener() {
                        @Override public void onHost(SubnetScanner.Probe probe) {
                            if (cancelled.get() || finished.get() || !probe.alive) return;
                            Device device = merge(probe.ip, "TCP");
                            device.latencyMs = probe.latencyMs;
                            for (Integer port : probe.openPorts) addDetectedPort(device, port, null);
                            emit(device);
                        }
                        @Override public void onProgress(int done, int total) {
                            if (!finished.get()) listener.onProgress(done, total);
                        }
                    });

            if (!cancelled.get() && config.reverseDns) resolveNames();
            if (!cancelled.get()) probeWebInfo();
            listener.onStage("Identificando servicios…");
            stopDiscovery();
            finish(start);
        } catch (Throwable t) {
            if (!finished.getAndSet(true)) {
                listener.onError("Error durante el escaneo: " + t.getClass().getSimpleName());
            }
            stopDiscovery();
        }
    }

    private void finish(long start) {
        if (finished.getAndSet(true)) return;
        stopDiscovery();
        if (netbiosPool != null) netbiosPool.shutdownNow();
        synchronized (lock) {
            for (Device device : report.devices) HostClassifier.classify(device);
            report.sortDevices();
        }
        report.durationMs = System.currentTimeMillis() - start;
        report.cancelled = cancelled.get();
        listener.onStage("Finalizando…");
        listener.onFinished(report);
    }

    private Device merge(String ip, String source) {
        synchronized (lock) {
            Device device = report.addOrGet(ip);
            device.sources.add(source);
            device.lastSeen = System.currentTimeMillis();
            return device;
        }
    }

    private void emit(Device device) {
        if (finished.get() || cancelled.get()) return;
        synchronized (lock) {
            HostClassifier.classify(device);
        }
        listener.onDevice(device);
    }

    private void addDetectedPort(Device device, int port, String serviceHint) {
        if (device.getPortState(port) == PortResult.STATE_OPEN) return;
        String service = ServiceCatalog.name(port);
        if (service.isEmpty() && serviceHint != null) service = serviceHint;
        device.addPortResult(new PortResult(port, PortResult.STATE_OPEN, service, "",
                -1, ServiceCatalog.risk(port), ServiceCatalog.riskNote(port)));
    }

    private boolean accept(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (ip.equals(network.localIp) || ip.equals(network.gateway)) return true;
        return SubnetMath.isPrivate(ip) && SubnetMath.sameSubnet(ip, network.localIp, network.prefix);
    }

    private void startDiscovery(String[] hosts) {
        if (config.mdns) {
            mdns = new MdnsDiscovery(context, (ip, name, type, port) -> {
                if (finished.get() || cancelled.get() || !accept(ip)) return;
                Device device = merge(ip, "mDNS");
                if (device.hostname.isEmpty() && name != null && !name.isEmpty()) device.hostname = name;
                String label = serviceLabel(type);
                if (!device.services.contains(label)) {
                    synchronized (lock) { device.services.add(label); }
                }
                if (port > 0) {
                    addDetectedPort(device, port, label);
                }
                emit(device);
            });
            mdns.start();
        }
        if (config.ssdp) {
            ssdp = new SsdpDiscovery(context, network.localIp, (ip, name, vendor, model, type, location) -> {
                if (finished.get() || cancelled.get() || !accept(ip)) return;
                Device device = merge(ip, "SSDP");
                if (device.hostname.isEmpty() && name != null && !name.isEmpty()) device.hostname = name;
                String maker = (vendor == null ? "" : vendor) + (model == null ? "" : " " + model);
                maker = maker.trim();
                if (device.vendor.isEmpty() && !maker.isEmpty()) device.vendor = maker;
                if (!device.services.contains("UPnP")) {
                    synchronized (lock) { device.services.add("UPnP"); }
                }
                if (location != null) {
                    try {
                        int port = URI.create(location).getPort();
                        if (port > 0) addDetectedPort(device, port, "UPnP");
                        else addDetectedPort(device, 80, "UPnP");
                    } catch (Exception ignored) {}
                }
                emit(device);
            });
            ssdp.start();
        }
        if (config.netbios) {
            netbiosPool = Executors.newFixedThreadPool(32, r -> {
                Thread t = new Thread(r, "netscope-nb");
                t.setDaemon(true);
                return t;
            });
            for (final String host : hosts) {
                netbiosPool.execute(() -> {
                    if (finished.get() || cancelled.get()) return;
                    String name = NetbiosResolver.query(host, 350);
                    if (name != null && !name.isEmpty() && accept(host)) {
                        Device device = merge(host, "NetBIOS");
                        if (device.hostname.isEmpty()) device.hostname = name;
                        emit(device);
                    }
                });
            }
        }
    }

    private void stopDiscovery() {
        if (mdns != null) { try { mdns.stop(); } catch (Exception ignored) {} mdns = null; }
        if (ssdp != null) { try { ssdp.stop(); } catch (Exception ignored) {} ssdp = null; }
    }

    private void resolveNames() {
        List<Device> pending = new ArrayList<>();
        synchronized (lock) {
            for (Device device : report.devices) {
                if (device.hostname == null || device.hostname.isEmpty()) pending.add(device);
            }
        }
        if (pending.isEmpty() || pending.size() > 64) return;
        listener.onStage("Resolviendo nombres…");
        dnsPool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "netscope-dns");
            t.setDaemon(true);
            return t;
        });
        List<Future<String>> futures = new ArrayList<>();
        for (final Device device : pending) {
            futures.add(dnsPool.submit(() -> {
                try {
                    InetAddress address = InetAddress.getByName(device.ip);
                    return address.getCanonicalHostName();
                } catch (Exception e) {
                    return null;
                }
            }));
        }
        long deadline = System.currentTimeMillis() + 3000;
        for (int i = 0; i < pending.size(); i++) {
            if (cancelled.get() || finished.get()) break;
            try {
                long wait = Math.max(50, deadline - System.currentTimeMillis());
                String name = futures.get(i).get(wait, TimeUnit.MILLISECONDS);
                Device device = pending.get(i);
                if (name != null && !name.isEmpty() && !name.equals(device.ip)) {
                    synchronized (lock) { device.hostname = name; }
                    emit(device);
                }
            } catch (Exception e) {
                futures.get(i).cancel(true);
            }
        }
        dnsPool.shutdownNow();
        dnsPool = null;
    }

    private void probeWebInfo() {
        List<Device> targets = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();
        synchronized (lock) {
            for (Device device : report.devices) {
                if (targets.size() >= 48) break;
                int port = firstWebPort(device);
                if (port <= 0) continue;
                targets.add(device);
                ports.add(port);
            }
        }
        if (targets.isEmpty()) return;
        listener.onStage("Recopilando información…");
        probePool = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r, "netscope-probe");
            t.setDaemon(true);
            return t;
        });
        List<Future<HttpAudit.Result>> futures = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            final String ip = targets.get(i).ip;
            final int port = ports.get(i);
            futures.add(probePool.submit(() -> HttpAudit.audit(ip, port, 900)));
        }
        long deadline = System.currentTimeMillis() + 10000;
        for (int i = 0; i < targets.size(); i++) {
            if (cancelled.get() || finished.get()) break;
            Device device = targets.get(i);
            try {
                long wait = Math.max(50, deadline - System.currentTimeMillis());
                HttpAudit.Result info = futures.get(i).get(wait, TimeUnit.MILLISECONDS);
                if (info == null || !info.reachable) continue;
                synchronized (lock) {
                    device.httpTitle = info.title;
                    device.server = info.server;
                    device.tls = info.tls;
                    device.webAudit = info.compact();
                    if (device.hostname.isEmpty() && !info.title.isEmpty()) {
                        device.hostname = deriveName(info.title);
                    }
                    if (!device.sources.contains("HTTP")) device.sources.add("HTTP");
                }
                emit(device);
            } catch (Exception e) {
                futures.get(i).cancel(true);
            }
        }
        probePool.shutdownNow();
        probePool = null;
    }

    private static int firstWebPort(Device device) {
        int[] preferred = {443, 8443, 9443, 80, 8080, 8008, 8081, 8888, 8000,
                3000, 5000, 8090, 9000, 10000};
        for (int port : preferred) {
            if (device.hasOpenPort(port)) return port;
        }
        for (PortResult port : device.openPorts) {
            if (ServiceCatalog.isHttp(port.port)) return port.port;
        }
        return -1;
    }

    private static String serviceLabel(String type) {
        if (type == null || type.isEmpty()) return "";
        String label = type.startsWith("_") ? type.substring(1) : type;
        int dot = label.indexOf('.');
        if (dot > 0) label = label.substring(0, dot);
        return label;
    }

    private static String deriveName(String title) {
        String cleaned = title.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 48) cleaned = cleaned.substring(0, 48).trim();
        return cleaned;
    }
}
