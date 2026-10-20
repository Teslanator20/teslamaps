package com.teslamaps.adm.leap.pov;

// Ported from ADM's pov/PovCapture.kt (mc/26.1.2 branch)
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.leap.AdmLeapMenu;
import com.teslamaps.adm.leap.AdmWorld;
import com.teslamaps.adm.leap.mixin.AdmCameraAccessor;
import com.teslamaps.adm.leap.mixin.AdmGameRendererInvoker;
import com.teslamaps.adm.leap.mixin.AdmMinecraftAccessor;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2f;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Renders the four teammate feeds into offscreen targets and blits them into the leap menu's quarters. */
public final class PovCapture {

    private PovCapture() {}

    private static final int FEEDS = 4;
    private static final int OUT_OF_RANGE_ARGB = 0xFF141414;
    private static final float STANDING_EYE = 1.62f;
    private static final long RANGE_GRACE_NANOS = 2_000_000_000L;

    private static final RenderTarget[] feeds = new RenderTarget[FEEDS];
    private static final boolean[] empty = {true, true, true, true};
    private static final boolean[] outOfRange = new boolean[FEEDS];
    private static int feedWidth, feedHeight, nextFeed, blitsSubmitted;
    private static boolean capturing, freeRequested, skippedMainPass;
    public static volatile boolean disabledForSession;
    private static double lastCostMs;
    private static Marker standIn;
    private static final Map<String, UUID> seenAs = new HashMap<>();

    public static boolean capturing() { return capturing; }

    public static void requestFree() { freeRequested = true; }

    public static void onWorldChange() {
        freeRequested = true;
        standIn = null;
        seenAs.clear();
    }

    public static void releaseNow() { freeFeedsNow(); }

    public static String costLine() {
        if (!TeslaMapsConfig.get().admPovShowCost || !PovPreviews.wants()) return null;
        int live = 0;
        for (int i = 0; i < FEEDS; i++) if (!empty[i] && !outOfRange[i]) live++;
        return "§7POV §f" + String.format(java.util.Locale.ROOT, "%.1f", lastCostMs) + "§7ms  §f" + live + "§7/4";
    }

    // ------------------------------------------------------------------ GUI

    public static void onScreenExtract(GuiGraphicsExtractor gfx) {
        blitsSubmitted = 0;
        if (disabledForSession) return;
        Minecraft mc = Minecraft.getInstance();
        if (!PovPreviews.wants() || mc.level == null || mc.player == null) {
            freeFeedsNow();
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        float scale = TeslaMapsConfig.get().admPovResolution;
        int width = (int) ((main.width / 2) * scale);
        int height = (int) ((main.height / 2) * scale);
        if (width < 16 || height < 16) return;
        ensureFeeds(width, height);
        AdmLeapMenu.Mate[] mates = AdmLeapMenu.teammates();
        for (int i = 0; i < FEEDS; i++) empty[i] = mates[i] == null;
        submitBlits(gfx);
        redrawKeptHuds(gfx);
    }

    private static void submitBlits(GuiGraphicsExtractor gfx) {
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        int gw = gfx.guiWidth(), gh = gfx.guiHeight();
        Matrix3x2f pose = new Matrix3x2f(gfx.pose());
        float opacity = TeslaMapsConfig.get().admPovOpacity;
        boolean opaque = opacity >= 1f;
        var pipeline = opaque ? RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND : RenderPipelines.GUI_TEXTURED;
        int tint = opaque ? -1 : (Math.max(0, Math.min(255, (int) (opacity * 255f))) << 24) | 0xFFFFFF;
        for (int i = 0; i < FEEDS; i++) {
            if (empty[i] || feeds[i] == null) continue;
            GpuTextureView view = feeds[i].getColorTextureView();
            if (view == null) continue;
            int col = i % 2, row = i / 2;
            int x0 = col == 0 ? 0 : gw / 2, x1 = col == 0 ? gw / 2 : gw;
            int y0 = row == 0 ? 0 : gh / 2, y1 = row == 0 ? gh / 2 : gh;
            gfx.guiRenderState.addGuiElement(new BlitRenderState(pipeline, TextureSetup.singleTexture(view, sampler), pose,
                    x0, y0, x1, y1, 0f, 1f, 1f, 0f, tint, null));
            blitsSubmitted++;
        }
    }

    private static Field hudOnField, hudIdField, hudDrawField;

    @SuppressWarnings("unchecked")
    private static void redrawKeptHuds(GuiGraphicsExtractor gfx) {
        try {
            if (hudOnField == null) {
                hudOnField = com.teslamaps.registry.FeatureRegistry.class.getDeclaredField("hudOn");
                hudOnField.setAccessible(true);
            }
            List<Object> list = (List<Object>) hudOnField.get(null);
            if (list == null || list.isEmpty()) return;
            DeltaTracker d = Minecraft.getInstance().getDeltaTracker();
            for (Object h : list) {
                if (hudIdField == null) {
                    hudIdField = h.getClass().getDeclaredField("id");
                    hudIdField.setAccessible(true);
                    hudDrawField = h.getClass().getDeclaredField("draw");
                    hudDrawField.setAccessible(true);
                }
                String id = (String) hudIdField.get(h);
                if (id == null || id.equals("admpovcost") || !PovPreviews.keepsHud(id)) continue;
                try {
                    ((net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement) hudDrawField.get(h)).extractRenderState(gfx, d);
                } catch (Throwable t) {
                    TeslaMaps.LOGGER.debug("[adm] pov keep hud {} failed", id, t);
                }
            }
        } catch (ReflectiveOperationException e) {
            TeslaMaps.LOGGER.debug("[adm] pov keep huds unavailable", e);
        }
    }

    // ------------------------------------------------------------------ render phase

    /** HEAD of GameRenderer.renderLevel: true cancels the main world render (Skip Own View). */
    public static boolean beforeLevelRender(DeltaTracker delta) {
        if (capturing || disabledForSession) return false;
        skippedMainPass = false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.admPovSkipOwnView || c.admPovOpacity < 1f || !canCapture()) return false;
        if (blitsSubmitted < FEEDS) return false;
        if (runPasses(delta) == 0) return false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture color = main.getColorTexture(), depth = main.getDepthTexture();
        if (color != null && depth != null) RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, 0xFF000000, depth, 1.0);
        blitsSubmitted = 0;
        skippedMainPass = true;
        return true;
    }

    /** RETURN of GameRenderer.renderLevel. */
    public static void afterLevelRender(DeltaTracker delta) {
        if (capturing || disabledForSession) return;
        if (skippedMainPass) { skippedMainPass = false; return; }
        if (blitsSubmitted == 0 || !canCapture()) return;
        blitsSubmitted = 0;
        runPasses(delta);
    }

    private static boolean canCapture() {
        Minecraft mc = Minecraft.getInstance();
        return PovPreviews.wants() && mc.level != null && mc.player != null && feeds[0] != null;
    }

    private static int runPasses(DeltaTracker delta) {
        long started = System.nanoTime();
        int ran = 0;
        capturing = true;
        try {
            int n = Math.max(1, Math.min(FEEDS, TeslaMapsConfig.get().admPovPerFrame));
            for (int k = 0; k < n; k++) {
                renderFeed(nextFeed, delta);
                nextFeed = (nextFeed + 1) % FEEDS;
                ran++;
            }
            restoreMainCamera(delta);
            EntityCullingBridge.requestRecull();
        } catch (Throwable t) {
            fail(t);
            try { restoreMainCamera(delta); } catch (Throwable ignored) { }
        } finally {
            capturing = false;
        }
        lastCostMs = (System.nanoTime() - started) / 1_000_000.0;
        return ran;
    }

    private static Player findByName(String name) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (Player p : level.players()) if (p.getName().getString().equalsIgnoreCase(name)) return p;
        return null;
    }

    private static void renderFeed(int index, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (empty[index]) return;
        RenderTarget feed = feeds[index];
        if (feed == null) return;
        AdmLeapMenu.Mate slot = AdmLeapMenu.teammates()[index];
        if (slot == null) return;

        Player loaded = findByName(slot.name());
        if (loaded != null && loaded.isRemoved()) loaded = null;
        if (loaded != null) seenAs.put(slot.name(), loaded.getUUID());
        Entity target;
        if (loaded == null) target = recentStandIn(slot);
        else if (loaded == mc.player || !loaded.isAlive()) target = null;
        else target = loaded;
        if (target == null) {
            if (!outOfRange[index]) {
                outOfRange[index] = true;
                GpuTexture tex = feed.getColorTexture();
                if (tex != null) RenderSystem.getDevice().createCommandEncoder().clearColorTexture(tex, OUT_OF_RANGE_ARGB);
            }
            return;
        }
        outOfRange[index] = false;

        GameRenderer gameRenderer = mc.gameRenderer;
        AdmGameRendererInvoker invoker = (AdmGameRendererInvoker) gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        AdmCameraAccessor cam = (AdmCameraAccessor) camera;
        var window = mc.getWindow();
        Options options = mc.options;

        Entity savedEntity = mc.getCameraEntity();
        int savedW = window.getWidth(), savedH = window.getHeight();
        CameraType savedType = options.getCameraType();
        boolean savedHide = options.hideGui;
        float savedEye = cam.adm$getEyeHeight(), savedEyeOld = cam.adm$getEyeHeightOld();
        RenderTarget savedTarget = mc.getMainRenderTarget();
        PovPose.Restore pose = null;
        Boolean cullingWas = null;
        try {
            float worldPartial = delta.getGameTimeDeltaPartialTick(false);
            if (target instanceof Player p) pose = PovPose.begin(p, PovPreviews.mode(), TeslaMapsConfig.get().admPovSmoothingTicks);
            float eye = target instanceof Player p ? p.getEyeHeight() : STANDING_EYE;

            mc.setCameraEntity(target);
            options.setCameraType(CameraType.FIRST_PERSON);
            options.hideGui = true;
            window.setWidth(feed.width);
            window.setHeight(feed.height);
            cam.adm$setEyeHeight(eye);
            cam.adm$setEyeHeightOld(eye);
            ((AdmMinecraftAccessor) mc).adm$setMainRenderTarget(feed);

            camera.update(delta);
            cullingWas = EntityCullingBridge.disable();
            invoker.adm$extractWindow();
            invoker.adm$extractOptions();
            invoker.adm$extractCamera(delta, worldPartial, camera.getCameraEntityPartialTicks(delta));
            gameRenderer.getGameRenderState().levelRenderState.reset();
            mc.levelRenderer.extractLevel(delta, camera, worldPartial);
            gameRenderer.renderLevel(delta);
        } finally {
            ((AdmMinecraftAccessor) mc).adm$setMainRenderTarget(savedTarget);
            window.setWidth(savedW);
            window.setHeight(savedH);
            options.setCameraType(savedType);
            options.hideGui = savedHide;
            cam.adm$setEyeHeight(savedEye);
            cam.adm$setEyeHeightOld(savedEyeOld);
            mc.setCameraEntity(savedEntity);
            if (pose != null) pose.restore();
            EntityCullingBridge.restore(cullingWas);
        }
    }

    private static void restoreMainCamera(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        GameRenderer gr = mc.gameRenderer;
        Camera camera = gr.getMainCamera();
        AdmGameRendererInvoker invoker = (AdmGameRendererInvoker) gr;
        camera.update(delta);
        float worldPartial = delta.getGameTimeDeltaPartialTick(false);
        invoker.adm$extractWindow();
        invoker.adm$extractOptions();
        invoker.adm$extractCamera(delta, worldPartial, camera.getCameraEntityPartialTicks(delta));
    }

    // ------------------------------------------------------------------ feeds

    private static void ensureFeeds(int width, int height) {
        boolean all = true;
        for (RenderTarget f : feeds) if (f == null) all = false;
        if (!freeRequested && feedWidth == width && feedHeight == height && all) return;
        freeFeedsNow();
        feedWidth = width;
        feedHeight = height;
        for (int i = 0; i < FEEDS; i++) {
            TextureTarget feed = new TextureTarget("teslamaps:adm_pov" + i, width, height, true);
            GpuTexture tex = feed.getColorTexture();
            if (tex != null) RenderSystem.getDevice().createCommandEncoder().clearColorTexture(tex, OUT_OF_RANGE_ARGB);
            feeds[i] = feed;
        }
        java.util.Arrays.fill(outOfRange, false);
        nextFeed = 0;
    }

    private static void freeFeedsNow() {
        freeRequested = false;
        boolean any = false;
        for (RenderTarget f : feeds) if (f != null) any = true;
        if (!any) return;
        for (int i = 0; i < FEEDS; i++) {
            if (feeds[i] != null) feeds[i].destroyBuffers();
            feeds[i] = null;
        }
        feedWidth = 0;
        feedHeight = 0;
        nextFeed = 0;
    }

    // ------------------------------------------------------------------ out of range

    private static Entity recentStandIn(AdmLeapMenu.Mate p) {
        if (p.dead() || AdmWorld.isDead(p.name())) return null;
        UUID id = seenAs.get(p.name());
        if (id == null) return null;
        PovInterpolator.Sample s = PovPose.lastSeen(id);
        if (s == null || System.nanoTime() - s.nanos() > RANGE_GRACE_NANOS) return null;
        return standIn(s.x(), s.y(), s.z(), s.headYaw(), s.pitch());
    }

    private static Entity standIn(double x, double y, double z, float yaw, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        Marker e = standIn;
        if (e == null || e.level() != level) { e = new Marker(EntityType.MARKER, level); standIn = e; }
        e.setPos(x, y, z);
        e.xo = x; e.yo = y; e.zo = z;
        e.setYRot(yaw); e.yRotO = yaw;
        e.setXRot(pitch); e.xRotO = pitch;
        return e;
    }

    private static void fail(Throwable t) {
        disabledForSession = true;
        freeRequested = true;
        TeslaMaps.LOGGER.error("[adm] pov preview failed - disabled for this session", t);
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§8[§6ADM§8] §cPOV previews hit an error and are off for this session §7(see the log)."));
    }
}
