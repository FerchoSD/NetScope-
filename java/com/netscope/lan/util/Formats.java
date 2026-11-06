package com.netscope.lan.util;

import com.netscope.lan.core.model.PortResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class Formats {
    private Formats() {}

    public static String time(long timestamp) {
        return new SimpleDateFormat("dd MMM · HH:mm:ss", new Locale("es", "ES"))
                .format(new Date(timestamp));
    }

    public static String shortTime(long timestamp) {
        return new SimpleDateFormat("dd MMM, HH:mm", new Locale("es", "ES"))
                .format(new Date(timestamp));
    }

    public static String duration(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60000) return String.format(new Locale("es", "ES"), "%.1f s", ms / 1000.0);
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        return minutes + " min " + seconds + " s";
    }

    public static String latency(long ms) {
        if (ms < 0) return "—";
        return ms + " ms";
    }

    public static String fileTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(timestamp));
    }

    public static String ports(List<PortResult> ports, int max) {
        if (ports == null || ports.isEmpty()) return "";
        List<PortResult> sorted = new ArrayList<>(ports);
        Collections.sort(sorted, new Comparator<PortResult>() {
            @Override public int compare(PortResult a, PortResult b) {
                return Integer.compare(a.port, b.port);
            }
        });
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (PortResult result : sorted) {
            if (shown >= max) {
                sb.append(" · +").append(sorted.size() - shown);
                break;
            }
            if (sb.length() > 0) sb.append(" · ");
            sb.append(result.port);
            if (!result.service.isEmpty()) sb.append(' ').append(result.service);
            shown++;
        }
        return sb.toString();
    }
}
