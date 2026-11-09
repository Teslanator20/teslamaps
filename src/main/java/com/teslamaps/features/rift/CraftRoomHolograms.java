package com.teslamaps.features.rift;

import com.teslamaps.utils.HypixelModApi;

public final class CraftRoomHolograms {

    private CraftRoomHolograms() {}

    static boolean inRift() {
        String mode = HypixelModApi.mode();
        if (mode != null) return mode.equalsIgnoreCase("rift");
        return RiftSpot.inRift();
    }
}
