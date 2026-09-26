package com.teslamaps.features.choco;

// After SkyHanni's HoppityEggsManager.sendNextEggAvailable / sendNextHuntIn
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.ChatTimestamps;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class HoppityNextEgg {

    private static final long EPOCH = 1560275700000L, YEAR_MS = 446_400_000L, MONTH_MS = YEAR_MS / 12,
            DAY_MS = 1_200_000L, HOUR_MS = 50_000L;
    private static final int[] SPAWN_HOURS = {7, 14, 21};
    private static final String NEXT_EGG = "Next egg available in ";
    private static final String NEXT_HUNT = "The next Hoppity's Hunt is in ";

    private HoppityNextEgg() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HoppityNextEgg").section(ChocoData.CATEGORY).when(() -> cfg().hoppityNextEggChat)
                .serverChat(HoppityNextEgg::onChat)
                .config(ChocoData.CATEGORY, b -> {
                    b.toggle("Egglocator: Next Egg Time", () -> cfg().hoppityNextEggChat, v -> cfg().hoppityNextEggChat = v)
                            .desc("When the Egglocator finds no egg, a chat line tells when the next one spawns (or when the next Hunt starts). The line replaces itself.");
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("There are no hidden Chocolate Rabbit Eggs nearby!")
                || (t.startsWith("You have already collected this Chocolate ") && t.contains("Try again when it respawns"))) {
            if (!eventActive()) return;
            String msg = "§e" + NEXT_EGG + "§b" + ChocoData.format(untilNextEgg()) + "§e.";
            TickScheduler.schedule(1, () -> send(msg));
        } else if (t.equals("This only works during Hoppity's Hunt!")) {
            long inYear = (System.currentTimeMillis() - EPOCH) % YEAR_MS;
            String msg = "§eHoppity's Hunt is not active. " + NEXT_HUNT + "§b" + ChocoData.format(YEAR_MS - inYear) + "§e.";
            TickScheduler.schedule(1, () -> send(msg));
        }
    }

    static boolean eventActive() {
        long inYear = (System.currentTimeMillis() - EPOCH) % YEAR_MS;
        return inYear / MONTH_MS < 3;
    }

    static long untilNextEgg() {
        long inDay = (System.currentTimeMillis() - EPOCH) % DAY_MS;
        for (int h : SPAWN_HOURS) {
            long at = h * HOUR_MS;
            if (at > inDay) return at - inDay;
        }
        return DAY_MS - inDay + SPAWN_HOURS[0] * HOUR_MS;
    }

    private static void send(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var chat = mc.gui.getChat();
        List<GuiMessage> all = ((ChatComponentAccessor) chat).teslamaps$getAllMessages();
        for (int i = 0; i < Math.min(30, all.size()); i++) {
            String s = ChatTimestamps.cut(StringUtil.stripTrim(all.get(i).content().getString()));
            if (!s.startsWith("[TeslaMaps] ")) continue;
            s = s.substring(12);
            if (!s.startsWith(NEXT_EGG) && !s.contains(NEXT_HUNT)) continue;
            all.remove(i);
            ((ChatComponentAccessor) chat).teslamaps$refreshTrimmedMessages();
            break;
        }
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg));
    }
}
