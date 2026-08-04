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

import androidx.preference.PreferenceManager;

/** Narrow, shell-permission-protected ADB entry point for managed radio provisioning. */
public final class RadioProvisionReceiver extends BroadcastReceiver {
    public static final String ACTION_ASSIGN_DEVICE_PROFILE =
            "se.lublin.mumla.action.PROVISION_DEVICE_PROFILE";
    public static final String EXTRA_DEVICE_PROFILE = "deviceProfile";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (RadioLauncherShortcutInstaller.ACTION_PROVISION_SHORTCUT.equals(intent.getAction())) {
            RadioLauncherShortcutInstaller.ensureInstalled(
                    context,
                    PreferenceManager.getDefaultSharedPreferences(context),
                    true);
        } else if (ACTION_ASSIGN_DEVICE_PROFILE.equals(intent.getAction())) {
            String profile = intent.getStringExtra(EXTRA_DEVICE_PROFILE);
            if (DeviceIdentityManager.isValidDeviceId(profile)) {
                new DeviceIdentityManager(PreferenceManager.getDefaultSharedPreferences(context))
                        .setDeviceIdForAdmin(profile);
            }
        }
    }
}
