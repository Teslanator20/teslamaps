package com.teslamaps.features.fishing;

// Ported from GanKura's GoldenFishHandler / GoldenFishHud / GoldfinShardScanner (after SkyHanni's GoldenFishTimer)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class GoldenFishTimer {

    private static final long MIN_SPAWN_MS = 8 * 60_000L, MAX_SPAWN_MS = 12 * 60_000L, SHARD_MS = 30_000L;
    private static final long ROD_TIMEOUT_MS = 3 * 60_000L, DESPAWN_MS = 60_000L;
    private static final int MAX_INTERACTIONS = 3;
    private static final List<String> SAMPLE = List.of("§6§lGolden Fish", "§7Can spawn since: §b1:05", "§7Chance: §b26%",
            "§7Throw rod in: §b2:12", "§7Goldfin Shard: §b10 §8(-5:00)");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static long spawnBase, lastRod, despawnAt;
    private static int interactions;
    private static boolean warnedRod, countedCast, hasLavaRod;
    private static int ticks;

    private GoldenFishTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("goldenFishTimer", "Golden Fish Timer").section("Fishing")
                .enabled(() -> cfg().goldenFishTimer)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("GoldenFishTimer").section("Fishing").when(() -> cfg().goldenFishTimer)
                .tick(GoldenFishTimer::tick)
                .serverChat(GoldenFishTimer::onChat)
                .hudHiddenInInventory("goldenfishtimer", GoldenFishTimer::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Golden Fish");
                    b.toggle("Golden Fish Timer", () -> c.goldenFishTimer, v -> c.goldenFishTimer = v)
                            .desc("Crimson Isle with a lava rod: spawn window (8-12 min after the first lava cast, -30s per Goldfin Shard level), "
                                    + "chance, rod reset countdown, interactions and despawn timer. Position in /tmap gui")
                            .tags("golden", "fish", "trophy", "lava", "goldfin");
                    b.toggle("  Titles", () -> c.goldenFishTitles, v -> c.goldenFishTitles = v)
                            .desc("Golden Fish! on spawn, Pull! once it is weak");
                    b.toggle("  Throw Rod Warning", () -> c.goldenFishRodWarning, v -> c.goldenFishRodWarning = v)
                            .desc("Title before the timer resets after 3 min without a lava cast");
                    b.slider("    Warn Seconds Before", 5f, 120f, () -> (float) c.goldenFishRodWarnSeconds,
                            v -> c.goldenFishRodWarnSeconds = Math.round(v), 0);
                    b.slider("  Goldfin Shard Level", 0f, 10f, () -> (float) c.goldfinShardLevel, v -> {
                        c.goldfinShardLevel = Math.round(v);
                        c.goldfinShardRead = true;
                    }, 0).desc("Read automatically when you open the Attribute Menu");
                });
    }

    private static long minSpawn() { return MIN_SPAWN_MS - SHARD_MS * cfg().goldfinShardLevel; }
    private static long maxSpawn() { return MAX_SPAWN_MS - SHARD_MS * cfg().goldfinShardLevel; }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.contains("Golden Fish")) return;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        switch (t) {
            case "You spot a Golden Fish surface from beneath the lava!" -> {
                despawnAt = now + DESPAWN_MS;
                interactions = 0;
                title(mc, "§6§lGolden Fish!");
            }
            case "The Golden Fish escapes your hook but looks weakened." -> {
                despawnAt = now + DESPAWN_MS;
                interactions++;
            }
            case "The Golden Fish is weak!" -> {
                despawnAt = now + DESPAWN_MS;
                interactions = MAX_INTERACTIONS;
                title(mc, "§a§lPull!");
            }
            case "The Golden Fish swims back beneath the lava..." -> restart(now);
            default -> {
                if (t.contains("TROPHY FISH!")) restart(now);
            }
        }
    }

    private static void restart(long now) {
        despawnAt = 0;
        interactions = 0;
        spawnBase = now;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) {
            hasLavaRod = findLavaRod(mc);
            scanGoldfin(mc);
        }
        if (!SkyblockUtils.getArea().equalsIgnoreCase("Crimson Isle") || !hasLavaRod) {
            reset();
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        FishingHook hook = mc.player.fishing;
        if (hook == null) {
            countedCast = false;
        } else if (!countedCast && hook.isInLava()) {
            countedCast = true;
            warnedRod = false;
            lastRod = now;
            if (spawnBase == 0) spawnBase = now;
        }
        if (lastRod != 0 && now - lastRod > ROD_TIMEOUT_MS) {
            spawnBase = 0;
            lastRod = 0;
        }
        if (cfg().goldenFishRodWarning && !warnedRod && lastRod != 0
                && lastRod + ROD_TIMEOUT_MS - now <= cfg().goldenFishRodWarnSeconds * 1000L) {
            warnedRod = true;
            title(mc, "§c§lThrow your rod!");
        }
        if (despawnAt != 0 && now >= despawnAt) restart(now);
        if (ticks % 5 == 0) buildLines(now);
    }

    private static void buildLines(long now) {
        List<String> l = new ArrayList<>(6);
        l.add("§6§lGolden Fish");
        if (despawnAt != 0) {
            l.add("§7Despawns in: §b" + time(despawnAt - now));
            l.add("§7Interactions: " + (interactions >= MAX_INTERACTIONS ? "§a§lPULL " : "§b") + interactions + "/" + MAX_INTERACTIONS);
        } else if (spawnBase != 0) {
            long until = spawnBase + minSpawn() - now;
            if (until > 0) {
                l.add("§7Can spawn in: §b" + time(until));
            } else {
                long since = -until;
                double chance = Math.min(1.0, (double) since / Math.max(1, maxSpawn() - minSpawn()));
                l.add("§7Can spawn since: §b" + time(since));
                l.add("§7Chance: §b" + Math.round(chance * 100) + "%");
            }
            if (lastRod != 0) l.add("§7Throw rod in: §b" + time(lastRod + ROD_TIMEOUT_MS - now));
            int lvl = cfg().goldfinShardLevel;
            l.add(cfg().goldfinShardRead ? "§7Goldfin Shard: §b" + lvl + " §8(-" + time(lvl * SHARD_MS) + ")"
                    : "§7Goldfin Shard: §cUnknown §8(open the Attribute Menu)");
        } else {
            lines = List.of();
            return;
        }
        lines = l;
        width = TextHud.width(l);
    }

    private static boolean findLavaRod(Minecraft mc) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            for (String line : ItemUtil.loreLines(st)) if (line.trim().equals("Lava Rod")) return true;
        }
        return false;
    }

    private static void scanGoldfin(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !s.getTitle().getString().contains("Attribute Menu")) return;
        for (Slot slot : s.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            boolean found = false;
            int level = 0;
            for (String line : ItemUtil.loreLines(st)) {
                if (line.contains("Source: Goldfin Shard")) found = true;
                int i = line.indexOf("Attribute Level: ");
                if (i >= 0) {
                    try { level = Integer.parseInt(line.substring(i + 17).trim().split(" ")[0]); } catch (NumberFormatException ignored) {}
                }
            }
            if (!found) continue;
            level = Math.max(0, Math.min(10, level));
            if (cfg().goldfinShardLevel != level || !cfg().goldfinShardRead) {
                cfg().goldfinShardLevel = level;
                cfg().goldfinShardRead = true;
                TeslaMapsConfig.save();
            }
            return;
        }
    }

    private static void title(Minecraft mc, String text) {
        if (!cfg().goldenFishTitles || mc.player == null) return;
        mc.gui.hud.setTimes(0, 30, 5);
        mc.gui.hud.setTitle(Component.literal(text));
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
    }

    private static void reset() {
        spawnBase = 0;
        lastRod = 0;
        despawnAt = 0;
        interactions = 0;
        warnedRod = false;
        countedCast = false;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
