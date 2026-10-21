// Ported from ADM's p3sim/clear/mobs/MobAi.kt
package com.teslamaps.adm.sim.clear.mobs;

public class AiState {
    public boolean chasing = false;
    public int unseen = 0;
    public boolean drawn = false;
    public int seeTime = 0;
    public int cooldown = 0;
    public int sinceTeleport = 0;
    public int loopIndex = 0;
    public double strollX = 0.0;
    public double strollZ = 0.0;
    public int strollLeft = 0;
}
