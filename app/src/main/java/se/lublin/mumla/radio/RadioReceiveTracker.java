/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import java.util.HashSet;
import java.util.Set;

import se.lublin.humla.model.TalkState;

/** Service-owned remote RX state used by config activation safety checks. */
public final class RadioReceiveTracker {
    private final Set<Integer> activeSessions = new HashSet<>();

    public synchronized void update(int session, boolean self, TalkState state) {
        if (self || state == null || state == TalkState.PASSIVE) {
            activeSessions.remove(session);
        } else {
            activeSessions.add(session);
        }
    }

    public synchronized void remove(int session) {
        activeSessions.remove(session);
    }

    public synchronized void clear() {
        activeSessions.clear();
    }

    public synchronized boolean isReceiving() {
        return !activeSessions.isEmpty();
    }
}
