package com.teslamaps.features.mining;

// After GanKura's CorleoneHandler
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** One "BOSS CORLEONE" title when Corleone shows up in the Crystal Hollows. */
public final class CorleoneTitle {

    private static final String SKIN_HASH = "3c37b434bbb65fe5838afced8301604126214b2a";

    private static boolean present;
    private static Object lastLevel;
    private static int ticks;

    private CorleoneTitle() {}

    public static void register() {
        Feature.of("CorleoneTitle").section("Mob Visuals").when(() -> TeslaMapsConfig.get().corleoneSpawnTitle)
                .tick(CorleoneTitle::tick)
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Boss Corleone Spawn Title", () -> c.corleoneSpawnTitle, v -> c.corleoneSpawnTitle = v)
                            .desc("Crystal Hollows: shows BOSS CORLEONE once when he spawns, works without picking him in Mob Visuals")
                            .tags("corleone", "crystal hollows", "boss");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            present = false;
        }
        if (++ticks % 10 != 0) return;
        if (!com.teslamaps.utils.SkyblockUtils.getArea().equalsIgnoreCase("Crystal Hollows")) return;
        boolean found = false;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isRemoved()) continue;
            if (p instanceof AbstractClientPlayer acp
                    && acp.getSkin().body().texturePath().toString().endsWith(SKIN_HASH)) {
                found = true;
                break;
            }
        }
        if (found && !present) {
            mc.gui.setTimes(5, 50, 10);
            mc.gui.setTitle(Component.literal("§d§lBOSS CORLEONE"));
        }
        present = found;
    }
}
