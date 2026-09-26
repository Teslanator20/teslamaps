package com.teslamaps.features.kuudra;

// Ported from Odin's features/impl/nether/SupplyHelper.kt + utils/skyblock/Supply.kt and Shizo's kuudra/phaseone/SupplyHighlighter.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Phase 1 aid: supply spots from the giants, giant/crate boxes, pre-spawn circles, reachable hitbox, drop-off pads. */
public final class KuudraSupplyHelper {

    // Odin's Supply drop-off spots: Triangle, X, Equals, Slash, Shop, X Cannon
    private static final BlockPos[] DROP_OFFS = {
            new BlockPos(-94, 78, -106), new BlockPos(-106, 78, -112), new BlockPos(-98, 78, -99),
            new BlockPos(-106, 78, -99), new BlockPos(-98, 78, -112), new BlockPos(-110, 78, -106)};
    private static final double STAND_EYE = 1.62, SNEAK_EYE = 1.54, REACH = 3.0;

    private static final boolean[] dropDone = new boolean[DROP_OFFS.length];
    private static List<BlockPos> supplies = List.of();
    private static List<Entity> giants = List.of(), zombies = List.of();
    private static List<ArmorStand> preSpawns = List.of();
    private static int ticks;

    private KuudraSupplyHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraSupplyHelper").section("Kuudra").legit()
                .when(() -> cfg().kuudraSupplyHelper)
                .tick(KuudraSupplyHelper::tick)
                .world(KuudraSupplyHelper::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Supply Helper (Phase 1)");
                    b.toggle("Supply Helper", () -> c.kuudraSupplyHelper, v -> c.kuudraSupplyHelper = v).legit()
                            .desc("Phase 1 visual aid. Pick the parts below; everything shows only while supplies are out.")
                            .tags("supply", "supplies", "giant", "crate", "kuudra");
                    b.toggle("  Supply Waypoints", () -> c.kuudraSupplyWaypoints, v -> c.kuudraSupplyWaypoints = v)
                            .desc("Beacon + box where each supply lies, worked out from the giant's position and rotation, through walls");
                    b.color("  Supply Color", () -> c.kuudraSupplyColor, v -> c.kuudraSupplyColor = v);
                    b.toggle("  Drop-Off Waypoints", () -> c.kuudraSupplyDropOffs, v -> c.kuudraSupplyDropOffs = v)
                            .desc("The 6 pads where supplies are placed, until each one is filled");
                    b.toggle("  Highlight Giants", () -> c.kuudraSupplyGiants, v -> c.kuudraSupplyGiants = v);
                    b.toggle("    Compact Giant Box", () -> c.kuudraSupplyGiantsCompact, v -> c.kuudraSupplyGiantsCompact = v)
                            .desc("Only the top fifth of the giant");
                    b.color("    Giant Color", () -> c.kuudraSupplyGiantColor, v -> c.kuudraSupplyGiantColor = v);
                    b.toggle("  Highlight Crate Zombies", () -> c.kuudraSupplyZombies, v -> c.kuudraSupplyZombies = v)
                            .desc("The crates you can hit (Y 60-78, no armor)");
                    b.color("    Zombie Color", () -> c.kuudraSupplyZombieColor, v -> c.kuudraSupplyZombieColor = v);
                    b.toggle("    Show Reachable Box", () -> c.kuudraSupplyReach, v -> c.kuudraSupplyReach = v)
                            .desc("Only the part of the crate hitbox within 3 blocks of your server eye height (sneak 1.54 / stand 1.62)");
                    b.toggle("  Pre-Spawn Circles", () -> c.kuudraSupplyPreCircles, v -> c.kuudraSupplyPreCircles = v)
                            .desc("Circle around each SUPPLIES marker, turns green when your fishing hook lands inside");
                    KuudraNoPre.config(b);
                    KuudraPileWaypoints.config(b);
                });
    }

    static void reset() {
        java.util.Arrays.fill(dropDone, false);
        supplies = List.of();
        giants = List.of();
        zombies = List.of();
        preSpawns = List.of();
    }

    private static void tick() {
        if (KuudraContext.phase() != KuudraContext.SUPPLIES) {
            if (!supplies.isEmpty() || !giants.isEmpty() || !zombies.isEmpty() || !preSpawns.isEmpty()) reset();
            return;
        }
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        List<BlockPos> sup = new ArrayList<>();
        List<Entity> gi = new ArrayList<>(), zo = new ArrayList<>();
        List<ArmorStand> pre = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Giant g) {
                if (c.kuudraSupplyWaypoints && StringUtil.strip(g.getMainHandItem().getHoverName().getString()).endsWith("Head")) {
                    double a = Math.toRadians(g.getYRot() + 130);
                    sup.add(BlockPos.containing(g.getX() + 3.7 * Math.cos(a), 73, g.getZ() + 3.7 * Math.sin(a)));
                }
                if (c.kuudraSupplyGiants && crate(g)) gi.add(g);
            } else if (e instanceof Zombie z && !(e instanceof ZombifiedPiglin)) {
                if (c.kuudraSupplyZombies && crate(z)) zo.add(z);
            } else if (e instanceof ArmorStand as && as.hasCustomName()) {
                String name = StringUtil.stripTrim(as.getName().getString());
                if (c.kuudraSupplyPreCircles && name.equalsIgnoreCase("SUPPLIES") && as.getY() >= 65 && as.getY() <= 85) pre.add(as);
                else if (name.equals("✓ SUPPLIES RECEIVED ✓")) {
                    int x = (int) as.getX(), z = (int) as.getZ();
                    for (int i = 0; i < DROP_OFFS.length; i++) {
                        if (DROP_OFFS[i].getX() == x && DROP_OFFS[i].getZ() == z) dropDone[i] = true;
                    }
                }
            }
        }
        supplies = sup;
        giants = gi;
        zombies = zo;
        preSpawns = pre;
    }

    // Shizo's isValidKuudraEntity: alive, Y 60-78, no armor
    private static boolean crate(LivingEntity e) {
        if (!e.isAlive() || e.getY() < 60 || e.getY() > 78) return false;
        return e.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && e.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && e.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && e.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (KuudraContext.phase() != KuudraContext.SUPPLIES) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        if (c.kuudraSupplyWaypoints) {
            int col = TeslaMapsConfig.parseColor(c.kuudraSupplyColor);
            for (int i = 0, n = supplies.size(); i < n; i++) WaypointSet.draw(pose, cam, supplies.get(i), col, "§ePick Up!", true, true, true);
        }
        if (c.kuudraSupplyDropOffs) {
            for (int i = 0; i < DROP_OFFS.length; i++) {
                if (!dropDone[i]) WaypointSet.draw(pose, cam, DROP_OFFS[i], 0xFFFF5555, "§ePlace Here!", true, true, true);
            }
        }
        if (c.kuudraSupplyGiants) {
            int col = TeslaMapsConfig.parseColor(c.kuudraSupplyGiantColor);
            for (int i = 0, n = giants.size(); i < n; i++) {
                AABB box = ESPRenderer.lerpBox(giants.get(i));
                if (c.kuudraSupplyGiantsCompact) box = new AABB(box.minX, box.maxY - (box.maxY - box.minY) * 0.2, box.minZ, box.maxX, box.maxY, box.maxZ);
                ESPRenderer.drawBoxOutline(pose, box, col | 0xFF000000, 2f, cam, true);
            }
        }
        if (c.kuudraSupplyZombies) {
            int col = TeslaMapsConfig.parseColor(c.kuudraSupplyZombieColor);
            Vec3 eye = c.kuudraSupplyReach && mc.player != null
                    ? new Vec3(mc.player.getX(), mc.player.getY() + (mc.player.isCrouching() ? SNEAK_EYE : STAND_EYE), mc.player.getZ()) : null;
            for (int i = 0, n = zombies.size(); i < n; i++) {
                Entity z = zombies.get(i);
                ESPRenderer.drawBoxOutline(pose, ESPRenderer.lerpBox(z), col | 0xFF000000, 2f, cam, true);
                if (eye != null) {
                    AABB reach = reachable(z.getBoundingBox(), eye);
                    if (reach != null) ESPRenderer.drawFilledBox(pose, reach, 0x6600FFFF, cam, true);
                }
            }
        }
        if (c.kuudraSupplyPreCircles) {
            FishingHook hook = mc.player != null ? mc.player.fishing : null;
            for (int i = 0, n = preSpawns.size(); i < n; i++) {
                ArmorStand s = preSpawns.get(i);
                boolean in = hook != null && Math.hypot(hook.getX() - s.getX(), hook.getZ() - s.getZ()) <= 4.0;
                ESPRenderer.drawCircle(pose, s.getX(), 75.1, s.getZ(), 4.0, in ? 0xFF55FF55 : 0xFFFF5555, 3f, cam, true);
            }
        }
    }

    private static AABB reachable(AABB e, Vec3 eye) {
        double cx = Math.max(e.minX, Math.min(eye.x, e.maxX));
        double cy = Math.max(e.minY, Math.min(eye.y, e.maxY));
        double cz = Math.max(e.minZ, Math.min(eye.z, e.maxZ));
        if (eye.distanceToSqr(cx, cy, cz) > REACH * REACH) return null;
        double minX = Math.max(e.minX, eye.x - REACH), minY = Math.max(e.minY, eye.y - REACH), minZ = Math.max(e.minZ, eye.z - REACH);
        double maxX = Math.min(e.maxX, eye.x + REACH), maxY = Math.min(e.maxY, eye.y + REACH), maxZ = Math.min(e.maxZ, eye.z + REACH);
        return minX < maxX && minY < maxY && minZ < maxZ ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }
}
