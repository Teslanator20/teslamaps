package com.teslamaps.features;

// Concept from SkyblockExtras' Ghost Loot counter
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ghost grind (Sorrow, Volta, Plasma, Ghostly Boots, coins) loot counter. */
public final class LootCounters {

    private static final Pattern RARE_DROP = Pattern.compile("^RARE DROP! (.+?)(?: \\(\\+[\\d,.]+% ✯ Magic Find\\))?$");
    private static final Pattern GHOST_COINS = Pattern.compile("^The ghost's death materialized ([\\d,]+) coins from the mists!$");
    private static final Map<String, String> GHOST_ITEMS = Map.of(
            "Sorrow", "SORROW", "Volta", "VOLTA", "Plasma", "PLASMA", "Ghostly Boots", "GHOST_BOOTS");

    private static Tracker ghost;
    private static HudEditElement ghostHud;
    private static boolean inMines;

    private LootCounters() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ghost = Tracker.create("ghost_loot", "§b§lGhost Loot").priceSource(() -> cfg().lootCounterPriceSource);
        ghostHud = HudEditElement.of("ghostLoot", "Ghost Loot").section("Mining")
                .enabled(() -> cfg().ghostLootCounter)
                .size(() -> Math.max(80, ghost.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, ghost.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> ghost.draw(g, mc, x, y, s));

        Feature.of("GhostLoot").section("Mining").when(() -> cfg().ghostLootCounter)
                .tick(LootCounters::ghostTick)
                .serverChat(LootCounters::ghostChat)
                .hud("ghostloot", LootCounters::renderGhost)
                .config("Mining", b -> {
                    b.label("Ghost Loot");
                    b.toggle("Ghost Loot Counter", () -> cfg().ghostLootCounter, v -> cfg().ghostLootCounter = v)
                            .desc("Sorrow, Volta, Plasma, Ghostly Boots and ghost coins, in the Dwarven Mines (/tmap gui)")
                            .tags("ghost", "sorrow", "mist");
                    b.priceSource("  Price Source", () -> cfg().lootCounterPriceSource, v -> cfg().lootCounterPriceSource = v);
                    b.trackerButtons(ghost);
                });
    }

    private static void ghostTick() {
        ghost.tick();
        inMines = SkyblockUtils.isInArea("Dwarven Mines");
    }

    private static void ghostChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("The ghost's death")) {
            Matcher m = GHOST_COINS.matcher(t);
            if (m.matches()) ghost.addCoins(Long.parseLong(m.group(1).replace(",", "")));
        } else if (t.startsWith("RARE DROP!")) {
            Matcher m = RARE_DROP.matcher(t);
            if (!m.matches()) return;
            String id = GHOST_ITEMS.get(m.group(1).trim());
            if (id != null) ghost.add(id, 1);
        }
    }

    private static void renderGhost(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inMines || ghostHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        ghost.draw(g, mc, ghostHud.x(), ghostHud.y(), ghostHud.scale());
    }
}
