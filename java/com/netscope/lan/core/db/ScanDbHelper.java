package com.netscope.lan.core.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class ScanDbHelper extends SQLiteOpenHelper {
    private static final String NAME = "netscope.db";
    private static final int VERSION = 2;

    public ScanDbHelper(Context context) {
        super(context, NAME, null, VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE scans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "ssid TEXT, subnet TEXT, gateway TEXT, local_ip TEXT, iface TEXT, dns TEXT," +
                "prefix INTEGER, device_count INTEGER, open_ports INTEGER, duration_ms INTEGER)");
        db.execSQL("CREATE TABLE devices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "scan_id INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE," +
                "ip TEXT NOT NULL, hostname TEXT, vendor TEXT, type TEXT, type_label TEXT," +
                "latency_ms INTEGER, gateway INTEGER, self INTEGER, open_count INTEGER," +
                "os_guess TEXT, server TEXT, http_title TEXT, tls TEXT, services TEXT)");
        db.execSQL("CREATE INDEX idx_devices_scan ON devices(scan_id)");
        db.execSQL("CREATE TABLE ports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE," +
                "port INTEGER, state TEXT, service TEXT, banner TEXT, risk INTEGER)");
        db.execSQL("CREATE INDEX idx_ports_device ON ports(device_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE devices ADD COLUMN os_guess TEXT");
            db.execSQL("ALTER TABLE devices ADD COLUMN server TEXT");
            db.execSQL("ALTER TABLE devices ADD COLUMN http_title TEXT");
            db.execSQL("ALTER TABLE devices ADD COLUMN tls TEXT");
            db.execSQL("ALTER TABLE devices ADD COLUMN services TEXT");
        }
    }
}
