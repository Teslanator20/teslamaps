package com.teslamaps.features;

// Terracotta hider from SkyHanni's TerracottaPhase.kt, sounds + interest bar from Skytils' DungeonFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.BossHealthOverlayAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class SadanFeatures {

    private static final Pattern SPLASH = Pattern.compile("[✧✯]?(\\d+[⚔+✧❤♞☄✷ﬗ✯]*)");

    private static volatile boolean phase = false;
    private static final Set<Integer> splashes = new HashSet<>();
    private static final Set<Integer> deadTerracottas = new HashSet<>();

    private static double interestEnd = -1;
    private static int interestLength = 105;
    private static UUID interestBar = null;
    private static Component interestName = null;
    private static float interestProgress = 0f;
    private static Object lastLevel = null;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SadanTerracotta").when(() -> cfg().sadanTerracottaHide || cfg().sadanInterest).tick(SadanFeatures::tick).serverChat(SadanFeatures::onChat);
        Feature.of("SadanTerracottaHide")
                .config("Hide", b -> {
                    b.label("Boss Fights");
                    b.toggle("Sadan Terracotta Phase: Hide Particles", () -> cfg().sadanTerracottaHide, v -> cfg().sadanTerracottaHide = v)
                            .desc("F6/M6: hides all particles between Sadan's first line and \"ENOUGH!\".");
                    b.toggle("  Also Mute Hurt/Eat Sounds", () -> cfg().sadanTerracottaSounds, v -> cfg().sadanTerracottaSounds = v);
                    b.toggle("  Also Hide Damage Splashes", () -> cfg().sadanTerracottaSplashes, v -> cfg().sadanTerracottaSplashes = v);
                    b.toggle("Hide Wither King (M7 P5)", () -> cfg().hideWitherKing, v -> cfg().hideWitherKing = v)
                            .desc("Drops the Wither King model and its witch particles in the dragon phase.");
                });
    }

    private static boolean onF6() {
        DungeonFloor f = DungeonManager.getCurrentFloor();
        return f != null && f.getLevel() == 6 && DungeonManager.isInDungeon();
    }

    private static void onChat(String raw) {
        if (!raw.contains("Sadan")) return;
        String m = StringUtil.strip(raw);
        if (!m.startsWith("[BOSS] Sadan")) return;
        if (m.contains("So you made it all the way here")) phase = true;
        else if (m.contains("ENOUGH!") || m.contains("It was inevitable.")) {
            phase = false;
            splashes.clear();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel || !onF6()) {
            lastLevel = mc.level;
            reset();
            return;
        }
        TeslaMapsConfig c = cfg();

        splashes.clear();
        if (phase && c.sadanTerracottaHide && c.sadanTerracottaSplashes) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ArmorStand a) || a.tickCount > 300 || !a.hasCustomName()) continue;
                String n = StringUtil.strip(a.getCustomName().getString()).replace(",", "");
                if (SPLASH.matcher(n).matches()) splashes.add(a.getId());
            }
        }

        tickInterest(mc, c);
    }

    private static void tickInterest(Minecraft mc, TeslaMapsConfig c) {
        if (!c.sadanInterest || !c.bossbarHealth || !c.section("Timers")) {
            interestBar = null;
            return;
        }
        UUID found = null;
        var events = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).teslamaps$getEvents();
        if (events != null) {
            for (LerpingBossEvent ev : events.values()) {
                if (ev.getName() != null && ev.getName().getString().contains("Sadan's Interest")) {
                    found = ev.getId();
                    break;
                }
            }
        }
        if (found == null) { interestBar = null; return; }
        double now = System.currentTimeMillis() / 1000.0;
        if (interestEnd == -1) {
            interestLength = DungeonManager.getCurrentFloor() == DungeonFloor.M6 ? 115 : 105;
            interestEnd = now + interestLength;
        }
        double left = interestEnd - now;
        if (interestEnd < 0 || left < 0) {
            interestEnd = -2;
            interestBar = null;
            return;
        }
        interestBar = found;
        interestProgress = (float) Math.max(0, Math.min(1, left / interestLength));
        interestName = Component.literal("§c§lSadan's Interest: §6" + (int) left + "s");
    }

    /** Main thread, from the entity event packet. */
    public static void onEntityEvent(Entity e, byte id) {
        if (id != 3 || interestEnd <= 0 || !(e instanceof Player)) return;
        if (!"Terracotta".equals(e.getName().getString().trim())) return;
        if (deadTerracottas.add(e.getId())) interestEnd -= 1;
    }

    public static Component interestName(LerpingBossEvent ev) {
        UUID bar = interestBar;
        return bar != null && bar.equals(ev.getId()) ? interestName : null;
    }

    public static float interestProgress(BossEvent ev, float original) {
        UUID bar = interestBar;
        return bar != null && bar.equals(ev.getId()) ? interestProgress : original;
    }

    private static boolean hideActive() {
        return phase && cfg().sadanTerracottaHide;
    }

    /** Any thread: static flag + config reads only. */
    public static boolean hideParticle() {
        return hideActive();
    }

    public static boolean muteSound(SoundEvent sound, float vol, float pitch) {
        if (!hideActive() || !cfg().sadanTerracottaSounds) return false;
        if (sound == SoundEvents.GENERIC_HURT) return true;
        return sound == SoundEvents.GENERIC_EAT.value() && vol == 1f && Math.abs(pitch - 0.6984127f) < 0.001f;
    }

    public static boolean shouldHide(Entity e) {
        return phase && !splashes.isEmpty() && splashes.contains(e.getId());
    }

    private static void reset() {
        phase = false;
        splashes.clear();
        deadTerracottas.clear();
        interestEnd = -1;
        interestBar = null;
    }
}
