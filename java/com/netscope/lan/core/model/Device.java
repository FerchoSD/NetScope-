package com.netscope.lan.core.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Device {
    public final String ip;
    public String hostname = "";
    public String customName = "";
    public boolean trusted;
    public boolean isNew;
    public String vendor = "";
    public String type = "unknown";
    public String typeLabel = "Dispositivo";
    public String osGuess = "";
    public String server = "";
    public String httpTitle = "";
    public String tls = "";
    public String webAudit = "";
    public final Set<String> services = new LinkedHashSet<>();
    public long latencyMs = -1;
    public boolean gateway;
    public boolean self;
    public final Set<String> sources = new LinkedHashSet<>();
    public final List<PortResult> openPorts = new ArrayList<>();
    public byte[] portStates;
    public int scannedPorts;
    public int closedPorts;
    public int filteredPorts;
    public long lastSeen = System.currentTimeMillis();

    public Device(String ip) {
        this.ip = ip;
    }

    private void ensureStates() {
        if (portStates == null) portStates = new byte[65536];
    }

    public void resetPortScan() {
        portStates = new byte[65536];
        scannedPorts = 0;
        closedPorts = 0;
        filteredPorts = 0;
        openPorts.clear();
    }

    public void addPortResult(PortResult result) {
        ensureStates();
        int previous = portStates[result.port];
        if (previous == PortResult.STATE_CLOSED) closedPorts--;
        if (previous == PortResult.STATE_FILTERED) filteredPorts--;
        if (previous == PortResult.STATE_UNSCANNED && result.state != PortResult.STATE_UNSCANNED) {
            scannedPorts++;
        }
        portStates[result.port] = (byte) result.state;
        if (result.state == PortResult.STATE_CLOSED) closedPorts++;
        if (result.state == PortResult.STATE_FILTERED) filteredPorts++;
        if (result.state == PortResult.STATE_OPEN) {
            removeOpenPort(result.port);
            openPorts.add(result);
        }
    }

    public void removeOpenPort(int port) {
        for (int i = openPorts.size() - 1; i >= 0; i--) {
            if (openPorts.get(i).port == port) openPorts.remove(i);
        }
    }

    public int getPortState(int port) {
        if (portStates == null || port < 0 || port > 65535) return PortResult.STATE_UNSCANNED;
        return portStates[port];
    }

    public boolean hasOpenPort(int port) {
        for (PortResult r : openPorts) if (r.port == port) return true;
        return false;
    }

    public int openPortCount() {
        return openPorts.size();
    }

    public String displayName() {
        if (customName != null && customName.length() > 0) return customName;
        if (hostname != null && hostname.length() > 0) return hostname;
        if (self) return "Este dispositivo";
        if (gateway) return "Puerta de enlace";
        return ip;
    }
}
