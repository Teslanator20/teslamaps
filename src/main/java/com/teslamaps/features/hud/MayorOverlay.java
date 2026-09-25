package com.teslamaps.features.hud;

// Ported from SkyHanni's features/gui/MayorOverlay
import com.teslamaps.api.ElectionApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MayorOverlay {

    private static final Map<String, String> COLORS = Map.ofEntries(
            Map.entry("Aatrox", "§3"), Map.entry("Cole", "§e"), Map.entry("Diana", "§2"), Map.entry("Diaz", "§6"),
            Map.entry("Finnegan", "§c"), Map.entry("Foxy", "§d"), Map.entry("Marina", "§b"), Map.entry("Paul", "§c"),
            Map.entry("Scorpius", "§d"), Map.entry("Jerry", "§d"), Map.entry("Derpy", "§d"), Map.entry("Aura", "§b"));
    private static final List<String> SAMPLE = List.of("§6§lMAYOR OVERLAY", "§cMayor Paul", " §eEZPZ", "§bMinister Marina",
            " §6✯ §eFishing Festival", "§7New Mayor in: §e2d 4h");
    private static final int DESC_WIDTH = 200;

    private static List<String> lines = List.of();
    private static int width, timer;
    private static HudEditElement hud;

    private MayorOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("mayorOverlay", "Mayor Overlay")
                .enabled(() -> cfg().mayorOverlay)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("MayorOverlay").when(() -> cfg().mayorOverlay)
                .tick(MayorOverlay::tick)
                .hudHiddenInInventory("mayoroverlay", MayorOverlay::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Mayor Overlay");
                    b.toggle("Mayor Overlay", () -> c.mayorOverlay, v -> c.mayorOverlay = v)
                            .desc("Current mayor and minister with their perks, the running election's candidates and the time until the next mayor. Position in /tmap gui");
                    b.toggle("  Show Perks", () -> c.mayorOverlayPerks, v -> c.mayorOverlayPerks = v);
                    b.toggle("  Perk Descriptions", () -> c.mayorOverlayDesc, v -> c.mayorOverlayDesc = v);
                    b.toggle("  Show Minister", () -> c.mayorOverlayMinister, v -> c.mayorOverlayMinister = v);
                    b.toggle("  Show Candidates", () -> c.mayorOverlayCandidates, v -> c.mayorOverlayCandidates = v);
                    b.toggle("  New Mayor Countdown", () -> c.mayorOverlayNewMayor, v -> c.mayorOverlayNewMayor = v);
                });
    }

    private static String color(String name) {
        return COLORS.getOrDefault(name, "§c");
    }

    private static void tick() {
        if (timer++ % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>();
        out.add("§6§lMAYOR OVERLAY");
        if (!ElectionApi.isLoaded()) {
            out.add("§7Loading election data...");
        } else {
            person(out, "Mayor", ElectionApi.mayorFull(), c);
            if (c.mayorOverlayMinister) person(out, "Minister", ElectionApi.ministerFull(), c);
            if (c.mayorOverlayCandidates) for (ElectionApi.Person p : ElectionApi.candidates()) person(out, "Candidate", p, c);
        }
        if (c.mayorOverlayNewMayor) {
            long left = ElectionApi.nextMayorAt() - System.currentTimeMillis();
            out.add("§7New Mayor in: §e" + format(left));
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void person(List<String> out, String title, ElectionApi.Person p, TeslaMapsConfig c) {
        if (p == null || p.name().isEmpty()) return;
        out.add(color(p.name()) + title + " " + p.name());
        if (!c.mayorOverlayPerks) return;
        Minecraft mc = Minecraft.getInstance();
        for (ElectionApi.Perk k : p.perks()) {
            out.add(" " + (k.minister() ? "§6✯ " : "") + "§e" + k.name());
            if (!c.mayorOverlayDesc || k.description().isEmpty()) continue;
            for (FormattedCharSequence seq : mc.font.split(Component.literal("§7" + k.description()), DESC_WIDTH)) {
                StringBuilder sb = new StringBuilder("   §7");
                seq.accept((i, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                out.add(sb.toString());
            }
        }
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 86400) return s / 86400 + "d " + s / 3600 % 24 + "h";
        if (s >= 3600) return s / 3600 + "h " + s / 60 % 60 + "m";
        if (s >= 60) return s / 60 + "m " + s % 60 + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
