package com.teslamaps.features;

// Ported from SkyHanni's PestTrapFeatures.kt and PestTrapApi.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Interval warnings for full, unbaited and unplaced pest traps, plus a release key in the trap menu. */
public final class PestTraps {

    private static final Pattern PLACED = Pattern.compile("Pest Traps: (\\d+)/(\\d+)");
    private static final Pattern FULL = Pattern.compile("Full Traps: (.+)");
    private static final Pattern NO_BAIT = Pattern.compile("No Bait: (.+)");
    private static final int RELEASE_SLOT = 16;

    private static int placed = -1, max = 3;
    private static String full, noBait;
    private static long enteredAt;
    private static long nextWarn;
    private static int ticks;
    private static boolean keyDown;

    private PestTraps() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PestTraps").section("Garden").when(() -> cfg().pestTrapWarn).tick(PestTraps::tick);
        Feature.of("PestTrapRelease").section("Garden").legit().when(() -> cfg().pestTrapRelease && cfg().pestTrapReleaseKey != -1)
                .tick(PestTraps::tickRelease);
    }

    private static void tick() {
        if (!SkyblockUtils.isInGarden()) {
            enteredAt = 0;
            placed = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (enteredAt == 0) enteredAt = now;
        if (++ticks < 20) return;
        ticks = 0;
        read();
        if (now - enteredAt < 5000 || now < nextWarn) return;
        List<String> warnings = warnings();
        if (warnings.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        nextWarn = now + Math.max(10, c.pestTrapWarnInterval) * 1000L;
        Minecraft mc = Minecraft.getInstance();
        if (c.pestTrapWarnChat && mc.player != null) {
            for (String w : warnings) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + w));
        }
        if (c.pestTrapWarnTitle && mc.gui != null) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal(warnings.get(0)));
            mc.gui.setSubtitle(Component.empty());
        }
        if (c.pestTrapWarnVolume > 0f) {
            var sound = SoundOptions.resolve(c.pestTrapWarnSound);
            if (sound != null) LoudSound.play(sound, c.pestTrapWarnVolume, 1.0f);
        }
    }

    private static void read() {
        full = null;
        noBait = null;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            Matcher m = PLACED.matcher(line);
            if (m.find()) {
                placed = Integer.parseInt(m.group(1));
                max = Integer.parseInt(m.group(2));
                continue;
            }
            m = FULL.matcher(line);
            if (m.find()) { full = traps(m.group(1)); continue; }
            m = NO_BAIT.matcher(line);
            if (m.find()) noBait = traps(m.group(1));
        }
    }

    private static String traps(String s) {
        s = s.trim();
        return s.equalsIgnoreCase("None") || s.isEmpty() ? null : s.replace(", ", "§7, §a");
    }

    private static List<String> warnings() {
        TeslaMapsConfig c = cfg();
        List<String> out = new ArrayList<>(3);
        if (c.pestTrapWarnFull && full != null) out.add("§cFull Traps: §a" + full);
        if (c.pestTrapWarnNoBait && noBait != null) out.add("§cNo Bait: §a" + noBait);
        if (c.pestTrapWarnUnplaced && placed >= 0 && placed < max) {
            out.add("§aUnplaced Traps: §c" + placed + "§4/§c" + max + " §aplaced");
        }
        return out;
    }

    private static void tickRelease() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ContainerScreen screen) || !SkyblockUtils.isInGarden()) { keyDown = false; return; }
        String title = StringUtil.stripTrim(screen.getTitle().getString());
        if (!title.equals("Pest Trap") && !title.equals("Mouse Trap") && !title.equals("Vermin Trap")) { keyDown = false; return; }
        boolean down = KeyNames.isDown(cfg().pestTrapReleaseKey);
        if (down && !keyDown && !LegitMode.blocksCheats() && screen.getMenu().slots.size() > RELEASE_SLOT) {
            mc.gameMode.handleContainerInput(screen.getMenu().containerId, RELEASE_SLOT, 0, ContainerInput.PICKUP, mc.player);
        }
        keyDown = down;
    }
}
