package com.argusforge.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local "My Vault" persistence. Generated artifacts are saved here (tagged by
 * tab) and survive app restarts. Backed by SharedPreferences, consistent with
 * ConfigStore.
 */
public final class VaultStore {

    private static final String PREFS = "argusforge_vault";
    private static final String KEY = "entries";

    private final SharedPreferences prefs;

    public VaultStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<JSONObject> list() {
        List<JSONObject> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e != null) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Persist a new entry and return it (newest-first ordering). */
    public synchronized JSONObject save(String tab, String content) {
        try {
            JSONObject e = new JSONObject();
            e.put("id", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            e.put("tab", tab);
            e.put("content", content == null ? "" : content);
            e.put("title", titleFor(content, tab));
            e.put("ts", System.currentTimeMillis());
            List<JSONObject> all = list();
            all.add(0, e);
            write(all);
            return e;
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    public synchronized void delete(String id) {
        List<JSONObject> keep = new ArrayList<>();
        for (JSONObject e : list()) {
            if (!id.equals(e.optString("id"))) keep.add(e);
        }
        write(keep);
    }

    private void write(List<JSONObject> all) {
        JSONArray arr = new JSONArray();
        for (JSONObject e : all) arr.put(e);
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    /** First non-empty, non-"[M]" line of the content as the entry title. */
    private static String titleFor(String content, String tab) {
        String first = "";
        if (content != null) {
            for (String line : content.split("\\R")) {
                String t = line.trim();
                if (!t.isEmpty() && !"[M]".equals(t)) {
                    first = t;
                    break;
                }
            }
        }
        if (first.isEmpty()) first = "badusb".equals(tab) ? "Bad USB payload" : "Evil portal";
        return first.length() > 64 ? first.substring(0, 64) + "\u2026" : first;
    }
}
