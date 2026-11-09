package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// Ported from SkyHanni's SeaCreatureFeatures
/** Title + sound on a rare catch, and the party announce that goes with it. */
public final class RareSeaCreature {

    private static final Pattern DOUBLE_HOOK = Pattern.compile("It's a Double Hook!(?: Woot woot!)?");
    private static final long DOUBLE_HOOK_WINDOW_MS = 2_000;

    private static long lastDoubleHook = 0;

    private RareSeaCreature() {}

    public static void onChatMessage(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.rareCatchAlert && !c.rareCatchAnnounce && !c.rareCatchKeySwap) return;

        String clean = StringUtil.stripTrim(text);
        if (DOUBLE_HOOK.matcher(clean).matches()) {
            lastDoubleHook = System.currentTimeMillis();
            return;
        }

        String name = SeaCreatureList.nameFor(clean);
        if (name == null) return;
        boolean target = FishCatchKeySwap.TARGETS.contains(name);
        if (c.rareCatchKeySwap && target) FishCatchKeySwap.trigger();
        SeaCreatureList.Info info = SeaCreatureList.info(name);
        if (info == null) return;
        boolean rare = isRare(info, c.rareCatchRarity) || target;
        boolean doAlert = c.seaCreatureAlert.getOrDefault(name, rare);
        boolean doAnnounce = c.seaCreatureAnnounce.getOrDefault(name, rare);
        if (!doAlert && !doAnnounce) return;

        boolean doubleHook = System.currentTimeMillis() - lastDoubleHook < DOUBLE_HOOK_WINDOW_MS;
        lastDoubleHook = 0;

        if (c.rareCatchAlert && doAlert) alert(c, info, doubleHook);
        if (c.rareCatchAnnounce && doAnnounce) announce(name, doubleHook);
    }

    /** What the Counts As Rare rule says, without per-creature overrides. */
    public static boolean ruleRare(String name) {
        SeaCreatureList.Info info = SeaCreatureList.info(name);
        if (info == null) return false;
        return isRare(info, TeslaMapsConfig.get().rareCatchRarity) || FishCatchKeySwap.TARGETS.contains(name);
    }

    private static boolean isRare(SeaCreatureList.Info info, String mode) {
        return switch (mode) {
            case "Epic+" -> rank(info.rarity()) >= 3;
            case "Rare+" -> rank(info.rarity()) >= 2;
            default -> info.rare();
        };
    }

    private static int rank(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            default -> 0;
        };
    }

    private static void alert(TeslaMapsConfig c, SeaCreatureList.Info info, boolean doubleHook) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String title = c.rareCatchName
                ? info.color() + "§l" + info.name() + "!"
                : info.color() + "§lRARE CATCH!";
        mc.gui.hud.setTimes(0, 35, 5);
        mc.gui.hud.setTitle(Component.literal(title));
        mc.gui.hud.setSubtitle(Component.literal(doubleHook ? "§eDouble Hook!" : ""));
        ScreenFlash.alert(c.screenFlashRareCatch);
        if (c.rareCatchVolume > 0f) {
            LoudSound.play(SoundOptions.resolve(c.rareCatchSound), c.rareCatchVolume, 1.4f);
        }
    }

    private static void announce(String name, boolean doubleHook) {
        Minecraft mc = Minecraft.getInstance();
        String line = (doubleHook ? "DOUBLE HOOK: " : "") + "I caught " + article(name) + " " + name + "!";
        mc.execute(() -> {
            com.teslamaps.features.ChatQueue.command("pc " + line);
        });
    }

    private static String article(String name) {
        char first = Character.toUpperCase(name.charAt(0));
        return "AEIOU".indexOf(first) >= 0 ? "an" : "a";
    }
}
