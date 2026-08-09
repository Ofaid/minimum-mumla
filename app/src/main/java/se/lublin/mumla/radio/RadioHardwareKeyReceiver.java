/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import se.lublin.mumla.service.MumlaService;

/** Receives the T56 firmware PTT broadcasts that remain available while keyguard owns the key. */
public final class RadioHardwareKeyReceiver extends BroadcastReceiver {
    public static final String ACTION_T56_PTT_DOWN = "unipro.hotkey.ptt.down";
    public static final String ACTION_T56_PTT_UP = "unipro.hotkey.ptt.up";
    public static final String ACTION_T56_IDENTITY_LONG = "unipro.hotkey.p2.long";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !RadioDeviceProfile.T56.equals(RadioDeviceProfile.detectCurrent())) {
            return;
        }
        if (ACTION_T56_IDENTITY_LONG.equals(intent.getAction())) {
            try {
                context.startActivity(new Intent(context, RadioShellActivity.class)
                        .putExtra(RadioShellActivity.EXTRA_TOGGLE_IDENTITY, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (RuntimeException ignored) {
                // The next deliberate hold can retry after a transient Activity-start failure.
            }
            return;
        }
        String serviceAction;
        if (ACTION_T56_PTT_DOWN.equals(intent.getAction())) {
            serviceAction = MumlaService.ACTION_RADIO_PTT_DOWN;
        } else if (ACTION_T56_PTT_UP.equals(intent.getAction())) {
            serviceAction = MumlaService.ACTION_RADIO_PTT_UP;
        } else {
            return;
        }
        try {
            context.startService(new Intent(context, MumlaService.class).setAction(serviceAction));
        } catch (RuntimeException ignored) {
            // The next hardware press can retry after a transient service-start failure.
        }
    }
}
