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

public enum DoorType {
    NONE(0x00000000),
    NORMAL(0x808080FF),
    WITHER(0x000000FF),
    BLOOD(0xFF0000FF),
    ENTRANCE(0x00FF00FF);

    private final int color;

    DoorType(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public static DoorType fromInt(int type) {
        return switch (type) {
            case 0 -> NORMAL;
            case 1 -> WITHER;
            case 2 -> BLOOD;
            case 3 -> ENTRANCE;
            default -> NONE;
        };
    }
}
