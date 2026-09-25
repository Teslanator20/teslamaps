package com.teslamaps.features.accessories;

// Ported from Skyblocker's AccessoriesHelperWidget.java + AccessoryTooltip.java, slot symbols from SkyOcean
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class AccessoryHelper {

    private static final Pattern BAG_TITLE = Pattern.compile("^Accessory Bag(?: \\(\\d+/\\d+\\))?$");
    private static final String[] FILTERS = {"All", "Missing", "Upgrades"};
    private static final int W = 180, ROW = 18, HEAD = 14, FOOT = 14;

    private record Row(String id, String name, double cost, int mp, double perMp, String replaces, String recombRarity) {}

    private static Feature feature;
    private static int filter;
    private static int page;
    private static boolean inBag;
    private static int timer;
    private static int builtFor = Integer.MIN_VALUE;
    private static List<Row> rows = List.of();
    private static final Map<String, ItemStack> icons = new ConcurrentHashMap<>();
    private static final Set<String> resolving = new HashSet<>();
    private static int px, py, ph, rowsShown;

    private AccessoryHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("Accessories").section("Inventory")
                .when(() -> cfg().accessoryHelper || cfg().accessoryTooltip || cfg().accessorySlotSymbol)
                .tick(AccessoryHelper::tick)
                .config("Inventory", b -> {
                    b.label("Accessories");
                    b.toggle("Accessory Helper", () -> cfg().accessoryHelper, v -> cfg().accessoryHelper = v)
                            .desc("Panel next to the Accessory Bag: missing and upgradable accessories, cheapest coins per Magical Power first.");
                    b.toggle("  Highest Tier Only", () -> cfg().accessoryHelperHighestOnly, v -> cfg().accessoryHelperHighestOnly = v);
                    b.toggle("Accessory Status In Tooltip", () -> cfg().accessoryTooltip, v -> cfg().accessoryTooltip = v)
                            .desc("Collected / Upgrade / Upgradable / Downgrade / Missing on every accessory.");
                    b.toggle("  Status Symbol In Slots", () -> cfg().accessorySlotSymbol, v -> cfg().accessorySlotSymbol = v);
                    b.info("Open every Accessory Bag page once so the owned accessories are known");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature != null && cfg().accessoryTooltip && feature.active()) onTooltip(stack, lines);
        });
    }

    private static boolean active() {
        return feature != null && feature.active();
    }

    private static void tick() {
        AccessoryData.tick();
        Minecraft mc = Minecraft.getInstance();
        inBag = cfg().accessoryHelper && mc.screen instanceof AbstractContainerScreen<?> cs
                && BAG_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches();
        if (!inBag) {
            page = 0;
            return;
        }
        PriceManager.ensureFresh();
        int key = AccessoryData.version() * 31 + filter * 7 + (cfg().accessoryHelperHighestOnly ? 1 : 0);
        if (key == builtFor && timer-- > 0) return;
        timer = 40;
        builtFor = key;
        rows = build();
    }

    private static Double price(String id) {
        Double p = PriceManager.bazaarBuyOrNull(id);
        if (p == null) p = PriceManager.lowestBinOrNull(id);
        if (p == null) p = CraftCost.costOrNull(id, PriceManager.SOURCE_BAZAAR_BUY);
        return p;
    }

    private static String name(String id) {
        String n = SkyblockItems.coloredName(id);
        return n != null ? n : "§f" + id;
    }

    private static List<Row> build() {
        List<Row> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean highest = cfg().accessoryHelperHighestOnly;
        for (String id : SkyblockItems.accessoryIds()) {
            if (!AccessoryData.isAccessory(id)) continue;
            int t = AccessoryData.tierOf(id);
            int have = AccessoryData.ownedTierFor(id);
            String fam = AccessoryData.familyOf(id);
            if (fam == null ? have >= 0 : t <= have) continue;
            if (filter == 1 && have >= 0) continue;
            if (filter == 2 && have < 0) continue;
            if (highest && fam != null && t < AccessoryData.familyMaxTier(fam)) continue;
            if (!seen.add(id)) continue;
            String ownedId = have >= 0 ? AccessoryData.ownedBestInFamily(id) : null;
            int mp = AccessoryData.mp(SkyblockItems.tier(id));
            if (ownedId != null) mp -= AccessoryData.mp(SkyblockItems.tier(ownedId));
            Double cost = price(id);
            if (cost != null && ownedId != null) {
                Double back = price(ownedId);
                if (back != null) cost -= back;
            }
            double c = cost == null ? -1 : Math.max(0, cost);
            double per = cost == null || mp <= 0 ? Double.MAX_VALUE : c / mp;
            out.add(new Row(id, name(id), c, mp, per, ownedId, null));
        }
        if (filter != 1) {
            Set<String> rarities = new HashSet<>();
            for (String id : AccessoryData.owned()) {
                if (!AccessoryData.isAccessory(id) || AccessoryData.isRecombed(id)) continue;
                String r = SkyblockItems.tier(id);
                if (AccessoryData.recombed(r) != null) rarities.add(r);
            }
            Double rp = PriceManager.bazaarBuyOrNull("RECOMBOBULATOR_3000");
            if (rp == null) rp = PriceManager.lowestBinOrNull("RECOMBOBULATOR_3000");
            double recomb = rp == null ? 6_000_000 : rp;
            for (String r : rarities) {
                int mp = AccessoryData.mp(AccessoryData.recombed(r)) - AccessoryData.mp(r);
                if (mp <= 0) continue;
                out.add(new Row("RECOMBOBULATOR_3000", "§dRecombobulate a " + r.replace('_', ' ').toLowerCase(), recomb, mp, recomb / mp, null, r));
            }
        }
        out.sort(Comparator.comparingDouble(Row::perMp));
        return out;
    }

    private static ItemStack icon(String id) {
        ItemStack st = icons.get(id);
        if (st != null) return st;
        if (resolving.add(id)) SkyblockItems.resolve(id, s -> icons.put(id, s));
        return ItemStack.EMPTY;
    }

    private static int pages() {
        return Math.max(1, (rows.size() + Math.max(1, rowsShown) - 1) / Math.max(1, rowsShown));
    }

    /** Panel left of the bag, from ContainerOverlays. */
    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!inBag || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Font font = mc.font;
        int h = Math.max(120, acc.getImageHeight());
        rowsShown = Math.max(1, (h - HEAD - FOOT) / ROW);
        px = Math.max(2, acc.getX() - W - 4);
        py = acc.getY();
        page = Math.min(page, pages() - 1);
        ph = h;
        HudStyle.screenPanel(ctx, px, py, W, h, 4);
        ctx.text(font, "§6Accessory Helper", px + 4, py + 4, 0xFFFFFFFF, true);
        String f = "§7[" + FILTERS[filter] + "]";
        ctx.text(font, f, px + W - 4 - font.width(f), py + 4, 0xFFFFFFFF, true);

        if (!AccessoryData.hasData()) {
            ctx.text(font, "§7Open your bag pages first", px + 4, py + HEAD + 4, 0xFFFFFFFF, true);
        } else if (rows.isEmpty()) {
            ctx.text(font, SkyblockItems.accessoryIds().isEmpty() ? "§7Loading..." : "§aNothing left here", px + 4, py + HEAD + 4, 0xFFFFFFFF, true);
        }
        Row hover = null;
        int start = page * rowsShown;
        for (int i = 0; i < rowsShown && start + i < rows.size(); i++) {
            Row r = rows.get(start + i);
            int ry = py + HEAD + i * ROW;
            boolean over = mouseX >= px && mouseX < px + W && mouseY >= ry && mouseY < ry + ROW;
            if (over) {
                ctx.fill(px + 2, ry, px + W - 2, ry + ROW, 0x30FFFFFF);
                hover = r;
            }
            ItemStack st = icon(r.id);
            if (!st.isEmpty()) ctx.item(st, px + 4, ry + 1);
            String per = r.perMp == Double.MAX_VALUE ? "§8?" : "§6" + NumberUtil.formatSuffixed(r.perMp);
            int pw = font.width(per);
            String n = font.plainSubstrByWidth(r.name, W - 30 - pw - 6);
            ctx.text(font, n, px + 22, ry + 5, 0xFFFFFFFF, true);
            ctx.text(font, per, px + W - 4 - pw, ry + 5, 0xFFFFFFFF, true);
        }
        String pg = "§7‹ §f" + (page + 1) + "/" + pages() + " §7›";
        ctx.text(font, pg, px + (W - font.width(pg)) / 2, py + h - FOOT + 3, 0xFFFFFFFF, true);
        String top = cfg().accessoryHelperHighestOnly ? "§e[Top]" : "§8[Top]";
        ctx.text(font, "§8coins/MP", px + 4, py + h - FOOT + 3, 0xFFFFFFFF, true);
        ctx.text(font, top, px + W - 4 - font.width(top), py + h - FOOT + 3, 0xFFFFFFFF, true);

        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name));
            if (hover.recombRarity != null) tip.add(Component.literal("§7One owned " + hover.recombRarity.replace('_', ' ') + " accessory"));
            if (hover.replaces != null) tip.add(Component.literal("§7Replaces " + name(hover.replaces)));
            tip.add(Component.literal("§7Magical Power: §a+" + hover.mp));
            tip.add(Component.literal("§7Cost: " + (hover.cost < 0 ? "§8unknown" : "§6" + NumberUtil.formatSuffixed(hover.cost)
                    + (hover.replaces != null ? " §8(after selling the old one)" : ""))));
            if (hover.perMp != Double.MAX_VALUE) tip.add(Component.literal("§7Coins per MP: §6" + NumberUtil.formatSuffixed(hover.perMp)));
            ctx.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
    }

    private static boolean inside(double mx, double my) {
        return mx >= px && mx < px + W && my >= py && my < py + ph;
    }

    public static boolean handleClick(double mx, double my, int button) {
        if (!inBag || !active() || !inside(mx, my)) return false;
        int h = ph;
        if (my < py + HEAD) {
            if (mx > px + W / 2) {
                filter = (filter + 1) % FILTERS.length;
                page = 0;
                builtFor = Integer.MIN_VALUE;
            }
        } else if (my >= py + h - FOOT) {
            if (mx > px + W - 40) {
                cfg().accessoryHelperHighestOnly = !cfg().accessoryHelperHighestOnly;
                page = 0;
                builtFor = Integer.MIN_VALUE;
            } else if (mx < px + W / 2 && mx > px + 50) {
                page = Math.max(0, page - 1);
            } else if (mx >= px + W / 2) {
                page = Math.min(pages() - 1, page + 1);
            }
        }
        return true;
    }

    public static boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!inBag || !active() || !inside(mx, my)) return false;
        page = Math.max(0, Math.min(pages() - 1, page + (scrollY < 0 ? 1 : -1)));
        return true;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        String id = ItemUtil.skyblockId(stack);
        if (!AccessoryData.isAccessory(id)) return;
        AccessoryData.Report r = AccessoryData.report(id);
        if (r == null) return;
        String dupe = AccessoryData.isDuplicate(id) ? " §b☰ duplicate" : "";
        lines.add(Component.literal("§7Accessory: " + r.status().symbol + " " + r.status().label
                + (r.detail().isEmpty() ? "" : " §8" + r.detail()) + dupe));
    }

    /** Status symbol in each accessory slot, from ContainerOverlays. */
    public static void renderSlots(GuiGraphicsExtractor ctx) {
        if (!cfg().accessorySlotSymbol || !active() || !AccessoryData.hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String id = ItemUtil.skyblockId(st);
            if (!AccessoryData.isAccessory(id)) continue;
            String sym;
            if (AccessoryData.isDuplicate(id)) sym = "§b☰";
            else {
                AccessoryData.Report r = AccessoryData.report(id);
                if (r == null) continue;
                sym = r.status().symbol;
            }
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.text(mc.font, sym, sx + 17 - mc.font.width(sym), sy + 9, 0xFFFFFFFF, true);
        }
    }
}
