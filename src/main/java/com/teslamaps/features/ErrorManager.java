package com.teslamaps.features;

// SkyHanni's ErrorManager, trimmed
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Catches exceptions from feature callbacks so one bad feature can't crash the game. */
public final class ErrorManager {

    private static final int MAX_FRAMES = 15;
    private static final Set<String> seen = new HashSet<>();
    private static final List<Component> queued = new ArrayList<>();
    private static String version;

    private ErrorManager() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        com.teslamaps.registry.Feature.of("ErrorManager")
                .when(() -> !queued.isEmpty())
                .tick(ErrorManager::flush)
                .config("Interface", b -> {
                    b.label("Errors");
                    b.toggle("Catch Feature Errors", () -> cfg().catchFeatureErrors, v -> cfg().catchFeatureErrors = v)
                            .desc("An exception in a feature's tick, HUD, world render or chat handler is caught and the game keeps running."
                                    + " Off = the game crashes like before.")
                            .tags("crash", "exception", "error");
                    b.toggle("  Report In Chat", () -> cfg().featureErrorsInChat, v -> cfg().featureErrorsInChat = v)
                            .desc("One chat line per distinct error, click copies the stack trace. Always written to latest.log.");
                    b.info("/tmap debug throwerror, /tmap debug reseterrors");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("debug")
                        .then(ClientCommands.literal("throwerror").executes(c -> {
                            try {
                                throw new IllegalStateException("test error from /tmap debug throwerror");
                            } catch (IllegalStateException e) {
                                caught("ErrorManager.test", e);
                            }
                            return 1;
                        }))
                        .then(ClientCommands.literal("reseterrors").executes(c -> {
                            seen.clear();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §fError cache cleared"));
                            return 1;
                        })))));
    }

    /** Call from a catch block. Rethrows unless catching is on. */
    public static void caught(String where, Throwable t) {
        if (!cfg().catchFeatureErrors || FabricLoader.getInstance().isDevelopmentEnvironment()) sneakyThrow(t);
        StackTraceElement[] st = t.getStackTrace();
        String key = where + "|" + t.getClass().getName() + "|" + (st.length > 0 ? st[0].toString() : "");
        if (!seen.add(key)) return;
        TeslaMaps.LOGGER.error("[TeslaMaps] caught error in {}", where, t);
        if (!cfg().featureErrorsInChat) return;

        String copy = report(where, t);
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage();
        Component line = Component.literal("§c[TeslaMaps " + version() + "]: §f" + where + " §7threw §c" + msg + " §8(click to copy)")
                .withStyle(s -> s.withClickEvent(new ClickEvent.CopyToClipboard(copy))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to copy the error report"))));
        queued.add(line);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.isSameThread()) flush();
    }

    private static void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        List<Component> out = new ArrayList<>(queued);
        queued.clear();
        for (Component c : out) mc.player.sendSystemMessage(c);
    }

    private static String report(String where, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("TeslaMaps ").append(version()).append(" error in ").append(where).append('\n');
        sb.append("MC 26.1.2, area: ").append(com.teslamaps.utils.SkyblockUtils.getArea()).append('\n');
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 4) {
            if (depth > 1) sb.append("Caused by: ");
            sb.append(cur).append('\n');
            StackTraceElement[] st = cur.getStackTrace();
            for (int i = 0; i < Math.min(MAX_FRAMES, st.length); i++) sb.append("    at ").append(st[i]).append('\n');
            if (st.length > MAX_FRAMES) sb.append("    ... ").append(st.length - MAX_FRAMES).append(" more\n");
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return sb.toString();
    }

    private static String version() {
        if (version == null) version = FabricLoader.getInstance().getModContainer(TeslaMaps.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        return version;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
