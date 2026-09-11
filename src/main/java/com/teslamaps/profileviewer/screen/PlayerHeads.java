package com.teslamaps.profileviewer.screen;

import com.mojang.authlib.GameProfile;
import com.teslamaps.api.PvApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A GameProfile straight from a uuid has no texture property, so the skin manager would only
 * ever hand back steve. Resolve the full profile first, then keep the lookup around.
 */
public class PlayerHeads {
    private static final Map<String, Supplier<PlayerSkin>> lookups = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> pending = new ConcurrentHashMap<>();
    private static final Map<String, String> uuids = new ConcurrentHashMap<>();

    public static PlayerSkin playerSkin(String uuid, String name) {
        Supplier<PlayerSkin> lookup = lookups.get(uuid);
        if (lookup != null) return lookup.get();
        skin(uuid, name);
        return null;
    }

    public static Identifier skin(String uuid, String name) {
        Supplier<PlayerSkin> lookup = lookups.get(uuid);
        if (lookup != null) return lookup.get().body().texturePath();

        if (pending.putIfAbsent(uuid, true) == null) {
            CompletableFuture.runAsync(() -> {
                try {
                    UUID id = UUID.fromString(PvApi.dashed(uuid));
                    GameProfile resolved = Minecraft.getInstance().services().profileResolver()
                            .fetchById(id).orElse(new GameProfile(id, name));
                    Minecraft.getInstance().execute(() ->
                            lookups.put(uuid, Minecraft.getInstance().getSkinManager().createLookup(resolved, false)));
                } catch (Exception ignored) {
                }
            });
        }
        return null;
    }

    /** Same, but starting from a name - used by the recent player column. */
    public static void faceByName(GuiGraphicsExtractor ctx, String name, int x, int y, int size) {
        String uuid = uuids.get(name.toLowerCase());
        if (uuid != null) {
            face(ctx, uuid, name, x, y, size);
            return;
        }
        if (pending.putIfAbsent("name:" + name.toLowerCase(), true) == null) {
            com.teslamaps.api.HypixelApi.nameToUuid(name).thenAccept(id -> {
                if (id != null) uuids.put(name.toLowerCase(), id);
            });
        }
    }

    /** Face plus hat layer, the way every vanilla head icon is drawn. */
    public static void face(GuiGraphicsExtractor ctx, String uuid, String name, int x, int y, int size) {
        Identifier texture = skin(uuid, name);
        if (texture == null) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
    }

    /** Whole skin front, roughly what NEU's rotating player render occupies. */
    public static void body(GuiGraphicsExtractor ctx, String uuid, String name, int x, int y, int scale) {
        Identifier t = skin(uuid, name);
        if (t == null) return;

        int head = 8 * scale;
        int torsoW = 8 * scale;
        int limbW = 4 * scale;
        int limbH = 12 * scale;

        int bodyX = x + limbW;

        part(ctx, t, bodyX, y, head, head, 8, 8, 8, 8);            // head
        part(ctx, t, bodyX, y, head, head, 40, 8, 8, 8);           // hat
        part(ctx, t, bodyX, y + head, torsoW, limbH, 20, 20, 8, 12);
        part(ctx, t, bodyX, y + head, torsoW, limbH, 20, 36, 8, 12);
        part(ctx, t, x, y + head, limbW, limbH, 44, 20, 4, 12);    // right arm
        part(ctx, t, x, y + head, limbW, limbH, 44, 36, 4, 12);
        part(ctx, t, bodyX + torsoW, y + head, limbW, limbH, 36, 52, 4, 12); // left arm
        part(ctx, t, bodyX + torsoW, y + head, limbW, limbH, 52, 52, 4, 12);
        part(ctx, t, bodyX, y + head + limbH, limbW, limbH, 4, 20, 4, 12);   // right leg
        part(ctx, t, bodyX, y + head + limbH, limbW, limbH, 4, 36, 4, 12);
        part(ctx, t, bodyX + limbW, y + head + limbH, limbW, limbH, 20, 52, 4, 12); // left leg
        part(ctx, t, bodyX + limbW, y + head + limbH, limbW, limbH, 4, 52, 4, 12);
    }

    private static void part(GuiGraphicsExtractor ctx, Identifier texture, int x, int y,
                             int w, int h, float u, float v, int uw, int vh) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, uw, vh, 64, 64);
    }
}
