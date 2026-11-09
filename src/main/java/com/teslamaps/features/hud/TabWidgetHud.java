package com.teslamaps.features.hud;

// Widget headers from SkyHanni's data/model/TabWidget.kt, display idea from features/gui/TabWidgetDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Picked tab widgets and single scoreboard values as one movable block. */
public final class TabWidgetHud {

    private static final Map<String, Pattern> WIDGETS = new LinkedHashMap<>();
    private static final List<Pattern> BOUNDARIES = new ArrayList<>();
    private static final Map<String, String> SCOREBOARD = new LinkedHashMap<>();
    private static final int MAX_LINES = 12;

    static {
        w("Area", "(?:Area|Dungeon): .*");
        w("Server", "Server: .*");
        w("Gems", "Gems: .*");
        w("Fairy Souls", "Fairy Souls: .*");
        w("Profile", "Profile: .*");
        w("SB Level", "SB Level: .*");
        w("Bank", "Bank: .*");
        w("Interest", "Interest: .*");
        w("Soulflow", "Soulflow: .*");
        w("Pet", "Pet:");
        w("Pet Training", "Pet Training:");
        w("Kat", "Kat: .*");
        w("Fire Sales", "Fire Sales: .*");
        w("Election", "Election: .*");
        w("Event", "Event: .*");
        w("Skills", "Skills: ?[\\d.]*");
        w("Stats", "Stats:");
        w("Minions", "Minions: \\d+/\\d+");
        w("North Stars", "North Stars: .*");
        w("Collection", "Collection:");
        w("Jacob's Contest", "Jacob's Contest:.*");
        w("Slayer", "Slayer:");
        w("Daily Quests", "Daily Quests:");
        w("Active Effects", "Active Effects: .*");
        w("Bestiary", "Bestiary:");
        w("Essence", "Essence:.*");
        w("Forges", "Forges:");
        w("Timers", "Timers:");
        w("Trapper", "Trapper:");
        w("Commissions", "Commissions:");
        w("Powders", "Powders:");
        w("Crystals", "Crystals:");
        w("Unclaimed Chests", "Unclaimed chests: .*");
        w("Park Weather", "(?:Thunder|Rain): .*");
        w("Broodmother", "Broodmother: .*");
        w("Eyes Placed", "Eyes placed: .*|Dragon spawned!|Egg respawning!");
        w("Protector", "Protector: .*");
        w("Dragon", "Dragon: \\(.*\\)");
        w("Volcano", "Volcano: .*");
        w("Reputation", "(?:Barbarian|Mage) Reputation:");
        w("Faction Quests", "Faction Quests:");
        w("Trophy Fish", "Trophy Fish:");
        w("Composter", "Composter:");
        w("Garden Level", "Garden Level: .*");
        w("Copper", "Copper: .*");
        w("Sowdust", "Sowdust: .*");
        w("Pests", "Pests:");
        w("Pest Traps", "Pest Traps: .*");
        w("Visitors", "Visitors: .*");
        w("Crop Milestones", "Crop Milestones:");
        w("Pet Sitter", "Pet Sitter:.*");
        w("Dungeons", "Dungeons:");
        w("Puzzles", "Puzzles: .*");
        w("Frozen Corpses", "Frozen Corpses:");
        w("Scrap", "Scrap: .*");
        w("Event Trackers", "Event Trackers:");
        w("Salts", "Salts:");
        w("Whispers", "\\w+ Whispers: .*");
        w("Shard Traps", "Shard Traps.*");
        w("Pity", "Pity:");
        w("Pickaxe Ability", "Pickaxe Ability:");
        for (String b : new String[]{"Players \\(\\d+\\)", "Info", "Coop .*", "Guests \\(\\d+\\)", "Island",
                "Player Stats", "Account Info", "Dungeon Stats", "Party.*", "Good to know:", "Advertisement:",
                "Island closes in: .*", "Opened Rooms: .*", "Full Traps: .*", "No Bait: .*"})
            BOUNDARIES.add(Pattern.compile(b));
        SCOREBOARD.put("Bits (Scoreboard)", "Bits:");
        SCOREBOARD.put("Heat (Scoreboard)", "Heat:");
        SCOREBOARD.put("Cold (Scoreboard)", "Cold:");
        SCOREBOARD.put("North Stars (Scoreboard)", "North Stars:");
        SCOREBOARD.put("Copper (Scoreboard)", "Copper:");
        SCOREBOARD.put("Motes (Scoreboard)", "Motes:");
    }

    private static void w(String name, String header) {
        WIDGETS.put(name, Pattern.compile(header));
    }

    private static final List<String> OPTIONS = new ArrayList<>();
    static {
        OPTIONS.addAll(WIDGETS.keySet());
        OPTIONS.addAll(SCOREBOARD.keySet());
    }

    private static final List<String> SAMPLE = List.of("§e§lCommissions:", " §fMithril Miner: §a52%",
            " §fGoblin Slayer: §c0%", "§3Soulflow: §31,234⸎", "§bBits: §b12,500");

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;

    private TabWidgetHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("tabWidgetHud", "Tab Widgets").section("Interface")
                .enabled(() -> cfg().tabWidgetHud)
                .size(() -> Math.max(80, lines.isEmpty() ? width(SAMPLE) : width),
                        () -> Math.max(3, lines.isEmpty() ? SAMPLE.size() : lines.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? width(SAMPLE) : width));
        Feature.of("TabWidgetHud").when(() -> cfg().tabWidgetHud)
                .tick(TabWidgetHud::tick)
                .hudHiddenInInventory("tabwidgethud", TabWidgetHud::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Tab Widgets HUD");
                    b.toggle("Tab Widgets HUD", () -> c.tabWidgetHud, v -> c.tabWidgetHud = v)
                            .desc("Shows the picked tab list widgets (Commissions, Pest Traps, Soulflow, Bank, ...) and scoreboard values (Bits, Heat, Cold) as one block (/tmap gui)")
                            .tags("tab", "widget", "commissions", "soulflow", "bits", "heat", "bank");
                    b.searchList("  Widgets", () -> OPTIONS, c.tabWidgetHudWidgets::contains, t -> {
                        if (!c.tabWidgetHudWidgets.remove(t)) c.tabWidgetHudWidgets.add(t);
                    });
                    b.info("A widget only shows where Hypixel puts it in the tab list (/widget to enable it)");
                });
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        List<String> picked = cfg().tabWidgetHudWidgets;
        if (picked.isEmpty() || !SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        List<String> tab = TabListUtils.getOrderedLines();
        List<String> out = new ArrayList<>();
        for (String name : picked) {
            Pattern header = WIDGETS.get(name);
            if (header != null) collectWidget(tab, header, out);
            String prefix = SCOREBOARD.get(name);
            if (prefix != null) {
                for (String l : ScoreboardUtils.getScoreboardLines()) {
                    if (ScoreboardUtils.cleanLine(l).startsWith(prefix)) { out.add(l.trim()); break; }
                }
            }
        }
        lines = out;
        width = width(out);
    }

    private static void collectWidget(List<String> tab, Pattern header, List<String> out) {
        for (int i = 0; i < tab.size(); i++) {
            String t = StringUtil.strip(tab.get(i)).trim();
            if (!header.matcher(t).matches()) continue;
            out.add(tab.get(i).trim());
            int n = 1;
            for (int j = i + 1; j < tab.size() && n < MAX_LINES; j++) {
                String raw = tab.get(j);
                String s = StringUtil.strip(raw).trim();
                if (s.isEmpty() || isHeader(s)) break;
                out.add(" " + raw.trim());
                n++;
            }
            return;
        }
    }

    private static boolean isHeader(String s) {
        for (Pattern p : WIDGETS.values()) if (p.matcher(s).matches()) return true;
        for (Pattern p : BOUNDARIES) if (p.matcher(s).matches()) return true;
        return false;
    }

    private static int width(List<String> l) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String s : l) w = Math.max(w, mc.font.width(s));
        return w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        int pad = HudStyle.padding();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -pad, -pad, w + pad * 2, l.size() * 10 - 1 + pad * 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
