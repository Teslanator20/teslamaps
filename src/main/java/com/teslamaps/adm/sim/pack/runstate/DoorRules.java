// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

import java.util.Map;

/** Door and key rules and tick constants. */
public class DoorRules {
    public final int volumeW, volumeH, volumeD, volumeY0;
    /** Ticks a door stays barrier before all 36 blocks turn to air (14). */
    public final int barrierTicks;
    public final double[] barrierTicksQ;
    /** Null when unmeasured: use {@code START_LEAD_TICKS} (20). */
    public final Integer startLeadTicks;
    public final Dist entranceFallAfterStart1;
    public final int freeFairyDoorWithinTicks;
    public final Map<Integer, Integer> witherPerLayoutCounts;
    public final Map<Integer, Integer> paidKeysPerRunCounts;
    public final Dist keyObtainToUse;
    public final double pickupInstantFrac;
    public final Dist pickupDelayed;
    public final int autoCollectTicks;
    public final double standWitherY, standBloodY;
    public final String standNameWither, standNameBlood;
    public final Dist standLifetime;
    public final String noKeyChat;
    public final String noKeySoundId;
    public final double noKeySoundVolume, noKeySoundPitch;
    public final Dist bloodKeyAfterStart;
    public final Dist bloodDoorAfterKey;
    public final JsonObject raw;

    public DoorRules(int volumeW, int volumeH, int volumeD, int volumeY0, int barrierTicks, double[] barrierTicksQ, Integer startLeadTicks,
                     Dist entranceFallAfterStart1, int freeFairyDoorWithinTicks, Map<Integer, Integer> witherPerLayoutCounts,
                     Map<Integer, Integer> paidKeysPerRunCounts, Dist keyObtainToUse, double pickupInstantFrac, Dist pickupDelayed,
                     int autoCollectTicks, double standWitherY, double standBloodY, String standNameWither, String standNameBlood,
                     Dist standLifetime, String noKeyChat, String noKeySoundId, double noKeySoundVolume, double noKeySoundPitch,
                     Dist bloodKeyAfterStart, Dist bloodDoorAfterKey, JsonObject raw) {
        this.volumeW = volumeW;
        this.volumeH = volumeH;
        this.volumeD = volumeD;
        this.volumeY0 = volumeY0;
        this.barrierTicks = barrierTicks;
        this.barrierTicksQ = barrierTicksQ;
        this.startLeadTicks = startLeadTicks;
        this.entranceFallAfterStart1 = entranceFallAfterStart1;
        this.freeFairyDoorWithinTicks = freeFairyDoorWithinTicks;
        this.witherPerLayoutCounts = witherPerLayoutCounts;
        this.paidKeysPerRunCounts = paidKeysPerRunCounts;
        this.keyObtainToUse = keyObtainToUse;
        this.pickupInstantFrac = pickupInstantFrac;
        this.pickupDelayed = pickupDelayed;
        this.autoCollectTicks = autoCollectTicks;
        this.standWitherY = standWitherY;
        this.standBloodY = standBloodY;
        this.standNameWither = standNameWither;
        this.standNameBlood = standNameBlood;
        this.standLifetime = standLifetime;
        this.noKeyChat = noKeyChat;
        this.noKeySoundId = noKeySoundId;
        this.noKeySoundVolume = noKeySoundVolume;
        this.noKeySoundPitch = noKeySoundPitch;
        this.bloodKeyAfterStart = bloodKeyAfterStart;
        this.bloodDoorAfterKey = bloodDoorAfterKey;
        this.raw = raw;
    }
}
