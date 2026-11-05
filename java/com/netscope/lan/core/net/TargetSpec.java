package com.netscope.lan.core.net;

import java.util.Locale;

/**
 * Interpreta el objetivo del laboratorio: host o IP, puerto opcional y TLS.
 * Acepta entradas como "ejemplo.com", "https://ejemplo.com/ruta",
 * "192.168.1.1:8080" o "[2001:db8::1]:443".
 */
public final class TargetSpec {
    public final String host;
    public final int port;
    public final boolean tls;

    private TargetSpec(String host, int port, boolean tls) {
        this.host = host;
        this.port = port;
        this.tls = tls;
    }

    public boolean hasPort() {
        return port > 0;
    }

    public static TargetSpec parse(String rawHost, String rawPort, boolean tlsSwitch) {
        String text = rawHost == null ? "" : rawHost.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("host vacío");

        boolean schemeTls = false;
        int scheme = text.indexOf("://");
        if (scheme >= 0) {
            String name = text.substring(0, scheme).trim().toLowerCase(Locale.ROOT);
            schemeTls = "https".equals(name) || "wss".equals(name);
            text = text.substring(scheme + 3);
        }
        int end = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        if (end >= 0) text = text.substring(0, end);
        int at = text.lastIndexOf('@');
        if (at >= 0) text = text.substring(at + 1);

        int embeddedPort = -1;
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close < 0) throw new IllegalArgumentException("IPv6 sin cierre");
            String after = text.substring(close + 1).trim();
            if (after.startsWith(":")) embeddedPort = parsePort(after.substring(1));
            else if (!after.isEmpty()) throw new IllegalArgumentException("sufijo no válido");
            text = text.substring(1, close).trim();
        } else {
            int first = text.indexOf(':');
            int last = text.lastIndexOf(':');
            if (first >= 0 && first == last) {
                embeddedPort = parsePort(text.substring(first + 1));
                text = text.substring(0, first).trim();
            }
        }

        int fieldPort = rawPort == null || rawPort.trim().isEmpty() ? -1 : parsePort(rawPort.trim());
        int port = fieldPort > 0 ? fieldPort : embeddedPort;
        if (!isValidHost(text)) throw new IllegalArgumentException("host no válido");

        boolean tls = tlsSwitch || schemeTls;
        return new TargetSpec(text, port, tls);
    }

    public static boolean isValidHost(String host) {
        if (host == null) return false;
        String value = host.trim();
        if (value.isEmpty() || value.length() > 253) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '/' || c == '\\' || c == '@') return false;
        }
        if (value.indexOf(':') >= 0) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                        || (c >= 'A' && c <= 'F') || c == ':' || c == '.';
                if (!ok) return false;
            }
            return value.indexOf(':') != value.length() - 1 || value.endsWith("::");
        }
        if (value.startsWith(".") || value.endsWith(".") || value.startsWith("-") || value.endsWith("-")) {
            return false;
        }
        boolean dot = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
            if (!ok) return false;
            if (c == '.') {
                if (dot) return false;
                dot = true;
            } else {
                dot = false;
            }
        }
        return true;
    }

    public static int parsePort(String text) {
        if (text == null) throw new IllegalArgumentException("puerto vacío");
        String value = text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("puerto vacío");
        if (value.length() > 5) throw new IllegalArgumentException("puerto no válido");
        int port = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') throw new IllegalArgumentException("puerto no válido");
            port = port * 10 + (c - '0');
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("puerto fuera de rango");
        return port;
    }
}
