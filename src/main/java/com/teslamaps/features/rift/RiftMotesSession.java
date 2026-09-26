package com.teslamaps.features.rift;

// Ported from SkyHanni's MotesSession.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class RiftMotesSession {

    private static final String PREFIX = "Lifetime Motes: ";

    private static long initial = -1, current = -1, enteredAt;
    private static int ticks;

    private RiftMotesSession() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("RiftMotesSession").section("Rift").when(() -> cfg().motesSession)
                .tick(RiftMotesSession::tick)
                .config("Rift", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Motes");
                    b.toggle("Motes Per Session", () -> c.motesSession, v -> c.motesSession = v)
                            .desc("When you leave the Rift: chat line with the motes gained this visit and motes/h (hover for details). Needs the Rift Info tab widget.");
                });
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        if (CraftRoomHolograms.inRift()) {
            long m = readMotes();
            if (m < 0) return;
            if (initial < 0) {
                initial = m;
                enteredAt = System.currentTimeMillis();
            }
            current = m;
            return;
        }
        if (initial < 0 || com.teslamaps.utils.HypixelModApi.mode() == null && SkyblockUtils.getArea().isEmpty()) return;
        send();
        initial = current = -1;
    }

    private static long readMotes() {
        for (String line : TabListUtils.getTabListLines()) {
            String t = StringUtil.stripTrim(line);
            if (!t.startsWith(PREFIX)) continue;
            try {
                return Long.parseLong(t.substring(PREFIX.length()).replace(",", "").replace(".", "").trim());
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static void send() {
        long gained = current - initial;
        if (gained < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long ms = Math.max(1000, System.currentTimeMillis() - enteredAt);
        long perHour = (long) (gained / (ms / 3_600_000.0));
        Component hover = Component.literal("§7Gained: §d" + String.format("%,d", gained) + " motes\n§7Time spent: §d" + time(ms)
                + "\n§7Motes/h: §d" + String.format("%,d", perHour));
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eGained §d" + String.format("%,d", gained)
                        + " motes §ethis Rift session! (§d" + com.teslamaps.utils.NumberUtil.formatSuffixed(perHour) + "/h§e)")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
    }

    private static String time(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }
}
