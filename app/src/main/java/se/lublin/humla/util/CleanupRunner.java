/*
 * Copyright (C) 2026 The Humla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.humla.util;

/** Runs every teardown action even when an earlier resource reports a runtime failure. */
public final class CleanupRunner {
    private CleanupRunner() {
    }

    public static RuntimeException runAll(Runnable... actions) {
        RuntimeException firstFailure = null;
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        return firstFailure;
    }
}
