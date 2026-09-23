package com.teslamaps.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.debug.Perf;
import com.teslamaps.features.HudForeground;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class FeatureRegistry {

    record Tick(Feature f, String perf, Runnable r, boolean always) {}
    record World(Feature f, String perf, Consumer<WorldCtx> r) {}
    record Hud(Feature f, String id, HudElement r, InventoryMode mode) {}
    record Chat(Feature f, String perf, Consumer<String> r, boolean serverOnly) {}

    public record WorldCtx(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {}

    enum InventoryMode { ALWAYS, FOREGROUND, HIDDEN }

    static final List<Tick> TICKS = new ArrayList<>();
    static final List<World> WORLD = new ArrayList<>();
    static final List<Hud> FOREGROUND = new ArrayList<>();
    static final List<Chat> CHAT = new ArrayList<>();

    private FeatureRegistry() {}

    /** Once, from onInitializeClient. */
    public static void init() {
        CoreFeatures.register();
        Features.register();
    }

    static void addHud(Feature f, String id, HudElement r, InventoryMode mode) {
        Hud h = new Hud(f, id, r, mode);
        if (mode == InventoryMode.FOREGROUND) FOREGROUND.add(h);
        // in front of PLAYER_LIST = drawn under the tab list
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, id), Perf.hud(id, (g, d) -> {
                    if (mode != InventoryMode.ALWAYS && HudForeground.active()) return;
                    if (f.active()) r.extractRenderState(g, d);
                }));
    }

    public static void tick(Minecraft client) {
        boolean world = client.player != null && client.level != null;
        for (int i = 0, n = TICKS.size(); i < n; i++) {
            Tick t = TICKS.get(i);
            if (!t.always && !world) continue;
            if (!t.f.active()) continue;
            Perf.begin(t.perf);
            t.r.run();
            Perf.end();
        }
    }

    public static void world(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        WorldCtx ctx = new WorldCtx(pose, buffers, cam);
        for (int i = 0, n = WORLD.size(); i < n; i++) {
            World w = WORLD.get(i);
            if (!w.f.active()) continue;
            Perf.begin(w.perf);
            w.r.accept(ctx);
            Perf.end();
        }
    }

    /** Panels drawn over the inventory screen; called by HudForeground. */
    public static void foreground(GuiGraphicsExtractor g, DeltaTracker d) {
        for (int i = 0, n = FOREGROUND.size(); i < n; i++) {
            Hud h = FOREGROUND.get(i);
            if (h.f.active()) h.r.extractRenderState(g, d);
        }
    }

    public static void chat(String text, boolean fromServer) {
        for (int i = 0, n = CHAT.size(); i < n; i++) {
            Chat c = CHAT.get(i);
            if (c.serverOnly && !fromServer) continue;
            if (!c.f.active()) continue;
            Perf.begin(c.perf);
            c.r.accept(text);
            Perf.end();
        }
    }
}
