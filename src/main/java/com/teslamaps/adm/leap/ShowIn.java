package com.teslamaps.adm.leap;

// Ported from ADM's pov/ShowIn.kt
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;

import java.util.Collection;

/** Where a feature may show: boss, Goldor (F7/M7 P3), or blood rush. */
public final class ShowIn {

    private ShowIn() {}

    public enum Place { BLOOD_RUSH, BOSS, GOLDOR }

    private static boolean bloodOpened;

    public static void onChat(String raw) {
        if (StringUtil.strip(raw).equals("The BLOOD DOOR has been opened!")) bloodOpened = true;
    }

    public static void reset() { bloodOpened = false; }

    public static boolean isIn(Place place) {
        return switch (place) {
            case BOSS -> DungeonManager.isInBossArea();
            case GOLDOR -> {
                DungeonFloor f = DungeonManager.getCurrentFloor();
                var p = Minecraft.getInstance().player;
                yield DungeonManager.isInBossArea() && f != null && f.getLevel() == 7 && p != null && p.getY() > 100 && p.getY() <= 155;
            }
            case BLOOD_RUSH -> DungeonManager.isInDungeon() && !DungeonManager.isInBossArea() && !bloodOpened;
        };
    }

    public static boolean allows(Collection<Place> places) {
        if (places.isEmpty()) return true;
        for (Place p : places) if (isIn(p)) return true;
        return false;
    }
}
