package com.teslamaps.features.bazaar;

// Ported from Skysoft's BazaarTrackerHud / Alerts / Chat / Parsing, trimmed to one class
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.BzTrackedOrder;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BazaarOrderTracker {

    enum Status {
        COMPETITIVE("§a"), OUTBID("§c"), UNDERCUT("§6"), FILLED("§b"), UNKNOWN("§8");
        final String color;
        Status(String color) { this.color = color; }
        boolean warning() { return this == OUTBID || this == UNDERCUT; }
    }

    private static final Pattern TITLE = Pattern.compile("(?:Your|Co-op) Bazaar Orders");
    private static final Pattern HEADER = Pattern.compile("^(BUY|SELL) (.+)$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x");
    private static final Pattern FILLED = Pattern.compile("^Filled: ([\\d,.kKmM]+)/([\\d,.kKmM]+)(?: (\\S+))?");
    private static final Pattern PRICE = Pattern.compile("^Price per unit: ([\\d,.]+) coins");
    private static final Pattern OWNER = Pattern.compile("^By: (?:\\[[^]]+] )*(\\w{1,16})$");

    private static final Pattern BUY_SETUP = Pattern.compile("^\\[Bazaar] Buy Order Setup! ([\\d,]+)x (.+) for ([\\d,.]+) coins\\.$");
    private static final Pattern SELL_SETUP = Pattern.compile("^\\[Bazaar] Sell Offer Setup! ([\\d,]+)x (.+) for ([\\d,.]+) coins\\.$");
    private static final Pattern FLIPPED = Pattern.compile("^\\[Bazaar] Order Flipped! ([\\d,]+)x (.+) for ([\\d,.]+) coins of total expected profit\\.$");
    private static final Pattern BUY_CANCEL = Pattern.compile("^(?:\\[Bazaar] )?Cancelled! Refunded ([\\d,.]+) coins from cancelling Buy Order!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL_CANCEL = Pattern.compile("^(?:\\[Bazaar] )?Cancelled! Refunded ([\\d,]+)x (.+) from cancelling sell offer!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUY_CLAIM = Pattern.compile("^\\[Bazaar] Claimed ([\\d,]+)x (.+) worth ([\\d,.]+) coins bought for ([\\d,.]+) each!$");
    private static final Pattern SELL_CLAIM = Pattern.compile("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+) at ([\\d,.]+) each!$");
    private static final Pattern WAS_FILLED = Pattern.compile("^\\[Bazaar] Your (Buy Order|Sell Offer) for ([\\d,]+)x (.+) was filled!$");

    private static final long OUTBID_COOLDOWN_MS = 60_000;
    private static final double EPSILON = 0.01;
    private static final List<String> SAMPLE = List.of("§e§lBazaar Orders",
            "§bBUY §e640x §fEnchanted Diamond §7@ §6172k §8(120/640) §aCOMPETITIVE",
            "§dSELL §e64x §fBooster Cookie §7@ §63.1M §8(0/64) §cUNDERCUT");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static Screen scanned;
    private static int screenTicks;
    private static final Map<String, Status> lastStatus = new HashMap<>();
    private static final Map<String, Long> lastOutbidSound = new HashMap<>();

    /** Last order clicked in the orders menu, so a cancel line knows the item. */
    public static String lastClickedItem;
    public static boolean lastClickedBuy;

    private BazaarOrderTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("bzTracker", "Bazaar Order Tracker")
                .enabled(() -> cfg().bzTracker)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width,
                        () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("BazaarOrderTracker").when(() -> cfg().bzTracker)
                .tick(BazaarOrderTracker::tick)
                .serverChat(BazaarOrderTracker::onChat)
                .hudForeground("bztracker", BazaarOrderTracker::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Bazaar & Auction Tools");
                    b.toggle("Bazaar Order Tracker HUD", () -> c.bzTracker, v -> { c.bzTracker = v; ticks = 0; })
                            .desc("All your buy orders and sell offers with fill progress and a live market status "
                                    + "(competitive / outbid / undercut / filled). Open Bazaar Orders once to load them. Position in /tmap gui.")
                            .tags("bazaar", "order", "flip", "outbid", "undercut", "tracker");
                    b.slider("  Max Orders", 1, 20, () -> (float) c.bzTrackerMaxOrders, v -> c.bzTrackerMaxOrders = Math.round(v), 0);
                    b.toggle("  Only In Bazaar Menus", () -> c.bzTrackerOnlyMenus, v -> c.bzTrackerOnlyMenus = v);
                    b.toggle("  Hide When Empty", () -> c.bzTrackerHideEmpty, v -> { c.bzTrackerHideEmpty = v; ticks = 0; });
                    b.toggle("  Sound: Filled", () -> c.bzTrackerSoundFilled, v -> c.bzTrackerSoundFilled = v);
                    b.toggle("  Sound: Partly Filled", () -> c.bzTrackerSoundPartial, v -> c.bzTrackerSoundPartial = v);
                    b.toggle("  Sound: Outbid / Undercut", () -> c.bzTrackerSoundOutbid, v -> c.bzTrackerSoundOutbid = v)
                            .desc("At most once per minute per order");
                });
    }

    private static List<BzTrackedOrder> orders() {
        String k = SbProfile.key();
        return cfg().bzTrackerOrders.computeIfAbsent(k == null ? "default" : k, x -> new ArrayList<>());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs
                && TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) {
            if (cs != scanned) { scanned = cs; screenTicks = 0; }
            // Hypixel fills the menu async
            if (++screenTicks >= 20 && screenTicks % 10 == 0) scan(cs, mc);
        } else {
            scanned = null;
        }
        if (ticks++ % 20 != 0) return;
        PriceManager.ensureFresh();
        build();
    }

    private static void scan(AbstractContainerScreen<?> cs, Minecraft mc) {
        if (mc.player == null) return;
        String own = mc.player.getName().getString();
        List<BzTrackedOrder> old = orders();
        List<BzTrackedOrder> found = new ArrayList<>();
        boolean partial = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher h = HEADER.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!h.matches()) continue;
            BzTrackedOrder o = new BzTrackedOrder();
            o.buy = h.group(1).equals("BUY");
            o.item = h.group(2);
            boolean mine = true, full = false;
            int filledTotal = 0;
            for (String line : ItemUtil.loreLines(stack)) {
                String l = line.trim();
                Matcher m;
                if ((m = AMOUNT.matcher(l)).find()) o.amount = parseInt(m.group(1));
                else if ((m = FILLED.matcher(l)).find()) {
                    o.filled = parseShort(m.group(1));
                    filledTotal = parseShort(m.group(2));
                    full = "100%!".equals(m.group(3));
                } else if ((m = PRICE.matcher(l)).find()) o.price = parseDouble(m.group(1));
                else if ((m = OWNER.matcher(l)).find()) mine = m.group(1).equalsIgnoreCase(own);
            }
            if (!mine) continue;
            if (o.amount <= 0) o.amount = filledTotal;
            if (full) o.filled = o.amount;
            BzTrackedOrder prev = match(old, o.buy, o.item, o.price);
            o.created = prev != null ? prev.created : System.currentTimeMillis();
            if (prev != null && o.filled > prev.filled && o.filled < o.amount) partial = true;
            found.add(o);
        }
        boolean changed = found.size() != old.size();
        for (int i = 0; !changed && i < found.size(); i++) {
            BzTrackedOrder a = found.get(i), b = old.get(i);
            changed = a.buy != b.buy || !a.item.equals(b.item) || a.amount != b.amount || a.filled != b.filled
                    || Math.abs(a.price - b.price) > EPSILON;
        }
        if (!changed) return;
        old.clear();
        old.addAll(found);
        if (partial && cfg().bzTrackerSoundPartial) play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.35f);
        TeslaMapsConfig.save();
        ticks = 0;
    }

    private static BzTrackedOrder match(List<BzTrackedOrder> list, boolean buy, String item, double price) {
        BzTrackedOrder best = null;
        for (BzTrackedOrder o : list) {
            if (o.buy != buy || !o.item.equalsIgnoreCase(item)) continue;
            if (price > 0 && Math.abs(o.price - price) < EPSILON) return o;
            if (best == null) best = o;
        }
        return best;
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.startsWith("[Bazaar]") && !s.startsWith("Cancelled!")) return;
        List<BzTrackedOrder> list = orders();
        Matcher m;
        if ((m = BUY_SETUP.matcher(s)).matches() || (m = SELL_SETUP.matcher(s)).matches()) {
            BzTrackedOrder o = new BzTrackedOrder();
            o.buy = s.contains("Buy Order Setup");
            o.amount = parseInt(m.group(1));
            o.item = m.group(2);
            double total = parseDouble(m.group(3));
            o.price = o.amount > 0 ? total / o.amount : 0;
            o.created = System.currentTimeMillis();
            list.add(o);
        } else if ((m = FLIPPED.matcher(s)).matches()) {
            int amount = parseInt(m.group(1));
            String item = m.group(2);
            BzTrackedOrder buy = match(list, true, item, 0);
            if (buy != null) list.remove(buy);
            BzTrackedOrder o = new BzTrackedOrder();
            o.amount = amount;
            o.item = item;
            o.created = System.currentTimeMillis();
            list.add(o);
        } else if (BUY_CANCEL.matcher(s).matches()) {
            if (lastClickedItem != null && lastClickedBuy) {
                BzTrackedOrder o = match(list, true, lastClickedItem, 0);
                if (o != null) list.remove(o);
            }
        } else if ((m = SELL_CANCEL.matcher(s)).matches()) {
            BzTrackedOrder o = match(list, false, m.group(2), 0);
            if (o != null) list.remove(o);
        } else if ((m = BUY_CLAIM.matcher(s)).matches()) {
            claimed(list, true, m.group(2), parseDouble(m.group(4)));
        } else if ((m = SELL_CLAIM.matcher(s)).matches()) {
            claimed(list, false, m.group(3), parseDouble(m.group(4)));
        } else if ((m = WAS_FILLED.matcher(s)).matches()) {
            boolean buy = m.group(1).equals("Buy Order");
            BzTrackedOrder o = match(list, buy, m.group(3), 0);
            int amount = parseInt(m.group(2));
            for (BzTrackedOrder x : list) {
                if (x.buy == buy && x.item.equalsIgnoreCase(m.group(3)) && x.amount == amount && x.filled < x.amount) { o = x; break; }
            }
            if (o != null) o.filled = o.amount;
            if (cfg().bzTrackerSoundFilled) play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f);
        } else {
            return;
        }
        TeslaMapsConfig.save();
        ticks = 0;
    }

    private static void claimed(List<BzTrackedOrder> list, boolean buy, String item, double price) {
        BzTrackedOrder o = match(list, buy, item, price);
        if (o != null && o.filled >= o.amount) list.remove(o);
    }

    /** Called from the slot click hook in every container. */
    public static void onSlotClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (!TeslaMapsConfig.get().bzTracker || slot == null || slot.container instanceof Inventory || slot.getItem().isEmpty()) return;
        if (!TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return;
        Matcher h = HEADER.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString()));
        if (!h.matches()) return;
        lastClickedBuy = h.group(1).equals("BUY");
        lastClickedItem = h.group(2);
    }

    static Status status(BzTrackedOrder o) {
        if (o.amount > 0 && o.filled >= o.amount) return Status.FILLED;
        if (o.price <= 0) return Status.UNKNOWN;
        String id = PriceManager.idForName(o.item);
        if (id == null) id = SkyblockItems.idForName(o.item);
        if (id == null) return Status.UNKNOWN;
        Double best = o.buy ? PriceManager.bazaarBuyOrderOrNull(id) : PriceManager.bazaarSellOfferOrNull(id);
        if (best == null) return Status.UNKNOWN;
        boolean ok = o.buy ? o.price + EPSILON >= best : o.price <= best + EPSILON;
        // market data older than the order would flag every fresh order
        if (ok || PriceManager.bazaarUpdatedAt() <= o.created) return Status.COMPETITIVE;
        return o.buy ? Status.OUTBID : Status.UNDERCUT;
    }

    private static int priority(Status s) {
        return switch (s) {
            case FILLED -> 3;
            case OUTBID, UNDERCUT -> 2;
            case COMPETITIVE -> 1;
            case UNKNOWN -> 0;
        };
    }

    private static void build() {
        TeslaMapsConfig c = cfg();
        List<BzTrackedOrder> list = orders();
        List<Status> statuses = new ArrayList<>(list.size());
        long now = System.currentTimeMillis();
        Map<String, Status> seen = new HashMap<>();
        for (BzTrackedOrder o : list) {
            Status st = status(o);
            statuses.add(st);
            String key = (o.buy ? "B|" : "S|") + o.item + "|" + o.price;
            seen.put(key, st);
            Status prev = lastStatus.get(key);
            if (prev != null && st.warning() && !prev.warning() && c.bzTrackerSoundOutbid) {
                Long last = lastOutbidSound.get(key);
                if (last == null || now - last >= OUTBID_COOLDOWN_MS) {
                    lastOutbidSound.put(key, now);
                    play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.65f, 0.7f);
                }
            }
        }
        lastStatus.clear();
        lastStatus.putAll(seen);
        lastOutbidSound.keySet().retainAll(seen.keySet());

        if (list.isEmpty() && c.bzTrackerHideEmpty) {
            lines = List.of();
            return;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) order.add(i);
        order.sort((a, b) -> {
            int p = Integer.compare(priority(statuses.get(b)), priority(statuses.get(a)));
            return p != 0 ? p : Long.compare(list.get(b).created, list.get(a).created);
        });
        List<String> out = new ArrayList<>();
        out.add("§e§lBazaar Orders");
        if (list.isEmpty()) out.add("§7Open §eBazaar Orders §7to load orders.");
        int max = Math.max(1, Math.min(20, c.bzTrackerMaxOrders));
        for (int i = 0; i < order.size() && i < max; i++) {
            BzTrackedOrder o = list.get(order.get(i));
            Status st = statuses.get(order.get(i));
            out.add((o.buy ? "§bBUY " : "§dSELL ") + "§e" + o.amount + "x §f" + o.item
                    + " §7@ §6" + (o.price > 0 ? coins(o.price) : "?")
                    + " §8(" + o.filled + "/" + o.amount + ") " + st.color + (st == Status.UNKNOWN ? "" : st.name()));
        }
        if (order.size() > max) out.add("§8+" + (order.size() - max) + " more");
        lines = out;
        width = TextHud.width(out);
    }

    static String coins(double v) {
        if (v >= 1000) return com.teslamaps.utils.ItemPrice.format(v);
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        if (cfg().bzTrackerOnlyMenus) {
            if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
            String t = cs.getTitle().getString();
            if (!t.contains("Bazaar") && !t.contains("Order options")) return;
        }
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The filled line abbreviates: "1.2k", "34", "1.9m". */
    static int parseShort(String s) {
        String clean = s.replace(",", "").toLowerCase(Locale.ROOT);
        double f = 1;
        if (clean.endsWith("k")) { f = 1_000; clean = clean.substring(0, clean.length() - 1); }
        else if (clean.endsWith("m")) { f = 1_000_000; clean = clean.substring(0, clean.length() - 1); }
        try {
            return (int) Math.round(Double.parseDouble(clean) * f);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
