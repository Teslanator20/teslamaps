package com.teslamaps.features;

// Ported from Bax's DamageTakenTrigger.java / AutomationEngine.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DamageAlert {

    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
    private static final Pattern WARP = Pattern.compile("^(?:Sending to server |Evacuating to |Warping\\b|Sending you to )", Pattern.CASE_INSENSITIVE);
    private static final long FLASH_MS = 400;

    private static String lastRaw = null;
    private static int lastHealth = -1;
    private static long suppressUntil = 0;
    private static long flashAt = 0;
    private static Object lastLevel = null;
    private static String lastArea = "";

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DamageAlert").section("Combat").when(() -> cfg().damageAlert)
                .tick(DamageAlert::tick)
                .serverChat(DamageAlert::onChat)
                .hud("damagealertflash", DamageAlert::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Damage Taken Alert");
                    b.toggle("Damage Taken Alert", () -> c.damageAlert, v -> c.damageAlert = v)
                            .desc("Alerts when your SkyBlock health (action bar) drops. Quiet for a few seconds after warps and island swaps.");
                    b.slider("  Min Damage (0 = any)", 0f, 5000f,
                            () -> (float) c.damageAlertMin, v -> c.damageAlertMin = Math.round(v / 10f) * 10, 0);
                    b.toggle("  Sound", () -> c.damageAlertSound, v -> c.damageAlertSound = v);
                    b.soundDropdown("    Sound Type", SoundOptions.keys(), () -> c.damageAlertSoundType, v -> c.damageAlertSoundType = v);
                    b.toggle("  Screen Flash", () -> c.damageAlertFlash, v -> c.damageAlertFlash = v);
                    b.toggle("  Title", () -> c.damageAlertTitle, v -> c.damageAlertTitle = v);
                    b.toggle("  Chat Message", () -> c.damageAlertChat, v -> c.damageAlertChat = v);
                });
    }

    private static void onChat(String raw) {
        if (WARP.matcher(StringUtil.strip(raw).trim()).find()) suppress(5000);
    }

    private static void suppress(long ms) {
        suppressUntil = Math.max(suppressUntil, System.currentTimeMillis() + ms);
        lastHealth = -1;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            suppress(2500);
        }
        String area = SkyblockUtils.getArea();
        if (!area.equals(lastArea)) {
            lastArea = area;
            suppress(2500);
        }
        String raw = ActionBarFilter.lastRaw();
        if (raw == lastRaw) return;
        lastRaw = raw;
        Matcher m = HEALTH.matcher(StringUtil.strip(raw));
        if (!m.find()) return;
        int cur;
        try { cur = Integer.parseInt(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return; }
        int prev = lastHealth;
        lastHealth = cur;
        if (prev <= 0 || cur <= 0 || cur >= prev || System.currentTimeMillis() < suppressUntil) return;
        int dmg = prev - cur;
        TeslaMapsConfig c = cfg();
        if (dmg < c.damageAlertMin) return;
        if (c.damageAlertSound) LoudSound.play(SoundOptions.resolve(c.damageAlertSoundType), 1f, 1f);
        if (c.damageAlertFlash) flashAt = System.currentTimeMillis();
        if (c.damageAlertTitle) {
            mc.gui.hud.setTimes(0, 15, 5);
            mc.gui.hud.setTitle(Component.literal(""));
            mc.gui.hud.setSubtitle(Component.literal("§c-" + dmg + " ❤"));
        }
        if (c.damageAlertChat && mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[Damage] §f-" + dmg + " ❤ §7(" + cur + " left)"));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (flashAt == 0) return;
        long dt = System.currentTimeMillis() - flashAt;
        if (dt >= FLASH_MS) { flashAt = 0; return; }
        int alpha = (int) (0x60 * (1 - dt / (double) FLASH_MS));
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (alpha << 24) | 0xFF0000);
    }
}
