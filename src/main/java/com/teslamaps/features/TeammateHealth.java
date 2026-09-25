package com.teslamaps.features;

// SkyblockAddons DungeonPlayer isLow/isCritical + SkyHanni LowHealthAlert: scoreboard heart colour -> warning
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TeammateHealth {

    private static final Pattern TEAMMATE = Pattern.compile(
            "(?:§.)*\\[(?<cls>\\w)] (?:§.)*(?<name>\\w{2,16}) (?:(?:§.)*\\[Lvl?[\\w,.]*]?|(?:§(?<color>.))*(?<health>[\\w,.]+))");

    private record State(String label, int level) {}

    private static final Map<String, State> states = new HashMap<>();
    private static int tickCount = 0;

    private TeammateHealth() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        Feature.of("TeammateHealth").section("ESP").legit()
                .when(() -> cfg().teammateHealth && DungeonManager.isInDungeon())
                .tick(TeammateHealth::tick)
                .world(TeammateHealth::render);
    }

    private static boolean healerOk() {
        return !cfg().teammateHealthOnlyHealer || "Healer".equals(PlayerTracker.getLocalClass());
    }

    private static void tick() {
        if (++tickCount % 5 != 0) return;
        states.clear();
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line.indexOf('[') < 0) continue;
            Matcher m = TEAMMATE.matcher(line);
            if (!m.find() || m.group("health") == null) continue;
            String color = m.group("color");
            String health = m.group("health");
            int level = "DEAD".equals(health) ? 0 : "c".equals(color) ? 2 : "e".equals(color) ? 1 : 0;
            String cls = m.group("cls");
            states.put(m.group("name"), new State(classColor(cls) + "[" + cls + "] §f" + m.group("name"), level));
        }
    }

    private static String classColor(String abbr) {
        return switch (abbr) {
            case "H" -> "§d";
            case "M" -> "§b";
            case "B" -> "§c";
            case "A" -> "§6";
            case "T" -> "§a";
            default -> "§7";
        };
    }

    /** 0 = fine, 1 = low (yellow hearts), 2 = critical (red hearts). */
    public static int level(String name) {
        if (name == null) return 0;
        State s = states.get(name);
        return s == null ? 0 : s.level;
    }

    /** Ring colour for the map head, 0 = none. */
    public static int mapColor(String name) {
        TeslaMapsConfig c = cfg();
        if (!c.teammateHealth || !c.teammateHealthMap || states.isEmpty()) return 0;
        if (!c.section("ESP") || LegitMode.blocksCheats() || !healerOk()) return 0;
        int lvl = level(name);
        return lvl == 2 ? 0xFFFF3333 : lvl == 1 ? 0xFFFFDD33 : 0;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (states.isEmpty() || !healerOk()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        TeslaMapsConfig c = cfg();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            State s = states.get(p.getName().getString());
            if (s == null) continue;
            AABB box = ESPRenderer.lerpBox(p);
            if (c.teammateHealthOutline && s.level > 0) {
                ESPRenderer.drawBoxOutline(pose, box, s.level == 2 ? 0xFFFF3333 : 0xFFFFDD33, 3.0f, cam, true);
            }
            Vec3 top = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.6, (box.minZ + box.maxZ) / 2);
            if (c.teammateHealthLabels) ESPRenderer.drawText(pose, s.label, top, 1.0f, cam, true, true);
            if (s.level > 0) {
                ESPRenderer.drawText(pose, s.level == 2 ? "§c§l⚠ CRITICAL" : "§e§l⚠ LOW",
                        top.add(0, c.teammateHealthLabels ? 0.45 : 0, 0), 1.5f, cam, true, true);
            }
        }
    }
}
