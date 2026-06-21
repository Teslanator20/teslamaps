/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.map;

public enum CheckmarkState {
    UNEXPLORED(0x404040FF),  // Darkened/unexplored - ordinal 0
    NONE(0xFFFFFFFF),        // Room visited, no checkmark - ordinal 1
    WHITE(0xFFFFFFFF),       // Cleared - ordinal 2
    GREEN(0x00FF00FF),       // All secrets found - ordinal 3
    FAILED(0xFF0000FF);      // Failed - ordinal 4

    private final int color;

    CheckmarkState(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
