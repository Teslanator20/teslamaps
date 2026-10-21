// Ported from ADM's p3sim/clear/bots/BotEffects.kt
package com.teslamaps.adm.sim.clear.bots;

/** The sounds a teammate's footstep can be. Every one is played at volume 0.15, pitch 1.0 on the blocks channel. */
public enum StepSound {
    GLASS("block.glass.place"), GRAVEL("block.gravel.step"), WOOL("block.wool.step"), WOOD("block.wood.step"), GRASS("block.grass.step");

    public final String sound;

    StepSound(String sound) { this.sound = sound; }
}
