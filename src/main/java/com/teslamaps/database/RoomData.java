package com.teslamaps.database;

import java.util.List;

public class RoomData {
    private String name;
    private String type;
    private int secrets;
    private List<Integer> cores;
    private int roomID;
    private String clear;
    private int crypts;
    private String shape;
    private String doors;
    private boolean prince;

    private Integer clearScore;
    private Integer secretScore;
    private Integer roomScore;

    public RoomData() {}

    public RoomData(String name, String type, int secrets, List<Integer> cores, int roomID, String shape) {
        this.name = name;
        this.type = type;
        this.secrets = secrets;
        this.cores = cores;
        this.roomID = roomID;
        this.shape = shape;
        this.clear = "mob";
    }

    public void setCores(List<Integer> cores) {
        this.cores = cores;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getSecrets() {
        return secrets;
    }

    public List<Integer> getCores() {
        return cores;
    }

    public int getRoomID() {
        return roomID;
    }

    public String getClear() {
        return clear;
    }

    public int getCrypts() {
        return crypts;
    }

    public String getShape() {
        return shape;
    }

    public String getDoors() {
        return doors;
    }

    public boolean getPrince() {
        return prince;
    }

    public Integer getClearScore() {
        return clearScore;
    }

    public Integer getSecretScore() {
        return secretScore;
    }

    public Integer getRoomScore() {
        return roomScore;
    }

    @Override
    public String toString() {
        return "RoomData{name='" + name + "', type='" + type + "', secrets=" + secrets + ", shape='" + shape + "'}";
    }
}
