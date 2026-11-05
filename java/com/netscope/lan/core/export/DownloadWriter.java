package com.netscope.lan.core.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class DownloadWriter {
    public static final class Saved {
        public final Uri uri;
        public final String path;
        public Saved(Uri uri, String path) { this.uri = uri; this.path = path; }
    }

    private DownloadWriter() {}

    public static Saved save(Context context, String content, String fileName, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NetScope");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("No se pudo crear el archivo");
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) throw new IllegalStateException("No se pudo abrir el archivo");
            try {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                out.close();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return new Saved(uri, "Download/NetScope/" + fileName);
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "NetScope");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear la carpeta");
        File file = new File(dir, fileName);
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } finally {
            out.close();
        }
        return new Saved(null, file.getAbsolutePath());
    }

    public static void share(Context context, Saved saved, String mime, String title) {
        if (saved == null || saved.uri == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, saved.uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, title));
    }
}
