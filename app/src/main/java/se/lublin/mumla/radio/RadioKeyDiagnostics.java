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
import android.view.InputDevice;
import android.view.KeyEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Stores a small app-private hardware-key trace for T99/T88 commissioning. */
public final class RadioKeyDiagnostics {
    public static final String RELATIVE_LOG_PATH = "radio-diagnostics/key-events.log";
    private static final long MAX_LOG_BYTES = 32L * 1024L;
    private static final Object LOCK = new Object();

    private RadioKeyDiagnostics() {
    }

    public static void record(Context context, String path, KeyEvent event) {
        if (context == null || event == null
                || !RadioPttKeyManager.isRadioProfile(RadioDeviceProfile.detectCurrent())
                || !RadioPttKeyManager.isDiagnosticHardwareKey(event.getKeyCode())) {
            return;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 1) {
            return;
        }

        InputDevice device = event.getDevice();
        String deviceName = device == null ? "unknown" : sanitize(device.getName());
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN"
                : event.getAction() == KeyEvent.ACTION_UP ? "UP" : "OTHER";
        String line = String.format(Locale.ROOT,
                "eventTime=%d path=%s action=%s keyCode=%d keyName=%s scanCode=%d"
                        + " repeat=%d deviceId=%d source=0x%08X device=%s%n",
                event.getEventTime(), sanitize(path), action, event.getKeyCode(),
                KeyEvent.keyCodeToString(event.getKeyCode()), event.getScanCode(),
                event.getRepeatCount(), event.getDeviceId(), event.getSource(), deviceName);

        synchronized (LOCK) {
            File directory = new File(context.getFilesDir(), "radio-diagnostics");
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            File log = new File(directory, "key-events.log");
            boolean append = log.length() < MAX_LOG_BYTES;
            try (FileOutputStream stream = new FileOutputStream(log, append)) {
                stream.write(line.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Diagnostics must never interfere with PTT delivery or key release.
            }
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replace('\n', '_').replace('\r', '_').replace('\t', '_');
    }
}
