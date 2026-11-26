package com.teslamaps.features.events;

// Ported from SkyHanni's GiftProfitTracker.kt, GiftApi.kt and IsGiftingDetection.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GiftProfitTracker {

    private static final Pattern REWARD = Pattern.compile("^(COMMON|RARE|SWEET|SANTA TIER|PARTY TIER)! (.+) gift with .+!$");
    private static final Pattern NORTH_STARS = Pattern.compile("^EXTRA! \\+([\\d,]+) North Stars?$");
    private static final Pattern XP = Pattern.compile("^\\+([\\d,]+) ([\\w ]+) XP$");
    private static final Pattern COINS = Pattern.compile("^\\+([\\d,]+) Coins$");
    private static final Pattern BOOST = Pattern.compile("^([\\w ]+) XP Boost ([IVX]+) Potion$");
    private static final Pattern BOOK = Pattern.compile("^([A-Za-z' ]+) ([IVX]+)$");
    private static final Pattern AMOUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final String[] RARITIES = {"COMMON", "RARE", "SWEET", "SANTA TIER", "PARTY TIER"};
    private static final String[] RARITY_NAMES = {"§f§lCOMMON", "§9§lRARE", "§e§lSWEET", "§c§lSANTA TIER", "§c§lPARTY TIER"};
    private static final String[] GIFTS = {"white", "green", "red", "party"};
    private static final String[] SPAM = {
            "This player is playing a profile mode which doesn't allow gifting!",
            "An error occurred!",
            "Can't place gifts this close to spawn!",
            "You cannot place a gift so close to an NPC!",
            "Click a player to gift them! This isn't a player!",
    };

    private static Tracker tracker, stats;
    private static HudEditElement hud;
    private static Feature feature;
    private static boolean holding, show;
    private static long lastGiftAt;
    private static Vec3 lastGiftPos;
    private static Object lastLevel;
    private static int ticks;

    private GiftProfitTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        stats = Tracker.create("gift_profit_stats", "Gift Stats").fixedValue("north_stars", 0);
        for (String r : RARITIES) stats.fixedValue("rarity_" + r, 0);
        tracker = Tracker.create("gift_profit", "§e§lGift Profit Tracker")
                .priceSource(() -> cfg().giftProfitPriceSource)
                .name(Tracker.COINS, "§6Gift Coins")
                .extra(GiftProfitTracker::extraLines);
        hud = HudEditElement.of("giftProfitTracker", "Gift Profit Tracker").section("Events")
                .enabled(() -> cfg().giftProfitTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        feature = Feature.of("GiftProfitTracker").section("Events").when(() -> cfg().giftProfitTracker || cfg().giftSpamFilter)
                .tick(GiftProfitTracker::tick)
                .serverChat(GiftProfitTracker::onChat)
                .hudHiddenInInventory("giftprofittracker", GiftProfitTracker::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Gift Profit Tracker", () -> c.giftProfitTracker, v -> c.giftProfitTracker = v)
                            .desc("Tracks what opened gifts give you: items, coins, North Stars, skill XP and reward rarities. "
                                    + "Add used gifts with /tmap gift add <white|green|red|party> [amount], their cost is "
                                    + "taken off the profit. Shows while holding a gift or right after gifting. Move it in /tmap gui.")
                            .tags("gift", "winter", "jerry", "north star", "profit");
                    b.toggle("  Only While Holding A Gift", () -> c.giftProfitOnlyHolding, v -> c.giftProfitOnlyHolding = v);
                    b.priceSource("  Price Source", () -> c.giftProfitPriceSource, v -> c.giftProfitPriceSource = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{
                                    () -> { tracker.resetSession(); stats.resetSession(); },
                                    () -> { tracker.resetTotal(); stats.resetTotal(); },
                                    tracker::toggleView});
                    b.toggle("Hide Gift Error Messages", () -> c.giftSpamFilter, v -> c.giftSpamFilter = v)
                            .desc("Hides gift error lines (too close to spawn/NPC, not a player, profile mode, refunded ...).")
                            .tags("gift", "spam", "filter");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("gift").then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("type", StringArgumentType.word())
                                .suggests((c, sb) -> { for (String g : GIFTS) sb.suggest(g); return sb.buildFuture(); })
                                .executes(c -> addUsed(StringArgumentType.getString(c, "type"), 1))
                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(c -> addUsed(StringArgumentType.getString(c, "type"),
                                                IntegerArgumentType.getInteger(c, "amount")))))))));
    }

    private static int addUsed(String type, int amount) {
        Minecraft mc = Minecraft.getInstance();
        String t = type.toLowerCase(Locale.ROOT);
        boolean ok = false;
        for (String g : GIFTS) ok |= g.equals(t);
        if (!ok) {
            mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§c[TeslaMaps] Usage: /tmap gift add <white|green|red|party> [amount]")); });
            return 0;
        }
        String id = t.toUpperCase(Locale.ROOT) + "_GIFT";
        mc.execute(() -> {
            tracker.add(id, -amount);
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§a[TeslaMaps] Added §2" + amount + "x §7" + id + " §ato used gifts."));
        });
        return 1;
    }

    private static List<String> extraLines(Tracker.View view) {
        List<String> out = new ArrayList<>();
        long stars = stats.count("north_stars", view);
        if (stars > 0) out.add("§d" + String.format(Locale.ROOT, "%,d", stars) + " §5North Stars");
        long total = 0;
        for (String r : RARITIES) total += stats.count("rarity_" + r, view);
        for (String k : stats.keys()) {
            if (!k.startsWith("xp_")) continue;
            long n = stats.count(k, view);
            if (n > 0) out.add("§3+" + ItemPrice.format(n) + " " + k.substring(3) + " XP");
        }
        if (total > 0) {
            out.add("§eTotal Rewards§7: " + String.format(Locale.ROOT, "%,d", total));
            for (int i = 0; i < RARITIES.length; i++) {
                long n = stats.count("rarity_" + RARITIES[i], view);
                if (n > 0) out.add("§7- " + n + "x " + RARITY_NAMES[i] + " §7(" + String.format(Locale.ROOT, "%.1f%%", n * 100.0 / total) + ")");
            }
        }
        return out;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!cfg().giftProfitTracker) return;
        Matcher m = NORTH_STARS.matcher(t);
        if (m.matches()) {
            stats.add("north_stars", Long.parseLong(m.group(1).replace(",", "")));
            tracker.markActivity();
            mark();
            return;
        }
        if (!t.contains(" gift with ")) return;
        m = REWARD.matcher(t);
        if (!m.matches()) return;
        stats.add("rarity_" + m.group(1), 1);
        mark();
        String reward = m.group(2).trim();
        if ((m = XP.matcher(reward)).matches()) {
            stats.fixedValue("xp_" + m.group(2), 0);
            stats.add("xp_" + m.group(2), Long.parseLong(m.group(1).replace(",", "")));
            tracker.markActivity();
            return;
        }
        if ((m = COINS.matcher(reward)).matches()) {
            tracker.addCoins(Long.parseLong(m.group(1).replace(",", "")));
            return;
        }
        if ((m = BOOST.matcher(reward)).matches()) {
            String key = "POTION_" + m.group(1).toUpperCase(Locale.ROOT).replace(' ', '_') + "_XP_BOOST;" + roman(m.group(2));
            tracker.name(key, "§a" + reward);
            tracker.add(key, 1);
            return;
        }
        if (reward.equals("◆ Ice Rune")) {
            tracker.name("ICE_RUNE;1", "§f◆ Ice Rune");
            tracker.add("ICE_RUNE;1", 1);
            return;
        }
        long amount = 1;
        String name = reward;
        if ((m = AMOUNT.matcher(reward)).matches()) {
            name = m.group(1);
            amount = Long.parseLong(m.group(2).replace(",", ""));
        }
        String id = PriceManager.idForName(name);
        if (id == null) id = SkyblockItems.idForName(name);
        if (id == null && (m = BOOK.matcher(name)).matches()) {
            id = "ENCHANTMENT_" + m.group(1).toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "") + "_" + roman(m.group(2));
            tracker.name(id, "§9" + name);
        }
        tracker.add(id != null ? id : name, amount);
    }

    private static int roman(String r) {
        int n = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            n += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return n;
    }

    private static void mark() {
        Minecraft mc = Minecraft.getInstance();
        lastGiftAt = System.currentTimeMillis();
        lastGiftPos = mc.player != null ? mc.player.position() : null;
    }

    /** From ChatMixin: true hides the line. */
    public static boolean hide(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.giftSpamFilter || feature == null || !feature.active()) return false;
        String t = StringUtil.stripTrim(raw);
        for (String s : SPAM) if (t.equals(s)) return true;
        return t.endsWith("disconnected, gift refunded!") || (t.startsWith("This gift is for ") && t.endsWith(", sorry!"));
    }

    private static void tick() {
        if (!cfg().giftProfitTracker) {
            show = false;
            return;
        }
        tracker.tick();
        stats.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            lastGiftAt = 0;
            lastGiftPos = null;
        }
        if (++ticks % 5 != 0) return;
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        holding = id.equals("WHITE_GIFT") || id.equals("GREEN_GIFT") || id.equals("RED_GIFT");
        boolean recent = !cfg().giftProfitOnlyHolding && lastGiftPos != null
                && System.currentTimeMillis() - lastGiftAt < 180_000 && mc.player.position().distanceTo(lastGiftPos) <= 10;
        show = SkyblockUtils.isInSkyblock() && (holding || recent);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
