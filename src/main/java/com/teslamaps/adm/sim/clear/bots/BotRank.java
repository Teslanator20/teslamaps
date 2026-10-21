// Ported from ADM's p3sim/clear/bots/BotIdentity.kt
package com.teslamaps.adm.sim.clear.bots;

/** The rank a teammate shows in chat (and the colour of its name everywhere). */
public enum BotRank {
    MVP_PLUS("§b", "§b[MVP§c+§b]", 0.69),
    VIP("§a", "§a[VIP]", 0.12),
    VIP_PLUS("§a", "§a[VIP§6+§a]", 0.12),
    MVP_PLUS_PLUS("§6", "§6[MVP§c++§6]", 0.06);

    public final String nameColour;
    public final String bracket;
    public final double share;

    BotRank(String nameColour, String bracket, double share) {
        this.nameColour = nameColour;
        this.bracket = bracket;
        this.share = share;
    }
}
