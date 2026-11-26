package com.teslamaps.features;

// Ported from SkyHanni's WarpMenuUniques.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class HoppityWarpUniques {

    private static final int MAX_EGGS = 15;
    private static final Pattern EGG_FOUND = Pattern.compile(
            "(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) "
            + "(?:Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg");
    private static final Set<String> ISLANDS = Set.of(
            "Hub", "The Farming Islands", "The Park", "Gold Mine", "Deep Caverns", "Dwarven Mines",
            "Crystal Hollows", "Spider's Den", "The End", "Crimson Isle", "Dungeon Hub", "Backwater Bayou", "Galatea");

    private static Feature feature;

    private HoppityWarpUniques() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HoppityWarpUniques").when(() -> cfg().hoppityWarpUniques)
                .serverChat(HoppityWarpUniques::onChat)
                .config("Tooltips & Value", b -> {
                    b.toggle("Hoppity Uniques In Warp Menu", () -> cfg().hoppityWarpUniques, v -> cfg().hoppityWarpUniques = v)
                            .desc("Fast Travel tooltips show how many unique Hoppity egg spots you found on that island (x/15). Counts eggs found while this is on.");
                    b.toggle("  Hide When Maxed", () -> cfg().hoppityWarpHideMaxed, v -> cfg().hoppityWarpHideMaxed = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(stack, lines);
        });
    }

    // Hoppity's Hunt runs through the three spring months
    private static boolean isSpring() {
        long sec = System.currentTimeMillis() / 1000L - 1560275700L;
        return (sec % 446400L) / 37200L < 3;
    }

    private static void onChat(String raw) {
        String text = StringUtil.strip(raw);
        if (!text.contains("Chocolate") || !EGG_FOUND.matcher(text).find()) return;
        String area = SkyblockUtils.getArea();
        if (area.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pos = HoppityEggESP.nearestEgg(8);
        if (pos == null) pos = mc.player.position();
        List<String> spots = cfg().hoppityUniqueEggs.computeIfAbsent(area, k -> new ArrayList<>());
        for (String s : spots) {
            String[] p = s.split(",");
            if (p.length != 3) continue;
            try {
                double dx = Double.parseDouble(p[0]) - pos.x, dy = Double.parseDouble(p[1]) - pos.y, dz = Double.parseDouble(p[2]) - pos.z;
                if (dx * dx + dy * dy + dz * dz < 16) return;
            } catch (NumberFormatException ignored) {}
        }
        spots.add((int) Math.floor(pos.x) + "," + (int) Math.floor(pos.y) + "," + (int) Math.floor(pos.z));
        TeslaMapsConfig.save();
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (lines.size() < 2 || !isSpring()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Fast Travel")) return;
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        int dash = name.indexOf(" - ");
        if (dash > 0) name = name.substring(0, dash).trim();
        String island = switch (name) {
            case "SkyBlock Hub" -> "Hub";
            case "The Barn" -> "The Farming Islands";
            default -> name;
        };
        if (!ISLANDS.contains(island)) return;
        List<String> spots = cfg().hoppityUniqueEggs.get(island);
        int n = spots == null ? 0 : Math.min(spots.size(), MAX_EGGS);
        if (n >= MAX_EGGS && cfg().hoppityWarpHideMaxed) return;
        lines.add(Math.min(2, lines.size()), Component.literal("§7Collected Hoppity Eggs: " + (n == MAX_EGGS ? "§a" : "") + n + "/" + MAX_EGGS));
    }
}
