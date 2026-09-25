package com.teslamaps.features;

// Ported from Odin's TerminalTitles.kt, hide modes from Devonian's TerminalHideCompletion.kt,
// class colors and "Phase Done" from NoammAddons' F7Titles.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;

public class TerminalTitles {

    public static final String[] MODES = {"Replace", "Hide Others", "Hide All"};
    public static final String[] FORMAT_NAMES = {"Name [3/7]", "Name Type (3/7)", "3/7"};
    private static final String[] FORMATS = {
            "§6{name} §8[§c{current}§8/§a{total}§8]",
            "{cname} {type} §f({progress}§f)",
            "{progress}"};

    private static Feature feature;
    private static long replacedAt = 0;

    public static void register() {
        feature = Feature.of("TerminalTitles").when(() -> TeslaMapsConfig.get().terminalTitles);
    }

    /** Title or subtitle packet on the main thread. True = drop it. */
    public static boolean onTitle(String raw, boolean subtitle) {
        if (feature == null || !feature.active() || raw == null) return false;
        if (!subtitle && System.currentTimeMillis() - replacedAt < 250 && StringUtil.stripTrim(raw).isEmpty()) return true;
        if (raw.indexOf('(') < 0) return false;
        if (!DungeonManager.isInDungeon()) return false;
        Matcher m = TerminalProgress.COMPLETED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return false;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        String name = m.group(1), type = m.group(3);
        boolean wanted = switch (type) {
            case "terminal" -> c.terminalTitleTerminal;
            case "lever" -> c.terminalTitleLever;
            default -> c.terminalTitleDevice;
        };
        if (!wanted) return false;

        Minecraft mc = Minecraft.getInstance();
        boolean own = mc.player != null && name.equals(mc.player.getName().getString());
        switch (c.terminalTitleMode) {
            case 1: return !own;
            case 2: return true;
            default: break;
        }
        if (c.terminalTitleSelfOnly && !own) return false;

        int cur = Integer.parseInt(m.group(4)), total = Integer.parseInt(m.group(5));
        String text;
        if (c.terminalTitlePhaseDone && cur == total) {
            text = "§a§lPhase Done!";
        } else {
            String fmt = FORMATS[Math.max(0, Math.min(FORMATS.length - 1, c.terminalTitleFormat))];
            String typeName = switch (type) {
                case "terminal" -> "§5Terminal";
                case "device" -> "§bDevice";
                default -> "§cLever";
            };
            String progress = (cur == total ? "§a" : cur * 2 >= total ? "§e" : "§c") + cur + "§8/§a" + total;
            text = fmt.replace("{cname}", TerminalProgress.classColor(name) + name + "§r")
                    .replace("{name}", name)
                    .replace("{action}", m.group(2))
                    .replace("{type}", typeName)
                    .replace("{current}", String.valueOf(cur))
                    .replace("{total}", String.valueOf(total))
                    .replace("{progress}", progress);
        }
        replacedAt = System.currentTimeMillis();
        mc.gui.setTimes(0, 20, 5);
        if (c.terminalTitleSubtitle) {
            mc.gui.setTitle(Component.empty());
            mc.gui.setSubtitle(Component.literal(text));
        } else {
            mc.gui.setSubtitle(Component.empty());
            mc.gui.setTitle(Component.literal(text));
        }
        return true;
    }
}
