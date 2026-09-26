package com.teslamaps.features.crimson;

// After SkyHanni's nether/MatriarchHelper.kt (no island graph: straight lines) and SkyblockExtras' daily reminder
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MatriarchHelper {

    private static final String PEARL_TEXTURE = "2dbd4e5d3d9c05a036fb62e6e70faf9e6f98d294f9d006781c144c9f15b87715";
    private static final String AREA = "Belly of the Beast";

    private static List<Slime> pearls = List.of();
    private static List<Vec3> route = List.of();
    private static boolean inArea;
    private static int ticks, collected;
    private static long remindedDay = -1;
    private static Object lastLevel;

    private MatriarchHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MatriarchHelper").section("Crimson Isle").when(() -> cfg().matriarchHelper || cfg().matriarchReminder)
                .tick(MatriarchHelper::tick)
                .world(MatriarchHelper::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Matriarch");
                    b.toggle("Heavy Pearl Helper", () -> c.matriarchHelper, v -> c.matriarchHelper = v)
                            .desc("Belly of the Beast: highlights the Heavy Pearls and draws a line through them")
                            .tags("matriarch", "heavy pearl", "pearls", "crimson", "belly");
                    b.color("  Highlight Color", () -> c.colorMatriarchPearl, v -> c.colorMatriarchPearl = v);
                    b.toggle("  Line", () -> c.matriarchLine, v -> c.matriarchLine = v);
                    b.toggle("  Order By Distance", () -> c.matriarchByDistance, v -> c.matriarchByDistance = v)
                            .desc("Off = lowest pearl first");
                    b.toggle("  Daily Reminder", () -> c.matriarchReminder, v -> c.matriarchReminder = v)
                            .desc("On joining the Crimson Isle: reminder if the pearls were not collected today. "
                                    + "Marked done after 3 pearls or /tmap crimson matriarchdone");
                });
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    public static void markDone() {
        cfg().matriarchDoneDay = today();
        TeslaMapsConfig.save();
        Crimson.say("§aMatriarch pearls marked as done for today.");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            pearls = List.of();
            route = List.of();
        }
        if (++ticks % 10 != 0) return;
        boolean crimson = Crimson.here();
        if (crimson && cfg().matriarchReminder && remindedDay != today() && cfg().matriarchDoneDay != today() && ticks > 100) {
            remindedDay = today();
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §dHave you done your daily Matriarch (Heavy Pearls) quest? ")
                    .append(Component.literal("§a[Done]").withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/tmap crimson matriarchdone")))));
        }
        inArea = crimson && cfg().matriarchHelper && AREA.equals(FishingHelpers.subArea());
        if (!inArea) {
            pearls = List.of();
            route = List.of();
            return;
        }
        List<Slime> found = new ArrayList<>(3);
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.isRemoved() || !PEARL_TEXTURE.equals(SoulweaverHider.headTextureUrl(as.getItemBySlot(EquipmentSlot.HEAD)))) continue;
            for (Slime s : mc.level.getEntitiesOfClass(Slime.class, as.getBoundingBox().inflate(0.5))) {
                if (!found.contains(s)) found.add(s);
            }
        }
        for (Slime old : pearls) {
            if (!found.contains(old) && old.isRemoved() && old.distanceToSqr(mc.player) < 36 && ++collected >= 3) {
                collected = 0;
                if (cfg().matriarchDoneDay != today()) markDone();
            }
        }
        pearls = found;
        route = order(mc, found);
    }

    private static List<Vec3> order(Minecraft mc, List<Slime> found) {
        List<Vec3> pts = new ArrayList<>(found.size());
        for (Slime s : found) pts.add(s.position().add(0, 1.2, 0));
        if (!cfg().matriarchByDistance) {
            pts.sort(Comparator.comparingDouble(v -> v.y));
            return pts;
        }
        List<Vec3> out = new ArrayList<>(pts.size());
        Vec3 cur = mc.player.position();
        while (!pts.isEmpty()) {
            Vec3 best = pts.get(0);
            for (Vec3 p : pts) if (p.distanceToSqr(cur) < best.distanceToSqr(cur)) best = p;
            pts.remove(best);
            out.add(best);
            cur = best;
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inArea || pearls.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().colorMatriarchPearl);
        for (Slime s : pearls) {
            if (s.isRemoved()) continue;
            ESPRenderer.drawFilledBox(pose, s.getBoundingBox().inflate(0.3), color, cam, true);
        }
        if (!cfg().matriarchLine || route.isEmpty()) return;
        int line = color | 0xFF000000;
        ESPRenderer.drawTracerFromCamera(pose, route.get(0), line, cam);
        for (int i = 0; i + 1 < route.size(); i++) ESPRenderer.drawLine(pose, route.get(i), route.get(i + 1), line, 3f, cam, true);
    }
}
