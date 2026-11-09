package com.teslamaps.features.hud;

// Ported from SkyHanni's features/itemabilities/CrownOfAvariceCounter.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrownOfAvarice {

    private static final long MAX = 1_000_000_000L;
    private static final List<String> SAMPLE = List.of("§6Crown of Avarice: §e482.3M", "§aCoins Per Hour: §63.2M",
            "§aTime until Max: §b161h 47m", "§aLast coins gained: §6+12,400", "§aCoins this session: §64,812,000",
            "§aSession Time: §b1h 30m");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private static String uuid = "";
    private static long total = -1, earned, lastGain;
    private static long lastActivity, activeMillis;
    private static Object level;

    private CrownOfAvarice() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("crownOfAvarice", "Crown of Avarice")
                .enabled(() -> cfg().crownOfAvarice)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("CrownOfAvarice").when(() -> cfg().crownOfAvarice)
                .tick(CrownOfAvarice::tick)
                .hudForeground("crownofavarice", CrownOfAvarice::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Crown of Avarice");
                    b.toggle("Crown of Avarice Counter", () -> c.crownOfAvarice, v -> c.crownOfAvarice = v)
                            .desc("While wearing the Crown: coins in it, coins per hour, time to 1B and the session. Position in /tmap gui.");
                    b.toggle("  Short Numbers", () -> c.crownOfAvariceShort, v -> c.crownOfAvariceShort = v);
                    b.toggle("  Reset On World Change", () -> c.crownOfAvariceResetOnWorld, v -> c.crownOfAvariceResetOnWorld = v);
                    b.slider("  AFK Pause (s)", 30, 600, () -> (float) c.crownOfAvariceAfk, v -> c.crownOfAvariceAfk = Math.round(v), 0);
                    b.button("  Reset Session", CrownOfAvarice::reset);
                });
    }

    private static void reset() {
        earned = lastGain = 0;
        lastActivity = activeMillis = 0;
    }

    private static long active() {
        if (lastActivity == 0) return activeMillis;
        long open = System.currentTimeMillis() - lastActivity;
        return activeMillis + (open <= afkMs() ? open : 0);
    }

    private static long afkMs() {
        return Math.max(30, cfg().crownOfAvariceAfk) * 1000L;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            level = mc.level;
            if (cfg().crownOfAvariceResetOnWorld) reset();
            total = -1;
        }
        if (++timer % 5 != 0) return;
        ItemStack helm = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!"CROWN_OF_AVARICE".equals(ItemUtil.skyblockId(helm))) {
            lines = List.of();
            return;
        }
        CustomData data = helm.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        var tag = data.copyTag();
        long coins = tag.getLong("collected_coins").orElse(0L);
        String id = tag.getString("uuid").orElse("");
        if (!id.equals(uuid)) {
            uuid = id;
            total = coins;
        } else if (total < 0) {
            total = coins;
        } else if (coins != total) {
            long diff = coins - total;
            total = coins;
            if (diff < 0) reset();
            else {
                long now = System.currentTimeMillis();
                if (lastActivity != 0 && now - lastActivity <= afkMs()) activeMillis += now - lastActivity;
                lastActivity = now;
                earned += diff;
                lastGain = diff;
            }
        }
        if (timer % 20 == 0 || lines.isEmpty()) build();
    }

    private static void build() {
        boolean paused = lastActivity == 0 || System.currentTimeMillis() - lastActivity > afkMs();
        String p = paused && earned > 0 ? " §c(PAUSED)" : "";
        long ms = active();
        double perHour = ms > 0 ? earned * 3_600_000.0 / ms : 0;
        boolean calc = ms < 60_000;
        List<String> out = new ArrayList<>(6);
        out.add("§6Crown of Avarice: §e" + num(total));
        out.add("§aCoins Per Hour: §6" + (calc ? "Calculating..." : num((long) perHour)) + p);
        String until = perHour <= 0 ? "Forever..." : time((long) ((MAX - total) / perHour * 3_600_000));
        out.add("§aTime until Max: §b" + (total >= MAX ? "§aMaxed" : calc ? "Calculating..." : until) + p);
        out.add("§aLast coins gained: §6" + (lastGain == 0 ? "§cnever" : "+" + String.format(Locale.ROOT, "%,d", lastGain)));
        out.add("§aCoins this session: §6" + String.format(Locale.ROOT, "%,d", earned));
        out.add("§aSession Time: §b" + time(ms));
        lines = out;
        width = TextHud.width(out);
    }

    private static String num(long n) {
        if (n < 0) n = 0;
        return cfg().crownOfAvariceShort ? ItemPrice.format(n) : String.format(Locale.ROOT, "%,d", n);
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? s / 3600 + "h " + s / 60 % 60 + "m" : s / 60 + "m " + s % 60 + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty()) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof InventoryScreen)) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
