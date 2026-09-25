package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/GemstoneMoneyPerHour.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.HudVisibility;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coins per hour from Pristine procs and rough gemstones going into the sacks, valued at one gemstone tier. */
public final class GemstoneMoneyPerHour {

    public static final String[] TIERS = {"Rough", "Flawed", "Fine", "Flawless"};
    private static final Pattern PRISTINE = Pattern.compile("^PRISTINE! You found . Flawed (\\w+) Gemstone x(\\d+)!$");
    private static final Pattern ROUGH = Pattern.compile("\\+([\\d,]+) (?:\\S )?Rough (\\w+) Gemstone");
    private static final List<String> SAMPLE = List.of("§d§lGemstone Money per Hour", "§aSelling Fine Jade §afor §63.2k §aeach",
            "§aCoins/hr: §64.1M", "§aCoins made: §6820k", "§aUptime: §b12m 0s");

    private static HudEditElement hud;
    private static long start, lastMined, uptimeSec;
    private static double coins;
    private static String lastGem = "";
    private static boolean useNextSack, paused;
    private static int ticks;
    private static List<String> lines = List.of();
    private static int width;

    private GemstoneMoneyPerHour() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("gemstoneMoney", "Gemstone Money per Hour").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().gemstoneMoney)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines),
                        () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("GemstoneMoneyPerHour").section("Mining").when(() -> cfg().gemstoneMoney)
                .tick(GemstoneMoneyPerHour::tick)
                .serverChat(GemstoneMoneyPerHour::onChat)
                .hud("gemstonemoney", GemstoneMoneyPerHour::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Gemstone Money per Hour");
                    b.toggle("Gemstone Money per Hour", () -> c.gemstoneMoney, v -> c.gemstoneMoney = v)
                            .desc("Coins/h while gemstone mining: Pristine procs plus rough gems into the sacks, converted to the tier you sell. /tmap gemstonemoney reset. Position in /tmap gui.")
                            .tags("gemstone", "gem", "money", "coins per hour", "pristine", "profit");
                    b.dropdown("  Sell As", TIERS, () -> c.gemstoneMoneyTier, v -> { c.gemstoneMoneyTier = v; rebuild(); });
                    b.toggle("  Use NPC Price", () -> c.gemstoneMoneyNpc, v -> c.gemstoneMoneyNpc = v)
                            .desc("Off = the higher of bazaar and NPC price");
                    b.toggle("  Pause When Not Mining", () -> c.gemstoneMoneyPause, v -> c.gemstoneMoneyPause = v)
                            .desc("Off = reset instead after the timeout");
                    b.slider("  Timeout (s)", 5, 300, () -> (float) c.gemstoneMoneyTimeout,
                            v -> c.gemstoneMoneyTimeout = Math.round(v), 0);
                    b.button("  Reset Gemstone Money", GemstoneMoneyPerHour::reset);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("gemstonemoney").then(ClientCommands.literal("reset").executes(c -> {
                    reset();
                    c.getSource().sendFeedback(Component.literal("§6[TeslaMaps] §aReset gemstone money per hour."));
                    return 1;
                })))));
    }

    private static int tierNum() {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(cfg().gemstoneMoneyTier)) return i + 1;
        return 3;
    }

    private static String id(String gem) {
        return cfg().gemstoneMoneyTier.toUpperCase(Locale.ROOT) + "_" + gem.toUpperCase(Locale.ROOT) + "_GEM";
    }

    private static double price(String id) {
        double npc = SkyblockItems.hasNpcPrice(id) ? SkyblockItems.npcPrice(id) : 0;
        if (cfg().gemstoneMoneyNpc) return npc;
        Double bz = PriceManager.getPriceOrNull(id, PriceManager.SOURCE_BAZAAR_SELL);
        return Math.max(npc, bz == null ? 0 : bz);
    }

    private static double value(String gem, long amount, int type) {
        return amount * Math.pow(80, type - tierNum()) * price(id(gem));
    }

    private static void onChat(String text) {
        Matcher m = PRISTINE.matcher(StringUtil.stripTrim(text));
        if (!m.matches()) return;
        long now = System.currentTimeMillis();
        if (start == 0) start = now;
        paused = false;
        useNextSack = true;
        lastMined = now;
        lastGem = m.group(1);
        coins += value(lastGem, Long.parseLong(m.group(2)), 2);
    }

    /** ChatMixin, server lines only: rough gems from the "[Sacks]" hover after a Pristine proc. */
    public static void onChatComponent(Component message) {
        if (!useNextSack || !message.getString().contains("[Sacks]")) return;
        StringBuilder added = new StringBuilder();
        collect(message, added);
        if (added.isEmpty()) return;
        useNextSack = false;
        Matcher m = ROUGH.matcher(added);
        while (m.find()) {
            try {
                coins += value(m.group(2), Long.parseLong(m.group(1).replace(",", "")), 1);
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void collect(Component c, StringBuilder added) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            if (h.trim().startsWith("Added")) added.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, added);
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        if (lastMined == 0) {
            lines = List.of();
            return;
        }
        if (System.currentTimeMillis() - lastMined > cfg().gemstoneMoneyTimeout * 1000L) {
            if (cfg().gemstoneMoneyPause) paused = true;
            else { reset(); return; }
        } else {
            uptimeSec++;
        }
        rebuild();
    }

    private static void rebuild() {
        if (start == 0 || lastGem.isEmpty()) {
            lines = List.of();
            return;
        }
        double perHour = coins / Math.max(uptimeSec, 1) * 3600;
        String id = id(lastGem);
        List<String> out = new ArrayList<>(5);
        out.add("§d§lGemstone Money per Hour");
        out.add("§aSelling " + cfg().gemstoneMoneyTier + " " + lastGem + " §afor §6" + ItemPrice.format(price(id)) + " §aeach");
        out.add("§aCoins/hr: §6" + ItemPrice.format(perHour));
        out.add("§aCoins made: §6" + ItemPrice.format(coins));
        out.add("§aUptime: §b" + (uptimeSec / 60) + "m " + (uptimeSec % 60) + "s" + (paused ? " §c(PAUSED)" : ""));
        lines = out;
        width = TextHud.width(out);
    }

    public static void reset() {
        start = 0;
        lastMined = 0;
        uptimeSec = 0;
        coins = 0;
        paused = false;
        useNextSack = false;
        lastGem = "";
        lines = List.of();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || hud == null || HudVisibility.suppressed() || !Mining.onMiningIsland()) return;
        TextHud.draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
