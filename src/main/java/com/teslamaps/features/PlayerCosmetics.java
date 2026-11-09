package com.teslamaps.features;

// Player size after Odin's PlayerSize.kt / Devonian's PlayerScale
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PlayerCosmetics {

    public static final String[] TARGETS = {"Self", "Real Players", "Everyone"};

    private PlayerCosmetics() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PlayerSize").section("Render").when(() -> cfg().playerSize)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Player Size", () -> c.playerSize, v -> c.playerSize = v)
                            .desc("Scales player models client-side. Negative height turns them upside down.");
                    b.dropdown("  Players", TARGETS, () -> c.playerSizeTarget, v -> c.playerSizeTarget = v)
                            .desc("Real Players skips Hypixel NPCs.");
                    b.slider("  Width", -1f, 3f, () -> c.playerSizeX, v -> c.playerSizeX = v, 2);
                    b.slider("  Height", -1f, 3f, () -> c.playerSizeY, v -> c.playerSizeY = v, 2);
                    b.slider("  Depth", -1f, 3f, () -> c.playerSizeZ, v -> c.playerSizeZ = v, 2);
                });
    }

    /** From PlayerSizeMixin, before the model is drawn. */
    public static void scale(int entityId, PoseStack pose) {
        if (!scales(entityId)) return;
        TeslaMapsConfig c = cfg();
        if (c.playerSizeY < 0) pose.translate(0f, c.playerSizeY * 2, 0f);
        pose.scale(c.playerSizeX, c.playerSizeY, c.playerSizeZ);
    }

    public static boolean scales(int entityId) {
        TeslaMapsConfig c = cfg();
        return c.playerSize && c.section("Render") && matches(entityId, c.playerSizeTarget);
    }

    public static boolean matches(int entityId, String target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (entityId == mc.player.getId()) return true;
        if ("Self".equals(target) || mc.level == null) return false;
        if ("Everyone".equals(target)) return true;
        Entity e = mc.level.getEntity(entityId);
        return e instanceof Player p && p.getUUID().version() == 4;
    }
}
