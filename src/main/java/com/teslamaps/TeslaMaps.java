package com.teslamaps;

import com.teslamaps.command.TMapCommand;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.database.RoomDatabase;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.features.AutoGFS;
import com.teslamaps.features.LividSolver;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.MapRenderer;
import com.teslamaps.render.TeslaRenderLayers;
import com.teslamaps.render.TeslaRenderPipelines;
import com.teslamaps.scanner.CryptScanner;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.scanner.RoomScanner;
import com.teslamaps.scanner.SecretTracker;
import com.teslamaps.slayer.SlayerHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TeslaMaps implements ClientModInitializer {
    public static final String MOD_ID = "teslamaps";
    public static final Logger LOGGER = LoggerFactory.getLogger("TeslaMaps");

    private static TeslaMaps instance;

    @Override
    public void onInitializeClient() {
        instance = this;

        // Load config
        TeslaMapsConfig.load();

        // Initialize render pipelines (must be early)
        TeslaRenderPipelines.init();
        TeslaRenderLayers.init();

        // Load room database
        RoomDatabase.getInstance().load();

        // Register /tmap command
        ClientCommandRegistrationCallback.EVENT.register(TMapCommand::register);

        // Register HUD render callback
        HudRenderCallback.EVENT.register(MapRenderer::render);
        HudRenderCallback.EVENT.register(SlayerHUD::render);
        // HUD indicators removed - using 3D tracers instead
        HudRenderCallback.EVENT.register(LividSolver::renderHUD);

        // Initialize starred mob ESP
        StarredMobESP.init();

        // Initialize AutoGFS
        AutoGFS.init();

        // Register world render event for 3D tracers/beacons
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (context.consumers() != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) {
                Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
                Vec3d playerEyePos = cameraPos;
                StarredMobESP.renderWorldElements(
                        context.matrices(),
                        context.consumers(),
                        cameraPos,
                        playerEyePos
                );
                LividSolver.renderWorld(
                        context.matrices(),
                        context.consumers(),
                        cameraPos,
                        playerEyePos
                );
            }
        });

        // Register tick event for dungeon detection and scanning
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null) {
                DungeonManager.tick();
                RoomScanner.tick();
                PlayerTracker.tick();
                MapScanner.tick();
                StarredMobESP.tick();
                SecretTracker.tick();
                SlayerHUD.tick();
                AutoGFS.tick();
                LividSolver.tick();
                CryptScanner.tick();
            }
        });
    }

    public static TeslaMaps getInstance() {
        return instance;
    }
}
