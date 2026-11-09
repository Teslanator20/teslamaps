package com.teslamaps.features;

// Ported from SkyHanni's MantidKillDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MantidKillDisplay {

    private static final int MAX_BONUS = 20;
    private static final long EXPIRE_MS = 10 * 60_000L;
    private static final Pattern PEST_DROP = Pattern.compile("You received ([\\d,]+)x (.+) for killing an? (.+)!");
    private static final String[] SHOW = {"Always", "Mantid Armor", "Farming Tool", "Vacuum"};
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final ArrayDeque<Long> expires = new ArrayDeque<>();
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static boolean visible;
    private static boolean wearing;
    private static Object lastLevel;
    private static int ticks;

    private MantidKillDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("mantidKillDisplay", "Mantid Kill Display").ctx(HudEditElement.HudCtx.GARDEN)
                .section("Garden").enabled(() -> cfg().mantidKillDisplay)
                .size(() -> Math.max(90, width), () -> Math.max(10, lines.size() * 10))
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s,
                        List.of("§2Mantid Bonus: §c12§7/§a20", "§e3 Pests Expire: §b4m 12s")));
        Feature.of("MantidKillDisplay").section("Garden").when(() -> cfg().mantidKillDisplay)
                .tick(MantidKillDisplay::tick)
                .serverChat(MantidKillDisplay::onChat)
                .hud("mantidkilldisplay", MantidKillDisplay::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mantid Kill Display", () -> c.mantidKillDisplay, v -> c.mantidKillDisplay = v)
                            .desc("Mantid reforge: pest kills of the last 10 minutes (max 20) and when the next ones expire. Resets on world change. Move it in /tmap gui.")
                            .tags("mantid", "reforge", "pest", "bonus");
                    b.dropdown("  Show When", SHOW, () -> c.mantidShowWhen, v -> c.mantidShowWhen = v);
                    b.slider("  Group Expiry Within (sec)", 0f, 60f,
                            () -> (float) c.mantidGroupSeconds, v -> c.mantidGroupSeconds = Math.round(v), 0);
                });
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        Matcher m = PEST_DROP.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String item = m.group(2).trim(), pest = m.group(3).trim();
        boolean kill = switch (pest) {
            case "Field Mouse" -> item.equals("Dung");
            case "Lunar Moth" -> item.equals("Enchanted Sunflower");
            default -> !item.equals("Overclocker 3000");
        };
        if (!kill || !mantid()) return;
        expires.addLast(System.currentTimeMillis() + EXPIRE_MS);
        while (expires.size() > MAX_BONUS) expires.pollFirst();
        build();
    }

    private static boolean mantid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            CustomData d = st.get(DataComponents.CUSTOM_DATA);
            if (d != null && "mantid".equals(d.copyTag().getStringOr("modifier", ""))) return true;
        }
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            expires.clear();
            build();
        }
        if (ticks++ % 20 != 0) return;
        if (!SkyblockUtils.isInGarden()) {
            visible = false;
            return;
        }
        wearing = mantid();
        visible = switch (cfg().mantidShowWhen) {
            case "Always" -> true;
            case "Farming Tool" -> FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem());
            case "Vacuum" -> ItemUtil.skyblockId(mc.player.getMainHandItem()).contains("VACUUM");
            default -> wearing;
        };
        build();
    }

    private static void build() {
        long now = System.currentTimeMillis();
        while (!expires.isEmpty() && expires.peekFirst() <= now) expires.pollFirst();
        int bonus = Math.min(expires.size(), MAX_BONUS);
        String first = "§2Mantid Bonus: " + (bonus < MAX_BONUS ? "§c" : "§a") + bonus + "§7/§a" + MAX_BONUS;
        if (bonus == 0) {
            lines = List.of(first);
        } else {
            long next = expires.peekFirst();
            long window = cfg().mantidGroupSeconds * 1000L;
            int group = 0;
            for (long t : expires) {
                if (t - next <= window) group++;
                else break;
            }
            String pests = group == 1 ? "1 Pest Expires" : group + " Pests Expire";
            lines = List.of(first, "§e" + pests + ": §b" + time(next - now));
        }
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : lines) w = Math.max(w, font.width(s));
        width = w;
    }

    private static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || !visible || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        int w = 0;
        if (l == lines) w = width;
        else for (String s : l) w = Math.max(w, mc.font.width(s));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, -2, -2, w + 4, l.size() * 10 + 2);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
