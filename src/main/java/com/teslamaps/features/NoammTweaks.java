package com.teslamaps.features;

// Ported from NoammAddons' misc/TimeChanger.kt, misc/ArrowFix.kt, misc/NameTagTweaks.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NoammTweaks {

    public static final String[] TIME_MODES = {"Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Real Time"};
    private static final long[] TIME_VALUES = {1000L, 6000L, 12000L, 13000L, 18000L, 23000L};

    private static final Set<String> bowCache = new HashSet<>();
    private static final Set<String> notBowCache = new HashSet<>();

    private NoammTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NoammTweaks")
                .config("Render", b -> {
                    b.label("Time Changer");
                    b.toggle("Time Changer", () -> cfg().timeChanger, v -> cfg().timeChanger = v)
                            .desc("Fixed client-side time of day.");
                    b.dropdown("  Time", TIME_MODES,
                            () -> TIME_MODES[Math.max(0, Math.min(TIME_MODES.length - 1, cfg().timeChangerMode))],
                            v -> cfg().timeChangerMode = Math.max(0, List.of(TIME_MODES).indexOf(v)));
                    b.label("Nametag Tweaks");
                    b.toggle("Hide Nametag Background", () -> cfg().nametagNoBackground, v -> cfg().nametagNoBackground = v)
                            .desc("No black box behind nametags.");
                    b.toggle("Shadowed Nametag", () -> cfg().nametagShadow, v -> cfg().nametagShadow = v)
                            .desc("Text shadow on nametag labels.");
                    b.toggle("Hide Dinnerbone Nametag", () -> cfg().hideDinnerboneNametag, v -> cfg().hideDinnerboneNametag = v)
                            .desc("Hides the nametag of entities named \"Dinnerbone\".");
                })
                .config("Combat", b -> {
                    b.label("Arrow Fix");
                    b.toggle("No Shortbow Pullback", () -> cfg().arrowFix, v -> cfg().arrowFix = v)
                            .desc("Skips the bow draw animation on shortbows.");
                });
    }

    public static boolean timeChangerOn() {
        return cfg().timeChanger;
    }

    public static long time() {
        int mode = cfg().timeChangerMode;
        if (mode >= 0 && mode < TIME_VALUES.length) return TIME_VALUES[mode];
        LocalTime now = LocalTime.now();
        long ticks = (now.getHour() * 1000L) + (long) (now.getMinute() * 16.66) - 6000;
        if (ticks < 0) ticks += 24000;
        return ticks;
    }

    public static boolean arrowFixOn() {
        return cfg().arrowFix;
    }

    public static boolean isShortbow(ItemStack item) {
        if (item == null || item.isEmpty() || !item.is(Items.BOW)) return false;
        String id = ItemUtil.skyblockId(item);
        if (bowCache.contains(id)) return true;
        if (notBowCache.contains(id)) return false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return false;
        List<Component> lines = lore.lines();
        for (int i = lines.size() - 3; i >= 0; i--) {
            if (lines.get(i).getString().contains("Shortbow: Instantly shoots!")) {
                bowCache.add(id);
                return true;
            }
        }
        notBowCache.add(id);
        return false;
    }

    public static boolean hideDinnerbone(Entity entity) {
        if (!cfg().hideDinnerboneNametag) return false;
        Component name = entity.getCustomName();
        return name != null && name.getString().equalsIgnoreCase("Dinnerbone");
    }
}
