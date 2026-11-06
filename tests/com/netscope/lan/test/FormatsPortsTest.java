package com.netscope.lan.test;

import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.util.Formats;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FormatsPortsTest {
    private static PortResult open(int port, String service) {
        return new PortResult(port, PortResult.STATE_OPEN, service, "", 1, 0, "");
    }

    @Test
    public void listsPortsSortedWithService() {
        List<PortResult> ports = new ArrayList<>(Arrays.asList(
                open(443, "HTTPS"), open(22, "SSH"), open(80, "HTTP")));
        assertEquals("22 SSH · 80 HTTP · 443 HTTPS", Formats.ports(ports, 10));
    }

    @Test
    public void truncatesWhenOverLimit() {
        List<PortResult> ports = new ArrayList<>(Arrays.asList(
                open(22, "SSH"), open(80, "HTTP"), open(443, "HTTPS"), open(3389, "RDP")));
        assertEquals("22 SSH · 80 HTTP · +2", Formats.ports(ports, 2));
    }

    @Test
    public void emptyWhenNoPorts() {
        assertEquals("", Formats.ports(null, 5));
        assertTrue(Formats.ports(Collections.<PortResult>emptyList(), 5).isEmpty());
    }
}
