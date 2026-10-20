package com.teslamaps.adm.practice;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

// Ported from ADM's OdinSimonSays.kt: ADM's Simon Says solver, standalone. Replaces ours while on.
public final class AdmSimonSays {
    private AdmSimonSays() {}

    private static final SsSolver<BlockPos> solver = new SsSolver<>();
    private static final ConcurrentHashMap<BlockPos, Boolean> lampLit = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Long> buttonTick = new ConcurrentHashMap<>();
    private static final List<BlockPos> GRID = new ArrayList<>();
    static {
        for (int y = 120; y <= 123; y++) for (int z = 92; z <= 95; z++) GRID.add(new BlockPos(110, y, z));
    }
    private static Object lastLevel;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AdmSimonSays").section(Adm.SECTION).when(() -> cfg().admBetterSs || cfg().admSsPractice)
                .tick(AdmSimonSays::tick)
                .world(AdmSimonSays::render)
                .config("ADM Terminals & Practice", b -> {
                    b.label("Simon Says");
                    b.toggle("Better Solver", () -> cfg().admBetterSs, v -> cfg().admBetterSs = v)
                            .desc("ADM's solver: handles skips after a fail, 2-light skips, flashes before the show and lag. Replaces the Simon Says Solver while on; Block Wrong Clicks uses its answer.");
                });
    }

    /** ADM's solver replaces ours (drawing, Block Wrong Clicks). */
    public static boolean overrides() {
        return Adm.overrides(cfg().admBetterSs);
    }

    private static boolean tracking() {
        return Adm.on() && (cfg().admBetterSs || cfg().admSsPractice) && !SsPractice.practicing() && PracticeUtil.inP3();
    }

    private static boolean onDevice(BlockPos pos) {
        return (pos.getX() == 110 || pos.getX() == 111) && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95;
    }

    /** Netty thread, on a block packet's arrival: only bookkeeping, the rest hops to the client thread. */
    public static void onPacket(List<BlockPos> positions, List<BlockState> states) {
        if (!Adm.on() || !(cfg().admBetterSs || cfg().admSsPractice)) return;
        long at = ServerTickTimer.now();
        List<Object[]> lamps = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (!onDevice(pos)) continue;
            if (pos.getX() == 110) { buttonTick.put(pos, at); continue; }
            boolean lit = states.get(i).getBlock() == Blocks.SEA_LANTERN;
            Boolean was = lampLit.put(pos, lit);
            lamps.add(new Object[]{pos, lit, was == null ? !lit : was});
        }
        if (lamps.isEmpty()) return;
        Minecraft.getInstance().execute(() -> {
            if (!tracking()) return;
            PracticeUtil.safely("ss solver lamps", () -> {
                for (Object[] l : lamps) solver.lamp((BlockPos) l[0], (Boolean) l[1], (Boolean) l[2], at);
            });
        });
    }

    /** Client thread, before a block change is applied. */
    public static void onBlockUpdate(BlockPos pos, BlockState old, BlockState updated) {
        if (pos.getX() != 110 || pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95) return;
        if (!tracking()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PracticeUtil.safely("ss solver", () -> {
            long tick = ServerTickTimer.now();
            Long packetTick = buttonTick.get(pos);
            long pt = packetTick != null ? packetTick : tick;
            boolean newButton = updated.getBlock() instanceof ButtonBlock;
            boolean oldButton = old.getBlock() instanceof ButtonBlock;
            if (newButton && !oldButton) {
                int n = 0;
                for (BlockPos p : GRID) if (mc.level.getBlockState(p).getBlock() instanceof ButtonBlock) n++;
                if (n >= 8) solver.buttonsUp(pt);
            } else if (updated.isAir() && !old.isAir()) {
                int n = 0;
                for (BlockPos p : GRID) if (mc.level.getBlockState(p).isAir()) n++;
                if (n > 8) solver.buttonsGone(pt);
            } else if (newButton && oldButton && updated.getValue(BlockStateProperties.POWERED) && !old.getValue(BlockStateProperties.POWERED)) {
                solver.pressed(pos.east(), tick);
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            solver.reset();
            lampLit.clear();
            buttonTick.clear();
        }
        if (!tracking()) return;
        solver.serverTick(ServerTickTimer.now() - 1);
    }

    /** The answer's lamps (x 111) and how many are pressed, or null while there is none. */
    static List<BlockPos> answer() { return solver.answer(); }
    static int next() { return solver.next(); }

    /** Block Wrong Clicks with ADM's answer: true swallows the click. */
    public static boolean blockClick(BlockPos pos) {
        if (pos.getX() != 110 || pos.getY() < 120 || pos.getY() > 123 || pos.getZ() < 92 || pos.getZ() > 95) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isCrouching()) return false;
        if (!PracticeUtil.inP3() || SsPractice.practicing()) return false;
        if (mc.level.getBlockState(pos).getBlock() != Blocks.STONE_BUTTON) return false;
        List<BlockPos> a = solver.answer();
        int i = solver.next();
        if (a.isEmpty() || i >= a.size()) return false;
        return !pos.equals(a.get(i).west());
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!overrides() || !PracticeUtil.inP3() || SsPractice.practicing()) return;
        List<BlockPos> a = solver.answer();
        int next = solver.next();
        for (int i = next; i < a.size(); i++) {
            BlockPos b = a.get(i).west();
            AABB box = new AABB(b.getX() + 0.05, b.getY() + 0.37, b.getZ() + 0.3, b.getX() - 0.15, b.getY() + 0.63, b.getZ() + 0.7);
            drawSolverBox(pose, box, color(i - next), cam);
        }
    }

    static int color(int order) {
        String c = switch (order) {
            case 0 -> cfg().colorSimonSaysFirst;
            case 1 -> cfg().colorSimonSaysSecond;
            default -> cfg().colorSimonSaysThird;
        };
        return TeslaMapsConfig.parseColor(c);
    }

    static void drawSolverBox(PoseStack pose, AABB box, int color, Vec3 cam) {
        ESPRenderer.drawFilledBox(pose, box, color, cam, false);
        ESPRenderer.drawBoxOutline(pose, box, color | 0xFF000000, 2f, cam, false);
    }
}
