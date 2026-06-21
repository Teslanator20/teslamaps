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

import com.teslamaps.database.RoomData;

import java.util.ArrayList;
import java.util.List;

public class DungeonRoom {
    private String name;
    private RoomType type;
    private int secrets;       // Max secrets (from database)
    private int foundSecrets = -1;  // Secrets found (-1 = unknown)
    private String shape;
    private CheckmarkState checkmarkState = CheckmarkState.UNEXPLORED;
    private boolean explored = false;
    private int rotation = -1;  // Room rotation: 0, 90, 180, 270 degrees (-1 = not detected)
    private int cornerX = 0;    // Corner X (blue terracotta position)
    private int cornerZ = 0;    // Corner Z (blue terracotta position)

    private final List<int[]> components = new ArrayList<>();

    private final DoorType[] doors = new DoorType[4];

    private RoomData roomData;

    public DungeonRoom() {
        for (int i = 0; i < 4; i++) {
            doors[i] = DoorType.NONE;
        }
    }

    public DungeonRoom(int gridX, int gridZ) {
        this();
        addComponent(gridX, gridZ);
    }

    public void loadFromRoomData(RoomData data) {
        this.roomData = data;
        this.name = data.getName();
        this.type = RoomType.fromString(data.getType());
        this.secrets = data.getSecrets();
        this.shape = data.getShape();
    }

    public void addComponent(int gridX, int gridZ) {
        for (int[] comp : components) {
            if (comp[0] == gridX && comp[1] == gridZ) {
                return;
            }
        }
        components.add(new int[]{gridX, gridZ});
    }

    public boolean hasComponent(int gridX, int gridZ) {
        for (int[] comp : components) {
            if (comp[0] == gridX && comp[1] == gridZ) {
                return true;
            }
        }
        return false;
    }

    public List<int[]> getComponents() {
        return components;
    }

    public int[] getPrimaryComponent() {
        return components.isEmpty() ? new int[]{0, 0} : components.get(0);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RoomType getType() {
        return type;
    }

    public void setType(RoomType type) {
        this.type = type;
    }

    public int getSecrets() {
        return secrets;
    }

    public void setSecrets(int secrets) {
        this.secrets = secrets;
    }

    public int getFoundSecrets() {
        return foundSecrets;
    }

    public void setFoundSecrets(int foundSecrets) {
        this.foundSecrets = foundSecrets;
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public CheckmarkState getCheckmarkState() {
        return checkmarkState;
    }

    public void setCheckmarkState(CheckmarkState checkmarkState) {
        this.checkmarkState = checkmarkState;
    }

    public boolean isExplored() {
        return explored;
    }

    public void setExplored(boolean explored) {
        this.explored = explored;
        if (explored && checkmarkState == CheckmarkState.UNEXPLORED) {
            checkmarkState = CheckmarkState.NONE;
        }
    }

    public DoorType getDoor(int direction) {
        if (direction < 0 || direction >= 4) return DoorType.NONE;
        return doors[direction];
    }

    public void setDoor(int direction, DoorType doorType) {
        if (direction >= 0 && direction < 4) {
            doors[direction] = doorType;
        }
    }

    public RoomData getRoomData() {
        return roomData;
    }

    public int getCrypts() {
        return roomData != null ? roomData.getCrypts() : 0;
    }

    public boolean isIdentified() {
        return roomData != null;
    }

    public net.minecraft.core.BlockPos relativeToActual(net.minecraft.core.BlockPos relative) {
        int[] primary = getPrimaryComponent();
        return com.teslamaps.scanner.ComponentGrid.relativeToActual(primary[0], primary[1], relative);
    }

    public net.minecraft.core.BlockPos actualToRelative(net.minecraft.core.BlockPos actual) {
        int[] primary = getPrimaryComponent();
        return com.teslamaps.scanner.ComponentGrid.actualToRelative(primary[0], primary[1], actual);
    }

    public net.minecraft.core.BlockPos getCorner() {
        int[] primary = getPrimaryComponent();
        int[] corner = com.teslamaps.scanner.ComponentGrid.gridToWorldCorner(primary[0], primary[1]);
        return new net.minecraft.core.BlockPos(corner[0], 0, corner[1]);
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public boolean hasRotation() {
        return rotation >= 0;
    }

    public void setCorner(int x, int z) {
        this.cornerX = x;
        this.cornerZ = z;
    }

    public int getCornerX() {
        return cornerX;
    }

    public int getCornerZ() {
        return cornerZ;
    }
}
