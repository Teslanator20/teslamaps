package com.teslamaps.features.hud;

// Ported from GanKura's QuiverScanner / QuiverHud / QuiverAlertHandler
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuiverHud {

    private static final int MENU_SLOT = 8;
    private static final Pattern ACTIVE = Pattern.compile("Active Arrow: (.*) \\(([\\d,]+)\\)");
    private static final Pattern REMAINING = Pattern.compile("Arrows Remaining: ([\\d,]+)");
    private static final List<String> SAMPLE = List.of("§b1,234x §fFlint Arrows");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static String arrow;
    private static int count = -1;
    private static String lastArrow;
    private static int lastCount = -1;

    private QuiverHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("quiverHud", "Quiver")
                .enabled(() -> cfg().quiverHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("QuiverHud").when(() -> cfg().quiverHud || cfg().quiverLowWarning)
                .tick(QuiverHud::tick)
                .hudHiddenInInventory("quiverhud", QuiverHud::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Quiver");
                    b.toggle("Quiver Display", () -> c.quiverHud, v -> c.quiverHud = v)
                            .desc("Selected arrow type and how many are left, read from the SkyBlock Menu. Position in /tmap gui.");
                    b.toggle("  Only While Holding A Bow", () -> c.quiverOnlyBow, v -> c.quiverOnlyBow = v);
                    b.toggle("Low Arrow Warning", () -> c.quiverLowWarning, v -> c.quiverLowWarning = v)
                            .desc("Title and sound when the quiver drops below 50 and 10 arrows. Not on arrow swaps or refills.");
                });
    }

    private static void tick() {
        if (++timer % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock() || !scan(mc.player.getInventory().getItem(MENU_SLOT))) {
            arrow = null;
            count = -1;
            lastArrow = null;
            lastCount = -1;
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        if (c.quiverLowWarning && lastCount >= 0 && arrow.equals(lastArrow) && count < lastCount) {
            if (lastCount > 10 && count <= 10) alert(mc, "§c§lARROWS ALMOST OUT", 1.4f, true);
            else if (lastCount > 50 && count <= 50) alert(mc, "§e§lLOW ON ARROWS", 1.0f, false);
        }
        lastArrow = arrow;
        lastCount = count;

        ItemStack held = mc.player.getMainHandItem();
        boolean bow = held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem;
        if (!c.quiverHud || (c.quiverOnlyBow && !bow)) {
            lines = List.of();
            return;
        }
        String col = count <= 10 ? "§c" : count <= 50 ? "§e" : "§b";
        String name = arrow.contains("§") ? arrow : "§f" + arrow;
        if (count != 1 && !StringUtil.strip(name).endsWith("s")) name += "s";
        lines = List.of(col + String.format(Locale.ROOT, "%,d", count) + "x " + name);
        width = TextHud.width(lines);
    }

    private static boolean scan(ItemStack menu) {
        if (menu.isEmpty()) return false;
        List<String> lore = ItemUtil.loreLines(menu);
        for (String line : lore) {
            Matcher m = ACTIVE.matcher(StringUtil.strip(line));
            if (m.find()) return set(m.group(1).trim(), m.group(2));
        }
        for (String line : lore) {
            Matcher m = REMAINING.matcher(StringUtil.strip(line));
            if (m.find()) return set(StringUtil.stripTrim(menu.getHoverName().getString()), m.group(1));
        }
        return false;
    }

    private static boolean set(String name, String amount) {
        if (name.isBlank()) return false;
        try {
            count = Integer.parseInt(amount.replace(",", ""));
        } catch (NumberFormatException e) {
            return false;
        }
        arrow = name;
        return true;
    }

    private static void alert(Minecraft mc, String title, float pitch, boolean critical) {
        mc.gui.hud.setTitle(Component.literal(title));
        mc.gui.hud.setSubtitle(Component.literal("§f" + String.format(Locale.ROOT, "%,d", count) + " left"));
        LoudSound.play(critical ? SoundEvents.ANVIL_LAND : SoundEvents.ARROW_HIT_PLAYER, 1.0f, pitch);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
