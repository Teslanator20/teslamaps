package com.teslamaps.map;

public enum CheckmarkState {
    // Order matters! UNEXPLORED must be first (ordinal 0) so all other states have higher ordinals
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
