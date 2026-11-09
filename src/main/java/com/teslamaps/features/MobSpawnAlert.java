package com.teslamaps.features;

// Ported from Odin's MobSpawn
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MobSpawnAlert {

    public static final String[] MODES = {"Title + Sound", "Sound Only"};

    private static final Set<Integer> seen = new HashSet<>();
    private static Object world;
    private static long lastAlert;
    private static int ticks;

    private MobSpawnAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MobSpawnAlert").section("ESP")
                .when(() -> cfg().mobSpawnAlert)
                .tick(MobSpawnAlert::tick);
    }

    public static void config(ConfigBuilder b) {
        TeslaMapsConfig c = b.config();
        b.toggle("  Spawn Alert", () -> c.mobSpawnAlert, v -> c.mobSpawnAlert = v)
                .desc("Alert when a mob whose name contains a Custom ESP entry shows up (works in Legit Mode too)");
        b.dropdown("    Alert Style", MODES, () -> c.mobSpawnAlertMode, v -> c.mobSpawnAlertMode = v);
        b.soundDropdown("    Sound", SoundOptions.keys(), () -> c.mobSpawnAlertSound, v -> c.mobSpawnAlertSound = v);
        b.slider("    Cooldown (s)", 0f, 60f, () -> c.mobSpawnAlertCooldown, v -> c.mobSpawnAlertCooldown = v, 0);
        b.toggle("    Chat Line", () -> c.mobSpawnAlertChat, v -> c.mobSpawnAlertChat = v);
        b.channel("    Send Coords To", () -> c.mobSpawnAlertCoords, v -> c.mobSpawnAlertCoords = v);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            world = mc.level;
            seen.clear();
        }
        if (++ticks % 5 != 0) return;
        List<String> list = cfg().customESPMobs;
        if (list == null || list.isEmpty()) return;
        List<String> needles = new ArrayList<>(list.size());
        for (String s : list) if (s != null && !s.isBlank()) needles.add(s.toLowerCase());
        if (needles.isEmpty()) return;
        if (seen.size() > 4096) seen.clear();
        for (Entity e : EntityCache.all()) {
            Component cn = e.getCustomName();
            if (cn == null || seen.contains(e.getId())) continue;
            String name = StringUtil.stripTrim(cn.getString());
            String lower = name.toLowerCase();
            for (String n : needles) {
                if (!lower.contains(n)) continue;
                seen.add(e.getId());
                alert(mc, name, e);
                break;
            }
        }
    }

    private static void alert(Minecraft mc, String name, Entity e) {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        if (now - lastAlert < (long) (c.mobSpawnAlertCooldown * 1000)) return;
        lastAlert = now;
        if (!"Sound Only".equals(c.mobSpawnAlertMode)) {
            mc.gui.hud.setTitle(Component.literal("§c" + name));
            mc.gui.hud.setSubtitle(Component.literal("§espawned!"));
            mc.gui.hud.setTimes(5, 40, 10);
        }
        LoudSound.play(SoundOptions.resolve(c.mobSpawnAlertSound), 1f, 1f);
        int x = e.getBlockX(), y = e.getBlockY(), z = e.getBlockZ();
        if (c.mobSpawnAlertChat && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + name + " §7spawned at §e" + x + " " + y + " " + z));
        }
        Announce.send(c.mobSpawnAlertCoords, "x: " + x + ", y: " + y + ", z: " + z + " | " + name);
    }
}
