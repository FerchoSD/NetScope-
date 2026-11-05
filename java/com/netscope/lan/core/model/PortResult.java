package com.netscope.lan.core.model;

public final class PortResult {
    public static final int STATE_UNSCANNED = 0;
    public static final int STATE_OPEN = 1;
    public static final int STATE_CLOSED = 2;
    public static final int STATE_FILTERED = 3;
    public static final int STATE_ERROR = 4;

    public final int port;
    public final int state;
    public final String service;
    public final String banner;
    public final long rttMs;
    public final int risk;
    public final String riskNote;

    public PortResult(int port, int state, String service, String banner,
                      long rttMs, int risk, String riskNote) {
        this.port = port;
        this.state = state;
        this.service = service == null ? "" : service;
        this.banner = banner == null ? "" : banner;
        this.rttMs = rttMs;
        this.risk = risk;
        this.riskNote = riskNote == null ? "" : riskNote;
    }

    public boolean isOpen() {
        return state == STATE_OPEN;
    }

    public static String stateLabel(int state) {
        switch (state) {
            case STATE_OPEN: return "Abierto";
            case STATE_CLOSED: return "Cerrado";
            case STATE_FILTERED: return "Filtrado";
            case STATE_ERROR: return "Error";
            default: return "Sin analizar";
        }
    }
}
