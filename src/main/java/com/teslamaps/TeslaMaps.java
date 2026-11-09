package com.teslamaps;

import com.teslamaps.command.TMapCommand;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.render.TeslaRenderLayers;
import com.teslamaps.render.TeslaRenderPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeslaMaps implements ClientModInitializer {
    public static final String MOD_ID = "teslamaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("TeslaMaps");

    /** Solver / feature tracing. Silent unless /tmap debug is on. */
    public static void debug(String msg, Object... args) {
        if (TeslaMapsConfig.get().debugMode) LOGGER.info(msg, args);
    }

    private static TeslaMaps instance;

    @Override
    public void onInitializeClient() {
        instance = this;
        long initStart = System.nanoTime();

        TeslaMapsConfig.load();
        com.teslamaps.features.StorageCache.load();
        com.teslamaps.features.ItemIndex.load();
        com.teslamaps.utils.SkyblockItems.load();
        // joining hypixel loads its resource pack, which is when the custom item models appear
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public net.minecraft.resources.Identifier getFabricId() {
                        return net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "item_models");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        com.teslamaps.utils.SkyblockItems.onResourceReload();
                    }
                });
        com.teslamaps.utils.SackItems.load();
        com.teslamaps.utils.SeaCreatureList.load();
        com.teslamaps.features.Updater.init();
        com.teslamaps.features.customize.Customize.init();
        com.teslamaps.screen.customize.CustomizeButton.register();
        com.teslamaps.screen.ContestTimersButton.register();
        com.teslamaps.screen.AuctionDealsButton.register();
        com.teslamaps.features.invbuttons.InventoryButtons.register();

        com.teslamaps.render.gui.ImageBackgrounds.init();
        com.teslamaps.features.capes.CapeManager.init();
        TeslaRenderPipelines.init();
        TeslaRenderLayers.init();
        com.teslamaps.render.gui.TeslaVertexFormats.init();
        com.teslamaps.render.gui.TeslaGuiPipelines.init();
        com.teslamaps.render.gui.ChromaPipelines.init();
        com.teslamaps.render.gui.AnimFades.init();
        com.teslamaps.features.TranslucentLava.registerFluidModel();

        RoomDatabase.getInstance().load();

        com.teslamaps.dungeon.DungeonWaypoints.load();
        com.teslamaps.dungeon.PrinceWaypoints.load();
        com.teslamaps.features.waypoints.RouteStorage.load();
        com.teslamaps.features.GreenhousePlanner.loadStored();

        com.teslamaps.features.AuctionPriceInput.init();
        com.teslamaps.features.SearchOverlay.init();

        net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(data ->
                data instanceof com.teslamaps.features.CompactorPreview.Contents c
                        ? new com.teslamaps.render.gui.CompactorTooltip(c) : null);

        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.MODIFY_COMMAND.register(TMapCommand::expandShortcut);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(
                com.teslamaps.features.GetFromSacks::allowCommand);

        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register(
                (stack, ctx, flag, lines) -> {
                    com.teslamaps.features.EnchantColors.onTooltip(stack, lines);
                    if (TeslaMapsConfig.get().section("Skills")) com.teslamaps.features.SkillOverflow.onTooltip(stack, lines);
                });

        com.teslamaps.registry.FeatureRegistry.init();

        StarredMobESP.init();

        AutoGFS.init();

        com.teslamaps.features.WorldScanner.init();

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
                com.teslamaps.registry.FeatureRegistry.world(context.poseStack(), context.submitNodeCollector(),
                        mc.gameRenderer.mainCamera().position(),
                        context.levelState().cameraRenderState.cullFrustum);
            }
        });
        LevelRenderEvents.END_MAIN.register(context -> {
            com.teslamaps.debug.Perf.begin("ESP.flush");
            com.teslamaps.render.WorldBuffers.INSTANCE.draw();
            com.teslamaps.debug.Perf.end();
        });

        // drop the per-tick entity snapshot; it rebuilds lazily on the first caller
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            com.teslamaps.utils.EntityCache.invalidate();
            com.teslamaps.render.EntityGlow.clear();
            com.teslamaps.esp.StarredMobESP.clearGlowCache();
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                com.teslamaps.features.ChatHistoryPersistence.restore(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                com.teslamaps.features.ChatHistoryPersistence.save(client));

        ClientTickEvents.END_CLIENT_TICK.register(com.teslamaps.registry.FeatureRegistry::tick);

        LOGGER.info("init took {} ms", (System.nanoTime() - initStart) / 1_000_000);
    }

    public static TeslaMaps getInstance() {
        return instance;
    }
}
