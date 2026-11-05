package com.netscope.lan.test;

import com.netscope.lan.core.net.ServiceCatalog;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServiceCatalogTest {
    @Test
    public void knownServices() {
        assertEquals("SSH", ServiceCatalog.name(22));
        assertEquals("HTTPS", ServiceCatalog.name(443));
        assertEquals("", ServiceCatalog.name(31337));
    }

    @Test
    public void riskLevels() {
        assertEquals(2, ServiceCatalog.risk(445));
        assertEquals(1, ServiceCatalog.risk(8080));
        assertEquals(0, ServiceCatalog.risk(31337));
        assertTrue(ServiceCatalog.riskNote(3306).length() > 0);
        assertEquals("", ServiceCatalog.riskNote(31337));
    }

    @Test
    public void httpPorts() {
        assertTrue(ServiceCatalog.isHttp(80));
        assertTrue(ServiceCatalog.isHttp(8080));
        assertTrue(!ServiceCatalog.isHttp(22));
    }

    @Test
    public void portPresetsAreSortedAndUnique() {
        assertPreset(ServiceCatalog.quickPorts());
        assertPreset(ServiceCatalog.webPorts());
        assertPreset(ServiceCatalog.commonPorts());
        assertEquals(20, ServiceCatalog.quickPorts().length);
        assertEquals(15, ServiceCatalog.webPorts().length);
        assertEquals(65535, ServiceCatalog.fullPorts().length);
        assertEquals(1, ServiceCatalog.fullPorts()[0]);
        assertEquals(65535, ServiceCatalog.fullPorts()[65534]);
    }

    @Test
    public void parseCustomRanges() {
        assertArrayEquals(new int[]{22, 80, 81, 82, 443},
                ServiceCatalog.parsePorts("443, 80-82, 22, 80"));
        assertArrayEquals(new int[]{1, 65535}, ServiceCatalog.parsePorts("1 65535"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectEmptyRange() {
        ServiceCatalog.parsePorts("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectOutOfBounds() {
        ServiceCatalog.parsePorts("70000");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectInvertedRange() {
        ServiceCatalog.parsePorts("100-50");
    }

    private static void assertPreset(int[] ports) {
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < ports.length; i++) {
            assertTrue("puerto fuera de rango: " + ports[i], ports[i] >= 1 && ports[i] <= 65535);
            assertTrue("duplicado: " + ports[i], seen.add(ports[i]));
            if (i > 0) assertTrue("desordenado en " + i, ports[i - 1] < ports[i]);
        }
    }
}
