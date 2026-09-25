package com.teslamaps.features;

// Ported from SkyHanni's ToolTooltipTweaks.kt (compact descriptions)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.List;

public final class FarmingToolTooltips {

    private static Feature feature;

    private FarmingToolTooltips() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("FarmingToolTooltips").section("Garden").when(() -> cfg().compactFarmingTooltips)
                .config("Garden", b -> b.toggle("Compact Farming Tool Tooltips",
                                () -> cfg().compactFarmingTooltips, v -> cfg().compactFarmingTooltips = v)
                        .desc("Hoes and other farming tools drop the reforge blurb, the generic counter text and the Farmhand explanation. Values stay.")
                        .tags("tooltip", "hoe", "reforge", "counter", "farmhand"));
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().compactFarmingTooltips || lines.size() < 3 || feature == null) return;
            if (!SkyblockUtils.isInSkyblock() || !FarmingSensitivity.isFarmingTool(stack)) return;
            if (!feature.active()) return;
            com.teslamaps.debug.Perf.begin("FarmingToolTooltips");
            try {
                compact(lines);
            } finally {
                com.teslamaps.debug.Perf.end();
            }
        });
    }

    private static void compact(List<Component> lines) {
        boolean farmhand = false, counter = false, reforge = false;
        Iterator<Component> it = lines.iterator();
        if (it.hasNext()) it.next();
        while (it.hasNext()) {
            Component c = it.next();
            String line = StringUtil.stripTrim(c.getString());
            if (!farmhand && line.startsWith("Bonus ") && darkGray(c)) farmhand = true;
            if (farmhand) {
                it.remove();
                farmhand = !line.isEmpty();
                continue;
            }
            if (counter) {
                if (line.startsWith("You have")) counter = false;
                else {
                    it.remove();
                    continue;
                }
            }
            if (line.equals("Logarithmic Counter") || line.equals("Collection Analysis")) counter = true;

            if (line.equals("Blessed Bonus")) reforge = true;
            if (reforge) {
                it.remove();
                reforge = !line.isEmpty() && !line.equals("chance for multiple crops.");
                continue;
            }
            if (line.equals("Bountiful Bonus")) reforge = true;
        }
    }

    private static boolean darkGray(Component c) {
        String f = ChatText.formatted(c);
        int i = f.indexOf("Bonus ");
        return i > 0 && f.substring(0, i).contains("§8");
    }
}
