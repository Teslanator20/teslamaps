package com.teslamaps.features.mobvisuals;

import com.teslamaps.utils.SkyblockUtils;

/** Where a sea creature can be fished up. Ported from GanKura's MobVisual.FishingArea. */
public enum FishArea {
    ANYWHERE,
    BACKWATER_BAYOU,
    LOTUS_ATOLL,
    MOONGLADE_MARSH,
    TORRHUS_CANYON,
    CRIMSON_ISLE,
    JERRYS_WORKSHOP,
    DWARVEN_MINES,
    FARMING_ISLANDS,
    CRYSTAL_HOLLOWS;

    public boolean isHere() {
        return switch (this) {
            case ANYWHERE -> true;
            case BACKWATER_BAYOU -> MobArea.area("Backwater Bayou");
            case LOTUS_ATOLL -> MobArea.area("Lotus Atoll");
            case MOONGLADE_MARSH -> MobArea.area("Moonglade Marsh");
            case TORRHUS_CANYON -> MobArea.area("Torrhus Canyon") || MobArea.area("Torrhus Heights");
            case CRIMSON_ISLE -> MobArea.area("Crimson Isle");
            case JERRYS_WORKSHOP -> MobArea.area("Jerry's Workshop");
            case DWARVEN_MINES -> MobArea.area("Dwarven Mines");
            case FARMING_ISLANDS -> MobArea.area("The Farming Islands");
            case CRYSTAL_HOLLOWS -> SkyblockUtils.getArea().equalsIgnoreCase("Crystal Hollows");
        };
    }
}
