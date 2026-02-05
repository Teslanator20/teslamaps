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

    // Grid components this room occupies (for multi-component rooms like L-shapes)
    private final List<int[]> components = new ArrayList<>();

    // Doors: index 0=North, 1=South, 2=West, 3=East
    private final DoorType[] doors = new DoorType[4];

    // Room data from database
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
        // Check if component already exists
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

    // Getters and setters
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

    /**
     * Check if this room has been identified (matched to a known room in database).
     */
    public boolean isIdentified() {
        return roomData != null;
    }

    /**
     * Convert room-relative coordinates to actual world coordinates.
     * Uses the room's primary component for conversion.
     */
    public net.minecraft.util.math.BlockPos relativeToActual(net.minecraft.util.math.BlockPos relative) {
        int[] primary = getPrimaryComponent();
        return com.teslamaps.scanner.ComponentGrid.relativeToActual(primary[0], primary[1], relative);
    }

    /**
     * Convert actual world coordinates to room-relative coordinates.
     * Uses the room's primary component for conversion.
     */
    public net.minecraft.util.math.BlockPos actualToRelative(net.minecraft.util.math.BlockPos actual) {
        int[] primary = getPrimaryComponent();
        return com.teslamaps.scanner.ComponentGrid.actualToRelative(primary[0], primary[1], actual);
    }

    /**
     * Get the room corner as a BlockPos (for puzzle solvers).
     * Returns the world position of the room's northwest corner at Y=0.
     */
    public net.minecraft.util.math.BlockPos getCorner() {
        int[] primary = getPrimaryComponent();
        int[] corner = com.teslamaps.scanner.ComponentGrid.gridToWorldCorner(primary[0], primary[1]);
        return new net.minecraft.util.math.BlockPos(corner[0], 0, corner[1]);
    }

    /**
     * Get the room rotation in degrees (0, 90, 180, 270).
     * -1 means rotation has not been detected yet.
     */
    public int getRotation() {
        return rotation;
    }

    /**
     * Set the room rotation in degrees (0, 90, 180, 270).
     */
    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    /**
     * Check if rotation has been detected.
     */
    public boolean hasRotation() {
        return rotation >= 0;
    }

    /**
     * Set the corner position (blue terracotta location).
     */
    public void setCorner(int x, int z) {
        this.cornerX = x;
        this.cornerZ = z;
    }

    /**
     * Get the corner X coordinate.
     */
    public int getCornerX() {
        return cornerX;
    }

    /**
     * Get the corner Z coordinate.
     */
    public int getCornerZ() {
        return cornerZ;
    }
}
