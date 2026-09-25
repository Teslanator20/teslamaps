package com.teslamaps.features.mining;

// Ported from SkyOcean's features/mining/ForgeReminder.kt, slot data from ForgeSlotHud
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;

/** Chat reminder with a warp / call link once forge items are done, repeated every few minutes. */
public final class ForgeReminder {

    public static final String[] ACTIONS = {"Warp", "Call Fred", "Both"};

    private static long lastSent;
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
                            .desc("Chat line \"2x Refined Mithril, ...\" once forge items are done, with a clickable warp / Fred link. Uses the Forge Slot data (Forges tab widget or opening The Forge).")
                            .tags("forge", "fred", "reminder", "refined", "claim");
                    b.slider("  Repeat Every (min)", 1, 30, () -> (float) c.forgeReminderDelay,
                            v -> c.forgeReminderDelay = Math.round(v), 0);
                    b.dropdown("  Link", ACTIONS, () -> c.forgeReminderAction, v -> c.forgeReminderAction = v);
                    b.toggle("  Not While Busy", () -> c.forgeReminderBusy, v -> c.forgeReminderBusy = v)
                            .desc("No reminder in dungeons, Kuudra, Mineshafts, the Rift, the Dark Auction or the Safari");
                });
        SbProfile.onChange(() -> lastSent = 0);
    }

    private static void tick() {
        if (++ticks % 100 != 0) return;
        if (!SkyblockUtils.inSkyblockCached()) return;
        long now = System.currentTimeMillis();
        if (now - lastSent < cfg().forgeReminderDelay * 60_000L) return;
        if (cfg().forgeReminderBusy && busy()) return;
        Map<String, Integer> done = ForgeSlotHud.readyItems();
        if (done.isEmpty()) return;
        lastSent = now;
        send(Minecraft.getInstance(), done);
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
