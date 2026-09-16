package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coins earned in the garden, from pests and from farming.
 * <p>
 * Four signals feed this: the pest kill message, rare drop announcements, sack
 * change notifications and the inventory itself. They overlap heavily — a pest
 * drop is announced, appears in the inventory, then moves into a sack, and may
 * be compacted twice on the way — so the same item passes the tracker several
 * times over. Everything except the first sighting is held against a credit, and
 * only what cannot be explained by something already counted becomes income.
 */
public class FarmProfit {

    /** "+64 Wheat (1.2M)" — sign, amount, item, sack total. */
    private static final Pattern SACK_CHANGE = Pattern.compile("([+-][\\d,]+) (.+) \\((.+)\\)");

    /**
     * "RARE DROP! Wriggling Larva (+153)" — pest rarities that land in the
     * inventory instead of a sack, so the sack lines never mention them.
     * RARE CROP! is deliberately not matched: those do go into sacks.
     */
    private static final Pattern RARE_DROP =
            Pattern.compile("RARE DROP! (.+?)(?: \\(\\+[\\d,]+.*\\))?$");

    /** "You received 101x Enchanted Wild Rose for killing a Praying Mantis!" */
    private static final Pattern PEST_DROP =
            Pattern.compile("You received ([\\d,]+)x (.+) for killing an? .+!");

    /** "Moved 141 Compacted Moonflower from your Sacks to your inventory." */
    private static final Pattern SACK_WITHDRAW =
            Pattern.compile("Moved ([\\d,]+) (.+) from your Sacks to your inventory\\.");

    public enum Window {
        SESSION("Session", 0),
        MIN_30("30 min", 30 * 60_000L),
        HOUR_1("1 hour", 60 * 60_000L),
        HOUR_3("3 hours", 3 * 60 * 60_000L),
        ALL_TIME("All time", -1);

        public final String label;
        public final long millis;

        Window(String label, long millis) {
            this.label = label;
            this.millis = millis;
        }

        public Window next() {
            Window[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public Window previous() {
            Window[] all = values();
            return all[(ordinal() - 1 + all.length) % all.length];
        }
    }

    /** {@code pest} separates kill and rare drops from farmed crops. */
    private record Gain(long time, String item, long amount, double coins, boolean pest) {}

    private static final Deque<Gain> gains = new ArrayDeque<>();
    private static Window window = Window.SESSION;

    /**
     * The session starts with the first drop, not with the game. Counting from
     * launch would spread the earnings over the walk to the farm and drag the
     * hourly rate down for no reason.
     * <p>
     * Real breaks are left out of the rate the same way. What counts as activity
     * is deliberately wide: every broken block and every drop, so hunting pests
     * between two sack messages does not read as standing around.
     */
    private static long idleAfter() {
        return Math.max(30, com.teslamaps.config.TeslaMapsConfig.get().farmIdleSeconds) * 1000L;
    }

    private static long sessionStart = 0L;
    private static long lastGain = 0L;
    private static long activeMillis = 0L;

    public static boolean started() { return sessionStart > 0; }

    public static boolean idle() {
        return sessionStart > 0 && System.currentTimeMillis() - lastGain > idleAfter();
    }

    /** Time actually spent farming, breaks excluded. */
    public static long activeMillis() {
        if (sessionStart == 0) return 0;
        long now = System.currentTimeMillis();
        long open = now - lastGain;
        return activeMillis + (open <= idleAfter() ? open : 0);
    }

    /**
     * Anything that shows the player is working counts, not just income.
     * Breaking blocks that happen to drop nothing kept the clock running into a
     * false break otherwise.
     */
    public static void markActivity() {
        long now = System.currentTimeMillis();
        if (sessionStart == 0) {
            sessionStart = now;
        } else {
            long gap = now - lastGain;
            if (gap <= idleAfter()) activeMillis += gap;
        }
        lastGain = now;
    }

    // All-time totals survive restarts; the rolling windows do not.
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, long[]>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("farm_profit.json");
    /** item -> [amount, coins] */
    private static Map<String, long[]> allTime = new HashMap<>();
    private static boolean loaded = false;

    public static Window window() { return window; }

    public static void cycleWindow() { window = window.next(); }

    /**
     * Sack notifications collapse to "[Sacks] +557 items."; the per-item list
     * only exists in the hover tooltip, so that is what gets parsed.
     */
    private static String lastHover = null;
    private static long lastHoverTime = 0L;

    public static void onChatComponent(net.minecraft.network.chat.Component message) {
        if (!SkyblockUtils.isInGarden()) return;

        String plain = message.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (plain.startsWith("RARE DROP!")) {
            Matcher matcher = RARE_DROP.matcher(plain);
            if (matcher.matches()) {
                record(matcher.group(1).trim(), 1, true);
                credit(matcher.group(1).trim(), 1);
            }
            return;
        }

        Matcher withdraw = SACK_WITHDRAW.matcher(plain);
        if (withdraw.matches()) {
            // Coming back out of a sack is not income; hold it against the
            // inventory gain that follows.
            credit(withdraw.group(2).trim(),
                    Long.parseLong(withdraw.group(1).replace(",", "")));
            return;
        }

        Matcher kill = PEST_DROP.matcher(plain);
        if (kill.matches()) {
            lastPestKill = System.currentTimeMillis();
            long amount = Long.parseLong(kill.group(1).replace(",", ""));
            String item = kill.group(2).trim();
            record(item, amount, true);
            credit(item, amount);
            return;
        }

        if (!plain.contains("[Sacks]")) return;

        String hover = hoverText(message);
        if (hover.isEmpty()) return;

        // The same tooltip arriving twice in a moment means the message reached
        // us twice, not that the drops happened twice.
        long now = System.currentTimeMillis();
        if (hover.equals(lastHover) && now - lastHoverTime < 500) return;
        lastHover = hover;
        lastHoverTime = now;

        applySackMessage(hover);
    }

    /**
     * Applies one sack notification as a whole.
     * <p>
     * Sacks compact their contents on their own: 160 Enchanted Sunflower become
     * one Compacted Sunflower, reported as a loss of the lower tier and a gain of
     * the higher one in the same message. Counting only the gains therefore paid
     * for the same items twice, once per tier. Applying both sides lets a
     * compaction cancel itself out.
     * <p>
     * A message with no gain at all is not farming: that is the player taking
     * items out to sell them, and it must not read as a loss.
     */
    private static void applySackMessage(String hover) {
        List<String[]> changes = new ArrayList<>();

        for (String line : hover.split("\n")) {
            Matcher matcher = SACK_CHANGE.matcher(line.replaceAll("(?i)§[0-9A-FK-OR]", "").trim());
            if (!matcher.matches()) continue;

            long amount;
            try {
                amount = Long.parseLong(matcher.group(1).replace(",", "").replace("+", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (amount == 0) continue;
            changes.add(new String[]{matcher.group(2).trim(), String.valueOf(amount)});
        }

        if (com.teslamaps.config.TeslaMapsConfig.get().debugMode) {
            StringBuilder debug = new StringBuilder("§b[Sacks]§7");
            for (String[] change : changes) {
                debug.append(' ').append(change[1]).append(' ').append(change[0]).append(';');
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(debug.toString()));
            }
        }

        Map<String, Long> gained = new HashMap<>();
        for (String[] change : changes) {
            long amount = Long.parseLong(change[1]);
            if (amount > 0) gained.merge(normalize(change[0]), amount, Long::sum);
        }

        for (String[] change : changes) {
            String item = change[0];
            long amount = Long.parseLong(change[1]);

            if (amount > 0) {
                amount -= consumeForSack(item, amount);
                if (amount > 0) recordUpgradable(item, amount);
                continue;
            }

            String upper = upperTier(normalize(item));
            if (upper != null && gained.containsKey(upper)) continue;   // compactor

            // A message that also carries gains is a craft: the material turned
            // into the item above, and that item is booked as income. Crafting
            // Enchanted Cookies out of stored cocoa paid for the same beans
            // twice otherwise, once as cocoa and once as cookies.
            if (!gained.isEmpty()) {
                deduct(item, -amount);
                continue;
            }

            // Taken out on its own is not a loss — it was earned once and moving
            // it does not unearn it. It shows up in the inventory next, so hold
            // it against that and it is not counted a second time on arrival.
            credit(item, -amount);
        }
    }

    /**
     * Items already accounted for, waiting to be seen again somewhere else.
     * <p>
     * The two directions are kept apart on purpose. Inbound is what should turn up
     * in the inventory next: a pest drop announced on the kill, or stock taken out
     * of a sack. Outbound is what should turn up as a sack gain next, because it
     * just left the inventory. Sharing one pool let an outbound credit swallow a
     * genuine inventory drop, which is how the compactor eating 160 cocoa beans
     * silently cancelled the next 160 that fell.
     */
    private record Credit(long amount, long time) {}

    private static final Map<String, Credit> inbound = new HashMap<>();
    private static final Map<String, Credit> outbound = new HashMap<>();
    /** Long enough for the other side to catch up, short enough not to eat real gains. */
    private static final long CREDIT_TTL = 60_000L;

    private static void credit(String item, long amount) { add(inbound, item, amount); }

    private static void creditOutbound(String item, long amount) { add(outbound, item, amount); }

    private static void add(Map<String, Credit> pool, String item, long amount) {
        item = normalize(item);
        Credit existing = pool.get(item);
        long now = System.currentTimeMillis();
        long carried = existing != null && now - existing.time() < CREDIT_TTL ? existing.amount() : 0;
        pool.put(item, new Credit(carried + amount, now));
    }

    /** A sack gain may be stock just put away, or a drop already announced. */
    private static long consumeForSack(String item, long amount) {
        long used = take(outbound, item, amount);
        return used + take(inbound, item, amount - used);
    }

    /** An inventory gain can only settle something expected to arrive here. */
    private static long consumeCredit(String item, long amount) {
        return take(inbound, item, amount);
    }

    private static long take(Map<String, Credit> pool, String item, long amount) {
        if (amount <= 0) return 0;
        item = normalize(item);
        Credit credit = pool.get(item);
        if (credit == null) return 0;

        long now = System.currentTimeMillis();
        if (now - credit.time() >= CREDIT_TTL) {
            pool.remove(item);
            return 0;
        }

        long used = Math.min(amount, credit.amount());
        long left = credit.amount() - used;
        if (left <= 0) {
            pool.remove(item);
        } else {
            pool.put(item, new Credit(left, credit.time()));
        }
        return used;
    }

    /**
     * SHOW_TEXT hover contents of a component and its children, each distinct
     * tooltip counted once.
     * <p>
     * Siblings carry the same hover event as their parent, so collecting them all
     * blindly counted every item once per sibling and quietly multiplied the
     * whole tracker.
     */
    private static String hoverText(net.minecraft.network.chat.Component message) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        collectHover(message, seen);
        return String.join("\n", seen);
    }

    private static void collectHover(net.minecraft.network.chat.Component component,
                                      java.util.Set<String> seen) {
        var hover = component.getStyle().getHoverEvent();
        if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowText showText) {
            seen.add(showText.value().getString());
        }
        for (net.minecraft.network.chat.Component child : component.getSiblings()) {
            collectHover(child, seen);
        }
    }

    /**
     * Watches the inventory for items the sacks never saw.
     * <p>
     * A full sack stops accepting drops and they land in the inventory instead,
     * silently — no sack line is sent for them. Counting the difference between
     * two snapshots is the only way to notice.
     * <p>
     * Only counted while the player is actually farming, meaning a crop broke in
     * the last few seconds. Items appearing in the inventory at any other moment
     * are being moved rather than earned — taken out of a sack, bought, picked
     * back up, or handed over at an NPC — and each of those was being booked as
     * income a second time.
     */
    private static Map<String, Integer> snapshot = null;
    private static int tickCounter = 0;

    /** How long after a broken crop an inventory gain still counts as a drop. */
    private static final long FARMING_WINDOW = 5_000L;

    /**
     * Pest kills count as farming too, for a good deal longer.
     * <p>
     * Ultimate Sunset I and Pesterminator I are announced nowhere — no kill line,
     * no rare drop line — so the inventory is the only place they ever show up.
     * Shooting pests breaks no crop, so the crop window alone threw every one of
     * them away as "not farming".
     */
    private static final long PEST_WINDOW = 20_000L;
    private static long lastPestKill = 0L;

    /**
     * Quiet time after a screen closes before the inventory is trusted again.
     * <p>
     * Closing a container hands anything on the cursor back to the inventory a
     * moment later, and slots keep shuffling for a few ticks. Sampling into that
     * churn invented gains for items that had been sitting there all along.
     */
    private static final long SETTLE_AFTER_SCREEN = 1_500L;

    private static long screenClosedAt = 0L;

    public static void tick() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (mc.player == null || mc.screen != null || !SkyblockUtils.isInGarden()) {
            snapshot = null;
            screenClosedAt = now;
            return;
        }
        if (now - screenClosedAt < SETTLE_AFTER_SCREEN) {
            snapshot = null;
            return;
        }
        if (++tickCounter % 10 != 0) return;

        boolean farming = now - FarmSpeed.lastBreakMillis() < FARMING_WINDOW
                || now - lastPestKill < PEST_WINDOW;

        Map<String, Integer> current = new HashMap<>();
        for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().getNonEquipmentItems()) {
            countStack(current, stack);
        }
        // On the cursor counts as held too, otherwise picking a stack up reads as
        // losing it and putting it down reads as finding it.
        if (mc.player.containerMenu != null) {
            countStack(current, mc.player.containerMenu.getCarried());
        }

        if (snapshot != null) {
            // Anything leaving the inventory without a screen open is being put
            // away, and the sack will report it as a gain in a moment. Pest drops
            // arrive here rather than in the sack, so without this every one of
            // them was counted again on its way into storage.
            for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
                int gone = entry.getValue() - current.getOrDefault(entry.getKey(), 0);
                if (gone <= 0) continue;

                // Unless the compactor ate them: the tier above went up in the
                // same breath, and that upgrade is settled by the ledger instead.
                String upper = upperTier(entry.getKey());
                if (upper != null
                        && current.getOrDefault(upper, 0) > snapshot.getOrDefault(upper, 0)) {
                    continue;
                }
                creditOutbound(entry.getKey(), gone);
            }

            for (Map.Entry<String, Integer> entry : current.entrySet()) {
                int delta = entry.getValue() - snapshot.getOrDefault(entry.getKey(), 0);
                if (delta <= 0) continue;

                String item = entry.getKey();
                // Gear and tools carry no price and would only clutter the list.
                if (!BazaarPrices.known(item) || BazaarPrices.priceOf(item) <= 0) continue;

                pending.add(new Pending(item, delta, System.currentTimeMillis(), farming));
            }
        }
        snapshot = current;
        flushPending(mc);
    }

    private static void countStack(Map<String, Integer> into,
                                   net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String name = stack.getHoverName().getString()
                .replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
        if (name.isEmpty()) return;
        into.merge(name, stack.getCount(), Integer::sum);
    }

    /** An inventory gain that has not been judged yet. */
    private record Pending(String item, long amount, long time, boolean farming) {}

    private static final List<Pending> pending = new ArrayList<>();

    /**
     * How long to wait before booking an inventory gain.
     * <p>
     * The item often reaches the inventory before the message explaining it. Judging
     * the gain right away meant the kill message had not arrived yet, so a pest drop
     * looked like it came from nowhere: double counted before, dropped after.
     */
    private static final long INV_DELAY = 1_500L;

    private static void flushPending(net.minecraft.client.Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean debug = com.teslamaps.config.TeslaMapsConfig.get().debugMode && mc.player != null;

        for (int i = 0; i < pending.size(); ) {
            Pending p = pending.get(i);
            if (now - p.time() < INV_DELAY) { i++; continue; }
            pending.remove(i);

            long counted = p.amount() - consumeCredit(p.item(), p.amount());
            if (counted <= 0) continue;

            // Everything else has to happen while crops are breaking to count;
            // items moved around at an NPC or a container are not income.
            if (!p.farming()) {
                if (debug) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§8[Inv] ignored +" + counted + " " + p.item() + " (not farming)"));
                }
                continue;
            }
            if (debug) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§8[Inv] +" + counted + " " + p.item()));
            }
            recordUpgradable(p.item(), counted);
        }
    }

    /**
     * How much of every item has been counted so far, so a later upgrade of the
     * same material can take its value back off again.
     */
    private static final Map<String, Long> ledger = new HashMap<>();
    private static final int TIER_RATIO = 160;

    /** Sack lines shorten "Enchanted"; the kill messages spell it out. */
    private static String normalize(String item) {
        return item.startsWith("Ench ") ? "Enchanted " + item.substring(5) : item;
    }

    /** "Sunflower" -> "Enchanted Sunflower" -> "Compacted Sunflower". */
    private static String upperTier(String item) {
        if (item.startsWith("Compacted ")) return null;
        if (item.startsWith("Enchanted ")) return "Compacted " + item.substring(10);
        return "Enchanted " + item;
    }

    /** "Compacted Sunflower" -> "Enchanted Sunflower" -> "Sunflower". */
    private static String lowerTier(String item) {
        if (item.startsWith("Compacted ")) return "Enchanted " + item.substring(10);
        if (item.startsWith("Enchanted ")) return item.substring(10);
        return null;
    }

    /**
     * Books a gain that may not be new material at all.
     * <p>
     * A compactor turns 160 Enchanted Sunflower into one Compacted Sunflower and
     * reports only the gain, never the loss. Counting that as income pays for the
     * same flowers twice — once per tier they pass through. So an upgrade first
     * takes the lower tier's value back out, and only what cannot be explained by
     * already-counted material is treated as fresh income.
     */
    private static void recordUpgradable(String item, long amount) {
        item = normalize(item);
        if (amount <= 0) {
            record(item, amount, false);
            return;
        }

        String lower = lowerTier(item);
        if (lower != null) {
            long available = ledger.getOrDefault(lower, 0L);
            long converted = Math.min(amount, available / TIER_RATIO);
            if (converted > 0) record(lower, -converted * TIER_RATIO, false);
        }
        record(item, amount, false);
    }

    /**
     * Takes material that was consumed back off the tracker, never more than was
     * booked for it. Deducting blind would turn material that was bought rather
     * than farmed into a phantom loss.
     */
    private static void deduct(String item, long amount) {
        item = normalize(item);
        long off = Math.min(amount, ledger.getOrDefault(item, 0L));
        if (off > 0) record(item, -off, false);
    }

    private static void record(String item, long amount, boolean pest) {
        if (amount == 0) return;
        item = normalize(item);
        ledger.merge(item, amount, Long::sum);
        if (amount > 0) markActivity();
        double coins = BazaarPrices.priceOf(item) * amount;

        gains.addLast(new Gain(System.currentTimeMillis(), item, amount, coins, pest));
        trim();

        if (!loaded) load();
        long[] total = allTime.computeIfAbsent(item, k -> new long[2]);
        total[0] += amount;
        total[1] += (long) coins;
        saveThrottled();
    }

    /** Drops entries older than the longest rolling window we can show. */
    private static void trim() {
        long cutoff = System.currentTimeMillis() - Window.HOUR_3.millis;
        while (!gains.isEmpty() && gains.peekFirst().time() < cutoff && gains.size() > 4096) {
            gains.removeFirst();
        }
    }

    public record Entry(String item, long amount, double coins) {}

    /** Totals for the active window, biggest earner first. */
    public static List<Entry> entries() {
        Map<String, long[]> counts = new HashMap<>();
        Map<String, Double> coins = new HashMap<>();

        if (window == Window.ALL_TIME) {
            if (!loaded) load();
            List<Entry> out = new ArrayList<>();
            for (Map.Entry<String, long[]> e : allTime.entrySet()) {
                out.add(new Entry(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
            out.removeIf(entry -> entry.amount() <= 0);
            out.sort(Comparator.comparingDouble(Entry::coins).reversed());
            return out;
        }

        long cutoff = window == Window.SESSION
                ? sessionStart
                : System.currentTimeMillis() - window.millis;

        for (Gain gain : gains) {
            if (gain.time() < cutoff) continue;
            counts.computeIfAbsent(gain.item(), k -> new long[1])[0] += gain.amount();
            coins.merge(gain.item(), gain.coins(), Double::sum);
        }

        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : counts.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue()[0], coins.getOrDefault(e.getKey(), 0.0)));
        }
        out.removeIf(entry -> entry.amount() <= 0);
        out.sort(Comparator.comparingDouble(Entry::coins).reversed());
        return out;
    }

    public static double totalCoins() {
        double total = 0;
        for (Entry entry : entries()) total += entry.coins();
        return total;
    }

    /**
     * Coins from pest kills and their rare drops only, which is what a pest-only
     * tracker reports. The rest is farmed crops.
     */
    public static double pestCoins() {
        if (window == Window.ALL_TIME) return -1;   // source is not stored per item
        long cutoff = window == Window.SESSION
                ? sessionStart
                : System.currentTimeMillis() - window.millis;

        double total = 0;
        for (Gain gain : gains) {
            if (gain.time() >= cutoff && gain.pest()) total += gain.coins();
        }
        return total;
    }

    /** Coins per hour, based on how long the active window actually covers. */
    public static double coinsPerHour() {
        long span;
        if (window == Window.SESSION) {
            span = activeMillis();
        } else if (window == Window.ALL_TIME) {
            if (!loaded) load();
            return 0;   // no timespan is stored for all time
        } else {
            span = window.millis;
            long covered = activeMillis();
            if (covered < span) span = covered;
        }
        if (span < 1000) return 0;
        return totalCoins() / (span / 3_600_000.0);
    }

    /** Prints every tracked item with amount, unit price and total. */
    public static void printBreakdown() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        List<Entry> entries = entries();
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§b[TeslaMaps] §fProfit — §e" + window.label));

        if (entries.isEmpty()) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§7  nothing tracked yet"));
            return;
        }

        double total = 0;
        for (Entry entry : entries) {
            total += entry.coins();
            double each = entry.amount() > 0 ? entry.coins() / entry.amount() : 0;
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(String.format(
                    "§7  %s x%,d  §f%s  §8(%.1f each)",
                    entry.item(), entry.amount(), shortCoins(entry.coins()), each)));
        }

        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§b[TeslaMaps] §fTotal §a" + shortCoins(total)
                        + " §7(" + shortCoins(coinsPerHour()) + "/h)"));
    }

    public static String shortCoins(double coins) {
        if (coins >= 1_000_000_000) return String.format("%.2fB", coins / 1_000_000_000);
        if (coins >= 1_000_000) return String.format("%.2fM", coins / 1_000_000);
        if (coins >= 1_000) return String.format("%.1fk", coins / 1_000);
        return String.format("%.0f", coins);
    }

    public static void resetSession() {
        gains.clear();
        ledger.clear();
        inbound.clear();
        outbound.clear();
        pending.clear();
        snapshot = null;
        sessionStart = 0L;
        lastGain = 0L;
        activeMillis = 0L;
    }

    public static void cycleWindowBack() { window = window.previous(); }

    /** "12m 04s", or "1h 03m" once it gets long. */
    public static String activeTime() {
        long seconds = activeMillis() / 1000;
        if (seconds >= 3600) return String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        return String.format("%dm %02ds", seconds / 60, seconds % 60);
    }

    public static void resetAllTime() {
        allTime.clear();
        save();
    }

    private static long lastSave = 0L;

    private static void saveThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastSave < 30_000L) return;
        lastSave = now;
        save();
    }

    private static void load() {
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                Map<String, long[]> read = GSON.fromJson(Files.readString(PATH), TYPE);
                if (read != null) allTime = read;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load farm profit", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(allTime));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save farm profit", e);
        }
    }
}
