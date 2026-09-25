package com.teslamaps.features;

// Concept from SkyblockExtras' Ghost Loot / Dragon Loot counters, dragon lines from SkyHanni's DragonFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ghost grind (Sorrow, Volta, Plasma, Ghostly Boots, coins) and Ender Dragon loot counters. */
public final class LootCounters {

    private static final Pattern RARE_DROP = Pattern.compile("^RARE DROP! (.+?)(?: \\(\\+[\\d,.]+% ✯ Magic Find\\))?$");
    private static final Pattern GHOST_COINS = Pattern.compile("^The ghost's death materialized ([\\d,]+) coins from the mists!$");
    private static final Pattern DRAGON_DOWN = Pattern.compile("^(SUPERIOR|STRONG|UNSTABLE|YOUNG|WISE|OLD|PROTECTOR|HOLY) DRAGON DOWN!$");
    private static final Pattern DRAGON_ITEM = Pattern.compile(
            "^((SUPERIOR|STRONG|UNSTABLE|YOUNG|WISE|OLD|PROTECTOR|HOLY)_(FRAGMENT|DRAGON_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)))$"
                    + "|^(DRAGON_CLAW|DRAGON_HORN|DRAGON_SCALE|ASPECT_OF_THE_DRAGON|ENDER_DRAGON|TRAVEL_SCROLL_TO_DRAGONS_NEST)$");
    private static final Map<String, String> GHOST_ITEMS = Map.of(
            "Sorrow", "SORROW", "Volta", "VOLTA", "Plasma", "PLASMA", "Ghostly Boots", "GHOST_BOOTS");
    private static final String KILLS = "DRAGON_KILLS";
    private static final String EYES = "EYES_PLACED";
    private static final long LOOT_WINDOW_MS = 60_000;

    private static Tracker ghost, dragon;
    private static HudEditElement ghostHud, dragonHud;
    private static boolean inMines, inEnd;
    private static long dragonDownAt;
    private static Map<String, Long> invCounts = null;
    private static int ticks;

    private LootCounters() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ghost = Tracker.create("ghost_loot", "§b§lGhost Loot").priceSource(() -> cfg().lootCounterPriceSource);
        dragon = Tracker.create("dragon_loot", "§5§lDragon Loot").priceSource(() -> cfg().lootCounterPriceSource)
                .name(KILLS, "§dDragons Killed").name(EYES, "§5Eyes Placed")
                .extra(() -> "§6Dragons since Superior: §f" + cfg().dragonsSinceSuperior);
        ghostHud = HudEditElement.of("ghostLoot", "Ghost Loot").section("Mining")
                .enabled(() -> cfg().ghostLootCounter)
                .size(() -> Math.max(80, ghost.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, ghost.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> ghost.draw(g, mc, x, y, s));
        dragonHud = HudEditElement.of("dragonLoot", "Dragon Loot").section("The End")
                .enabled(() -> cfg().dragonLootCounter)
                .size(() -> Math.max(80, dragon.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, dragon.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> dragon.draw(g, mc, x, y, s));

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
        Feature.of("DragonLoot").section("The End").when(() -> cfg().dragonLootCounter)
                .tick(LootCounters::dragonTick)
                .serverChat(LootCounters::dragonChat)
                .hud("dragonloot", LootCounters::renderDragon)
                .config("The End", b -> {
                    b.label("Dragon Loot");
                    b.toggle("Dragon Loot Counter", () -> cfg().dragonLootCounter, v -> cfg().dragonLootCounter = v)
                            .desc("Dragon kills, eyes placed, dragons since Superior and dragon drops picked up after a kill (/tmap gui)")
                            .tags("dragon", "superior", "drags since");
                    b.priceSource("  Price Source", () -> cfg().lootCounterPriceSource, v -> cfg().lootCounterPriceSource = v);
                    b.trackerButtons(dragon);
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

    private static void dragonTick() {
        dragon.tick();
        inEnd = SkyblockUtils.isInArea("The End");
        Minecraft mc = Minecraft.getInstance();
        boolean window = inEnd && System.currentTimeMillis() - dragonDownAt < LOOT_WINDOW_MS;
        if (!window) { invCounts = null; return; }
        if (++ticks % 5 != 0) return;
        Map<String, Long> next = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = ItemUtil.skyblockId(st);
            if (id == null || id.isEmpty() || !DRAGON_ITEM.matcher(id).matches()) continue;
            next.merge(id, (long) st.getCount(), Long::sum);
        }
        if (invCounts != null && mc.screen == null) {
            for (Map.Entry<String, Long> e : next.entrySet()) {
                long d = e.getValue() - invCounts.getOrDefault(e.getKey(), 0L);
                if (d > 0) dragon.add(e.getKey(), d);
            }
        }
        invCounts = next;
    }

    private static void dragonChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.startsWith("☬ You placed a Summoning Eye!")) {
            dragon.add(EYES, 1);
            return;
        }
        if (t.startsWith("You recovered a Summoning Eye!")) {
            dragon.add(EYES, -1);
            return;
        }
        if (!t.endsWith("DRAGON DOWN!")) return;
        Matcher m = DRAGON_DOWN.matcher(t);
        if (!m.matches()) return;
        dragon.add(KILLS, 1);
        String type = m.group(1);
        dragon.add(type.charAt(0) + type.substring(1).toLowerCase() + " Dragon", 1);
        cfg().dragonsSinceSuperior = type.equals("SUPERIOR") ? 0 : cfg().dragonsSinceSuperior + 1;
        TeslaMapsConfig.save();
        dragonDownAt = System.currentTimeMillis();
        invCounts = null;
    }

    private static void renderGhost(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inMines || ghostHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        ghost.draw(g, mc, ghostHud.x(), ghostHud.y(), ghostHud.scale());
    }

    private static void renderDragon(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!inEnd || dragonHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        dragon.draw(g, mc, dragonHud.x(), dragonHud.y(), dragonHud.scale());
    }
}
