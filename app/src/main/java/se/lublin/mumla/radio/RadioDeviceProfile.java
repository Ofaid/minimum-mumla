/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.os.Build;

import java.util.Locale;

/** Identifies supported radio hardware without coupling the app to one handset model. */
public final class RadioDeviceProfile {
    public static final String T99 = "t99";
    public static final String T88 = "t88";
    public static final String GENERIC = "generic-radio";

    private RadioDeviceProfile() {
    }

    public static String detectCurrent() {
        return detect(Build.MANUFACTURER, Build.MODEL);
    }

    /**
     * Uses only stable build metadata for initial profile selection. Detailed key mapping remains
     * server-configurable because OEM firmware can expose the same physical button differently.
     */
    public static String detect(String manufacturer, String model) {
        String normalizedManufacturer = normalize(manufacturer);
        String normalizedModel = normalize(model);

        if ("youdotech".equals(normalizedManufacturer) && "qm011".equals(normalizedModel)) {
            return T99;
        }
        if (normalizedModel.contains("t88") || normalizedManufacturer.contains("t88")) {
            return T88;
        }
        return GENERIC;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
