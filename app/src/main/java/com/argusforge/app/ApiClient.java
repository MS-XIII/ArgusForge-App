package com.argusforge.app;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * All network I/O. Pure HttpURLConnection (no third-party HTTP client).
 * Bruce firmware devices may serve self-signed TLS, so those calls open with a
 * trust-all context; the LLM / GitHub / App Store calls use normal system TLS.
 */
public final class ApiClient {

    static final class Result {
        final int code;
        final byte[] bytes;
        final String text;

        Result(int code, byte[] bytes, String text) {
            this.code = code;
            this.bytes = bytes;
            this.text = text;
        }
    }

    // ---- HTTP core ---------------------------------------------------------

    private Result request(String method, String url, Map<String, String> headers,
                           byte[] body, int timeoutMs, boolean trustAll) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (trustAll && conn instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) conn;
            https.setSSLSocketFactory(sslContext().getSocketFactory());
            https.setHostnameVerifier(ALLOW_ALL);
        }
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        conn.setInstanceFollowRedirects(true);
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        if (body != null) {
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        byte[] bytes = readAll(is);
        String text = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        conn.disconnect();
        return new Result(code, bytes, text);
    }

    private static byte[] readAll(InputStream is) {
        if (is == null) return new byte[0];
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String rstrip(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static SSLContext sslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {
            }

            public void checkServerTrusted(X509Certificate[] c, String a) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new SecureRandom());
        return sc;
    }

    private static final HostnameVerifier ALLOW_ALL = new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    // ---- LLM generation ----------------------------------------------------

    public String generate(String baseUrl, String apiKey, String model,
                           String system, String prompt) throws Exception {
        String url = rstrip(baseUrl) + "/chat/completions";
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.7);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        payload.put("messages", messages);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "application/json");

        Result r = request("POST", url, headers,
                payload.toString().getBytes(StandardCharsets.UTF_8), 300000, false);
        if (r.code != 200) {
            throw new RuntimeException("HTTP " + r.code + ": " + truncate(r.text, 400));
        }
        JSONObject data = new JSONObject(r.text);
        return data.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content");
    }

    // ---- Bruce -------------------------------------------------------------

    public boolean ping(String host, boolean trustAll) {
        try {
            return request("GET", rstrip(host) + "/", null, null, 3000, trustAll).code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public void bruceUpload(String host, String user, String password,
                            String destPath, byte[] content, String filename) throws Exception {
        String url = rstrip(host) + "/api/upload";
        String boundary = "----ArgusForge" + System.currentTimeMillis();

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeTextPart(body, boundary, "path", destPath);
        writeFilePart(body, boundary, "file", filename, content);
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        headers.put("Authorization", "Basic " + Base64.encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));

        Result r = request("POST", url, headers, body.toByteArray(), 30000, true);
        if (r.code != 200 && r.code != 201 && r.code != 204) {
            throw new RuntimeException("upload failed: HTTP " + r.code + " - " + truncate(r.text, 200));
        }
    }

    private static void writeTextPart(ByteArrayOutputStream out, String boundary,
                                      String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream out, String boundary,
                                      String name, String filename, byte[] data) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\""
                + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + guessMime(filename) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String guessMime(String filename) {
        String f = filename.toLowerCase();
        if (f.endsWith(".html") || f.endsWith(".htm")) return "text/html";
        if (f.endsWith(".txt")) return "text/plain";
        if (f.endsWith(".js")) return "application/javascript";
        if (f.endsWith(".json")) return "application/json";
        if (f.endsWith(".png")) return "image/png";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    // ---- GitHub ------------------------------------------------------------

    private static Map<String, String> githubHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        if (token != null && !token.trim().isEmpty()) {
            headers.put("Authorization", "Bearer " + token.trim());
        }
        return headers;
    }

    /** Returns {repo, branch, files:[{path,size}]}. */
    public JSONObject githubRepoTree(String repo, String token) throws Exception {
        Map<String, String> headers = githubHeaders(token);
        Result r = request("GET", "https://api.github.com/repos/" + repo, headers, null, 15000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);
        String branch = new JSONObject(r.text).optString("default_branch", "main");

        r = request("GET", "https://api.github.com/repos/" + repo + "/git/trees/"
                + branch + "?recursive=1", headers, null, 30000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);

        JSONArray tree = new JSONObject(r.text).optJSONArray("tree");
        JSONObject out = new JSONObject();
        out.put("repo", repo);
        out.put("branch", branch);
        JSONArray files = new JSONArray();
        if (tree != null) {
            for (int i = 0; i < tree.length(); i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item != null && "blob".equals(item.optString("type"))) {
                    files.put(new JSONObject()
                            .put("path", item.optString("path"))
                            .put("size", item.optLong("size", 0)));
                }
            }
        }
        out.put("files", files);
        return out;
    }

    public List<String> githubSearch(String q, String token) throws Exception {
        String url = "https://api.github.com/search/repositories?q="
                + URLEncoder.encode(q, "UTF-8") + "&sort=stars&per_page=15";
        Result r = request("GET", url, githubHeaders(token), null, 15000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);
        JSONArray items = new JSONObject(r.text).optJSONArray("items");
        List<String> repos = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null) repos.add(it.optString("full_name"));
            }
        }
        return repos;
    }

    public byte[] downloadRaw(String repo, String branch, String path) throws Exception {
        String url = "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path;
        Result r = request("GET", url, null, null, 30000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);
        return r.bytes;
    }

    // ---- Bruce App Store (bruce.computer/appstore data endpoint) -----------

    /** Fetch the appstore's canonical data (the JSON the store page renders). */
    public JSONObject fetchStoreJson() throws Exception {
        String url = "https://brucedevices.github.io/App-Store-Data/releases/category-all.json";
        Result r = request("GET", url, null, null, 30000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);
        return new JSONObject(r.text);
    }

    /** Download a pinned file from a source repo at a specific commit. */
    public byte[] downloadRawPath(String owner, String repo, String commit, String path) throws Exception {
        String p = path;
        while (p.startsWith("/")) p = p.substring(1);
        String url = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/"
                + commit + "/" + encodePath(p);
        Result r = request("GET", url, null, null, 30000, false);
        if (r.code != 200) throw new RuntimeException("HTTP " + r.code);
        return r.bytes;
    }

    /** URL-encode each path segment, preserving slashes (spaces -> %20, etc.). */
    public static String encodePath(String p) {
        if (p == null) return "";
        String[] segs = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append("/");
            try {
                sb.append(URLEncoder.encode(segs[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                sb.append(segs[i]);
            }
        }
        return sb.toString();
    }

    public static String basename(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    // ---- library filters ---------------------------------------------------

    /** True if the file is a usable Evil Portal HTML template (not docs/readme). */
    public static boolean isPortalFile(String path) {
        String low = path.toLowerCase();
        String base = basename(low);
        if (base.startsWith("readme") || base.startsWith("license") || base.startsWith("licence")
                || base.startsWith("changelog") || base.startsWith("contributing")
                || base.endsWith(".md") || low.contains("/docs/") || low.contains("/.github/")
                || low.contains("/node_modules/"))
            return false;
        return low.endsWith(".html") || low.endsWith(".htm");
    }

    /**
     * True if the file is a usable DuckyScript payload. Excludes READMEs,
     * licenses, docs, configs and any non-script text, so the library only
     * lists payloads the device can actually run.
     */
    public static boolean isBadUsbScript(String path) {
        String low = path.toLowerCase();
        String base = basename(low);
        if (base.startsWith("readme") || base.startsWith("license") || base.startsWith("licence")
                || base.startsWith("changelog") || base.startsWith("contributing")
                || base.startsWith("todo") || base.startsWith("notes") || base.startsWith("authors")
                || base.startsWith("credits") || base.startsWith("install") || base.startsWith("usage")
                || base.startsWith(".git") || base.startsWith("docker")
                || base.endsWith(".md") || base.endsWith(".json") || base.endsWith(".yml")
                || base.endsWith(".yaml") || base.endsWith(".cfg") || base.endsWith(".ini")
                || base.endsWith(".conf") || base.endsWith(".toml") || base.endsWith(".png")
                || base.endsWith(".jpg") || base.endsWith(".jpeg") || base.endsWith(".gif")
                || base.endsWith(".svg"))
            return false;
        if (low.contains("/docs/") || low.contains("/.github/") || low.contains("/.git/")
                || low.contains("/node_modules/") || low.contains("/examples/") || low.contains("/tests/"))
            return false;
        if (low.endsWith(".duck") || low.endsWith(".ds") || low.endsWith(".badusb")
                || low.endsWith(".payload"))
            return true;
        if (low.endsWith(".txt")) {
            return !(base.contains("config") || base.contains("template")
                    || base.contains("settings") || base.contains("readme"));
        }
        return false;
    }
}
