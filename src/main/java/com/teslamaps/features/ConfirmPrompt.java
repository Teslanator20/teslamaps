package com.teslamaps.features;

// Ported from Skyblocker's ConfirmationPromptHelper
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Optional;

public final class ConfirmPrompt {

    private static final List<String> PHRASES = List.of("[Aye sure do!]", "[You guessed it!]", "[Sure thing, partner!]", "YES", "Yes");
    private static final List<String> BUTTONS = List.of(
            "§e ➜ §a[Aye sure do!]", "§e ➜ §a[You guessed it!]", "§e ➜ §a[Sure thing, partner!]", "§a§l[YES]", "§a[Yes]");
    private static final long VALID_MS = 60_000;

    private static String command;
    private static long foundAt;
    private static Object lastLevel;

    private ConfirmPrompt() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        return cfg().confirmPromptHelper && cfg().section("Chat");
    }

    public static void register() {
        Feature.of("ConfirmPrompt").section("Chat").when(() -> cfg().confirmPromptHelper)
                .tick(ConfirmPrompt::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Chat Extras");
                    b.toggle("Click Chat To Confirm Prompts", () -> c.confirmPromptHelper, v -> c.confirmPromptHelper = v)
                            .desc("After a [YES] / NPC confirm prompt, a left click anywhere in open chat (not on another button) answers it. Valid for 60s.")
                            .tags("confirm", "yes", "prompt", "carnival");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            command = null;
        }
    }

    public static void onChatComponent(Component message) {
        if (!on() || !SkyblockUtils.isInSkyblock()) return;
        String s = message.getString();
        boolean any = false;
        for (String p : PHRASES) if (s.contains(p)) { any = true; break; }
        if (!any) return;
        Optional<String> found = message.visit((style, text) -> {
            String t = text.replaceAll("\\s+", " ").trim();
            if (BUTTONS.contains(t) && style.getClickEvent() instanceof ClickEvent.RunCommand run
                    && (run.command().startsWith("/chatprompt") || run.command().startsWith("/selectnpcoption"))) {
                return Optional.of(run.command());
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (found.isEmpty()) return;
        command = found.get();
        foundAt = System.currentTimeMillis();
        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Click anywhere in chat to confirm."));
        });
    }

    /** Left click in open chat that hit nothing clickable. */
    public static void onEmptyChatClick() {
        if (!on() || command == null) return;
        String cmd = command;
        command = null;
        if (System.currentTimeMillis() - foundAt > VALID_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }
}
