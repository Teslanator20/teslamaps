package com.teslamaps.features;

// NEU Misc: "Filter Skyblock Levels in Chat", "Enable text field tweaks" (EditBoxUndoMixin),
// "Stop Hearts Bouncing" (HeartsBounceMixin)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;

public final class QolTweaks {

    private QolTweaks() {}

    public static void register() {
        ChatFilter.RULES.add(new ChatFilter.Rule("chatFilterSbLevel", ChatFilter.G_SPAM, true, false,
                c -> c.chatFilterSbLevel, t -> lowLevel()));
        Feature.of("QolTweaks")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("SkyBlock Level Filter");
                    b.toggle("Hide Low SkyBlock Levels", () -> c.chatFilterSbLevel, v -> c.chatFilterSbLevel = v)
                            .desc("Hides public chat from players below the level set here. Uses the Spam filter action; needs the chat filter on.");
                    b.slider("  Minimum Level", 1f, 500f, () -> (float) c.chatFilterSbLevelMin,
                            v -> c.chatFilterSbLevelMin = Math.round(v), 0);
                })
                .config("Interface", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Text Fields");
                    b.toggle("Text Field Undo", () -> c.textFieldUndo, v -> c.textFieldUndo = v)
                            .desc("Ctrl+Z undoes and Ctrl+Y (or Ctrl+Shift+Z) redoes in every text field.");
                });
    }

    private static boolean lowLevel() {
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || m.channel() != PlayerChat.Channel.ALL || m.level() == null || m.own()) return false;
        try {
            return Integer.parseInt(m.level()) < TeslaMapsConfig.get().chatFilterSbLevelMin;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
