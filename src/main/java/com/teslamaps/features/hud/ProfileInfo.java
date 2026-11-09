package com.teslamaps.features.hud;

// SkyHanni's SkyBlockXPBar
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileInfo {

    private static final Pattern LEVEL = Pattern.compile("^SB Level: \\[(\\d+)] (\\d+)/100.*");

    private static int timer;
    private static int level = -1, xp;
    private static boolean ignoredArea;

    private ProfileInfo() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ProfileInfo").when(() -> cfg().sbLevelXpBar)
                .tick(ProfileInfo::tick)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("SkyBlock Level");
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

    private static void tick() {
        if (++timer % 10 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            level = -1;
            return;
        }
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        ignoredArea = area.contains("rift") || area.contains("catacombs");
        int lvl = -1, x = 0;
        for (String raw : TabListUtils.getTabListLines()) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("SB Level:")) {
                Matcher m = LEVEL.matcher(l);
                if (m.matches()) {
                    lvl = Integer.parseInt(m.group(1));
                    x = Integer.parseInt(m.group(2));
                    break;
                }
            }
        }
        level = lvl;
        xp = x;
    }
}
