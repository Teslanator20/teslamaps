/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class TimerTriggers {

    private static long warpEnd = -1;
    private static int purplePadTicks = 0; // F7 purple-pad countdown (Storm trigger)
    private static int relicTicks = 0;     // M7 relic spawn countdown (Necron trigger)

    private static final class Mask {
        final String key, name; final long imm; long cd; long proc = -1;
        final ItemStack icon;
        Mask(String key, String name, long imm, long cd, ItemStack icon) {
            this.key = key; this.name = name; this.imm = imm; this.cd = cd; this.icon = icon;
        }
        void tick(boolean enabled) {
            if (!enabled || proc < 0) { DungeonTimers.clear(key); return; }
            long dt = now() - proc;
            if (dt < cd) {
                // skull icon identifies the mask, so no name/label text — just the time
                String s = (dt < imm) ? "§a" + fmt(imm - dt)
                                      : color(cd - dt, cd) + fmt(cd - dt);
                DungeonTimers.set(key, s, icon);
            } else if (dt < cd + 5000) {
                DungeonTimers.set(key, "§a§lREADY", icon);
            } else { proc = -1; DungeonTimers.clear(key); }
        }
    }

    public static ItemStack maskIcon(int i) {
        return switch (i) { case 0 -> BONZO.icon; case 1 -> SPIRIT.icon; default -> PHOENIX.icon; };
    }

    // hardcoded mask/pet head icons (texture hashes), Odin-style icon + ready/cooldown display
    private static ItemStack createSkull(String hash) {
        try {
            ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
            String b64 = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // PropertyMap copies into an ImmutableMultimap, so fill a mutable map first then wrap
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> mm =
                    com.google.common.collect.LinkedHashMultimap.create();
            mm.put("textures", new com.mojang.authlib.properties.Property("textures", b64));
            com.mojang.authlib.properties.PropertyMap props = new com.mojang.authlib.properties.PropertyMap(mm);
            com.mojang.authlib.GameProfile profile =
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "skull", props);
            head.set(DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(profile));
            return head;
        } catch (Throwable t) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[TimerTriggers] failed to build skull icon", t);
            return null; // DungeonTimers falls back to text-only when icon is null
        }
    }

    private static final Mask BONZO = new Mask("bonzo", "§9Bonzo", 3000, 360000,
            createSkull("12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c"));
    private static final Mask SPIRIT = new Mask("spirit", "§bSpirit", 3000, 30000,
            createSkull("9bbe721d7ad8ab965f08cbec0b834f779b5197f79da4aea3d13d253ece9dec2"));
    private static final Mask PHOENIX = new Mask("phoenix", "§6Phoenix", 4000, 60000,
            createSkull("66b1b59bc890c9c97527787dde20600c8b86f6b9912d51a6bfcdb0e4c2aa3c97"));

    public static void onChatMessage(String m) {
        if (m.contains("The Catacombs, Floor") && m.contains("entered")) warpEnd = now() + 30000;
        if (m.contains("[BOSS] Storm: ENERGY HEED MY CALL!")
                || m.contains("[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!")) purplePadTicks = 96;
        if (m.contains("[BOSS] Necron: All this, for nothing...")) relicTicks = 45;
        if (m.contains("Bonzo's Mask saved your life")) { BONZO.proc = now(); BONZO.cd = readCooldown("Bonzo's Mask", 360000); announce("Bonzo"); }
        if (m.contains("Spirit Mask saved your life")) { SPIRIT.proc = now(); announce("Spirit"); }
        if (m.contains("Phoenix Pet saved you from certain death")) { PHOENIX.proc = now(); announce("Phoenix"); }
    }

    private static void announce(String name) {
        if (!TeslaMapsConfig.get().invincibilityAnnounce) return;
        if (!com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("pc " + name + " Procced!");
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.dungeon.DungeonFloor f = com.teslamaps.dungeon.DungeonManager.getCurrentFloor();
        if (c.warpCooldown && warpEnd > 0) {
            long ms = warpEnd - now();
            if (ms < 0) { warpEnd = -1; DungeonTimers.clear("warp"); }
            else DungeonTimers.set("warp", "§eWarp§f: " + color(ms, 30000) + fmt(ms));
        } else DungeonTimers.clear("warp");

        BONZO.tick(c.bonzoTimer);
        SPIRIT.tick(c.spiritMaskTimer);
        PHOENIX.tick(c.phoenixTimer);

        if (c.purplePadTimer && purplePadTicks > 0) {
            purplePadTicks--;
            DungeonTimers.set("purplepad", "§dPurple§f: §d" + String.format("%.2fs", purplePadTicks * 0.05));
        } else { purplePadTicks = 0; DungeonTimers.clear("purplepad"); }

        if (c.leapCounter && f != null && f.getLevel() == 7 && mc.player != null && mc.level != null) {
            int n = 0;
            for (var pl : mc.level.players()) {
                if (pl == mc.player) continue;
                if (pl.distanceToSqr(mc.player) <= 9.0) n++;
            }
            DungeonTimers.set("leaps", "§bLeaps§f: §a" + n + "§7/4");
        } else { DungeonTimers.clear("leaps"); }

        if (c.simonSaysProgress && com.teslamaps.dungeon.puzzle.SimonSaysSolver.getSequenceSize() > 0) {
            DungeonTimers.set("ss", "§eSS§f: §a" + com.teslamaps.dungeon.puzzle.SimonSaysSolver.getClicked()
                    + "§7/§a" + com.teslamaps.dungeon.puzzle.SimonSaysSolver.getSequenceSize());
        } else DungeonTimers.clear("ss");

        if (c.relicTimer && relicTicks > 0) {
            relicTicks--;
            DungeonTimers.set("relic", "§aRelic§f: §a" + String.format("%.2fs", relicTicks * 0.05));
        } else { relicTicks = 0; DungeonTimers.clear("relic"); }

        if (c.deathTickTimer && mc.level != null
                && com.teslamaps.dungeon.DungeonManager.isInDungeon()
                && !com.teslamaps.dungeon.DungeonManager.isInBoss()) {
            int dt = 40 - (int) (mc.level.getGameTime() % 40);
            DungeonTimers.set("deathtick", "§cDeath§f: " + color(dt, 40) + String.format("%.2fs", dt * 0.05));
        } else { DungeonTimers.clear("deathtick"); }
    }

    private static long readCooldown(String itemName, long fallback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return fallback;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty() || !strip(s.getHoverName().getString()).contains(itemName)) continue;
            ItemLore lore = s.get(DataComponents.LORE);
            if (lore == null) continue;
            for (Component line : lore.lines()) {
                String l = strip(line.getString());
                if (l.startsWith("Cooldown:") && l.endsWith("s")) {
                    try { return Long.parseLong(l.replaceAll("[^0-9]", "")) * 1000; } catch (Exception ignored) {}
                }
            }
        }
        return fallback;
    }

    private static long now() { return System.currentTimeMillis(); }
    private static String fmt(long ms) { return String.format("%.2fs", ms / 1000.0); }
    private static String color(long num, long max) {
        if (num >= max * 0.75) return "§4";
        if (num >= max * 0.50) return "§c";
        if (num >= max * 0.25) return "§e";
        return "§a";
    }
    private static String strip(String s) { return s == null ? "" : s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim(); }
}
