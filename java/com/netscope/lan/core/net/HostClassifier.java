package com.netscope.lan.core.net;

import com.netscope.lan.core.model.Device;

import java.util.Locale;

public final class HostClassifier {
    private HostClassifier() {}

    public static void classify(Device d) {
        String hint = ((d.hostname == null ? "" : d.hostname) + " "
                + (d.vendor == null ? "" : d.vendor)).toLowerCase(Locale.ROOT);

        if (d.gateway || hint.contains("internetgateway") || hint.contains("router")
                || hint.contains("gateway") || hint.contains("punto de acceso")
                || (d.hasOpenPort(53) && (d.hasOpenPort(80) || d.hasOpenPort(443)))) {
            set(d, "router", "Router / gateway");
        } else if (d.hasOpenPort(631) || d.hasOpenPort(9100) || d.hasOpenPort(515)
                || hint.contains("printer") || hint.contains("impresora") || hint.contains("hp ")
                || hint.contains("epson") || hint.contains("canon") || hint.contains("brother")) {
            set(d, "printer", "Impresora");
        } else if (d.hasOpenPort(554) || d.hasOpenPort(8554)
                || hint.contains("camera") || hint.contains("camara") || hint.contains("cámara")
                || hint.contains("hikvision") || hint.contains("dahua")) {
            set(d, "camera", "Cámara IP");
        } else if (d.hasOpenPort(8009) || d.hasOpenPort(32400) || hint.contains("mediarenderer")
                || hint.contains("mediaserver") || hint.contains("chromecast") || hint.contains("roku")
                || hint.contains("smarttv") || hint.contains("smart tv") || hint.contains("airplay")) {
            set(d, "tv", "TV / media");
        } else if (d.hasOpenPort(139) || d.hasOpenPort(445) || d.hasOpenPort(3389)) {
            set(d, "pc", "Ordenador");
        } else if (d.hasOpenPort(548) || d.hasOpenPort(2049) || d.hasOpenPort(5000)
                || hint.contains("nas") || hint.contains("synology") || hint.contains("qnap")) {
            set(d, "nas", "NAS / almacenamiento");
        } else if (d.hasOpenPort(62078) || hint.contains("iphone") || hint.contains("android")
                || hint.contains("phone") || d.self) {
            set(d, "phone", d.self ? "Este dispositivo" : "Móvil");
        } else if (d.hasOpenPort(22) || d.hasOpenPort(80) || d.hasOpenPort(443)
                || d.hasOpenPort(8080)) {
            set(d, "server", "Servidor");
        } else {
            set(d, "unknown", "Dispositivo");
        }
    }

    private static void set(Device d, String type, String label) {
        d.type = type;
        d.typeLabel = label;
    }

    public static String iconLetter(String type) {
        if ("router".equals(type)) return "R";
        if ("printer".equals(type)) return "P";
        if ("camera".equals(type)) return "C";
        if ("tv".equals(type)) return "TV";
        if ("pc".equals(type)) return "PC";
        if ("nas".equals(type)) return "N";
        if ("phone".equals(type)) return "M";
        if ("server".equals(type)) return "S";
        return "?";
    }
}
