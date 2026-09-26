package com.teslamaps.features.diana;

// Ported from Devonian's BurrowWaypoint.kt / BurrowManager.kt, SkyHanni's PreciseGuessBurrow.kt,
// ArrowGuessBurrow.kt, DianaFixChat.kt and MuteBuggedSpade.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DianaBurrows {

    static final String PREFIX = "§2[Diana] §r";

    enum Type {
        START("§aStart"), MOB("§cMob"), TREASURE("§6Treasure"), GUESS("§9Guess"), OLD_GUESS("§5Old Guess"), ARROW("§bArrow Guess");
        final String label;
        Type(String label) { this.label = label; }
        boolean real() { return this == START || this == MOB || this == TREASURE; }
    }

    static final class Burrow {
        final BlockPos pos;
        Type type;
        long seen, expires;
        String text;
        Burrow(BlockPos pos, Type type, long now, long ttl) {
            this.pos = pos;
            this.type = type;
            this.seen = now;
            this.expires = now + ttl;
            this.text = type.label;
        }
    }

    private static final long TTL = 5 * 60_000;
    private static final double[] HUB_MIN = {-300, 55, -240}, HUB_MAX = {210, 130, 210};

    private static final List<Burrow> burrows = new ArrayList<>();
    private static final Map<BlockPos, Long> recentDug = new HashMap<>();

    private static final BezierFitter fitter = new BezierFitter(3);
    private static long lastSpade, lastLava, lastBurrowChat;
    private static BlockPos guess;
    private static String guessText = Type.GUESS.label;
    private static List<BlockPos> arrowGuesses = List.of();
    private static final List<String> arrowTexts = new ArrayList<>();

    // arrow
    private static final Set<Vec3> arrowPoints = new LinkedHashSet<>();
    private static long arrowPointAt;
    private static Vec3[] pendingRay;
    private static int pendingMin, pendingMax;
    private static long pendingRayAt;
    private static final List<double[]> recentRays = new ArrayList<>();

    // particle quality help
    private static boolean foundGuess, handledFail, pqSet;
    private static int errorCounter, successCounter;
    private static long lastPqPrompt;

    private static Object lastLevel;
    private static Feature feature;

    private DianaBurrows() {}

    static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        feature = Feature.of("DianaBurrows").section("Events")
                .when(() -> cfg().dianaBurrowGuess || cfg().dianaBurrowDetect || cfg().dianaArrowGuess)
                .tick(DianaBurrows::tick)
                .world(DianaBurrows::render)
                .serverChat(DianaBurrows::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Diana");
                    b.toggle("Burrow Guess", () -> c.dianaBurrowGuess, v -> c.dianaBurrowGuess = v)
                            .desc("Right click a spade: the lava particle trail is fitted to a curve and the next burrow is marked with a waypoint and beam. A real burrow nearby removes the guess.")
                            .tags("diana", "burrow", "spade", "guess", "griffin", "mythological");
                    b.color("  Guess Color", () -> c.dianaGuessColor, v -> c.dianaGuessColor = v);
                    b.toggle("  Line To Guess", () -> c.dianaGuessLine, v -> c.dianaGuessLine = v);
                    b.toggle("  Remember Old Guesses", () -> c.dianaOldGuesses, v -> c.dianaOldGuesses = v)
                            .desc("A new spade use keeps the previous guess in its own color until you walk up to it");
                    b.color("  Old Guess Color", () -> c.dianaOldGuessColor, v -> c.dianaOldGuessColor = v);
                    b.toggle("  \"Use Spade\" Title On Fail", () -> c.dianaUseSpadeTitle, v -> c.dianaUseSpadeTitle = v);
                    b.toggle("  Particle Quality Help", () -> c.dianaParticleHelp, v -> c.dianaParticleHelp = v)
                            .desc("No guess 3s after the spade: asks you to try again, after the second miss a clickable line sets /pq extreme");
                    b.toggle("  Mute Bugged Spade Music", () -> c.dianaMuteSpadeMusic, v -> c.dianaMuteSpadeMusic = v)
                            .desc("Mutes the low music the spade sometimes plays on the Hub; real game music stays");
                    b.toggle("Burrow Waypoints", () -> c.dianaBurrowDetect, v -> c.dianaBurrowDetect = v)
                            .desc("Burrows found by their particles get a waypoint with their type: Start, Mob or Treasure. Dug burrows disappear.")
                            .tags("diana", "burrow", "treasure", "mob", "start");
                    b.color("  Start Color", () -> c.dianaStartColor, v -> c.dianaStartColor = v);
                    b.color("  Mob Color", () -> c.dianaMobColor, v -> c.dianaMobColor = v);
                    b.color("  Treasure Color", () -> c.dianaTreasureColor, v -> c.dianaTreasureColor = v);
                    b.toggle("Arrow Guess", () -> c.dianaArrowGuess, v -> c.dianaArrowGuess = v)
                            .desc("After digging a burrow the dust arrow is read and the next burrow of the chain is guessed without a spade click. Several candidates show grey.")
                            .tags("diana", "burrow", "arrow", "guess");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()) onSpadeUse(hand);
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && isSpade(player.getItemInHand(hand))) onDig(hit.getBlockPos());
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (level.isClientSide() && isSpade(player.getItemInHand(hand))) onDig(pos);
            return InteractionResult.PASS;
        });
    }

    static boolean isSpade(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        return id != null && (id.equals("ANCESTRAL_SPADE") || id.equals("ARCHAIC_SPADE") || id.equals("DEIFIC_SPADE"));
    }

    static boolean onHub() {
        return SkyblockUtils.isInArea("Hub");
    }

    private static boolean on() {
        return feature != null && feature.active() && onHub();
    }

    static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(PREFIX + text));
    }

    static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 30, 10);
        mc.gui.setTitle(Component.literal(text));
    }

    /** Where the chain continues: spade guess first, then the arrow guess. Null = nothing known. */
    static BlockPos target() {
        if (guess != null) return guess;
        List<BlockPos> a = arrowGuesses;
        return a.isEmpty() ? null : a.get(0);
    }

    static int color(String hex) {
        return TeslaMapsConfig.parseColor(hex) | 0xFF000000;
    }

    private static void resetAll() {
        burrows.clear();
        recentDug.clear();
        fitter.reset();
        guess = null;
        arrowGuesses = List.of();
        arrowPoints.clear();
        pendingRay = null;
        recentRays.clear();
        lastSpade = 0;
    }

    // ---- input ----

    private static void onSpadeUse(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !on() || !isSpade(mc.player.getItemInHand(hand))) return;
        long now = System.currentTimeMillis();
        if (now - lastLava < 200) return;
        if (guess != null) keepOldGuess(guess, now);
        guess = null;
        fitter.reset();
        lastSpade = now;
        foundGuess = false;
        handledFail = false;
    }

    private static void keepOldGuess(BlockPos g, long now) {
        if (!cfg().dianaOldGuesses) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(g)) <= 100) return;
        if (!(g.getZ() < -30 ? g.getX() > -230 : g.getX() > -300) || g.getX() >= 210 || g.getZ() <= -240 || g.getZ() >= 210
                || g.getY() <= 50 || g.getY() >= 120) return;
        add(g, Type.OLD_GUESS, now);
    }

    private static void onDig(BlockPos pos) {
        if (!on()) return;
        long now = System.currentTimeMillis();
        burrows.removeIf(b -> b.pos.equals(pos));
        recentDug.put(pos.immutable(), now + 1500);
        if (recentDug.size() > 20) recentDug.entrySet().removeIf(e -> e.getValue() < now);
        if (pos.equals(guess)) guess = null;
    }

    private static void onChat(String raw) {
        if (!on()) return;
        String t = StringUtil.stripTrim(raw);
        if (t.equals("Poof! You have cleared your griffin burrows!")) {
            resetAll();
            return;
        }
        if (t.startsWith("You dug out a Griffin Burrow!") || t.startsWith("You finished the Griffin burrow chain!")
                || t.equals("Defeat all the burrow defenders in order to dig it!")) {
            lastBurrowChat = System.currentTimeMillis();
            if (!t.startsWith("You finished")) {
                if (pendingRay != null && lastBurrowChat - pendingRayAt < 1500) useRay(pendingRay, pendingMin, pendingMax);
            } else arrowGuesses = List.of();
            pendingRay = null;
            if (t.startsWith("You dug out") || t.startsWith("You finished")) removeNearest();
            return;
        }
        if (t.startsWith("Maximum Particles per Tick now:") && cfg().dianaParticleHelp && cfg().dianaBurrowGuess) {
            pqSet = true;
            errorCounter = 0;
            msg("§eNow try again!");
        }
    }

    // the dug burrow is the one next to the player
    private static void removeNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Burrow best = null;
        double bestD = 16;
        for (Burrow b : burrows) {
            if (!b.type.real()) continue;
            double d = mc.player.distanceToSqr(Vec3.atCenterOf(b.pos));
            if (d < bestD) { bestD = d; best = b; }
        }
        if (best != null) {
            burrows.remove(best);
            recentDug.put(best.pos, System.currentTimeMillis() + 1500);
        }
    }

    // ---- particles ----

    private static boolean dist(ClientboundLevelParticlesPacket p, float x, float y, float z) {
        return Math.abs(p.getXDist() - x) < 0.01f && Math.abs(p.getYDist() - y) < 0.01f && Math.abs(p.getZDist() - z) < 0.01f;
    }

    /** From ClientPlayNetworkHandlerMixin. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (!Minecraft.getInstance().isSameThread()) return;
        ParticleType<?> type = p.getParticle().getType();
        if (type != ParticleTypes.DRIPPING_LAVA && type != ParticleTypes.ENCHANTED_HIT && type != ParticleTypes.CRIT
                && type != ParticleTypes.DUST) return;
        if (!on()) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        if (c.dianaBurrowDetect || c.dianaBurrowGuess || c.dianaArrowGuess) {
            Type found = null;
            if (type == ParticleTypes.ENCHANTED_HIT && p.getCount() == 4 && dist(p, 0.5f, 0.1f, 0.5f)) found = Type.START;
            else if (type == ParticleTypes.CRIT && p.getCount() == 3 && dist(p, 0.5f, 0.1f, 0.5f)) found = Type.MOB;
            else if (type == ParticleTypes.DRIPPING_LAVA && p.getCount() == 2 && dist(p, 0.35f, 0.1f, 0.35f)) found = Type.TREASURE;
            if (found != null) {
                BlockPos pos = BlockPos.containing(p.getX(), p.getY() - 1, p.getZ());
                Long until = recentDug.get(pos);
                if (until == null || until < now) add(pos, found, now);
                return;
            }
        }
        if (type == ParticleTypes.DRIPPING_LAVA && c.dianaBurrowGuess && p.getCount() == 2 && p.getMaxSpeed() == -0.5f
                && dist(p, 0f, 0f, 0f)) {
            lastLava = now;
            if (now - lastSpade > 3000) return;
            Minecraft mc = Minecraft.getInstance();
            Vec3 at = new Vec3(p.getX(), p.getY(), p.getZ());
            if (fitter.isEmpty() && mc.player != null && at.distanceTo(mc.player.getEyePosition()) > 4) return;
            if (!fitter.tryAdd(at, 3.0)) return;
            Vec3 solved = fitter.solve();
            if (solved == null) return;
            BlockPos g = groundAt(BlockPos.containing(solved.x, solved.y - 0.5, solved.z));
            if (!g.equals(guess)) {
                guess = g;
                arrowGuesses = List.of();
                if (!foundGuess) onGuessFound();
            }
            return;
        }
        if (type == ParticleTypes.DUST && c.dianaArrowGuess && p.getCount() == 0 && p.getMaxSpeed() == 1.0f) onArrowParticle(p, now);
    }

    private static void add(BlockPos pos, Type type, long now) {
        for (Burrow b : burrows) {
            if (!b.pos.equals(pos)) continue;
            if (b.type.real() == type.real()) {
                b.type = type;
                b.seen = now;
                b.expires = now + TTL;
                return;
            }
        }
        burrows.add(new Burrow(pos.immutable(), type, now, TTL));
        if (type.real()) {
            burrows.removeIf(b -> !b.type.real() && b.pos.distSqr(pos) < 100);
            if (guess != null && guess.distSqr(pos) < 100) guess = null;
            if (!arrowGuesses.isEmpty() && arrowGuesses.get(0).distSqr(pos) < 100) arrowGuesses = List.of();
        }
    }

    // y=110 and floating guesses land on the grass block of that column
    private static BlockPos groundAt(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(pos) || isGround(level, pos)) return pos;
        BlockPos best = null;
        for (int y = 140; y >= 50; y--) {
            BlockPos q = new BlockPos(pos.getX(), y, pos.getZ());
            if (isGround(level, q) && (best == null || Math.abs(y - pos.getY()) < Math.abs(best.getY() - pos.getY()))) best = q;
        }
        return best != null ? best : pos;
    }

    private static boolean isGround(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) return false;
        BlockPos up = pos.above();
        BlockState above = level.getBlockState(up);
        return above.getCollisionShape(level, up).isEmpty() || above.is(BlockTags.LEAVES) || above.is(Blocks.SPRUCE_FENCE);
    }

    private static boolean validCandidate(Level level, BlockPos pos) {
        return !level.hasChunkAt(pos) || isGround(level, pos);
    }

    // ---- arrow guess (SkyHanni ArrowGuessBurrow) ----

    private static void onArrowParticle(ClientboundLevelParticlesPacket p, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(p.getX(), p.getY(), p.getZ()) > 36) return;
        int min, max;
        float r = p.getXDist(), g = p.getYDist(), bl = p.getZDist();
        if (r == 0 && g == 128 && bl == 0) { min = 0; max = 117; }
        else if (r == 255 && g == 255 && bl == 0) { min = 112; max = 282; }
        else if (r == 255 && g == 0 && bl == 0) { min = 281; max = 600; }
        else return;
        if (now - arrowPointAt > 3000) arrowPoints.clear();
        arrowPointAt = now;
        if (arrowPoints.size() > 200) return;
        arrowPoints.add(new Vec3(p.getX(), p.getY(), p.getZ()));
        Vec3[] ray = detectArrow();
        if (ray == null) return;
        BlockPos dug = BlockPos.containing(ray[0]);
        burrows.removeIf(b -> !b.type.real() && b.pos.equals(dug));
        if (now - lastBurrowChat <= 500) useRay(ray, min, max);
        else {
            pendingRay = ray;
            pendingMin = min;
            pendingMax = max;
            pendingRayAt = now;
        }
    }

    private static void useRay(Vec3[] ray, int min, int max) {
        List<BlockPos> found = guessFromRay(ray[0], ray[1], min, max);
        if (found.isEmpty()) {
            if (cfg().dianaUseSpadeTitle) title("§eUse Spade");
            return;
        }
        arrowGuesses = found;
    }

    private static Vec3[] detectArrow() {
        List<Vec3> line = findLine(20, 0.12);
        if (line == null) return null;
        int c1 = near(line.get(1)), c2 = near(line.get(line.size() - 2));
        if (!((c1 == 2 && c2 == 4) || (c1 == 4 && c2 == 2))) return null;
        Vec3 tip = c1 == 4 ? line.get(0) : line.get(line.size() - 1);
        Vec3 base = c1 == 4 ? line.get(line.size() - 1) : line.get(0);
        Vec3 o = base.add(0, -1.5, 0);
        Vec3 dir = tip.add(0, -1.5, 0).subtract(o).normalize();
        long now = System.currentTimeMillis();
        recentRays.removeIf(d -> now - (long) d[6] > 18_000);
        for (double[] d : recentRays) {
            if (d[0] == o.x && d[1] == o.y && d[2] == o.z && d[3] == dir.x && d[4] == dir.y && d[5] == dir.z) return null;
        }
        recentRays.add(new double[] {o.x, o.y, o.z, dir.x, dir.y, dir.z, now});
        arrowPoints.clear();
        return new Vec3[] {o, dir};
    }

    private static int near(Vec3 origin) {
        int n = 0;
        for (Vec3 p : arrowPoints) if (!p.equals(origin) && p.distanceToSqr(origin) <= 0.12 * 0.12) n++;
        return n;
    }

    private static List<Vec3> findLine(int length, double maxDist) {
        List<Vec3> pts = new ArrayList<>(arrowPoints);
        if (pts.size() < length) return null;
        for (Vec3 start : pts) {
            List<Vec3> line = new ArrayList<>();
            line.add(start);
            if (extend(line, pts, length, maxDist)) return line;
        }
        return null;
    }

    private static boolean extend(List<Vec3> line, List<Vec3> pts, int length, double maxDist) {
        if (line.size() == length) return true;
        Vec3 last = line.get(line.size() - 1), first = line.get(0), second = line.size() > 1 ? line.get(1) : first;
        Vec3 next = null;
        double best = Double.MAX_VALUE;
        for (Vec3 p : pts) {
            if (line.contains(p)) continue;
            double d = last.distanceTo(p);
            if (d > maxDist || d >= best) continue;
            if (second.subtract(first).cross(p.subtract(first)).lengthSqr() >= 1e-6) continue;
            best = d;
            next = p;
        }
        if (next == null) return false;
        line.add(next);
        if (extend(line, pts, length, maxDist)) return true;
        line.remove(line.size() - 1);
        return false;
    }

    private static List<BlockPos> guessFromRay(Vec3 origin, Vec3 dir, int min, int max) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        double[] o = {origin.x, origin.y, origin.z}, d = {dir.x, dir.y, dir.z};
        for (int i = 0; i < 3; i++) if (o[i] < HUB_MIN[i] || o[i] > HUB_MAX[i]) return List.of();
        double tExit = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9) continue;
            double t1 = (HUB_MIN[i] - o[i]) / d[i], t2 = (HUB_MAX[i] - o[i]) / d[i];
            tExit = Math.min(tExit, Math.max(t1, t2));
        }
        if (tExit == Double.MAX_VALUE || tExit <= 0) return List.of();
        double[] diff = {d[0] * tExit, d[1] * tExit, d[2] * tExit};
        int axis = -1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(diff[i]) <= 0.9) continue;
            if (axis < 0 || Math.abs(diff[i]) < Math.abs(diff[axis])) axis = i;
        }
        if (axis < 0) return List.of();
        double sign = Math.signum(d[axis]);
        int steps = (int) Math.abs(diff[axis]);
        double bestScore = Double.MAX_VALUE;
        List<BlockPos> best = new ArrayList<>();
        List<Double> bestDist = new ArrayList<>();
        for (int i = 1; i <= steps; i++) {
            double t = (o[axis] + i * sign - o[axis]) / d[axis];
            Vec3 point = origin.add(dir.scale(t));
            BlockPos block = BlockPos.containing(point);
            if (!validCandidate(level, block)) continue;
            Vec3 center = Vec3.atCenterOf(block);
            double toRay = center.subtract(origin).cross(dir).length();
            double fromOrigin = point.distanceTo(origin);
            if (fromOrigin <= 0) continue;
            double score = Math.round(toRay * 500000 / fromOrigin * 100) / 100.0;
            if (score < bestScore) {
                bestScore = score;
                best.clear();
                bestDist.clear();
            }
            if (score == bestScore) {
                best.add(block);
                bestDist.add(fromOrigin);
            }
        }
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < best.size(); i++) {
            int dist = (int) (double) bestDist.get(i);
            if (dist >= min && dist <= max) out.add(best.get(i));
        }
        return out;
    }

    // ---- particle quality help (SkyHanni DianaFixChat) ----

    private static void onGuessFound() {
        foundGuess = true;
        if (cfg().dianaParticleHelp && pqSet) msg("§aChanging the particle quality worked, good job!");
        pqSet = false;
        errorCounter = 0;
        successCounter++;
    }

    private static void checkFail(long now) {
        if (!cfg().dianaParticleHelp || !cfg().dianaBurrowGuess || lastSpade == 0 || foundGuess || handledFail) return;
        if (now - lastSpade > 60_000 || now - lastSpade <= 3000) return;
        handledFail = true;
        Minecraft mc = Minecraft.getInstance();
        for (Burrow b : burrows) {
            if (b.type.real() && mc.player != null && mc.player.distanceToSqr(Vec3.atCenterOf(b.pos)) < 900) return;
        }
        if (cfg().dianaUseSpadeTitle) title("§eUse Spade");
        errorCounter++;
        if (errorCounter == 1) {
            if (successCounter < 5) msg("§eCould not find a Diana guess from the particles, please try again.");
            return;
        }
        if (pqSet || now - lastPqPrompt < 30_000 || mc.player == null) return;
        lastPqPrompt = now;
        mc.player.sendSystemMessage(Component.literal(PREFIX + "§cNo Diana guess found! §eClick here to set the particle quality to extreme.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/pq extreme"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eRuns /pq extreme")))));
    }

    /** From SoundPlayMixin: true mutes the sound. */
    public static boolean muteMusic(SoundInstance s) {
        TeslaMapsConfig c = cfg();
        if (!c.dianaMuteSpadeMusic || !c.section("Events")) return false;
        String path = s.getIdentifier().getPath();
        if (!path.startsWith("music")) return false;
        boolean real = s.getPitch() == 1f && s.getVolume() == 1f && s.getX() == 0 && s.getY() == 0 && s.getZ() == 0;
        return !real && onHub();
    }

    // ---- tick / render ----

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            resetAll();
            successCounter = 0;
            return;
        }
        if (!onHub()) return;
        long now = System.currentTimeMillis();
        checkFail(now);
        boolean spade = isSpade(mc.player.getMainHandItem());
        Iterator<Burrow> it = burrows.iterator();
        while (it.hasNext()) {
            Burrow b = it.next();
            double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(b.pos)));
            if (now >= b.expires) { it.remove(); continue; }
            if (b.type.real() && spade && d < 20 && now - b.seen > 3000) { it.remove(); continue; }
            if (!b.type.real() && spade && d < 10) { it.remove(); continue; }
            b.text = b.type.label + " §7(" + Math.round(d) + "m)";
        }
        if (guess != null) guessText = Type.GUESS.label + " §7(" + Math.round(Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(guess)))) + "m)";
        List<BlockPos> a = arrowGuesses;
        arrowTexts.clear();
        for (BlockPos p : a) arrowTexts.add(Type.ARROW.label + " §7(" + Math.round(Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(p)))) + "m)");
        if (!a.isEmpty() && mc.player.distanceToSqr(Vec3.atCenterOf(a.get(0))) < 25) arrowGuesses = List.of();
        if (guess != null && now - lastSpade > 1000 && mc.player.distanceToSqr(Vec3.atCenterOf(guess)) < 9) guess = null;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (burrows.isEmpty() && guess == null && arrowGuesses.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        for (int i = 0, n = burrows.size(); i < n; i++) {
            Burrow b = burrows.get(i);
            int color;
            switch (b.type) {
                case START -> { if (!c.dianaBurrowDetect) continue; color = color(c.dianaStartColor); }
                case MOB -> { if (!c.dianaBurrowDetect) continue; color = color(c.dianaMobColor); }
                case TREASURE -> { if (!c.dianaBurrowDetect) continue; color = color(c.dianaTreasureColor); }
                default -> color = color(c.dianaOldGuessColor);
            }
            WaypointSet.draw(pose, cam, b.pos, color, b.text, true, true, true);
        }
        BlockPos g = guess;
        if (g != null) {
            int color = color(c.dianaGuessColor);
            WaypointSet.draw(pose, cam, g, color, guessText, true, true, true);
            if (c.dianaGuessLine) ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(g), color, cam, true);
        }
        List<BlockPos> a = arrowGuesses;
        for (int i = 0; i < a.size() && i < arrowTexts.size(); i++) {
            int color = i == 0 && a.size() == 1 ? 0xFF55FFFF : 0xFFAAAAAA;
            WaypointSet.draw(pose, cam, a.get(i), color, arrowTexts.get(i), true, true, true);
        }
        if (g == null && a.size() == 1 && c.dianaGuessLine) ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(a.get(0)), 0xFF55FFFF, cam, true);
    }
}
