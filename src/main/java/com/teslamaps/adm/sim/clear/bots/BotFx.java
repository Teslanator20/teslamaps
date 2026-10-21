// Ported from ADM's p3sim/clear/bots/BotWorldImpl.kt
package com.teslamaps.adm.sim.clear.bots;

/** What a bot's kill looks and sounds like from outside. Called on the server thread. */
@FunctionalInterface
public interface BotFx {
    /** [seat] killed a mob at ([x], [y], [z]). */
    void killed(int seat, double x, double y, double z);
}
