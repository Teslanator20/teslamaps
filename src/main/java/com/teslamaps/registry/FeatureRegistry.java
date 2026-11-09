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
import com.teslamaps.render.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
    record Hud(Feature f, String id, HudElement r, InventoryMode mode, HudElement draw) {}
    record Chat(Feature f, String perf, Consumer<String> r, boolean serverOnly) {}
    record Config(String category, Consumer<ConfigBuilder> build, boolean fresh) {}
    record Category(String name, String sidebarSection, boolean gated) {}

    public record WorldCtx(PoseStack pose, MultiBufferSource buffers, Vec3 cam, SubmitNodeCollector collector) {}

    enum InventoryMode { ALWAYS, FOREGROUND, HIDDEN }

    static final List<Tick> TICKS = new ArrayList<>();
    static final List<World> WORLD = new ArrayList<>();
    static final List<Hud> HUDS = new ArrayList<>();
    static final List<Hud> FOREGROUND = new ArrayList<>();
    static final List<Chat> CHAT = new ArrayList<>();
    static final List<Config> CONFIG = new ArrayList<>();
    static final List<Category> CATEGORIES = new ArrayList<>();
    static final List<Feature> ALL = new ArrayList<>();

    // gate cache: refreshed every tick end and on config/world change; only features whose gate
    // was on sit in these lists, and each callback still re-checks active() live before running
    private static List<Tick> ticksOn = List.of();
    private static List<World> worldOn = List.of();
    private static List<Hud> hudOn = List.of();
    private static List<Hud> foregroundOn = List.of();
    private static boolean dirty = true;
    private static boolean seenWorld;
    private static int seenVersion;
    private static com.teslamaps.config.TeslaMapsConfig seenCfg;
    private static boolean hudLayer;

    /** Re-evaluates every gate before the next callback; for state that turns a feature on between ticks. */
    public static void markDirty() {
        dirty = true;
    }

    private static boolean hasWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    private static void ensureFresh(boolean world) {
        if (dirty || world != seenWorld || seenCfg != com.teslamaps.config.TeslaMapsConfig.get()
                || seenVersion != com.teslamaps.config.TeslaMapsConfig.version()) refresh(world);
    }

    private static final List<String> SECTION_NAMES = new ArrayList<>();
    private static boolean[] secOn = new boolean[0];

    static int sectionIndex(String name) {
        int i = SECTION_NAMES.indexOf(name);
        if (i >= 0) return i;
        SECTION_NAMES.add(name);
        return SECTION_NAMES.size() - 1;
    }

    private static void refresh(boolean world) {
        dirty = false;
        seenWorld = world;
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        seenCfg = c;
        seenVersion = com.teslamaps.config.TeslaMapsConfig.version();
        if (secOn.length != SECTION_NAMES.size()) secOn = new boolean[SECTION_NAMES.size()];
        for (int i = 0; i < secOn.length; i++) secOn[i] = c.section(SECTION_NAMES.get(i));
        boolean legitBlocks = com.teslamaps.features.LegitMode.blocksCheats();
        boolean outside = !inSkyblock && c.outsideSbGate;
        boolean changed = false;
        for (int i = 0, n = ALL.size(); i < n; i++) {
            Feature f = ALL.get(i);
            if (!f.gated) continue;
            boolean v = (world || f.always) && gate(f, legitBlocks, outside);
            if (v != f.on) {
                f.on = v;
                changed = true;
            }
        }
        if (changed) rebuild();
    }

    private static boolean gate(Feature f, boolean legitBlocks, boolean outside) {
        try {
            return f.activeCached(secOn, legitBlocks, outside);
        } catch (Throwable e) {
            com.teslamaps.features.ErrorManager.caught(f.name, e);
            return false;
        }
    }

    private static void rebuild() {
        List<Tick> t = new ArrayList<>();
        for (Tick x : TICKS) if (x.f.on) t.add(x);
        List<World> w = new ArrayList<>();
        for (World x : WORLD) if (x.f.on) w.add(x);
        List<Hud> h = new ArrayList<>();
        for (Hud x : HUDS) if (x.f.on) h.add(x);
        List<Hud> fg = new ArrayList<>();
        for (Hud x : FOREGROUND) if (x.f.on) fg.add(x);
        ticksOn = t;
        worldOn = w;
        hudOn = h;
        foregroundOn = fg;
    }

    private static boolean inSkyblock = true;
    private static final java.util.Set<String> OUTSIDE_SB = new java.util.HashSet<>();
    private static int sbTicks = 0;

    /** Central "only in SkyBlock" gate; features picked in the list keep running outside. */
    static boolean blockedOutsideSb(String name) {
        return !inSkyblock && com.teslamaps.config.TeslaMapsConfig.get().outsideSbGate && !OUTSIDE_SB.contains(name);
    }

    private static void updateOutsideSb() {
        com.teslamaps.config.TeslaMapsConfig c = com.teslamaps.config.TeslaMapsConfig.get();
        if (!c.outsideSbGate) { inSkyblock = true; return; }
        if (sbTicks++ % 20 != 0) return;
        inSkyblock = com.teslamaps.utils.SkyblockUtils.isInSkyblock();
        OUTSIDE_SB.clear();
        if (c.outsideSbFeatures != null) OUTSIDE_SB.addAll(c.outsideSbFeatures);
    }

    /** Names of the section-gated features, for the outside-SkyBlock list. */
    public static List<String> featureNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        for (Feature f : ALL) if (f.section != null && !f.always) names.add(f.name);
        return new ArrayList<>(names);
    }

    private FeatureRegistry() {}

    static boolean freshRows = false;

    /** Config rows registered after this call go to /tmap all (features newer than 2.1.77). */
    static void newFeaturesFromHere() {
        freshRows = true;
    }

    /** Once, from onInitializeClient. */
    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                c -> com.teslamaps.features.tracker.Tracker.saveAll());
        CoreFeatures.register();
        CoreHudElements.register();
        Features.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((h, s, c) -> markDirty());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> markDirty());
    }

    static void addHud(Feature f, String id, HudElement r, InventoryMode mode) {
        f.gated = true;
        Hud h = new Hud(f, id, r, mode, Perf.hud(id, (g, d) -> {
            if (f.active()) drawShifted(id, r, g, d);
        }));
        HUD_IDS.add(id);
        HUDS.add(h);
        if (mode == InventoryMode.FOREGROUND) FOREGROUND.add(h);
        if (hudLayer) return;
        hudLayer = true;
        // one layer for every feature HUD, in front of PLAYER_LIST = drawn under the tab list
        HudElementRegistry.attachElementBefore(VanillaHudElements.PLAYER_LIST,
                Identifier.fromNamespaceAndPath(TeslaMaps.MOD_ID, "features"), FeatureRegistry::hud);
    }

    private static void hud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        ensureFresh(hasWorld());
        List<Hud> list = hudOn;
        if (list.isEmpty()) return;
        boolean fg = HudForeground.active();
        for (int i = 0, n = list.size(); i < n; i++) {
            Hud h = list.get(i);
            if (h.mode != InventoryMode.ALWAYS && fg) continue;
            h.draw.extractRenderState(g, d);
        }
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
            try {
                g.pose().translate(dx, dy);
                if (gs != 1f) {
                    int px = e.pivotX(), py = e.pivotY();
                    g.pose().translate(px, py);
                    g.pose().scale(gs, gs);
                    g.pose().translate(-px, -py);
                }
                r.extractRenderState(g, d);
            } finally {
                g.pose().popMatrix();
            }
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
        updateOutsideSb();
        boolean world = client.player != null && client.level != null;
        ensureFresh(world);
        List<Tick> list = ticksOn;
        for (int i = 0, n = list.size(); i < n; i++) {
            Tick t = list.get(i);
            if (!t.always && !world) continue;
            if (!t.f.active()) continue;
            Perf.begin(t.perf);
            try {
                t.r.run();
            } catch (Throwable e) {
                com.teslamaps.features.ErrorManager.caught(t.perf, e);
            }
            Perf.end();
        }
        refresh(world);
    }

    public static void world(PoseStack pose, SubmitNodeCollector collector, Vec3 cam,
                             net.minecraft.client.renderer.culling.Frustum frustum) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        if (!com.teslamaps.features.WorldOverlayToggle.beginWorld()) return;
        ensureFresh(hasWorld());
        List<World> list = worldOn;
        WorldCtx ctx = new WorldCtx(pose, com.teslamaps.render.WorldBuffers.INSTANCE, cam, collector);
        com.teslamaps.render.ESPRenderer.beginFrame(frustum, collector);
        try {
            for (int i = 0, n = list.size(); i < n; i++) {
                World w = list.get(i);
                if (!w.f.active()) continue;
                Perf.begin(w.perf);
                try {
                    w.r.accept(ctx);
                } catch (Throwable e) {
                    com.teslamaps.features.ErrorManager.caught(w.perf, e);
                }
                Perf.end();
            }
        } finally {
            com.teslamaps.render.ESPRenderer.endFrame();
        }
    }

    /** Panels drawn over the inventory screen; called by HudForeground. */
    public static void foreground(GuiGraphicsExtractor g, DeltaTracker d) {
        if (com.teslamaps.features.GlobalRender.off()) return;
        ensureFresh(hasWorld());
        List<Hud> list = foregroundOn;
        for (int i = 0, n = list.size(); i < n; i++) {
            Hud h = list.get(i);
            if (!h.f.active()) continue;
            try {
                drawShifted(h.id, h.r, g, d);
            } catch (Throwable e) {
                com.teslamaps.features.ErrorManager.caught(h.id, e);
            }
        }
    }

    public static void chat(String text, boolean fromServer) {
        for (int i = 0, n = CHAT.size(); i < n; i++) {
            Chat c = CHAT.get(i);
            if (c.serverOnly && !fromServer) continue;
            if (!c.f.active()) continue;
            Perf.begin(c.perf);
            try {
                c.r.accept(text);
            } catch (Throwable e) {
                com.teslamaps.features.ErrorManager.caught(c.perf, e);
            }
            Perf.end();
        }
    }

    /**
     * A config-screen category of its own. Gated = gets an "Enable X" master and works as a
     * {@code section(...)} name. Existing categories need no declaration.
     */
    public static void category(String name, String sidebarSection, boolean gated) {
        for (Category c : CATEGORIES) if (c.name.equals(name)) return;
        CATEGORIES.add(new Category(name, sidebarSection, gated));
    }

    /** A plain heading row between feature rows of a category. */
    public static void label(String category, String text) {
        CONFIG.add(new Config(category, b -> b.label(text), freshRows));
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
        try {
            for (Config c : CONFIG) {
                b.into(categories.computeIfAbsent(c.category, k -> new ArrayList<>()));
                b.freshMode(c.fresh);
                c.build.accept(b);
            }
        } finally {
            b.freshMode(false);
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
