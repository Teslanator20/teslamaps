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
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Mayor Overlay");
                    b.toggle("Mayor Overlay", () -> c.mayorOverlay, v -> c.mayorOverlay = v)
                            .desc("Current mayor and minister with their perks, the running election's candidates and the time until the next mayor. Position in /tmap gui");
                    b.toggle("  Show Perks", () -> c.mayorOverlayPerks, v -> c.mayorOverlayPerks = v);
                    b.toggle("  Perk Descriptions", () -> c.mayorOverlayDesc, v -> c.mayorOverlayDesc = v);
                    b.toggle("  Show Minister", () -> c.mayorOverlayMinister, v -> c.mayorOverlayMinister = v);
                    b.toggle("  Show Candidates", () -> c.mayorOverlayCandidates, v -> c.mayorOverlayCandidates = v);
                    b.toggle("  New Mayor Countdown", () -> c.mayorOverlayNewMayor, v -> c.mayorOverlayNewMayor = v);
                    b.toggle("  Jerry Perkpocalypse Mayor", () -> c.mayorOverlayJerry, v -> c.mayorOverlayJerry = v)
                            .desc("While Jerry is mayor: the current Perkpocalypse mayor and the time until it changes. Open /calendar once to read it");
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
        if (c.mayorOverlayJerry) scanCalendar();
        List<String> out = new ArrayList<>();
        out.add("§6§lMAYOR OVERLAY");
        if (!ElectionApi.isLoaded()) {
            out.add("§7Loading election data...");
        } else {
            person(out, "Mayor", ElectionApi.mayorFull(), c);
            if (c.mayorOverlayMinister) person(out, "Minister", ElectionApi.ministerFull(), c);
            if (c.mayorOverlayJerry && "Jerry".equals(ElectionApi.mayor())) jerry(out, c);
            if (c.mayorOverlayCandidates) for (ElectionApi.Person p : ElectionApi.candidates()) person(out, "Candidate", p, c);
        }
        if (c.mayorOverlayNewMayor) {
            long left = ElectionApi.nextMayorAt() - System.currentTimeMillis();
            out.add("§7New Mayor in: §e" + format(left));
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static final long SIX_HOURS = 6 * 3_600_000L;
    private static final long YEAR_MS = 446400000L;
    private static final Map<String, String> PERK_MAYOR = perkMayors();

    private static Map<String, String> perkMayors() {
        Map<String, String> m = new java.util.HashMap<>();
        String[][] t = {
                {"Aatrox", "SLASHED Pricing", "Slayer XP Buff", "Pathfinder"},
                {"Cole", "Prospection", "Mining XP Buff", "Mining Fiesta", "Molten Forge"},
                {"Diana", "Huntress' Intuition", "Mythological Ritual", "Pet XP Buff", "Sharing is Caring"},
                {"Diaz", "Volume Trading", "Shopping Spree", "Stock Exchange", "Long Term Investment"},
                {"Finnegan", "Pelt-pocalypse", "Grand Feast", "GOATed", "Blooming Business", "Pest Eradicator"},
                {"Foxy", "Sweet Benevolence", "A Time for Giving", "Chivalrous Carnival", "Extra Event"},
                {"Marina", "Fishing XP Buff", "Luck of the Sea 2.0", "Fishing Festival", "Double Trouble"},
                {"Paul", "Marauder", "EZPZ", "Benediction"},
                {"Scorpius", "Bribe", "Darker Auctions"},
                {"Derpy", "TURBO MINIONS!!!", "QUAD TAXES!!!", "DOUBLE MOBS HP!!!", "MOAR SKILLZ!!!"},
                {"Aura", "Fundraising", "Minion Union", "Universal Income", "Work Better", "Work Harder", "Work Smarter"}};
        for (String[] row : t) for (int i = 1; i < row.length; i++) m.put(row[i], row[0]);
        return m;
    }

    private static void jerry(List<String> out, TeslaMapsConfig c) {
        long now = System.currentTimeMillis();
        if (c.jerryMayorName.isEmpty() || c.jerryMayorUntil <= now) {
            out.add("§dPerkpocalypse §cunknown §7(open /calendar)");
            return;
        }
        out.add(color(c.jerryMayorName) + "Perkpocalypse " + c.jerryMayorName + " §7(§6" + format(c.jerryMayorUntil - now) + "§7)");
    }

    // calendar: "Mayor Jerry" item, first perk a couple of lines under "Perkpocalypse Perks:"
    private static void scanCalendar() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
        if (!com.teslamaps.utils.StringUtil.stripTrim(cs.getTitle().getString()).equals("Calendar and Events")) return;
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            net.minecraft.world.item.ItemStack st = slot.getItem();
            if (st.isEmpty() || !com.teslamaps.utils.StringUtil.stripTrim(st.getHoverName().getString()).equals("Mayor Jerry")) continue;
            List<String> lore = com.teslamaps.utils.ItemUtil.loreLines(st);
            for (int i = 0; i < lore.size(); i++) {
                if (!com.teslamaps.utils.StringUtil.stripTrim(lore.get(i)).equals("Perkpocalypse Perks:")) continue;
                for (int j = i + 1; j < Math.min(lore.size(), i + 5); j++) {
                    String mayor = mayorForPerk(com.teslamaps.utils.StringUtil.stripTrim(lore.get(j)));
                    if (mayor == null) continue;
                    long next = ElectionApi.nextMayorAt();
                    long until = next - YEAR_MS;
                    long now = System.currentTimeMillis();
                    while (until <= now) until += SIX_HOURS;
                    TeslaMapsConfig c = cfg();
                    if (!mayor.equals(c.jerryMayorName) || c.jerryMayorUntil != Math.min(until, next)) {
                        c.jerryMayorName = mayor;
                        c.jerryMayorUntil = Math.min(until, next);
                        TeslaMapsConfig.save();
                    }
                    return;
                }
                return;
            }
        }
    }

    private static String mayorForPerk(String line) {
        String m = PERK_MAYOR.get(line);
        if (m != null) return m;
        return line.startsWith("Extra Event") ? "Foxy" : null;
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
