package com.netscope.lan.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.netscope.lan.core.model.ScanConfig;

public final class Prefs {
    private static final String NAME = "netscope";
    private static final String LEGAL = "legal_accepted";
    private static final String TIMEOUT = "timeout_ms";
    private static final String CONCURRENCY = "concurrency";
    private static final String MDNS = "mdns";
    private static final String SSDP = "ssdp";
    private static final String NETBIOS = "netbios";
    private static final String REVERSE_DNS = "reverse_dns";
    private static final String DEV_MODE = "dev_mode";
    private static final String MAX_HOSTS = "max_hosts";

    private Prefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static boolean isLegalAccepted(Context context) {
        return prefs(context).getBoolean(LEGAL, false);
    }

    public static void setLegalAccepted(Context context) {
        prefs(context).edit().putBoolean(LEGAL, true).apply();
    }

    public static int getTimeoutMs(Context context) {
        return prefs(context).getInt(TIMEOUT, 400);
    }

    public static void setTimeoutMs(Context context, int value) {
        prefs(context).edit().putInt(TIMEOUT, value).apply();
    }

    public static int getConcurrency(Context context) {
        return prefs(context).getInt(CONCURRENCY, 64);
    }

    public static void setConcurrency(Context context, int value) {
        prefs(context).edit().putInt(CONCURRENCY, value).apply();
    }

    public static boolean isMdns(Context context) {
        return prefs(context).getBoolean(MDNS, true);
    }

    public static void setMdns(Context context, boolean value) {
        prefs(context).edit().putBoolean(MDNS, value).apply();
    }

    public static boolean isSsdp(Context context) {
        return prefs(context).getBoolean(SSDP, true);
    }

    public static void setSsdp(Context context, boolean value) {
        prefs(context).edit().putBoolean(SSDP, value).apply();
    }

    public static boolean isNetbios(Context context) {
        return prefs(context).getBoolean(NETBIOS, true);
    }

    public static void setNetbios(Context context, boolean value) {
        prefs(context).edit().putBoolean(NETBIOS, value).apply();
    }

    public static boolean isReverseDns(Context context) {
        return prefs(context).getBoolean(REVERSE_DNS, true);
    }

    public static void setReverseDns(Context context, boolean value) {
        prefs(context).edit().putBoolean(REVERSE_DNS, value).apply();
    }

    public static boolean isDevMode(Context context) {
        return prefs(context).getBoolean(DEV_MODE, false);
    }

    public static void setDevMode(Context context, boolean value) {
        prefs(context).edit().putBoolean(DEV_MODE, value).apply();
    }

    public static int getMaxHosts(Context context) {
        return prefs(context).getInt(MAX_HOSTS, 512);
    }

    public static void setMaxHosts(Context context, int value) {
        prefs(context).edit().putInt(MAX_HOSTS, value).apply();
    }

    public static ScanConfig scanConfig(Context context) {
        ScanConfig config = new ScanConfig();
        config.timeoutMs = getTimeoutMs(context);
        config.concurrency = getConcurrency(context);
        config.maxHosts = getMaxHosts(context);
        config.mdns = isMdns(context);
        config.ssdp = isSsdp(context);
        config.netbios = isNetbios(context);
        config.reverseDns = isReverseDns(context);
        return config;
    }
}
