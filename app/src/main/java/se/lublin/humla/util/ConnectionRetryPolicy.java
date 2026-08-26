/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.humla.util;

/** Pure policy for deciding whether a disconnected service keeps retrying. */
public final class ConnectionRetryPolicy {
    private ConnectionRetryPolicy() {
    }

    public static boolean shouldRetry(boolean autoReconnect, boolean retryAllErrors,
                                      HumlaException.HumlaDisconnectReason reason) {
        return autoReconnect && reason != null
                && (retryAllErrors
                || reason == HumlaException.HumlaDisconnectReason.CONNECTION_ERROR);
    }

    public static long retryDelayMs(long baseDelayMs, int failedAttempts, long maximumDelayMs) {
        int exponent = Math.max(0, Math.min(failedAttempts, 20));
        long multiplier = 1L << exponent;
        if (baseDelayMs > maximumDelayMs / multiplier) {
            return maximumDelayMs;
        }
        return Math.min(baseDelayMs * multiplier, maximumDelayMs);
    }

    public static long remainingAttemptDelayMs(long minimumIntervalMs, long nowMs,
                                               long lastAttemptMs) {
        if (minimumIntervalMs <= 0L || lastAttemptMs < 0L || nowMs < lastAttemptMs) {
            return 0L;
        }
        long elapsedMs = nowMs - lastAttemptMs;
        return Math.max(0L, minimumIntervalMs - elapsedMs);
    }
}
