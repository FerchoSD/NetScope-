package com.netscope.lan.test;

import com.netscope.lan.core.export.JsonExporter;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonExporterTest {
    @Test
    public void exportsNetworkAndDevices() {
        ScanReport report = new ScanReport();
        report.timestamp = 0L;
        report.ssid = "Casa \"5G\"";
        report.subnet = "192.168.1.0/24";
        report.localIp = "192.168.1.10";
        report.gateway = "192.168.1.1";
        report.iface = "wlan0";
        report.dns = "8.8.8.8";
        report.durationMs = 1200;

        Device device = report.addOrGet("192.168.1.1");
        device.hostname = "router";
        device.sources.add("tcp");
        device.addPortResult(new PortResult(80, PortResult.STATE_OPEN, "HTTP", "nginx", 3, 1, "HTTP sin cifrar"));

        String json = JsonExporter.export(report);
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"app\": \"NetScope\""));
        assertTrue(json.contains("\"ssid\": \"Casa \\\"5G\\\"\""));
        assertTrue(json.contains("\"local_ip\": \"192.168.1.10\""));
        assertTrue(json.contains("\"ip\": \"192.168.1.1\""));
        assertTrue(json.contains("\"gateway\": true"));
        assertTrue(json.contains("\"sources\": [\"tcp\"]"));
        assertTrue(json.contains("\"port\": 80, \"state\": \"open\""));
        assertTrue(json.contains("\"risk\": \"medium\""));
        assertEquals(countChar(json, '{'), countChar(json, '}'));
        assertEquals(countChar(json, '['), countChar(json, ']'));
    }

    @Test
    public void escapesControlCharacters() {
        ScanReport report = new ScanReport();
        Device device = report.addOrGet("10.0.0.5");
        device.hostname = "linea\nnueva";
        device.addPortResult(new PortResult(22, PortResult.STATE_OPEN, "SSH", "", -1, 0, ""));

        String json = JsonExporter.exportDevice(report, device);
        assertTrue(json.contains("\"hostname\": \"linea\\nnueva\""));
        assertTrue(json.contains("\"latency_ms\": -1") == false);
        assertTrue(json.contains("\"latency_ms\": null"));
        assertFalse(json.contains("\"risk\""));
    }

    private static int countChar(String text, char c) {
        int n = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == c) n++;
        return n;
    }
}
