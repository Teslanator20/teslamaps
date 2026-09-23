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
        if (mode == InventoryMode.FOREGROUND) FOREGROUND.add(h);
        // in front of PLAYER_LIST = drawn under the tab list
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, id), Perf.hud(id, (g, d) -> {
                    if (mode != InventoryMode.ALWAYS && HudForeground.active()) return;
                    if (f.active()) r.extractRenderState(g, d);
                }));
    }

    public static void tick(Minecraft client) {
        com.teslamaps.utils.ServerTickTimer.clientTick();
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
