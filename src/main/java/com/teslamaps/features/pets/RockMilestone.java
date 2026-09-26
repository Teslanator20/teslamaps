package com.teslamaps.features.pets;

// After SkyblockAddons' ROCK_PET_TRACKER; sync line format from SkyHanni's SkillAchievements ("Ores mined: n")
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.profileviewer.data.PetIcons;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Ores mined towards the next Rock pet milestone; the Dolphin one lives in FishingProgressHud. */
public final class RockMilestone {

    private static final int[] ROCK = {2500, 7500, 20000, 100000, 250000};
    private static final String[] RARITY = {"§fCommon", "§aUncommon", "§9Rare", "§5Epic", "§6Legendary"};
    private static final String PREFIX = "ores mined:";
    private static final String SAMPLE = "§7Rock §9Rare§7: §e14,210§7/§e20,000";

    private static final ProfiledFile FILE = new ProfiledFile("rock_ores.txt",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("rock_ores.txt"));

    private static Feature feature;
    private static HudEditElement hud;
    private static long ores = -1;
    private static boolean loaded, dirty;
    private static int ticks;
    private static String line = "";
    private static ItemStack icon;
    private static BlockPos lastPos;
    private static long lastPosTime;

    private RockMilestone() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("rockMilestoneHud", "Rock Milestone").section("Mining")
                .enabled(() -> cfg().rockMilestoneHud)
                .size(() -> width(line.isEmpty() ? SAMPLE : line), () -> 20)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        feature = Feature.of("RockMilestone").section("Mining").when(() -> cfg().rockMilestoneHud)
                .tick(RockMilestone::tick)
                .hudHiddenInInventory("rockmilestone", RockMilestone::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Rock Pet Milestone", () -> c.rockMilestoneHud, v -> c.rockMilestoneHud = v)
                            .desc("Ores mined towards the next Rock pet milestone, counted while you mine. "
                                    + "Open the Mining skill menu (\"Ores mined\") once for the real value. "
                                    + "Dolphin: Fishing > Dolphin & Expertise Display. Position in /tmap gui")
                            .tags("rock", "pet", "milestone", "ores mined");
                });
    }

    public static boolean listening() {
        return feature != null && cfg().rockMilestoneHud && feature.active() && ores >= 0;
    }

    /** Client block update, main thread: the crosshair ore turning into something else. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState || !isOre(oldState) || isOre(newState) || DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK || !bh.getBlockPos().equals(pos)) return;
        long now = System.currentTimeMillis();
        if (pos.equals(lastPos) && now - lastPosTime < 500) return;
        lastPos = pos.immutable();
        lastPosTime = now;
        ores++;
        dirty = true;
    }

    private static boolean isOre(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
    }

    private static void tick() {
        ensure();
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            for (Slot s : cs.getMenu().slots) {
                ItemStack st = s.getItem();
                if (st.isEmpty()) continue;
                for (String l : ItemUtil.loreLines(st)) {
                    String t = l.trim();
                    if (!t.toLowerCase(Locale.ROOT).startsWith(PREFIX)) continue;
                    try {
                        long v = Long.parseLong(t.substring(PREFIX.length()).replace(",", "").trim());
                        if (v != ores) { ores = v; dirty = true; }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (dirty && ticks % 200 == 0) save();
        if (ores < 0) {
            line = "§7Rock§7: §copen the Mining skill menu once";
            return;
        }
        int tier = 0;
        while (tier < ROCK.length && ores >= ROCK[tier]) tier++;
        String cur = tier == 0 ? "§8None" : RARITY[tier - 1];
        line = tier >= ROCK.length
                ? "§7Rock " + cur + "§7: §a" + fmt(ores) + " §a✔"
                : "§7Rock " + cur + "§7: §e" + fmt(ores) + "§7/§e" + fmt(ROCK[tier]);
        if (icon == null) icon = PetIcons.forType("ROCK");
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        if (loaded && dirty) save();
        loaded = true;
        ores = -1;
        try {
            Path p = FILE.path();
            if (Files.exists(p)) ores = Long.parseLong(Files.readString(p).trim());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to load rock ores: {}", e.toString());
        }
    }

    private static void save() {
        dirty = false;
        if (ores < 0) return;
        try {
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, Long.toString(ores));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save rock ores: {}", e.toString());
        }
    }

    private static int width(String text) {
        return Minecraft.getInstance().font.width(text) + 26;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(text), 20);
        ItemStack ic = icon != null ? icon : ItemStack.EMPTY;
        if (!ic.isEmpty()) g.item(ic, 2, 2);
        g.text(mc.font, text, 21, 6, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || line.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), line);
    }
}
