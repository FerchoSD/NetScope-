package com.netscope.lan.test;

import com.netscope.lan.core.net.SubnetMath;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubnetMathTest {
    @Test
    public void ipToLongRoundTrip() {
        assertEquals("192.168.1.10", SubnetMath.longToIp(SubnetMath.ipToLong("192.168.1.10")));
        assertEquals("0.0.0.0", SubnetMath.longToIp(SubnetMath.ipToLong("0.0.0.0")));
        assertEquals("255.255.255.255", SubnetMath.longToIp(SubnetMath.ipToLong("255.255.255.255")));
    }

    @Test
    public void invalidIpReturnsZero() {
        assertEquals(0L, SubnetMath.ipToLong("256.1.1.1"));
        assertEquals(0L, SubnetMath.ipToLong("no-es-ip"));
        assertEquals(0L, SubnetMath.ipToLong("1.2.3"));
        assertEquals(0L, SubnetMath.ipToLong(null));
    }

    @Test
    public void prefixMaskRoundTrip() {
        for (int p = 0; p <= 32; p++) {
            assertEquals(p, SubnetMath.maskToPrefix(SubnetMath.prefixToMask(p)));
        }
        assertEquals("255.255.255.0", SubnetMath.prefixToMask(24));
        assertEquals("255.255.0.0", SubnetMath.prefixToMask(16));
    }

    @Test
    public void networkAndBroadcast() {
        assertEquals("192.168.1.0", SubnetMath.longToIp(SubnetMath.network("192.168.1.77", 24)));
        assertEquals("192.168.1.255", SubnetMath.longToIp(SubnetMath.broadcast("192.168.1.77", 24)));
        assertEquals("10.0.0.0/8", SubnetMath.cidr("10.20.30.40", 8));
    }

    @Test
    public void sameSubnetComparison() {
        assertTrue(SubnetMath.sameSubnet("192.168.1.10", "192.168.1.200", 24));
        assertFalse(SubnetMath.sameSubnet("192.168.1.10", "192.168.2.10", 24));
        assertTrue(SubnetMath.sameSubnet("192.168.1.10", "192.168.2.10", 16));
    }

    @Test
    public void usableHostsExcludesNetworkAndBroadcast() {
        String[] hosts = SubnetMath.usableHosts("192.168.1.10", 24, 256);
        assertEquals(254, hosts.length);
        assertEquals("192.168.1.1", hosts[0]);
        assertEquals("192.168.1.254", hosts[hosts.length - 1]);
    }

    @Test
    public void usableHostsWindowContainsLocalIp() {
        String[] hosts = SubnetMath.usableHosts("10.0.5.200", 16, 16);
        assertEquals(16, hosts.length);
        boolean found = false;
        for (String host : hosts) if ("10.0.5.200".equals(host)) found = true;
        assertTrue(found);
    }

    @Test
    public void pointToPointSubnetKeepsBothAddresses() {
        String[] hosts = SubnetMath.usableHosts("10.0.0.1", 31, 10);
        assertEquals(2, hosts.length);
        assertEquals("10.0.0.0", hosts[0]);
        assertEquals("10.0.0.1", hosts[1]);
    }

    @Test
    public void privateRanges() {
        assertTrue(SubnetMath.isPrivate("10.1.2.3"));
        assertTrue(SubnetMath.isPrivate("172.16.0.1"));
        assertTrue(SubnetMath.isPrivate("192.168.0.1"));
        assertTrue(SubnetMath.isPrivate("169.254.1.1"));
        assertTrue(SubnetMath.isPrivate("127.0.0.1"));
        assertTrue(SubnetMath.isPrivate("100.64.0.1"));
        assertFalse(SubnetMath.isPrivate("8.8.8.8"));
        assertFalse(SubnetMath.isPrivate("172.32.0.1"));
    }
}
