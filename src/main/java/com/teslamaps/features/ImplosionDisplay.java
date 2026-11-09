package com.teslamaps.features;

// After SkyblockAddons' SPIRIT_SCEPTRE_DISPLAY (PlayerListener / RenderListener)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImplosionDisplay {

    private static final Pattern HIT = Pattern.compile("^Your (?:Implosion|Guided Bat|Spirit Sceptre) hit (\\d+) enem(?:y|ies) for ([\\d,.]+) damage\\.?$");
    private static final Set<String> ITEMS = Set.of("HYPERION", "VALKYRIE", "SCYLLA", "ASTRAEA", "BAT_WAND", "STARRED_BAT_WAND");

    private static Feature feature;
    private static String lastLine;
    private static String hitsText, damageText;
    private static String name;
    private static ItemStack icon = ItemStack.EMPTY;
    private static boolean holding;
    private static Object lastLevel;

    private ImplosionDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("implosionDisplay", "Implosion Damage").section("Combat")
                .enabled(() -> cfg().implosionDisplay)
                .size(() -> 120, () -> 34)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, ItemStack.EMPTY, "§6Hyperion", "§7Hit §e3 §7enemies", "§c1,234,567 §7damage"));
        feature = Feature.of("ImplosionDisplay").section("Combat").when(() -> cfg().implosionDisplay)
                .tick(ImplosionDisplay::tick)
                .serverChat(ImplosionDisplay::onChat)
                .hudHiddenInInventory("implosiondisplay", ImplosionDisplay::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Wither Blades");
                    b.toggle("Implosion Damage HUD", () -> c.implosionDisplay, v -> c.implosionDisplay = v)
                            .desc("While a wither blade or Spirit Sceptre is in hand: enemies hit and damage of the last Implosion / Guided Bat. Move it in /tmap gui.")
                            .tags("implosion", "hyperion", "spirit sceptre", "damage", "aoe");
                    b.toggle("  Hide Chat Message", () -> c.implosionDisplayHideChat, v -> c.implosionDisplayHideChat = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hitsText = null;
        }
        ItemStack held = mc.player.getMainHandItem();
        holding = ITEMS.contains(ItemUtil.skyblockId(held));
        if (holding && held != icon) {
            icon = held;
            name = held.getHoverName().getString();
        }
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("Your ")) return;
        Matcher m = HIT.matcher(t);
        if (!m.matches()) return;
        lastLine = t;
        int n = Integer.parseInt(m.group(1));
        hitsText = "§7Hit §e" + n + (n == 1 ? " §7enemy" : " §7enemies");
        damageText = "§c" + m.group(2) + " §7damage";
    }

    /** From ChatMixin: hide the line that was just shown in the HUD. */
    public static boolean hide(String text) {
        if (lastLine == null || feature == null || !feature.active() || !cfg().implosionDisplayHideChat) return false;
        return lastLine.equals(StringUtil.stripTrim(text));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!holding || hitsText == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        draw(g, mc, c.implosionDisplayX, c.implosionDisplayY, c.implosionDisplayScale, icon, name, hitsText, damageText);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, ItemStack stack, String title, String hits, String dmg) {
        int w = 24 + Math.max(mc.font.width(title), Math.max(mc.font.width(hits), mc.font.width(dmg))) + 4;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(s, s);
        HudStyle.panel(g, 0, 0, w, 34);
        if (!stack.isEmpty()) g.item(stack, 4, 9);
        g.text(mc.font, title, 24, 3, 0xFFFFFFFF, true);
        g.text(mc.font, hits, 24, 13, 0xFFFFFFFF, true);
        g.text(mc.font, dmg, 24, 23, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
