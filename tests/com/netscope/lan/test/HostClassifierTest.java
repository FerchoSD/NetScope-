package com.netscope.lan.test;

import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.net.HostClassifier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HostClassifierTest {
    private static Device device(String ip, int... openPorts) {
        Device device = new Device(ip);
        for (int port : openPorts) {
            device.addPortResult(new PortResult(port, PortResult.STATE_OPEN, "svc", "", -1, 0, ""));
        }
        return device;
    }

    @Test
    public void classifiesGatewayAsRouter() {
        Device device = device("192.168.1.1", 80, 443);
        device.gateway = true;
        HostClassifier.classify(device);
        assertEquals("router", device.type);
        assertEquals("Router / gateway", device.typeLabel);
        assertEquals("Firmware embebido", device.osGuess);
    }

    @Test
    public void classifiesPrinterByPort() {
        Device device = device("192.168.1.20", 631);
        HostClassifier.classify(device);
        assertEquals("printer", device.type);
        assertEquals("Impresora", device.typeLabel);
    }

    @Test
    public void classifiesCameraByVendorHint() {
        Device device = device("192.168.1.30", 554);
        device.vendor = "Hikvision";
        HostClassifier.classify(device);
        assertEquals("camera", device.type);
        assertEquals("Cámara IP", device.typeLabel);
    }

    @Test
    public void classifiesMediaDevice() {
        Device device = device("192.168.1.40", 32400);
        HostClassifier.classify(device);
        assertEquals("tv", device.type);
        assertEquals("TV / media", device.typeLabel);
    }

    @Test
    public void classifiesWindowsComputer() {
        Device device = device("192.168.1.50", 445, 139);
        HostClassifier.classify(device);
        assertEquals("pc", device.type);
        assertEquals("Windows (probable)", device.osGuess);
    }

    @Test
    public void classifiesAppleDevice() {
        Device device = device("192.168.1.60", 548);
        HostClassifier.classify(device);
        assertEquals("nas", device.type);
        assertEquals("Apple (probable)", device.osGuess);
    }

    @Test
    public void classifiesLinuxServer() {
        Device device = device("192.168.1.70", 22);
        HostClassifier.classify(device);
        assertEquals("server", device.type);
        assertEquals("Linux/Unix (probable)", device.osGuess);
    }

    @Test
    public void classifiesPhoneAndSelf() {
        Device phone = device("192.168.1.80", 62078);
        HostClassifier.classify(phone);
        assertEquals("phone", phone.type);
        assertEquals("Apple (probable)", phone.osGuess);

        Device self = device("192.168.1.90", 5555);
        self.self = true;
        HostClassifier.classify(self);
        assertEquals("phone", self.type);
        assertEquals("Este dispositivo", self.typeLabel);
        assertEquals("Android (este dispositivo)", self.osGuess);
    }

    @Test
    public void fallsBackToUnknown() {
        Device device = device("192.168.1.100");
        HostClassifier.classify(device);
        assertEquals("unknown", device.type);
        assertEquals("Dispositivo", device.typeLabel);
        assertEquals("No determinado", device.osGuess);
    }

    @Test
    public void guessesOsFromHints() {
        Device unnamed = device("10.0.0.2");
        assertTrue(HostClassifier.guessOs(unnamed, "mi-linux-box").startsWith("Linux"));
        assertTrue(HostClassifier.guessOs(unnamed, "macbook airplay").startsWith("Apple"));
        assertTrue(HostClassifier.guessOs(unnamed, "windows-pc smb").startsWith("Windows"));
    }

    @Test
    public void mapsIconLetters() {
        assertEquals("R", HostClassifier.iconLetter("router"));
        assertEquals("P", HostClassifier.iconLetter("printer"));
        assertEquals("C", HostClassifier.iconLetter("camera"));
        assertEquals("TV", HostClassifier.iconLetter("tv"));
        assertEquals("PC", HostClassifier.iconLetter("pc"));
        assertEquals("N", HostClassifier.iconLetter("nas"));
        assertEquals("M", HostClassifier.iconLetter("phone"));
        assertEquals("S", HostClassifier.iconLetter("server"));
        assertEquals("?", HostClassifier.iconLetter("unknown"));
    }
}
