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
