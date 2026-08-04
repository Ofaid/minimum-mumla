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

    /**
     * Makes a supported radio usable on first launch without requiring a user to visit Settings.
     * Existing explicit preferences are preserved so a user can still opt out.
     */
    public static void applyDefaults(SharedPreferences preferences) {
        String profile = RadioDeviceProfile.detectCurrent();
        if (!isRadioProfile(profile)) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        if (!preferences.contains(Settings.PREF_INPUT_METHOD)) {
            editor.putString(Settings.PREF_INPUT_METHOD, Settings.ARRAY_INPUT_METHOD_PTT);
            changed = true;
        }
        if (!preferences.contains(Settings.PREF_PUSH_KEY)) {
            editor.putInt(Settings.PREF_PUSH_KEY, KeyEvent.KEYCODE_F1);
            changed = true;
        }
        if (!preferences.contains(Settings.PREF_PTT_TOGGLE)) {
            editor.putBoolean(Settings.PREF_PTT_TOGGLE, false);
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
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
