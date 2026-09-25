package com.teslamaps.features.mining;

// Ported from Skyblocker's WishingCompassSolver.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Two Wishing Compass uses 8+ blocks apart, the two particle trails intersected into a waypoint. */
public final class WishingCompassSolver {

    private enum Zone { NUCLEUS, JUNGLE, MITHRIL, GOBLIN, PRECURSOR, MAGMA }
    private enum State { IDLE, FIRST, WAITING, SECOND }

    private static final AABB NUCLEUS = new AABB(462, 63, 461, 564, 181, 565);
    private static final AABB JUNGLE = new AABB(201, 63, 201, 513, 189, 513);
    private static final AABB MITHRIL = new AABB(512, 63, 201, 824, 189, 513);
    private static final AABB GOBLIN = new AABB(201, 63, 512, 513, 189, 824);
    private static final AABB PRECURSOR = new AABB(512, 63, 512, 824, 189, 824);
    private static final AABB MAGMA = new AABB(201, 30, 201, 824, 64, 824);
    private static final Vec3 TEMPLE_DOOR_OFFSET = new Vec3(-57, 36, -21);
    private static final int PARTICLES_PER_LINE = 25;
    private static final long PARTICLES_MAX_DELAY = 500;
    private static final double PARTICLES_MAX_DISTANCE = 0.9;
    private static final double DISTANCE_BETWEEN_USES = 8;
    private static final double DISTANCE_TOLERANCE = 5.0;
    private static final String PREFIX = "§b[TeslaMaps] ";
    private static final String FAILED = "The Wishing Compass can't seem to locate anything!";

    private static final WaypointSet waypoints = new WaypointSet().removeWithin(8);
    private static Feature feature;
    private static State state = State.IDLE;
    private static Vec3 startOne = Vec3.ZERO, startTwo = Vec3.ZERO, dirOne = Vec3.ZERO, dirTwo = Vec3.ZERO;
    private static int countOne, countTwo;
    private static long lastParticle;
    private static Vec3 lastPos = Vec3.ZERO;
    private static Object lastLevel;

    private WishingCompassSolver() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("WishingCompassSolver").section("Mining").when(() -> cfg().wishingCompassSolver)
                .tick(WishingCompassSolver::tick)
                .serverChat(WishingCompassSolver::onChat)
                .world(WishingCompassSolver::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Wishing Compass Solver", () -> c.wishingCompassSolver, v -> c.wishingCompassSolver = v)
                            .desc("Use a Wishing Compass twice, at least 8 blocks apart, in the same Crystal Hollows zone; the two trails are intersected into a waypoint.")
                            .tags("wishing compass", "crystal hollows", "waypoint", "temple", "divan", "odawa", "yolkar");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()) onUse(hand);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) onUse(hand);
            return InteractionResult.PASS;
        });
    }

    private static boolean on() {
        return feature != null && feature.active() && SkyblockUtils.isInArea("Crystal Hollows");
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + text));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            waypoints.clear();
        }
        waypoints.tick();
    }

    private static void onChat(String raw) {
        if (state != State.IDLE && StringUtil.stripTrim(raw).equals(FAILED)) reset();
    }

    private static void reset() {
        state = State.IDLE;
        startOne = startTwo = dirOne = dirTwo = Vec3.ZERO;
        countOne = countTwo = 0;
        lastParticle = System.currentTimeMillis();
        lastPos = Vec3.ZERO;
    }

    private static void onUse(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !on()) return;
        if (!"WISHING_COMPASS".equals(ItemUtil.skyblockId(mc.player.getItemInHand(hand)))) return;
        Vec3 eye = mc.player.position().add(0, 1.62, 0);
        Zone zone = zoneOf(eye);
        switch (state) {
            case IDLE -> {
                if (zone == Zone.NUCLEUS) {
                    msg("§cUse the Wishing Compass outside the Crystal Nucleus.");
                    return;
                }
                start(State.FIRST, eye);
            }
            case WAITING -> {
                if (startOne.closerThan(eye, DISTANCE_BETWEEN_USES)) {
                    msg("§eMove at least 8 blocks away from the first use.");
                    return;
                }
                if (zone != zoneOf(startOne)) {
                    msg("§eYou changed zones, starting over.");
                    reset();
                    start(State.FIRST, eye);
                } else {
                    start(State.SECOND, eye);
                }
            }
            case FIRST, SECOND -> {
                if (System.currentTimeMillis() - lastParticle < PARTICLES_MAX_DELAY) {
                    msg("§cWait for the trail to finish.");
                    return;
                }
                msg("§cCould not read the last use, starting over.");
                reset();
                start(State.FIRST, eye);
            }
        }
    }

    private static void start(State next, Vec3 eye) {
        state = next;
        if (next == State.FIRST) startOne = eye;
        else startTwo = eye;
        lastParticle = System.currentTimeMillis();
        lastPos = eye;
    }

    /** From ClientPlayNetworkHandlerMixin. */
    public static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (state != State.FIRST && state != State.SECOND) return;
        if (!Minecraft.getInstance().isSameThread() || !ParticleTypes.HAPPY_VILLAGER.equals(packet.getParticle().getType())) return;
        if (!on()) return;
        Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        lastParticle = System.currentTimeMillis();
        if (pos.distanceTo(lastPos) > PARTICLES_MAX_DISTANCE) return;
        lastPos = pos;
        if (state == State.FIRST) {
            dirOne = dirOne.add(pos.subtract(startOne).normalize().scale(1.0 / PARTICLES_PER_LINE));
            if (++countOne >= PARTICLES_PER_LINE) {
                state = State.WAITING;
                msg("§aFirst trail recorded. Move 8+ blocks and use the compass again.");
            }
        } else {
            dirTwo = dirTwo.add(pos.subtract(startTwo).normalize().scale(1.0 / PARTICLES_PER_LINE));
            if (++countTwo >= PARTICLES_PER_LINE) solveAndMark();
        }
    }

    private static void solveAndMark() {
        Vec3 target = intersect(startOne, dirOne, startTwo, dirTwo);
        if (target == null) {
            msg("§cThe trails don't cross, try again from different spots.");
        } else {
            Zone zone = zoneOf(startOne);
            String name = targetName(zone);
            if (!boxOf(zone).inflate(100, 0, 100).contains(target)) {
                name = "Unknown";
                msg("§cTarget is far outside the zone, it may not exist in this lobby.");
            }
            if (name.equals("Jungle Temple")) target = target.add(TEMPLE_DOOR_OFFSET);
            int color = colorOf(name);
            BlockPos pos = BlockPos.containing(target);
            msg("§aWishing Compass found §f" + name + "§a: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            waypoints.add(pos, "§b" + name, color, 0);
        }
        reset();
    }

    // closest points of the two lines, averaged
    private static Vec3 intersect(Vec3 p1, Vec3 d1, Vec3 p2, Vec3 d2) {
        double a = d1.dot(d1), b = d1.dot(d2), c = d2.dot(d2);
        Vec3 w = p1.subtract(p2);
        double d = d1.dot(w), e = d2.dot(w);
        double denom = a * c - b * b;
        if (Math.abs(denom) < 1e-9) return null;
        double t = (b * e - c * d) / denom;
        double s = (a * e - b * d) / denom;
        Vec3 c1 = p1.add(d1.scale(t)), c2 = p2.add(d2.scale(s));
        if (c1.distanceTo(c2) >= DISTANCE_TOLERANCE) return null;
        return c1.add(c2).scale(0.5);
    }

    private static Zone zoneOf(Vec3 p) {
        if (NUCLEUS.contains(p)) return Zone.NUCLEUS;
        if (JUNGLE.contains(p)) return Zone.JUNGLE;
        if (MITHRIL.contains(p)) return Zone.MITHRIL;
        if (GOBLIN.contains(p)) return Zone.GOBLIN;
        if (PRECURSOR.contains(p)) return Zone.PRECURSOR;
        if (MAGMA.contains(p)) return Zone.MAGMA;
        return Zone.NUCLEUS;
    }

    private static AABB boxOf(Zone z) {
        return switch (z) {
            case JUNGLE -> JUNGLE;
            case MITHRIL -> MITHRIL;
            case GOBLIN -> GOBLIN;
            case PRECURSOR -> PRECURSOR;
            case MAGMA -> MAGMA;
            default -> NUCLEUS;
        };
    }

    private static String targetName(Zone z) {
        if (zoneDone(z)) return "Unknown";
        return switch (z) {
            case JUNGLE -> hasJungleKey() ? "Jungle Temple" : "Odawa";
            case MITHRIL -> "Mines of Divan";
            case GOBLIN -> kingsScent() ? "Goblin Queen's Den" : "King Yolkar";
            case PRECURSOR -> "Lost Precursor City";
            case MAGMA -> "Khazad-dûm";
            default -> "Unknown";
        };
    }

    private static int colorOf(String name) {
        return switch (name) {
            case "Jungle Temple", "Odawa" -> 0xAA00AA;
            case "Mines of Divan" -> 0x55FF55;
            case "Goblin Queen's Den", "King Yolkar" -> 0xFFAA00;
            case "Lost Precursor City" -> 0x55FFFF;
            case "Khazad-dûm" -> 0xFFFF55;
            default -> 0xFFFFFF;
        };
    }

    private static boolean zoneDone(Zone z) {
        boolean hasWidget = false;
        String notFound = switch (z) {
            case JUNGLE -> "Amethyst: ✖ Not Found";
            case MITHRIL -> "Jade: ✖ Not Found";
            case GOBLIN -> "Amber: ✖ Not Found";
            case PRECURSOR -> "Sapphire: ✖ Not Found";
            case MAGMA -> "Topaz: ✖ Not Found";
            default -> null;
        };
        if (notFound == null) return false;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.equals("Crystals:")) hasWidget = true;
            if (line.equals(notFound)) return false;
        }
        if (!hasWidget) {
            msg("§7Enable the Crystals tab widget (/tab) so finished zones are detected.");
            return false;
        }
        return true;
    }

    private static boolean kingsScent() {
        boolean hasWidget = false;
        for (String raw : TabListUtils.getTabListLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.startsWith("Active Effects:")) hasWidget = true;
            if (line.startsWith("King's Scent")) return true;
        }
        if (!hasWidget) msg("§7Enable the Active Effects tab widget (/tab) to detect King's Scent.");
        return false;
    }

    private static boolean hasJungleKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if ("JUNGLE_KEY".equals(ItemUtil.skyblockId(inv.getItem(i)))) return true;
        }
        return false;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        waypoints.render(pose, cam);
    }
}
