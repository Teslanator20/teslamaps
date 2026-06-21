/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.screen;

public class AppleColors {
    public static final int BACKGROUND = 0xFF1C1C1E;           // Dark background
    public static final int CARD_BACKGROUND = 0xFF2C2C2E;      // Dark card background
    public static final int CARD_SHADOW = 0x40000000;          // Shadow (25% black)

    public static final int TEXT_PRIMARY = 0xFFFFFFFF;         // White text
    public static final int TEXT_SECONDARY = 0xFF8E8E93;       // Gray text
    public static final int TEXT_TERTIARY = 0xFF636366;        // Darker gray text

    public static final int ACCENT_BLUE = 0xFF0A84FF;          // Bright blue
    public static final int ACCENT_GREEN = 0xFF30D158;         // Bright green
    public static final int ACCENT_RED = 0xFFFF453A;           // Bright red

    public static final int TOGGLE_ON = 0xFF30D158;            // Green
    public static final int TOGGLE_OFF = 0xFF39393D;           // Dark gray
    public static final int TOGGLE_KNOB = 0xFFFFFFFF;          // White knob

    public static final int INPUT_BACKGROUND = 0xFF1C1C1E;     // Dark input bg
    public static final int INPUT_BORDER = 0xFF48484A;         // Border gray
    public static final int INPUT_FOCUSED = 0xFF0A84FF;        // Blue when focused

    public static final int SEPARATOR = 0xFF48484A;            // Separator line

    public static int interpolate(int colorFrom, int colorTo, float progress) {
        int aFrom = (colorFrom >> 24) & 0xFF;
        int rFrom = (colorFrom >> 16) & 0xFF;
        int gFrom = (colorFrom >> 8) & 0xFF;
        int bFrom = colorFrom & 0xFF;

        int aTo = (colorTo >> 24) & 0xFF;
        int rTo = (colorTo >> 16) & 0xFF;
        int gTo = (colorTo >> 8) & 0xFF;
        int bTo = colorTo & 0xFF;

        int a = (int) (aFrom + (aTo - aFrom) * progress);
        int r = (int) (rFrom + (rTo - rFrom) * progress);
        int g = (int) (gFrom + (gTo - gFrom) * progress);
        int b = (int) (bFrom + (bTo - bFrom) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, float alpha) {
        int a = (int) (255 * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
