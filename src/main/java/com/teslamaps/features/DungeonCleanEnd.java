package com.teslamaps.features;

// Ported from SkyHanni's DungeonCleanEnd.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Guardian;

public final class DungeonCleanEnd {

    private static volatile boolean bossDone;
    private static volatile boolean chestsSpawned;

    private DungeonCleanEnd() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DungeonCleanEnd").when(() -> cfg().dungeonCleanEnd)
                .serverChat(DungeonCleanEnd::onChat)
                .tick(DungeonCleanEnd::tick)
                .config("Hide", b -> {
                    b.label("Clean End");
                    b.toggle("Dungeon Clean End", () -> cfg().dungeonCleanEnd, v -> cfg().dungeonCleanEnd = v)
                            .desc("Once the boss is done, hide every entity except you, your party and the reward chests, plus particles.");
                    b.toggle("  F3 Guardians While Sneaking", () -> cfg().dungeonCleanEndGuardians, v -> cfg().dungeonCleanEndGuardians = v);
                });
    }

    private static void tick() {
        if (!DungeonManager.isInDungeon()) { bossDone = false; chestsSpawned = false; }
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String m = StringUtil.stripTrim(raw);
        boolean end = m.startsWith("☠ Defeated ") || m.equals("> EXTRA STATS <");
        boolean header = m.startsWith("The Catacombs - ") || m.startsWith("Master Mode The Catacombs - ");
        DungeonFloor f = DungeonManager.getCurrentFloor();
        boolean necron = f == DungeonFloor.F7 && m.equals("[BOSS] Necron: All this, for nothing...");
        if (!bossDone && (end || header || necron)) {
            bossDone = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e" + (f != null ? f.getDisplayName() : "Floor") + " done!"));
        }
        if (end || header) chestsSpawned = true;
    }

    private static boolean blocking() {
        return bossDone && cfg().dungeonCleanEnd;
    }

    public static boolean shouldHide(Entity e) {
        if (!bossDone) return false;
        if (!blocking()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) return false;
        if (cfg().dungeonCleanEndGuardians && e instanceof Guardian && mc.player != null && mc.player.isShiftKeyDown()) {
            DungeonFloor f = DungeonManager.getCurrentFloor();
            if (f != null && f.getLevel() == 3) return false;
        }
        if (chestsSpawned && ((e instanceof ArmorStand && !e.hasCustomName()) || e instanceof RemotePlayer)) return false;
        return true;
    }

    public static boolean hideParticle() {
        return bossDone && blocking();
    }

    public static boolean muteSound(SoundEvent sound) {
        if (!bossDone || chestsSpawned || !blocking()) return false;
        return sound.location().getPath().startsWith("block.note_block.");
    }
}
