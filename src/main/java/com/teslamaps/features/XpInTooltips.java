package com.teslamaps.features;

// Ported from SkyHanni's XPInInventories.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class XpInTooltips {

    private static final Pattern LEVELS = Pattern.compile("(?:Starting cost: )?(\\d+) (?:Exp|XP) Levels");

    private static Feature feature;

    private XpInTooltips() {}

    public static void register() {
        feature = Feature.of("XpInTooltips").when(() -> TeslaMapsConfig.get().xpInTooltips)
                .config("Tooltips & Value", b -> b.toggle("Your XP in Cost Tooltips", () -> TeslaMapsConfig.get().xpInTooltips,
                                v -> TeslaMapsConfig.get().xpInTooltips = v)
                        .desc("Anvil and enchanting tooltips get a \"Your XP: N\" line, green if you have enough, red if not."));
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(lines);
        });
    }

    private static void onTooltip(List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() == null) return;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).getString();
            if (!s.contains("Levels")) continue;
            Matcher m = LEVELS.matcher(StringUtil.stripTrim(s));
            if (!m.matches()) continue;
            if (!SkyblockUtils.isInSkyblock()) return;
            int need = Integer.parseInt(m.group(1));
            int have = mc.player.experienceLevel;
            lines.add(i + 1, Component.literal("§7Your XP: " + (have >= need ? "§a" : "§c") + have));
            return;
        }
    }
}
