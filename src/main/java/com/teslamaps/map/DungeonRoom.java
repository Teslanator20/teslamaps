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
}
