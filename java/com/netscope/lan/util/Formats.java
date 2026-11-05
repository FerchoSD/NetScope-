package com.netscope.lan.util;

import java.text.SimpleDateFormat;
import java.util.Date;
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
}
