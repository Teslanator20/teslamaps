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
import com.teslamaps.screen.MapConfigScreen;
import com.teslamaps.screen.config.ConfigBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class FeatureRegistry {

    record Tick(Feature f, String perf, Runnable r, boolean always) {}
    record World(Feature f, String perf, Consumer<WorldCtx> r) {}
    record Hud(Feature f, String id, HudElement r, InventoryMode mode) {}
    record Chat(Feature f, String perf, Consumer<String> r, boolean serverOnly) {}
    record Config(String category, Consumer<ConfigBuilder> build) {}
    record Category(String name, String sidebarSection, boolean gated) {}

    public record WorldCtx(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {}

    enum InventoryMode { ALWAYS, FOREGROUND, HIDDEN }

    static final List<Tick> TICKS = new ArrayList<>();
    static final List<World> WORLD = new ArrayList<>();
    static final List<Hud> FOREGROUND = new ArrayList<>();
    static final List<Chat> CHAT = new ArrayList<>();
    static final List<Config> CONFIG = new ArrayList<>();
    static final List<Category> CATEGORIES = new ArrayList<>();

    private FeatureRegistry() {}

    /** Once, from onInitializeClient. */
    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                c -> com.teslamaps.features.tracker.Tracker.saveAll());
        CoreFeatures.register();
        CoreHudElements.register();
        Features.register();
    }

    static void addHud(Feature f, String id, HudElement r, InventoryMode mode) {
        Hud h = new Hud(f, id, r, mode);
        HUD_IDS.add(id);
        if (mode == InventoryMode.FOREGROUND) FOREGROUND.add(h);
        // in front of PLAYER_LIST = drawn under the tab list
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, id), Perf.hud(id, (g, d) -> {
                    if (mode != InventoryMode.ALWAYS && HudForeground.active()) return;
                    if (f.active()) drawShifted(id, r, g, d);
                }));
    }

    private static void drawShifted(String id, HudElement r, GuiGraphicsExtractor g, DeltaTracker d) {
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        float gs = HudEditElement.globalScale();
        HudEditElement e = c.hudClampToScreen || c.hudEdgeAnchors || gs != 1f ? HudEditElement.forHud(id) : null;
        int dx = e == null ? 0 : e.shiftX(), dy = e == null ? 0 : e.shiftY();
        if (e == null) gs = 1f;
        com.teslamaps.render.gui.HudStyle.beginPanel(id);
        com.teslamaps.render.gui.HudTextStyle.begin(id);
        try {
            if (dx == 0 && dy == 0 && gs == 1f) { r.extractRenderState(g, d); return; }
            g.pose().pushMatrix();
            g.pose().translate(dx, dy);
            if (gs != 1f) {
                int px = e.pivotX(), py = e.pivotY();
                g.pose().translate(px, py);
                g.pose().scale(gs, gs);
                g.pose().translate(-px, -py);
            }
            r.extractRenderState(g, d);
            g.pose().popMatrix();
        } finally {
            com.teslamaps.render.gui.HudTextStyle.end(g);
            com.teslamaps.render.gui.HudStyle.endPanel();
        }
    }

    static final List<String> HUD_IDS = new ArrayList<>();

    /** Every registered HUD id, for per-panel settings. */
    public static List<String> hudIds() { return HUD_IDS; }

    public static void tick(Minecraft client) {
        com.teslamaps.utils.ServerTickTimer.clientTick();
        com.teslamaps.features.Announce.tick();
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

    public static void world(PoseStack pose, MultiBufferSource buffers, Vec3 cam,
                             net.minecraft.client.renderer.culling.Frustum frustum) {
        if (!com.teslamaps.features.WorldOverlayToggle.beginWorld()) return;
        WorldCtx ctx = new WorldCtx(pose, buffers, cam);
        com.teslamaps.render.ESPRenderer.beginFrame(frustum);
        try {
            for (int i = 0, n = WORLD.size(); i < n; i++) {
                World w = WORLD.get(i);
                if (!w.f.active()) continue;
                Perf.begin(w.perf);
                w.r.accept(ctx);
                Perf.end();
            }
        } finally {
            Perf.begin("ESP.flush");
            com.teslamaps.render.ESPRenderer.endFrame();
            Perf.end();
        }
    }

    /** Panels drawn over the inventory screen; called by HudForeground. */
    public static void foreground(GuiGraphicsExtractor g, DeltaTracker d) {
        for (int i = 0, n = FOREGROUND.size(); i < n; i++) {
            Hud h = FOREGROUND.get(i);
            if (h.f.active()) drawShifted(h.id, h.r, g, d);
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

    /**
     * A config-screen category of its own. Gated = gets an "Enable X" master and works as a
     * {@code section(...)} name. Existing categories need no declaration.
     */
    public static void category(String name, String sidebarSection, boolean gated) {
        CATEGORIES.add(new Category(name, sidebarSection, gated));
    }

    public static boolean isGatedCategory(String name) {
        for (Category c : CATEGORIES) if (c.gated && c.name.equals(name)) return true;
        return false;
    }

    public static List<String> gatedCategories() {
        List<String> out = new ArrayList<>();
        for (Category c : CATEGORIES) if (c.gated) out.add(c.name);
        return out;
    }

    /** Appends every feature's rows; called by MapConfigScreen after its own categories. */
    public static void addConfig(ConfigBuilder b, Map<String, List<MapConfigScreen.SettingsEntry>> categories) {
        for (Category c : CATEGORIES) categories.computeIfAbsent(c.name, k -> new ArrayList<>());
        for (Config c : CONFIG) {
            b.into(categories.computeIfAbsent(c.category, k -> new ArrayList<>()));
            c.build.accept(b);
        }
    }

    /** Puts declared categories into their sidebar group. */
    public static void addSidebar(Map<String, List<String>> sections) {
        for (Category c : CATEGORIES) {
            List<String> list = new ArrayList<>(sections.getOrDefault(c.sidebarSection, List.of()));
            if (!list.contains(c.name)) list.add(c.name);
            sections.put(c.sidebarSection, list);
        }
    }
}
