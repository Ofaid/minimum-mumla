/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.view.KeyEvent;

/** Timing and classification rules for deliberate radio-shell hardware actions. */
public final class RadioKeyActionPolicy {
    public static final long EXIT_HOLD_MS = 5_000L;
    public static final long ROOM_CHANGE_HOLD_MS = 1_000L;

    private RadioKeyActionPolicy() {
    }

    /**
     * T99 labels do not match Android key names: MENU is DPAD_CENTER, EXIT is F2 and
     * the red key is BACK. All three must be held to prevent an accidental dashboard exit.
     */
    public static boolean isProtectedExitKey(String profile, int keyCode) {
        return RadioDeviceProfile.T99.equals(profile)
                && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_F2
                || keyCode == KeyEvent.KEYCODE_BACK);
    }

    public static boolean isRoomChangeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    public static int roomDirection(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return -1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return 1;
        }
        return 0;
    }

    public static boolean heldLongEnough(long startedAt, long endedAt, long requiredDuration) {
        return startedAt >= 0L && endedAt >= startedAt
                && endedAt - startedAt >= requiredDuration;
    }
}
