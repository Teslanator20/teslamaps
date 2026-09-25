package com.teslamaps.features.mining;

// Ported from SkyOcean's features/mining/ForgeReminder.kt (slot reading after SkyBlockAPI's ForgeAPI)
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat reminder with a warp / call link once forge items are done, repeated every few minutes. */
public final class ForgeReminder {

    public static final String[] ACTIONS = {"Warp", "Call Fred", "Both"};

    public static final class ForgeSlot {
        public String name;
        public long expiry;
        ForgeSlot() {}
        ForgeSlot(String name, long expiry) { this.name = name; this.expiry = expiry; }
    }

    private static final Pattern REMAINING = Pattern.compile("^Time Remaining: (.*)$");
    private static final Pattern UNIT = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<Map<Integer, ForgeSlot>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("forge.json", null);

    private static Map<Integer, ForgeSlot> slots = new HashMap<>();
    private static boolean loaded;
    private static Screen lastScreen;
    private static long openedAt, lastSent;
    private static int ticks;

    private ForgeReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ForgeReminder").section("Mining").when(() -> cfg().forgeReminder)
                .tick(ForgeReminder::tick)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Forge Reminder");
                    b.toggle("Forge Reminder", () -> c.forgeReminder, v -> c.forgeReminder = v)
                            .desc("Chat line \"2x Refined Mithril, ...\" once forge items are done, with a clickable warp / Fred link. Open The Forge once so the slots are known.")
                            .tags("forge", "fred", "reminder", "refined", "claim");
                    b.slider("  Repeat Every (min)", 1, 30, () -> (float) c.forgeReminderDelay,
                            v -> c.forgeReminderDelay = Math.round(v), 0);
                    b.dropdown("  Link", ACTIONS, () -> c.forgeReminderAction, v -> c.forgeReminderAction = v);
                    b.toggle("  Not While Busy", () -> c.forgeReminderBusy, v -> c.forgeReminderBusy = v)
                            .desc("No reminder in dungeons, Kuudra, Mineshafts, the Rift, the Dark Auction or the Safari");
                });
        SbProfile.onChange(() -> {
            loaded = false;
            lastSent = 0;
        });
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        slots = new HashMap<>();
        Path p = FILE.path();
        if (p == null || !Files.exists(p)) return;
        try {
            Map<Integer, ForgeSlot> d = GSON.fromJson(Files.readString(p), TYPE);
            if (d != null) {
                d.values().removeIf(java.util.Objects::isNull);
                slots = d;
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load forge.json", e);
        }
    }

    private static void save() {
        Path p = FILE.path();
        if (p == null) return;
        String json = GSON.toJson(slots, TYPE);
        net.minecraft.util.Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(p.getParent());
                Files.writeString(p, json);
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to save forge.json", e);
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).startsWith("The Forge")) {
            if (cs != lastScreen) {
                lastScreen = cs;
                openedAt = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - openedAt > 600 && ticks % 10 == 0) read(cs);
        } else {
            lastScreen = null;
        }
        if (++ticks % 100 != 0) return;
        if (!SkyblockUtils.inSkyblockCached()) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < cfg().forgeReminderDelay * 60_000L) return;
        if (cfg().forgeReminderBusy && busy()) return;
        ensure();
        Map<String, Integer> done = new LinkedHashMap<>();
        for (ForgeSlot s : slots.values()) if (s.expiry <= now) done.merge(s.name, 1, Integer::sum);
        if (done.isEmpty()) return;
        lastSent = now;
        send(mc, done);
    }

    private static void read(AbstractContainerScreen<?> cs) {
        Map<Integer, ForgeSlot> found = new HashMap<>();
        boolean any = false;
        long now = System.currentTimeMillis();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            any = true;
            if (ItemUtil.skyblockId(st) == null) continue;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = REMAINING.matcher(l.trim());
                if (!m.matches()) continue;
                found.put(slot.index, new ForgeSlot(StringUtil.stripTrim(st.getHoverName().getString()), now + duration(m.group(1))));
                break;
            }
        }
        if (!any) return;
        ensure();
        boolean changed = found.size() != slots.size();
        if (!changed) {
            for (Map.Entry<Integer, ForgeSlot> e : found.entrySet()) {
                ForgeSlot old = slots.get(e.getKey());
                if (old == null || !old.name.equals(e.getValue().name) || Math.abs(old.expiry - e.getValue().expiry) > 90_000) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) return;
        slots = found;
        save();
    }

    private static long duration(String text) {
        Matcher m = UNIT.matcher(text.toLowerCase(Locale.ROOT));
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    private static boolean busy() {
        if (DungeonManager.isInDungeon()) return true;
        String area = SkyblockUtils.getArea().toLowerCase(Locale.ROOT);
        return area.contains("kuudra") || area.contains("mineshaft") || area.contains("rift")
                || area.contains("dark auction") || area.contains("safari");
    }

    private static void send(Minecraft mc, Map<String, Integer> done) {
        if (mc.player == null) return;
        StringBuilder items = new StringBuilder();
        for (Map.Entry<String, Integer> e : done.entrySet()) {
            if (!items.isEmpty()) items.append("§7, ");
            items.append("§7").append(e.getValue()).append("x §f").append(e.getKey());
        }
        MutableComponent line = Component.literal("§6[TeslaMaps] §eForge Reminder §8» " + items);
        String action = cfg().forgeReminderAction;
        if (!"Call Fred".equals(action)) line.append(link("Click to warp", "/warp forge", "Warp to the Forge"));
        if (!"Warp".equals(action)) line.append(link("Click to call Fred", "/call fred", "Call Fred!"));
        mc.player.sendSystemMessage(line);
    }

    private static Component link(String text, String command, String hover) {
        return Component.literal(" §e[§6§n" + text + "§e]").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
}
