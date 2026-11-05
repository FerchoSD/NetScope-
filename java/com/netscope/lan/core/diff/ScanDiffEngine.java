package com.netscope.lan.core.diff;

import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanDiff;
import com.netscope.lan.core.model.ScanReport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ScanDiffEngine {
    private ScanDiffEngine() {}

    public static ScanDiff compare(ScanReport current, ScanReport previous) {
        ScanDiff diff = new ScanDiff();
        if (current == null) return diff;
        if (previous == null) {
            for (Device d : current.devices) diff.addedIps.add(d.ip);
            return diff;
        }
        Map<String, Device> previousByIp = new HashMap<>();
        for (Device d : previous.devices) previousByIp.put(d.ip, d);
        Set<String> currentIps = new HashSet<>();
        for (Device currentDevice : current.devices) {
            currentIps.add(currentDevice.ip);
            Device before = previousByIp.get(currentDevice.ip);
            if (before == null) {
                diff.addedIps.add(currentDevice.ip);
                continue;
            }
            Set<Integer> beforePorts = openPorts(before);
            Set<Integer> nowPorts = openPorts(currentDevice);
            ScanDiff.PortChange change = new ScanDiff.PortChange(currentDevice.ip);
            for (Integer port : nowPorts) {
                if (!beforePorts.contains(port)) change.opened.add(port);
            }
            for (Integer port : beforePorts) {
                if (!nowPorts.contains(port)) change.closed.add(port);
            }
            if (!change.isEmpty()) {
                java.util.Collections.sort(change.opened);
                java.util.Collections.sort(change.closed);
                diff.portChanges.add(change);
            }
        }
        for (Device d : previous.devices) {
            if (!currentIps.contains(d.ip)) diff.removedIps.add(d.ip);
        }
        return diff;
    }

    private static Set<Integer> openPorts(Device device) {
        Set<Integer> ports = new HashSet<>();
        for (PortResult result : device.openPorts) ports.add(result.port);
        return ports;
    }
}
