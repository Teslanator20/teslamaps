package com.teslamaps.features;

// Ported from SkyHanni's DungeonDeathCounter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Pattern;

public final class DungeonDeathCounter {

    private static final String SECTION = "Score & Splits";
    private static final Pattern DEATH = Pattern.compile(
            "☠ .+(?:(?:crush|di(?:sconnect)?|kill|suffocat)ed|fell|burnt).+became a ghost\\.");
    private static final List<String> SAMPLE = List.of("§cDeaths: 3");

    private static int deaths = 0;
    private static List<String> line = List.of();
    private static ClientLevel lastLevel;
    private static HudEditElement el;

    private DungeonDeathCounter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("deathCounter", "Death Counter").ctx(HudEditElement.HudCtx.DUNGEON).section(SECTION)
                .enabled(() -> cfg().deathCounter)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE.get(0)), () -> 10)
                .preview((g, mc, x, y, s) -> BlessingHud.draw(g, mc, x, y, s, SAMPLE, mc.font.width(SAMPLE.get(0))));
        Feature.of("DeathCounter").section(SECTION).when(() -> cfg().deathCounter)
                .tick(DungeonDeathCounter::tick)
                .serverChat(DungeonDeathCounter::onChat)
                .hud("deathcounter", DungeonDeathCounter::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            set(0);
        }
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String msg = StringUtil.strip(raw);
        if (msg.length() < 20 || msg.indexOf('☠') < 0 || !DEATH.matcher(msg).find()) return;
        set(deaths + 1);
        Minecraft mc = Minecraft.getInstance();
        if (cfg().deathCounterChat && mc.player != null)
            mc.player.sendSystemMessage(Component.literal("§c§l" + deaths + ". DEATH!"));
    }

    private static void set(int n) {
        deaths = n;
        String color = n <= 2 ? "§e" : n == 3 ? "§c" : "§4";
        line = n == 0 ? List.of() : List.of(color + "Deaths: " + n);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = line;
        if (l.isEmpty() || el == null || !DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        BlessingHud.draw(g, mc, el.x(), el.y(), el.scale(), l, mc.font.width(l.get(0)));
    }
}
