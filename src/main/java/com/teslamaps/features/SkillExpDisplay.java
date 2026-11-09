package com.teslamaps.features;

// Ported from Skysoft's SkillExpDisplay.kt, extras from SkyHanni's SkillProgress.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.GuiAccessor;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts the skill-xp segment out of the action bar and re-renders it as its own movable
 * HUD element. Lives and dies with the vanilla overlay message timer.
 */
public class SkillExpDisplay {

    private static final Pattern GAIN = Pattern.compile(
            "\\+(?<gained>[\\d.,]+) (?<skill>Combat|Farming|Fishing|Mining|Foraging|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social|Hunting) "
                    + "\\((?<progress>(?:[\\d.,]+%)|(?:[\\d.,]+[kmbKMB]?/[\\d,.]+[kmbKMB]?))\\)");

    private static final double[] XP_TABLE = {
            0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425,
            9925, 14925, 22425, 32425, 47425, 67425, 97425, 147425, 222425, 322425,
            522425, 822425, 1222425, 1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425,
            8022425, 9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425, 21222425, 23322425,
            25522425, 27822425, 30222425, 32722425, 35322425, 38072425, 40972425, 44072425, 47472425, 51172425,
            55172425, 59472425, 64072425, 68972425, 74172425, 79672425, 85472425, 91572425, 97972425, 104672425,
            111672425
    };
    private static final double[] SMALL_XP_TABLE = {
            0, 50, 150, 275, 435, 635, 885, 1200, 1600, 2100,
            2725, 3510, 4510, 5760, 7325, 9325, 11825, 14950, 18950, 23950,
            30200, 38050, 47850, 60100, 75400, 94500
    };

    private static final String PREVIEW = "§3+152.6 Foraging (52.7%)";
    private static final String PREVIEW_LEVEL = "§9[§d25§9] ";

    private static String line = null;
    private static String levelText = "";
    private static ItemStack icon = ItemStack.EMPTY;
    private static float fraction = -1;

    /** Called with the raw action bar text before filtering. Returns true when the segment should be cut. */
    public static boolean onActionBar(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.skillExpDisplay || !c.section("Skills")) { line = null; return false; }

        Matcher m = GAIN.matcher(StringUtil.strip(raw));
        if (!m.find()) return false;
        String gained = m.group("gained");
        String skill = m.group("skill");
        String progress = m.group("progress");
        build(c, gained, skill, progress);
        return true;
    }

    private static void build(TeslaMapsConfig c, String gained, String skill, String progress) {
        Integer level = SkillTracker.tabLevel(skill);
        double cur = -1, max = -1, pct = -1;
        int slash = progress.indexOf('/');
        if (slash >= 0) {
            cur = parseAmount(progress.substring(0, slash));
            max = parseAmount(progress.substring(slash + 1));
            if (max > 0) pct = cur / max * 100;
        } else {
            pct = parseAmount(progress.substring(0, progress.length() - 1));
            double[] table = skill.equals("Runecrafting") || skill.equals("Social") ? SMALL_XP_TABLE : XP_TABLE;
            if (level != null && level >= 0 && level + 1 < table.length) {
                max = table[level + 1] - table[level];
                cur = max * pct / 100;
            }
        }

        String shown = progress;
        if (c.skillExpFormat == 1 && pct >= 0) shown = trimZero(String.format(Locale.ROOT, "%.1f", pct)) + "%";
        else if (c.skillExpFormat == 2 && max > 0) shown = String.format(Locale.ROOT, "%,d/%,d", (long) cur, (long) max);

        StringBuilder sb = new StringBuilder("§3+").append(gained).append(' ').append(skill).append(" (").append(shown).append(')');
        if (c.skillExpActionsLeft && max > 0 && cur < max) {
            double gain = parseAmount(gained);
            if (gain > 0) sb.append(" §6").append(String.format(Locale.ROOT, "%,d", (long) Math.ceil((max - cur) / gain))).append(" left");
        }
        line = sb.toString();
        levelText = c.skillExpLevel && level != null ? "§9[§d" + level + "§9] " : "";
        icon = c.skillExpIcon ? new ItemStack(iconFor(skill)) : ItemStack.EMPTY;
        fraction = pct >= 0 ? (float) Math.min(1.0, pct / 100) : -1;
    }

    private static net.minecraft.world.item.Item iconFor(String skill) {
        return switch (skill) {
            case "Combat" -> Items.STONE_SWORD;
            case "Farming" -> Items.GOLDEN_HOE;
            case "Fishing" -> Items.FISHING_ROD;
            case "Mining" -> Items.STONE_PICKAXE;
            case "Foraging" -> Items.JUNGLE_SAPLING;
            case "Enchanting" -> Items.ENCHANTING_TABLE;
            case "Alchemy" -> Items.BREWING_STAND;
            case "Carpentry" -> Items.CRAFTING_TABLE;
            case "Taming" -> Items.POLAR_BEAR_SPAWN_EGG;
            case "Hunting" -> Items.LEAD;
            case "Runecrafting" -> Items.MAGMA_CREAM;
            default -> Items.EMERALD;
        };
    }

    private static double parseAmount(String s) {
        s = s.replace(",", "").trim();
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k') mult = 1e3;
        else if (last == 'm') mult = 1e6;
        else if (last == 'b') mult = 1e9;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String trimZero(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.skillExpDisplay || line == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyblockUtils.isInSkyblock()) return;
        if (((GuiAccessor) mc.gui.hud).teslamaps$overlayMessageTime() <= 0) { line = null; return; }

        draw(g, mc, c, x(mc, c), y(mc, c), c.skillExpDisplayScale, levelText, icon, line, fraction);
    }

    public static void drawPreview(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        draw(g, mc, c, x, y, scale, c.skillExpLevel ? PREVIEW_LEVEL : "",
                c.skillExpIcon ? new ItemStack(Items.JUNGLE_SAPLING) : ItemStack.EMPTY,
                c.skillExpActionsLeft ? PREVIEW + " §6340 left" : PREVIEW, 0.527f);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, TeslaMapsConfig c, int x, int y, float scale,
                             String level, ItemStack stack, String text, float frac) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int tx = 0;
        if (!level.isEmpty()) {
            g.text(mc.font, level, 0, 0, 0xFFFFFFFF, true);
            tx += mc.font.width(level);
        }
        if (!stack.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(tx, -0.5f);
            g.pose().scale(0.5625f, 0.5625f);
            g.item(stack, 0, 0);
            g.pose().popMatrix();
            tx += 11;
        }
        g.text(mc.font, text, tx, 0, 0xFFFFFFFF, true);
        if (c.skillExpBar && frac >= 0) {
            int w = Math.max(20, c.skillExpBarWidth);
            TeslaGui.progressTrack(g, 0, 11, w, 5, frac, true, TeslaMapsConfig.parseColor(c.skillExpBarColor));
        }
        g.pose().popMatrix();
    }

    private static int contentWidth(Minecraft mc, TeslaMapsConfig c) {
        String text = line != null ? line : (c.skillExpActionsLeft ? PREVIEW + " §6340 left" : PREVIEW);
        String level = line != null ? levelText : (c.skillExpLevel ? PREVIEW_LEVEL : "");
        int w = mc.font.width(text) + mc.font.width(level) + (c.skillExpIcon ? 11 : 0);
        if (c.skillExpBar) w = Math.max(w, Math.max(20, c.skillExpBarWidth));
        return w;
    }

    public static int x(Minecraft mc, TeslaMapsConfig c) {
        if (c.skillExpDisplayX >= 0) return c.skillExpDisplayX;
        int w = (int) (contentWidth(mc, c) * c.skillExpDisplayScale);
        return (mc.getWindow().getGuiScaledWidth() - w) / 2;
    }

    public static int y(Minecraft mc, TeslaMapsConfig c) {
        if (c.skillExpDisplayY >= 0) return c.skillExpDisplayY;
        return mc.getWindow().getGuiScaledHeight() - 76;
    }

    public static int width(Minecraft mc, float scale) {
        return (int) (contentWidth(mc, TeslaMapsConfig.get()) * scale);
    }

    public static int height(float scale) {
        return (int) ((TeslaMapsConfig.get().skillExpBar ? 16 : 9) * scale);
    }
}
