// Ported from ADM's p3sim/clear/secrets/SecretsSettings.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.settings.BoolSetting;
import com.teslamaps.adm.sim.settings.NumSetting;
import com.teslamaps.adm.sim.settings.SimSettings;

public final class SecretsSettings {
    private SecretsSettings() {}

    public static final double ITEM_SPAWN_RANGE = 32.0;
    public static final double BAT_SPAWN_RANGE = 14.0;
    public static final double SKULL_SPAWN_RANGE = 16.0;
    public static final double PICKUP_RANGE = 2.25;
    public static final double SUPERBOOM_RADIUS = 3.0;
    public static final int LEVER_WALL_TICKS = 25;
    public static final int BONUS_BAT_PERCENT = 100;

    private static NumSetting itemRangeS = null;
    private static NumSetting batRangeS = null;
    private static NumSetting skullRangeS = null;
    private static NumSetting pickupRangeS = null;
    private static NumSetting boomRadiusS = null;
    private static NumSetting leverTicksS = null;
    private static BoolSetting autoCreditS = null;
    private static NumSetting bonusBatS = null;
    private static BoolSetting blessingBoostS = null;

    public static void register() {
        itemRangeS = SimSettings.num("Secrets Item Range", ITEM_SPAWN_RANGE, 8.0, 64.0, 1.0, " blocks", "How near (sideways) a party member must be before an item secret lies on the floor.");
        batRangeS = SimSettings.num("Secrets Bat Range", BAT_SPAWN_RANGE, 4.0, 64.0, 1.0, " blocks", "How near (sideways) a party member must be before a secret bat appears.");
        skullRangeS = SimSettings.num("Secrets Skull Range", SKULL_SPAWN_RANGE, 4.0, 64.0, 1.0, " blocks", "How near (sideways) a party member must be before a wither essence or key skull appears.");
        pickupRangeS = SimSettings.num("Secrets Pickup Range", PICKUP_RANGE, 0.0, 5.0, 0.5, " blocks", "How far past your body (sideways) you reach to pick up an item secret. Hypixel's is about 2.25 (recorded); the game's own is 1.");
        boomRadiusS = SimSettings.num("Secrets Superboom Radius", SUPERBOOM_RADIUS, 1.0, 8.0, 0.5, " blocks", "How far a Superboom TNT reaches: a crypt or secret wall with any block this close to the blast is destroyed whole.");
        leverTicksS = SimSettings.num("Secrets Lever Wall Time", LEVER_WALL_TICKS, 0, 100, 1, " ticks", "How long a lever's wall stays an invisible barrier before it opens for good.");
        autoCreditS = SimSettings.bool("Secrets Auto Credit", true, "A room whose secrets the room data cannot all place counts the missing ones when you find the last one it does place, so the room can still turn green.");
        bonusBatS = SimSettings.num("Secrets Bonus Bat Chance", BONUS_BAT_PERCENT, 0, 100, 1, "%", "How often a run has a bonus bat at all: one of the first few secret bats killed then also counts a point of bonus score.");
        blessingBoostS = SimSettings.bool("Blessing Boost x1.25", false, "Hypixel has days when every blessing is 25 percent stronger (Power V reads +36.3 & 1.18x instead of +29 & 1.15x). Turn this on to practise a boosted day.");
    }

    public static double itemRange() { return itemRangeS != null ? itemRangeS.get() : ITEM_SPAWN_RANGE; }
    public static double batRange() { return batRangeS != null ? batRangeS.get() : BAT_SPAWN_RANGE; }
    public static double skullRange() { return skullRangeS != null ? skullRangeS.get() : SKULL_SPAWN_RANGE; }
    public static double pickupRange() { return pickupRangeS != null ? pickupRangeS.get() : PICKUP_RANGE; }
    public static double superboomRadius() { return boomRadiusS != null ? boomRadiusS.get() : SUPERBOOM_RADIUS; }
    public static int leverWallTicks() { return leverTicksS != null ? leverTicksS.getInt() : LEVER_WALL_TICKS; }
    public static boolean autoCredit() { return autoCreditS != null ? autoCreditS.get() : true; }

    public static double blessingBoost() {
        Boolean rb = replayBoost;
        boolean on = rb != null ? rb : (blessingBoostS != null && blessingBoostS.get());
        return on ? Blessings.BOOST : 1.0;
    }

    /** A replay's boost, as its recording shows it, over the setting; null outside a replay. */
    public static volatile Boolean replayBoost = null;

    public static int bonusBatPercent() { return bonusBatS != null ? bonusBatS.getInt() : BONUS_BAT_PERCENT; }
}
