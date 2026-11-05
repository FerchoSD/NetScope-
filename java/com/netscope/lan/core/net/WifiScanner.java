package com.netscope.lan.core.net;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WifiScanner {
    public static final class Network {
        public String ssid = "";
        public String bssid = "";
        public int frequency;
        public int channel;
        public String band = "";
        public String security = "";
        public int signalPercent;
        public boolean hidden;
        public boolean connected;
    }

    private WifiScanner() {}

    public static WifiManager manager(Context context) {
        return (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
    }

    public static boolean isWifiEnabled(Context context) {
        try {
            WifiManager wm = manager(context);
            return wm != null && wm.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean requestScan(Context context) {
        try {
            WifiManager wm = manager(context);
            return wm != null && wm.startScan();
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Network> results(Context context) {
        List<Network> networks = new ArrayList<>();
        try {
            WifiManager wm = manager(context);
            if (wm == null) return networks;
            List<ScanResult> raw = wm.getScanResults();
            if (raw == null) return networks;
            for (ScanResult result : raw) {
                if (result == null) continue;
                Network network = fromResult(result);
                if (network != null) networks.add(network);
            }
        } catch (SecurityException ignored) {
            // Sin ubicación/NEARBY_WIFI_DEVICES no se pueden leer resultados.
        } catch (Exception ignored) {
        }
        Collections.sort(networks, new Comparator<Network>() {
            @Override public int compare(Network a, Network b) {
                return Integer.compare(b.signalPercent, a.signalPercent);
            }
        });
        return networks;
    }

    public static Network connected(Context context) {
        try {
            WifiManager wm = manager(context);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) return null;
            Network network = new Network();
            String ssid = info.getSSID();
            if (ssid != null) ssid = ssid.replace("\"", "");
            network.ssid = ssid == null || ssid.contains("<unknown") ? "" : ssid;
            network.bssid = info.getBSSID() == null ? "" : info.getBSSID();
            network.frequency = info.getFrequency();
            network.channel = channelForFrequency(network.frequency);
            network.band = bandForFrequency(network.frequency);
            network.signalPercent = signalPercent(info.getRssi());
            network.connected = true;
            for (ScanResult result : wm.getScanResults()) {
                if (result == null || result.BSSID == null) continue;
                if (result.BSSID.equalsIgnoreCase(network.bssid)) {
                    if (network.ssid.isEmpty()) network.ssid = result.SSID == null ? "" : result.SSID;
                    network.frequency = result.frequency;
                    network.channel = channelForFrequency(result.frequency);
                    network.band = bandForFrequency(result.frequency);
                    network.security = securityLabel(result.capabilities);
                    network.signalPercent = signalPercent(result.level);
                    break;
                }
            }
            if (network.security.isEmpty()) network.security = "Desconocida";
            return network;
        } catch (Exception e) {
            return null;
        }
    }

    private static Network fromResult(ScanResult result) {
        Network network = new Network();
        network.ssid = result.SSID == null ? "" : result.SSID.trim();
        network.hidden = network.ssid.isEmpty();
        if (network.hidden) network.ssid = "Red oculta";
        network.bssid = result.BSSID == null ? "" : result.BSSID;
        network.frequency = result.frequency;
        network.channel = channelForFrequency(result.frequency);
        network.band = bandForFrequency(result.frequency);
        network.security = securityLabel(result.capabilities);
        network.signalPercent = signalPercent(result.level);
        return network;
    }

    public static int channelForFrequency(int frequency) {
        if (frequency == 2484) return 14;
        if (frequency >= 2412 && frequency <= 2472) return (frequency - 2407) / 5;
        if (frequency >= 5000 && frequency <= 5925) return (frequency - 5000) / 5;
        if (frequency >= 5955 && frequency <= 7115) return (frequency - 5950) / 5;
        return -1;
    }

    public static String bandForFrequency(int frequency) {
        if (frequency <= 0) return "";
        if (frequency < 3000) return "2,4 GHz";
        if (frequency < 5925) return "5 GHz";
        return "6 GHz";
    }

    public static String securityLabel(String capabilities) {
        String caps = capabilities == null ? "" : capabilities.toUpperCase(Locale.ROOT);
        boolean wpa3 = caps.contains("SAE") || caps.contains("WPA3");
        boolean wpa2 = caps.contains("WPA2") || caps.contains("RSN");
        boolean wpa = caps.contains("WPA");
        boolean wep = caps.contains("WEP");
        boolean enterprise = caps.contains("EAP") || caps.contains("802.1X");
        String base;
        if (wpa3) base = "WPA3";
        else if (wpa2) base = "WPA2";
        else if (wpa) base = "WPA";
        else if (wep) base = "WEP";
        else base = "Abierta";
        if (enterprise && !"Abierta".equals(base)) return base + " Enterprise";
        return base;
    }

    public static int signalPercent(int rssi) {
        if (rssi <= -90) return 0;
        if (rssi >= -30) return 100;
        return (rssi + 90) * 100 / 60;
    }

    public static int signalBars(int percent) {
        if (percent <= 0) return 0;
        if (percent >= 75) return 4;
        if (percent >= 50) return 3;
        if (percent >= 25) return 2;
        return 1;
    }

    public static String signalGlyph(int percent) {
        int bars = signalBars(percent);
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(i < bars ? '█' : '░');
        return sb.toString();
    }
}
