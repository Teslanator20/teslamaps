package com.teslamaps.features.capes;

// Ported from NoammAddons' features/impl/dev/Cosmetics.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Halo and body size. Your own comes from the config; other TeslaMaps users' from optional
 * "halo" / "size" fields on their cape-server row, once the server sends them.
 */
public final class Cosmetics {

    public record Look(int halo, float sx, float sy, float sz) {
        boolean hasHalo() { return halo != -1; }
        boolean sized() { return sx != 1f || sy != 1f || sz != 1f; }
    }

    private static final Map<String, Look> remote = new ConcurrentHashMap<>();
    private static volatile Map<Integer, Look> byEntityId = Map.of();
    private static Feature feature;

    private Cosmetics() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    @SuppressWarnings("unchecked")
    public static void register() {
        feature = Feature.of("Cosmetics").section("Render").when(() -> cfg().cosmetics)
                .tick(Cosmetics::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Cosmetics");
                    b.toggle("Cosmetics", () -> c.cosmetics, v -> c.cosmetics = v)
                            .desc("Halo and body size. Yours is only visible to you until the cape server syncs it.");
                    b.toggle("  Halo", () -> c.cosmeticHalo, v -> c.cosmeticHalo = v);
                    b.color("    Halo Color", () -> c.cosmeticHaloColor, v -> c.cosmeticHaloColor = v);
                    b.toggle("  Custom Size", () -> c.cosmeticSize, v -> c.cosmeticSize = v);
                    b.slider("    Size X", 0.2f, 2f, () -> c.cosmeticSizeX, v -> c.cosmeticSizeX = v, 2);
                    b.slider("    Size Y", -2f, 2f, () -> c.cosmeticSizeY, v -> c.cosmeticSizeY = v, 2)
                            .desc("Negative = upside down");
                    b.slider("    Size Z", 0.2f, 2f, () -> c.cosmeticSizeZ, v -> c.cosmeticSizeZ = v, 2);
                    b.toggle("  Show Other Users' Cosmetics", () -> c.cosmeticShowOthers, v -> c.cosmeticShowOthers = v);
                });
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof AvatarRenderer<?>) {
                helper.register(new HaloLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) renderer));
            }
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        Map<Integer, Look> next = new HashMap<>();
        Look own = own(c);
        if (own != null) next.put(mc.player.getId(), own);
        if (c.cosmeticShowOthers && !remote.isEmpty()) {
            int self = mc.player.getId();
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p.getId() == self) continue;
                Look l = remote.get(p.getUUID().toString().replace("-", ""));
                if (l != null) next.put(p.getId(), l);
            }
        }
        byEntityId = next.isEmpty() ? Map.of() : next;
    }

    private static Look own(TeslaMapsConfig c) {
        int halo = c.cosmeticHalo ? TeslaMapsConfig.parseColor(c.cosmeticHaloColor) & 0xFFFFFF : -1;
        float sy = c.cosmeticSizeY;
        if (Math.abs(sy) < 0.1f) sy = sy < 0 ? -0.1f : 0.1f;
        Look l = c.cosmeticSize ? new Look(halo, c.cosmeticSizeX, sy, c.cosmeticSizeZ) : new Look(halo, 1f, 1f, 1f);
        return l.hasHalo() || l.sized() ? l : null;
    }

    static Look forId(int id) {
        Map<Integer, Look> m = byEntityId;
        if (m.isEmpty() || !cfg().cosmetics || feature == null || !feature.active()) return null;
        return m.get(id);
    }

    /** Called from CapeSync for each lookup row; absent fields clear that player's look. */
    static void readRow(String uuid, JsonObject row) {
        int halo = -1;
        float sx = 1f, sy = 1f, sz = 1f;
        try {
            JsonElement h = row.get("halo");
            if (h != null && !h.isJsonNull()) {
                String s = h.getAsString().replace("#", "");
                if (s.matches("[0-9a-fA-F]{6}")) halo = Integer.parseInt(s, 16);
            }
            JsonElement z = row.get("size");
            if (z != null && !z.isJsonNull()) {
                String[] p = z.getAsString().split(",");
                if (p.length == 3) {
                    sx = clamp(Float.parseFloat(p[0].trim()), 0.2f, 2f);
                    sy = Float.parseFloat(p[1].trim());
                    sy = Math.abs(sy) < 0.1f ? 1f : clamp(sy, -2f, 2f);
                    sz = clamp(Float.parseFloat(p[2].trim()), 0.2f, 2f);
                }
            }
        } catch (RuntimeException ignored) {
        }
        Look l = new Look(halo, sx, sy, sz);
        String key = uuid.toLowerCase(Locale.ROOT);
        if (l.hasHalo() || l.sized()) remote.put(key, l);
        else remote.remove(key);
    }

    static void forget(String uuid) {
        remote.remove(uuid);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? 1f : Math.max(lo, Math.min(hi, v));
    }

    /** From AvatarScaleMixin at the head of AvatarRenderer.scale. */
    public static void scale(AvatarRenderState state, PoseStack pose) {
        Look l = forId(state.id);
        if (l == null || !l.sized()) return;
        if (l.sy() < 0) pose.translate(0f, l.sy() * 2f, 0f);
        pose.scale(l.sx(), l.sy(), l.sz());
    }

    /** From EntityNametagMixin: keeps the name above a resized head or a halo. */
    public static void adjustNametag(Entity e, EntityRenderState state) {
        if (!(e instanceof AbstractClientPlayer) || state.nameTagAttachment == null) return;
        Look l = forId(e.getId());
        if (l == null) return;
        Vec3 a = state.nameTagAttachment;
        double y = (a.y + 0.15) * (l.sized() ? Math.abs(l.sy()) : 1f) + (l.hasHalo() ? 0.15 : 0);
        state.nameTagAttachment = new Vec3(a.x, y, a.z);
    }
}
