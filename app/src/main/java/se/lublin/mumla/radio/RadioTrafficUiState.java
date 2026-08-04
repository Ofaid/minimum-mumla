/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import java.util.List;

/** Immutable visible RX state resolved from the service-owned talker snapshot. */
public final class RadioTrafficUiState {
    public enum Kind {
        READY,
        SINGLE_TALKER,
        MULTIPLE_TALKERS
    }

    private final Kind kind;
    private final String talker;

    private RadioTrafficUiState(Kind kind, String talker) {
        this.kind = kind;
        this.talker = talker;
    }

    public static RadioTrafficUiState from(List<String> activeTalkers) {
        if (activeTalkers == null || activeTalkers.isEmpty()) {
            return new RadioTrafficUiState(Kind.READY, "");
        }
        if (activeTalkers.size() == 1) {
            String talker = activeTalkers.get(0);
            return new RadioTrafficUiState(Kind.SINGLE_TALKER,
                    talker == null ? "" : talker);
        }
        return new RadioTrafficUiState(Kind.MULTIPLE_TALKERS, "");
    }

    public Kind getKind() {
        return kind;
    }

    public String getTalker() {
        return talker;
    }
}
