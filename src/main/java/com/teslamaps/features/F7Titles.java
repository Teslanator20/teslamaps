package com.teslamaps.features;

// Ported from NoammAddons' F7Titles.kt (crystal + wither titles)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class F7Titles {

    private static final Pattern CRYSTAL = Pattern.compile("^(\\d)/(\\d) Energy Crystals are now active!$");
    private static final Pattern ENRAGED = Pattern.compile("^⚠ (\\w+) is enraged! ⚠$");

    private static boolean maxorSeen, maxorDead, goldorStart, goldorDead, necronStart, necronDead;
    private static Object lastLevel;

    private F7Titles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("F7Titles").when(() -> cfg().f7Titles)
                .serverChat(F7Titles::onChat)
                .tick(F7Titles::tick);
    }

    private static boolean inF7Boss() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 7 && DungeonManager.isInBossArea();
    }

    private static void onChat(String raw) {
        if (!inF7Boss()) return;
        String text = StringUtil.stripTrim(raw);
        switch (text) {
            case "[BOSS] Necron: ARGH!" -> necronStart = true;
            case "The Core entrance is opening!" -> goldorStart = true;
            default -> {}
        }
        if (!cfg().f7BossTitles) return;
        switch (text) {
            case "[BOSS] Maxor: YOU TRICKED ME!", "[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!" -> show("§dMaxor Stunned!");
            case "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> show("§bStorm Crushed!");
            case "[BOSS] Storm: I should have known that I stood no chance." -> show("§bStorm Dead!");
            default -> {}
        }
    }

    /** Subtitle packet, main thread. True = drop Hypixel's subtitle. */
    public static boolean onSubtitle(String raw) {
        TeslaMapsConfig c = cfg();
        if (!c.f7Titles || !inF7Boss()) return false;
        String text = StringUtil.stripTrim(raw);
        if (text.isEmpty()) return false;
        if (c.f7CrystalTitles) {
            if (text.equals("The Energy Laser is charging up!")) return true;
            Matcher m = CRYSTAL.matcher(text);
            if (m.matches()) {
                int cur = Integer.parseInt(m.group(1)), max = Integer.parseInt(m.group(2));
                subtitle("§3Crystal §r(" + (cur == max ? "§b" : "§c") + cur + "§r/§b" + max + "§r)", false);
                return true;
            }
        }
        if (c.f7BossTitles) {
            Matcher m = ENRAGED.matcher(text);
            if (m.matches()) {
                String boss = m.group(1);
                String color = switch (boss) {
                    case "Storm" -> "§b";
                    case "Maxor" -> "§5";
                    case "Goldor" -> "§7";
                    case "Necron" -> "§c";
                    default -> "";
                };
                show(color + text);
                return true;
            }
        }
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            maxorSeen = maxorDead = goldorStart = goldorDead = necronStart = necronDead = false;
        }
        if (!cfg().f7BossTitles || !inF7Boss()) return;
        var events = ((com.teslamaps.mixin.BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).teslamaps$getEvents();
        if (events == null) return;
        for (LerpingBossEvent ev : events.values()) {
            if (ev.getName() == null) continue;
            String name = ev.getName().getString();
            boolean zero = ev.getProgress() <= 0f;
            if (name.contains("Maxor")) {
                if (!zero) maxorSeen = true;
                else if (maxorSeen && !maxorDead) { maxorDead = true; show("§dMaxor Dead!"); }
            } else if (name.contains("Goldor")) {
                if (zero && goldorStart && !goldorDead) { goldorDead = true; show("§7Goldor Dead!"); }
            } else if (name.contains("Necron")) {
                if (zero && necronStart && !necronDead) { necronDead = true; show("§cNecron Dead!!"); }
            }
        }
    }

    private static void show(String text) {
        subtitle(text, true);
    }

    private static void subtitle(String text, boolean sound) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(0, 40, 10);
        mc.gui.hud.setSubtitle(Component.literal(text));
        mc.gui.hud.setTitle(Component.empty());
        if (sound && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.25f, 1f);
    }
}
