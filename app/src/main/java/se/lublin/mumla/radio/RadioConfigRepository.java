/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Loads the radio configuration from the embedded safe default and optional GitHub Pages data.
 * Network refresh must be called from a worker thread. No access token is logged or persisted by
 * this class outside the JSON configuration cache supplied by the application.
 */
public final class RadioConfigRepository {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CONFIG_BYTES = 262144;
    public static final int MAX_PTT_SECONDS = 120;
    public static final String DEFAULT_BASE_URL = "https://awatchar.github.io/minimum/";

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final String ASSET_DEFAULT = "radio/default.json";
    private static final String ACTIVE_FILE = "active-config.json";
    private static final String PREVIOUS_FILE = "previous-config.json";
    private static final String TEMP_FILE = "downloaded-config.tmp";

    private final Context context;
    private final String baseUrl;

    public RadioConfigRepository(Context context) {
        this(context, DEFAULT_BASE_URL);
    }

    public RadioConfigRepository(Context context, String baseUrl) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (baseUrl == null || !baseUrl.startsWith("https://") || !baseUrl.endsWith("/")) {
            throw new IllegalArgumentException("config base URL must be HTTPS and end with '/'");
        }
        this.context = context.getApplicationContext();
        this.baseUrl = baseUrl;
    }

    /** Returns the validated active cache, or the embedded default if the cache is absent/bad. */
    public JSONObject loadActiveOrDefault() throws IOException, JSONException {
        File active = new File(cacheDirectory(), ACTIVE_FILE);
        if (active.isFile()) {
            try {
                JSONObject cached = readJson(active);
                validateConfig(cached, null);
                return cached;
            } catch (JSONException | IOException ignored) {
                // A bad active cache must never prevent the radio client from starting.
            }
        }
        JSONObject fallback = readEmbeddedDefault();
        validateConfig(fallback, null);
        return fallback;
    }

    /**
     * Fetches and merges default, model and optional device configuration. The result is written
     * atomically to the active cache; the prior active config is retained as a rollback copy.
     */
    public JSONObject refresh(String deviceId, String modelProfile)
            throws IOException, JSONException {
        if (!DeviceIdentityManager.isValidDeviceId(deviceId)) {
            throw new IllegalArgumentException("invalid device id");
        }
        if (!isSafePathPart(modelProfile)) {
            throw new IllegalArgumentException("invalid model profile");
        }

        JSONObject merged = readEmbeddedDefault();
        JSONObject remoteDefault = fetchJson("default.json");
        validateConfig(remoteDefault, null);
        merged = merge(merged, remoteDefault);

        JSONObject model = fetchJson("models/" + modelProfile + ".json");
        validateOverlay(model, null);
        merged = merge(merged, model);

        try {
            JSONObject device = fetchJson("devices/" + deviceId + ".json");
            validateOverlay(device, deviceId);
            merged = merge(merged, device);
        } catch (NotFoundException ignored) {
            // Device-specific overrides are optional.
        }

        validateConfig(merged, deviceId);
        File active = new File(cacheDirectory(), ACTIVE_FILE);
        if (active.isFile()) {
            JSONObject current = null;
            try {
                current = readJson(active);
                validateConfig(current, null);
            } catch (IOException | JSONException ignored) {
                // An unreadable active cache is not a valid downgrade baseline.
            }
            if (current != null) {
                rejectDowngrade(merged, current);
            }
        }
        writeActive(merged);
        return merged;
    }

    /** Rejects a candidate that would replace a newer Last Known Good configuration. */
    public static void rejectDowngrade(JSONObject candidate, JSONObject active)
            throws JSONException {
        int candidateVersion = candidate == null ? -1 : candidate.optInt("configVersion", -1);
        int activeVersion = active == null ? -1 : active.optInt("configVersion", -1);
        if (candidateVersion < activeVersion) {
            throw new JSONException("config downgrade rejected");
        }
    }

    /** Deep-merges JSON objects; arrays and scalar values from overlay replace the base value. */
    public static JSONObject merge(JSONObject base, JSONObject overlay) throws JSONException {
        JSONObject result = new JSONObject(base.toString());
        JSONArray names = overlay.names();
        if (names == null) {
            return result;
        }
        for (int i = 0; i < names.length(); i++) {
            String name = names.getString(i);
            Object overlayValue = overlay.get(name);
            Object baseValue = result.opt(name);
            if (overlayValue instanceof JSONObject && baseValue instanceof JSONObject) {
                result.put(name, merge((JSONObject) baseValue, (JSONObject) overlayValue));
            } else {
                result.put(name, overlayValue);
            }
        }
        return result;
    }

    /** Validates invariants that protect startup and the PTT safety contract. */
    public static void validateConfig(JSONObject config, String expectedDeviceId)
            throws JSONException {
        validateOverlay(config, expectedDeviceId);
        JSONObject mumble = config.optJSONObject("mumble");
        JSONObject ptt = config.optJSONObject("ptt");
        if (mumble == null || mumble.optString("serverId", "").isEmpty()
                || mumble.optString("defaultRoom", "").isEmpty()) {
            throw new JSONException("incomplete Mumble config");
        }
        int port = mumble.optInt("port", -1);
        if (port < 1 || port > 65535) {
            throw new JSONException("invalid Mumble port");
        }
        if (ptt == null || ptt.optInt("maximumTxSeconds", 0) < 1
                || ptt.optInt("maximumTxSeconds", MAX_PTT_SECONDS) > MAX_PTT_SECONDS
                || !ptt.optBoolean("releaseOnNetworkLoss", false)) {
            throw new JSONException("unsafe PTT config");
        }
        JSONArray rooms = config.optJSONArray("rooms");
        JSONObject hardware = config.optJSONObject("hardware");
        if (rooms == null || rooms.length() == 0 || hardware == null
                || hardware.optString("profile", "").isEmpty()) {
            throw new JSONException("incomplete radio config");
        }
    }

    /** Validates a complete config overlay without requiring fields supplied by the base config. */
    public static void validateOverlay(JSONObject config, String expectedDeviceId)
            throws JSONException {
        if (config == null || config.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new JSONException("unsupported config schema");
        }
        if (config.optInt("configVersion", 0) < 1) {
            throw new JSONException("invalid config version");
        }
        String configDeviceId = config.optString("deviceId", "*");
        if (!"*".equals(configDeviceId)
                && !DeviceIdentityManager.isValidDeviceId(configDeviceId)) {
            throw new JSONException("invalid config device id");
        }
        if (expectedDeviceId != null && !"*".equals(configDeviceId)
                && !expectedDeviceId.equals(configDeviceId)) {
            throw new JSONException("config is for another device");
        }
        JSONObject mumble = config.optJSONObject("mumble");
        if (mumble != null && mumble.has("port")) {
            int port = mumble.optInt("port", -1);
            if (port < 1 || port > 65535) {
                throw new JSONException("invalid Mumble port");
            }
        }
        if (mumble != null && mumble.has("serverCertificateSha256")) {
            RadioConnectionConfig.normalizeFingerprint(
                    mumble.optString("serverCertificateSha256", ""));
        }
        JSONObject ptt = config.optJSONObject("ptt");
        if (ptt != null) {
            int maximum = ptt.optInt("maximumTxSeconds", MAX_PTT_SECONDS);
            if (maximum < 1 || maximum > MAX_PTT_SECONDS
                    || (ptt.has("releaseOnNetworkLoss")
                    && !ptt.optBoolean("releaseOnNetworkLoss", false))) {
                throw new JSONException("unsafe PTT overlay");
            }
        }
        JSONArray rooms = config.optJSONArray("rooms");
        if (rooms != null && rooms.length() == 0) {
            throw new JSONException("empty room overlay");
        }
        JSONObject hardware = config.optJSONObject("hardware");
        if (hardware != null && hardware.has("profile")
                && hardware.optString("profile", "").isEmpty()) {
            throw new JSONException("empty hardware profile");
        }
    }

    private JSONObject readEmbeddedDefault() throws IOException, JSONException {
        try (InputStream input = context.getAssets().open(ASSET_DEFAULT)) {
            return new JSONObject(readLimited(input));
        }
    }

    private JSONObject fetchJson(String path) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new NotFoundException();
            }
            if (status < 200 || status >= 300) {
                throw new IOException("config HTTP status " + status);
            }
            return new JSONObject(readLimited(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private File cacheDirectory() {
        File directory = new File(context.getFilesDir(), "radio-config");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("cannot create radio config cache");
        }
        return directory;
    }

    private void writeActive(JSONObject config) throws IOException {
        File directory = cacheDirectory();
        File active = new File(directory, ACTIVE_FILE);
        File previous = new File(directory, PREVIOUS_FILE);
        File temporary = new File(directory, TEMP_FILE);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(config.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (active.isFile() && !active.renameTo(previous)) {
            throw new IOException("cannot preserve previous config");
        }
        if (!temporary.renameTo(active)) {
            throw new IOException("cannot activate downloaded config");
        }
    }

    private static JSONObject readJson(File file) throws IOException, JSONException {
        try (InputStream input = new FileInputStream(file)) {
            return new JSONObject(readLimited(input));
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_CONFIG_BYTES) {
                throw new IOException("config exceeds maximum size");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isSafePathPart(String value) {
        return value != null && value.matches("[a-z0-9-]{1,64}");
    }

    private static final class NotFoundException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
