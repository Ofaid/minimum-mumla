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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Narrow, shell-permission-protected ADB entry point for managed radio provisioning. */
public final class RadioProvisionReceiver extends BroadcastReceiver {
    public static final String ACTION_ASSIGN_DEVICE_PROFILE =
            "se.lublin.mumla.action.PROVISION_DEVICE_PROFILE";
    public static final String ACTION_REPORT_IDENTITY =
            "se.lublin.mumla.action.PROVISION_REPORT_IDENTITY";
    public static final String ACTION_INSTALL_RADIO_CONFIG =
            "se.lublin.mumla.action.PROVISION_RADIO_CONFIG";
    public static final String ACTION_INSTALL_DEVICE_CONFIG_CREDENTIAL =
            "se.lublin.mumla.action.PROVISION_DEVICE_CONFIG_CREDENTIAL";
    public static final String ACTION_SET_APRS_OBJECT_NAME =
            "se.lublin.mumla.action.PROVISION_APRS_OBJECT_NAME";
    public static final String EXTRA_DEVICE_PROFILE = "deviceProfile";
    public static final String EXTRA_CONFIG_PATH = "configPath";
    public static final String EXTRA_DEVICE_CONFIG_CREDENTIAL = "deviceConfigCredential";
    public static final String EXTRA_DEVICE_CONFIG_CREDENTIAL_PATH =
            "deviceConfigCredentialPath";
    public static final String EXTRA_APRS_OBJECT_NAME = "objectName";

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
        } else if (ACTION_REPORT_IDENTITY.equals(intent.getAction())) {
            String deviceId = new DeviceIdentityManager(
                    PreferenceManager.getDefaultSharedPreferences(context)).getOrCreateDeviceId();
            setResultCode(-1);
            setResultData(deviceId);
        } else if (ACTION_INSTALL_RADIO_CONFIG.equals(intent.getAction())) {
            String credential = intent.getStringExtra(EXTRA_DEVICE_CONFIG_CREDENTIAL);
            String credentialPath = intent.getStringExtra(EXTRA_DEVICE_CONFIG_CREDENTIAL_PATH);
            if (credential != null || credentialPath != null) {
                installDeviceConfigCredential(context, credential, credentialPath);
            } else {
                installRadioConfig(context, intent.getStringExtra(EXTRA_CONFIG_PATH));
            }
        } else if (ACTION_INSTALL_DEVICE_CONFIG_CREDENTIAL.equals(intent.getAction())) {
            installDeviceConfigCredential(context,
                    intent.getStringExtra(EXTRA_DEVICE_CONFIG_CREDENTIAL),
                    intent.getStringExtra(EXTRA_DEVICE_CONFIG_CREDENTIAL_PATH));
        } else if (ACTION_SET_APRS_OBJECT_NAME.equals(intent.getAction())) {
            updateAprsObjectName(context, intent.getStringExtra(EXTRA_APRS_OBJECT_NAME));
        }
    }

    private void updateAprsObjectName(Context context, String objectName) {
        setResultCode(0);
        setResultData("rejected");
        try {
            String deviceId = new DeviceIdentityManager(
                    PreferenceManager.getDefaultSharedPreferences(context)).getOrCreateDeviceId();
            new RadioConfigRepository(context).updateActiveAprsObjectName(deviceId, objectName);
            setResultCode(-1);
            setResultData("updated");
        } catch (IOException | RuntimeException | org.json.JSONException ignored) {
            // The narrow provisioning result never exposes config content or credentials.
        }
    }

    private void installRadioConfig(Context context, String requestedPath) {
        setResultCode(0);
        setResultData("rejected");
        if (requestedPath == null) {
            return;
        }
        try {
            File configFile = new File(requestedPath).getCanonicalFile();
            File parent = configFile.getParentFile();
            if (parent == null || !"/data/local/tmp".equals(parent.getCanonicalPath())
                    || !configFile.getName().startsWith("minimum-radio-config-")
                    || !configFile.getName().endsWith(".json")) {
                return;
            }
            String deviceId = new DeviceIdentityManager(
                    PreferenceManager.getDefaultSharedPreferences(context)).getOrCreateDeviceId();
            try (FileInputStream input = new FileInputStream(configFile)) {
                new RadioConfigRepository(context).installProvisionedActive(input, deviceId);
            }
            setResultCode(-1);
            setResultData("installed");
        } catch (IOException | RuntimeException | org.json.JSONException ignored) {
            // Provisioning failures are returned without logging config content or credentials.
        }
    }

    private void installDeviceConfigCredential(Context context, String credential,
                                               String requestedPath) {
        setResultCode(0);
        setResultData("rejected");
        if (credential == null && requestedPath == null) {
            return;
        }
        try {
            DeviceConfigCredentialStore store = new DeviceConfigCredentialStore(context);
            if (credential != null) {
                store.setCredential(credential);
            } else {
                File credentialFile = new File(requestedPath).getCanonicalFile();
                File parent = credentialFile.getParentFile();
                if (parent == null || !"/data/local/tmp".equals(parent.getCanonicalPath())
                        || !credentialFile.getName().startsWith("minimum-device-credential-")
                        || !credentialFile.getName().endsWith(".txt")) {
                    return;
                }
                try (FileInputStream input = new FileInputStream(credentialFile)) {
                    store.setCredential(input);
                }
            }
            RadioConfigUpdater.scheduleNow(context);
            setResultCode(-1);
            setResultData("credential-installed");
        } catch (IOException | RuntimeException ignored) {
            // Credential failures never expose the supplied token or its contents.
        }
    }
}
