package com.teslamaps.features;

// Ported from SkyHanni's data/NotificationManager
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.KeyNames;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayDeque;
import java.util.List;

public final class Notifications {

    private record Note(List<String> lines, long lengthMs) {}

    private static final String CLOSE = "§c[X] Close";
    private static final int MAX_QUEUE = 20;

    private static final ArrayDeque<Note> queue = new ArrayDeque<>();
    private static Note current;
    private static long endAt;
    private static int width;
    private static long closedAt;
    private static boolean keyDown;
    private static String closeText = CLOSE;
    private static int lastKey = -2;

    private Notifications() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("Notifications").when(() -> cfg().notificationPopups)
                .tickAlways(Notifications::tick)
                .hudForeground("notifications", Notifications::render)
                .config("QoL & Misc", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Notification Popups");
                    b.toggle("Notification Popups", () -> c.notificationPopups, v -> c.notificationPopups = v)
                            .desc("Important warnings (No Bits, effects running out) also show as a popup that stays until closed or timed out. /tmap notify <text> to test");
                    b.toggle("  Show Over Inventory", () -> c.notificationOverInventory, v -> c.notificationOverInventory = v);
                    b.keybind("  Close Key", () -> c.notificationCloseKey, v -> c.notificationCloseKey = v);
                });
    }

    public static boolean enabled() {
        return cfg().notificationPopups;
    }

    /** Queues a popup; {@code \n} splits lines, seconds <= 0 = until closed. No-op while off. */
    public static void show(String text, int seconds) {
        if (!enabled() || text == null || text.isBlank()) return;
        Minecraft.getInstance().execute(() -> {
            if (queue.size() >= MAX_QUEUE) queue.pollFirst();
            queue.addLast(new Note(List.of(text.split("\n")), seconds <= 0 ? 0 : seconds * 1000L));
        });
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (current != null && current.lengthMs > 0 && now >= endAt) current = null;
        if (current == null && !queue.isEmpty()) {
            current = queue.pollFirst();
            endAt = current.lengthMs > 0 ? now + current.lengthMs : Long.MAX_VALUE;
            Minecraft mc = Minecraft.getInstance();
            int w = 0;
            for (String l : current.lines) w = Math.max(w, mc.font.width("§7" + l));
            width = Math.max(w, mc.font.width(CLOSE) + 40) + 8;
        }
        int key = cfg().notificationCloseKey;
        if (key != lastKey) {
            lastKey = key;
            closeText = key == -1 ? "" : "§c[" + KeyNames.display(key) + "] Close";
        }
        Minecraft mc = Minecraft.getInstance();
        boolean canPress = mc.gui.screen() == null || mc.gui.screen() instanceof AbstractContainerScreen<?>;
        boolean down = canPress && key != -1 && KeyNames.isDown(key);
        if (down && !keyDown && current != null && now - closedAt > 200) {
            current = null;
            closedAt = now;
        }
        keyDown = down;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Note n = current;
        if (n == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() != null && !cfg().notificationOverInventory) return;
        int h = n.lines.size() * 10 + 18;
        int mid = mc.getWindow().getGuiScaledWidth() / 2;
        int top = (int) (mc.getWindow().getGuiScaledHeight() * 0.75 - h / 2.0);
        int left = mid - width / 2;
        HudStyle.panel(g, left, top, width, h);
        if (!closeText.isEmpty()) g.text(mc.font, closeText, left + width - 3 - mc.font.width(closeText), top + 4, 0xFFFFFFFF, true);
        if (n.lengthMs > 0) {
            long s = Math.max(0, (endAt - System.currentTimeMillis() + 999) / 1000);
            g.text(mc.font, "§8" + (s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s"), left + 4, top + 4, 0xFFFFFFFF, true);
        }
        for (int i = 0; i < n.lines.size(); i++) {
            String l = "§7" + n.lines.get(i);
            g.text(mc.font, l, mid - mc.font.width(l) / 2, top + 19 + i * 10, 0xFFFFFFFF, true);
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("notify")
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (!enabled()) {
                                ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
                                        "§b[TeslaMaps] §7Notification Popups are off (Interface settings)"));
                                return 1;
                            }
                            show(StringArgumentType.getString(ctx, "text").replace("\\n", "\n"), 0);
                            return 1;
                        }));
    }
}
