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
    // Odin tick timers (-1 = inactive)
    private static int necronDropTicks = -1;  // Necron drop after "your journey ends now"
    private static int goldorTicks = -1;      // repeating 60t device/terminal pulse in P3
    private static int goldorStartTicks = -1; // 104t until terms/devices can be activated
    private static int stormPadTicks = -1;    // repeating 20t Storm pad pulse
    private static int lightningTicks = -1;   // 560t until Storm's lightning

    /** How long READY keeps standing after the cooldown ends. */
    private static final long READY_LINGER = 30000;

    private static final class Mask {
        final String key, name; final long imm; long cd; long proc = -1;
        final String hash; ItemStack icon;
        Mask(String key, String name, long imm, long cd, String hash) {
            this.key = key; this.name = name; this.imm = imm; this.cd = cd; this.hash = hash;
        }
        // built lazily: <clinit> can run before the item registry is bound
        ItemStack icon() {
            if (icon == null) icon = createSkull(hash);
            return icon;
        }
        void tick(boolean enabled) {
            if (!enabled || proc < 0) { DungeonTimers.clear(key); return; }
            long dt = now() - proc;
            if (dt < cd) {
                // skull icon identifies the mask, so no name/label text — just the time
                String s = (dt < imm) ? "§a" + fmt(imm - dt)
                                      : color(cd - dt, cd) + fmt(cd - dt);
                DungeonTimers.set(key, s, icon());
            } else if (dt < cd + READY_LINGER) {
                DungeonTimers.set(key, "§a§lREADY", icon());
            } else { proc = -1; DungeonTimers.clear(key); }
        }
    }

    public static ItemStack maskIcon(int i) {
        return switch (i) { case 0 -> BONZO.icon(); case 1 -> SPIRIT.icon(); default -> PHOENIX.icon(); };
    }

    private static boolean skullWarned = false;

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
            if (!skullWarned) { skullWarned = true; com.teslamaps.TeslaMaps.LOGGER.warn("[TimerTriggers] failed to build skull icon", t); }
            return null; // DungeonTimers falls back to text-only when icon is null
        }
    }

    private static final Mask BONZO = new Mask("bonzo", "§9Bonzo", 3000, 360000,
            "12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c");
    private static final Mask SPIRIT = new Mask("spirit", "§bSpirit", 3000, 30000,
            "9bbe721d7ad8ab965f08cbec0b834f779b5197f79da4aea3d13d253ece9dec2");
    private static final Mask PHOENIX = new Mask("phoenix", "§6Phoenix", 4000, 60000,
            "66b1b59bc890c9c97527787dde20600c8b86f6b9912d51a6bfcdb0e4c2aa3c97");

    public static void onChatMessage(String m) {
        if (m.contains("The Catacombs, Floor") && m.contains("entered")) warpEnd = now() + 30000;
        if (m.contains("[BOSS] Storm: ENERGY HEED MY CALL!")
                || m.contains("[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!")) purplePadTicks = 96;
        if (m.contains("[BOSS] Necron: All this, for nothing...")) relicTicks = 45;
        // Odin tick timers (F7/M7 boss)
        if (m.contains("[BOSS] Necron: I'm afraid, your journey ends now.")) necronDropTicks = 60;
        if (m.contains("[BOSS] Goldor: Who dares trespass into my domain?")) goldorTicks = 60;
        if (m.contains("The Core entrance is opening!")) { goldorTicks = -1; goldorStartTicks = -1; }
        if (m.contains("[BOSS] Storm: I should have known that I stood no chance.")) {
            goldorStartTicks = 104;
            stormPadTicks = -1;
        }
        if (m.contains("[BOSS] Storm: Pathetic Maxor, just like expected.")) {
            stormPadTicks = 20;
            lightningTicks = 560;
        }
        if (m.contains("Bonzo's Mask saved your life")) { BONZO.proc = now(); BONZO.cd = readCooldown("Bonzo's Mask", 360000); announce("Bonzo"); }
        if (m.contains("Spirit Mask saved your life")) { SPIRIT.proc = now(); announce("Spirit"); }
        if (m.contains("Phoenix Pet saved you from certain death")) { PHOENIX.proc = now(); announce("Phoenix"); }
        if (m.contains("You have teleported to ")) {
            String t = com.teslamaps.utils.StringUtil.strip(m).trim();
            if (t.startsWith("You have teleported to ") && t.endsWith("!")) ownLeapAt = now();
        }
    }

    // Ported from NoammAddons' F7Titles.kt
    private static final com.teslamaps.utils.ServerTickTimer LIGHTNING = new com.teslamaps.utils.ServerTickTimer();

    /** Main thread. True = swallow Hypixel's title. */
    public static boolean onTitle(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.lightningTimerTitle || !c.section("Timers")) return false;
        com.teslamaps.dungeon.DungeonFloor f = com.teslamaps.dungeon.DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 7 || !com.teslamaps.dungeon.DungeonManager.isInBossArea()) return false;
        String t = com.teslamaps.utils.StringUtil.stripTrim(raw);
        if (t.isEmpty() || t.length() > 2) return false;
        int n;
        try { n = Integer.parseInt(t); } catch (NumberFormatException e) { return false; }
        if (!LIGHTNING.active() && (n == 4 || n == 6)) {
            LIGHTNING.start((int) Math.round(n * 1.35 * 20), () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setTimes(0, 40, 5);
                mc.gui.setTitle(Component.literal("§aStorm's Lightning Ended!"));
                if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.25f, 1f);
            });
        }
        return true;
    }

    // Ported from Devonian's LeapCounter.kt
    private record LeapSpot(double x, double y, double z, double r, int need) {}
    private static final LeapSpot[] LEAP_SPOTS = {
            new LeapSpot(58.5, 109.0, 131.5, 1.5, 4),
            new LeapSpot(60.5, 132.0, 139.0, 2.0, 4),
            new LeapSpot(69.5, 109.0, 122.5, 1.0, 4),
            new LeapSpot(48.5, 109.0, 122.5, 1.0, 4),
            new LeapSpot(54.5, 115.0, 50.5, 1.5, 4),
            new LeapSpot(2.5, 109.0, 104.5, 3.0, 3),
            new LeapSpot(18.5, 121.5, 92.0, 2.0, 3),
            new LeapSpot(54.5, 5.0, 76.5, 8.0, 4),
    };
    private static final boolean[] leapSpotDone = new boolean[LEAP_SPOTS.length];
    private static final java.util.Set<String> leapedAt = new java.util.HashSet<>();
    private static int leapSpot = -1;
    private static long ownLeapAt = -1;

    private static void tickLeapSpots(Minecraft mc, TeslaMapsConfig c) {
        if (ownLeapAt > 0 && now() - ownLeapAt < 3000) return;
        int idx = -1;
        for (int i = 0; i < LEAP_SPOTS.length; i++) {
            LeapSpot sp = LEAP_SPOTS[i];
            if (mc.player.distanceToSqr(sp.x, sp.y, sp.z) <= sp.r * sp.r) idx = i;
        }
        if (idx < 0 || leapSpotDone[idx]) {
            leapedAt.clear();
            leapSpot = -1;
            DungeonTimers.clear("leaps");
            return;
        }
        if (idx != leapSpot) { leapedAt.clear(); leapSpot = idx; }
        LeapSpot sp = LEAP_SPOTS[idx];
        String self = mc.player.getName().getString();
        for (var dp : com.teslamaps.player.PlayerTracker.getPlayers()) {
            if (dp.getName().equals(self) || dp.getUuid() == null) continue;
            var pl = mc.level.getPlayerByUUID(dp.getUuid());
            if (pl == null || pl == mc.player) continue;
            if (pl.distanceToSqr(sp.x, sp.y, sp.z) <= sp.r * sp.r) leapedAt.add(dp.getName());
        }
        int n = leapedAt.size();
        if (n == 0) { DungeonTimers.clear("leaps"); return; }
        if (n >= sp.need) {
            leapSpotDone[idx] = true;
            if (c.leapCounterAlert) {
                mc.gui.setTimes(0, 20, 5);
                mc.gui.setTitle(Component.literal("§9" + sp.need + " have leaped"));
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
            }
        }
        String col = n >= sp.need ? "§a" : n * 2 >= sp.need ? "§6" : "§c";
        DungeonTimers.set("leaps", "§bLeaps§f: " + col + n + "§7/" + sp.need);
    }

    private static void resetLeapSpots() {
        java.util.Arrays.fill(leapSpotDone, false);
        leapedAt.clear();
        leapSpot = -1;
        ownLeapAt = -1;
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

        BONZO.tick(c.invincibilityTimer);
        SPIRIT.tick(c.invincibilityTimer);
        PHOENIX.tick(c.invincibilityTimer);

        if (c.purplePadTimer && purplePadTicks > 0) {
            purplePadTicks--;
            DungeonTimers.set("purplepad", "§dPurple§f: §d" + fmtTicks(purplePadTicks));
        } else { purplePadTicks = 0; DungeonTimers.clear("purplepad"); }

        // only in the boss arena (off the room grid) - showed a useless 0/4 for the whole clear before
        boolean leapArea = c.leapCounter && f != null && f.getLevel() == 7 && mc.player != null && mc.level != null
                && com.teslamaps.scanner.ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null;
        if (leapArea && c.leapCounterSpots) {
            tickLeapSpots(mc, c);
        } else if (leapArea) {
            int n = 0;
            int total = 0;
            for (var dp : com.teslamaps.player.PlayerTracker.getPlayers()) {
                if (dp.getName().equals(mc.player.getName().getString())) continue;
                total++;
                var pl = dp.getUuid() != null ? mc.level.getPlayerByUUID(dp.getUuid()) : null;
                if (pl != null && pl != mc.player && pl.distanceToSqr(mc.player) <= 9.0) n++;
            }
            if (total > 0) DungeonTimers.set("leaps", "§bLeaps§f: §a" + n + "§7/" + total);
            else DungeonTimers.clear("leaps");
        } else { DungeonTimers.clear("leaps"); }

        if (c.simonSaysProgress && com.teslamaps.dungeon.puzzle.SimonSaysSolver.getSequenceSize() > 0) {
            DungeonTimers.set("ss", "§eSS§f: §a" + com.teslamaps.dungeon.puzzle.SimonSaysSolver.getClicked()
                    + "§7/§a" + com.teslamaps.dungeon.puzzle.SimonSaysSolver.getSequenceSize());
        } else DungeonTimers.clear("ss");

        if (c.relicTimer && relicTicks > 0) {
            relicTicks--;
            DungeonTimers.set("relic", "§aRelic§f: §a" + fmtTicks(relicTicks));
        } else { relicTicks = 0; DungeonTimers.clear("relic"); }

        boolean inDungeon = com.teslamaps.dungeon.DungeonManager.isInDungeon();
        if (!inDungeon) {
            necronDropTicks = -1; goldorTicks = -1; goldorStartTicks = -1;
            stormPadTicks = -1; lightningTicks = -1;
            resetLeapSpots();
            LIGHTNING.stop();
        }

        if (LIGHTNING.active()) {
            mc.gui.setTimes(0, 3, 0);
            mc.gui.setTitle(Component.literal(String.format(java.util.Locale.ROOT, "§c§l%.1f", LIGHTNING.remaining() / 20.0)));
        }

        if (c.necronDropTimer && necronDropTicks >= 0) {
            DungeonTimers.set("necrondrop", "§4Necron§f: " + tickColor(necronDropTicks, 60) + fmtTicks(necronDropTicks));
            necronDropTicks--;
        } else DungeonTimers.clear("necrondrop");

        if (c.goldorTickTimer && goldorStartTicks >= 0) {
            DungeonTimers.set("goldortick", "§aStart§f: " + tickColor(goldorStartTicks, 104) + fmtTicks(goldorStartTicks));
            goldorStartTicks--;
        } else if (c.goldorTickTimer && goldorTicks >= 0) {
            DungeonTimers.set("goldortick", "§7Tick§f: " + tickColor(goldorTicks, 60) + fmtTicks(goldorTicks));
            if (--goldorTicks < 0) goldorTicks = 60; // devices/terms pulse every 60 ticks until the core opens
        } else DungeonTimers.clear("goldortick");

        if (c.stormPadTimer && stormPadTicks >= 0) {
            DungeonTimers.set("stormpad", "§bPad§f: " + tickColor(stormPadTicks, 20) + fmtTicks(stormPadTicks));
            if (--stormPadTicks < 0) stormPadTicks = 20; // pad pulses every 20 ticks
        } else DungeonTimers.clear("stormpad");

        if (LIGHTNING.active()) lightningTicks = -1;
        if (c.lightningTimer && LIGHTNING.active()) {
            int lt = (int) LIGHTNING.remaining();
            DungeonTimers.set("lightning", "§bLightning§f: " + tickColor(lt, 162) + fmtTicks(lt));
        } else if (c.lightningTimer && lightningTicks >= 0) {
            DungeonTimers.set("lightning", "§bLightning§f: " + tickColor(lightningTicks, 560) + fmtTicks(lightningTicks));
            lightningTicks--;
        } else DungeonTimers.clear("lightning");

        if (c.secretTickTimer && mc.level != null && inDungeon
                && !com.teslamaps.dungeon.DungeonManager.isInBoss()) {
            int st = 20 - (int) (mc.level.getGameTime() % 20);
            DungeonTimers.set("secrettick", "§7Secret§f: " + tickColor(st, 20) + fmtTicks(st));
        } else DungeonTimers.clear("secrettick");
    }

    // Odin's tick-timer coloring: green >= 66%, gold >= 33%, red below
    private static String tickColor(int ticks, int max) {
        if (ticks >= max * 0.66f) return "§a";
        if (ticks >= max * 0.33f) return "§6";
        return "§c";
    }

    private static String fmtTicks(int ticks) {
        return switch (TeslaMapsConfig.get().tickTimerFormat) {
            case "Ticks" -> ticks + "t";
            case "Both" -> String.format("%.2fs §7(%dt)", ticks * 0.05, ticks);
            default -> String.format("%.2fs", ticks * 0.05);
        };
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
