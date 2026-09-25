package com.teslamaps.features;

// Ported from SkyHanni's PhantomleafSolver.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The basedrum hint's volume is 1 - dist/30: every Y 74 block at that distance gets a beacon. */
public final class PhantomleafSolver {

    private static final int SCALE = 30;
    private static final int Y = 74;
    private static final float MIN_PITCH = 0.61f, MAX_PITCH = 0.62f;

    private static Feature feature;
    private static boolean searching;
    private static Vec3 lastPos;
    private static volatile List<BlockPos> candidates = List.of();
    private static volatile List<AABB> boxes = List.of();
    private static Object lastLevel;

    private PhantomleafSolver() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PhantomleafSolver").section("Garden")
                .when(() -> cfg().phantomleafSolver)
                .tick(PhantomleafSolver::tick)
                .world(PhantomleafSolver::render)
                .serverChat(PhantomleafSolver::onChat)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Greenhouse");
                    b.toggle("Phantomleaf Solver", () -> c.phantomleafSolver, v -> c.phantomleafSolver = v)
                            .desc("Reads the volume of the Phantomleaf hint sound and puts beacons on every block it can hide in. Stand still for a hint, move a bit to narrow it down.")
                            .tags("greenhouse", "mutation", "hide and seek");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
    }

    private static void onChat(String raw) {
        String msg = StringUtil.strip(raw);
        if (msg.contains("Phantomleaf: Poof! Try and find me!")) {
            searching = true;
        } else if (msg.contains("Phantomleaf: You found me!") || msg.contains("Phantomleaf: That's not me! Better luck next time!")) {
            reset();
        }
    }

    private static void reset() {
        searching = false;
        candidates = List.of();
        boxes = List.of();
        lastPos = null;
    }

    /** SoundEngine.play, main thread, after the sound is resolved. */
    public static void onSound(SoundInstance instance, String id, float pitch, float volume) {
        if (!searching || feature == null || !feature.active()) return;
        if (pitch < MIN_PITCH || pitch > MAX_PITCH) return;
        if (!id.equals("minecraft:block.note_block.basedrum")) return;
        if (!SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        Vec3 last = lastPos;
        lastPos = pos;
        if (last == null || last.x != pos.x || last.z != pos.z) return;

        double dist = SCALE * (1.0 - volume);
        List<BlockPos> out = new ArrayList<>();
        double cx = Math.floor(pos.x) + 0.5, cz = Math.floor(pos.z) + 0.5;
        for (int dx = -SCALE - 1; dx <= SCALE + 1; dx++) {
            for (int dz = -SCALE - 1; dz <= SCALE + 1; dz++) {
                double d = Math.hypot(cx + dx - pos.x, cz + dz - pos.z);
                if (Math.abs(d - dist) < 0.001) out.add(BlockPos.containing(cx + dx, Y, cz + dz));
            }
        }
        List<AABB> bx = new ArrayList<>(out.size());
        for (BlockPos p : out) bx.add(new AABB(p));
        boxes = bx;
        candidates = out;
        if (out.isEmpty()) chat("No solutions found. Try moving a little.");
        else if (out.size() > 1) chat("Multiple candidates found. Move a little to recalculate.");
    }

    private static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §e" + s));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!searching) return;
        List<BlockPos> c = candidates;
        List<AABB> bx = boxes;
        if (c.isEmpty() || bx.size() != c.size()) return;
        int color = c.size() > 1 ? 0xFFFFFF55 : 0xFF55FF55;
        for (int i = 0; i < c.size(); i++) {
            ESPRenderer.drawFilledBox(pose, bx.get(i), (color & 0x00FFFFFF) | 0x66000000, cam, true);
            ESPRenderer.drawBeaconBeam(pose, c.get(i), color, cam);
        }
    }
}
