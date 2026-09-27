package com.teslamaps.features.tracker;

// HUD-038, after SkyHanni's SkyHanniItemTracker click rows + /shedittracker
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

public final class TrackerRows {

    private TrackerRows() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TrackerRows")
                .config("Trackers", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Trackers");
                    b.toggle("Interactive Tracker Rows", () -> c.trackerInteractive, v -> c.trackerInteractive = v)
                            .desc("With a menu open, click a tracker row to hide it (also from the profit), Ctrl+Click to remove it, "
                                    + "click the profit line to copy it. Fresh drops show green. "
                                    + "/tmap tracker <id> edit <item> <amount> fixes a count.");
                });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!cfg().trackerInteractive || event.button() != 0) return true;
                return !Tracker.click(event.x(), event.y(), event.hasControlDown());
            });
        });
    }

    /** edit / hide / remove / copy under /tmap tracker <id>. */
    public static <T extends com.mojang.brigadier.builder.ArgumentBuilder<FabricClientCommandSource, T>> T attach(T idArg) {
        idArg.then(ClientCommands.literal("edit")
                .then(ClientCommands.argument("item", StringArgumentType.word())
                        .suggests((c, b) -> suggestKeys(c, b))
                        .then(ClientCommands.argument("amount", LongArgumentType.longArg())
                                .executes(c -> run(c, "edit")))));
        idArg.then(ClientCommands.literal("hide")
                .then(ClientCommands.argument("item", StringArgumentType.word())
                        .suggests((c, b) -> suggestKeys(c, b))
                        .executes(c -> run(c, "hide"))));
        idArg.then(ClientCommands.literal("remove")
                .then(ClientCommands.argument("item", StringArgumentType.word())
                        .suggests((c, b) -> suggestKeys(c, b))
                        .executes(c -> run(c, "remove"))));
        idArg.then(ClientCommands.literal("copy").executes(c -> run(c, "copy")));
        return idArg;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKeys(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        Tracker t = Tracker.get(StringArgumentType.getString(c, "id"));
        if (t != null) {
            String typed = b.getRemaining().toUpperCase(java.util.Locale.ROOT);
            for (String k : t.keys()) if (k.toUpperCase(java.util.Locale.ROOT).startsWith(typed) && !k.equals(b.getRemaining())) b.suggest(k);
        }
        return b.buildFuture();
    }

    private static int run(CommandContext<FabricClientCommandSource> c, String action) {
        String id = StringArgumentType.getString(c, "id");
        Tracker t = Tracker.get(id);
        if (t == null) {
            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §cNo tracker " + id));
            return 0;
        }
        String msg;
        switch (action) {
            case "edit" -> {
                String item = key(t, StringArgumentType.getString(c, "item"));
                long amount = LongArgumentType.getLong(c, "amount");
                if (amount == 0) {
                    c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §cAmount can not be zero."));
                    return 0;
                }
                t.edit(item, amount);
                msg = (amount > 0 ? "added " : "removed ") + Math.abs(amount) + "x " + item;
            }
            case "hide" -> {
                String item = key(t, StringArgumentType.getString(c, "item"));
                t.toggleHidden(item);
                msg = item + (t.hidden(item) ? " hidden" : " shown") + (cfg().trackerInteractive ? "" : " (turn on Interactive Tracker Rows)");
            }
            case "remove" -> {
                String item = key(t, StringArgumentType.getString(c, "item"));
                t.remove(item);
                msg = item + " removed";
            }
            default -> {
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(t.profitText());
                msg = "profit copied";
            }
        }
        c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §f" + id + ": " + msg));
        return 1;
    }

    // keys are item ids; accept any case
    private static String key(Tracker t, String typed) {
        for (String k : t.keys()) if (k.equalsIgnoreCase(typed)) return k;
        return typed.toUpperCase(java.util.Locale.ROOT);
    }
}
