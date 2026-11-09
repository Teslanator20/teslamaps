package com.teslamaps.features.waypoints;

// Keys and crosshair placement after Skysoft's WaypointKeybinds.kt / WaypointPlacement.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.RouteEditScreen;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class RouteKeys {

    private static final boolean[] keyDown = new boolean[6];

    private RouteKeys() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RouteKeys").section("Ordered Waypoints").when(() -> cfg().routeEditorExtras)
                .tick(RouteKeys::tick)
                .config("Ordered Waypoints", b -> {
                    b.label("Editor & Keys");
                    b.toggle("Route Editor Extras", () -> cfg().routeEditorExtras, v -> cfg().routeEditorExtras = v)
                            .desc("Editor: Ctrl+Z / Ctrl+Y (64 steps), drag rows by their number to reorder, Add At Crosshair. Plus the keys below.")
                            .tags("undo", "redo", "drag", "keybind", "route");
                    b.keybind("  Edit Looked-At Point Key", () -> cfg().routeEditKey, v -> cfg().routeEditKey = v)
                            .desc("Opens the editor of the loaded route with the waypoint under the crosshair selected");
                    b.keybind("  Place Point Key", () -> cfg().routePlaceKey, v -> cfg().routePlaceKey = v)
                            .desc("Adds a point at your feet, while sneaking at the crosshair block (128 blocks). Saved right away.");
                    b.keybind("  Pause / Resume Key", () -> cfg().routePauseKey, v -> cfg().routePauseKey = v);
                    b.keybind("  Next Point Key", () -> cfg().routeNextKey, v -> cfg().routeNextKey = v);
                    b.keybind("  Previous Point Key", () -> cfg().routePrevKey, v -> cfg().routePrevKey = v);
                    b.keybind("  Stop Route Key", () -> cfg().routeStopKey, v -> cfg().routeStopKey = v);
                });
    }

    private static boolean pressed(int idx, int key, boolean allowed) {
        boolean down = allowed && KeyNames.isDown(key);
        boolean edge = down && !keyDown[idx];
        keyDown[idx] = down;
        return edge;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        boolean free = mc.gui.screen() == null;

        if (pressed(0, c.routeEditKey, free)) openEditor(mc);
        if (pressed(1, c.routePlaceKey, free)) placePoint(mc);
        if (pressed(2, c.routePauseKey, free)) chat(OrderedWaypoints.togglePause());
        if (pressed(3, c.routeNextKey, free)) chat(OrderedWaypoints.skip(1));
        if (pressed(4, c.routePrevKey, free)) chat(OrderedWaypoints.unskip(1));
        if (pressed(5, c.routeStopKey, free)) chat(OrderedWaypoints.unload());
    }

    private static void openEditor(Minecraft mc) {
        String name = OrderedWaypoints.loadedName();
        if (name.isEmpty() || RouteStorage.get(name) == null) {
            chat("§cNo saved route loaded.");
            return;
        }
        mc.gui.setScreen(new RouteEditScreen(null, name, OrderedWaypoints.lookedAt()));
    }

    private static void placePoint(Minecraft mc) {
        BlockPos pos;
        if (mc.player.isShiftKeyDown()) {
            HitResult hit = mc.player.pick(128, 1f, false);
            if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
                chat("§cNo block under the crosshair.");
                return;
            }
            pos = bh.getBlockPos();
        } else {
            pos = mc.player.blockPosition().below();
        }
        chat(OrderedWaypoints.place(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + msg));
    }
}
