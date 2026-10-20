package com.teslamaps.adm.leap.splits;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.leap.AdmWorld;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Ported from ADM's storm/StormCrushView.kt
public final class StormCrushView {

    public static final double RANGE = 5.0;

    private final AABB arena = new AABB(0.0, 150.0, 0.0, 140.0, 215.0, 145.0);
    private final Map<StormCrush.Pillar, Integer> lastStep = new HashMap<>();

    private record Snapshot(AABB box, StormCrush.Verdict verdict, Integer bottom, boolean armed, long at) {}
    private final List<Snapshot> snapshots = new ArrayList<>();

    public record Check(StormCrush.Verdict verdict, boolean armed) {}

    public void reset() { lastStep.clear(); snapshots.clear(); }

    public boolean inArena() {
        var p = Minecraft.getInstance().player;
        return p != null && arena.contains(p.position());
    }

    public void onStep(StormCrush.Pillar pillar, int tick) { lastStep.put(pillar, tick); }

    public Check check(int now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        WitherBoss storm = null;
        double best = Double.MAX_VALUE;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof WitherBoss w) || !w.isAlive() || !arena.contains(w.position()) || !AdmWorld.isBossWither(w)) continue;
            double d = w.distanceToSqr(70.0, 180.0, 53.0);
            if (d < best) { best = d; storm = w; }
        }
        if (storm == null) return null;
        Vec3 at = storm.getPositionCodec().getBase();
        if (at == null || at.equals(Vec3.ZERO)) at = storm.position();
        StormCrush.Pillar pillar = StormCrush.nearest(at.x, at.z);
        if (StormCrush.distance(pillar, at.x, at.z) > RANGE) return null;
        Integer bottom = bottomOf(level, pillar);
        StormCrush.Verdict verdict = StormCrush.judge(pillar, at.x, at.y, at.z, bottom);
        boolean armed = StormCrush.armed(lastStep.get(pillar), now);
        AABB box = new AABB(at.x, at.y, at.z, at.x + 1, at.y + StormCrush.HEAD, at.z + 1);
        snapshots.add(new Snapshot(box, verdict, bottom, armed, System.currentTimeMillis()));
        return new Check(verdict, armed);
    }

    public void render(PoseStack pose, Vec3 cam, int seconds, boolean margins, boolean outline, int insideColor, int outsideColor) {
        if (snapshots.isEmpty()) return;
        long now = System.currentTimeMillis();
        long life = seconds * 1000L;
        snapshots.removeIf(s -> now - s.at > life);
        if (!inArena()) return;
        for (Snapshot s : snapshots) {
            float fade = Math.max(0f, Math.min(1f, (life - (now - s.at)) / 1000f));
            int colour = s.verdict.inside() ? insideColor : outsideColor;
            float a = ((colour >>> 24) / 255f) * fade;
            int fill = ((int) (a * 255) << 24) | (colour & 0xFFFFFF);
            int line = ((int) (fade * 255) << 24) | (colour & 0xFFFFFF);
            ESPRenderer.drawFilledBox(pose, s.box, fill, cam, true);
            ESPRenderer.drawBoxOutline(pose, s.box, line, 2f, cam, true);
            StormCrush.Pillar p = s.verdict.pillar();
            if (outline && s.bottom != null) {
                double y = s.bottom;
                AABB square = new AABB(p.minX(), y, p.minZ(), p.minX() + StormCrush.PILLAR, y + 0.02, p.minZ() + StormCrush.PILLAR);
                ESPRenderer.drawBoxOutline(pose, square, ((int) (0.6f * fade * 255) << 24) | (colour & 0xFFFFFF), 2f, cam, true);
            }
            if (margins) ESPRenderer.drawText(pose, label(s), new Vec3((s.box.minX + s.box.maxX) / 2, s.box.maxY + 0.5, (s.box.minZ + s.box.maxZ) / 2), 1f, cam, false, true);
        }
    }

    private static Integer bottomOf(ClientLevel level, StormCrush.Pillar p) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(p.columnX(), StormCrush.TOP, p.columnZ());
        if (level.getBlockState(pos).isAir()) return null;
        int y = StormCrush.TOP;
        while (y > StormCrush.FLOOR && !level.getBlockState(pos.setY(y - 1)).isAir()) y--;
        return y;
    }

    private static String label(Snapshot s) {
        StormCrush.Verdict v = s.verdict;
        String side = (v.inset() >= 0 ? "§a" : "§c") + StormDetail.fmt(Math.abs(v.inset())) + (v.inset() >= 0 ? " in" : " out");
        String head = v.head() == null ? "§7no pillar"
                : (v.head() >= 0 ? "§a" : "§c") + "head " + (v.head() >= 0 ? "+" : "-") + StormDetail.fmt(Math.abs(v.head()));
        return v.pillar().colour() + v.pillar().name() + " §7· " + side + " §7· " + head + (s.armed ? "" : " §8(not lowered)");
    }
}
