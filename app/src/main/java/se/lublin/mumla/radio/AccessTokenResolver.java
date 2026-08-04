/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package se.lublin.mumla.radio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves public room access tokens from a complete Minimum radio configuration. */
public final class AccessTokenResolver {
    private AccessTokenResolver() {
    }

    /**
     * Returns non-blank public room tokens in configuration order, without duplicates.
     * Protected-room references are intentionally not resolved here because secure storage is
     * not available yet. Malformed rooms and access entries are ignored.
     *
     * @param config complete radio configuration, or {@code null}
     * @return an immutable, first-seen ordered list of public access tokens
     */
    public static List<String> resolve(JSONObject config) {
        if (config == null) {
            return Collections.emptyList();
        }

        JSONArray rooms = config.optJSONArray("rooms");
        if (rooms == null) {
            return Collections.emptyList();
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (int index = 0; index < rooms.length(); index++) {
            try {
                Object roomValue = rooms.opt(index);
                if (!(roomValue instanceof JSONObject)) {
                    continue;
                }

                Object accessValue = ((JSONObject) roomValue).opt("access");
                if (!(accessValue instanceof JSONObject)) {
                    continue;
                }

                JSONObject access = (JSONObject) accessValue;
                Object mode = access.opt("mode");
                if (!(mode instanceof String) || !"public".equals(mode)) {
                    continue;
                }

                Object tokenValue = access.opt("token");
                if (!(tokenValue instanceof String)) {
                    continue;
                }

                String token = ((String) tokenValue).trim();
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            } catch (RuntimeException ignored) {
                // A malformed entry must not prevent later rooms from being resolved.
            }
        }

        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(tokens));
    }
}
