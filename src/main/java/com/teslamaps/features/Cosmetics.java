package com.teslamaps.features;

// Idea from SkyblockExtras' ParticleCloak + Colored Names (jar strings only, no source)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.CapeSync;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class Cosmetics {

    public static final String[] CLOAK_TYPES = {"Dust", "Flame", "Soul Flame", "End Rod", "Heart", "Cherry Leaves",
            "Snowflake", "Witch", "Portal", "Electric Spark", "Note", "Totem"};

    private record Cached(Component original, Component result) {}
    private static final Map<Integer, Cached> nameCache = new HashMap<>();
    private static Object world;
    private static java.util.Set<Integer> users = java.util.Set.of();
    private static int userTicks;

    private Cosmetics() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ParticleCloak").section("Render").when(() -> cfg().particleCloak)
                .tick(Cosmetics::tickCloak);
        Feature.of("ColoredNames").section("Render").when(() -> cfg().coloredNames)
                .tick(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != world || nameCache.size() > 256) {
                        world = mc.level;
                        nameCache.clear();
                    }
                    if (cfg().coloredNameUsers && ++userTicks % 40 == 1) {
                        java.util.Set<Integer> next = new java.util.HashSet<>();
                        for (Player pl : mc.level.players()) {
                            if (pl != mc.player && CapeSync.isUser(pl.getUUID().toString())) next.add(pl.getId());
                        }
                        users = next;
                    }
                })
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Cosmetics");
                    b.toggle("Particle Cloak", () -> c.particleCloak, v -> c.particleCloak = v)
                            .desc("Particles trail behind your back like a cape. Only you see it.");
                    b.dropdown("  Particle", CLOAK_TYPES, () -> c.particleCloakType, v -> c.particleCloakType = v);
                    b.color("  Dust Color", () -> c.particleCloakColor, v -> c.particleCloakColor = v);
                    b.slider("  Density", 1f, 10f, () -> (float) c.particleCloakDensity, v -> c.particleCloakDensity = Math.round(v));
                    b.slider("  Motion", 0f, 2f, () -> c.particleCloakMotion, v -> c.particleCloakMotion = v, 2);
                    b.toggle("  Also In First Person", () -> c.particleCloakFirstPerson, v -> c.particleCloakFirstPerson = v);
                    b.toggle("Colored Names", () -> c.coloredNames, v -> c.coloredNames = v)
                            .desc("Recolors name tags: your own and other TeslaMaps users (known through the cape server).");
                    b.color("  Own Name Color", () -> c.coloredNameSelf, v -> c.coloredNameSelf = v);
                    b.toggle("  Color TeslaMaps Users", () -> c.coloredNameUsers, v -> c.coloredNameUsers = v);
                    b.color("  TeslaMaps User Color", () -> c.coloredNameUserColor, v -> c.coloredNameUserColor = v);
                });
    }

    private static void tickCloak() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        TeslaMapsConfig c = cfg();
        if (p.isInvisible() || p.isSpectator()) return;
        if (!c.particleCloakFirstPerson && mc.options.getCameraType().isFirstPerson()) return;
        ParticleOptions opt = particle(c);
        double yaw = Math.toRadians(p.yBodyRot);
        double bx = Math.sin(yaw) * 0.3, bz = -Math.cos(yaw) * 0.3;
        double sx = Math.cos(yaw), sz = Math.sin(yaw);
        double h = p.isCrouching() ? 1.1 : 1.4;
        double m = c.particleCloakMotion;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < Math.max(1, c.particleCloakDensity); i++) {
            double side = r.nextDouble(-0.3, 0.3);
            double up = r.nextDouble(0.2, h);
            double back = (h - up) * 0.25;
            double x = p.getX() + bx * (1 + back * 3) + sx * side;
            double y = p.getY() + up;
            double z = p.getZ() + bz * (1 + back * 3) + sz * side;
            mc.level.addParticle(opt, x, y, z, bx * 0.1 * m, 0.02 * m, bz * 0.1 * m);
        }
    }

    private static ParticleOptions particle(TeslaMapsConfig c) {
        return switch (c.particleCloakType) {
            case "Flame" -> ParticleTypes.FLAME;
            case "Soul Flame" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "End Rod" -> ParticleTypes.END_ROD;
            case "Heart" -> ParticleTypes.HEART;
            case "Cherry Leaves" -> ParticleTypes.CHERRY_LEAVES;
            case "Snowflake" -> ParticleTypes.SNOWFLAKE;
            case "Witch" -> ParticleTypes.WITCH;
            case "Portal" -> ParticleTypes.PORTAL;
            case "Electric Spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "Note" -> ParticleTypes.NOTE;
            case "Totem" -> ParticleTypes.TOTEM_OF_UNDYING;
            default -> new DustParticleOptions(TeslaMapsConfig.parseColor(c.particleCloakColor) & 0xFFFFFF, 1f);
        };
    }

    /** From EntityRendererNameMixin, render thread. */
    public static Component recolor(Entity e, Component tag) {
        TeslaMapsConfig c = cfg();
        if (tag == null || !c.coloredNames || !(e instanceof Player p) || !c.section("Render")) return tag;
        Minecraft mc = Minecraft.getInstance();
        String color;
        if (p == mc.player) color = c.coloredNameSelf;
        else if (c.coloredNameUsers && users.contains(p.getId())) color = c.coloredNameUserColor;
        else return tag;
        Cached hit = nameCache.get(p.getId());
        if (hit != null && hit.original.equals(tag)) return hit.result;
        Component out = rebuild(tag, p.getGameProfile().name(), TextColor.fromRgb(TeslaMapsConfig.parseColor(color) & 0xFFFFFF));
        nameCache.put(p.getId(), new Cached(tag, out));
        return out;
    }

    private static Component rebuild(Component tag, String name, TextColor color) {
        MutableComponent out = Component.empty();
        boolean[] done = {false};
        tag.visit((style, text) -> {
            int i = done[0] ? -1 : text.indexOf(name);
            if (i < 0) {
                out.append(Component.literal(text).setStyle(style));
            } else {
                done[0] = true;
                if (i > 0) out.append(Component.literal(text.substring(0, i)).setStyle(style));
                out.append(Component.literal(name).setStyle(style.withColor(color)));
                if (i + name.length() < text.length())
                    out.append(Component.literal(text.substring(i + name.length())).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return done[0] ? out : tag;
    }
}
