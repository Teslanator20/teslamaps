package com.teslamaps.features;

// Concept after Skytils' PricePaid
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Remembers what a BIN purchase cost, keyed by the item uuid, and shows it in the tooltip. */
public final class PricePaid {

    private static final Pattern PURCHASED = Pattern.compile("^You purchased (.+) for ([\\d,]+) coins!$");
    private static final Pattern PRICE = Pattern.compile("^(?:Buy it now|Price): ([\\d,]+) coins$");
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("price_paid.json");
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Long>>() {}.getType();
    private static final int KEEP = 5000;

    private static Map<String, Long> paid;
    private static String pendingUuid, pendingName;
    private static long pendingPrice, pendingAt;
    private static ItemStack lastStack;
    private static Long lastValue;
    private static int ticks;
    private static Feature feature;

    private PricePaid() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PricePaid").when(() -> cfg().pricePaid)
                .tick(PricePaid::tick)
                .serverChat(PricePaid::onChat)
                .config("Tooltips & Value", b -> {
                    b.label("Price Paid");
                    b.toggle("Price Paid In Tooltip", () -> cfg().pricePaid, v -> cfg().pricePaid = v)
                            .desc("BIN purchases remember their price per item uuid, the tooltip shows \"Price Paid\". Set it by hand with /tmap pricepaid <coins|clear> on the held item.")
                            .tags("flip", "bin", "bought", "paid");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active() || lines.isEmpty()) return;
            Long v = lookup(stack);
            if (v != null) lines.add(Component.literal("§7Price Paid: §6" + EstimatedValue.exact(v) + " coins"));
        });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("pricepaid")
                        .then(ClientCommands.argument("coins", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> {
                                    String arg = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "coins");
                                    c.getSource().sendFeedback(Component.literal(setHeld(arg)));
                                    return 1;
                                })))));
    }

    private static Long lookup(ItemStack stack) {
        if (stack != lastStack) {
            lastStack = stack;
            String uuid = ItemUtil.itemUuid(stack);
            lastValue = uuid == null ? null : data().get(uuid);
        }
        return lastValue;
    }

    private static String setHeld(String arg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "§cNo player";
        String uuid = ItemUtil.itemUuid(mc.player.getMainHandItem());
        if (uuid == null) return "§cHold an item with a uuid";
        if (arg.equalsIgnoreCase("clear")) {
            data().remove(uuid);
            save();
            lastStack = null;
            return "§aPrice paid cleared";
        }
        Long coins = AuctionPriceInput.parseCompact(arg);
        if (coins == null || coins < 0) return "§cNot a number: " + arg;
        put(uuid, coins);
        return "§aPrice paid set to §6" + EstimatedValue.exact(coins) + " coins";
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("BIN Auction View")) return;
        if (cs.getMenu().slots.size() < 14) return;
        ItemStack item = cs.getMenu().slots.get(13).getItem();
        String uuid = ItemUtil.itemUuid(item);
        if (uuid == null) return;
        long price = 0;
        for (String l : ItemUtil.loreLines(item)) {
            Matcher m = PRICE.matcher(l.trim());
            if (m.matches()) {
                try { price = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
                break;
            }
        }
        if (price <= 0) return;
        pendingUuid = uuid;
        pendingPrice = price;
        pendingName = StringUtil.stripTrim(item.getHoverName().getString());
        pendingAt = System.currentTimeMillis();
    }

    private static void onChat(String raw) {
        Matcher m = PURCHASED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches() || pendingUuid == null) return;
        if (System.currentTimeMillis() - pendingAt > 60_000) return;
        String name = m.group(1).trim();
        if (!pendingName.isEmpty() && !pendingName.contains(name) && !name.contains(pendingName)) return;
        long coins = pendingPrice;
        try { coins = Long.parseLong(m.group(2).replace(",", "")); } catch (NumberFormatException ignored) {}
        put(pendingUuid, coins);
        pendingUuid = null;
    }

    private static void put(String uuid, long coins) {
        Map<String, Long> d = data();
        d.remove(uuid);
        d.put(uuid, coins);
        while (d.size() > KEEP) d.remove(d.keySet().iterator().next());
        save();
        lastStack = null;
    }

    private static Map<String, Long> data() {
        if (paid != null) return paid;
        paid = new LinkedHashMap<>();
        try {
            if (Files.exists(PATH)) {
                Map<String, Long> read = new Gson().fromJson(Files.readString(PATH), TYPE);
                if (read != null) paid.putAll(read);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("price_paid.json: {}", e.toString());
        }
        return paid;
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, new Gson().toJson(paid, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("price_paid.json: {}", e.toString());
        }
    }
}
