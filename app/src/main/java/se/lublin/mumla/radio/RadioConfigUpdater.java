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
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.json.JSONException;

import java.io.IOException;

/** Schedules a quiet, best-effort config refresh without delaying the normal Mumla startup. */
public final class RadioConfigUpdater {
    private static final String TAG = RadioConfigUpdater.class.getName();
    private static final String PREF_LAST_ATTEMPT = "radio_config_last_attempt_ms";
    private static final long REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private RadioConfigUpdater() {
    }

    public static void schedule(Context context) {
        Context applicationContext = context.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        long now = System.currentTimeMillis();
        long lastAttempt = preferences.getLong(PREF_LAST_ATTEMPT, 0L);
        if (lastAttempt > 0L && now - lastAttempt < REFRESH_INTERVAL_MS) {
            return;
        }
        preferences.edit().putLong(PREF_LAST_ATTEMPT, now).apply();

        new Thread(() -> {
            try {
                String deviceId = new DeviceIdentityManager(preferences).getOrCreateDeviceId();
                String modelProfile = RadioDeviceProfile.detectCurrent();
                new RadioConfigRepository(applicationContext).refresh(deviceId, modelProfile);
            } catch (IOException | JSONException | RuntimeException exception) {
                // Remote config is optional. The repository's embedded/cache fallback remains the
                // source of truth when the network is unavailable or the response is unsafe.
                Log.w(TAG, "Radio config refresh skipped; using local configuration ("
                        + exception.getClass().getSimpleName() + ")");
            }
        }, "minimum-radio-config").start();
    }
}
