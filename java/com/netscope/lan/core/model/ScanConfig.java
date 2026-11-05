package com.netscope.lan.core.model;

import com.netscope.lan.core.net.ServiceCatalog;

public final class ScanConfig {
    public int timeoutMs = 400;
    public int concurrency = 64;
    public boolean mdns = true;
    public boolean ssdp = true;
    public boolean netbios = true;
    public boolean reverseDns = true;
    public int maxHosts = 512;
    public int[] livenessPorts = ServiceCatalog.quickPorts();
}
