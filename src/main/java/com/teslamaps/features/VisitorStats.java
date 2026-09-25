package com.teslamaps.features;

// Ported from SkyHanni's GardenVisitorDropStatistics.kt (+ VisitorReward.kt)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lifetime visitor numbers: accepted/denied, copper, xp, coins spent, bits, powder, rarities, rare rewards. */
public final class VisitorStats {

    private static final Pattern ACCEPTED = Pattern.compile("^OFFER ACCEPTED with (.+) \\((.+)\\)$");
    private static final Pattern GAIN = Pattern.compile("^\\+([\\d,.]+k?) (Copper|Garden Experience|Farming XP|Bits|Mithril Powder|Gemstone Powder)$");
    private static final Pattern REQUIRED = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");
    private static final String[] RARITIES = {"UNCOMMON", "RARE", "LEGENDARY", "MYTHIC", "SPECIAL"};
    private static final String[] RARITY_COLORS = {"§a", "§9", "§6", "§d", "§c"};
    private static final String[][] REWARDS = {
            {"Flowering Bouquet", "§9"}, {"Overgrown Grass", "§9"}, {"Green Bandana", "§9"}, {"Dedication IV", "§9"},
            {"Music Rune", "§9"}, {"Space Helmet", "§c"}, {"Cultivating I", "§9"}, {"Replenish I", "§9"},
            {"Delicate V", "§9"}, {"Copper Dye", "§8"}, {"Jungle Key", "§5"}, {"Fruit Bowl", "§9"},
            {"Harvest Harbinger V", "§9"}, {"Hypercharge Chip", "§9"}, {"Quickdraw Chip", "§9"},
            {"Farming Exp Boost", "§5"}, {"Unfulfilled Jerryseed", "§a"}, {"Voter's Badge", "§f"},
            {"VIP Voter's Badge", "§a"}, {"Elite Voter's Badge", "§9"}, {"Supreme Voter's Badge", "§5"},
            {"Wild Strawberry Dye", "§d"}, {"Velvet Top Hat", "§9"}, {"Cashmere Jacket", "§9"},
            {"Satin Trousers", "§9"}, {"Oxford Shoes", "§9"}, {"Carnival Ticket", "§a"},
            {"Visitors' Gratitude", "§f"}, {"Farming Contest Display", "§a"}, {"Astronaut Minion Skin", "§f"},
            {"Fast Food Barn Skin", "§6"}, {"Jelly Garden Greenhouse Skin", "§6"},
    };
    private static final int LINE = 10;

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static boolean dirty = true, show;
    private static long lastAccept, pendingAt, pendingCoins;
    private static final List<String> pendingRewards = new ArrayList<>();
    private static int ticks;

    private VisitorStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("visitorStats", "Visitor Statistics").section("Garden").ctx(HudEditElement.HudCtx.GARDEN)
                .enabled(() -> cfg().visitorStats)
                .size(() -> Math.max(100, width), () -> Math.max(LINE, lines.size() * LINE))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? List.of("§e§lVisitor Statistics") : lines, Math.max(100, width)));
        Feature.of("VisitorStats").section("Garden").when(() -> cfg().visitorStats)
                .tick(VisitorStats::tick)
                .serverChat(VisitorStats::onChat)
                .hudHiddenInInventory("visitorstats", VisitorStats::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Visitor Statistics");
                    b.toggle("Visitor Statistics", () -> c.visitorStats, v -> c.visitorStats = v)
                            .desc("Lifetime visitor numbers: accepted/denied, copper, farming + garden xp, coins spent, bits, powder, rarities and rare rewards (/tmap gui)")
                            .tags("visitor", "copper", "drops", "statistics", "bouquet", "space helmet");
                    b.toggle("  Only On The Barn Plot", () -> c.visitorStatsOnlyBarn, v -> c.visitorStatsOnlyBarn = v);
                    b.button("Reset Visitor Statistics", VisitorStats::reset);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("visitorstats")
                        .then(ClientCommands.literal("reset").executes(c -> { reset(); return 1; })))));
    }

    public static void reset() {
        TeslaMapsConfig c = cfg();
        c.visitorStatAccepted = c.visitorStatDenied = c.visitorStatCopper = c.visitorStatFarmingXp = 0;
        c.visitorStatGardenXp = c.visitorStatCoinsSpent = c.visitorStatBits = c.visitorStatMithril = c.visitorStatGemstone = 0;
        c.visitorStatRarities.clear();
        c.visitorStatRewards.clear();
        TeslaMapsConfig.save();
        dirty = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Visitor statistics reset"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (ticks++ % 10 == 0) {
            show = SkyblockUtils.isInGarden() && (!cfg().visitorStatsOnlyBarn || onBarn(mc));
        }
        if (pendingAt != 0 && System.currentTimeMillis() - pendingAt > 5000) {
            pendingAt = 0;
            pendingRewards.clear();
        }
        if (dirty) build(mc);
    }

    private static boolean onBarn(Minecraft mc) {
        return Math.abs(mc.player.getX()) < 48 && Math.abs(mc.player.getZ()) < 48;
    }

    /** From HandledScreenClickMixin. */
    public static void onSlotClick(AbstractContainerScreen<?> cs, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.visitorStats || !c.section("Garden") || slot == null || !slot.hasItem() || slot.container instanceof Inventory) return;
        if (!SkyblockUtils.isInGarden()) return;
        String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
        if (name.equals("Refuse Offer") && slot.index == 33) {
            c.visitorStatDenied++;
            TeslaMapsConfig.save();
            dirty = true;
            return;
        }
        if (!name.equals("Accept Offer") || slot.index != 29) return;
        pendingRewards.clear();
        pendingCoins = 0;
        String section = "";
        for (String raw : ItemUtil.loreLines(slot.getItem())) {
            String l = raw.trim();
            if (l.endsWith(":")) { section = l; continue; }
            if (l.isEmpty()) { section = ""; continue; }
            if (section.startsWith("Items Required")) {
                Matcher m = REQUIRED.matcher(l);
                if (!m.matches()) continue;
                String id = PriceManager.idForName(m.group(1).trim());
                Double p = id == null ? null : PriceManager.bazaarBuyOrNull(id);
                long amount = m.group(2) == null ? 1 : parse(m.group(2));
                if (p != null) pendingCoins += (long) (p * amount);
            } else if (section.startsWith("Rewards")) {
                for (String[] r : REWARDS) {
                    if (l.equals(r[0]) || l.startsWith(r[0] + " ")) { pendingRewards.add(r[0]); break; }
                }
            }
        }
        pendingAt = System.currentTimeMillis();
    }

    private static void onChat(String text) {
        if (!SkyblockUtils.isInGarden()) return;
        String t = StringUtil.stripTrim(text);
        TeslaMapsConfig c = cfg();
        Matcher m = ACCEPTED.matcher(t);
        if (m.matches()) {
            lastAccept = System.currentTimeMillis();
            c.visitorStatAccepted++;
            String rarity = m.group(2).trim().toUpperCase(Locale.ROOT);
            c.visitorStatRarities.merge(rarity, 1L, Long::sum);
            if (pendingAt != 0) {
                c.visitorStatCoinsSpent += pendingCoins;
                for (String r : pendingRewards) c.visitorStatRewards.merge(r, 1L, Long::sum);
                pendingAt = 0;
                pendingRewards.clear();
            }
            TeslaMapsConfig.save();
            dirty = true;
            return;
        }
        if (System.currentTimeMillis() - lastAccept > 1500) return;
        m = GAIN.matcher(t);
        if (!m.matches()) return;
        long v = parse(m.group(1));
        switch (m.group(2)) {
            case "Copper" -> c.visitorStatCopper += v;
            case "Garden Experience" -> c.visitorStatGardenXp += v;
            case "Farming XP" -> c.visitorStatFarmingXp += v;
            case "Bits" -> c.visitorStatBits += v;
            case "Mithril Powder" -> c.visitorStatMithril += v;
            default -> c.visitorStatGemstone += v;
        }
        TeslaMapsConfig.save();
        dirty = true;
    }

    private static long parse(String s) {
        String n = s.replace(",", "").toLowerCase(Locale.ROOT);
        double mult = 1;
        if (n.endsWith("k")) { mult = 1000; n = n.substring(0, n.length() - 1); }
        try {
            return Math.round(Double.parseDouble(n) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void build(Minecraft mc) {
        dirty = false;
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>();
        out.add("§e§lVisitor Statistics");
        out.add("§eTotal: §f" + num(c.visitorStatAccepted + c.visitorStatDenied));
        out.add("§2Accepted: §f" + num(c.visitorStatAccepted));
        out.add("§cDenied: §f" + num(c.visitorStatDenied));
        StringBuilder rar = new StringBuilder();
        for (int i = 0; i < RARITIES.length; i++) {
            if (i > 0) rar.append("§f-");
            rar.append(RARITY_COLORS[i]).append(num(c.visitorStatRarities.getOrDefault(RARITIES[i], 0L)));
        }
        out.add(rar.toString());
        out.add("");
        out.add("§cCopper: §f" + num(c.visitorStatCopper));
        out.add("§3Farming EXP: §7" + EstimatedValue.fmt(c.visitorStatFarmingXp));
        out.add("§2Garden EXP: §7" + EstimatedValue.fmt(c.visitorStatGardenXp));
        out.add("§6Coins Spent: §f" + EstimatedValue.fmt(c.visitorStatCoinsSpent));
        out.add("§bBits: §b" + num(c.visitorStatBits));
        if (c.visitorStatMithril > 0) out.add("§2Mithril Powder: §2" + num(c.visitorStatMithril));
        if (c.visitorStatGemstone > 0) out.add("§dGemstone Powder: §d" + num(c.visitorStatGemstone));
        boolean first = true;
        for (String[] r : REWARDS) {
            long n = c.visitorStatRewards.getOrDefault(r[0], 0L);
            if (n <= 0) continue;
            if (first) { out.add(""); first = false; }
            out.add(r[1] + r[0] + ": §b" + num(n));
        }
        for (Map.Entry<String, Long> e : c.visitorStatRewards.entrySet()) {
            boolean known = false;
            for (String[] r : REWARDS) if (r[0].equals(e.getKey())) { known = true; break; }
            if (!known) out.add("§f" + e.getKey() + ": §b" + num(e.getValue()));
        }
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        lines = out;
    }

    private static String num(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || lines.isEmpty() || hud == null) return;
        draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * LINE + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * LINE, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
