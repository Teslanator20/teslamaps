package com.teslamaps.slayer;

// Ported from SkyHanni's EndermanSlayerHideParticles.kt and HideMobNames.kt;
// pacified blazes after Skytils' SlayerFeatures
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerHiders {

    private static final String ICONS = ""
            + "";
    private static final Pattern MOB_NAME = Pattern.compile("\\[Lv\\d+] (?:[" + ICONS + "]+)? ?"
            + "(?:Zombie|Zombie Villager|Crypt Ghoul|Graveyard Zombie|Dasher Spider|Weaver Spider|Splitter Spider"
            + "|Voracious Spider|Silverfish|Wolf|Howling Spirit|Pack Spirit|Enderman|Voidling Fanatic|Blaze"
            + "|Mutated Blaze|Bezal|Smoldering Blaze|Flaming Spider) (.+)/(.+)❤");

    private static final HashMap<String, Boolean> nameCache = new HashMap<>();
    private static IntOpenHashSet hiddenNames = new IntOpenHashSet();
    private static double[] endermen = new double[0];
    private static int endermanCount;
    private static boolean inEnd;
    private static boolean pacified;
    private static int ticks;

    private SlayerHiders() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerHiders").section("Combat")
                .when(() -> cfg().slayerHideEndermanParticles || cfg().slayerHideMobNames
                        || cfg().slayerHidePacifiedBlazes)
                .tick(SlayerHiders::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Slayer Clutter");
                    b.toggle("Hide Enderman Particles", () -> c.slayerHideEndermanParticles, v -> c.slayerHideEndermanParticles = v)
                            .desc("In The End: smoke, flame and witch particles within 3 blocks of an Enderman are dropped.")
                            .tags("slayer", "voidgloom", "zealot", "particles");
                    b.toggle("Hide Slayer Mob Names", () -> c.slayerHideMobNames, v -> c.slayerHideMobNames = v)
                            .desc("Hides name tags of normal slayer mobs (zombies, spiders, wolves, endermen, blazes) while they are at full or 0 HP.")
                            .tags("slayer", "nametag", "hide");
                    b.toggle("Hide Pacified Blazes", () -> c.slayerHidePacifiedBlazes, v -> c.slayerHidePacifiedBlazes = v)
                            .desc("With Smoldering Polarization during your Inferno Demonlord fight, blazes more than 3 blocks away (not the boss) are not rendered.")
                            .tags("slayer", "inferno", "blaze", "hide");
                    SlayerSpawnHiders.config(b);
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) inEnd = SkyblockUtils.isInArea("The End");

        if (c.slayerHideEndermanParticles && inEnd) {
            List<EnderMan> list = EntityCache.byType(EnderMan.class);
            if (endermen.length < list.size() * 3) endermen = new double[list.size() * 3];
            int n = 0;
            for (EnderMan e : list) {
                endermen[n * 3] = e.getX();
                endermen[n * 3 + 1] = e.getY() + 1.45;
                endermen[n * 3 + 2] = e.getZ();
                n++;
            }
            endermanCount = n;
        } else endermanCount = 0;

        boolean fight = SlayerQuest.bossActive();
        pacified = c.slayerHidePacifiedBlazes && fight && "Inferno Demonlord".equals(SlayerQuest.boss()) && InfernoHelper.gummyActive();

        if (c.slayerHideMobNames && ticks % 3 == 0) {
            if (mc.level != null && mc.player != null && SkyblockUtils.isInSkyblock()) {
                IntOpenHashSet next = new IntOpenHashSet();
                for (ArmorStand s : EntityCache.namedArmorStands()) {
                    Component n = s.getCustomName();
                    if (n != null && hideName(n.getString())) next.add(s.getId());
                }
                hiddenNames = next;
            } else if (!hiddenNames.isEmpty()) hiddenNames = new IntOpenHashSet();
        } else if (!c.slayerHideMobNames && !hiddenNames.isEmpty()) hiddenNames = new IntOpenHashSet();
    }

    private static boolean hideName(String raw) {
        Boolean cached = nameCache.get(raw);
        if (cached != null) return cached;
        if (nameCache.size() > 2048) nameCache.clear();
        boolean hide = false;
        if (raw.indexOf('❤') >= 0) {
            Matcher m = MOB_NAME.matcher(StringUtil.strip(raw).trim());
            if (m.find()) hide = m.group(1).equals(m.group(2)) || m.group(1).equals("0");
        }
        nameCache.put(raw, hide);
        return hide;
    }

    /** From ParticleHiderMixin. */
    public static boolean hideParticle(ParticleOptions options, double x, double y, double z) {
        if (endermanCount == 0 || !cfg().slayerHideEndermanParticles) return false;
        ParticleType<?> t = options.getType();
        if (t != ParticleTypes.LARGE_SMOKE && t != ParticleTypes.FLAME && t != ParticleTypes.WITCH) return false;
        double[] e = endermen;
        for (int i = 0; i < endermanCount; i++) {
            double dx = e[i * 3] - x, dy = e[i * 3 + 1] - y, dz = e[i * 3 + 2] - z;
            if (dx * dx + dy * dy + dz * dz < 9) return true;
        }
        return false;
    }

    /** From EntityRenderDispatcherMixin. */
    public static boolean shouldHide(Entity e) {
        if (e instanceof ArmorStand) return !hiddenNames.isEmpty() && hiddenNames.contains(e.getId()) && cfg().slayerHideMobNames;
        if (pacified && e instanceof Blaze && cfg().slayerHidePacifiedBlazes) {
            Minecraft mc = Minecraft.getInstance();
            return e != SlayerHUD.getBossEntity() && mc.player != null && e.distanceToSqr(mc.player) > 9;
        }
        return false;
    }

}
