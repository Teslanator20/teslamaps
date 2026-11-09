package com.teslamaps.features.tracker;

// Concept from Skysoft's features/profit/CustomProfitTrackerSharing.kt + TrackerItemsEditor.kt
import com.google.gson.Gson;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.CustomTrackerDef;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.TextFieldsScreen;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Up to three user-defined item/coin trackers, shareable as a TMCT1: code. */
public final class CustomTrackers {

    private static final int SLOTS = 3;
    private static final String PREFIX = "TMCT1:";
    private static final String[] SLOT_NAMES = {"Tracker 1", "Tracker 2", "Tracker 3"};
    private static final Pattern SACK_ADD = Pattern.compile("\\+([\\d,]+) (.+?) \\(");
    private static final Pattern MOVED = Pattern.compile("^Moved ([\\d,]+) (.+) from your Sacks to your inventory\\.$");
    private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,]+(?:\\.\\d+)?)");
    private static final List<String> SAMPLE = List.of("§e§lCustom Tracker §7(Session)", "§f128x §aEnchanted Bone §7- §6192k",
            "§eProfit: §6192k", "§eper hour: §61.1M", "§eTime: §f10m 12s");
    private static final Gson GSON = new Gson();

    private static final Tracker[] trackers = new Tracker[SLOTS];
    private static final boolean[] active = new boolean[SLOTS];
    @SuppressWarnings("unchecked")
    private static final Set<String>[] ids = new Set[SLOTS];
    private static final String[] parsedFrom = new String[SLOTS];
    private static final Map<String, Long> suppress = new HashMap<>();
    private static Map<String, Integer> inventory;
    private static long purse = -1;
    private static Object level;
    private static int selected, timer;
    private static String lastHover;
    private static long lastHoverMs;
    private static HudEditElement hud;

    private CustomTrackers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static CustomTrackerDef def(int i) {
        List<CustomTrackerDef> l = cfg().customTrackerDefs;
        while (l.size() < SLOTS) {
            CustomTrackerDef d = new CustomTrackerDef();
            d.enabled = false;
            d.name = "Custom Tracker " + (l.size() + 1);
            l.add(d);
        }
        return l.get(i);
    }

    public static void register() {
        for (int i = 0; i < SLOTS; i++) {
            int slot = i;
            trackers[i] = Tracker.create("custom_" + (i + 1), "§e§lCustom Tracker")
                    .priceSource(() -> def(slot).priceSource)
                    .idleSeconds(() -> def(slot).pauseSeconds);
        }
        hud = HudEditElement.of("customTracker", "Custom Trackers")
                .enabled(() -> cfg().customTrackers)
                .size(() -> size()[0], () -> size()[1])
                .preview((g, mc, x, y, s) -> {
                    if (anyActive()) draw(g, mc, x, y, s);
                    else TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE));
                });
        Feature.of("CustomTrackers").when(() -> cfg().customTrackers)
                .tick(CustomTrackers::tick)
                .serverChat(CustomTrackers::onChat)
                .hudHiddenInInventory("customtracker", CustomTrackers::render)
                .config("Trackers", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Trackers");
                    b.toggle("Custom Profit Trackers", () -> c.customTrackers, v -> c.customTrackers = v)
                            .desc("Your own trackers: pick items (ids or names, comma separated), islands and coins. Position in /tmap gui.");
                    b.dropdown("  Edit", SLOT_NAMES, () -> SLOT_NAMES[selected], v -> {
                        for (int i = 0; i < SLOTS; i++) if (SLOT_NAMES[i].equals(v)) selected = i;
                    });
                    b.toggle("    Enabled", () -> def(selected).enabled, v -> def(selected).enabled = v);
                    b.button("    Name / Islands / Items", () -> Minecraft.getInstance().gui.setScreen(new TextFieldsScreen(
                            Minecraft.getInstance().gui.screen(), SLOT_NAMES[selected],
                            "Islands empty = everywhere. Items: ENCHANTED_BONE, Enchanted Rotten Flesh, ...",
                            List.of(new TextFieldsScreen.Field("Name", () -> def(selected).name, v -> def(selected).name = v),
                                    new TextFieldsScreen.Field("Islands", () -> def(selected).areas, v -> def(selected).areas = v),
                                    new TextFieldsScreen.Field("Items", () -> def(selected).items, v -> def(selected).items = v)),
                            4000)));
                    b.toggle("    Track Coins", () -> def(selected).coins, v -> def(selected).coins = v)
                            .desc("Purse gains while no menu is open");
                    b.priceSource("    Price Source", () -> def(selected).priceSource, v -> def(selected).priceSource = v);
                    b.slider("    Pause After (s)", 15, 900, () -> (float) def(selected).pauseSeconds,
                            v -> def(selected).pauseSeconds = Math.round(v), 0);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"}, new Runnable[]{
                            () -> trackers[selected].resetSession(), () -> trackers[selected].resetTotal(),
                            () -> trackers[selected].toggleView()});
                    b.buttonRow(new String[]{"Copy Share Code", "Import From Clipboard"}, new Runnable[]{
                            CustomTrackers::copyCode, CustomTrackers::importCode});
                });
    }

    private static void msg(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[TeslaMaps] §7" + s));
    }

    private static void copyCode() {
        String json = GSON.toJson(def(selected));
        String code = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        msg("Share code for §f" + def(selected).name + " §7copied.");
    }

    private static void importCode() {
        String code = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
        CustomTrackerDef d = null;
        if (code.startsWith(PREFIX) && code.length() < 20_000) {
            try {
                String json = new String(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())), StandardCharsets.UTF_8);
                d = GSON.fromJson(json, CustomTrackerDef.class);
            } catch (Exception ignored) {}
        }
        if (d == null) {
            msg("§cNo valid tracker code in the clipboard.");
            return;
        }
        d.name = clip(d.name, 100, "Custom Tracker");
        d.areas = clip(d.areas, 4000, "");
        d.items = clip(d.items, 4000, "");
        d.pauseSeconds = Math.clamp(d.pauseSeconds, 15, 900);
        if (d.priceSource < 0 || d.priceSource > 3) d.priceSource = 1;
        d.enabled = true;
        def(selected);
        cfg().customTrackerDefs.set(selected, d);
        TeslaMapsConfig.save();
        msg("Imported §f" + StringUtil.strip(d.name) + " §7into " + SLOT_NAMES[selected] + ". Reopen the settings to see it.");
    }

    private static String clip(String s, int max, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Set<String> parse(int i) {
        String items = def(i).items == null ? "" : def(i).items;
        if (items.equals(parsedFrom[i]) && ids[i] != null) return ids[i];
        parsedFrom[i] = items;
        Set<String> out = new HashSet<>();
        for (String t : items.split(",")) {
            t = t.trim();
            if (t.isEmpty()) continue;
            String byName = SkyblockItems.idForName(t);
            out.add(byName != null ? byName : t.toUpperCase(Locale.ROOT).replace(' ', '_'));
        }
        return ids[i] = out;
    }

    private static boolean inArea(CustomTrackerDef d) {
        if (d.areas == null || d.areas.isBlank()) return true;
        String area = SkyblockUtils.getArea();
        for (String a : d.areas.split(",")) {
            a = a.trim();
            if (!a.isEmpty() && area.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    private static boolean anyActive() {
        for (boolean a : active) if (a) return true;
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            inventory = null;
            purse = -1;
            suppress.clear();
        }
        timer++;
        if (timer % 20 == 1) {
            boolean sb = SkyblockUtils.isInSkyblock();
            for (int i = 0; i < SLOTS; i++) {
                CustomTrackerDef d = def(i);
                active[i] = sb && d.enabled && inArea(d) && (d.coins || !parse(i).isEmpty());
                trackers[i].title("§e§l" + d.name);
            }
        }
        if (!anyActive()) return;
        if (timer % 5 == 0) countInventory(mc);
        if (timer % 10 == 0) countPurse(mc);
        for (int i = 0; i < SLOTS; i++) if (active[i]) trackers[i].tick();
    }

    private static void add(String id, long amount) {
        for (int i = 0; i < SLOTS; i++) if (active[i] && parse(i).contains(id)) trackers[i].add(id, amount);
    }

    private static void countInventory(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        Map<String, Integer> now = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = ItemUtil.skyblockId(s);
            if (!id.isEmpty()) now.merge(id, s.getCount(), Integer::sum);
        }
        Map<String, Integer> before = inventory;
        inventory = now;
        if (before == null || mc.gui.screen() != null) return;
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            long gained = e.getValue() - before.getOrDefault(e.getKey(), 0);
            if (gained <= 0) continue;
            Long sup = suppress.get(e.getKey());
            if (sup != null) {
                long used = Math.min(sup, gained);
                gained -= used;
                if (sup - used <= 0) suppress.remove(e.getKey());
                else suppress.put(e.getKey(), sup - used);
            }
            if (gained > 0) add(e.getKey(), gained);
        }
    }

    private static void countPurse(Minecraft mc) {
        boolean coins = false;
        for (int i = 0; i < SLOTS; i++) coins |= active[i] && def(i).coins;
        if (!coins) return;
        long value = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = PURSE.matcher(ScoreboardUtils.cleanLine(raw));
            if (m.find()) {
                try {
                    value = (long) Double.parseDouble(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        if (value < 0) return;
        long gain = purse < 0 ? 0 : value - purse;
        purse = value;
        if (gain <= 0 || mc.gui.screen() != null) return;
        for (int i = 0; i < SLOTS; i++) if (active[i] && def(i).coins) trackers[i].addCoins(gain);
    }

    private static void onChat(String raw) {
        if (!anyActive()) return;
        Matcher m = MOVED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String id = SkyblockItems.idForName(m.group(2));
        if (id == null) return;
        try {
            suppress.merge(id, Long.parseLong(m.group(1).replace(",", "")), Long::sum);
        } catch (NumberFormatException ignored) {}
    }

    /** From ChatMixin, server lines only: the "Added" hover of a [Sacks] message. */
    public static void onChatComponent(Component message) {
        if (!cfg().customTrackers || !anyActive()) return;
        String plain = message.getString();
        if (!plain.contains("[Sacks]")) return;
        StringBuilder added = new StringBuilder();
        collect(message, added);
        String text = added.toString();
        if (text.isBlank()) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastHover) && now - lastHoverMs < 500) return;
        lastHover = text;
        lastHoverMs = now;
        Matcher m = SACK_ADD.matcher(text);
        while (m.find()) {
            String id = SkyblockItems.idForName(m.group(2));
            if (id == null) continue;
            try {
                add(id, Long.parseLong(m.group(1).replace(",", "")));
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void collect(Component c, StringBuilder added) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText show) {
            String h = StringUtil.strip(show.value().getString());
            if (h.trim().startsWith("Added")) added.append(h).append('\n');
        }
        for (Component s : c.getSiblings()) collect(s, added);
    }

    private static int[] size() {
        if (!anyActive()) return new int[] { TextHud.width(SAMPLE), TextHud.height(SAMPLE.size()) };
        var font = Minecraft.getInstance().font;
        int pad = HudStyle.padding(), w = 0, h = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!active[i]) continue;
            int[] s = trackers[i].size(font);
            w = Math.max(w, s[0] + pad * 2);
            h += s[1] + pad * 2 + 4;
        }
        return new int[] { w, h };
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        int pad = HudStyle.padding();
        float dy = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!active[i] || trackers[i].lines().isEmpty()) continue;
            trackers[i].draw(g, mc, x + (int) (pad * scale), y + (int) (pad * scale + dy), scale);
            dy += (trackers[i].size(mc.font)[1] + pad * 2 + 4) * scale;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || !anyActive()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale());
    }
}
