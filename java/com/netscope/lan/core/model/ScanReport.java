package com.netscope.lan.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ScanReport {
    public long id;
    public long timestamp = System.currentTimeMillis();
    public String ssid = "";
    public String subnet = "";
    public String gateway = "";
    public String localIp = "";
    public String iface = "";
    public String dns = "";
    public int prefix;
    public long durationMs;
    public boolean cancelled;
    public final List<Device> devices = new ArrayList<>();

    public Device findDevice(String ip) {
        for (Device d : devices) if (d.ip.equals(ip)) return d;
        return null;
    }

    public Device addOrGet(String ip) {
        Device d = findDevice(ip);
        if (d == null) {
            d = new Device(ip);
            d.gateway = ip.equals(gateway);
            devices.add(d);
        }
        return d;
    }

    public int totalOpenPorts() {
        int n = 0;
        for (Device d : devices) n += d.openPortCount();
        return n;
    }

    public void sortDevices() {
        Collections.sort(devices, new Comparator<Device>() {
            @Override public int compare(Device a, Device b) {
                long x = com.netscope.lan.core.net.SubnetMath.ipToLong(a.ip);
                long y = com.netscope.lan.core.net.SubnetMath.ipToLong(b.ip);
                return Long.compare(x, y);
            }
        });
    }
}
