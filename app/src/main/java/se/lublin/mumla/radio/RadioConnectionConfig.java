/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable, validated subset of radio config used by the live connection/UI path. */
public final class RadioConnectionConfig {
    private static final int MAX_ROOMS = 16;

    private final int configVersion;
    private final String serviceName;
    private final String serverId;
    private final String host;
    private final int port;
    private final String serverCertificateSha256;
    private final boolean autoConnect;
    private final boolean autoReconnect;
    private final List<String> accessTokens;
    private final List<Room> rooms;
    private final int defaultRoomIndex;

    private RadioConnectionConfig(int configVersion, String serviceName, String serverId,
                                  String host, int port, boolean autoConnect,
                                  String serverCertificateSha256, boolean autoReconnect,
                                  List<String> accessTokens,
                                  List<Room> rooms, int defaultRoomIndex) {
        this.configVersion = configVersion;
        this.serviceName = serviceName;
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.serverCertificateSha256 = serverCertificateSha256;
        this.autoConnect = autoConnect;
        this.autoReconnect = autoReconnect;
        this.accessTokens = Collections.unmodifiableList(new ArrayList<>(accessTokens));
        this.rooms = Collections.unmodifiableList(new ArrayList<>(rooms));
        this.defaultRoomIndex = defaultRoomIndex;
    }

    public static RadioConnectionConfig fromJson(JSONObject config) throws JSONException {
        RadioConfigRepository.validateConfig(config, null);

        JSONObject mumble = config.getJSONObject("mumble");
        String host = requireNonBlank(mumble.optString("host", ""), "Mumble host");
        if (host.length() > 253 || !host.matches("[A-Za-z0-9.-]+")) {
            throw new JSONException("invalid Mumble host");
        }

        String defaultRoomId = requireNonBlank(mumble.optString("defaultRoom", ""),
                "default room");
        String serverCertificateSha256 = normalizeFingerprint(
                mumble.optString("serverCertificateSha256", ""));
        JSONArray roomArray = config.getJSONArray("rooms");
        if (roomArray.length() > MAX_ROOMS) {
            throw new JSONException("too many radio rooms");
        }

        List<Room> rooms = new ArrayList<>();
        int defaultRoomIndex = -1;
        for (int index = 0; index < roomArray.length(); index++) {
            JSONObject roomJson = roomArray.optJSONObject(index);
            if (roomJson == null) {
                throw new JSONException("invalid room entry");
            }
            Room room = new Room(
                    requireNonBlank(roomJson.optString("id", ""), "room id"),
                    requireNonBlank(roomJson.optString("label", ""), "room label"),
                    normalizePath(roomJson.optString("path", "")),
                    roomJson.optString("presetKey", ""));
            if (room.id.equals(defaultRoomId)) {
                if (defaultRoomIndex >= 0) {
                    throw new JSONException("duplicate default room");
                }
                defaultRoomIndex = rooms.size();
            }
            rooms.add(room);
        }
        if (defaultRoomIndex < 0) {
            throw new JSONException("default room is missing");
        }

        JSONObject service = config.optJSONObject("service");
        String serviceName = service == null
                ? "Minimum"
                : requireNonBlank(service.optString("name", "Minimum"), "service name");

        return new RadioConnectionConfig(
                config.getInt("configVersion"),
                serviceName,
                requireNonBlank(mumble.optString("serverId", ""), "server id"),
                host,
                mumble.getInt("port"),
                mumble.optBoolean("autoConnect", false),
                serverCertificateSha256,
                mumble.optBoolean("autoReconnect", true),
                AccessTokenResolver.resolve(config),
                rooms,
                defaultRoomIndex);
    }

    private static String requireNonBlank(String value, String field) throws JSONException {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new JSONException(field + " is blank");
        }
        return trimmed;
    }

    static String normalizePath(String value) throws JSONException {
        String path = requireNonBlank(value, "room path");
        if (!path.startsWith("/") || path.length() > 512 || path.contains("//")) {
            throw new JSONException("invalid room path");
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    static String normalizeFingerprint(String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.replace(":", "").replace(" ", "")
                .toUpperCase(Locale.ROOT);
        if (!normalized.matches("[0-9A-F]{64}")) {
            throw new JSONException("invalid server certificate fingerprint");
        }
        return normalized;
    }

    public int getConfigVersion() {
        return configVersion;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServerId() {
        return serverId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getServerCertificateSha256() {
        return serverCertificateSha256;
    }

    public boolean isAutoConnect() {
        return autoConnect;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public List<String> getAccessTokens() {
        return accessTokens;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public int getDefaultRoomIndex() {
        return defaultRoomIndex;
    }

    public Room getDefaultRoom() {
        return rooms.get(defaultRoomIndex);
    }

    public static final class Room {
        private final String id;
        private final String label;
        private final String path;
        private final String presetKey;

        Room(String id, String label, String path, String presetKey) {
            this.id = id;
            this.label = label;
            this.path = path;
            this.presetKey = presetKey == null ? "" : presetKey.trim();
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getPath() {
            return path;
        }

        public String getPresetKey() {
            return presetKey;
        }
    }
}
