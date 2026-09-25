package com.teslamaps.features;

// After Zen's TradeAPI / TradeHistoryHUD: every finished trade, saved with items, value and partner
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TradeHistory {

    public static final class Item {
        public String name;
        public int count;
        public double value;
        public boolean coins;
    }

    public static final class Trade {
        public long time;
        public String partner;
        public List<Item> gave = new ArrayList<>();
        public List<Item> received = new ArrayList<>();

        public double gaveValue() { return sum(gave); }
        public double receivedValue() { return sum(received); }
        public double profit() { return receivedValue() - gaveValue(); }

        private static double sum(List<Item> l) {
            double v = 0;
            for (Item i : l) v += i.value;
            return v;
        }
    }

    private static final Pattern COMPLETED = Pattern.compile("^Trade completed with (?:\\[[^]]+] )?(\\w+)!$");
    private static final Pattern CANCELLED = Pattern.compile("cancelled the trade!$");
    private static final int MAX = 2000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Feature feature;
    private static List<Trade> trades;
    private static Trade pending;
    private static long pendingAt;
    private static int ticks;

    private TradeHistory() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TradeHistory")
                .when(() -> cfg().tradeHistory)
                .tick(TradeHistory::tick)
                .serverChat(TradeHistory::onChat)
                .config("Tooltips & Value", b -> {
                    b.label("Trade History");
                    b.toggle("Trade History", () -> cfg().tradeHistory, v -> cfg().tradeHistory = v)
                            .desc("Saves every finished trade (items, coins, partner, value). /tmap tradehistory opens the list.")
                            .tags("trade", "history", "log", "scam");
                    b.button("  Open Trade History", () -> Minecraft.getInstance().setScreen(
                            new com.teslamaps.screen.TradeHistoryScreen(Minecraft.getInstance().screen)));
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("tradehistory").executes(c -> {
                    Minecraft.getInstance().schedule(() -> Minecraft.getInstance().setScreen(
                            new com.teslamaps.screen.TradeHistoryScreen(null)));
                    return 1;
                }))));
    }

    private static int ours(int i) { return (i / 4) * 9 + i % 4; }
    private static int theirs(int i) { return (i / 4) * 9 + 5 + i % 4; }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.startsWith("You    ") || cs.getMenu().slots.size() < 45) return;
        if (ticks++ % 5 != 0) return;
        PriceManager.ensureFresh();
        List<Slot> slots = cs.getMenu().slots;
        Trade t = new Trade();
        t.partner = title.substring(3).trim();
        for (int i = 0; i < 16; i++) {
            add(t.gave, slots.get(ours(i)).getItem());
            add(t.received, slots.get(theirs(i)).getItem());
        }
        pending = t;
        pendingAt = System.currentTimeMillis();
    }

    private static void add(List<Item> out, ItemStack stack) {
        if (stack.isEmpty()) return;
        Item it = new Item();
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        it.count = stack.getCount();
        if (name.endsWith(" coins")) {
            it.coins = true;
            it.name = name;
            it.value = NumberUtil.parseSuffixed(name.substring(0, name.length() - 6).trim());
        } else {
            it.name = name;
            it.value = EstimatedValue.compute(stack) * stack.getCount();
        }
        out.add(it);
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (CANCELLED.matcher(text).find()) {
            pending = null;
            return;
        }
        Matcher m = COMPLETED.matcher(text);
        if (!m.matches() || pending == null || System.currentTimeMillis() - pendingAt > 15_000) return;
        Trade t = pending;
        pending = null;
        t.time = System.currentTimeMillis();
        if (t.partner == null || t.partner.isEmpty()) t.partner = m.group(1);
        List<Trade> all = all();
        all.addFirst(t);
        while (all.size() > MAX) all.removeLast();
        save();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("trade_history.json");
    }

    /** Newest first. */
    public static List<Trade> all() {
        if (trades != null) return trades;
        List<Trade> list = new ArrayList<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                List<Trade> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<List<Trade>>() {}.getType());
                if (read != null) for (Trade t : read) if (t != null) list.add(t);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read trade history: {}", e.toString());
        }
        for (Trade t : list) {
            if (t.gave == null) t.gave = new ArrayList<>();
            if (t.received == null) t.received = new ArrayList<>();
            if (t.partner == null) t.partner = "?";
        }
        trades = list;
        return list;
    }

    public static void clear() {
        all().clear();
        save();
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(trades), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to save trade history: {}", e.toString());
        }
    }
}
