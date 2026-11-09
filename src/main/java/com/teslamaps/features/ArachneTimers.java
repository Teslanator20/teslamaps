package com.teslamaps.features;

// Ported from GanKura's ArachneHandler / ArachneStatusHud / ArachneCrystalParticleMixin
// and SkyHanni's ArachneSpawnTimer.kt / ArachneKillTimer.kt / ArachneMinisNametagHider.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.screen.config.ConfigBuilder;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.ServerTicks;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ArachneTimers {

    private static final BlockPos ALTAR = new BlockPos(-283, 51, -179);
    private static final BlockPos WEB = new BlockPos(-283, 48, -210);
    private static final Vec3 ALTAR_LABEL = new Vec3(-282.5, 53.0, -178.5);
    private static final long SMALL_TICKS = 360, QUICK_TICKS = 420, NORMAL_TICKS = 740;
    private static final String[] QUOTES = {
            "No, this is impossible...", "I will be back, even stronger!", "Stop resisting.",
            "How can this be...what a humiliation.", "This isn't the end. You will learn how resourceful spiders are."};
    private static final List<String> SAMPLE = List.of("§5§lArachne Status", "Altar: §eSpawning §c(12.0s)", "Size: §aSmall", "§dBrood: §e4");

    private static boolean summoning, awaiting, bossMessage, downConfirmed, everConfirmed, lastReady;
    private static boolean inSanctuary, webLoaded, cobweb;
    private static String size;
    private static long spawnTarget, crystalAt;
    private static volatile int particles;
    private static long spawnedAt, killMs;
    private static int brood;

    private static IntOpenHashSet broodTags = new IntOpenHashSet();
    private static List<String> lines = List.of();
    private static int linesW;
    private static String altarText;
    private static Object lastLevel;
    private static int ticks;
    private static HudEditElement hud;

    private ArachneTimers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("arachneStatus", "Arachne Status").section("Combat")
                .enabled(() -> cfg().arachneTimers && cfg().arachneStatusHud)
                .size(() -> Math.max(linesW, 130), () -> CropStats.linesHeight(4))
                .preview((g, mc, x, y, s) -> CropStats.drawLines(g, mc, x, y, s, SAMPLE, 130));
        Feature.of("ArachneTimers").section("Combat").when(() -> cfg().arachneTimers)
                .tick(ArachneTimers::tick)
                .serverChat(ArachneTimers::onChat)
                .world(ArachneTimers::renderWorld)
                .hudHiddenInInventory("arachnestatus", ArachneTimers::render);
    }

    /** Rows under ArachneQol's "Arachne" label in the Combat category. */
    public static void config(ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("Arachne Spawn Timer", () -> c.arachneTimers, v -> c.arachneTimers = v)
                .desc("Calling = small (18 s), Crystal = big: quick (21 s) or normal (37 s) spawn, read from the altar particles")
                .tags("arachne", "spider", "calling", "crystal");
        b.toggle("  Status HUD", () -> c.arachneStatusHud, v -> c.arachneStatusHud = v)
                .desc("Ready / Spawning / Spawned, size and remaining brood. Position in /tmap gui");
        b.toggle("  Countdown at Altar", () -> c.arachneAltarText, v -> c.arachneAltarText = v);
        b.toggle("  Kill Timer in Chat", () -> c.arachneKillTimer, v -> c.arachneKillTimer = v)
                .desc("\"Arachne took X seconds to kill.\" after ARACHNE DOWN");
        b.toggle("  Hide Brood Nametags", () -> c.arachneHideBroodTags, v -> c.arachneHideBroodTags = v);
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !broodTags.isEmpty() && broodTags.contains(e.getId())
                && cfg().arachneTimers && cfg().arachneHideBroodTags;
    }

    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (!awaiting || !Minecraft.getInstance().isSameThread()) return;
        if (p.xMaxSpeed() != 1f || p.particle().getType() != ParticleTypes.DUST) return;
        Vector3f col = ((DustParticleOptions) p.particle()).getColor();
        if (col.x > 0.05f || col.y > 0.05f || col.z > 0.05f) return;
        double dx = p.x() - ALTAR.x(), dy = p.y() - ALTAR.y(), dz = p.z() - ALTAR.z();
        if (dx * dx + dy * dy + dz * dz > 4.0) return;
        particles++;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return ServerTicks.now(mc.level != null ? mc.level.getGameTime() : 0);
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInArea("Spider's Den")) return;
        String msg = StringUtil.stripTrim(raw);
        if (msg.contains("placed an Arachne's Calling! Something is awakening! (4/4)")) {
            start("Small");
            awaiting = false;
            spawnTarget = now() + SMALL_TICKS;
            return;
        }
        if (msg.contains("placed an Arachne Crystal! Something is awakening!")) {
            start("Big");
            spawnTarget = 0;
            awaiting = true;
            crystalAt = System.currentTimeMillis();
            particles = 0;
            return;
        }
        if (msg.startsWith("[BOSS] Arachne")) {
            if (msg.endsWith("A befitting welcome!") || msg.endsWith("With your sacrifice.")) {
                spawnedAt = System.currentTimeMillis();
                killMs = 0;
            }
            if (!isQuote(msg)) {
                bossMessage = true;
                everConfirmed = true;
                lastReady = false;
            }
            return;
        }
        if (msg.equals("ARACHNE DOWN!")) {
            summoning = false;
            size = null;
            awaiting = false;
            bossMessage = false;
            downConfirmed = true;
            everConfirmed = true;
            lastReady = true;
            if (spawnedAt > 0) killMs = System.currentTimeMillis() - spawnedAt;
            spawnedAt = 0;
            return;
        }
        if (killMs > 0 && msg.startsWith("Your Damage:")) {
            if (cfg().arachneKillTimer && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                        String.format("§eArachne took §b%.2f§e seconds to kill.", killMs / 1000.0)));
            }
            killMs = 0;
        }
    }

    private static void start(String s) {
        summoning = true;
        size = s;
        bossMessage = false;
        downConfirmed = false;
        everConfirmed = true;
        lastReady = false;
        spawnedAt = 0;
    }

    private static boolean isQuote(String msg) {
        for (String q : QUOTES) if (msg.contains(q)) return true;
        return false;
    }

    private static void reset() {
        summoning = awaiting = bossMessage = downConfirmed = false;
        inSanctuary = webLoaded = cobweb = false;
        size = null;
        spawnTarget = 0;
        spawnedAt = killMs = 0;
        brood = 0;
        broodTags = new IntOpenHashSet();
        lines = List.of();
        altarText = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (awaiting && System.currentTimeMillis() - crystalAt > 1000) {
            spawnTarget = now() + (particles <= 20 ? QUICK_TICKS : NORMAL_TICKS);
            awaiting = false;
        }
        if (++ticks % 5 != 0) return;
        if (!SkyblockUtils.isInArea("Spider's Den")) {
            if (!lines.isEmpty() || altarText != null || !broodTags.isEmpty()) {
                lines = List.of();
                altarText = null;
                broodTags = new IntOpenHashSet();
            }
            return;
        }
        if (ticks % 20 == 0) {
            boolean sanctuary = false;
            for (String raw : ScoreboardUtils.getScoreboardLines()) {
                if (ScoreboardUtils.cleanLine(raw).contains("Arachne's Sanctuary")) { sanctuary = true; break; }
            }
            inSanctuary = sanctuary;
        }
        if (inSanctuary) {
            webLoaded = mc.level.hasChunk(WEB.getX() >> 4, WEB.getZ() >> 4);
            cobweb = webLoaded && mc.level.getBlockState(WEB).is(Blocks.COBWEB);
            if (cobweb) { summoning = false; everConfirmed = true; lastReady = false; }
        }

        IntOpenHashSet tags = new IntOpenHashSet();
        int b = 0;
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            String n = s.getCustomName() != null ? s.getCustomName().getString() : "";
            if (!n.contains("Arachne's Brood")) continue;
            tags.add(s.getId());
            if (n.contains("❤")) b++;
        }
        broodTags = tags;
        brood = b;

        String status = status();
        TeslaMapsConfig c = cfg();
        if (c.arachneStatusHud) {
            List<String> out = new ArrayList<>(4);
            out.add("§5§lArachne Status");
            out.add("Altar: " + status);
            if (size != null) out.add("Size: " + ("Big".equals(size) ? "§c" : "§a") + size);
            if (brood > 0) out.add("§dBrood: §e" + brood);
            int w = 0;
            for (String l : out) w = Math.max(w, mc.font.width(l));
            linesW = w + 8;
            lines = out;
        } else lines = List.of();
        double rem = spawnTarget > 0 ? spawnTarget - ServerTicks.now() : 0;
        altarText = c.arachneAltarText && summoning && !awaiting && rem > 0 ? String.format("§b%.1fs", rem / 20.0) : null;
    }

    private static String status() {
        if (inSanctuary && cobweb) return "§cSpawned";
        if (summoning) {
            if (awaiting) return "§eSpawning §f(...)";
            double rem = Math.max(0, spawnTarget - ServerTicks.now());
            if (rem > 0) return String.format("§eSpawning §c(%.1fs)", rem / 20.0);
            return inSanctuary ? "§eSpawning §e(Soon)" : "§6Spawned/Killed";
        }
        if (inSanctuary && bossMessage) return "§eSpawning §e(Soon)";
        if (inSanctuary && downConfirmed) return "§aReady";
        if (inSanctuary && !webLoaded) return "§7Unknown";
        if (inSanctuary) return "§aReady";
        if (!everConfirmed) return "§7Unknown §8(visit the Sanctuary)";
        return lastReady ? "§aReady" : "§6Spawned/Killed";
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        String t = altarText;
        if (t == null) return;
        ESPRenderer.drawText(pose, t, ALTAR_LABEL, 1.5f, cam);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        CropStats.drawLines(g, mc, hud.x(), hud.y(), hud.scale(), l, linesW);
    }
}
