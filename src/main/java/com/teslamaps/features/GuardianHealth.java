package com.teslamaps.features;

// Ported from Skyblocker's GuardianHealth.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GuardianHealth {

    private static final AABB BOSS_ROOM = new AABB(34, 65, -32, -32, 100, 36);
    private static final Pattern GUARDIAN = Pattern.compile("^(.*?) Guardian (.*?)([A-Za-z])❤$");
    private static final Pattern PROFESSOR = Pattern.compile("^﴾ (The Professor) (.*?)([A-Za-z])❤ ﴿$");
    private static final String START = "[BOSS] The Professor: I was burdened with terrible news recently...";

    private record Label(Entity mob, String text) {}

    private static List<Label> labels = List.of();
    private static boolean inBoss;
    private static Object lastLevel;

    private GuardianHealth() {}

    public static void register() {
        Feature.of("GuardianHealth").section("Timers").when(() -> TeslaMapsConfig.get().guardianHealth)
                .serverChat(GuardianHealth::onChat)
                .tick(GuardianHealth::tick)
                .world(GuardianHealth::render);
    }

    private static boolean floor3() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 3 && DungeonManager.isInDungeon();
    }

    private static void onChat(String text) {
        if (!inBoss && floor3() && START.equals(StringUtil.stripTrim(text))) inBoss = true;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inBoss = false;
        }
        if (!inBoss || !floor3() || mc.level == null) {
            labels = List.of();
            return;
        }
        List<Label> out = new ArrayList<>();
        for (Guardian g : mc.level.getEntitiesOfClass(Guardian.class, BOSS_ROOM, e -> true)) {
            for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, g.getBoundingBox().inflate(0, 1, 0), ArmorStand::hasCustomName)) {
                String name = StringUtil.stripTrim(s.getName().getString());
                Matcher m = (name.contains("The Professor") ? PROFESSOR : GUARDIAN).matcher(name);
                if (!m.matches()) continue;
                out.add(new Label(g, "§a" + m.group(2) + m.group(3)));
                break;
            }
        }
        labels = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Label> l = labels;
        if (l.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Label lb : l) {
            if (lb.mob.isRemoved()) continue;
            ESPRenderer.drawText(pose, lb.text, lb.mob.getPosition(pt).add(0, lb.mob.getBbHeight() + 0.5, 0), 2.0f, cam);
        }
    }
}
