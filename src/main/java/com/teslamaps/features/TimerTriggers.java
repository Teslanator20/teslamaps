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
    private static int bossStartTicks = -1;   // Maxor 167t / Storm 120t
    private static int bossStartMax = 167;
    private static long runStartMs = 0;
    private static long lastServerTick = -1;

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
        boolean onCooldown() {
            return proc >= 0 && tnow() - proc < cd;
        }
        void tick(boolean enabled, String mode, int marker) {
            long dt = proc < 0 ? 0 : tnow() - proc;
            if (proc >= 0 && dt >= cd + READY_LINGER) proc = -1;
            boolean active = proc >= 0 && dt < imm;
            boolean onCd = proc >= 0 && dt < cd;
            boolean show = enabled && switch (mode) {
                case "Always" -> true;
                case "Any" -> active || onCd;
                case "When Active" -> active;
                case "On Cooldown" -> onCd;
                default -> proc >= 0;
            };
            if (!show) { DungeonTimers.clear(key); return; }
            // skull icon identifies the mask, so no name/label text — just the time
            String s = active ? "§a" + fmt(imm - dt)
                     : onCd ? color(cd - dt, cd) + fmt(cd - dt)
                     : "§a§lREADY";
            DungeonTimers.set(key, s, icon(), marker);
        }
    }

    private static long tnow() { return com.teslamaps.utils.ServerTickTimer.now() * 50L; }

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

    private static volatile long timeAnchor = -1, tickAnchor;

    /** Time packet: re-sync the secret tick to the server clock. */
    public static void onTimePacket(long gameTime) {
        tickAnchor = com.teslamaps.utils.ServerTickTimer.now();
        timeAnchor = gameTime;
    }

    public static void onChatMessage(String m) {
        if (m.contains("The Catacombs, Floor") && m.contains("entered")) {
            warpEnd = now() + 30000;
            warpStart(ENTERED.matcher(strip(m)));
        } else if (m.contains("warped the party to a SkyBlock dungeon!")) {
            warpStart(WARPED.matcher(strip(m)));
        }
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
        boolean procOk = !TeslaMapsConfig.get().invincibilityOnlyDungeon || com.teslamaps.dungeon.DungeonManager.isInDungeon();
        if (procOk && m.contains("Bonzo's Mask saved your life")) { BONZO.proc = tnow(); BONZO.cd = readCooldown("Bonzo's Mask", 360000); procced(BONZO, "Bonzo"); }
        if (procOk && m.contains("Spirit Mask saved your life")) { SPIRIT.proc = tnow(); procced(SPIRIT, "Spirit"); }
        if (procOk && m.contains("Phoenix Pet saved you from certain death")) { PHOENIX.proc = tnow(); procced(PHOENIX, "Phoenix"); }
        if (m.contains("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!")) { bossStartTicks = 167; bossStartMax = 167; }
        if (m.contains("[BOSS] Maxor: I'M TOO YOUNG TO DIE AGAIN!")) { bossStartTicks = 120; bossStartMax = 120; }
        if (m.contains("[NPC] Mort: Here, I found this map when I first entered the dungeon.")) runStartMs = now();
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

    private static final java.util.regex.Pattern ENTERED =
            java.util.regex.Pattern.compile("(?:\\[[^\\]]+] )?(\\w{1,16}) entered (?:MM )?The Catacombs, Floor");
    private static final java.util.regex.Pattern WARPED =
            java.util.regex.Pattern.compile("(?:\\[[^\\]]+] )?(\\w{1,16}) warped the party to a SkyBlock dungeon!");
    private static final java.util.Map<String, Long> playerWarpEnds = new java.util.LinkedHashMap<>();
    private static final java.util.Set<String> shownWarpKeys = new java.util.HashSet<>();

    private static void warpStart(java.util.regex.Matcher mt) {
        if (!mt.find()) return;
        long end = now() + 30000;
        Long prev = playerWarpEnds.get(mt.group(1));
        if (prev != null && end - prev < 5000) return;
        playerWarpEnds.put(mt.group(1), end);
    }

    private static void warpDoneSound() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.warpCooldownSound) com.teslamaps.utils.LoudSound.play(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), c.warpCooldownVolume, 1.5f);
    }

    private static void tickWarp(TeslaMapsConfig c, Minecraft mc) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        if (c.warpCooldown && !c.warpCooldownPerPlayer && warpEnd > 0) {
            long ms = warpEnd - now();
            if (ms < 0) { warpEnd = -1; warpDoneSound(); }
            else { DungeonTimers.set("warp", "§eWarp§f: " + color(ms, 30000) + fmt(ms)); keep.add("warp"); }
        }
        String self = mc.player != null ? mc.player.getName().getString() : "";
        var it = playerWarpEnds.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            long ms = e.getValue() - now();
            boolean shown = c.warpCooldown && c.warpCooldownPerPlayer
                    && (!c.warpCooldownOwnOnly || e.getKey().equals(self));
            if (ms < 0) {
                it.remove();
                if (shown) warpDoneSound();
                continue;
            }
            if (!shown) continue;
            String key = "warp_" + e.getKey();
            DungeonTimers.set(key, "§e" + e.getKey() + "§f: " + color(ms, 30000) + fmt(ms));
            keep.add(key);
        }
        for (String k : shownWarpKeys) if (!keep.contains(k)) DungeonTimers.clear(k);
        if (!keep.contains("warp")) DungeonTimers.clear("warp");
        shownWarpKeys.clear();
        shownWarpKeys.addAll(keep);
    }

    private static void procced(Mask mask, String name) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (c.invincibilityTitle) {
            mc.gui.setTimes(0, 30, 5);
            mc.gui.setTitle(Component.literal(mask.name));
        }
        if (!c.invincibilityAnnounce) return;
        if (!com.teslamaps.dungeon.DungeonManager.isInDungeon()) return;
        String msg = "pc " + name + " Procced!";
        if (c.invincibilityAnnounceCount) {
            int used = (BONZO.onCooldown() ? 1 : 0) + (SPIRIT.onCooldown() ? 1 : 0) + (PHOENIX.onCooldown() ? 1 : 0);
            msg += " (" + used + "/3)";
        }
        if (mc.getConnection() != null) mc.getConnection().sendCommand(msg);
    }

    private static boolean wearing(Minecraft mc, String id) {
        if (mc.player == null) return false;
        String worn = com.teslamaps.utils.ItemUtil.skyblockId(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        return worn.equals(id) || worn.equals("STARRED_" + id);
    }

    private static void tickMasks(TeslaMapsConfig c, Minecraft mc) {
        boolean ctx = (!c.invincibilityOnlyDungeon || com.teslamaps.dungeon.DungeonManager.isInDungeon())
                && (!c.invincibilityOnlyBoss || com.teslamaps.dungeon.DungeonManager.isInBossArea());
        boolean on = c.invincibilityTimer && ctx;
        String mode = c.invincibilityShow == null ? "Default" : c.invincibilityShow;
        int marker = c.invincibilityEquippedMarker ? TeslaMapsConfig.parseColor(c.invincibilityEquippedColor) | 0xFF000000 : 0;
        BONZO.tick(on && !c.invincibilityHideBonzo, mode, marker != 0 && wearing(mc, "BONZO_MASK") ? marker : 0);
        SPIRIT.tick(on && !c.invincibilityHideSpirit, mode, marker != 0 && wearing(mc, "SPIRIT_MASK") ? marker : 0);
        PHOENIX.tick(on && !c.invincibilityHidePhoenix, mode, 0);
    }

    /** Cooldown drawn over the mask items in a container slot. */
    public static void drawSlot(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.world.inventory.Slot slot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.invincibilityItemOverlay || !c.invincibilityTimer || !c.section("Timers")) return;
        if (BONZO.proc < 0 && SPIRIT.proc < 0 && PHOENIX.proc < 0) return;
        ItemStack st = slot.getItem();
        Mask m = null;
        if (!st.isEmpty() && st.is(net.minecraft.world.item.Items.PLAYER_HEAD)) {
            String id = com.teslamaps.utils.ItemUtil.skyblockId(st);
            if (id.equals("BONZO_MASK") || id.equals("STARRED_BONZO_MASK")) m = BONZO;
            else if (id.equals("SPIRIT_MASK") || id.equals("STARRED_SPIRIT_MASK")) m = SPIRIT;
        }
        if (m == null && c.invincibilityPhoenixSlot && slot.getContainerSlot() == 9
                && slot.container instanceof net.minecraft.world.entity.player.Inventory) m = PHOENIX;
        if (m == null || !m.onCooldown()) return;
        double pct = (m.cd - (tnow() - m.proc)) / (double) m.cd;
        int col = TeslaMapsConfig.parseColor(c.invincibilityItemColor);
        int x = slot.x, y = slot.y;
        if (c.invincibilityItemDurability) {
            g.fill(x + 2, y + 13, x + 14, y + 15, 0xFF000000);
            g.fill(x + 2, y + 13, x + 14 - (int) ((1 - pct) * 12), y + 14, col);
        } else {
            g.fill(x, y + (int) ((1 - pct) * 16), x + 16, y + 16, col);
        }
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.dungeon.DungeonFloor f = com.teslamaps.dungeon.DungeonManager.getCurrentFloor();
        long serverTick = com.teslamaps.utils.ServerTickTimer.now();
        int dt = lastServerTick < 0 ? 1 : (int) Math.max(0, Math.min(40, serverTick - lastServerTick));
        lastServerTick = serverTick;
        tickWarp(c, mc);

        tickMasks(c, mc);

        if (c.purplePadTimer && purplePadTicks > 0) {
            purplePadTicks = Math.max(0, purplePadTicks - dt);
            show("purplepad", "§dPurple§f: ", "§d" + fmtTicks(purplePadTicks));
        } else { purplePadTicks = 0; hide("purplepad"); }

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
            relicTicks = Math.max(0, relicTicks - dt);
            show("relic", "§aRelic§f: ", "§a" + fmtTicks(relicTicks));
        } else { relicTicks = 0; hide("relic"); }

        boolean inDungeon = com.teslamaps.dungeon.DungeonManager.isInDungeon();
        if (!inDungeon) {
            necronDropTicks = -1; goldorTicks = -1; goldorStartTicks = -1;
            stormPadTicks = -1; lightningTicks = -1; bossStartTicks = -1;
            timeAnchor = -1;
            runStartMs = 0;
            resetLeapSpots();
            LIGHTNING.stop();
        }

        if (LIGHTNING.active()) {
            mc.gui.setTimes(0, 3, 0);
            mc.gui.setTitle(Component.literal(String.format(java.util.Locale.ROOT, "§c§l%.1f", LIGHTNING.remaining() / 20.0)));
        }

        // display first, then advance by the server ticks since the last client tick
        if (c.bossStartTimers && bossStartTicks >= 0) {
            show("bossstart", "§aStart§f: ", tickColor(bossStartTicks, bossStartMax) + fmtTicks(bossStartTicks));
        } else hide("bossstart");
        bossStartTicks = countDown(bossStartTicks, dt);

        if (c.necronDropTimer && necronDropTicks >= 0) {
            show("necrondrop", "§4Necron§f: ", tickColor(necronDropTicks, 60) + fmtTicks(necronDropTicks));
        } else hide("necrondrop");
        necronDropTicks = countDown(necronDropTicks, dt);

        if (c.goldorTickTimer && goldorStartTicks >= 0) {
            show("goldortick", "§aStart§f: ", tickColor(goldorStartTicks, 104) + fmtTicks(goldorStartTicks));
        } else if (c.goldorTickTimer && goldorTicks >= 0) {
            show("goldortick", "§7Tick§f: ", tickColor(goldorTicks, 60) + fmtTicks(goldorTicks));
        } else hide("goldortick");
        goldorStartTicks = countDown(goldorStartTicks, dt);
        goldorTicks = pulse(goldorTicks, dt, 60);

        if (c.stormPadTimer && stormPadTicks >= 0) {
            show("stormpad", "§bPad§f: ", tickColor(stormPadTicks, 20) + fmtTicks(stormPadTicks));
        } else hide("stormpad");
        stormPadTicks = pulse(stormPadTicks, dt, 20);

        if (LIGHTNING.active()) lightningTicks = -1;
        if (c.lightningTimer && LIGHTNING.active()) {
            int lt = (int) LIGHTNING.remaining();
            show("lightning", "§bLightning§f: ", tickColor(lt, 162) + fmtTicks(lt));
        } else if (c.lightningTimer && lightningTicks >= 0) {
            show("lightning", "§bLightning§f: ", tickColor(lightningTicks, 560) + fmtTicks(lightningTicks));
        } else hide("lightning");
        lightningTicks = countDown(lightningTicks, dt);

        if (c.deathTickTimer && mc.level != null && inDungeon && (runStartMs == 0 || now() - runStartMs < 6000)) {
            int dk = 40 - (int) (mc.level.getGameTime() % 40);
            show("deathtick", "§cDeath§f: ", tickColor(dk, 40) + fmtTicks(dk));
        } else hide("deathtick");

        if (c.secretTickTimer && mc.level != null && inDungeon
                && !com.teslamaps.dungeon.DungeonManager.isInBossArea()) {
            long gt = timeAnchor >= 0
                    ? timeAnchor + (com.teslamaps.utils.ServerTickTimer.now() - tickAnchor)
                    : mc.level.getGameTime();
            int st = 20 - (int) (gt % 20);
            show("secrettick", "§7Secret§f: ", tickColor(st, 20) + fmtTicks(st));
        } else hide("secrettick");
    }

    private static int countDown(int ticks, int dt) {
        if (ticks < 0) return -1;
        return ticks - dt < 0 ? -1 : ticks - dt;
    }

    private static int pulse(int ticks, int dt, int period) {
        if (ticks < 0) return -1;
        ticks -= dt;
        while (ticks < 0) ticks += period;
        return ticks;
    }

    private static void show(String key, String prefix, String value) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String text = c.tickTimerNoPrefix ? value : prefix + value;
        if (c.tickTimersSeparate && BossTickHuds.owns(key)) {
            DungeonTimers.clear(key);
            BossTickHuds.set(key, text);
        } else {
            BossTickHuds.clear(key);
            DungeonTimers.set(key, text);
        }
    }

    private static void hide(String key) {
        DungeonTimers.clear(key);
        BossTickHuds.clear(key);
    }

    // Odin's tick-timer coloring: green >= 66%, gold >= 33%, red below
    private static String tickColor(int ticks, int max) {
        if (ticks >= max * 0.66f) return "§a";
        if (ticks >= max * 0.33f) return "§6";
        return "§c";
    }

    private static String fmtTicks(int ticks) {
        boolean bare = TeslaMapsConfig.get().tickTimerNoSuffix;
        return switch (TeslaMapsConfig.get().tickTimerFormat) {
            case "Ticks" -> bare ? String.valueOf(ticks) : ticks + "t";
            case "Both" -> String.format("%.2fs §7(%dt)", ticks * 0.05, ticks);
            default -> String.format(bare ? "%.2f" : "%.2fs", ticks * 0.05);
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
