/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import se.lublin.mumla.Settings;

/** Applies radio defaults and centralizes the multi-key PTT contract. */
public final class RadioPttKeyManager {
    private RadioPttKeyManager() {
    }

    /** Makes a managed radio usable without requiring an operator to visit Mumla settings. */
    public static void applyDefaults(SharedPreferences preferences) {
        String profile = RadioDeviceProfile.detectCurrent();
        if (!isRadioProfile(profile)) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putString(Settings.PREF_INPUT_METHOD, Settings.ARRAY_INPUT_METHOD_PTT)
                .putBoolean(Settings.PREF_PTT_TOGGLE, false)
                .putBoolean(Settings.PREF_AUTO_RECONNECT, true)
                .putBoolean(Settings.PREF_PREPROCESSOR_ENABLED, true)
                .putBoolean(Settings.PREF_HALF_DUPLEX, true)
                .putBoolean(Settings.PREF_USE_TTS, true)
                .putBoolean(Settings.PREF_PTT_SOUND, true);
        if (!preferences.contains(Settings.PREF_PUSH_KEY)) {
            editor.putInt(Settings.PREF_PUSH_KEY, KeyEvent.KEYCODE_F1);
        }
        editor.apply();
    }

    /** Returns true for the primary configured key and supported radio profile defaults. */
    public static boolean isConfiguredPttKey(int keyCode, Settings settings) {
        if (settings != null && keyCode == settings.getPushToTalkKey()) {
            return true;
        }
        String profile = RadioDeviceProfile.detectCurrent();
        if (!isRadioProfile(profile)) {
            return false;
        }
        return keyCode == KeyEvent.KEYCODE_F1
                || keyCode == KeyEvent.KEYCODE_F2
                || isMediaStyleKey(keyCode);
    }

    public static boolean isMediaStyleKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
    }

    public static boolean isRadioProfile(String profile) {
        return RadioDeviceProfile.T99.equals(profile) || RadioDeviceProfile.T88.equals(profile);
    }
}
