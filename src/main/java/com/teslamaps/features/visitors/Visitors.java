package com.teslamaps.features.visitors;

// After SkyHanni's VisitorApi / VisitorListener / GardenVisitorTooltip
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.BazaarPrices;
import com.teslamaps.features.ItemIndex;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Garden visitors from the tab list plus whatever their open menu showed. Shared by the visitor features. */
public final class Visitors {

    public static final int INFO_SLOT = 13, ACCEPT_SLOT = 29, REFUSE_SLOT = 33;

    public enum Status {
        NEW("§eNew", 0xFFFF55), WAITING("§7Waiting", -1), READY("§aItems Ready", 0x55FF55),
        ACCEPTED("§8Accepted", 0x555555), REFUSED("§cRefused", 0xFF5555);

        public final String label;
        public final int rgb;

        Status(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }

    public static final class Visitor {
        public final String name;
        public String colored;
        public Status status = Status.NEW;
        public boolean read;
        public final Map<String, Integer> items = new LinkedHashMap<>();
        public final List<String> rewards = new ArrayList<>();
        public final Map<String, Double> linePrice = new HashMap<>();
        public int copper, gardenXp, offersAccepted = -1;
        public double cost, rewardValue, copperValue;
        public int entityId = -1, tagId = -1;
        public long lastSeen;

        Visitor(String name) {
            this.name = name;
            this.colored = "§f" + name;
        }

        public int pricePerCopper() {
            return copper <= 0 ? 0 : (int) (cost / copper);
        }

        /** Coins lost when accepting, negative is a profit. Copper counted at its Green Thumb value. */
        public double loss() {
            return cost - rewardValue - copperValue;
        }
    }

    // Hypixel's visitor reward list SkyHanni warns about, plain names
    public static final String[] RARE_REWARDS = {
            "Flowering Bouquet", "Overgrown Grass", "Green Bandana", "Dedication IV", "Music Rune",
            "Space Helmet", "Cultivating I", "Replenish I", "Delicate V", "Copper Dye", "Jungle Key",
            "Fruit Bowl", "Harvest Harbinger V", "Hypercharge Chip", "Quickdraw Chip", "Farming Exp Boost",
            "Unfulfilled Jerryseed", "Voter's Badge", "Wild Strawberry Dye", "Velvet Top Hat",
            "Cashmere Jacket", "Satin Trousers", "Oxford Shoes", "Carnival Ticket", "Visitors' Gratitude",
            "Farming Contest Display", "Astronaut Minion Skin", "Fast Food Barn Skin",
            "Jelly Garden Greenhouse Skin"
    };

    private static final Pattern COUNT = Pattern.compile("Visitors: \\((\\d+)\\)");
    private static final Pattern ITEM_X = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern LEAD_NUM = Pattern.compile("^([\\d,]+)x? (.+)$");
    private static final Pattern ACCEPTED = Pattern.compile("Offers Accepted: (\\d+)");

    private static final Map<String, Visitor> visitors = new LinkedHashMap<>();
    private static Visitor open;
    private static int openMenu = -1, checkedMenu = -1;
    private static int ticks;
    private static long enteredGarden;
    private static int version;
    private static Map<String, Integer> have = Map.of();

    private Visitors() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    static boolean anyOn() {
        TeslaMapsConfig c = cfg();
        return c.visitorShoppingList || c.visitorOfferInfo || c.visitorRewardGuard || c.visitorStatusHighlight;
    }

    public static void register() {
        Feature.of("Visitors").section("Garden").when(Visitors::anyOn).tick(Visitors::tick);
    }

    public static Collection<Visitor> all() { return visitors.values(); }

    /** Bumps whenever a visitor, its status or the item counts change. */
    public static int version() { return version; }

    /** The visitor whose menu is open right now, or null. */
    public static Visitor open() {
        Minecraft mc = Minecraft.getInstance();
        if (open == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs.getMenu().containerId != openMenu) return null;
        return open;
    }

    public static Visitor byName(String plain) { return visitors.get(plain); }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInGarden()) {
            enteredGarden = 0L;
            open = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (enteredGarden == 0L) enteredGarden = now;
        readMenu(mc);
        if (++ticks % 10 != 0) return;
        readTab(now);
        if (ticks % 20 == 0) updateReady(mc);
    }

    private static void readTab(long now) {
        List<String> lines = TabListUtils.getOrderedLines();
        int remaining = -1;
        List<String> names = null;
        for (String raw : lines) {
            String clean = StringUtil.stripTrim(raw);
            if (remaining < 0) {
                Matcher m = COUNT.matcher(clean);
                if (m.find()) {
                    remaining = Integer.parseInt(m.group(1));
                    names = new ArrayList<>(remaining);
                }
                continue;
            }
            if (remaining-- <= 0) break;
            if (!clean.isEmpty()) names.add(clean);
        }
        if (names == null) return;
        boolean changed = false;
        for (String n : names) {
            Visitor v = visitors.get(n);
            if (v == null) {
                v = new Visitor(n);
                visitors.put(n, v);
                changed = true;
            }
            v.lastSeen = now;
        }
        if (now - enteredGarden > 3000) changed |= visitors.values().removeIf(v -> now - v.lastSeen > 2500);
        if (changed) version++;
    }

    private static void readMenu(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= REFUSE_SLOT) return;
        int id = cs.getMenu().containerId;
        if ((id == openMenu || id == checkedMenu) && ticks % 10 != 0) return;
        checkedMenu = id;
        ItemStack info = slots.get(INFO_SLOT).getItem();
        ItemStack accept = slots.get(ACCEPT_SLOT).getItem();
        if (accept.isEmpty() || !StringUtil.stripTrim(accept.getHoverName().getString()).equals("Accept Offer")) return;
        List<String> infoLore = ItemUtil.loreLines(info);
        if (infoLore.size() != 4 || !infoLore.get(3).trim().startsWith("Offers Accepted:")) return;
        String colored = coloredName(info);
        String plain = StringUtil.stripTrim(colored);
        if (plain.isEmpty()) return;
        Visitor v = visitors.computeIfAbsent(plain, Visitor::new);
        v.colored = colored;
        v.lastSeen = System.currentTimeMillis();
        Matcher m = ACCEPTED.matcher(infoLore.get(3));
        if (m.find()) v.offersAccepted = Integer.parseInt(m.group(1));
        parseOffer(v, ItemUtil.loreLines(accept));
        if (open != v || openMenu != id) version++;
        open = v;
        openMenu = id;
    }

    private static String coloredName(ItemStack stack) {
        String name = ChatText.formatted(stack.getHoverName()).replace("§r", "").trim();
        String plain = StringUtil.strip(name);
        if (name.length() == plain.length() + 4) name = name.substring(2);
        return name.contains("§") ? name : "§f" + name;
    }

    static void parseOffer(Visitor v, List<String> lore) {
        v.items.clear();
        v.rewards.clear();
        v.linePrice.clear();
        v.copper = 0;
        v.gardenXp = 0;
        v.cost = 0;
        v.rewardValue = 0;
        boolean ready = false;
        int section = 0;
        for (String raw : lore) {
            String line = raw.trim();
            if (line.equals("Click to give!")) ready = true;
            if (line.equals("Items Required:")) { section = 1; continue; }
            if (line.equals("Rewards:")) { section = 2; continue; }
            if (line.isEmpty()) { if (section == 1) section = 0; continue; }
            if (section == 1) {
                Matcher m = ITEM_X.matcher(line);
                String name = m.matches() ? m.group(1).trim() : line;
                int amount = m.matches() ? parseInt(m.group(2)) : 1;
                v.items.merge(name, amount, Integer::sum);
                double p = buyPrice(name) * amount;
                v.cost += p;
                v.linePrice.put(line, p);
            } else if (section == 2) {
                String r = line.startsWith("+") ? line.substring(1).trim() : line;
                if (r.endsWith("❤")) r = r.substring(0, r.length() - 1).trim();
                int amount = 1;
                Matcher lead = LEAD_NUM.matcher(r);
                if (lead.matches()) {
                    amount = parseInt(lead.group(1));
                    r = lead.group(2).trim();
                } else {
                    Matcher x = ITEM_X.matcher(r);
                    if (x.matches()) {
                        amount = parseInt(x.group(2));
                        r = x.group(1).trim();
                    }
                }
                if (r.equals("Copper")) v.copper = amount;
                else if (r.equals("Garden Experience")) v.gardenXp = amount;
                else if (!r.endsWith("XP") && !r.endsWith("Experience") && !r.equals("Bits") && !r.equals("Coins")) {
                    v.rewards.add(r);
                    double p = sellPrice(r) * amount;
                    v.rewardValue += p;
                    if (p > 0) v.linePrice.put(line, p);
                }
            }
        }
        v.copperValue = v.copper * copperCoins();
        v.read = true;
        if (v.status == Status.NEW || v.status == Status.WAITING || v.status == Status.READY) {
            v.status = ready ? Status.READY : Status.WAITING;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static String idOf(String name) {
        String id = BazaarPrices.idForName(name);
        if (id != null && PriceManager.getPrice(id) > 0) return id;
        String book = BazaarPrices.idForName("Enchanted Book (" + name + ")");
        return book != null && PriceManager.getPrice(book) > 0 ? book : id;
    }

    public static double buyPrice(String name) {
        String id = idOf(name);
        if (id == null) return 0;
        Double b = PriceManager.bazaarBuyOrNull(id);
        return b != null && b > 0 ? b : PriceManager.getPrice(id);
    }

    static double sellPrice(String name) {
        String id = idOf(name);
        if (id == null) return 0;
        Double s = PriceManager.bazaarSellOrNull(id);
        return s != null && s > 0 ? s : PriceManager.getPrice(id);
    }

    /** Green Thumb I buys 1500 copper, so that is what one copper is worth. */
    public static double copperCoins() {
        return PriceManager.getPrice("ENCHANTMENT_GREEN_THUMB_1") / 1500.0;
    }

    public static boolean isRare(String reward) {
        for (String r : RARE_REWARDS) if (reward.contains(r)) return true;
        return false;
    }

    public static List<String> rareRewards(Visitor v) {
        List<String> out = new ArrayList<>(1);
        for (String r : v.rewards) if (isRare(r)) out.add(r);
        return out;
    }

    /** Held in inventory plus stored in sacks, by plain item name. */
    public static int having(String name) {
        return have.getOrDefault(name.toLowerCase(Locale.ROOT), 0);
    }

    private static void updateReady(Minecraft mc) {
        if (mc.player == null) return;
        Map<String, Integer> counts = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            counts.merge(StringUtil.stripTrim(st.getHoverName().getString()).toLowerCase(Locale.ROOT), st.getCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : ItemIndex.sackAmounts().entrySet()) {
            counts.merge(e.getKey().toLowerCase(Locale.ROOT), e.getValue(), Integer::sum);
        }
        boolean changed = !counts.equals(have);
        have = counts;
        for (Visitor v : visitors.values()) {
            if (!v.read || (v.status != Status.WAITING && v.status != Status.READY)) continue;
            Status s = Status.READY;
            for (Map.Entry<String, Integer> e : v.items.entrySet()) {
                if (having(e.getKey()) < e.getValue()) { s = Status.WAITING; break; }
            }
            if (s != v.status) {
                v.status = s;
                changed = true;
            }
        }
        if (changed) version++;
    }

    /** From HandledScreenClickMixin after the guards: marks the offer as answered. */
    public static void onAnswer(Slot slot, ContainerInput type) {
        Visitor v = open();
        if (v == null || slot == null || type == ContainerInput.QUICK_MOVE) return;
        String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
        if (name.equals("Refuse Offer")) {
            v.status = Status.REFUSED;
            version++;
        } else if (name.equals("Accept Offer") && ItemUtil.loreContains(slot.getItem(), "Click to give!")) {
            v.status = Status.ACCEPTED;
            version++;
        }
    }

    public static boolean ignored(Visitor v) {
        return cfg().visitorIgnored.contains(v.name);
    }

    public static void toggleIgnored(Visitor v) {
        List<String> l = cfg().visitorIgnored;
        if (!l.remove(v.name)) l.add(v.name);
        TeslaMapsConfig.save();
        version++;
    }
}
