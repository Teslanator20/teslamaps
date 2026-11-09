package com.teslamaps.features;

// Ported from SkyHanni's StereoHarmonyDisplay.kt / StereoHarmonyDiscReplacer.kt and Skyblocker's StereoHarmonyHelper.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stereo Harmony: playing vinyl HUD, crop icons in the menu, contest-crop highlight. */
public final class StereoHarmony {

    // {vinyl, item id, pest, crop}
    private static final String[][] VINYLS = {
            {"Pretty Fly", "VINYL_PRETTY_FLY", "Fly", "Wheat"},
            {"Cricket Choir", "VINYL_CRICKET_CHOIR", "Cricket", "Carrot"},
            {"Cicada Symphony", "VINYL_CICADA_SYMPHONY", "Locust", "Potato"},
            {"Rodent Revolution", "VINYL_RODENT_REVOLUTION", "Rat", "Pumpkin"},
            {"Buzzin' Beats", "VINYL_BUZZIN_BEATS", "Mosquito", "Sugar Cane"},
            {"Earthworm Ensemble", "VINYL_EARTHWORM_ENSEMBLE", "Earthworm", "Melon"},
            {"DynaMITES", "VINYL_DYNAMITES", "Mite", "Cactus"},
            {"Wings of Harmony", "VINYL_WINGS_OF_HARMONY", "Moth", "Cocoa Beans"},
            {"Slow and Groovy", "VINYL_SLOW_AND_GROOVY", "Slug", "Mushroom"},
            {"Not Just a Pest", "VINYL_BEETLE", "Beetle", "Nether Wart"},
            {"Firefly in the Hole", "VINYL_FIREFLY", "Firefly", "Moonflower"},
            {"Imagine Dragonflies", "VINYL_IMAGINE_DRAGONFLIES", "Dragonfly", "Sunflower"},
            {"Pray For Me", "VINYL_PRAY_FOR_ME", "Praying Mantis", "Wild Rose"},
    };
    private static final Pattern PEST_LORE = Pattern.compile("^When playing, (.+?) Pests.*");
    private static final Pattern CONTEST_CROP = Pattern.compile("^ [○☘] (.+) \\S+$");
    private static final String NOW_PLAYING = "Now Playing: ";
    private static final int LINE = 10;
    private static final int GREEN = 0x8055FF55, YELLOW = 0x80FFFF55;

    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static final int[] highlight = new int[54];
    private static Feature iconFeature, highlightFeature;
    private static Screen lastScreen;
    private static boolean inStereo;
    private static String contestCrop;
    private static List<String> lines = List.of();
    private static String builtFor;
    private static int width, ticks;

    private StereoHarmony() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("stereoHarmonyHud", "Stereo Harmony").section("Garden").ctx(HudEditElement.HudCtx.GARDEN)
                .enabled(() -> cfg().stereoHarmonyHud)
                .size(() -> Math.max(90, width), () -> LINE * 2)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s,
                        List.of("§ePlaying: §aWings of Harmony", "§ePest: §cMoth §7(Cocoa Beans)"), 130));
        Feature.of("StereoHarmony").section("Garden")
                .when(() -> cfg().stereoHarmonyHud || cfg().stereoMenuIcons || cfg().stereoContestHighlight)
                .tick(StereoHarmony::tick);
        Feature.of("StereoHarmonyHud").section("Garden").when(() -> cfg().stereoHarmonyHud)
                .hudHiddenInInventory("stereoharmonyhud", StereoHarmony::render);
        iconFeature = Feature.of("StereoHarmonyIcons").section("Garden").when(() -> cfg().stereoMenuIcons);
        highlightFeature = Feature.of("StereoHarmonyHighlight").section("Garden").when(() -> cfg().stereoContestHighlight)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Stereo Harmony");
                    b.toggle("Stereo Harmony Display", () -> c.stereoHarmonyHud, v -> c.stereoHarmonyHud = v)
                            .desc("Playing vinyl, the pest it attracts and that pest's crop. Read from the Stereo menu or your vacuum (/tmap gui)")
                            .tags("stereo", "vinyl", "pest", "vacuum");
                    b.toggle("  Hide When Nothing Plays", () -> c.stereoHideWhenNone, v -> c.stereoHideWhenNone = v);
                    b.toggle("  Crop Icons In The Stereo Menu", () -> c.stereoMenuIcons, v -> c.stereoMenuIcons = v)
                            .desc("Vinyls show the crop of their pest instead of the disc");
                    b.toggle("  Highlight Vinyl For Jacob's Crop", () -> c.stereoContestHighlight, v -> c.stereoContestHighlight = v)
                            .desc("During a Jacob's Contest the matching vinyl is green, yellow when it already plays");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != lastScreen) {
            lastScreen = mc.gui.screen();
            inStereo = mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                    && StringUtil.stripTrim(cs.getTitle().getString()).equals("Stereo Harmony");
            java.util.Arrays.fill(highlight, 0);
        }
        if (ticks++ % 10 != 0) return;
        if (inStereo && mc.gui.screen() instanceof AbstractContainerScreen<?> cs) {
            List<Slot> slots = cs.getMenu().slots;
            if (slots.size() > 4) readPlaying(slots.get(4).getItem());
            if (cfg().stereoContestHighlight) buildHighlights(slots);
        } else if (ticks % 40 == 1) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && ItemUtil.skyblockId(s).contains("VACUUM")) {
                    readPlaying(s);
                    break;
                }
            }
        }
        if (ticks % 20 == 1) contestCrop = readContestCrop();
        if (cfg().stereoHarmonyHud) buildLines(mc);
    }

    private static void readPlaying(ItemStack stack) {
        for (String l : ItemUtil.loreLines(stack)) {
            String t = l.trim();
            if (!t.startsWith(NOW_PLAYING)) continue;
            String v = t.substring(NOW_PLAYING.length());
            int p = v.indexOf(" (");
            if (p >= 0) v = v.substring(0, p);
            v = v.trim();
            if (v.equalsIgnoreCase("None")) v = "";
            if (!v.equals(cfg().stereoActiveVinyl)) {
                cfg().stereoActiveVinyl = v;
                TeslaMapsConfig.save();
            }
            return;
        }
    }

    private static String readContestCrop() {
        List<String> sb = ScoreboardUtils.getScoreboardLines();
        boolean contest = false;
        for (String l : sb) if (StringUtil.strip(l).contains("Jacob's Contest")) { contest = true; break; }
        if (!contest) return null;
        for (String l : sb) {
            Matcher m = CONTEST_CROP.matcher(StringUtil.strip(l));
            if (m.matches()) return m.group(1).trim();
        }
        return null;
    }

    private static boolean sameCrop(String a, String b) {
        return a != null && b != null && (a.startsWith(b) || b.startsWith(a));
    }

    private static void buildHighlights(List<Slot> slots) {
        java.util.Arrays.fill(highlight, 0);
        if (contestCrop == null) return;
        for (Slot slot : slots) {
            if (slot.container instanceof Inventory || slot.index >= highlight.length || !slot.hasItem()) continue;
            List<String> lore = ItemUtil.loreLines(slot.getItem());
            String crop = null;
            boolean playing = false;
            for (String l : lore) {
                Matcher m = PEST_LORE.matcher(l.trim());
                if (m.matches()) crop = cropOfPest(m.group(1).trim());
                if (l.contains("PLAYING")) playing = true;
            }
            if (sameCrop(crop, contestCrop)) highlight[slot.index] = playing ? YELLOW : GREEN;
        }
    }

    private static String cropOfPest(String pest) {
        for (String[] v : VINYLS) if (v[2].equalsIgnoreCase(pest)) return v[3];
        return null;
    }

    private static void buildLines(Minecraft mc) {
        String vinyl = cfg().stereoActiveVinyl;
        String key = vinyl + cfg().stereoHideWhenNone;
        if (key.equals(builtFor)) return;
        builtFor = key;
        String[] v = null;
        for (String[] e : VINYLS) if (e[0].equalsIgnoreCase(vinyl)) v = e;
        if (v == null) lines = vinyl.isEmpty() && cfg().stereoHideWhenNone ? List.of() : List.of("§ePlaying: §7" + (vinyl.isEmpty() ? "Nothing" : vinyl));
        else lines = List.of("§ePlaying: §a" + v[0], "§ePest: §c" + v[2] + " §7(" + v[3] + ")");
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        width = w;
    }

    public static void drawHighlight(GuiGraphicsExtractor g, Slot slot) {
        if (!inStereo || slot.container instanceof Inventory || slot.index >= highlight.length) return;
        int c = highlight[slot.index];
        if (c == 0 || highlightFeature == null || !highlightFeature.active()) return;
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, c);
    }

    /** Icon to draw for a slot; the stack itself when nothing changes. */
    public static ItemStack icon(Slot slot, ItemStack stack) {
        if (!inStereo || stack.isEmpty() || slot.container instanceof Inventory) return stack;
        if (Minecraft.getInstance().gui.screen() != lastScreen || iconFeature == null || !iconFeature.active()) return stack;
        int i = slot.index;
        if (!(i >= 11 && i <= 15) && !(i >= 20 && i <= 24) && !(i >= 30 && i <= 32)) return stack;
        String id = ItemUtil.skyblockId(stack);
        if (!id.startsWith("VINYL_")) return stack;
        String crop = null;
        for (String[] v : VINYLS) if (v[1].equals(id)) crop = v[3];
        if (crop == null) return stack;
        boolean playing = ItemUtil.loreContains(stack, "PLAYING");
        String key = crop + playing;
        String c = crop;
        return ICONS.computeIfAbsent(key, k -> {
            ItemStack s = new ItemStack(item(c));
            if (playing) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            return s;
        });
    }

    private static Item item(String crop) {
        return switch (crop) {
            case "Wheat" -> Items.WHEAT;
            case "Carrot" -> Items.CARROT;
            case "Potato" -> Items.POTATO;
            case "Pumpkin" -> Items.CARVED_PUMPKIN;
            case "Sugar Cane" -> Items.SUGAR_CANE;
            case "Melon" -> Items.MELON_SLICE;
            case "Cactus" -> Items.CACTUS;
            case "Cocoa Beans" -> Items.COCOA_BEANS;
            case "Mushroom" -> Items.RED_MUSHROOM;
            case "Nether Wart" -> Items.NETHER_WART;
            case "Moonflower" -> Items.BLUE_ORCHID;
            case "Wild Rose" -> Items.ROSE_BUSH;
            default -> Items.SUNFLOWER;
        };
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || !SkyblockUtils.isInGarden()) return;
        TeslaMapsConfig c = cfg();
        draw(g, Minecraft.getInstance(), c.stereoHarmonyHudX, c.stereoHarmonyHudY, c.stereoHarmonyHudScale, lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
