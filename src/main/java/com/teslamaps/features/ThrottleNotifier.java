package com.teslamaps.features;

// Ported from DulkirMod's ThrottleNotif.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public final class ThrottleNotifier {

    private static final String LINE = "This menu has been throttled! Please slow down...";
    private static long lastSent = 0L;
    private static Feature feature;

    private ThrottleNotifier() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("throttlemsg")
                        .executes(c -> {
                            c.getSource().sendFeedback(Component.literal("§7Throttle message: §f" + cfg().throttleNotifierText));
                            return 1;
                        })
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                            cfg().throttleNotifierText = StringArgumentType.getString(c, "text");
                            TeslaMapsConfig.save();
                            c.getSource().sendFeedback(Component.literal("§aThrottle message set: §f" + cfg().throttleNotifierText));
                            return 1;
                        })))));
        feature = Feature.of("ThrottleNotifier").section("Party").when(() -> cfg().throttleNotifier)
                .config("Party", b -> {
                    b.label("Throttle Notifier");
                    b.toggle("Throttle Notifier", () -> cfg().throttleNotifier, v -> cfg().throttleNotifier = v)
                            .desc("In dungeons: hides Hypixel's menu throttle line and tells your party instead.");
                    b.toggle("  At Most Every 8s", () -> cfg().throttleNotifierCooldown, v -> cfg().throttleNotifierCooldown = v);
                    b.info("Message: /tmap throttlemsg <text>");
                });
    }

    /** From ChatMixin; true = drop the line. */
    public static boolean hide(String text) {
        if (feature == null || !feature.active() || !DungeonManager.isInDungeon()) return false;
        if (!StringUtil.stripTrim(text).equals(LINE)) return false;
        long now = System.currentTimeMillis();
        if (!cfg().throttleNotifierCooldown || now - lastSent > 8000L) {
            lastSent = now;
            String msg = cfg().throttleNotifierText;
            Announce.party(msg == null || msg.isBlank() ? "Menu throttled!" : msg);
        }
        return true;
    }
}
