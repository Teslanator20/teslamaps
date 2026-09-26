package com.teslamaps.features.fishing;

// After SkyblockExtras' Dolphin Display / Expertise Kills; expertise tiers from Skyblocker's StackingEnchantProgressTooltip
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishingProgressHud {

    private static final int[] DOLPHIN = {250, 1000, 2500, 5000, 10000};
    private static final String[] DOLPHIN_RARITY = {"§fCommon", "§aUncommon", "§9Rare", "§5Epic", "§6Legendary"};
    private static final int[] EXPERTISE = {0, 50, 100, 250, 500, 1000, 2500, 5500, 10000, 15000};
    private static final String PREFIX = "Sea Creatures Killed: ";
    private static final List<String> SAMPLE = List.of("§3Dolphin §9Rare§7: §e1,834§7/§e2,500 §8(max 10,000)",
            "§3Expertise §eVI§7: §e1,210§7/§e2,500 §8(max 15,000)");

    private static final ProfiledFile FILE = new ProfiledFile("dolphin_kills.txt",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("dolphin_kills.txt"));

    private static long kills = -1;
    private static boolean loaded;
    private static int seenLiveKills;
    private static int ticks;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private FishingProgressHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("fishingProgressHud", "Dolphin / Expertise").section("Fishing")
                .enabled(() -> cfg().fishingProgressHud)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("FishingProgressHud").section("Fishing").when(() -> cfg().fishingProgressHud)
                .tick(FishingProgressHud::tick)
                .hudHiddenInInventory("fishingprogresshud", FishingProgressHud::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Dolphin / Expertise");
                    b.toggle("Dolphin & Expertise Display", () -> c.fishingProgressHud, v -> c.fishingProgressHud = v)
                            .desc("Sea creature kills towards the next Dolphin pet milestone and the expertise kills of the held rod. "
                                    + "Open a menu that shows \"Sea Creatures Killed\" (Dolphin milestone) once for the start value. Position in /tmap gui")
                            .tags("dolphin", "expertise", "milestone", "kills", "rod");
                    b.toggle("  Dolphin Milestone", () -> c.fishingProgressDolphin, v -> c.fishingProgressDolphin = v);
                    b.toggle("  Expertise Kills", () -> c.fishingProgressExpertise, v -> c.fishingProgressExpertise = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ensure();
        int live = LiveSeaCreatures.killsSinceLoad;
        if (live != seenLiveKills) {
            if (kills >= 0) kills += live - seenLiveKills;
            seenLiveKills = live;
            save();
        }
        if (++ticks % 10 != 0) return;
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            for (Slot s : cs.getMenu().slots) {
                ItemStack st = s.getItem();
                if (st.isEmpty()) continue;
                for (String l : ItemUtil.loreLines(st)) {
                    String t = l.trim();
                    if (!t.startsWith(PREFIX)) continue;
                    try {
                        long v = Long.parseLong(t.substring(PREFIX.length()).replace(",", "").trim());
                        if (v != kills) { kills = v; save(); }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        List<String> out = new ArrayList<>(2);
        TeslaMapsConfig c = cfg();
        if (c.fishingProgressDolphin) {
            if (kills < 0) out.add("§3Dolphin§7: §copen the Dolphin milestone once");
            else {
                int tier = 0;
                while (tier < DOLPHIN.length && kills >= DOLPHIN[tier]) tier++;
                String cur = tier == 0 ? "§8None" : DOLPHIN_RARITY[tier - 1];
                out.add(tier >= DOLPHIN.length
                        ? "§3Dolphin " + cur + "§7: §a" + fmt(kills) + " §a✔"
                        : "§3Dolphin " + cur + "§7: §e" + fmt(kills) + "§7/§e" + fmt(DOLPHIN[tier]) + " §8(max " + fmt(DOLPHIN[DOLPHIN.length - 1]) + ")");
            }
        }
        if (c.fishingProgressExpertise && mc.player != null) {
            ItemStack rod = mc.player.getMainHandItem();
            CustomData d = rod.getItem() instanceof FishingRodItem ? rod.get(DataComponents.CUSTOM_DATA) : null;
            int ek = d == null ? -1 : d.copyTag().getIntOr("expertise_kills", -1);
            if (ek >= 0) {
                int lvl = 0;
                while (lvl < EXPERTISE.length && ek >= EXPERTISE[lvl]) lvl++;
                out.add(lvl >= EXPERTISE.length
                        ? "§3Expertise §e" + roman(lvl) + "§7: §a" + fmt(ek) + " §a✔"
                        : "§3Expertise §e" + roman(lvl) + "§7: §e" + fmt(ek) + "§7/§e" + fmt(EXPERTISE[lvl]) + " §8(max " + fmt(EXPERTISE[EXPERTISE.length - 1]) + ")");
            }
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String roman(int n) {
        String[] r = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        kills = -1;
        seenLiveKills = LiveSeaCreatures.killsSinceLoad;
        try {
            Path p = FILE.path();
            if (Files.exists(p)) kills = Long.parseLong(Files.readString(p).trim());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load dolphin kills: {}", e.toString());
        }
    }

    private static void save() {
        if (kills < 0) return;
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, Long.toString(kills));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save dolphin kills: {}", e.toString());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
