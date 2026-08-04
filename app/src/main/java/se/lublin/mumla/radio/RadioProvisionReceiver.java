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

/** Narrow ADB provisioning entry point for installing the Launcher3 recovery shortcut. */
public final class RadioProvisionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!RadioLauncherShortcutInstaller.ACTION_PROVISION_SHORTCUT.equals(intent.getAction())) {
            return;
        }
        RadioLauncherShortcutInstaller.ensureInstalled(
                context,
                PreferenceManager.getDefaultSharedPreferences(context),
                true);
    }
}
