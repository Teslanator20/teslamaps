package com.teslamaps.features;

// Ported from Skyblocker's BazaarOrderTracker
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "N of them are yours" under each price level of the Create Buy Order / Sell Offer tooltip. */
public final class BazaarOrderTooltip {

    private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:Order|Offer) amount: ([\\d,]+)x");
    private static final Pattern PRICE = Pattern.compile("^Price per unit: ([\\d,.]+) coins");
    private static final Pattern BOOK = Pattern.compile("^- ([\\d,.]+) coins? each \\| ([\\d,]+)x (?:in|from) ([\\d,]+) (?:order|offer)s?");

    private record Order(String item, double unit, int amount, boolean sell) {}
    private record Insert(int after, String text) {}

    private static final List<Order> orders = new ArrayList<>();
    private static final Map<ItemStack, List<Insert>> cache = new IdentityHashMap<>();
    private static Feature feature;
    private static int ticks;

    private BazaarOrderTooltip() {}

    public static void register() {
        feature = Feature.of("BazaarOrderTooltip").section("Inventory")
                .when(() -> TeslaMapsConfig.get().bazaarOrderTooltip)
                .tick(BazaarOrderTooltip::tick);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || orders.isEmpty() || !feature.active()) return;
            List<Insert> ins = cache.get(stack);
            if (ins == null) {
                ins = build(stack, lines);
                if (cache.size() > 64) cache.clear();
                cache.put(stack, ins);
            }
            for (int i = ins.size() - 1; i >= 0; i--) {
                Insert in = ins.get(i);
                if (in.after() < lines.size()) lines.add(in.after() + 1, Component.literal(in.text()));
            }
        });
    }

    private static void tick() {
        if (ticks++ % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.endsWith("Bazaar Orders")) return;
        String me = mc.player.getName().getString();
        List<Order> found = new ArrayList<>();
        boolean any = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher n = NAME.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (!n.matches()) continue;
            any = true;
            Double unit = null;
            Integer amount = null;
            boolean mine = true;
            for (String l : ItemUtil.loreLines(stack)) {
                Matcher m;
                if (unit == null && (m = PRICE.matcher(l)).find()) unit = parse(m.group(1));
                else if (amount == null && (m = AMOUNT.matcher(l)).find()) amount = (int) (double) parse(m.group(1));
                else if (l.startsWith("By: ") && !l.contains(me)) mine = false;
            }
            if (unit == null || amount == null || !mine) continue;
            found.add(new Order(n.group(2).toLowerCase(Locale.ROOT), unit, amount, n.group(1).equals("SELL")));
        }
        // Hypixel fills the menu async: an empty page never wipes a good snapshot
        if (!any && !orders.isEmpty()) return;
        if (!found.equals(orders)) {
            orders.clear();
            orders.addAll(found);
            cache.clear();
        }
    }

    private static List<Insert> build(ItemStack stack, List<Component> lines) {
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        boolean sell;
        if (name.equals("Create Buy Order")) sell = false;
        else if (name.equals("Create Sell Offer")) sell = true;
        else return List.of();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs.getMenu().slots.size() <= 13) return List.of();
        String item = StringUtil.stripTrim(cs.getMenu().getSlot(13).getItem().getHoverName().getString()).toLowerCase(Locale.ROOT);

        List<Insert> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = BOOK.matcher(StringUtil.stripTrim(lines.get(i).getString()));
            if (!m.find()) continue;
            double unit = parse(m.group(1));
            int count = 0, amount = 0;
            for (Order o : orders) {
                if (o.sell() != sell || !o.item().equals(item) || Math.abs(o.unit() - unit) > 0.05) continue;
                count++;
                amount += o.amount();
            }
            if (count > 0) out.add(new Insert(i, "§8  - §a" + amount + " §7of them are yours (in §f" + count
                    + " §7" + (sell ? "offer" : "order") + (count == 1 ? "" : "s") + ")"));
        }
        return out;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
