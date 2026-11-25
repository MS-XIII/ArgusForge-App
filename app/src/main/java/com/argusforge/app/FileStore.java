package com.argusforge.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Saves generated artifacts / downloaded payloads to the public Downloads
 * folder (MediaStore on API 29+, app-private external storage as fallback).
 */
public final class FileStore {

    public static String saveToDownloads(Context ctx, String displayName,
                                         String relDir, byte[] data) {
        String dir = sanitize(relDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime(displayName));
                String rel = Environment.DIRECTORY_DOWNLOADS;
                if (!dir.isEmpty()) rel += "/" + dir;
                cv.put(MediaStore.Downloads.RELATIVE_PATH, rel);
                Uri uri = ctx.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new RuntimeException("null stream");
                        os.write(data);
                    }
                    return "Downloads/" + (dir.isEmpty() ? "" : dir + "/") + displayName;
                }
            } catch (Exception ignored) {
                // fall through to app-private storage
            }
        }
        File base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (base == null) base = ctx.getFilesDir();
        File f = new File(base, dir.isEmpty() ? displayName : dir + File.separator + displayName);
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(data);
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("save failed: " + e.getMessage());
        }
    }

    private static String sanitize(String rel) {
        if (rel == null) return "";
        return rel.replace("\\", "/")
                .replaceAll("\\.\\.+", "")
                .replaceAll("^/+", "")
                .replaceAll("/{2,}", "/")
                .replaceAll("[^a-zA-Z0-9_./-]", "_");
    }

    private static String mime(String name) {
        String f = name.toLowerCase();
        if (f.endsWith(".html") || f.endsWith(".htm")) return "text/html";
        if (f.endsWith(".txt")) return "text/plain";
        if (f.endsWith(".js")) return "application/javascript";
        if (f.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
