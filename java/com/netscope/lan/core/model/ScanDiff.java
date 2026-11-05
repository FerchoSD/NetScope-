package com.netscope.lan.core.model;

import java.util.ArrayList;
import java.util.List;

public final class ScanDiff {
    public final List<String> addedIps = new ArrayList<>();
    public final List<String> removedIps = new ArrayList<>();
    public final List<PortChange> portChanges = new ArrayList<>();

    public static final class PortChange {
        public final String ip;
        public final List<Integer> opened = new ArrayList<>();
        public final List<Integer> closed = new ArrayList<>();
        public PortChange(String ip) { this.ip = ip; }
        public boolean isEmpty() { return opened.isEmpty() && closed.isEmpty(); }
    }

    public boolean isEmpty() {
        return addedIps.isEmpty() && removedIps.isEmpty() && portChanges.isEmpty();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        if (!addedIps.isEmpty()) sb.append("Nuevos: ").append(join(addedIps)).append('\n');
        if (!removedIps.isEmpty()) sb.append("Desaparecidos: ").append(join(removedIps)).append('\n');
        for (PortChange c : portChanges) {
            if (c.isEmpty()) continue;
            sb.append(c.ip).append(": ");
            if (!c.opened.isEmpty()) sb.append("abre ").append(c.opened).append("  ");
            if (!c.closed.isEmpty()) sb.append("cierra ").append(c.closed);
            sb.append('\n');
        }
        if (sb.length() == 0) sb.append("Sin cambios detectados.");
        return sb.toString().trim();
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
