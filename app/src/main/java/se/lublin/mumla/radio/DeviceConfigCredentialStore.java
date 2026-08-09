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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Stores the per-device config bearer credential outside the normal backup-able preferences. */
public final class DeviceConfigCredentialStore {
    static final int MAX_CREDENTIAL_BYTES = 4096;
    private static final String CREDENTIAL_FILE = "device-config-credential";
    private static final String TEMP_FILE = "device-config-credential.tmp";

    private final File directory;
    private final Object lock = new Object();

    public DeviceConfigCredentialStore(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext == null) {
            applicationContext = context;
        }
        File noBackupDirectory = applicationContext.getNoBackupFilesDir();
        if (noBackupDirectory == null) {
            noBackupDirectory = applicationContext.getFilesDir();
        }
        if (noBackupDirectory == null) {
            throw new IllegalStateException("app-private credential directory is unavailable");
        }
        directory = new File(noBackupDirectory, "radio-config");
    }

    static DeviceConfigCredentialStore forDirectory(File directory) {
        if (directory == null) {
            throw new IllegalArgumentException("credential directory must not be null");
        }
        return new DeviceConfigCredentialStore(directory, true);
    }

    private DeviceConfigCredentialStore(File directory, boolean ignored) {
        this.directory = directory;
    }

    /** Returns the raw token, or null when no credential has been provisioned. */
    public String getCredential() throws IOException {
        synchronized (lock) {
            File credential = new File(directory, CREDENTIAL_FILE);
            if (!credential.isFile()) {
                return null;
            }
            return normalizeCredential(readLimited(credential));
        }
    }

    /** Returns the HTTP Authorization value without exposing the token to callers that log it. */
    public String getAuthorizationHeader() throws IOException {
        String credential = getCredential();
        return credential == null ? null : "Bearer " + credential;
    }

    /** Atomically installs or rotates the credential after validating header-safe characters. */
    public void setCredential(String credential) throws IOException {
        writeCredential(normalizeCredential(credential));
    }

    /** Installs a credential from a bounded protected provisioning stream. */
    public void setCredential(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("credential input must not be null");
        }
        writeCredential(normalizeCredential(readLimited(input)));
    }

    private void writeCredential(String normalized) throws IOException {
        synchronized (lock) {
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("cannot create app-private credential directory");
            }
            File temporary = new File(directory, TEMP_FILE);
            File target = new File(directory, CREDENTIAL_FILE);
            deleteIfPresent(temporary);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(normalized.getBytes(StandardCharsets.US_ASCII));
                output.getFD().sync();
            }
            deleteIfPresent(target);
            if (!temporary.renameTo(target)) {
                deleteIfPresent(temporary);
                throw new IOException("cannot install app-private credential");
            }
        }
    }

    /** Removes the credential without changing active, pending, or previous radio config. */
    public void clearCredential() throws IOException {
        synchronized (lock) {
            deleteIfPresent(new File(directory, CREDENTIAL_FILE));
            deleteIfPresent(new File(directory, TEMP_FILE));
        }
    }

    static String normalizeCredential(String credential) {
        if (credential == null) {
            throw new IllegalArgumentException("device config credential is missing");
        }
        String normalized = credential.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            normalized = normalized.substring("Bearer ".length()).trim();
        }
        if (normalized.isEmpty() || normalized.length() > MAX_CREDENTIAL_BYTES) {
            throw new IllegalArgumentException("invalid device config credential");
        }
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                throw new IllegalArgumentException("invalid device config credential");
            }
        }
        return normalized;
    }

    private static String readLimited(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return readLimited(input);
        }
    }

    private static String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_CREDENTIAL_BYTES) {
                throw new IOException("device config credential is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.US_ASCII.name());
    }

    private static void deleteIfPresent(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("cannot replace app-private credential");
        }
    }
}
