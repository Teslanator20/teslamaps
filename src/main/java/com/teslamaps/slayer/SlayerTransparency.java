package com.teslamaps.slayer;

// Ported from SkyHanni's ActiveBossTransparency.kt and HideIrrelevantMobsInSlayerArea.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;

public final class SlayerTransparency {

    private static IntOpenHashSet boss = new IntOpenHashSet();
    private static IntOpenHashSet cubes = new IntOpenHashSet();
    private static int lastHit = -1;
    private static final java.util.HashMap<Integer, Boolean> npcCache = new java.util.HashMap<>();
    private static Object lastLevel;
    private static int ticks;
    private static boolean inDesert;

    private SlayerTransparency() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) lastHit = entity.getId();
            return InteractionResult.PASS;
        });
        Feature.of("SlayerTransparency").section("Combat")
                .when(() -> cfg().slayerBossTransparency || cfg().slayerMagmaCubeTransparency)
                .tick(SlayerTransparency::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Active Boss Transparency", () -> c.slayerBossTransparency, v -> c.slayerBossTransparency = v)
                            .desc("During your own boss fight every other mob turns see-through. Your boss, the mob you hit last and NPCs stay solid.")
                            .tags("slayer", "transparent", "hide", "mobs");
                    b.slider("  Transparency (%)", 15, 70, () -> (float) c.slayerBossTransparencyLevel,
                            v -> c.slayerBossTransparencyLevel = Math.round(v), 0);
                    b.toggle("  Also Players", () -> c.slayerBossTransparencyPlayers, v -> c.slayerBossTransparencyPlayers = v);
                    b.toggle("Transparent Magma Cubes (Blaze Quest)", () -> c.slayerMagmaCubeTransparency, v -> c.slayerMagmaCubeTransparency = v)
                            .desc("Burning Desert with an Inferno Demonlord quest: Magma Cubes and their riders turn see-through.");
                    b.slider("  Cube Transparency (%)", 0, 100, () -> (float) c.slayerMagmaCubeLevel,
                            v -> c.slayerMagmaCubeLevel = Math.round(v), 0);
                });
    }

    /** Alpha 0-255 for LivingEntityAlphaMixin, 255 = untouched. */
    public static int alpha(Entity e) {
        if (boss.isEmpty() && cubes.isEmpty()) return 255;
        int id = e.getId();
        TeslaMapsConfig c = cfg();
        if (!c.section("Combat")) return 255;
        if (c.slayerBossTransparency && boss.contains(id)) return visible(c.slayerBossTransparencyLevel);
        if (c.slayerMagmaCubeTransparency && cubes.contains(id)) return visible(c.slayerMagmaCubeLevel);
        return 255;
    }

    private static int visible(int transparencyPct) {
        return Math.round((100 - Math.max(0, Math.min(100, transparencyPct))) * 2.55f);
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            npcCache.clear();
        }
        TeslaMapsConfig c = cfg();
        IntOpenHashSet nb = new IntOpenHashSet();
        IntOpenHashSet nc = new IntOpenHashSet();

        if (ticks % 20 == 0) inDesert = SkyblockUtils.isInArea("Crimson Isle") && subArea().contains("Burning Desert");
        boolean fight = c.slayerBossTransparency && SlayerQuest.bossActive();
        boolean desert = c.slayerMagmaCubeTransparency && inDesert && "Inferno Demonlord".equals(SlayerQuest.boss());

        if (fight || desert) {
            Entity own = SlayerHUD.getBossEntity();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e == mc.player) continue;
                if (desert && (e instanceof MagmaCube || e.getVehicle() instanceof MagmaCube)) nc.add(e.getId());
                if (!fight || e == own || e.getId() == lastHit) continue;
                if (e.distanceToSqr(mc.player) > 64 * 64) continue;
                if (e instanceof Player p) {
                    if (NametagMobs.isRealPlayer(p)) {
                        if (!c.slayerBossTransparencyPlayers) continue;
                    } else if (isNpc(e)) continue;
                }
                nb.add(e.getId());
            }
        }
        boss = nb;
        cubes = nc;
    }

    private static boolean isNpc(Entity e) {
        if (npcCache.size() > 512) npcCache.clear();
        Boolean known = npcCache.get(e.getId());
        if (known != null) return known;
        ArmorStand tag = NametagMobs.tagForMob(e);
        if (tag == null) return true;
        boolean npc = !tag.getName().getString().contains("❤");
        npcCache.put(e.getId(), npc);
        return npc;
    }

    private static String subArea() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(raw).trim();
            if (s.startsWith("⏣") || s.startsWith("ф")) return s.substring(1).trim();
        }
        return "";
    }
}
