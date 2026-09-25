package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Locale;

/** Shortcut expansions split on ';' run one after another; "wait <ms>" pauses. /cmdstop cancels. */
public final class CommandChains {

    private record Step(String command, long waitMs) {}

    private static final ArrayDeque<Step> queue = new ArrayDeque<>();
    private static long nextAt = 0L;
    private static boolean sending = false;

    private CommandChains() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean active() {
        return cfg().shortcutChains && cfg().section("Chat");
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(CommandChains::allow);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("cmdstop").executes(c -> stop()));
            d.register(ClientCommands.literal("tmap").then(ClientCommands.literal("cmdstop").executes(c -> stop())));
        });
        Feature.of("CommandChains").tickAlways(CommandChains::tick)
                .config("Chat", b -> {
                    b.toggle("Shortcut Command Chains", () -> cfg().shortcutChains, v -> cfg().shortcutChains = v)
                            .desc("A shortcut command like \"warp dhub; wait 2000; pc hi\" runs each part in turn. /cmdstop cancels.")
                            .tags("alias", "macro", "cmdstop");
                    b.slider("  Delay Between Commands (ms)", 250f, 3000f,
                            () -> (float) cfg().shortcutChainDelay, v -> cfg().shortcutChainDelay = Math.round(v / 50f) * 50);
                });
    }

    private static int stop() {
        int n = queue.size();
        queue.clear();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + (n == 0 ? "No command chain running" : "Command chain stopped")));
        return 1;
    }

    private static boolean allow(String command) {
        if (sending || !active() || command == null) return true;
        int sp = command.indexOf(' ');
        String alias = (sp < 0 ? command : command.substring(0, sp)).trim();
        String args = sp < 0 ? "" : command.substring(sp + 1).trim();
        if (alias.isEmpty() || alias.equalsIgnoreCase("tmap")) return true;
        for (TeslaMapsConfig.Shortcut sc : cfg().shortcuts) {
            if (sc.alias == null || sc.command == null || !sc.alias.trim().equalsIgnoreCase(alias)) continue;
            String exp = sc.command.trim();
            if (exp.indexOf(';') < 0) return true;
            start(exp, args);
            return false;
        }
        return true;
    }

    private static void start(String exp, String args) {
        queue.clear();
        boolean placeholder = exp.contains("{args}");
        boolean first = true;
        long wait = 0L;
        for (String raw : exp.split(";")) {
            String part = raw.trim();
            if (part.startsWith("/")) part = part.substring(1).trim();
            if (part.isEmpty()) continue;
            String lower = part.toLowerCase(Locale.ROOT);
            if (lower.startsWith("wait ") || lower.startsWith("delay ")) {
                try { wait += Math.max(0L, Long.parseLong(part.substring(part.indexOf(' ') + 1).trim())); } catch (NumberFormatException ignored) {}
                continue;
            }
            if (placeholder) part = part.replace("{args}", args).trim();
            else if (first && !args.isEmpty()) part = part + " " + args;
            queue.add(new Step(part, wait));
            wait = 0L;
            first = false;
        }
        nextAt = System.currentTimeMillis();
    }

    private static void tick() {
        if (queue.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            queue.clear();
            return;
        }
        Step s = queue.peek();
        if (System.currentTimeMillis() < nextAt + s.waitMs()) return;
        queue.poll();
        sending = true;
        try {
            mc.getConnection().sendCommand(s.command());
        } finally {
            sending = false;
        }
        nextAt = System.currentTimeMillis() + Math.max(250, cfg().shortcutChainDelay);
    }
}
