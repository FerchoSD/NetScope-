package com.netscope.lan.core.net;

import java.util.Locale;

public final class DistanceEstimator {
    private DistanceEstimator() {}

    // Modelo log-distancia (RSSI a 1 m y exponente de pérdida por banda).
    public static double rssiToMeters(int rssi, int frequencyMhz) {
        if (rssi <= -100 || rssi >= 0) return -1;
        double reference;
        double exponent;
        if (frequencyMhz >= 5925) {
            reference = -47.0;
            exponent = 3.2;
        } else if (frequencyMhz >= 3000) {
            reference = -46.0;
            exponent = 3.0;
        } else {
            reference = -40.0;
            exponent = 2.7;
        }
        double meters = Math.pow(10.0, (reference - rssi) / (10.0 * exponent));
        if (Double.isNaN(meters) || Double.isInfinite(meters)) return -1;
        return meters;
    }

    public static String distanceLabel(int rssi, int frequencyMhz) {
        double meters = rssiToMeters(rssi, frequencyMhz);
        if (meters < 0) return "—";
        if (meters > 200) return "> 200 m (estimado)";
        return formatMeters(meters) + " (estimado)";
    }

    public static String formatMeters(double meters) {
        Locale locale = Locale.forLanguageTag("es-ES");
        if (meters < 1) return String.format(locale, "≈ %.1f m", meters);
        if (meters < 10) return String.format(locale, "≈ %.1f m", meters);
        return String.format(locale, "≈ %.0f m", meters);
    }

    // El RTT no da metros fiables en Wi-Fi; se usa como indicador de proximidad.
    public static String proximityLabel(long rttMs) {
        if (rttMs < 0) return "—";
        if (rttMs <= 2) return "Muy cerca (estimado)";
        if (rttMs <= 10) return "Cerca en la LAN (estimado)";
        if (rttMs <= 40) return "Distancia media (estimado)";
        return "Lejos o enlace ocupado (estimado)";
    }
}
