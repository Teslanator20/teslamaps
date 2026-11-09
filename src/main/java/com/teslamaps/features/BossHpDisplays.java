package com.teslamaps.features;

// Concept from Skytils' BossHPDisplays.kt (GuardianRespawnTimer, GiantHPElement)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.registry.HudEditElement.HudCtx;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BossHpDisplays {

    private BossHpDisplays() {}

    private static final String[] GUARDIANS = {"Healthy", "Reinforced", "Chaos", "Laser"};
    private static final String[] GIANTS = {"Sadan", "Diamond Giant", "Bigfoot", "L.A.S.R.", "Jolly Pink Giant", "Mutant Giant"};
    private static final List<DungeonTimers.Row> GIANT_SAMPLE = List.of(
            new DungeonTimers.Row("§c§lSadan §a40M§c❤", null),
            new DungeonTimers.Row("§3Diamond Giant §a25M§c❤", null),
            new DungeonTimers.Row("§3Bigfoot §e12M§c❤", null));

    private record Label(String text, Vec3 pos) {}

    private static List<DungeonTimers.Row> giantRows = List.of();
    private static List<Label> giantLabels = List.of();
    private static int guardianShown = 0;
    private static int guardianTicks = 0, giantTicks = 0;
    private static HudEditElement giantHud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GuardianRespawnTimer").section("Timers").when(() -> cfg().guardianRespawnTimer)
                .tick(BossHpDisplays::tickGuardians);
        Feature.of("GuardianRespawnTimer.off").when(() -> guardianShown > 0 && !cfg().guardianRespawnTimer)
                .tick(() -> setGuardians(List.of()));

        giantHud = HudEditElement.of("giantHp", "Giant HP (F6)").ctx(HudCtx.DUNGEON).section("Timers")
                .enabled(() -> cfg().giantHpDisplay)
                .size(() -> Minecraft.getInstance().font.width("§3Diamond Giant §a25M§c❤"), () -> 31)
                .scaleRange(0.5f, 4.0f)
                .preview((g, mc, x, y, s) -> DungeonTimers.drawRows(g, mc, x, y, s, GIANT_SAMPLE));
        Feature.of("GiantHp").section("Timers").when(() -> cfg().giantHpDisplay || cfg().giantHpFeetLabel)
                .tick(BossHpDisplays::tickGiants)
                .hud("gianthp", BossHpDisplays::renderGiants)
                .world(BossHpDisplays::renderGiantLabels);

        Feature.of("BossHpDisplays").config("Timers", b -> {
            TeslaMapsConfig c = b.config();
            b.label("Floor Bosses");
            b.toggle("Guardian Respawn Timer (F3)", () -> c.guardianRespawnTimer, v -> c.guardianRespawnTimer = v)
                    .desc("Respawn timers of dead guardians in the F3 boss, shown in the timer HUD.");
            b.toggle("Giant HP (F6)", () -> c.giantHpDisplay, v -> c.giantHpDisplay = v)
                    .desc("Nametags of Sadan and the giants as a HUD list (/tmap gui).");
            b.toggle("  Giant HP at Feet", () -> c.giantHpFeetLabel, v -> c.giantHpFeetLabel = v)
                    .desc("Repeats each giant's nametag 10 blocks lower, near its feet.");
        });
    }

    private static boolean onFloor(int level) {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == level && DungeonManager.isInBossArea();
    }

    private static void tickGuardians() {
        if (++guardianTicks % 5 != 0) return;
        if (!onFloor(3)) { setGuardians(List.of()); return; }
        Minecraft mc = Minecraft.getInstance();
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand a && a.hasCustomName()) stands.add(a);
        }
        List<String> rows = new ArrayList<>();
        for (ArmorStand a : stands) {
            String name = StringUtil.strip(a.getCustomName().getString());
            if (name.indexOf('☠') < 0) continue;
            String time = name.replace("☠", "").trim();
            String type = guardianType(name);
            if (type != null) time = time.replace(type, "").replace("Guardian", "").trim();
            if (time.isEmpty() || !hasDigit(time)) continue;
            if (type == null) {
                double best = 9.0;
                for (ArmorStand o : stands) {
                    if (o == a) continue;
                    double d = o.distanceToSqr(a);
                    if (d > best) continue;
                    String t = guardianType(StringUtil.strip(o.getCustomName().getString()));
                    if (t != null) { type = t; best = d; }
                }
            }
            rows.add("§c" + (type != null ? type : "Guardian") + "§f: §e" + time);
            if (rows.size() >= 4) break;
        }
        setGuardians(rows);
    }

    private static String guardianType(String name) {
        if (!name.contains("Guardian")) return null;
        for (String g : GUARDIANS) if (name.contains(g)) return g;
        return null;
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return true;
        return false;
    }

    private static void setGuardians(List<String> rows) {
        for (int i = 0; i < 4; i++) {
            if (i < rows.size()) DungeonTimers.set("guardian" + i, rows.get(i));
            else DungeonTimers.clear("guardian" + i);
        }
        guardianShown = rows.size();
    }

    private static void tickGiants() {
        if (++giantTicks % 5 != 0) return;
        if (!onFloor(6)) { giantRows = List.of(); giantLabels = List.of(); return; }
        Minecraft mc = Minecraft.getInstance();
        List<DungeonTimers.Row> rows = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand a) || !a.hasCustomName()) continue;
            String raw = a.getCustomName().getString();
            String name = StringUtil.strip(raw);
            if (name.indexOf('❤') < 0 || !isGiant(name)) continue;
            rows.add(new DungeonTimers.Row(raw, null));
            labels.add(new Label(raw, a.position().add(0, -10, 0)));
        }
        giantRows = rows;
        giantLabels = labels;
    }

    private static boolean isGiant(String name) {
        for (String g : GIANTS) if (name.contains(g)) return true;
        return false;
    }

    private static void renderGiants(GuiGraphicsExtractor g, DeltaTracker d) {
        List<DungeonTimers.Row> rows = giantRows;
        if (rows.isEmpty() || !cfg().giantHpDisplay || giantHud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        DungeonTimers.drawRows(g, mc, giantHud.x(), giantHud.y(), giantHud.scale(), rows);
    }

    private static void renderGiantLabels(PoseStack pose, Vec3 cam) {
        List<Label> labels = giantLabels;
        if (labels.isEmpty() || !cfg().giantHpFeetLabel) return;
        for (Label l : labels) ESPRenderer.drawText(pose, l.text, l.pos, 1.0f, cam);
    }
}
