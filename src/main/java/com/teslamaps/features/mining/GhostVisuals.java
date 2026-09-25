package com.teslamaps.features.mining;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

// Ported from OdinClient's Ghosts.kt: visible ghosts, no charged layer, own HP nametag
public final class GhostVisuals {

    private record Tag(Creeper creeper, String text) {}

    private static Feature feature;
    private static boolean inMines;
    private static final List<Tag> tags = new ArrayList<>();

    private GhostVisuals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GhostVisuals").section("Mining").when(() -> cfg().ghostVisuals)
                .tick(GhostVisuals::tick)
                .world(GhostVisuals::render)
                .config("Mining", b -> {
                    b.toggle("Ghost Visuals", () -> cfg().ghostVisuals, v -> cfg().ghostVisuals = v)
                            .desc("Ghosts in the Dwarven Mines' Mist: make them visible, drop the lightning layer, "
                                    + "show a level/HP nametag (Runic ghosts in purple).")
                            .tags("ghost", "mist", "creeper", "runic", "dwarven");
                    b.toggle("  Show Ghosts", () -> cfg().ghostShow, v -> cfg().ghostShow = v);
                    b.toggle("  Hide Charged Layer", () -> cfg().ghostHideCharged, v -> cfg().ghostHideCharged = v);
                    b.toggle("  HP Nametag", () -> cfg().ghostNametag, v -> cfg().ghostNametag = v);
                });
    }

    private static boolean isGhost(Creeper c) {
        return c.getMaxHealth() >= 1_000_000f || inMines;
    }

    public static void onExtract(Creeper creeper, CreeperRenderState state) {
        TeslaMapsConfig c = cfg();
        if (!c.ghostVisuals || feature == null || !isGhost(creeper) || !feature.active()) return;
        if (c.ghostShow) state.isInvisible = false;
        if (c.ghostHideCharged) state.isPowered = false;
    }

    private static void tick() {
        inMines = SkyblockUtils.getArea().toLowerCase().contains("dwarven");
        tags.clear();
        if (!cfg().ghostNametag) return;
        for (Creeper c : EntityCache.byType(Creeper.class)) {
            if (c.isRemoved() || c.isDeadOrDying() || !isGhost(c)) continue;
            tags.add(new Tag(c, label(c)));
        }
    }

    private static String label(Creeper c) {
        float max = c.getMaxHealth(), hp = c.getHealth();
        boolean runic = max == 4_000_000f;
        String hpColor = runic ? "§d" : hp < max / 2 ? "§e" : "§a";
        return (runic ? "§5[§dLv250§5] §5Runic " : "§8[§7Lv250§8] §c") + "Ghost "
                + hpColor + suffixed(hp) + "§f/" + (runic ? "§5" : "§a") + suffixed(max) + "§c❤";
    }

    private static String suffixed(float n) {
        if (n >= 1_000_000f) {
            float m = n / 1_000_000f;
            return (m == Math.floor(m) ? String.valueOf((int) m) : String.valueOf(Math.round(m * 10) / 10f)) + "M";
        }
        return (int) (n / 1000) + "k";
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (tags.isEmpty()) return;
        for (Tag t : tags) {
            Creeper c = t.creeper();
            if (c.isRemoved()) continue;
            Vec3 pos = c.position().add(0, c.getBbHeight() + 0.5, 0);
            ESPRenderer.drawText(pose, t.text(), pos, 1f, cam);
        }
    }
}
