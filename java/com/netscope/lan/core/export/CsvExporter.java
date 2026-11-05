package com.netscope.lan.core.export;

import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CsvExporter {
    private CsvExporter() {}

    public static String export(ScanReport report) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append('\uFEFF');
        sb.append("fecha,ssid,subred,ip,nombre,fabricante,tipo,latencia_ms,puerto,estado,servicio,banner,riesgo\n");
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(report.timestamp));
        for (Device device : report.devices) {
            List<PortResult> ports = new ArrayList<>(device.openPorts);
            Collections.sort(ports, new Comparator<PortResult>() {
                @Override public int compare(PortResult a, PortResult b) { return Integer.compare(a.port, b.port); }
            });
            if (ports.isEmpty()) {
                row(sb, report, device, date, null);
            } else {
                for (PortResult port : ports) row(sb, report, device, date, port);
            }
        }
        return sb.toString();
    }

    private static void row(StringBuilder sb, ScanReport report, Device device, String date, PortResult port) {
        sb.append(csv(date)).append(',');
        sb.append(csv(report.ssid)).append(',');
        sb.append(csv(report.subnet)).append(',');
        sb.append(csv(device.ip)).append(',');
        sb.append(csv(device.hostname)).append(',');
        sb.append(csv(device.vendor)).append(',');
        sb.append(csv(device.typeLabel)).append(',');
        sb.append(device.latencyMs < 0 ? "" : String.valueOf(device.latencyMs)).append(',');
        if (port == null) {
            sb.append(",,,,,");
        } else {
            sb.append(port.port).append(',');
            sb.append("abierto").append(',');
            sb.append(csv(port.service)).append(',');
            sb.append(csv(port.banner)).append(',');
            sb.append(csv(risk(port.risk)));
        }
        sb.append('\n');
    }

    private static String risk(int value) {
        if (value >= 2) return "alto";
        if (value == 1) return "medio";
        return "";
    }

    static String csv(String value) {
        if (value == null) return "";
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
