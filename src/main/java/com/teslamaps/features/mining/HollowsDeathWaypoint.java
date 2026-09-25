package com.teslamaps.features.mining;

// Concept from Skytils' CHWaypoints "Last Death"
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.Vec3;

public final class HollowsDeathWaypoint {

    private static final int HISTORY = 20;
    private static final BlockPos[] history = new BlockPos[HISTORY];
    private static int head;
    private static BlockPos pending, shown;
    private static Object lastLevel;

    private HollowsDeathWaypoint() {}

    private static boolean on() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chScanner && c.chDeathWaypoint;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("chdeath")
                        .executes(c -> {
                            if (pending != null) {
                                shown = pending;
                                c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §fLast Death waypoint set."));
                            }
                            return 1;
                        })
                        .then(ClientCommands.literal("clear").executes(c -> {
                            shown = null;
                            return 1;
                        })))));
        Feature.of("HollowsDeathWaypoint").section("Mining").when(HollowsDeathWaypoint::on)
                .tickAlways(HollowsDeathWaypoint::tick)
                .serverChat(HollowsDeathWaypoint::onChat)
                .world(HollowsDeathWaypoint::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            pending = shown = null;
            java.util.Arrays.fill(history, null);
        }
        if (mc.player == null || !Mining.inHollows()) return;
        history[head] = mc.player.blockPosition();
        head = (head + 1) % HISTORY;
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (!t.startsWith("☠ You ") || !Mining.inHollows()) return;
        BlockPos p = history[head] != null ? history[head] : history[(head + HISTORY - 1) % HISTORY];
        if (p == null) return;
        pending = p;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eClick to set a §cLast Death §ewaypoint §7("
                + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/tmap chdeath"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eShow the Last Death waypoint")))));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        BlockPos p = shown;
        if (p == null || !Mining.inHollows()) return;
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.blockPosition().distSqr(p) < 9) {
            shown = null;
            return;
        }
        WaypointSet.draw(pose, cam, p, 0xFFFF5555, "§cLast Death", true, true, true);
    }
}
