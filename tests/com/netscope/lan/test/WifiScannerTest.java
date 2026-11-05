package com.netscope.lan.test;

import com.netscope.lan.core.net.WifiScanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WifiScannerTest {
    @Test
    public void mapsFrequenciesToChannels() {
        assertEquals(1, WifiScanner.channelForFrequency(2412));
        assertEquals(6, WifiScanner.channelForFrequency(2437));
        assertEquals(11, WifiScanner.channelForFrequency(2462));
        assertEquals(14, WifiScanner.channelForFrequency(2484));
        assertEquals(36, WifiScanner.channelForFrequency(5180));
        assertEquals(165, WifiScanner.channelForFrequency(5825));
        assertEquals(1, WifiScanner.channelForFrequency(5955));
        assertEquals(-1, WifiScanner.channelForFrequency(0));
    }

    @Test
    public void mapsFrequenciesToBands() {
        assertEquals("2,4 GHz", WifiScanner.bandForFrequency(2412));
        assertEquals("5 GHz", WifiScanner.bandForFrequency(5180));
        assertEquals("6 GHz", WifiScanner.bandForFrequency(6115));
        assertEquals("", WifiScanner.bandForFrequency(0));
    }

    @Test
    public void labelsSecurityModes() {
        assertEquals("WPA3", WifiScanner.securityLabel("[WPA3-SAE-CCMP][ESS]"));
        assertEquals("WPA2", WifiScanner.securityLabel("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"));
        assertEquals("WPA", WifiScanner.securityLabel("[WPA-PSK-TKIP][ESS]"));
        assertEquals("WEP", WifiScanner.securityLabel("[WEP][ESS]"));
        assertEquals("Abierta", WifiScanner.securityLabel(""));
        assertEquals("Abierta", WifiScanner.securityLabel(null));
        assertEquals("WPA2 Enterprise", WifiScanner.securityLabel("[RSN-EAP-CCMP][ESS]"));
    }

    @Test
    public void convertsRssiToPercentAndBars() {
        assertEquals(0, WifiScanner.signalPercent(-90));
        assertEquals(50, WifiScanner.signalPercent(-60));
        assertEquals(100, WifiScanner.signalPercent(-30));
        assertEquals(100, WifiScanner.signalPercent(-10));

        assertEquals(4, WifiScanner.signalBars(100));
        assertEquals(3, WifiScanner.signalBars(60));
        assertEquals(2, WifiScanner.signalBars(30));
        assertEquals(0, WifiScanner.signalBars(0));

        assertEquals("████", WifiScanner.signalGlyph(100));
        assertEquals("███░", WifiScanner.signalGlyph(50));
        assertEquals("░░░░", WifiScanner.signalGlyph(0));
    }
}
