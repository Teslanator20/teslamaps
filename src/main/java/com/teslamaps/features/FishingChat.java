package com.teslamaps.features;

// Ported from SkyHanni's SeaCreatureManager.kt + DulkirMod's DoubleHookDing.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;

public final class FishingChat {

    private static final String DH = "It's a Double Hook!";
    private static final String DH_WOOT = "It's a Double Hook! Woot woot!";

    private static long lastDoubleHook;

    private FishingChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FishingChat").section("Fishing").when(() -> cfg().fishingChat && cfg().fishingChatDhSound)
                .serverChat(FishingChat::onChat)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Fishing Chat");
                    b.toggle("Fishing Chat Tweaks", () -> c.fishingChat, v -> c.fishingChat = v)
                            .desc("Shorter sea creature catch lines, merged Double Hook line and a Double Hook sound. Hiding needs the Chat section on.")
                            .tags("fishing", "double hook", "sea creature", "chat", "compact");
                    b.toggle("  Short Catch Message", () -> c.fishingChatShort, v -> c.fishingChatShort = v)
                            .desc("\"You caught a <name>!\" instead of the long spawn sentence");
                    b.toggle("  Merge Double Hook Line", () -> c.fishingChatMergeDh, v -> c.fishingChatMergeDh = v)
                            .desc("Drops \"It's a Double Hook!\" and puts DOUBLE HOOK! on the catch line");
                    b.dropdown("    Position", new String[] { "Left", "Right" },
                            () -> c.fishingChatDhPosition, v -> c.fishingChatDhPosition = v);
                    b.toggle("  Hide Catch Messages", () -> c.fishingChatHide, v -> c.fishingChatHide = v)
                            .desc("Removes the sea creature spawn lines completely");
                    b.toggle("  Double Hook Sound", () -> c.fishingChatDhSound, v -> c.fishingChatDhSound = v);
                    b.soundDropdown("    Sound", SoundOptions.keys(),
                            () -> c.fishingChatDhSoundId, v -> c.fishingChatDhSoundId = v);
                    b.slider("    Volume", 0f, 4f,
                            () -> c.fishingChatDhVolume, v -> c.fishingChatDhVolume = v, 2);
                });
    }

    private static boolean isDoubleHook(String t) {
        return t.equals(DH) || t.equals(DH_WOOT);
    }

    private static void onChat(String text) {
        if (!isDoubleHook(StringUtil.stripTrim(text))) return;
        TeslaMapsConfig c = cfg();
        if (c.fishingChatDhVolume > 0f) LoudSound.play(SoundOptions.resolve(c.fishingChatDhSoundId), c.fishingChatDhVolume, 1f);
    }

    /** From ChatFilter.process: null = untouched, "" = drop, else the replacement. */
    public static String handle(String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.fishingChat || !c.section("Fishing") || PlayerChat.current() != null) return null;
        if (!c.fishingChatShort && !c.fishingChatMergeDh && !c.fishingChatHide) return null;
        String t = stripped.trim();
        if (t.isEmpty()) return null;
        if (isDoubleHook(t)) {
            lastDoubleHook = System.currentTimeMillis();
            return c.fishingChatMergeDh || c.fishingChatHide ? "" : null;
        }
        String name = SeaCreatureList.nameFor(t);
        if (name == null) return null;
        boolean dh = System.currentTimeMillis() - lastDoubleHook < 1000;
        lastDoubleHook = 0;
        if (c.fishingChatHide) return "";
        boolean merge = c.fishingChatMergeDh && dh;
        if (!c.fishingChatShort && !merge) return null;
        String line;
        if (c.fishingChatShort) {
            SeaCreatureList.Info info = SeaCreatureList.info(name);
            String color = info == null ? "§b" : info.color() + (info.rare() ? "§l" : "");
            line = "§9You caught " + article(name) + " " + color + name + "§9!";
        } else {
            line = "§b" + t;
        }
        if (merge) line = "Right".equals(c.fishingChatDhPosition) ? line + " §e§lDOUBLE HOOK!" : "§e§lDOUBLE HOOK! §r" + line;
        return line;
    }

    private static String article(String name) {
        return "AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an" : "a";
    }
}
