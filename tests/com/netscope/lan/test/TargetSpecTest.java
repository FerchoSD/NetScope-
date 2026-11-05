package com.netscope.lan.test;

import com.netscope.lan.core.net.TargetSpec;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TargetSpecTest {
    @Test
    public void aceptaHostSinPuerto() {
        TargetSpec spec = TargetSpec.parse("ejemplo.com", "", false);
        assertEquals("ejemplo.com", spec.host);
        assertEquals(-1, spec.port);
        assertFalse(spec.hasPort());
        assertFalse(spec.tls);
    }

    @Test
    public void aceptaIpYCampoDePuerto() {
        TargetSpec spec = TargetSpec.parse("192.168.1.1", "8080", false);
        assertEquals("192.168.1.1", spec.host);
        assertEquals(8080, spec.port);
        assertFalse(spec.tls);
    }

    @Test
    public void extraePuertoDelHost() {
        TargetSpec spec = TargetSpec.parse("ejemplo.com:8443", "", false);
        assertEquals("ejemplo.com", spec.host);
        assertEquals(8443, spec.port);
    }

    @Test
    public void elCampoDePuertoTienePrioridad() {
        TargetSpec spec = TargetSpec.parse("ejemplo.com:8080", "443", false);
        assertEquals(443, spec.port);
    }

    @Test
    public void limpiaEsquemaUsuarioYRuta() {
        TargetSpec spec = TargetSpec.parse("https://usuario@ejemplo.com/ruta?x=1#y", "", false);
        assertEquals("ejemplo.com", spec.host);
        assertTrue(spec.tls);
    }

    @Test
    public void interpretaIpv6ConPuerto() {
        TargetSpec spec = TargetSpec.parse("[2001:db8::1]:8080", "", false);
        assertEquals("2001:db8::1", spec.host);
        assertEquals(8080, spec.port);
    }

    @Test
    public void interpretaIpv6SinPuerto() {
        TargetSpec spec = TargetSpec.parse("2001:db8::1", "", false);
        assertEquals("2001:db8::1", spec.host);
        assertEquals(-1, spec.port);
    }

    @Test
    public void respetaElSwitchTls() {
        TargetSpec spec = TargetSpec.parse("ejemplo.com", "443", true);
        assertTrue(spec.tls);
    }

    @Test
    public void rechazaEntradasInvalidas() {
        String[][] cases = {
                {"", ""}, {"   ", ""}, {"host con espacio", ""}, {"ejemplo.com:70000", ""},
                {"ejemplo.com:", ""}, {"http://", ""}, {"-host.com", ""}, {"host..com", ""}
        };
        for (String[] entry : cases) {
            try {
                TargetSpec.parse(entry[0], entry[1], false);
                fail("Debía rechazar: " + entry[0]);
            } catch (IllegalArgumentException expected) {
                // correcto
            }
        }
    }

    @Test
    public void validaHosts() {
        assertTrue(TargetSpec.isValidHost("ejemplo.com"));
        assertTrue(TargetSpec.isValidHost("mi-equipo.local"));
        assertTrue(TargetSpec.isValidHost("10.0.0.1"));
        assertTrue(TargetSpec.isValidHost("2001:db8::1"));
        assertFalse(TargetSpec.isValidHost(""));
        assertFalse(TargetSpec.isValidHost("a b"));
        assertFalse(TargetSpec.isValidHost("host/ruta"));
    }
}
