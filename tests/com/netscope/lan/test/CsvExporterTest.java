package com.netscope.lan.test;

import com.netscope.lan.core.export.CsvExporter;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CsvExporterTest {
    @Test
    public void exportsHeaderAndRows() {
        ScanReport report = new ScanReport();
        report.timestamp = 0L;
        report.ssid = "Casa";
        report.subnet = "192.168.1.0/24";

        Device router = report.addOrGet("192.168.1.1");
        router.hostname = "router";
        router.vendor = "TP-Link";
        router.typeLabel = "Router";
        router.latencyMs = 4;
        router.osGuess = "Firmware embebido";
        router.services.add("HTTP");
        router.services.add("HTTPS");
        router.addPortResult(new PortResult(80, PortResult.STATE_OPEN, "HTTP", "nginx", 4, 1, "HTTP sin cifrar"));

        Device printer = report.addOrGet("192.168.1.20");
        printer.typeLabel = "Impresora";

        String csv = CsvExporter.export(report);
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("\uFEFFfecha,ssid,subred,ip,nombre,fabricante,tipo,latencia_ms,puerto,estado,servicio,banner,riesgo,so,servidor,titulo_http,tls,servicios"));
        assertTrue(lines[1].contains("192.168.1.1"));
        assertTrue(lines[1].contains(",80,abierto,HTTP,nginx,medio"));
        assertTrue(lines[1].endsWith(",Firmware embebido,,,,HTTP HTTPS"));
        assertTrue(lines[2].contains("192.168.1.20"));
        assertEquals(18, lines[2].split(",", -1).length);
    }

    @Test
    public void escapesCommasAndQuotes() {
        ScanReport report = new ScanReport();
        Device device = report.addOrGet("10.0.0.2");
        device.hostname = "mi,equipo";
        device.addPortResult(new PortResult(22, PortResult.STATE_OPEN, "SSH", "SSH-2.0 \"OpenSSH\"", 1, 0, ""));

        String csv = CsvExporter.export(report);
        assertTrue(csv.contains("\"mi,equipo\""));
        assertTrue(csv.contains("\"SSH-2.0 \"\"OpenSSH\"\"\""));
    }
}
