package com.netscope.lan.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.netscope.lan.R;
import com.netscope.lan.ui.MainActivity;

public final class ScanService extends Service {
    private static final String CHANNEL_ID = "netscope_scan";
    private static final int NOTIFICATION_ID = 4210;
    private static final String ACTION_START = "com.netscope.lan.SCAN_START";
    private static final String EXTRA_TEXT = "text";

    public static void start(Context context, String text) {
        Intent intent = new Intent(context, ScanService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TEXT, text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ScanService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Escaneos de red",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progreso de los escaneos de NetScope");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent == null ? "Escaneando…" : intent.getStringExtra(EXTRA_TEXT);
        if (text == null) text = "Escaneando…";
        startForeground(NOTIFICATION_ID, build(text));
        return START_NOT_STICKY;
    }

    private Notification build(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("NetScope")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_scan)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
