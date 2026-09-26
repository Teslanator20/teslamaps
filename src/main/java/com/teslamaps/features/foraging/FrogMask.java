package com.teslamaps.features.foraging;

// Ported from SkyHanni's FrogMaskFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Today's Frog Mask region on the HUD, plus a title when you forage in another Park area. */
public final class FrogMask {

    private static final Pattern REGION = Pattern.compile("Today's region: (.+)");
    private static final String ID = "FROG_MASK";
    private static final long SB_EPOCH = 1560275700000L, DAY_MS = 1_200_000L;
    private static final String SAMPLE = "§5Frog Mask§6 - §aDark Thicket §6for §b12m 30s";

    private static String text = "";
    private static ItemStack icon, maskIcon;
    private static HudEditElement hud;
    private static long lastWarn, lastLogClick;
    private static int ticks;

    private FrogMask() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("frogMask", "Frog Mask").section("Foraging")
                .enabled(() -> cfg().frogMask && cfg().frogMaskDisplay)
                .size(() -> width(SAMPLE), () -> 20)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("FrogMask").section("Foraging").when(() -> cfg().frogMask)
                .tick(FrogMask::tick)
                .hudHiddenInInventory("frogmask", FrogMask::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Frog Mask");
                    b.toggle("Frog Mask Helper", () -> c.frogMask, v -> c.frogMask = v)
                            .desc("Reads \"Today's region\" from your Frog Mask.")
                            .tags("frog", "mask", "park", "region");
                    b.toggle("  Region Display", () -> c.frogMaskDisplay, v -> c.frogMaskDisplay = v);
                    b.toggle("    Only In The Park", () -> c.frogMaskDisplayParkOnly, v -> c.frogMaskDisplayParkOnly = v);
                    b.toggle("  Wrong Region Warning", () -> c.frogMaskWarning, v -> c.frogMaskWarning = v);
                    b.toggle("    Only While Chopping", () -> c.frogMaskWarnForagingOnly, v -> c.frogMaskWarnForagingOnly = v);
                    b.slider("    Cooldown (s)", 5f, 300f, () -> (float) c.frogMaskWarnCooldown,
                            v -> c.frogMaskWarnCooldown = Math.round(v), 0);
                });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (cfg().frogMask && level.getBlockState(pos).is(BlockTags.LOGS)) lastLogClick = System.currentTimeMillis();
            return InteractionResult.PASS;
        });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        text = "";
        ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean worn = ID.equals(ItemUtil.skyblockId(helmet));
        ItemStack mask = worn ? helmet : null;
        if (mask == null) {
            for (ItemStack s : mc.player.getInventory()) {
                if (!s.isEmpty() && ID.equals(ItemUtil.skyblockId(s))) { mask = s; break; }
            }
        }
        if (mask == null) return;
        String region = null;
        for (String line : ItemUtil.loreLines(mask)) {
            Matcher m = REGION.matcher(StringUtil.stripTrim(line));
            if (m.matches()) { region = m.group(1).trim(); break; }
        }
        if (region == null) return;
        boolean park = SkyblockUtils.isInArea("The Park");

        if (c.frogMaskWarning && park) {
            long now = System.currentTimeMillis();
            String here = subArea();
            boolean chopping = now - lastLogClick < 5000;
            if (!here.isEmpty() && !here.equalsIgnoreCase(region) && now - lastWarn > c.frogMaskWarnCooldown * 1000L
                    && (!c.frogMaskWarnForagingOnly || chopping)) {
                lastWarn = now;
                mc.gui.setTimes(0, 40, 10);
                mc.gui.setSubtitle(Component.literal("§eFrog Mask boosts §a" + region));
                mc.gui.setTitle(Component.literal("§cWrong Region!"));
            }
        }

        if (c.frogMaskDisplay && (!c.frogMaskDisplayParkOnly || park)) {
            long now = System.currentTimeMillis();
            long next = SB_EPOCH + (Math.floorDiv(now - SB_EPOCH, DAY_MS) + 1) * DAY_MS;
            long left = Math.max(0, next - now) / 1000;
            maskIcon = mask;
            text = "§5Frog Mask§6 - §a" + region + " §6for §b" + (left / 60) + "m " + (left % 60) + "s";
        }
    }

    static String subArea() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(line);
            if (s.startsWith("⏣") || s.startsWith("ф")) return s.substring(1).trim();
        }
        return "";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = text;
        if (t.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    private static ItemStack icon() {
        if (maskIcon != null && !text.isEmpty()) return maskIcon;
        if (icon == null) icon = new ItemStack(Items.LILY_PAD);
        return icon;
    }

    private static int width(String t) {
        return Minecraft.getInstance().font.width(t) + 24;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String t) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(t), 20);
        g.item(icon(), 2, 2);
        g.text(mc.font, t, 21, 6, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
