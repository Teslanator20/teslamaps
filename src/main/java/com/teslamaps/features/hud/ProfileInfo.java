package com.teslamaps.features.hud;

// SkyHanni's ScoreboardElementProfile / ScoreboardElementSkyBlockXP and SkyBlockXPBar
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileInfo {

    private static final Pattern LEVEL = Pattern.compile("^SB Level: \\[(\\d+)] (\\d+)/100.*");
    private static final Pattern PROFILE = Pattern.compile("^Profile: ([A-Za-z0-9 ]+?)\\s*([♲Ⓑ☀])?.*$");
    private static final List<String> SAMPLE = List.of("§7♲ §fIronman §8(Apple)", "§7SB Level: §b287 §8(§b26§3/§b100§8)");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;
    private static int level = -1, xp;

    private ProfileInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("profileInfo", "Profile Info")
                .enabled(() -> cfg().profileInfoHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("ProfileInfo").when(() -> cfg().profileInfoHud || cfg().sbLevelXpBar)
                .tick(ProfileInfo::tick)
                .hudHiddenInInventory("profileinfo", ProfileInfo::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Profile Info");
                    b.toggle("Profile Info HUD", () -> c.profileInfoHud, v -> c.profileInfoHud = v)
                            .desc("Profile type (♲ Ironman, ☀ Stranded, Ⓑ Bingo, Normal) and SkyBlock level from the tab list. Position in /tmap gui.");
                    b.toggle("  Profile Type", () -> c.profileInfoType, v -> c.profileInfoType = v);
                    b.toggle("    Show Profile Name", () -> c.profileInfoName, v -> c.profileInfoName = v);
                    b.toggle("  SkyBlock Level", () -> c.profileInfoLevel, v -> c.profileInfoLevel = v);
                    b.toggle("SkyBlock Level XP Bar", () -> c.sbLevelXpBar, v -> c.sbLevelXpBar = v)
                            .desc("The vanilla XP bar shows your SkyBlock level and x/100 XP instead. Not in the Rift or dungeons.");
                });
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, ProfileInfo::wrap);
        HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, ProfileInfo::wrap);
    }

    private static HudElement wrap(HudElement original) {
        return (g, d) -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null || !xpBarActive() || com.teslamaps.features.GlobalRender.off()) {
                original.extractRenderState(g, d);
                return;
            }
            int lvl = p.experienceLevel;
            float prog = p.experienceProgress;
            p.experienceLevel = level;
            p.experienceProgress = xp / 100f;
            try {
                original.extractRenderState(g, d);
            } finally {
                p.experienceLevel = lvl;
                p.experienceProgress = prog;
            }
        };
    }

    private static boolean xpBarActive() {
        TeslaMapsConfig c = cfg();
        return c.sbLevelXpBar && level >= 0 && c.section("Interface") && !ignoredArea;
    }

    private static boolean ignoredArea;

    private static void tick() {
        if (++timer % 10 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            level = -1;
            lines = List.of();
            return;
        }
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        ignoredArea = area.contains("rift") || area.contains("catacombs");
        int lvl = -1, x = 0;
        String t = null, n = null;
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (lvl < 0 && l.startsWith("SB Level:")) {
                Matcher m = LEVEL.matcher(l);
                if (m.matches()) {
                    lvl = Integer.parseInt(m.group(1));
                    x = Integer.parseInt(m.group(2));
                }
            } else if (n == null && l.startsWith("Profile: ")) {
                Matcher m = PROFILE.matcher(l);
                if (m.matches()) {
                    n = m.group(1).trim();
                    t = m.group(2);
                }
            }
        }
        if (t == null) {
            for (String raw : ScoreboardUtils.getScoreboardLines()) {
                String l = ScoreboardUtils.cleanLine(raw);
                if (l.contains("♲ Ironman")) t = "♲";
                else if (l.contains("☀ Stranded")) t = "☀";
                else if (l.contains("Ⓑ Bingo")) t = "Ⓑ";
                if (t != null) break;
            }
        }
        level = lvl;
        xp = x;

        TeslaMapsConfig c = cfg();
        if (!c.profileInfoHud) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>(2);
        if (c.profileInfoType) {
            String line = switch (t == null ? "" : t) {
                case "♲" -> "§7♲ §fIronman";
                case "☀" -> "§a☀ §fStranded";
                case "Ⓑ" -> "§6Ⓑ §fBingo";
                default -> "§eNormal";
            };
            if (c.profileInfoName && n != null) line += " §8(" + n + ")";
            out.add(line);
        }
        if (c.profileInfoLevel && lvl >= 0) {
            out.add("§7SB Level: " + levelColor(lvl) + lvl + " §8(§b" + x + "§3/§b100§8)");
        }
        lines = out;
        width = TextHud.width(out);
    }

    public static String levelColor(int lvl) {
        String[] colors = {"§7", "§f", "§e", "§a", "§2", "§b", "§3", "§9", "§d", "§5", "§6", "§c", "§4"};
        return colors[Math.min(colors.length - 1, lvl / 40)];
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().profileInfoHud || mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
