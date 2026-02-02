package com.teslamaps.dungeon;

public enum DungeonState {
    NOT_IN_DUNGEON,
    STARTING,       // In dungeon lobby, not started yet
    IN_DUNGEON,     // Active dungeon run
    BOSS_FIGHT,     // In boss room
    COMPLETED       // Dungeon completed
}
