package com.teslamaps.features.crimson;

// Ported from Skyblocker's skyblock/crimson/dojo (DojoManager + the seven test helpers)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.PingMeter;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DojoHelper {

    private enum Test { NONE, FORCE, STAMINA, MASTERY, DISCIPLINE, SWIFTNESS, CONTROL, TENACITY }

    private static final String START = "[NPC] Master Tao: Ahhh, here we go! Let's get you into the Arena.";
    private static final Pattern TEST_OF = Pattern.compile("^Test of (\\w+) OBJECTIVES$");
    private static final int ZOMBIE_LIFE = 10100, BLOCK_LIFE = 6550;
    private static final Map<String, String> SWORD_NAME = Map.of("WOOD_SWORD", "Wood", "IRON_SWORD", "Iron",
            "GOLD_SWORD", "Gold", "DIAMOND_SWORD", "Diamond");
    private static final Map<String, Integer> SWORD_COLOR = Map.of("WOOD_SWORD", 0xA52A2A, "IRON_SWORD", 0xC0C0C0,
            "GOLD_SWORD", 0xFFD700, "DIAMOND_SWORD", 0x00FFFF);

    private static boolean inArena;
    private static Test test = Test.NONE;
    private static Object lastLevel;
    private static int ticks;
    private static final Set<Entity> known = new HashSet<>();

    // force
    private static final Map<Zombie, Long> zombies = new HashMap<>();
    private static Map<Entity, String> forceLabels = Map.of();
    private static Map<BlockPos, String> masteryLabels = Map.of();
    // mastery / swiftness
    private static final List<BlockPos> blockOrder = new ArrayList<>();
    private static final Map<BlockPos, Long> blockEnd = new HashMap<>();
    private static volatile BlockPos lastSwift;
    // control
    private static WitherSkeleton skeleton;
    private static Vec3 lastPos, pingOffset, lastPingOffset;
    private static long lastUpdate;
    // tenacity
    private static final Map<ArmorStand, Vec3> fireballs = new HashMap<>();
    private static final Map<ArmorStand, Vec3> particleOffsets = new HashMap<>();
    // stamina
    private static BlockPos middleBase;
    private static List<AABB> holes = new ArrayList<>(), lastHoles = new ArrayList<>();
    private static Map<AABB, Integer> holeDirs = new HashMap<>();

    private DojoHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DojoHelper").section("Crimson Isle").when(() -> cfg().dojoHelper)
                .tick(DojoHelper::tick)
                .serverChat(DojoHelper::onChat)
                .world(DojoHelper::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Dojo");
                    b.toggle("Dojo Helper", () -> c.dojoHelper, v -> c.dojoHelper = v)
                            .desc("Helps in the Dojo tests, each one can be switched below")
                            .tags("dojo", "belt", "crimson", "force", "stamina", "mastery", "discipline", "swiftness", "control", "tenacity");
                    b.toggle("  Force", () -> c.dojoForce, v -> c.dojoForce = v)
                            .desc("Lifetime above each zombie, minus zombies glow red");
                    b.toggle("  Stamina", () -> c.dojoStamina, v -> c.dojoStamina = v)
                            .desc("Wall holes: green = coming towards you, orange = moving away");
                    b.toggle("  Mastery", () -> c.dojoMastery, v -> c.dojoMastery = v)
                            .desc("Time left per lime wool (ping and arrow flight taken off) and a line to the next one");
                    b.toggle("  Discipline", () -> c.dojoDiscipline, v -> c.dojoDiscipline = v)
                            .desc("Only the zombies that match your held sword glow");
                    b.toggle("  Swiftness", () -> c.dojoSwiftness, v -> c.dojoSwiftness = v)
                            .desc("Marks the newest block");
                    b.toggle("  Control", () -> c.dojoControl, v -> c.dojoControl = v)
                            .desc("Box where to aim ahead of the Wither Skeleton, green when you look at it");
                    b.toggle("  Tenacity", () -> c.dojoTenacity, v -> c.dojoTenacity = v)
                            .desc("Fireball path and the block it will hit");
                });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && inArena && test == Test.FORCE && entity instanceof Zombie z && zombies.containsKey(z)) {
                zombies.put(z, System.currentTimeMillis() + ZOMBIE_LIFE);
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean enabled(Test t) {
        TeslaMapsConfig c = cfg();
        return switch (t) {
            case FORCE -> c.dojoForce;
            case STAMINA -> c.dojoStamina;
            case MASTERY -> c.dojoMastery;
            case DISCIPLINE -> c.dojoDiscipline;
            case SWIFTNESS -> c.dojoSwiftness;
            case CONTROL -> c.dojoControl;
            case TENACITY -> c.dojoTenacity;
            default -> false;
        };
    }

    private static void reset() {
        inArena = false;
        test = Test.NONE;
        known.clear();
        zombies.clear();
        forceLabels = Map.of();
        masteryLabels = Map.of();
        blockOrder.clear();
        blockEnd.clear();
        lastSwift = null;
        skeleton = null;
        lastPos = pingOffset = lastPingOffset = null;
        fireballs.clear();
        particleOffsets.clear();
        middleBase = null;
        holes = new ArrayList<>();
        lastHoles = new ArrayList<>();
        holeDirs = new HashMap<>();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals(START)) {
            reset();
            inArena = Crimson.here();
            if (inArena) PingMeter.requestSilent();
            return;
        }
        if (!inArena) return;
        if (t.equals("CHALLENGE COMPLETED") || t.equals("CHALLENGE FAILED")) {
            reset();
            return;
        }
        if (test != Test.NONE) return;
        Matcher m = TEST_OF.matcher(t);
        if (!m.matches()) return;
        try {
            test = Test.valueOf(m.group(1).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            test = Test.NONE;
        }
        if (!enabled(test)) test = Test.NONE;
    }

    /** From BlockUpdateMixin, main thread. */
    public static boolean listening() {
        return inArena && (test == Test.MASTERY || test == Test.SWIFTNESS);
    }

    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !listening()) return;
        BlockPos p = pos.immutable();
        if (test == Test.SWIFTNESS) {
            if (state.is(Blocks.LIME_WOOL)) lastSwift = p;
            return;
        }
        if (state.is(Blocks.LIME_WOOL)) {
            blockOrder.remove(p);
            blockOrder.add(p);
            long travel = (long) (mc.player.position().distanceTo(Vec3.atCenterOf(p)) * 1000 / 60);
            blockEnd.put(p, System.currentTimeMillis() + BLOCK_LIFE - (long) PingMeter.medianPingMs() - travel);
        } else if (state.isAir()) {
            blockOrder.remove(p);
            blockEnd.remove(p);
        }
    }

    /** From the particle packet hook; only the main-thread call counts. */
    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!inArena || test != Test.TENACITY || !Minecraft.getInstance().isSameThread()) return;
        if (packet.getParticle().getType() != ParticleTypes.FLAME) return;
        Vec3 pp = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        ArmorStand nearest = null;
        double best = 50;
        for (ArmorStand f : fireballs.keySet()) {
            double d = f.position().distanceTo(pp);
            if (d < best) {
                best = d;
                nearest = f;
            }
        }
        if (nearest != null) particleOffsets.put(nearest, pp.subtract(nearest.position()));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!inArena || test == Test.NONE) return;
        if (++ticks % 40 == 0 && !Crimson.here()) {
            reset();
            return;
        }
        trackEntities(mc);
        long now = System.currentTimeMillis();
        boolean glow = !com.teslamaps.features.LegitMode.blocksCheats();
        switch (test) {
            case FORCE -> {
                Map<Entity, String> labels = new HashMap<>();
                for (Map.Entry<Zombie, Long> e : zombies.entrySet()) {
                    float s = Math.max((e.getValue() - now) / 1000f, 0);
                    labels.put(e.getKey(), (s > 1 ? "§a" : s > 0 ? "§e" : "§c") + String.format(java.util.Locale.ROOT, "%.1f", s));
                }
                forceLabels = labels;
                for (Zombie z : zombies.keySet()) {
                    ArmorStand tag = NametagMobs.tagForMob(z);
                    String name = tag == null ? null : NametagMobs.tagName(tag);
                    if (glow && name != null && name.contains("-")) EntityGlow.mark(z, 0xFF0000);
                }
            }
            case DISCIPLINE -> {
                String held = ItemUtil.skyblockId(mc.player.getMainHandItem());
                String want = held == null ? null : SWORD_NAME.get(held);
                if (want == null) break;
                int color = SWORD_COLOR.get(held);
                for (Entity e : known) {
                    if (!(e instanceof Zombie z) || z.isRemoved()) continue;
                    ArmorStand tag = NametagMobs.tagForMob(z);
                    String name = tag == null ? null : NametagMobs.tagName(tag);
                    if (glow && name != null && name.endsWith(" " + want)) EntityGlow.mark(z, color);
                }
            }
            case MASTERY -> {
                Map<BlockPos, String> labels = new HashMap<>();
                for (BlockPos p : blockOrder) {
                    float s = Math.max((blockEnd.getOrDefault(p, now) - now) / 1000f, 0);
                    labels.put(p, (s > 1 ? "§a" : "§c") + String.format(java.util.Locale.ROOT, "%.2f", s));
                }
                masteryLabels = labels;
            }
            case STAMINA -> { if (ticks % 3 == 0) staminaUpdate(mc); }
            case CONTROL -> { if (ticks % 3 == 0) controlUpdate(); }
            default -> {}
        }
    }

    private static void trackEntities(Minecraft mc) {
        Set<Entity> now = new HashSet<>();
        Vec3 p = mc.player.position();
        for (Entity e : EntityCache.all()) {
            if (e == mc.player || e.isRemoved()) continue;
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz > 2500 || Math.abs(e.getY() - p.y) > 5 && !known.contains(e)) continue;
            now.add(e);
            if (!known.contains(e)) onSpawn(e);
        }
        for (Entity e : known) if (!now.contains(e)) onDespawn(e);
        known.clear();
        known.addAll(now);
    }

    private static void onSpawn(Entity e) {
        switch (test) {
            case FORCE -> { if (e instanceof Zombie z) zombies.put(z, System.currentTimeMillis() + ZOMBIE_LIFE); }
            case CONTROL -> { if (e instanceof WitherSkeleton w && skeleton == null) skeleton = w; }
            case TENACITY -> { if (e instanceof ArmorStand as) fireballs.put(as, as.position()); }
            default -> {}
        }
    }

    private static void onDespawn(Entity e) {
        if (e instanceof Zombie z) zombies.remove(z);
        if (e instanceof ArmorStand as) {
            fireballs.remove(as);
            particleOffsets.remove(as);
        }
    }

    // --- control ---

    private static void controlUpdate() {
        if (skeleton == null) return;
        if (lastPos != null) {
            lastPingOffset = pingOffset;
            double ping = PingMeter.medianPingMs() / 1000d;
            Vec3 move = skeleton.position().subtract(lastPos).multiply(1, 0.1, 1);
            pingOffset = move.scale(1 + 3 / 20d + ping);
        }
        lastPos = skeleton.position();
        lastUpdate = System.currentTimeMillis();
    }

    // --- stamina ---

    private static void staminaUpdate(Minecraft mc) {
        List<BlockPos> bottoms = findWallBlocks(mc);
        if (bottoms == null) return;
        List<AABB> walls = findWalls(bottoms);
        lastHoles = holes;
        holes = new ArrayList<>();
        for (AABB w : walls) holes.addAll(findHoles(mc, w));
        Map<AABB, Integer> old = holeDirs;
        holeDirs = new HashMap<>();
        for (AABB h : holes) {
            int d = direction(h);
            holeDirs.put(h, d == -1 ? old.getOrDefault(h, 4) : d);
        }
    }

    private static List<BlockPos> findWallBlocks(Minecraft mc) {
        BlockPos pp = mc.player.blockPosition();
        if (middleBase == null) {
            for (int x = pp.getX() - 10; x < pp.getX() + 10; x++)
                for (int y = pp.getY() - 5; y < pp.getY(); y++)
                    for (int z = pp.getZ() - 10; z < pp.getZ() + 10; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (mc.level.getBlockState(pos).is(Blocks.CHISELED_STONE_BRICKS)) {
                            middleBase = pos;
                            return null;
                        }
                    }
            return null;
        }
        List<BlockPos> out = new ArrayList<>();
        for (int x = middleBase.getX() - 15; x < middleBase.getX() + 15; x++)
            for (int z = middleBase.getZ() - 15; z < middleBase.getZ() + 15; z++) {
                BlockPos pos = new BlockPos(x, middleBase.getY() + 1, z);
                if (!mc.level.getBlockState(pos).isAir()) out.add(pos);
            }
        return out;
    }

    private static List<AABB> findWalls(List<BlockPos> bottoms) {
        Map<Integer, List<BlockPos>> byX = new HashMap<>(), byZ = new HashMap<>();
        for (BlockPos b : bottoms) {
            byX.computeIfAbsent(b.getX(), k -> new ArrayList<>()).add(b);
            byZ.computeIfAbsent(b.getZ(), k -> new ArrayList<>()).add(b);
        }
        List<List<BlockPos>> lines = new ArrayList<>();
        for (List<BlockPos> l : byX.values()) if (l.size() >= 13) lines.add(l);
        for (List<BlockPos> l : byZ.values()) if (l.size() >= 13) lines.add(l);
        List<AABB> out = new ArrayList<>();
        for (List<BlockPos> wall : lines) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int y = wall.get(0).getY();
            for (BlockPos p : wall) {
                minX = Math.min(minX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxZ = Math.max(maxZ, p.getZ());
            }
            out.add(AABB.encapsulatingFullBlocks(new BlockPos(minX, y, minZ), new BlockPos(maxX, y + 5, maxZ)));
        }
        return out;
    }

    private static List<AABB> findHoles(Minecraft mc, AABB box) {
        List<AABB> out = new ArrayList<>();
        Vec3i dir = box.getXsize() == 1 ? new Vec3i(0, 0, 1) : new Vec3i(1, 0, 0);
        List<BlockPos> topLeft = new ArrayList<>(), topRight = new ArrayList<>(), bottomLeft = new ArrayList<>();
        for (int z = (int) box.minZ; z < box.maxZ; z++)
            for (int x = (int) box.minX; x < box.maxX; x++)
                for (int y = (int) box.minY; y < box.maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.level.getBlockState(pos).isAir()) continue;
                    boolean top = y == box.maxY - 1 || !mc.level.getBlockState(pos.above()).isAir();
                    boolean bottom = !mc.level.getBlockState(pos.below()).isAir();
                    boolean left = !mc.level.getBlockState(pos.offset(dir)).isAir();
                    boolean right = !mc.level.getBlockState(pos.subtract(dir)).isAir();
                    if (top && left) topLeft.add(pos);
                    if (top && right) topRight.add(pos);
                    if (bottom && left) bottomLeft.add(pos);
                }
        for (int i = 0; i < topLeft.size() && i < topRight.size() && i < bottomLeft.size(); i++) {
            AABB hole = AABB.encapsulatingFullBlocks(topLeft.get(i), topRight.get(i));
            out.add(hole.expandTowards(0, bottomLeft.get(i).getY() - topLeft.get(i).getY(), 0));
        }
        return out;
    }

    /** 0 +x, 1 +z, 2 -x, 3 -z, 4 new, -1 unchanged. */
    private static int direction(AABB h) {
        if (lastHoles.contains(h)) return -1;
        if (lastHoles.contains(h.move(1, 0, 0))) return 0;
        if (lastHoles.contains(h.move(-1, 0, 0))) return 2;
        if (lastHoles.contains(h.move(0, 0, 1))) return 1;
        if (lastHoles.contains(h.move(0, 0, -1))) return 3;
        return 4;
    }

    private static boolean incoming(AABB h, int dir, BlockPos p) {
        return switch (dir) {
            case 0 -> p.getX() < h.minX;
            case 1 -> p.getZ() < h.minZ;
            case 2 -> p.getX() > h.maxX;
            case 3 -> p.getZ() > h.maxZ;
            default -> true;
        };
    }

    // --- render ---

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inArena || test == Test.NONE) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        switch (test) {
            case FORCE -> {
                for (Map.Entry<Entity, String> e : forceLabels.entrySet()) {
                    if (e.getKey().isRemoved()) continue;
                    ESPRenderer.drawText(pose, e.getValue(), e.getKey().getEyePosition(pt).add(0, 0.6, 0), 1.5f, cam);
                }
            }
            case STAMINA -> {
                BlockPos pp = mc.player.blockPosition();
                for (AABB h : holes) {
                    int color = incoming(h, holeDirs.getOrDefault(h, 4), pp) ? 0x4D00FF00 : 0x4DFFA300;
                    ESPRenderer.drawFilledBox(pose, h, color, cam, false);
                }
            }
            case MASTERY -> {
                if (!blockOrder.isEmpty()) {
                    ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(blockOrder.get(0)), 0xFFC0C0C0, cam);
                    if (blockOrder.size() >= 2) {
                        ESPRenderer.drawLine(pose, Vec3.atCenterOf(blockOrder.get(0)), Vec3.atCenterOf(blockOrder.get(1)), 0xFFC0C0C0, 2f, cam, true);
                    }
                }
                for (Map.Entry<BlockPos, String> e : masteryLabels.entrySet()) {
                    ESPRenderer.drawText(pose, e.getValue(), Vec3.atCenterOf(e.getKey().above()), 3f, cam);
                }
            }
            case SWIFTNESS -> {
                BlockPos b = lastSwift;
                if (b != null) ESPRenderer.drawFilledBox(pose, new AABB(b), 0x8000FF00, cam, true);
            }
            case CONTROL -> {
                if (skeleton == null || pingOffset == null || lastPingOffset == null) break;
                double f = Math.min(1, (double) (now - lastUpdate) / 150);
                Vec3 aim = skeleton.getEyePosition(pt).add(pingOffset.scale(f)).add(lastPingOffset.scale(1 - f));
                AABB box = new AABB(aim.add(-0.5, -0.5, -0.5), aim.add(0.5, 0.5, 0.5));
                Vec3 eye = mc.player.getEyePosition(pt);
                boolean looking = box.clip(eye, eye.add(mc.player.getViewVector(pt).scale(30))).isPresent();
                ESPRenderer.drawBoxOutline(pose, box, looking ? 0xFF00FF00 : 0xFFC0C0C0, 3f, cam, true);
            }
            case TENACITY -> {
                for (Map.Entry<ArmorStand, Vec3> e : fireballs.entrySet()) {
                    Vec3 off = particleOffsets.getOrDefault(e.getKey(), Vec3.ZERO);
                    Vec3 start = e.getValue().add(off), pos = e.getKey().position().add(off);
                    Vec3 d = pos.subtract(start);
                    if (d.length() <= 0.02) continue;
                    Vec3 end = start.add(d.scale(100));
                    ESPRenderer.drawLine(pose, start, end, 0xFFFF0000, 3f, cam, false);
                    HitResult hit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, e.getKey()));
                    if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                        ESPRenderer.drawFilledBox(pose, new AABB(bh.getBlockPos()), 0x80FF0000, cam, false);
                    }
                }
            }
            default -> {}
        }
    }
}
