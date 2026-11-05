package com.netscope.lan.core.net;

public final class SubnetMath {
    private SubnetMath() {}

    public static long ipToLong(String ip) {
        if (ip == null) return 0L;
        String[] p = ip.trim().split("\\.");
        if (p.length != 4) return 0L;
        long v = 0L;
        for (int i = 0; i < 4; i++) {
            int oct;
            try { oct = Integer.parseInt(p[i]); } catch (NumberFormatException e) { return 0L; }
            if (oct < 0 || oct > 255) return 0L;
            v = (v << 8) | oct;
        }
        return v & 0xFFFFFFFFL;
    }

    public static String longToIp(long v) {
        return ((v >>> 24) & 0xFF) + "." + ((v >>> 16) & 0xFF) + "." + ((v >>> 8) & 0xFF) + "." + (v & 0xFF);
    }

    public static long mask(int prefix) {
        if (prefix <= 0) return 0L;
        if (prefix >= 32) return 0xFFFFFFFFL;
        return (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
    }

    public static String prefixToMask(int prefix) {
        return longToIp(mask(prefix));
    }

    public static int maskToPrefix(String netmask) {
        long m = ipToLong(netmask);
        int n = 0;
        for (int i = 31; i >= 0; i--) {
            if ((m & (1L << i)) != 0) n++;
            else break;
        }
        return n;
    }

    public static long network(String ip, int prefix) {
        return ipToLong(ip) & mask(prefix);
    }

    public static long broadcast(String ip, int prefix) {
        long net = network(ip, prefix);
        return (net | (~mask(prefix) & 0xFFFFFFFFL)) & 0xFFFFFFFFL;
    }

    public static String cidr(String ip, int prefix) {
        return longToIp(network(ip, prefix)) + "/" + prefix;
    }

    public static boolean sameSubnet(String a, String b, int prefix) {
        return network(a, prefix) == network(b, prefix);
    }

    public static String[] usableHosts(String ip, int prefix, int maxHosts) {
        if (maxHosts < 1) maxHosts = 1;
        long net = network(ip, prefix);
        long bcast = broadcast(ip, prefix);
        long first, last;
        if (prefix >= 31) {
            first = net;
            last = bcast;
        } else {
            first = net + 1;
            last = bcast - 1;
        }
        if (last < first) return new String[0];
        long count = last - first + 1;
        int limit = (int) Math.min(count, (long) maxHosts);
        long start = first;
        if (count > limit) {
            long local = ipToLong(ip);
            start = local - (limit / 2);
            if (start < first) start = first;
            if (start + limit - 1 > last) start = last - limit + 1;
        }
        String[] out = new String[limit];
        for (int i = 0; i < limit; i++) out[i] = longToIp(start + i);
        return out;
    }

    public static boolean isPrivate(String ip) {
        long v = ipToLong(ip);
        return (v >= ipToLong("10.0.0.0") && v <= ipToLong("10.255.255.255"))
                || (v >= ipToLong("172.16.0.0") && v <= ipToLong("172.31.255.255"))
                || (v >= ipToLong("192.168.0.0") && v <= ipToLong("192.168.255.255"))
                || (v >= ipToLong("169.254.0.0") && v <= ipToLong("169.254.255.255"))
                || (v >= ipToLong("127.0.0.0") && v <= ipToLong("127.255.255.255"))
                || (v >= ipToLong("100.64.0.0") && v <= ipToLong("100.127.255.255"));
    }
}
