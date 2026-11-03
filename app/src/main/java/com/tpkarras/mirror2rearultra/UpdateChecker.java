package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Asks GitHub whether the Releases page holds a newer build than the one running.
 *
 * <p>Only the latest published release is read; drafts and pre-releases never
 * come back from that endpoint. Fetching the file itself and handing it to the
 * system installer is {@link UpdateInstaller}'s job.
 */
final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/N4RKOM/Mirror2RearUltra/releases/latest";
    /** Links that leave the app must stay on GitHub, whatever the response says. */
    private static final String TRUSTED_PREFIX = "https://github.com/";
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private static final int MAX_NOTES_CHARS = 4000;

    /** A published release, with the links and facts the update sheet shows. */
    static final class Release {
        final String tag;
        final String pageUrl;
        /** The changelog as plain text; empty when the release has none. */
        final String notes;
        /** The app's APK, or null when the release carries none. */
        @Nullable final String apkUrl;
        /** Size of the APK in bytes, 0 when GitHub did not say. */
        final long apkSize;
        /** Lower-case hex SHA-256 of the APK, or null when GitHub did not say. */
        @Nullable final String apkSha256;

        Release(String tag, String pageUrl, String notes, @Nullable String apkUrl,
                long apkSize, @Nullable String apkSha256) {
            this.tag = tag;
            this.pageUrl = pageUrl;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
        }

        /** The tag without its leading "v": "1.30.0". */
        String version() {
            return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        }
    }

    private UpdateChecker() {
    }

    /** Fetches the latest release; blocks, so keep it off the main thread. */
    static Release fetchLatest() throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Mirror2RearUltra");
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Update HTTP " + responseCode);
            }
            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_RESPONSE_BYTES) {
                        throw new IOException("Update response too large");
                    }
                    output.write(buffer, 0, read);
                }
                Release release = parse(new JSONObject(output.toString(StandardCharsets.UTF_8.name())));
                if (release == null) {
                    throw new IOException("Update response has no release");
                }
                return release;
            }
        } catch (JSONException error) {
            throw new IOException("Invalid update response", error);
        } finally {
            connection.disconnect();
        }
    }

    @Nullable
    static Release parse(JSONObject json) {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return null;
        }
        String tag = json.optString("tag_name", "");
        String pageUrl = json.optString("html_url", "");
        if (tag.isEmpty() || !pageUrl.startsWith(TRUSTED_PREFIX)) {
            return null;
        }
        String apkUrl = null;
        long apkSize = 0;
        String apkSha256 = null;
        JSONArray assets = json.optJSONArray("assets");
        if (assets != null) {
            String[] names = new String[assets.length()];
            String[] urls = new String[assets.length()];
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset != null) {
                    names[i] = asset.optString("name", "");
                    urls[i] = asset.optString("browser_download_url", "");
                }
            }
            int apk = pickApk(names, urls);
            if (apk >= 0) {
                JSONObject asset = assets.optJSONObject(apk);
                apkUrl = urls[apk];
                apkSize = Math.max(0, asset.optLong("size", 0));
                apkSha256 = sha256Of(asset.optString("digest", ""));
            }
        }
        String notes = cleanNotes(json.optString("body", ""));
        return new Release(tag, pageUrl, notes, apkUrl, apkSize, apkSha256);
    }

    /**
     * Index of the app's own APK among the release files, or -1. The Rear Unlock
     * module is published next to it and must not be mistaken for the update.
     */
    static int pickApk(String[] names, String[] urls) {
        for (int i = 0; i < names.length && i < urls.length; i++) {
            String name = names[i] == null ? "" : names[i].toLowerCase(Locale.ROOT);
            String url = urls[i] == null ? "" : urls[i];
            if (name.endsWith(".apk") && !name.contains("rearunlock") && url.startsWith(TRUSTED_PREFIX)) {
                return i;
            }
        }
        return -1;
    }

    /** The hex hash out of GitHub's "sha256:&lt;hex&gt;" digest, or null if it is not one. */
    @Nullable
    static String sha256Of(@Nullable String digest) {
        if (digest == null || !digest.startsWith("sha256:")) {
            return null;
        }
        String hex = digest.substring("sha256:".length()).toLowerCase(Locale.ROOT);
        return hex.matches("[0-9a-f]{64}") ? hex : null;
    }

    /**
     * Turns a release body written in Markdown into the plain lines the sheet
     * shows: headings lose their hashes, bullets become dots, links keep their
     * text, and GitHub's trailing "Full Changelog" link is dropped.
     */
    static String cleanNotes(@Nullable String body) {
        if (body == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean blankPending = false;
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.toLowerCase(Locale.ROOT).startsWith("**full changelog**")) {
                continue;
            }
            line = line.replaceAll("^#+\\s*", "");
            if (line.startsWith("* ") || line.startsWith("- ")) {
                line = "• " + line.substring(2).trim();
            }
            line = line.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                    .replace("**", "")
                    .replace("`", "");
            if (line.isEmpty()) {
                blankPending = out.length() > 0;
                continue;
            }
            if (blankPending) {
                out.append('\n');
                blankPending = false;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
            if (out.length() > MAX_NOTES_CHARS) {
                out.setLength(MAX_NOTES_CHARS);
                out.append('…');
                break;
            }
        }
        return out.toString();
    }

    /**
     * Whether {@code candidate} (a tag such as "v1.30.0") is newer than
     * {@code current} (a version name such as "1.29.1"). Numeric parts are
     * compared left to right; a missing part counts as zero, and anything
     * after a "-" or "+" is ignored. A tag that cannot be read is never newer.
     */
    static boolean isNewer(String current, String candidate) {
        int[] now = versionParts(current);
        int[] next = versionParts(candidate);
        if (now == null || next == null) {
            return false;
        }
        int length = Math.max(now.length, next.length);
        for (int i = 0; i < length; i++) {
            int a = i < now.length ? now[i] : 0;
            int b = i < next.length ? next[i] : 0;
            if (a != b) {
                return b > a;
            }
        }
        return false;
    }

    @Nullable
    private static int[] versionParts(@Nullable String version) {
        if (version == null) {
            return null;
        }
        String core = version.trim();
        if (core.startsWith("v") || core.startsWith("V")) {
            core = core.substring(1);
        }
        int cut = core.length();
        for (int i = 0; i < core.length(); i++) {
            char c = core.charAt(i);
            if (c == '-' || c == '+') {
                cut = i;
                break;
            }
        }
        core = core.substring(0, cut);
        if (core.isEmpty()) {
            return null;
        }
        String[] pieces = core.split("\\.", -1);
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                parts[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException notNumeric) {
                return null;
            }
            if (parts[i] < 0) {
                return null;
            }
        }
        return parts;
    }
}
