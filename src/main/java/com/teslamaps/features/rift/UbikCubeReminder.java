package com.teslamaps.features.rift;

// Ported from SkyHanni's UbikReminder.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UbikCubeReminder {

    private static final long COOLDOWN = 2 * 3_600_000L;
    private static final Pattern WAIT = Pattern.compile("^SPLIT! You need to wait (.+) before you can play again\\.$");
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([hms])");
    private static final String SAMPLE = "§e1h 23m";

    private static HudEditElement hud;
    private static String text = "";
    private static int ticks;
    private static boolean wasOpen;

    private UbikCubeReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        hud = HudEditElement.of("ubikHud", "Ubik's Cube").section("Rift")
                .enabled(() -> cfg().ubikReminder && cfg().ubikHud)
                .size(() -> width(text.isEmpty() ? SAMPLE : text), () -> 20)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, text.isEmpty() ? SAMPLE : text));
        Feature.of("UbikCubeReminder").section("Rift").when(() -> cfg().ubikReminder)
                .tick(UbikCubeReminder::tick)
                .serverChat(UbikCubeReminder::onChat)
                .hudHiddenInInventory("ubikhud", UbikCubeReminder::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Reminders");
                    b.toggle("Ubik's Cube Reminder", () -> c.ubikReminder, v -> c.ubikReminder = v)
                            .desc("Remembers the 2h Split or Steal cooldown per profile and tells you in chat when Ubik's Cube is ready again.");
                    b.toggle("  Show HUD", () -> c.ubikHud, v -> c.ubikHud = v)
                            .desc("Cube icon with the time left, or 'Ready!' (/tmap gui).");
                    b.toggle("  HUD Only When Ready", () -> c.ubikHudOnlyReady, v -> c.ubikHudOnlyReady = v);
                });
    }

    private static String key() {
        String k = SbProfile.key();
        return k == null || k.isEmpty() ? "default" : k;
    }

    private static void setReadyAt(long t) {
        cfg().ubikReadyAt.put(key(), t);
        TeslaMapsConfig.save();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("SPLIT! You need to wait")) return;
        Matcher m = WAIT.matcher(t);
        if (!m.matches()) return;
        long ms = 0;
        Matcher p = PART.matcher(m.group(1));
        while (p.find()) {
            long n = Long.parseLong(p.group(1));
            ms += switch (p.group(2)) {
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        if (ms > 0) setReadyAt(System.currentTimeMillis() + ms);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen instanceof AbstractContainerScreen<?> s && s.getTitle().getString().contains("Split or Steal");
        if (open && !wasOpen) setReadyAt(System.currentTimeMillis() + COOLDOWN);
        wasOpen = open;
        if (++ticks % 20 != 0) return;
        Long at = cfg().ubikReadyAt.get(key());
        long now = System.currentTimeMillis();
        if (at != null && at > 0 && now >= at && SkyblockUtils.isInSkyblock()) {
            setReadyAt(0);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §aUbik's Cube is ready in the Rift!"));
            at = 0L;
        }
        if (at == null || at <= 0) {
            text = "§aReady!";
        } else {
            text = cfg().ubikHudOnlyReady ? "" : "§e" + fmt(at - now);
        }
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }

    private static int width(String s) {
        return 26 + Minecraft.getInstance().font.width(s);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String s) {
        int w = width(s);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, 20);
        ItemStack icon = SkyblockItems.stackNow("UBIKS_CUBE");
        if (!icon.isEmpty()) g.item(icon, 2, 2);
        g.text(mc.font, s, 22, 6, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().ubikHud || mc.options.hideGui || text.isEmpty() || hud == null) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), text);
    }
}
