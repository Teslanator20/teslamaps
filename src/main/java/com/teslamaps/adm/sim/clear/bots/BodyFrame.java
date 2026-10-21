// Ported from ADM's p3sim/clear/bots/BotMotion.kt
package com.teslamaps.adm.sim.clear.bots;

/** What one tick of a bot's body shows besides where it is. [item] is the key of the new main-hand item, or null when it did not change. */
public class BodyFrame {
    public final boolean sprint;
    public final boolean crouch;
    public final float bodyYaw;
    public final float headYaw;
    public final boolean swing;
    public final String item;

    public BodyFrame(boolean sprint, boolean crouch, float bodyYaw, float headYaw, boolean swing, String item) {
        this.sprint = sprint;
        this.crouch = crouch;
        this.bodyYaw = bodyYaw;
        this.headYaw = headYaw;
        this.swing = swing;
        this.item = item;
    }
}
