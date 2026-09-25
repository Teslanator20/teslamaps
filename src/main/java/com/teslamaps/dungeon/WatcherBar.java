package com.teslamaps.dungeon;

// Ported from Devonian's WatcherBossBar.kt (hide part; the n/total label sits in BossBarHealth)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import net.minecraft.client.gui.components.LerpingBossEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WatcherBar {

    private WatcherBar() {}

    private static boolean hide = false;
    private static final List<LerpingBossEvent> kept = new ArrayList<>();

    public static void register() {
        Feature.of("WatcherBar").section("Blood Camp").tick(WatcherBar::tick);
    }

    private static void tick() {
        if (!TeslaMapsConfig.get().watcherBossbarHide || !DungeonManager.isInDungeon() || DungeonManager.isInBossArea()) {
            hide = false;
            return;
        }
        DungeonRoom room = DungeonManager.getCurrentRoom();
        hide = room == null || room.getType() != RoomType.BLOOD;
    }

    public static boolean hideNow() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return hide && c.watcherBossbarHide && c.section("Blood Camp");
    }

    public static Collection<LerpingBossEvent> filter(Collection<LerpingBossEvent> all) {
        kept.clear();
        for (LerpingBossEvent ev : all) {
            if (ev.getName() != null && ev.getName().getString().contains("The Watcher")) continue;
            kept.add(ev);
        }
        return kept;
    }
}
