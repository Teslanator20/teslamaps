package com.teslamaps.features;

// SkyHanni DefaultConfigFeatures / Skyblocker UpdateNotifications: options added since the last look
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.MapConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.LinkedHashSet;
import java.util.Set;

public final class NewOptions {

    private static boolean checked;

    private NewOptions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NewOptions").when(() -> !checked)
                .tick(NewOptions::tick)
                .config("Interface", b ->
                        b.toggle("Tell Me About New Options", () -> cfg().newOptionsNotify, v -> cfg().newOptionsNotify = v)
                                .desc("After an update, one chat line lists how many toggles are new. Click it (or /tmap newfeatures) to see them and turn them on per category.")
                                .tags("update", "new", "features", "changelog"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        checked = true;
        Set<String> fresh = fresh();
        if (fresh == null || fresh.isEmpty() || !cfg().newOptionsNotify) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + fresh.size() + " new option"
                        + (fresh.size() == 1 ? "" : "s") + " since your last look. §e[Show]")
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/tmap newfeatures"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open the new options")))));
    }

    /** Toggles not seen yet; the first run only records the baseline and returns null. */
    private static Set<String> fresh() {
        Set<String> all;
        try {
            all = MapConfigScreen.toggleKeys();
        } catch (RuntimeException e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("NewOptions: could not list toggles", e);
            return null;
        }
        if (cfg().knownToggles.isEmpty()) {
            cfg().knownToggles.addAll(all);
            TeslaMapsConfig.save();
            return null;
        }
        Set<String> out = new LinkedHashSet<>(all);
        out.removeAll(new java.util.HashSet<>(cfg().knownToggles));
        return out;
    }

    /** /tmap newfeatures */
    public static void open() {
        Set<String> fresh = fresh();
        Minecraft mc = Minecraft.getInstance();
        if (fresh == null || fresh.isEmpty()) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7No new options since your last look"));
            return;
        }
        MapConfigScreen.openNew(fresh);
    }

    public static void markSeen() {
        try {
            Set<String> known = new LinkedHashSet<>(cfg().knownToggles);
            known.addAll(MapConfigScreen.toggleKeys());
            cfg().knownToggles.clear();
            cfg().knownToggles.addAll(known);
            TeslaMapsConfig.save();
        } catch (RuntimeException e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("NewOptions: could not mark seen", e);
        }
    }
}
