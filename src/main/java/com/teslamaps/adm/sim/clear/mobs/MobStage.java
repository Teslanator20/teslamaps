// Ported from ADM's p3sim/clear/mobs/MobStage.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.Collection;
import java.util.UUID;

public interface MobStage {
    default void place(MobEntry e, double x, double y, double z) {}
    boolean available();
    boolean isLoaded(int x, int z);
    boolean materialise(MobEntry e);
    boolean isGone(MobEntry e);
    void release(MobEntry e);
    void die(MobEntry e);
    void retract(MobEntry e);
    void announceTo(UUID playerId, Collection<MobEntry> entries);
    void wake(MobEntry e);
    void reveal(MobEntry e);
    void refresh(MobEntry e);
    void hurtFlash(MobEntry e);
    double[] position(MobEntry e);
    void act(MobEntry e, AiAction action, double targetY, double targetX, double targetZ);
    default void tick() {}
    default void flush() {}
    default boolean canSee(MobEntry e, double x, double y, double z) { return true; }
    void rescue(MobEntry e);
}
