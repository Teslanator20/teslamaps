// Ported from ADM's p3sim/clear/hud/ActionBarModel.kt
package com.teslamaps.adm.sim.clear.hud;

import java.util.UUID;

/** One extra piece of the action bar, set through `ChannelService.actionBarSegment`. */
public class BarSegment {
    public final String key;
    public final String text;
    public final int priority;
    public final int expiresTick;
    public final UUID audience;
    public final int setTick;

    public BarSegment(String key, String text, int priority, int expiresTick, UUID audience, int setTick) {
        this.key = key;
        this.text = text;
        this.priority = priority;
        this.expiresTick = expiresTick;
        this.audience = audience;
        this.setTick = setTick;
    }
}
