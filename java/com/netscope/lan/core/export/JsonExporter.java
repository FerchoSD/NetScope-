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

public final class JsonExporter {
    private JsonExporter() {}

    public static String export(ScanReport report) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        sb.append("  \"app\": \"NetScope\",\n");
        sb.append("  \"version\": \"1.0\",\n");
        sb.append("  \"generated_at\": \"").append(iso(report.timestamp)).append("\",\n");
        sb.append("  \"duration_ms\": ").append(report.durationMs).append(",\n");
        sb.append("  \"cancelled\": ").append(report.cancelled).append(",\n");
        sb.append("  \"network\": {\n");
        sb.append("    \"ssid\": \"").append(esc(report.ssid)).append("\",\n");
        sb.append("    \"subnet\": \"").append(esc(report.subnet)).append("\",\n");
        sb.append("    \"local_ip\": \"").append(esc(report.localIp)).append("\",\n");
        sb.append("    \"gateway\": \"").append(esc(report.gateway)).append("\",\n");
        sb.append("    \"interface\": \"").append(esc(report.iface)).append("\",\n");
        sb.append("    \"dns\": \"").append(esc(report.dns)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"devices\": [");
        for (int i = 0; i < report.devices.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\n');
            appendDevice(sb, report.devices.get(i), "    ");
        }
        if (!report.devices.isEmpty()) sb.append('\n').append("  ");
        sb.append("]\n}\n");
        return sb.toString();
    }

    public static String exportDevice(ScanReport report, Device device) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\n");
        sb.append("  \"app\": \"NetScope\",\n");
        sb.append("  \"version\": \"1.0\",\n");
        sb.append("  \"generated_at\": \"").append(iso(System.currentTimeMillis())).append("\",\n");
        sb.append("  \"network\": {\n");
        sb.append("    \"ssid\": \"").append(esc(report.ssid)).append("\",\n");
        sb.append("    \"subnet\": \"").append(esc(report.subnet)).append("\"\n");
        sb.append("  },\n");
        sb.append("  \"device\": ");
        appendDevice(sb, device, "  ");
        sb.append("\n}\n");
        return sb.toString();
    }

    private static void appendDevice(StringBuilder sb, Device device, String indent) {
        sb.append(indent).append("{\n");
        sb.append(indent).append("  \"ip\": \"").append(esc(device.ip)).append("\",\n");
        sb.append(indent).append("  \"hostname\": \"").append(esc(device.hostname)).append("\",\n");
        sb.append(indent).append("  \"vendor\": \"").append(esc(device.vendor)).append("\",\n");
        sb.append(indent).append("  \"type\": \"").append(esc(device.type)).append("\",\n");
        sb.append(indent).append("  \"type_label\": \"").append(esc(device.typeLabel)).append("\",\n");
        sb.append(indent).append("  \"latency_ms\": ").append(device.latencyMs < 0 ? "null" : String.valueOf(device.latencyMs)).append(",\n");
        sb.append(indent).append("  \"gateway\": ").append(device.gateway).append(",\n");
        sb.append(indent).append("  \"sources\": [");
        int i = 0;
        for (String source : device.sources) {
            if (i++ > 0) sb.append(", ");
            sb.append('"').append(esc(source)).append('"');
        }
        sb.append("],\n");
        sb.append(indent).append("  \"open_ports\": [");
        List<PortResult> ports = new ArrayList<>(device.openPorts);
        Collections.sort(ports, new Comparator<PortResult>() {
            @Override public int compare(PortResult a, PortResult b) { return Integer.compare(a.port, b.port); }
        });
        for (int p = 0; p < ports.size(); p++) {
            PortResult r = ports.get(p);
            if (p > 0) sb.append(',');
            sb.append('\n').append(indent).append("    {");
            sb.append("\"port\": ").append(r.port);
            sb.append(", \"state\": \"open\"");
            sb.append(", \"service\": \"").append(esc(r.service)).append('"');
            if (!r.banner.isEmpty()) sb.append(", \"banner\": \"").append(esc(r.banner)).append('"');
            if (r.risk > 0) sb.append(", \"risk\": \"").append(riskLabel(r.risk)).append('"');
            if (!r.riskNote.isEmpty()) sb.append(", \"risk_note\": \"").append(esc(r.riskNote)).append('"');
            sb.append('}');
        }
        if (!ports.isEmpty()) sb.append('\n').append(indent).append("  ");
        sb.append("]\n");
        sb.append(indent).append('}');
    }

    private static String riskLabel(int risk) {
        if (risk >= 2) return "high";
        if (risk == 1) return "medium";
        return "low";
    }

    private static String iso(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date(timestamp));
    }

    static String esc(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
