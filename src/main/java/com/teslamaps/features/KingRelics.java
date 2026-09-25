package com.teslamaps.features;

// Ported from Odin's KingRelics.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class KingRelics {

    private enum Relic {
        GREEN("GREEN_KING_RELIC", "§aGreen", 0xFF55FF55, new BlockPos(49, 7, 44)),
        PURPLE("PURPLE_KING_RELIC", "§5Purple", 0xFFAA00AA, new BlockPos(54, 7, 41)),
        BLUE("BLUE_KING_RELIC", "§bBlue", 0xFF5555FF, new BlockPos(59, 7, 44)),
        ORANGE("ORANGE_KING_RELIC", "§6Orange", 0xFFFFAA00, new BlockPos(57, 7, 42)),
        RED("RED_KING_RELIC", "§cRed", 0xFFFF5555, new BlockPos(51, 7, 42));

        final String id, name;
        final int color;
        final BlockPos cauldron;

        Relic(String id, String name, int color, BlockPos cauldron) {
            this.id = id; this.name = name; this.color = color; this.cauldron = cauldron;
        }

        static Relic of(String id) {
            for (Relic r : values()) if (r.id.equals(id)) return r;
            return null;
        }
    }

    private static Relic current = null;
    private static long pickupTick = 0;
    private static boolean spawnAnnounced = false;
    private static Object lastLevel = null;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KingRelics").when(() -> cfg().kingRelics)
                .tick(KingRelics::tick)
                .world(KingRelics::render)
                .serverChat(KingRelics::onChat);
    }

    private static boolean p5() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DungeonManager.getCurrentFloor() == DungeonFloor.M7
                && DungeonManager.isInDungeon() && mc.player.getY() <= 45;
    }

    private static void onChat(String raw) {
        if (!raw.contains("All this, for nothing...")) return;
        if (!"[BOSS] Necron: All this, for nothing...".equals(StringUtil.stripTrim(raw))) return;
        if (DungeonManager.getCurrentFloor() != DungeonFloor.M7) return;
        pickupTick = ServerTickTimer.now();
        spawnAnnounced = false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            current = null; pickupTick = 0; spawnAnnounced = false;
        }
        if (!p5()) { current = null; return; }
        Relic found = null;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36 && found == null; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.is(Items.PLAYER_HEAD)) found = Relic.of(ItemUtil.skyblockId(st));
        }
        current = found;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Relic r = current;
        if (r == null || !cfg().kingRelicBeacon) return;
        ESPRenderer.drawBeaconBeam(pose, r.cauldron, r.color, cam);
    }

    /** useItemOn hook, main thread. */
    public static void onUseBlock(BlockState state, InteractionHand hand) {
        TeslaMapsConfig c = cfg();
        if (!c.kingRelics || !c.kingRelicPlaceTime || hand != InteractionHand.MAIN_HAND) return;
        Relic r = current;
        if (r == null || pickupTick == 0 || !p5()) return;
        if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.ANVIL)) return;
        float time = (ServerTickTimer.now() - pickupTick) / 20f;
        pickupTick = 0;
        String key = r.name();
        float old = c.kingRelicPbs.getOrDefault(key, 9999f);
        String suffix;
        if (time < old) {
            c.kingRelicPbs.put(key, time);
            TeslaMapsConfig.save();
            suffix = old < 9999f ? " §d§l(PB) §8was " + fmt(old) : " §d§l(PB)";
        } else {
            suffix = " §8(PB " + fmt(old) + ")";
        }
        msg(r.name + " relic §7placed in §6" + fmt(time) + suffix);
    }

    /** Main thread. */
    public static void onSetEquipment(ClientboundSetEquipmentPacket packet) {
        TeslaMapsConfig c = cfg();
        if (!c.kingRelics || !c.kingRelicSpawnMsg || pickupTick == 0 || spawnAnnounced || !p5()) return;
        for (var slot : packet.getSlots()) {
            ItemStack st = slot.getSecond();
            if (st.isEmpty() || !st.is(Items.PLAYER_HEAD)) continue;
            Relic r = Relic.of(ItemUtil.skyblockId(st));
            if (r == null) continue;
            spawnAnnounced = true;
            msg(r.name + " relic §7spawned in §6" + fmt((ServerTickTimer.now() - pickupTick) / 20f));
            return;
        }
    }

    private static String fmt(float s) {
        return String.format(Locale.ROOT, "%.2fs", s);
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + text));
    }
}
