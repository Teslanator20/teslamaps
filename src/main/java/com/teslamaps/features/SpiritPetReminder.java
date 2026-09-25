package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reminds you to swap off the Spirit pet (movement) to a damage pet when the boss starts.
public class SpiritPetReminder {

    private static final Pattern EQUIP = Pattern.compile("(?:summoned|equipped) your (?:\\[Lvl \\d+] )?(?<pet>.+?)[!.]");
    private static final Pattern DESPAWN = Pattern.compile("despawned your (?:\\[Lvl \\d+] )?(?<pet>.+?)[!.]");

    private static boolean spiritEquipped = false;
    private static boolean wasInBoss = false;
    private static boolean firedThisRun = false;
    private static boolean runStarted = false;
    private static boolean partyAlerted = false;
    private static int scanCooldown = 0;
    // Skytils DungeonFeatures SPIRIT_PET_TEXTURES
    private static final java.util.Set<String> SPIRIT_TEXTURES = java.util.Set.of(
            "8d9ccc670677d0cebaad4058d6aaf9acfab09abea5d86379a059902f2fe22655",
            "b8f33a49185c07ae21f3b4d5a569acd12ae11157e496cccc26487ed1b09ded3f");

    public static void onChatMessage(String raw) {
        String m = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (m.startsWith("[NPC] Mort: ") || m.equals("Starting in 1 second.")) runStarted = true;
        Matcher d = DESPAWN.matcher(m);
        if (d.find()) { spiritEquipped = false; return; }
        Matcher e = EQUIP.matcher(m);
        if (e.find()) spiritEquipped = e.group("pet").trim().equalsIgnoreCase("Spirit");
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().spiritPetReminder) return;
        if (!DungeonManager.isInDungeon()) {
            wasInBoss = false; firedThisRun = false; runStarted = false; partyAlerted = false;
            return;
        }
        if (TeslaMapsConfig.get().spiritPetPartyCheck && !runStarted && !partyAlerted && ++scanCooldown >= 20) {
            scanCooldown = 0;
            if (partyHasSpirit()) {
                partyAlerted = true;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §cSomeone in your party has a Spirit Pet equipped!"));
                    mc.gui.setTimes(5, 30, 10);
                    mc.gui.setTitle(Component.literal("§cSpirit Pet"));
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
                }
            }
        }
        boolean boss = DungeonManager.isInBoss();
        if (boss && !wasInBoss && !firedThisRun && spiritEquipped) {
            firedThisRun = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                        "§a[TeslaMaps] §c§lSWITCH OFF SPIRIT PET! §r§7— swap to your damage pet for the boss"));
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
            }
        }
        wasInBoss = boss;
    }

    private static boolean partyHasSpirit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand stand) || stand.hasCustomName()) continue;
            String url = SoulweaverHider.headTextureUrl(stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            if (url != null && SPIRIT_TEXTURES.contains(url)) return true;
        }
        return false;
    }
}
