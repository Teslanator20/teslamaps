package com.teslamaps.slayer;

// Ported from Skyblocker's BeaconHighlighter, ping on land from Skytils' yangGlyphPing
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class VoidgloomBeacon {

    private static final long BEACON_MS = 5000L;
    private static final int RED = 0xFFFF0000;
    private static final int FILL = 0x99FF0000;
    private static final int MAX_POINTS = 256;
    private static final float[] WIDTHS = new float[MAX_POINTS];
    static { java.util.Arrays.fill(WIDTHS, 3f); }

    private static final class Beacon {
        final BlockPos pos;
        final long placedMs;
        String label = "";
        Beacon(BlockPos pos, long placedMs) { this.pos = pos; this.placedMs = placedMs; }
    }

    private static final Map<Integer, List<Vec3>> paths = new HashMap<>();
    private static final Set<Integer> seenStands = new HashSet<>();
    private static final List<Beacon> beacons = new ArrayList<>();
    private static boolean beaconThrown;
    private static long thrownMs;
    private static boolean wasCarrying;
    private static Object lastLevel;

    private VoidgloomBeacon() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean highlight() { return cfg().voidgloomBeaconHighlight; }

    public static void register() {
        Feature.of("VoidgloomBeacon").section("Combat")
                .when(() -> cfg().voidgloomYangGlyphAlert || cfg().voidgloomBeaconHighlight)
                .tick(VoidgloomBeacon::tick)
                .world(VoidgloomBeacon::render)
                .serverChat(VoidgloomBeacon::onChat)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Voidgloom");
                    b.toggle("Yang Glyph Alert", () -> c.voidgloomYangGlyphAlert, v -> c.voidgloomYangGlyphAlert = v)
                            .desc("Title \"Yang Glyph!\" with a pling the moment your Voidgloom throws its beacon.")
                            .tags("voidgloom", "enderman", "beacon", "yang", "glyph", "slayer");
                    b.toggle("Beacon Highlight", () -> c.voidgloomBeaconHighlight, v -> c.voidgloomBeaconHighlight = v)
                            .desc("Red line along the flying beacon, the landed beacon filled red through walls "
                                    + "with a countdown to the explosion.")
                            .tags("voidgloom", "enderman", "beacon", "yang", "glyph", "slayer");
                    b.toggle("  Ping on Land", () -> c.voidgloomBeaconPing, v -> c.voidgloomBeaconPing = v)
                            .desc("Plays a sound when the beacon lands.");
                });
    }

    private static boolean fighting() {
        return SlayerQuest.bossActive() && "Voidgloom Seraph".equals(SlayerQuest.boss());
    }

    private static void reset() {
        beaconThrown = false;
        wasCarrying = false;
        paths.clear();
        seenStands.clear();
        beacons.clear();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!fighting()) {
            if (!paths.isEmpty() || !beacons.isEmpty() || beaconThrown) reset();
            return;
        }

        Entity boss = SlayerHUD.ownBoss() ? SlayerHUD.getBossEntity() : null;
        boolean carrying = boss instanceof EnderMan e && e.getCarriedBlock() != null && e.getCarriedBlock().is(Blocks.BEACON);
        if (boss != null) {
            if (wasCarrying && !carrying) onThrow(mc);
            wasCarrying = carrying;
        }
        if (beaconThrown && System.currentTimeMillis() - thrownMs > 8000) beaconThrown = false;

        if (!highlight()) return;
        if (beaconThrown) {
            AABB box = mc.player.getBoundingBox().inflate(40, 20, 40);
            for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, box,
                    a -> a.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON))) {
                if (seenStands.add(s.getId())) paths.computeIfAbsent(s.getId(), k -> new ArrayList<>()).add(s.getEyePosition());
            }
        }
        Iterator<Map.Entry<Integer, List<Vec3>>> it = paths.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Vec3>> en = it.next();
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !e.isAlive()) {
                if (!beaconThrown && beacons.isEmpty()) it.remove();
                continue;
            }
            Vec3 p = e.getEyePosition();
            List<Vec3> path = en.getValue();
            if (path.size() < MAX_POINTS && !path.getLast().equals(p)) path.add(p);
        }

        long now = System.currentTimeMillis();
        beacons.removeIf(b -> now - b.placedMs > 15000);
        for (Beacon bc : beacons) {
            float left = (BEACON_MS - (now - bc.placedMs)) / 1000f;
            bc.label = left >= 0 ? String.format("%.1fs", left) : "";
        }
    }

    private static void onThrow(Minecraft mc) {
        beaconThrown = true;
        thrownMs = System.currentTimeMillis();
        if (!cfg().voidgloomYangGlyphAlert) return;
        mc.gui.setTimes(5, 20, 10);
        mc.gui.setTitle(Component.literal("§cYang Glyph!"));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
    }

    public static boolean listening() {
        return highlight() && cfg().section("Combat") && fighting();
    }

    /** From BlockUpdateMixin, client level. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        beacons.removeIf(b -> b.pos.equals(pos));
        if (newState.is(Blocks.BEACON)) {
            beacons.add(new Beacon(pos.immutable(), System.currentTimeMillis()));
            beaconThrown = false;
            if (cfg().voidgloomBeaconPing) {
                Minecraft.getInstance().execute(() -> LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f));
            }
        } else if (oldState != null && oldState.is(Blocks.BEACON)) {
            Vec3 c = Vec3.atCenterOf(pos);
            Integer closest = null;
            double best = 4;
            for (Map.Entry<Integer, List<Vec3>> en : paths.entrySet()) {
                double d = en.getValue().getLast().distanceToSqr(c);
                if (d < best) { best = d; closest = en.getKey(); }
            }
            if (closest != null) paths.remove(closest);
        }
    }

    private static void onChat(String raw) {
        String t = com.teslamaps.utils.StringUtil.strip(raw);
        if (t.contains("SLAYER QUEST COMPLETE!") || t.contains("NICE! SLAYER BOSS SLAIN!")) reset();
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        if (!highlight() || (paths.isEmpty() && beacons.isEmpty())) return;
        for (List<Vec3> path : paths.values()) {
            ESPRenderer.drawPolyline(matrices, path, WIDTHS, RED, cam, true);
        }
        for (Beacon bc : beacons) {
            AABB box = new AABB(bc.pos);
            ESPRenderer.drawFilledBox(matrices, box, FILL, cam, true);
            ESPRenderer.drawBoxOutline(matrices, box, RED, 2f, cam, true);
            if (!bc.label.isEmpty()) {
                ESPRenderer.drawText(matrices, bc.label, Vec3.atCenterOf(bc.pos.above()), 1.5f, cam, true, true, 0xFF55FFFF);
            }
        }
    }
}
