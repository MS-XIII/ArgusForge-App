package com.argusforge.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Persistent settings. Bruce-only. Backed by SharedPreferences.
 */
public final class ConfigStore {

    private static final String PREFS = "argusforge";
    private static final String KEY_CONFIG = "config";

    private final SharedPreferences prefs;

    public ConfigStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Load config, merging over defaults so a partial file never crashes. */
    public JSONObject load() {
        JSONObject merged = defaults();
        String raw = prefs.getString(KEY_CONFIG, null);
        if (raw == null) return merged;
        try {
            JSONObject stored = new JSONObject(raw);
            for (Iterator<String> it = stored.keys(); it.hasNext(); ) {
                String k = it.next();
                merged.put(k, stored.opt(k));
            }
        } catch (Exception ignored) {
            // corrupt -> defaults
        }
        return merged;
    }

    public void save(JSONObject cfg) {
        prefs.edit().putString(KEY_CONFIG, cfg.toString()).apply();
    }

    /** Defaults (Bruce-only). */
    public static JSONObject defaults() {
        JSONObject d = new JSONObject();
        try {
            d.put("api_key", "");
            d.put("base_url", "https://api.openai.com/v1");
            d.put("model", "gpt-4o-mini");
            d.put("github_token", "");
            d.put("bruce_host", "http://bruce.local");
            d.put("bruce_user", "admin");
            d.put("bruce_pass", "bruce");
        } catch (Exception ignored) {
        }
        return d;
    }
}
