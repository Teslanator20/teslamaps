package com.teslamaps.features.garden;

// After SkyHanni's ComposterInventoryNumbers and GardenComposterInventoryFeatures
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Numbers in the Composter menu, upgrade cost prices and affordable upgrades in gold. */
public final class ComposterMenu {

    private static final Pattern VALUE = Pattern.compile("([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)\\s*$");
    private static final Pattern AVAILABLE = Pattern.compile("^Compost Available: ([\\d,]+)");
    private static final Pattern COST = Pattern.compile("^(.+?)(?: x([\\d,]+))?$");

    private record Tip(int slot, String text, boolean above) {}

    private static Feature feature;
    private static List<Tip> tips = List.of();
    private static List<Integer> gold = List.of();
    private static boolean upgrades;
    private static int ticks;

    private ComposterMenu() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ComposterMenu").section("Garden")
                .when(() -> cfg().composterMenuNumbers || cfg().composterUpgradePrices || cfg().composterUpgradeHighlight)
                .tick(ComposterMenu::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Composter Menu Numbers", () -> c.composterMenuNumbers, v -> c.composterMenuNumbers = v)
                            .desc("Compost available, organic matter and fuel (x/y) written into the Composter menu.");
                    b.toggle("  Upgrade Cost Prices", () -> c.composterUpgradePrices, v -> c.composterUpgradePrices = v)
                            .desc("Composter Upgrades: price behind every cost item plus the total, using the overlay's price source.");
                    b.toggle("  Highlight Affordable Upgrades", () -> c.composterUpgradeHighlight, v -> c.composterUpgradeHighlight = v)
                            .desc("Upgrades showing 'Click to upgrade!' turn gold.");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!upgrades || feature == null || !feature.active() || !cfg().composterUpgradePrices || lines.isEmpty()) return;
            addPrices(lines);
        });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !SkyblockUtils.isInGarden()) {
            clear();
            return;
        }
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        upgrades = title.equals("Composter Upgrades");
        TeslaMapsConfig c = cfg();
        List<Slot> slots = cs.getMenu().slots;
        if (title.equals("Composter") && c.composterMenuNumbers) {
            List<Tip> out = new ArrayList<>(3);
            if (slots.size() > 52) {
                for (String l : ItemUtil.loreLines(slots.get(13).getItem())) {
                    Matcher m = AVAILABLE.matcher(l.trim());
                    if (m.find()) { out.add(new Tip(13, "§6" + m.group(1), false)); break; }
                }
                value(out, slots.get(46).getItem(), 46, "§e");
                value(out, slots.get(52).getItem(), 52, "§a");
            }
            tips = out;
        } else if (!tips.isEmpty()) {
            tips = List.of();
        }
        if (upgrades && c.composterUpgradeHighlight) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (s.container instanceof Inventory || !s.hasItem()) continue;
                if (ItemUtil.loreContains(s.getItem(), "Click to upgrade!")) out.add(i);
            }
            gold = out;
        } else if (!gold.isEmpty()) {
            gold = List.of();
        }
        if (upgrades && c.composterUpgradePrices) PriceManager.ensureFresh();
    }

    private static void value(List<Tip> out, ItemStack stack, int slot, String color) {
        for (String l : ItemUtil.loreLines(stack)) {
            Matcher m = VALUE.matcher(l);
            if (!m.find()) continue;
            double have = NumberUtil.parseSuffixed(m.group(1));
            out.add(new Tip(slot, color + NumberUtil.formatSuffixed(have) + "§7/" + color + m.group(2), true));
            return;
        }
    }

    private static void clear() {
        if (!tips.isEmpty()) tips = List.of();
        if (!gold.isEmpty()) gold = List.of();
        upgrades = false;
    }

    private static void addPrices(List<Component> lines) {
        int header = -1, items = 0;
        double total = 0;
        int source = cfg().composterPriceSource;
        for (int i = 0; i < lines.size(); i++) {
            String s = StringUtil.stripTrim(lines.get(i).getString());
            if (header < 0) {
                if (s.equals("Upgrade Cost:")) header = i;
                continue;
            }
            if (s.isEmpty()) break;
            if (s.endsWith(" Copper")) continue;
            Matcher m = COST.matcher(s);
            if (!m.matches()) continue;
            String id = PriceManager.idForName(m.group(1));
            Double p = id == null ? null : PriceManager.getPriceOrNull(id, source);
            if (p == null) continue;
            int amount = m.group(2) == null ? 1 : NumberUtil.parseCommaInt(m.group(2));
            double price = p * amount;
            total += price;
            items++;
            lines.set(i, lines.get(i).copy().append(Component.literal(" §7(§6" + EstimatedValue.fmt(price) + "§7)")));
        }
        if (items > 1) lines.set(header, lines.get(header).copy().append(Component.literal(" §7(§6" + EstimatedValue.fmt(total) + "§7)")));
    }

    /** Gold fill on affordable upgrades, from ContainerOverlays. */
    public static void render(GuiGraphicsExtractor g) {
        if (gold.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (int i : gold) {
            if (i >= slots.size()) continue;
            Slot s = slots.get(i);
            int x = acc.getX() + s.x, y = acc.getY() + s.y;
            g.fill(x, y, x + 16, y + 16, 0x66FFAA00);
        }
    }

    /** Slot text, from SlotLockRenderMixin. */
    public static void renderTips(GuiGraphicsExtractor g) {
        if (tips.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Tip t : tips) {
            if (t.slot >= slots.size()) continue;
            Slot s = slots.get(t.slot);
            int w = mc.font.width(t.text);
            int x = acc.getX() + s.x + 8 - w / 2;
            int y = t.above ? acc.getY() + s.y - 10 : acc.getY() + s.y + 18;
            g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xB0000000);
            g.text(mc.font, t.text, x, y, 0xFFFFFFFF, true);
        }
    }
}
