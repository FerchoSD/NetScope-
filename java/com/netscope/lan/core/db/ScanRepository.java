package com.netscope.lan.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanReport;

import java.util.ArrayList;
import java.util.List;

public final class ScanRepository {
    public static final class ScanSummary {
        public long id;
        public long timestamp;
        public String ssid = "";
        public String subnet = "";
        public int deviceCount;
        public int openPorts;
        public long durationMs;
    }

    private final ScanDbHelper helper;

    public ScanRepository(Context context) {
        helper = new ScanDbHelper(context.getApplicationContext());
    }

    public long saveReport(ScanReport report) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues scan = new ContentValues();
            scan.put("ts", report.timestamp);
            scan.put("ssid", report.ssid);
            scan.put("subnet", report.subnet);
            scan.put("gateway", report.gateway);
            scan.put("local_ip", report.localIp);
            scan.put("iface", report.iface);
            scan.put("dns", report.dns);
            scan.put("prefix", report.prefix);
            scan.put("device_count", report.devices.size());
            scan.put("open_ports", report.totalOpenPorts());
            scan.put("duration_ms", report.durationMs);
            long scanId = db.insertOrThrow("scans", null, scan);
            report.id = scanId;

            for (Device device : report.devices) {
                ContentValues values = new ContentValues();
                values.put("scan_id", scanId);
                values.put("ip", device.ip);
                values.put("hostname", device.hostname);
                values.put("vendor", device.vendor);
                values.put("type", device.type);
                values.put("type_label", device.typeLabel);
                values.put("latency_ms", device.latencyMs);
                values.put("gateway", device.gateway ? 1 : 0);
                values.put("self", device.self ? 1 : 0);
                values.put("open_count", device.openPortCount());
                long deviceId = db.insertOrThrow("devices", null, values);
                for (PortResult port : device.openPorts) {
                    ContentValues p = new ContentValues();
                    p.put("device_id", deviceId);
                    p.put("port", port.port);
                    p.put("state", "open");
                    p.put("service", port.service);
                    p.put("banner", port.banner);
                    p.put("risk", port.risk);
                    db.insertOrThrow("ports", null, p);
                }
            }
            db.execSQL("DELETE FROM scans WHERE id NOT IN (SELECT id FROM scans ORDER BY ts DESC LIMIT 20)");
            db.setTransactionSuccessful();
            return scanId;
        } finally {
            db.endTransaction();
        }
    }

    public List<ScanSummary> listScans() {
        List<ScanSummary> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query("scans", null, null, null, null, null, "ts DESC");
        try {
            while (cursor.moveToNext()) {
                ScanSummary summary = new ScanSummary();
                summary.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                summary.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("ts"));
                summary.ssid = safe(cursor, "ssid");
                summary.subnet = safe(cursor, "subnet");
                summary.deviceCount = cursor.getInt(cursor.getColumnIndexOrThrow("device_count"));
                summary.openPorts = cursor.getInt(cursor.getColumnIndexOrThrow("open_ports"));
                summary.durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms"));
                result.add(summary);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public ScanReport loadReport(long scanId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query("scans", null, "id=?", new String[]{String.valueOf(scanId)},
                null, null, null);
        ScanReport report = null;
        try {
            if (cursor.moveToFirst()) {
                report = new ScanReport();
                report.id = scanId;
                report.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("ts"));
                report.ssid = safe(cursor, "ssid");
                report.subnet = safe(cursor, "subnet");
                report.gateway = safe(cursor, "gateway");
                report.localIp = safe(cursor, "local_ip");
                report.iface = safe(cursor, "iface");
                report.dns = safe(cursor, "dns");
                report.prefix = cursor.getInt(cursor.getColumnIndexOrThrow("prefix"));
                report.durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("duration_ms"));
            }
        } finally {
            cursor.close();
        }
        if (report == null) return null;

        Cursor devices = db.query("devices", null, "scan_id=?", new String[]{String.valueOf(scanId)},
                null, null, "id ASC");
        try {
            while (devices.moveToNext()) {
                long deviceId = devices.getLong(devices.getColumnIndexOrThrow("id"));
                Device device = new Device(safe(devices, "ip"));
                device.hostname = safe(devices, "hostname");
                device.vendor = safe(devices, "vendor");
                device.type = safe(devices, "type");
                device.typeLabel = safe(devices, "type_label");
                device.latencyMs = devices.getLong(devices.getColumnIndexOrThrow("latency_ms"));
                device.gateway = devices.getInt(devices.getColumnIndexOrThrow("gateway")) == 1;
                device.self = devices.getInt(devices.getColumnIndexOrThrow("self")) == 1;
                Cursor ports = db.query("ports", null, "device_id=?",
                        new String[]{String.valueOf(deviceId)}, null, null, "port ASC");
                try {
                    while (ports.moveToNext()) {
                        int port = ports.getInt(ports.getColumnIndexOrThrow("port"));
                        String service = safe(ports, "service");
                        String banner = safe(ports, "banner");
                        int risk = ports.getInt(ports.getColumnIndexOrThrow("risk"));
                        device.addPortResult(new PortResult(port, PortResult.STATE_OPEN, service,
                                banner, -1, risk, com.netscope.lan.core.net.ServiceCatalog.riskNote(port)));
                    }
                } finally {
                    ports.close();
                }
                report.devices.add(device);
            }
        } finally {
            devices.close();
        }
        report.sortDevices();
        return report;
    }

    public ScanReport latestReport() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM scans ORDER BY ts DESC LIMIT 1", null);
        try {
            if (cursor.moveToFirst()) return loadReport(cursor.getLong(0));
        } finally {
            cursor.close();
        }
        return null;
    }

    public ScanReport previousReport(ScanReport current) {
        if (current == null) return null;
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM scans WHERE ts < ? ORDER BY ts DESC LIMIT 1",
                new String[]{String.valueOf(current.timestamp)});
        try {
            if (cursor.moveToFirst()) return loadReport(cursor.getLong(0));
        } finally {
            cursor.close();
        }
        return null;
    }

    public void clear() {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("DELETE FROM ports");
        db.execSQL("DELETE FROM devices");
        db.execSQL("DELETE FROM scans");
    }

    private static String safe(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return "";
        return cursor.getString(index);
    }
}
