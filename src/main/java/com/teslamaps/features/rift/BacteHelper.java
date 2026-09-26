package com.teslamaps.features.rift;

// After SkyHanni's TentacleWaypoint.kt, BacteApi.kt, KillZoneWarning.kt, BlobbercystsHighlight.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colosseum: Bacte phase, tentacle waypoints, kill zone warning, Blobbercyst glow. */
public final class BacteHelper {

    private record Mark(BlockPos pos, AABB box, Vec3 text, String label) {}

    private static final Pattern NAME = Pattern.compile("\\[Lv\\d+] (B(?:a(?:c(?:te?)?)?)?) [\\d.,]+/[\\d.,]+❤");
    private static final Pattern GROWING = Pattern.compile("^(\\S*) is growing into (\\S+)!$");
    private static final Pattern KILL_ZONE = Pattern.compile("Get back in the arena or you will DIE(!+)");

    private static final Map<Slime, Integer> hits = new IdentityHashMap<>();
    private static List<Mark> marks = List.of();
    private static int phase;
    private static long chatPhaseAt, killMsgAt, killDeadline;
    private static Object lastLevel;
    private static int ticks;

    private BacteHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BacteTentacles").section("Rift").when(() -> cfg().riftBacteTentacles)
                .tick(BacteHelper::tickTentacles)
                .world(BacteHelper::render)
                .serverChat(BacteHelper::onPhaseChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Bacte Tentacle Waypoints", () -> c.riftBacteTentacles, v -> c.riftBacteTentacles = v)
                            .desc("Red beacon on every ground tentacle with its HP left (x/4, x/3 in phase 4), hit count in phase 5")
                            .tags("rift", "bacte", "colosseum", "tentacle");
                    b.toggle("Kill Zone Warning", () -> c.riftKillZoneWarning, v -> c.riftKillZoneWarning = v)
                            .desc("Title with a countdown when Bacte's arena kicks you out: \"Get back in the arena!\"")
                            .tags("rift", "bacte", "colosseum", "arena");
                    b.toggle("  Warning Sound", () -> c.riftKillZoneSound, v -> c.riftKillZoneSound = v);
                    b.toggle("Blobbercyst Highlight", () -> c.riftBlobbercystGlow, v -> c.riftBlobbercystGlow = v)
                            .desc("Glow on Blobbercysts").tags("rift", "blobbercyst", "colosseum", "bacte").legit();
                    b.color("  Color", () -> c.riftBlobbercystColor, v -> c.riftBlobbercystColor = v);
                });
        Feature.of("KillZoneWarning").section("Rift").when(() -> cfg().riftKillZoneWarning)
                .tick(BacteHelper::tickKillZone)
                .serverChat(BacteHelper::onKillZoneChat);
        Feature.of("BlobbercystGlow").section("Rift").legit().when(() -> cfg().riftBlobbercystGlow)
                .tick(BacteHelper::tickBlobbercysts);
    }

    private static void tickTentacles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            hits.clear();
            phase = 0;
        }
        if (!RiftSpot.inRift() || !RiftSpot.sub().equals("Colosseum")) {
            if (!hits.isEmpty()) hits.clear();
            marks = List.of();
            return;
        }
        if (ticks++ % 20 == 0) updatePhase(mc);
        for (Slime s : EntityCache.byType(Slime.class)) {
            if (hits.containsKey(s)) continue;
            if ((int) Math.ceil(s.getY()) != 68) continue;
            int size = s.getSize();
            if (size < 4 || size > 8) continue;
            hits.put(s, 0);
        }
        hits.keySet().removeIf(s -> s.isRemoved() || !s.isAlive() || s.getHealth() <= 0);
        if (hits.isEmpty()) {
            marks = List.of();
            return;
        }
        List<Mark> out = new ArrayList<>();
        for (Map.Entry<Slime, Integer> e : hits.entrySet()) {
            Slime s = e.getKey();
            BlockPos p = s.blockPosition();
            out.add(new Mark(p, new AABB(p), new Vec3(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5), label(e.getValue())));
        }
        marks = out;
    }

    private static String label(int h) {
        if (phase == 5) return "§a" + h + (h == 1 ? " Hit" : " Hits");
        int max = phase == 4 ? 3 : 4;
        return (h > 0 ? "§c" : "§a") + Math.max(0, max - h) + "§a/" + max + "§c❤";
    }

    private static void updatePhase(Minecraft mc) {
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String name = NametagMobs.tagName(as);
            if (name == null || !name.contains("❤")) continue;
            Matcher m = NAME.matcher(name);
            if (m.find()) {
                phase = m.group(1).length();
                return;
            }
        }
        if (System.currentTimeMillis() - chatPhaseAt > 10_000) phase = 0;
    }

    private static void onPhaseChat(String raw) {
        if (!RiftSpot.inRift()) return;
        Matcher m = GROWING.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        int n = m.group(2).length();
        if (n >= 1 && n <= 5) {
            phase = n;
            chatPhaseAt = System.currentTimeMillis();
        }
    }

    /** From ClientPlayNetworkHandlerMixin handleDamageEvent TAIL, main thread. */
    public static void onDamage(ClientboundDamageEventPacket p) {
        if (hits.isEmpty() || !cfg().riftBacteTentacles) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (!(e instanceof Slime s)) return;
        Integer h = hits.get(s);
        if (h == null || !p.sourceType().is(DamageTypes.GENERIC)) return;
        hits.put(s, h + 1);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Mark> l = marks;
        for (int i = 0; i < l.size(); i++) {
            Mark m = l.get(i);
            ESPRenderer.drawFilledBox(pose, m.box, 0x66FF0000, cam, true);
            ESPRenderer.drawBeaconBeam(pose, m.pos, 0x99FF0000, cam);
            ESPRenderer.drawText(pose, m.label, m.text, 1.2f, cam);
        }
    }

    private static void onKillZoneChat(String raw) {
        if (!RiftSpot.inRift()) return;
        Matcher m = KILL_ZONE.matcher(StringUtil.stripTrim(raw));
        if (!m.find()) return;
        long now = System.currentTimeMillis();
        killMsgAt = now;
        killDeadline = now + 250L * Math.max(0, 12 - m.group(1).length());
        if (cfg().riftKillZoneSound) LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
    }

    private static void tickKillZone() {
        long now = System.currentTimeMillis();
        if (now - killMsgAt > 250 || now >= killDeadline) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 3, 0);
        mc.gui.setTitle(Component.literal("§cGet back in the arena!"));
        mc.gui.setSubtitle(Component.literal(String.format(Locale.US, "§7%.2fs left", (killDeadline - now) / 1000.0)));
    }

    private static void tickBlobbercysts() {
        if (!RiftSpot.inRift()) return;
        Minecraft mc = Minecraft.getInstance();
        int col = TeslaMapsConfig.parseColor(cfg().riftBlobbercystColor) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (p.getName().getString().trim().equals("Blobbercyst")) EntityGlow.mark(p, col);
        }
    }
}
