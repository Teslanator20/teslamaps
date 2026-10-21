// Ported from ADM's p3sim/clear/replay/ReplayClient.kt
package com.teslamaps.adm.sim.clear.replay;

import com.teslamaps.adm.sim.SimNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * The game's side of a replay: while one plays, your mouse, keys and clicks do nothing in the world and your game sends no moves or
 * clicks of its own; your player is put where the recording's was each tick, looking where they looked (eased between ticks), crouching,
 * sprinting, holding and swinging as they did. Chat, commands and the menus still work. Client thread.
 */
public final class ReplayClient {
    private ReplayClient() {}

    public static volatile boolean active = false;

    private static double px = Double.NaN;
    private static double py = 0.0;
    private static double pz = 0.0;
    private static float yaw = 0f;
    private static float pitch = 0f;
    private static float fromYaw = 0f;
    private static float fromPitch = 0f;
    private static int slot = -1;
    private static int flags = 0;
    private static boolean fresh = false;

    public static void init() {
        SimNet.onClient(ReplaySession.NET, ReplayClient::handle);
        ClientTickEvents.END_CLIENT_TICK.register(c -> { if (active) tick(); });
    }

    private static Double toDoubleOrNull(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static void handle(String data) {
        String[] v = data.split(" ", -1);
        Minecraft mc = Minecraft.getInstance();
        switch (v[0]) {
            case "start" -> { active = true; px = Double.NaN; }
            case "stop" -> stop();
            case "p" -> {
                Double x = toDoubleOrNull(v[1]);
                if (x == null) return;
                px = x;
                py = Double.parseDouble(v[2]);
                pz = Double.parseDouble(v[3]);
                yaw = Float.parseFloat(v[4]);
                pitch = Float.parseFloat(v[5]);
                slot = Integer.parseInt(v[6]);
                flags = Integer.parseInt(v[7]);
                fresh = true;
            }
            case "inv" -> mc.execute(() -> {
                LocalPlayer p = mc.player;
                if (p == null) return;
                if (v[1].equals("1")) { if (mc.screen == null) mc.setScreen(new InventoryScreen(p)); }
                else if (mc.screen instanceof InventoryScreen) mc.setScreen(null);
            });
            default -> {}
        }
    }

    public static void stop() { active = false; }

    /** A system chat line as it arrived (see AdsReplayChatMixin), for the report. */
    public static void chat(String text) {
        ReplaySession s = ReplayModule.session;
        if (s != null) s.diff.adsChat(text);
    }

    /** End of the client tick: the recorded pose of this tick. */
    private static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (Double.isNaN(px)) return;
        if (!fresh) return;
        fresh = false;
        fromYaw = p.getYRot();
        fromPitch = p.getXRot();
        p.setPos(px, py, pz);
        p.setDeltaMovement(Vec3.ZERO);
        if (slot >= 0 && slot <= 8 && p.getInventory().getSelectedSlot() != slot) p.getInventory().setSelectedSlot(slot);
        boolean sprint = (flags & 1) != 0;
        if (p.isSprinting() != sprint) p.setSprinting(sprint);
        if ((flags & 8) != 0) p.swing(InteractionHand.MAIN_HAND);
    }

    /** Every frame (instead of the mouse): the look eased from the last tick's to this one's by the frame's place in the tick. */
    public static void look() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (Double.isNaN(px)) return;
        float t = Math.clamp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), 0f, 1f);
        float y = fromYaw + wrap(yaw - fromYaw) * t;
        float x = fromPitch + (pitch - fromPitch) * t;
        p.setYRot(y);
        p.setXRot(x);
        p.yHeadRot = y;
    }

    private static float wrap(float d) {
        float a = d % 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    /** Your crouch for the input (the camera height and the pose), the rest of the keys off. */
    public static boolean crouch() { return (flags & 2) != 0; }

    /** What your game must not send while a replay drives the player: the server gets the recording's instead. */
    public static boolean blocks(Packet<?> pk) {
        return active && (pk instanceof ServerboundMovePlayerPacket || pk instanceof ServerboundPlayerInputPacket || pk instanceof ServerboundPlayerCommandPacket
                || pk instanceof ServerboundUseItemPacket || pk instanceof ServerboundUseItemOnPacket || pk instanceof ServerboundAttackPacket || pk instanceof ServerboundInteractPacket
                || pk instanceof ServerboundPlayerActionPacket || pk instanceof ServerboundSwingPacket || pk instanceof ServerboundSetCarriedItemPacket
                || pk instanceof ServerboundContainerClickPacket || pk instanceof ServerboundContainerClosePacket || pk instanceof ServerboundContainerButtonClickPacket
                // Your own mods' chat: the recorded player's lines are the recording's.
                || pk instanceof ServerboundChatPacket
                || (pk instanceof ServerboundChatCommandPacket c && !ownCommand(c.command()))
                || (pk instanceof ServerboundChatCommandSignedPacket cs && !ownCommand(cs.command())));
    }

    /** The commands that drive ADS itself (the replay, the tick rate) still go through. */
    private static boolean ownCommand(String c) { return c.startsWith("ads") || c.startsWith("tick"); }
}
