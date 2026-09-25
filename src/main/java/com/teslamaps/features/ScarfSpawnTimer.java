package com.teslamaps.features;

// Concept from Zen's ScarfSpawnTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;

public class ScarfSpawnTimer {

    private static final ServerTickTimer TIMER = new ServerTickTimer();
    private static String label = "";

    public static void register() {
        Feature.of("ScarfSpawnTimer").section("Timers").when(() -> TeslaMapsConfig.get().scarfSpawnTimer)
                .serverChat(ScarfSpawnTimer::onChat)
                .tick(ScarfSpawnTimer::tick);
    }

    private static void onChat(String raw) {
        if (!raw.contains("Scarf")) return;
        String m = com.teslamaps.utils.StringUtil.strip(raw);
        if (!m.startsWith("[BOSS] Scarf")) return;
        if (m.contains("If you can beat my Undeads")) { label = "§7Undeads"; TIMER.start(155); }
        else if (m.contains("Those toys are not strong enough I see.")) { label = "§cScarf"; TIMER.start(200); }
    }

    private static void tick() {
        if (!TIMER.active() || !DungeonManager.isInDungeon()) {
            TIMER.stop();
            DungeonTimers.clear("scarf");
            return;
        }
        DungeonTimers.set("scarf", label + "§f: §e" + ServerTickTimer.seconds(TIMER.remaining()));
    }
}
