package com.teslamaps.features.crimson;

// Ported from SkyHanni's ashfang/AshfangHider.kt + AshfangManager.kt (full-HP name hide)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AshfangHider {

    private static final Pattern SPLASH = Pattern.compile("[✧✯]?\\d+[⚔+✧❤♞☄✷ﬗ✯]*");
    private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kMB]?)/([\\d.,]+[kMB]?)❤");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static final Set<Integer> hidden = new HashSet<>();
    private static boolean active;
    private static int ticks;
    private static Object lastLevel;

    private AshfangHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.ashfangHider && c.section("Crimson Isle") || c.ashfangHideFullHpNames && c.section("Mob Visuals");
    }

    public static void register() {
        Feature.of("AshfangHider").when(AshfangHider::on)
                .tick(AshfangHider::tick)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Ashfang Hider", () -> c.ashfangHider, v -> c.ashfangHider = v)
                            .desc("During the Ashfang fight: hide particles, glowstone armor stands and damage splashes")
                            .tags("ashfang", "particles", "damage splash", "glowstone");
                    b.toggle("  Hide Particles + Glowstone", () -> c.ashfangHideParticles, v -> c.ashfangHideParticles = v);
                    b.toggle("  Hide Damage Splashes", () -> c.ashfangHideSplashes, v -> c.ashfangHideSplashes = v);
                })
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("  Ashfang: Hide Full-HP Blaze Names", () -> c.ashfangHideFullHpNames, v -> c.ashfangHideFullHpNames = v)
                            .desc("While Ashfang is up, nametags of Followers, Underlings and Acolytes at full health are hidden. Their colours come from Pick Mobs > Crimson Isle")
                            .tags("ashfang", "follower", "underling", "acolyte", "blaze", "nametag");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            active = false;
            hidden.clear();
        }
        if (++ticks % 20 == 0) active = SkyblockUtils.isInArea("Crimson Isle") && findAshfang(mc);
        hidden.clear();
        if (!active) return;
        TeslaMapsConfig c = cfg();
        boolean hider = c.ashfangHider && c.section("Crimson Isle");
        boolean glow = hider && c.ashfangHideParticles;
        boolean splash = hider && c.ashfangHideSplashes;
        boolean names = c.ashfangHideFullHpNames && c.section("Mob Visuals");
        for (ArmorStand a : EntityCache.armorStands()) {
            if (a.isRemoved()) continue;
            if (glow && wearsGlowstone(a)) {
                hidden.add(a.getId());
                continue;
            }
            if (a.getCustomName() == null) continue;
            String n = StringUtil.strip(a.getCustomName().getString()).trim();
            if (splash && a.tickCount < 300 && SPLASH.matcher(n.replace(",", "")).matches()) {
                hidden.add(a.getId());
            } else if (names && (n.contains("Ashfang Follower") || n.contains("Ashfang Underling") || n.contains("Ashfang Acolyte"))) {
                Matcher m = HEALTH.matcher(n);
                if (m.find() && m.group(1).equals(m.group(2))) hidden.add(a.getId());
            }
        }
    }

    private static boolean findAshfang(Minecraft mc) {
        for (ArmorStand a : EntityCache.namedArmorStands()) {
            if (a.isRemoved() || a.getCustomName() == null) continue;
            String n = a.getCustomName().getString();
            if (!n.contains("Ashfang") || n.contains("Follower") || n.contains("Underling") || n.contains("Acolyte")) continue;
            if (a.distanceToSqr(mc.player) < 80 * 80) return true;
        }
        return false;
    }

    private static boolean wearsGlowstone(ArmorStand a) {
        for (EquipmentSlot s : SLOTS) {
            var stack = a.getItemBySlot(s);
            if (!stack.isEmpty() && "Glowstone".equals(stack.getHoverName().getString())) return true;
        }
        return false;
    }

    public static boolean shouldHide(Entity e) {
        return active && !hidden.isEmpty() && on() && hidden.contains(e.getId());
    }

    public static boolean hideParticle() {
        TeslaMapsConfig c = cfg();
        return active && c.ashfangHider && c.ashfangHideParticles && c.section("Crimson Isle");
    }
}
