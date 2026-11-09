package com.teslamaps.features;

// Ported from SkyHanni's SackApi
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Sack contents read when a sack menu is open, then kept live from the "[Sacks]" hover messages.
 * CORRECT = read from the menu, ALRIGHT = hidden "other items" changed since, OUTDATED = only seen in chat.
 */
public class SackApi {

    public static final int CORRECT = 0, ALRIGHT = 1, OUTDATED = 2;

    public static class Item {
        public long amount;
        public int status;
        public long updated;
        public String sack;

        Item() {}
        Item(long amount, int status) { this.amount = amount; this.status = status; this.updated = System.currentTimeMillis(); }
    }

    private static final Pattern SACK_TITLE = Pattern.compile("^(?:.* Sack|Enchanted .* Sack|Sack of Sacks)$");
    private static final Pattern CHANGE = Pattern.compile("([+-][\\d,]+) (.+?) \\((.+?)\\)");
    private static final Pattern STORED = Pattern.compile("Stored: ([\\d,.]+[kKmMbB]?)/");
    private static final Pattern GEM_NAME = Pattern.compile("^(?:\\S )?(?:(?:Rough|Flawed|Fine) )?(\\S+) Gemstones?$");
    private static final Pattern GEM_COUNT = Pattern.compile("^(Rough|Flawed|Fine): ([\\d,.]+[kKmM]?).*");

    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<String, Item>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("sacks.json", null);

    private static Map<String, Item> data = new HashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static Screen lastScreen;
    private static long openedMs;
    private static String lastHover;
    private static long lastHoverMs;
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean enabled() {
        return cfg().sackApi && cfg().section("Inventory");
    }

    public static void register() {
        Feature.of("SackApi").section("Inventory").when(() -> cfg().sackApi)
                .tick(SackApi::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Live Sack Contents", () -> c.sackApi, v -> c.sackApi = v)
                            .desc("Open each sack once, after that [Sacks] messages keep the amounts current. /tmap sacks lists them.");
                    b.info("Item search and Craft Helper use the live amounts");
                });
        com.teslamaps.utils.SbProfile.onChange(() -> {
            if (loaded && FILE.stale()) { save(); load(); }
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> save());
    }

    private static void ensure() {
        if (!loaded) load();
    }

    private static void load() {
        data = new HashMap<>();
        loaded = true;
        dirty = false;
        Path p = FILE.path();
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                Map<String, Item> d = GSON.fromJson(Files.readString(p), TYPE);
                if (d != null) {
                    d.values().removeIf(java.util.Objects::isNull);
                    data = d;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load sacks.json", e);
        }
    }

    private static void save() {
        if (!dirty) return;
        dirty = false;
        Path p = FILE.path();
        if (p == null) return;
        String json = GSON.toJson(data, TYPE);
        net.minecraft.util.Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, json);
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to save sacks.json", e);
            }
        });
    }

    private static void tick() {
        if (++ticks % 600 == 0) save();
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        if (mc.gui.screen() != lastScreen) {
            lastScreen = mc.gui.screen();
            openedMs = System.currentTimeMillis();
        }
        if (ticks % 10 != 0 || System.currentTimeMillis() - openedMs < 500) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!SACK_TITLE.matcher(title).matches()) return;
        ensure();
        currentSack = title.equals("Sack of Sacks") ? null : title;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            readSlot(st);
        }
    }

    private static void readSlot(ItemStack st) {
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        List<String> lore = ItemUtil.loreLines(st);
        Matcher gem = GEM_NAME.matcher(name);
        if (gem.matches()) {
            for (String l : lore) {
                Matcher m = GEM_COUNT.matcher(l.trim());
                if (m.matches()) set(m.group(1) + " " + gem.group(1) + " Gemstone", parse(m.group(2)));
            }
            return;
        }
        for (String l : lore) {
            Matcher m = STORED.matcher(l.trim());
            if (m.find()) {
                set(name, parse(m.group(1)));
                return;
            }
        }
    }

    private static void set(String name, long amount) {
        if (amount < 0) return;
        Item old = data.get(key(name));
        String sack = currentSack != null ? currentSack : old != null ? old.sack : null;
        if (old != null && old.amount == amount && old.status == CORRECT && java.util.Objects.equals(old.sack, sack)) return;
        Item it = new Item(amount, CORRECT);
        it.sack = sack;
        data.put(key(name), it);
        dirty = true;
        changes++;
    }

    private static String currentSack;

    /** Sack each item was last seen in, keyed by lower-case plain name. Empty while off. */
    public static Map<String, String> sackNames() {
        if (!enabled()) return Map.of();
        ensure();
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, Item> e : data.entrySet()) {
            if (e.getValue().sack != null) out.put(e.getKey(), e.getValue().sack);
        }
        return out;
    }

    /** Bumps whenever an amount changes. */
    public static int changes() { return changes; }

    private static int changes;

    private static long parse(String s) {
        try {
            String n = s.replace(",", "");
            char u = Character.toLowerCase(n.charAt(n.length() - 1));
            double mult = u == 'k' ? 1e3 : u == 'm' ? 1e6 : u == 'b' ? 1e9 : 1;
            if (mult != 1) n = n.substring(0, n.length() - 1);
            return Math.round(Double.parseDouble(n) * mult);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** From ChatMixin, server lines only. */
    public static void onChatComponent(Component message) {
        if (!enabled()) return;
        String plain = message.getString();
        if (!plain.contains("[Sacks]")) return;
        StringBuilder added = new StringBuilder(), removed = new StringBuilder();
        collect(message, added, removed);
        String text = added.toString() + "\n" + removed;
        if (text.isBlank()) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastHover) && now - lastHoverMs < 500) return;
        lastHover = text;
        lastHoverMs = now;

        ensure();
        Map<String, Long> changes = new LinkedHashMap<>();
        Map<String, String> sacks = new HashMap<>();
        for (String line : text.split("\n")) {
            Matcher m = CHANGE.matcher(StringUtil.stripTrim(line));
            while (m.find()) {
                try {
                    long d = Long.parseLong(m.group(1).replace(",", "").replace("+", ""));
                    changes.merge(key(m.group(2)), d, Long::sum);
                    String s = m.group(3);
                    int comma = s.indexOf(',');
                    sacks.putIfAbsent(key(m.group(2)), (comma > 0 ? s.substring(0, comma) : s).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        SackApi.changes++;
        for (Map.Entry<String, Long> e : changes.entrySet()) {
            Item it = data.get(e.getKey());
            if (it == null) {
                Item fresh = new Item(Math.max(0, e.getValue()), OUTDATED);
                fresh.sack = sacks.get(e.getKey());
                data.put(e.getKey(), fresh);
            } else {
                it.amount = Math.max(0, it.amount + e.getValue());
                it.updated = now;
            }
        }
        boolean other = added.indexOf("other items") >= 0 || removed.indexOf("other items") >= 0;
        if (other) {
            for (Map.Entry<String, Item> e : data.entrySet()) {
                if (!changes.containsKey(e.getKey()) && e.getValue().status == CORRECT) e.getValue().status = ALRIGHT;
            }
        }
        dirty = true;
    }

    private static void collect(Component c, StringBuilder added, StringBuilder removed) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            String t = h.trim();
            if (t.startsWith("Added")) added.append(h).append('\n');
            else if (t.startsWith("Removed")) removed.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, added, removed);
    }

    /** Stored amount by plain item name, -1 if unknown or the api is off. */
    public static long amount(String name) {
        if (!enabled() || name == null) return -1;
        ensure();
        Item it = data.get(key(name));
        return it == null ? -1 : it.amount;
    }

    /** Every known amount, keyed by lower-case plain name. Empty while off. */
    public static Map<String, Long> amounts() {
        if (!enabled()) return Map.of();
        ensure();
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Item> e : data.entrySet()) out.put(e.getKey(), e.getValue().amount);
        return out;
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("sacks")
                .executes(c -> list(c.getSource(), ""))
                .then(ClientCommands.argument("filter", StringArgumentType.greedyString())
                        .executes(c -> list(c.getSource(), StringArgumentType.getString(c, "filter"))));
    }

    private static int list(FabricClientCommandSource src, String filter) {
        if (!enabled()) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §7Live Sack Contents is off (Inventory settings)"));
            return 1;
        }
        ensure();
        String f = filter.toLowerCase(Locale.ROOT).trim();
        record Row(String name, Item item, double value) {}
        List<Row> rows = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Item> e : data.entrySet()) {
            if (e.getValue().amount <= 0) continue;
            String id = PriceManager.idForName(e.getKey());
            double price = id == null ? 0 : PriceManager.getPrice(id);
            double v = price * e.getValue().amount;
            total += v;
            if (!f.isEmpty() && !e.getKey().contains(f)) continue;
            rows.add(new Row(e.getKey(), e.getValue(), v));
        }
        rows.sort((a, b) -> Double.compare(b.value, a.value));
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Sacks: §f" + data.size() + " §7items, worth §6"
                + EstimatedValue.shortFormat(total)));
        int n = 0;
        for (Row r : rows) {
            if (++n > 15) break;
            String st = switch (r.item.status) {
                case CORRECT -> "§a●";
                case ALRIGHT -> "§e●";
                default -> "§c●";
            };
            src.sendFeedback(Component.literal(" " + st + " §f" + StringUtil.titleCase(r.name) + " §7x"
                    + String.format(Locale.ROOT, "%,d", r.item.amount)
                    + (r.value > 0 ? " §6" + EstimatedValue.shortFormat(r.value) : "")));
        }
        src.sendFeedback(Component.literal(" §a● §7from menu §e● §7probably right §c● §7open the sack to fix"));
        return 1;
    }
}
