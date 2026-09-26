package com.teslamaps.features;

// Ported from SkyHanni's features/combat/cocoon/CocoonOverlay.kt + CocoonAPI.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.rift.RiftSpot;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CocoonOverlay {

    private static final String LARVA = "4ceb0ed8fc2272b3d3d820676d52a38e7b2e8da8c687a233e0dabaa16c0e96df";
    private static final long LIFETIME = 6400;
    private static final Pattern CAUGHT = Pattern.compile("^CAUGHT! You cocooned an? ([\\w ]+)!$");

    private static final class Cocoon {
        final ArmorStand stand;
        final Vec3 pos;
        final long spawn;
        String name;
        boolean guessed;
        Cocoon(ArmorStand stand, String name) {
            this.stand = stand;
            this.pos = stand.position();
            this.spawn = System.currentTimeMillis();
            this.name = name;
        }
    }

    private static final List<Cocoon> cocoons = new ArrayList<>();
    private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet seen = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private static final List<Vec3> namePos = new ArrayList<>();
    private static final List<String> nameText = new ArrayList<>();
    private static final List<Vec3> timePos = new ArrayList<>();
    private static final List<String> timeText = new ArrayList<>();
    private static final List<Integer> timeColor = new ArrayList<>();
    private static Boolean canCocoon;
    private static Object lastLevel;
    private static int ticks;

    private CocoonOverlay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CocoonOverlay").section("Combat").when(() -> cfg().cocoonOverlay)
                .tick(CocoonOverlay::tick)
                .serverChat(CocoonOverlay::onChat)
                .world(CocoonOverlay::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Cocoons");
                    b.toggle("Cocoon Overlay", () -> c.cocoonOverlay, v -> c.cocoonOverlay = v)
                            .desc("Above every cocoon: the mob inside and a countdown until it hatches (about 6.4s, green to red).")
                            .tags("cocoon", "primordial", "blood shot", "hatch");
                    b.toggle("  Mob Name", () -> c.cocoonOverlayName, v -> c.cocoonOverlayName = v);
                    b.toggle("  Hatch Timer", () -> c.cocoonOverlayTimer, v -> c.cocoonOverlayTimer = v);
                    b.toggle("  Only With Cocoon Belt", () -> c.cocoonOverlayBeltOnly, v -> c.cocoonOverlayBeltOnly = v)
                            .desc("Only while The Primordial or a blood shot belt is equipped (open /equipment once so it is known).");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            cocoons.clear();
            seen.clear();
        }
        ticks++;
        if (ticks % 20 == 1) canCocoon = EquipmentDisplay.beltCanCocoon();
        long now = System.currentTimeMillis();
        cocoons.removeIf(k -> !k.stand.isAlive() || k.stand.isRemoved() || now - k.spawn > 8000);
        if (ticks % 2 == 0 && !RiftSpot.inRift()) scan(mc);
        build(mc, now);
    }

    private static void scan(Minecraft mc) {
        for (ArmorStand s : EntityCache.armorStands()) {
            if (seen.contains(s.getId()) || s.distanceToSqr(mc.player) > 32 * 32) continue;
            if (!LARVA.equals(SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD)))) continue;
            seen.add(s.getId());
            if (known(s)) continue;
            String name = mobName(mc, s.position());
            if (name == null) continue;
            Cocoon k = new Cocoon(s, name.startsWith("?") ? name.substring(1) : name);
            k.guessed = name.startsWith("?");
            cocoons.add(k);
        }
    }

    private static boolean known(ArmorStand s) {
        Vec3 p = s.position();
        for (Cocoon k : cocoons) {
            if (k.stand == s) return true;
            double dx = k.pos.x - p.x, dz = k.pos.z - p.z;
            if (dx * dx + dz * dz < 0.5) return true;
        }
        return false;
    }

    private static String mobName(Minecraft mc, Vec3 p) {
        LivingEntity best = null;
        double bestD = 4.0;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(p, p).inflate(2),
                x -> x instanceof LivingEntity && !(x instanceof ArmorStand) && !(x instanceof Player))) {
            double d = e.position().distanceToSqr(p);
            if (d < bestD) { bestD = d; best = (LivingEntity) e; }
        }
        if (best == null) return null;
        ArmorStand tag = NametagMobs.tagForMob(best);
        NametagMobs.Info info = tag == null ? null : NametagMobs.parse(tag, false);
        if (info != null) return info.name();
        return "?" + best.getType().getDescription().getString();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("CAUGHT!")) return;
        Matcher m = CAUGHT.matcher(t);
        if (!m.matches()) return;
        for (int i = cocoons.size() - 1; i >= 0; i--) {
            Cocoon k = cocoons.get(i);
            if (k.guessed) { k.name = m.group(1).trim(); k.guessed = false; return; }
        }
    }

    private static void build(Minecraft mc, long now) {
        namePos.clear();
        nameText.clear();
        timePos.clear();
        timeText.clear();
        timeColor.clear();
        TeslaMapsConfig c = cfg();
        if (c.cocoonOverlayBeltOnly && !Boolean.TRUE.equals(canCocoon)) return;
        for (Cocoon k : cocoons) {
            Vec3 top = k.pos.add(0, 2.4, 0);
            if (c.cocoonOverlayName && k.name != null) {
                namePos.add(top.add(0, 0.35, 0));
                nameText.add("§e" + k.name);
            }
            if (c.cocoonOverlayTimer) {
                long left = Math.max(0, LIFETIME - (now - k.spawn));
                float pct = left / (float) LIFETIME;
                int r = (int) (0x55 + (0xFF - 0x55) * (1 - pct));
                int g = (int) (0x55 + (0xFF - 0x55) * pct);
                timePos.add(top);
                timeText.add(String.format(Locale.ROOT, "%.1fs", left / 1000.0));
                timeColor.add(0xFF000000 | r << 16 | g << 8 | 0x55);
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (int i = 0; i < namePos.size(); i++) {
            ESPRenderer.drawText(pose, nameText.get(i), namePos.get(i), 1.5f, cam, true, true);
        }
        for (int i = 0; i < timePos.size(); i++) {
            ESPRenderer.drawText(pose, timeText.get(i), timePos.get(i), 1.5f, cam, true, true, timeColor.get(i));
        }
    }
}
