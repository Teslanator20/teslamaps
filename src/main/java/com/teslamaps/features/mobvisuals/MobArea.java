package com.teslamaps.features.mobvisuals;

import com.teslamaps.utils.SkyblockUtils;

/** Island (or Safari biome) a mob belongs to. Ported from GanKura's per-area MobVisual enums. */
public enum MobArea {
    THE_END("The End"),
    SPIDERS_DEN("Spider's Den"),
    CRIMSON_ISLE("Crimson Isle"),
    CRYSTAL_HOLLOWS("Crystal Hollows"),
    SAFARI_CAVERN("Safari - Cavern"),
    SAFARI_FOREST("Safari - Forest"),
    SAFARI_HAUNTED("Safari - Haunted"),
    SAFARI_ICY("Safari - Icy"),
    MOONGLADE_MARSH("Moonglade Marsh"),
    TORRHUS_CANYON("Torrhus Canyon"),
    LOTUS_ATOLL("Lotus Atoll"),
    GLACITE_MINESHAFT("Glacite Mineshaft"),
    GARDEN("Garden"),
    SEA_CREATURE("Sea Creatures");

    private final String title;

    MobArea(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public boolean isSafari() {
        return this == SAFARI_CAVERN || this == SAFARI_FOREST
                || this == SAFARI_HAUNTED || this == SAFARI_ICY;
    }

    public boolean isHere() {
        return switch (this) {
            case THE_END -> area("The End");
            case SPIDERS_DEN -> area("Spider's Den");
            case CRIMSON_ISLE -> area("Crimson Isle");
            case CRYSTAL_HOLLOWS -> area("Crystal Hollows");
            case SAFARI_CAVERN, SAFARI_FOREST, SAFARI_HAUNTED, SAFARI_ICY -> area("Safari");
            case MOONGLADE_MARSH -> area("Moonglade Marsh");
            // the canyon spans two named areas
            case TORRHUS_CANYON -> area("Torrhus Canyon") || area("Torrhus Heights");
            case LOTUS_ATOLL -> area("Lotus Atoll");
            case GLACITE_MINESHAFT -> area("Mineshaft");
            case GARDEN -> SkyblockUtils.isInGarden();
            // sea creatures gate on their own fishing area instead
            case SEA_CREATURE -> true;
        };
    }

    static boolean area(String name) {
        return SkyblockUtils.getArea().equalsIgnoreCase(name);
    }
}
