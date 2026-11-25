package com.argusforge.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JavaScript bridge exposed to the WebView UI as `window.Argus`.
 * Bruce-only. Every async call is dispatched to a
 * background executor and reported back through
 * `window.ArgusBridge.resolve(cbId, {ok, data|error})`.
 */
public final class Bridge {

    private static final String[] PORTAL_REPOS = {
            "bigbrodude6119/flipper-zero-evil-portal",
            "FlippieHacks/FlipperZeroEuropeanPortals",
            "RogueMaster/flipperzero-evil-portal",
            "L-ubu/flipper-portals",
    };

    private static final String[] BADUSB_REPOS = {
            "hak5/usbrubberducky-payloads",
            "I-Am-Jakoby/Flipper-Zero-BadUSB",
            "FalsePhilosopher/badusb",
    };

    private final Context ctx;
    private final WebView webView;
    private final ConfigStore store;
    private final VaultStore vault;
    private final ApiClient api;
    private final String systemPrompt;
    private final Map<String, JSONObject> storeItems = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());

    public Bridge(Context ctx, WebView webView, String systemPrompt) {
        this.ctx = ctx;
        this.webView = webView;
        this.store = new ConfigStore(ctx);
        this.vault = new VaultStore(ctx);
        this.api = new ApiClient();
        this.systemPrompt = systemPrompt;
    }

    // ---- sync surface ------------------------------------------------------

    @JavascriptInterface
    public String getConfig() {
        return store.load().toString();
    }

    @JavascriptInterface
    public void setConfig(String json) {
        try {
            store.save(new JSONObject(json));
        } catch (Exception ignored) {
        }
    }

    @JavascriptInterface
    public String deviceInfo() {
        try {
            return new JSONObject()
                    .put("platform", "android")
                    .put("os", "Android " + Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("app", "Argus Forge")
                    .put("version", "1.0.0")
                    .toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void copy(String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("argus-forge", text));
    }

    @JavascriptInterface
    public void toast(final String msg) {
        main.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }

    /** Save a generated artifact to the public Downloads folder. */
    @JavascriptInterface
    public void saveText(String filename, String content, String cbId) {
        exec.execute(() -> {
            try {
                String path = FileStore.saveToDownloads(ctx, filename, "",
                        content.getBytes(StandardCharsets.UTF_8));
                resolve(cbId, ok(new JSONObject().put("path", path)));
            } catch (Exception e) {
                resolve(cbId, err("save_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    // ---- generation --------------------------------------------------------

    @JavascriptInterface
    public void generate(String tab, String prompt, String cbId) {
        exec.execute(() -> {
            JSONObject cfg = store.load();
            if (cfg.optString("api_key", "").trim().isEmpty()) {
                resolve(cbId, err("missing_api_key", "Set your API key in Settings first."));
                return;
            }
            String base = cfg.optString("base_url", "").trim();
            String model = cfg.optString("model", "").trim();
            if (base.isEmpty() || model.isEmpty()) {
                resolve(cbId, err("incomplete_config", "Base URL and model must be set."));
                return;
            }
            try {
                String system = systemPrompt + tabContext(tab);
                String content = api.generate(base, cfg.optString("api_key", ""),
                        model, system, prompt);
                resolve(cbId, ok(new JSONObject().put("content", content)));
            } catch (Exception e) {
                resolve(cbId, err("generate_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    // ---- deploy generated artifact (Bruce only) ---------------------------

    @JavascriptInterface
    public void deployBruce(String tab, String content, String cbId) {
        exec.execute(() -> {
            JSONObject cfg = store.load();
            String host = cfg.optString("bruce_host", "").trim();
            if (host.isEmpty()) {
                resolve(cbId, err("no_host", "Set the Bruce firmware host in Settings."));
                return;
            }
            if (!api.ping(host, true)) {
                resolve(cbId, err("offline", "Could not reach Bruce at " + host));
                return;
            }
            try {
                String destPath = "badusb".equals(tab) ? "/badusb/" : "/evilportal/";
                String[] parts = filenameFor(tab);
                String filename = parts[0] + "_" + stamp() + parts[1];
                api.bruceUpload(host, cfg.optString("bruce_user", ""),
                        cfg.optString("bruce_pass", ""), destPath,
                        content.getBytes(StandardCharsets.UTF_8), filename);
                resolve(cbId, ok(new JSONObject().put("path", destPath + filename)));
            } catch (Exception e) {
                resolve(cbId, err("bruce_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    private static String[] filenameFor(String tab) {
        if ("badusb".equals(tab)) return new String[]{"badusb", ".txt"};
        return new String[]{"portal", ".html"};
    }

    // ---- My Vault ----------------------------------------------------------

    @JavascriptInterface
    public void vaultList(String cbId) {
        exec.execute(() -> {
            try {
                JSONArray arr = new JSONArray();
                for (JSONObject e : vault.list()) arr.put(e);
                resolve(cbId, ok(new JSONObject().put("entries", arr)));
            } catch (Exception e) {
                resolve(cbId, err("vault_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    @JavascriptInterface
    public void vaultSave(String tab, String content, String cbId) {
        exec.execute(() -> {
            try {
                JSONObject e = vault.save(tab, content);
                resolve(cbId, ok(new JSONObject().put("entry", e)));
            } catch (Exception ex) {
                resolve(cbId, err("vault_save_failed", String.valueOf(ex.getMessage())));
            }
        });
    }

    @JavascriptInterface
    public void vaultDelete(String id, String cbId) {
        exec.execute(() -> {
            vault.delete(id);
            try {
                resolve(cbId, ok(new JSONObject().put("deleted", id)));
            } catch (Exception e) {
                resolve(cbId, err("vault_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    // ---- library -----------------------------------------------------------

    @JavascriptInterface
    public void libraryRefresh(String cbId) {
        exec.execute(() -> {
            String token = store.load().optString("github_token", "").trim();
            List<JSONObject> portals = new ArrayList<>();
            List<JSONObject> badusb = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (String repo : PORTAL_REPOS) {
                try {
                    collectRepo(repo, token, true, portals);
                } catch (Exception e) {
                    errors.add(repo + ": " + e.getMessage());
                }
            }
            for (String repo : BADUSB_REPOS) {
                try {
                    collectRepo(repo, token, false, badusb);
                } catch (Exception e) {
                    errors.add(repo + ": " + e.getMessage());
                }
            }
            try {
                JSONObject data = new JSONObject()
                        .put("portals", new JSONArray(portals.toString()))
                        .put("badusb", new JSONArray(badusb.toString()))
                        .put("errors", new JSONArray(errors));
                resolve(cbId, ok(data));
            } catch (Exception e) {
                resolve(cbId, err("refresh_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    @JavascriptInterface
    public void librarySearch(String query, String cbId) {
        exec.execute(() -> {
            String token = store.load().optString("github_token", "").trim();
            List<JSONObject> portals = new ArrayList<>();
            List<JSONObject> badusb = new ArrayList<>();
            try {
                for (String repo : api.githubSearch(query, token)) {
                    try {
                        JSONObject tree = api.githubRepoTree(repo, token);
                        JSONArray files = tree.optJSONArray("files");
                        String branch = tree.optString("branch", "main");
                        if (files != null) {
                            for (int i = 0; i < files.length(); i++) {
                                JSONObject f = files.optJSONObject(i);
                                if (f == null) continue;
                                String path = f.optString("path");
                                if (ApiClient.isPortalFile(path)) {
                                    portals.add(entry(repo, branch, path, f.optLong("size", 0)));
                                } else if (ApiClient.isBadUsbScript(path)) {
                                    badusb.add(entry(repo, branch, path, f.optLong("size", 0)));
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                JSONObject data = new JSONObject()
                        .put("portals", new JSONArray(portals.toString()))
                        .put("badusb", new JSONArray(badusb.toString()));
                resolve(cbId, ok(data));
            } catch (Exception e) {
                resolve(cbId, err("search_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    @JavascriptInterface
    public void libraryDownload(String entriesJson, String cbId) {
        exec.execute(() -> {
            try {
                JSONArray arr = new JSONArray(entriesJson);
                int ok = 0;
                List<String> failed = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    if (e == null) continue;
                    try {
                        byte[] bytes = api.downloadRaw(e.optString("repo"),
                                e.optString("branch"), e.optString("path"));
                        String repoName = basename(e.optString("repo"));
                        String path = e.optString("path");
                        String relDir = repoName + "/" + parent(path);
                        String name = basename(path);
                        FileStore.saveToDownloads(ctx, name, relDir, bytes);
                        ok++;
                    } catch (Exception ex) {
                        failed.add(e.optString("repo") + "/" + e.optString("path") + ": " + ex.getMessage());
                    }
                }
                JSONObject data = new JSONObject()
                        .put("ok", ok).put("total", arr.length())
                        .put("failed", new JSONArray(failed));
                resolve(cbId, ok(data));
            } catch (Exception e) {
                resolve(cbId, err("download_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    @JavascriptInterface
    public void libraryDeployBruce(String entriesJson, String cbId) {
        exec.execute(() -> {
            JSONObject cfg = store.load();
            String host = cfg.optString("bruce_host", "").trim();
            if (host.isEmpty()) {
                resolve(cbId, err("no_host", "Set the Bruce firmware host in Settings."));
                return;
            }
            if (!api.ping(host, true)) {
                resolve(cbId, err("offline", "Could not reach Bruce at " + host));
                return;
            }
            try {
                JSONArray arr = new JSONArray(entriesJson);
                String user = cfg.optString("bruce_user", "");
                String pw = cfg.optString("bruce_pass", "");
                int ok = 0;
                List<String> failed = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    if (e == null) continue;
                    try {
                        byte[] bytes = api.downloadRaw(e.optString("repo"),
                                e.optString("branch"), e.optString("path"));
                        String fname = basename(e.optString("path"));
                        String destPath = (fname.toLowerCase().endsWith(".html")
                                || fname.toLowerCase().endsWith(".htm")) ? "/evilportal/" : "/badusb/";
                        String filename = e.optString("repo").replace("/", "_") + "_" + stamp() + "_" + fname;
                        api.bruceUpload(host, user, pw, destPath, bytes, filename);
                        ok++;
                    } catch (Exception ex) {
                        failed.add(e.optString("repo") + "/" + e.optString("path") + ": " + ex.getMessage());
                    }
                }
                resolve(cbId, ok(new JSONObject()
                        .put("ok", ok).put("total", arr.length()).put("failed", new JSONArray(failed))));
            } catch (Exception e) {
                resolve(cbId, err("bruce_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    // ---- Store (Apps + Themes) --------------------------------------------

    @JavascriptInterface
    public void storeFetch(String cbId) {
        exec.execute(() -> {
            try {
                JSONObject root = api.fetchStoreJson();
                JSONArray cats = root.optJSONArray("categories");
                JSONArray apps = new JSONArray();
                JSONArray themes = new JSONArray();
                storeItems.clear();
                if (cats != null) {
                    for (int i = 0; i < cats.length(); i++) {
                        JSONObject cat = cats.optJSONObject(i);
                        JSONArray list = cat != null ? cat.optJSONArray("apps") : null;
                        if (list == null) continue;
                        for (int j = 0; j < list.length(); j++) {
                            JSONObject item = list.optJSONObject(j);
                            if (item == null) continue;
                            storeItems.put(item.optString("slug"), item);
                            JSONObject disp = displayItem(item);
                            if ("Themes".equalsIgnoreCase(item.optString("category"))) {
                                themes.put(disp);
                            } else {
                                apps.put(disp);
                            }
                        }
                    }
                }
                resolve(cbId, ok(new JSONObject().put("apps", apps).put("themes", themes)));
            } catch (Exception e) {
                resolve(cbId, err("store_fetch_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    private JSONObject displayItem(JSONObject item) throws Exception {
        JSONObject d = new JSONObject();
        d.put("slug", item.optString("slug"));
        d.put("name", item.optString("name"));
        d.put("category", item.optString("category"));
        d.put("version", item.optString("version"));
        d.put("owner", item.optString("owner"));
        d.put("description", item.optString("description"));
        String mp = item.optString("metadataPath");
        String icon = "https://brucedevices.github.io/App-Store-Data/"
                + ApiClient.encodePath(mp.replace("metadata.json", "logo.png"));
        d.put("icon", icon);
        return d;
    }

    /**
     * Install a store item onto the connected Bruce device:
     * apps -> /BruceJS/<Category>/File.js, themes -> /Themes/<Name>/.
     */
    @JavascriptInterface
    public void storeInstall(String slug, String cbId) {
        exec.execute(() -> {
            JSONObject item = storeItems.get(slug);
            if (item == null) {
                resolve(cbId, err("not_found", "Store item not found; refresh and retry."));
                return;
            }
            JSONObject cfg = store.load();
            String host = cfg.optString("bruce_host", "").trim();
            if (host.isEmpty()) {
                resolve(cbId, err("no_host", "Set the Bruce firmware host in Settings."));
                return;
            }
            if (!api.ping(host, true)) {
                resolve(cbId, err("offline", "Could not reach Bruce at " + host));
                return;
            }
            String user = cfg.optString("bruce_user", "");
            String pw = cfg.optString("bruce_pass", "");
            boolean isTheme = "Themes".equalsIgnoreCase(item.optString("category"));
            String category = item.optString("category");
            String name = item.optString("name");
            String owner = item.optString("owner");
            String repo = item.optString("repo");
            String commit = item.optString("commit");
            String base = item.optString("path", "/");
            if (!base.endsWith("/")) base += "/";
            JSONArray files = item.optJSONArray("files");
            int ok = 0;
            List<String> failed = new ArrayList<>();
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    Object f = files.opt(i);
                    String srcName, dstName;
                    if (f instanceof JSONObject) {
                        JSONObject fo = (JSONObject) f;
                        srcName = fo.optString("source", "");
                        dstName = fo.optString("destination", srcName);
                    } else if (f != null) {
                        srcName = String.valueOf(f);
                        dstName = srcName;
                    } else {
                        continue;
                    }
                    try {
                        byte[] data = api.downloadRawPath(owner, repo, commit, base + srcName);
                        if (isTheme) {
                            api.bruceUpload(host, user, pw, "/Themes/" + name + "/", data, dstName);
                        } else {
                            api.bruceUpload(host, user, pw, "/BruceJS/" + category + "/", data, dstName);
                        }
                        ok++;
                    } catch (Exception ex) {
                        failed.add(srcName + ": " + ex.getMessage());
                    }
                }
            }
            try {
                resolve(cbId, ok(new JSONObject()
                        .put("ok", ok).put("total", files == null ? 0 : files.length())
                        .put("failed", new JSONArray(failed))));
            } catch (Exception e) {
                resolve(cbId, err("install_failed", String.valueOf(e.getMessage())));
            }
        });
    }

    // ---- helpers -----------------------------------------------------------

    private void collectRepo(String repo, String token, boolean portal, List<JSONObject> out) throws Exception {
        JSONObject tree = api.githubRepoTree(repo, token);
        JSONArray files = tree.optJSONArray("files");
        String branch = tree.optString("branch", "main");
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.optJSONObject(i);
                if (f == null) continue;
                String path = f.optString("path");
                boolean hit = portal ? ApiClient.isPortalFile(path) : ApiClient.isBadUsbScript(path);
                if (hit) out.add(entry(repo, branch, path, f.optLong("size", 0)));
            }
        }
    }

    private static JSONObject entry(String repo, String branch, String path, long size) {
        try {
            return new JSONObject()
                    .put("repo", repo).put("branch", branch)
                    .put("path", path).put("size", size);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String basename(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(0, i) : "";
    }

    private static String tabContext(String tab) {
        if ("badusb".equals(tab)) {
            return "\n\n[Current workspace tab: BAD USB SCRIPT CREATION. Generate DuckyScript / Rubber Ducky / Flipper Zero / O.MG payloads and any accompanying scripts.]";
        }
        return "\n\n[Current workspace tab: EVIL PORTAL CREATION. Generate captive-portal code, HTML/CSS/JS login pages, backend capture scripts, and deployment instructions.]";
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    private static String decodeLenient(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static JSONObject ok(JSONObject data) {
        try {
            return new JSONObject().put("ok", true).put("data", data);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject err(String code, String message) {
        try {
            return new JSONObject().put("ok", false).put("code", code).put("error", message);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void resolve(String cbId, JSONObject payload) {
        main.post(() -> {
            try {
                String json = JSONObject.quote(cbId) + "," + payload.toString();
                webView.evaluateJavascript("window.ArgusBridge && ArgusBridge.resolve(" + json + ");", null);
            } catch (Exception ignored) {
            }
        });
    }
}
