package com.teslamaps.dungeon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WitherDragons {

    public enum State { SPAWNING, ALIVE, DEAD }

    public enum Dragon {
        RED("Red", 'c', 0xFFFF5555, new BlockPos(27, 14, 59), new BlockPos(32, 22, 59),
                new AABB(14.5, 13, 45.5, 39.5, 28, 70.5), 24, 30, 56, 62),
        ORANGE("Orange", '6', 0xFFFFAA00, new BlockPos(85, 14, 56), new BlockPos(80, 23, 56),
                new AABB(72, 8, 47, 102, 28, 77), 82, 88, 53, 59),
        GREEN("Green", 'a', 0xFF55FF55, new BlockPos(27, 14, 94), new BlockPos(32, 23, 94),
                new AABB(7, 8, 80, 37, 28, 110), 23, 29, 91, 97),
        BLUE("Blue", 'b', 0xFF55FFFF, new BlockPos(84, 14, 94), new BlockPos(79, 23, 94),
                new AABB(71.5, 13, 82.5, 96.5, 26, 107.5), 82, 88, 91, 97),
        PURPLE("Purple", '5', 0xFFAA00AA, new BlockPos(56, 14, 125), new BlockPos(56, 22, 120),
                new AABB(45.5, 13, 113.5, 68.5, 23, 136.5), 53, 59, 122, 128);

        public final String dispName;
        public final char colorCode;
        public final int colorArgb;
        public final BlockPos spawnPos, statuePos;
        public final AABB box;
        public final double xMin, xMax, zMin, zMax;

        public State state = State.DEAD;
        public int timeToSpawn = 100;
        public int timesSpawned = 0;
        public UUID entityUUID = null;
        public boolean isSprayed = false;
        public long spawnedTime = 0;
        public boolean high = false;

        Dragon(String dispName, char colorCode, int colorArgb, BlockPos spawnPos, BlockPos statuePos, AABB box,
               double xMin, double xMax, double zMin, double zMax) {
            this.dispName = dispName; this.colorCode = colorCode; this.colorArgb = colorArgb;
            this.spawnPos = spawnPos; this.statuePos = statuePos; this.box = box;
            this.xMin = xMin; this.xMax = xMax; this.zMin = zMin; this.zMax = zMax;
        }

        public void setAlive(UUID entityId) {
            if (entityId != null) this.entityUUID = entityId;

            if (state == State.ALIVE || state != State.SPAWNING) return;
            state = State.ALIVE;

            timesSpawned++;
            spawnedTime = currentTick;
            isSprayed = false;

            TeslaMapsConfig config = TeslaMapsConfig.get();
            if (config.witherDragonMsg && config.witherDragons)
                msg("§" + colorCode + dispName + " §fdragon spawned §8(§7" + timesSpawned + "§8)");
        }

        public void setDead(boolean realTime) {
            state = State.DEAD;
            entityUUID = null;
            DragonCheck.lastDragonDeath = this;

            if (priorityDragon == this) priorityDragon = null;

            TeslaMapsConfig config = TeslaMapsConfig.get();
            if (config.witherDragonSendTime && config.witherDragons && realTime)
                personalBest(dispName, (currentTick - spawnedTime) / 20f, "s§7!",
                        "§" + colorCode + dispName + " §7was alive for §6");
        }

        public Vec3 center() {
            return new Vec3(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5);
        }
    }

    public static final Pattern WITHER_KING_REGEX = Pattern.compile(
            "^\\[BOSS] Wither King: (Oh, this one hurts!|I have more of those\\.|My soul is disposable\\.)$");

    public static Dragon priorityDragon = null;
    public static long currentTick = 0;

    /** Blessing of Power / Time levels, read off the tab footer (Odin reads the same lines). */
    public static int blessingPower = 0;
    public static int blessingTime = 0;

    private static final Pattern POWER_REGEX = Pattern.compile("Blessing of Power (X{0,3}(?:IX|IV|V?I{0,3}))");
    private static final Pattern TIME_REGEX = Pattern.compile("Blessing of Time (V)");

    private static ClientLevel lastLevel = null;

    /** M7 phase 5 — the dragon phase (Odin: getF7Phase() == P5). */
    private static boolean p5() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DungeonManager.isInDungeon()) return false;
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor.getLevel() != 7) return false;
        return mc.player.getY() <= 45;
    }

    private static boolean active() {
        return TeslaMapsConfig.get().witherDragons && p5();
    }

    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (!active()) return;
        // Devonian M7Events: flames at y 27 = the dragon spawns high
        boolean high = packet.getY() == 27.0 && (TeslaMapsConfig.get().witherDragonShowHigh || TeslaMapsConfig.get().dragonStackAimer);
        if (packet.getCount() != 20 || (packet.getY() != 19.0 && !high) || packet.getParticle().getType() != ParticleTypes.FLAME
                || packet.getXDist() != 2f || packet.getYDist() != 3f || packet.getZDist() != 2f
                || packet.getMaxSpeed() != 0f || packet.getX() % 1 != 0.0 || packet.getZ() % 1 != 0.0) return;

        int spawned = 0;
        List<Dragon> dragons = new ArrayList<>();

        for (Dragon dragon : Dragon.values()) {
            spawned += dragon.timesSpawned;

            boolean inRange = !(packet.getX() < dragon.xMin || packet.getX() > dragon.xMax
                    || packet.getZ() < dragon.zMin || packet.getZ() > dragon.zMax);

            if (dragon.state == State.SPAWNING) {
                if (high && inRange) dragon.high = true;
                if (!dragons.contains(dragon)) dragons.add(dragon);
                continue;
            }

            if (!inRange) continue;

            dragon.state = State.SPAWNING;
            dragon.timeToSpawn = 100;
            dragon.high = high;
            dragons.add(dragon);
        }

        if (dragons.isEmpty() || (dragons.size() != 2 && spawned < 2) || priorityDragon != null) return;

        priorityDragon = DragonPriority.findPriority(dragons);
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (config.witherDragonTitle)
            alert("§" + priorityDragon.colorCode + priorityDragon.dispName + " is spawning!"
                    + (config.witherDragonShowHigh && priorityDragon.high ? " §" + priorityDragon.colorCode + "§lHIGH" : ""));

        if (config.witherDragonPriority && dragons.size() > 1) {
            String list = dragons.stream().map(d -> "§" + d.colorCode + d.dispName).collect(Collectors.joining(", "));
            msg(list + "§r -> §" + priorityDragon.colorCode + priorityDragon.dispName + " §7is your priority dragon!");
        }
    }

    public static void onAddEntity(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        if (!active()) return;
        DragonCheck.dragonSpawn(packet);
    }

    public static void onSetEntityData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet) {
        if (!active()) return;
        DragonCheck.dragonUpdate(packet);
    }

    public static void onSetEquipment(net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket packet) {
        if (!active()) return;
        DragonCheck.dragonSprayed(packet);
    }

    public static void onTabFooter(String footer) {
        if (!DungeonManager.isInDungeon()) return;
        Matcher power = POWER_REGEX.matcher(footer);
        if (power.find()) blessingPower = romanToInt(power.group(1));
        Matcher time = TIME_REGEX.matcher(footer);
        if (time.find()) blessingTime = romanToInt(time.group(1));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            DragonCheck.dragonHealthMap.clear();
            reset();
            return;
        }
        if (!TeslaMapsConfig.get().witherDragons) return;

        for (Dragon dragon : Dragon.values()) {
            if (dragon.timeToSpawn > 0) dragon.timeToSpawn--;
            else if (dragon.state == State.SPAWNING) dragon.setAlive(null);
        }
        currentTick++;
    }

    public static void onChatMessage(String message) {
        if (!active()) return;
        String clean = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (!WITHER_KING_REGEX.matcher(clean).matches()) return;

        Dragon dragon = DragonCheck.lastDragonDeath;
        if (dragon == null) {
            for (Dragon d : Dragon.values()) if (d.state != State.DEAD) { dragon = d; break; }
        }
        if (dragon == null) return;

        if (TeslaMapsConfig.get().witherDragonConfirm)
            msg("§" + dragon.colorCode + dragon.dispName + " dragon counts.");
        if (dragon.state != State.DEAD) dragon.setDead(false);
        DragonCheck.lastDragonDeath = null;
    }

    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!active() || !newState.isAir()) return;
        for (Dragon dragon : Dragon.values()) {
            if (dragon.statuePos.equals(pos)) { dragon.setDead(false); break; }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!active()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        if (config.witherDragonHealth) {
            for (DragonCheck.HealthEntry entry : DragonCheck.dragonHealthMap.values().toArray(new DragonCheck.HealthEntry[0])) {
                if (entry.health() <= 0) continue;
                ESPRenderer.drawText(matrices, colorHealth(entry.health()),
                        entry.dragon().getPosition(partial), 5f, cameraPos, false);
            }
        }

        for (Dragon dragon : Dragon.values()) {
            if (config.witherDragonTimer && dragon.timeToSpawn > 0) {
                ESPRenderer.drawText(matrices,
                        "§" + dragon.colorCode + dragon.dispName.charAt(0) + ": " + getDragonTimer(dragon.timeToSpawn),
                        dragon.center(), 5f, cameraPos, false);
            }

            if (config.witherDragonBoxes && dragon.state != State.DEAD)
                ESPRenderer.drawBoxOutline(matrices, dragon.box, dragon.colorArgb, 3f, cameraPos, false);
        }

        if (config.witherDragonPriorityTracer && priorityDragon != null && priorityDragon.state == State.SPAWNING
                && mc.player != null) {
            ESPRenderer.drawTracerFromCamera(matrices, priorityDragon.center(), priorityDragon.colorArgb,
                    cameraPos, false);
        }
    }

    public static void renderHud(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.witherDragons || !config.witherDragonHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        Dragon dragon = priorityDragon;
        if (dragon == null || dragon.timeToSpawn <= 0) return;

        drawHud(context, mc, config.witherDragonHudX, config.witherDragonHudY, config.witherDragonHudScale,
                "§" + dragon.colorCode + dragon.dispName.charAt(0) + ": " + getDragonTimer(dragon.timeToSpawn));
    }

    public static void drawHud(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static String hudSample() {
        return "§5P: §a4.5s";
    }

    private static String getDragonTimer(int spawnTime) {
        String color = spawnTime <= 20 ? "§c" : spawnTime <= 60 ? "§e" : "§a";
        boolean symbol = TeslaMapsConfig.get().witherDragonTimerSymbol;
        return color + switch (TeslaMapsConfig.get().witherDragonTimerStyle) {
            case 0 -> (spawnTime * 50) + (symbol ? "ms" : "");
            case 1 -> String.format("%.1f", spawnTime / 20f) + (symbol ? "s" : "");
            default -> spawnTime + (symbol ? "t" : "");
        };
    }

    private static String colorHealth(float health) {
        if (health >= 750_000_000) return "§a" + formatHealth(health);
        if (health >= 500_000_000) return "§e" + formatHealth(health);
        if (health >= 250_000_000) return "§6" + formatHealth(health);
        return "§c" + formatHealth(health);
    }

    private static String formatHealth(float health) {
        if (health >= 1_000_000_000) return String.format("%.1f", health / 1_000_000_000) + "b";
        if (health >= 1_000_000) return String.format("%.1f", health / 1_000_000) + "m";
        if (health >= 1_000) return String.format("%.1f", health / 1_000) + "k";
        return String.valueOf((int) health);
    }

    /** Odin's PersonalBest.time — prints the time and tracks the best per dragon. */
    private static void personalBest(String name, float time, String unit, String message) {
        Minecraft.getInstance().execute(() -> {
            TeslaMapsConfig config = TeslaMapsConfig.get();
            float oldPB = config.dragonPbs.getOrDefault(name, 9999f);

            String suffix;
            if (oldPB > time) {
                config.dragonPbs.put(name, time);
                TeslaMapsConfig.save();
                suffix = "§7(§d§lNew PB§r§7) Old PB was §8" + oldPB;
            } else {
                suffix = "§8(§7" + oldPB + "§8)";
            }
            msg(message + time + unit + " " + suffix);
        });
    }

    private static int romanToInt(String s) {
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) {
                case 'I' -> 1; case 'V' -> 5; case 'X' -> 10;
                case 'L' -> 50; case 'C' -> 100; default -> 0;
            };
            if (v < prev) total -= v; else { total += v; prev = v; }
        }
        return total;
    }

    public static boolean paulBuff() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.witherDragonPaulBuff || (c.witherDragonPaulAuto && com.teslamaps.api.ElectionApi.benediction());
    }

    public static void reset() {
        reset(false);
    }

    public static void reset(boolean soft) {
        if (soft) {
            for (Dragon dragon : Dragon.values()) {
                dragon.state = State.DEAD;
                dragon.timesSpawned++;
            }
            return;
        }

        for (Dragon dragon : Dragon.values()) {
            dragon.timeToSpawn = 0;
            dragon.timesSpawned = 0;
            dragon.state = State.DEAD;
            dragon.entityUUID = null;
            dragon.isSprayed = false;
            dragon.spawnedTime = 0;
        }
        priorityDragon = null;
        DragonCheck.lastDragonDeath = null;
        blessingPower = 0;
        blessingTime = 0;
    }

    public static void debugDump() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        double totalPower = blessingPower * (WitherDragons.paulBuff() ? 1.25 : 1.0) + (blessingTime > 0 ? 2.5 : 0.0);
        String playerClass = PlayerTracker.getLocalClass();

        msg("§6=== Dragon Priority Debug ===");
        msg("§7In dungeon: §f" + DungeonManager.isInDungeon() + " §7| P5: §f" + p5() + " §7| active: §f" + active());
        msg("§7Power: §a" + blessingPower + " §7Time: §a" + blessingTime + " §7Paul: §f" + paulBuff()
                + " §7-> totalPower: §6" + totalPower);
        msg("§7Your class: §f" + (playerClass == null ? "Unknown" : playerClass));

        msg("§7Party classes:");
        for (PlayerTracker.DungeonPlayer player : PlayerTracker.getPlayers()) {
            msg("  §f" + player.getName() + " §7- §f" + player.getDungeonClass());
        }

        boolean wouldSplit = totalPower >= config.witherDragonNormalPower || totalPower >= config.witherDragonEasyPower;
        msg("§7Thresholds: normal §f" + config.witherDragonNormalPower + " §7easy §f" + config.witherDragonEasyPower
                + " §7-> would split (this power): §f" + wouldSplit);

        boolean bersOrMage = "Berserk".equals(playerClass) || "Mage".equals(playerClass);
        Dragon[] order = !wouldSplit ? DragonPriority.DEFAULT_ORDER
                : (bersOrMage ? DragonPriority.DRAGON_LIST : DragonPriority.reversed(DragonPriority.DRAGON_LIST));
        StringBuilder sb = new StringBuilder();
        for (Dragon dragon : order) sb.append("§").append(dragon.colorCode).append(dragon.dispName.charAt(0)).append(" ");
        msg("§7Order for your class: " + sb);

        msg("§7Dragon states:");
        for (Dragon dragon : Dragon.values()) {
            msg("  §" + dragon.colorCode + dragon.dispName + " §7state=§f" + dragon.state + " §7spawned=§f"
                    + dragon.timesSpawned + " §7timer=§f" + dragon.timeToSpawn + " §7sprayed=§f" + dragon.isSprayed);
        }
        msg("§7Current priority dragon: " + (priorityDragon == null ? "§fnone"
                : "§" + priorityDragon.colorCode + priorityDragon.dispName));
    }

    static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
        });
    }

    private static void alert(String title) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            mc.gui.setTimes(0, Math.max(1, c.witherDragonAlertTicks), 5);
            mc.gui.setTitle(Component.literal(title));
            if (!c.witherDragonAlertMute) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        });
    }
}
