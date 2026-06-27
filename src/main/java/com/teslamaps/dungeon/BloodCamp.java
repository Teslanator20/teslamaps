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
package com.teslamaps.dungeon;

import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.PingMeter;
import com.teslamaps.mixin.BossHealthOverlayAccessor;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class BloodCamp {

    private static final Pattern[] OPEN_MESSAGES = {
            Pattern.compile("^\\[BOSS] The Watcher: Things feel a little more roomy now, eh\\?$"),
            Pattern.compile("^\\[BOSS] The Watcher: Oh\\.\\. hello\\?$"),
            Pattern.compile("^\\[BOSS] The Watcher: I'm starting to get tired of seeing you around here\\.\\.\\.$"),
            Pattern.compile("^\\[BOSS] The Watcher: You've managed to scratch and claw your way here, eh\\?$"),
            Pattern.compile("^\\[BOSS] The Watcher: So you made it this far\\.\\.\\. interesting\\.$"),
            Pattern.compile("^\\[BOSS] The Watcher: Ah, we meet again\\.\\.\\.$"),
            Pattern.compile("^\\[BOSS] The Watcher: Ah, you've finally arrived\\.$"),
    };
    private static final Pattern MOVE_MESSAGE = Pattern.compile("^\\[BOSS] The Watcher: Let's see how you can handle this\\.$");

    private static long tickCounter = 0L;
    private static long openTimeMs = 0L;     // real time the blood opened (a greeting message)
    private static long openTick = 0L;       // tickCounter at blood open
    private static boolean hasFinal = false; // a move is scheduled
    private static long finalTick = 0L;      // tickCounter of the predicted move (= Kill Blood moment)

    private static long currentTickTime = 0L;           // ms, +50 per client tick (matches Odin's server-tick clock)
    private static Zombie currentWatcherEntity = null;
    private static boolean firstSpawns = true;          // first spawn wave travels 16.1 blocks, later ones 11.9
    private static long nextMoveEstimateTick = -1L;     // early seed countdown until "Let's see" refines it precisely
    private static volatile long serverTicks = 0L;
    private static volatile int lastPingId = Integer.MIN_VALUE;
    private static long openServerTick = 0L;            // serverTicks captured at blood open

    public static void onServerPing(int id) {
        if (id == lastPingId) return;
        lastPingId = id;
        serverTicks++;
    }
    private static final Map<ArmorStand, EntityData> entityDataMap = new ConcurrentHashMap<>();
    private static final Map<ArmorStand, RenderEData> renderDataMap = new ConcurrentHashMap<>();

    private static final class EntityData {
        Vec3 startVector;
        final long started;
        final boolean firstSpawns;
        final ArrayDeque<Vec3> deltaHistory = new ArrayDeque<>();
        Vec3 lastPosition;
        EntityData(Vec3 sv, long started, boolean fs) { this.startVector = sv; this.started = started; this.firstSpawns = fs; this.lastPosition = sv; }
    }

    private static final class RenderEData {
        Vec3 currVector, endVector, speedVectors;
        long endVecUpdated;
        Vec3 lastEndVector, lastPingPoint, lastEndPoint;
        RenderEData(Vec3 cv, Vec3 ev, long eu, Vec3 sv) { this.currVector = cv; this.endVector = ev; this.endVecUpdated = eu; this.speedVectors = sv; }
    }

    public static void tick() {
        tickCounter++;
        currentTickTime += 50;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bloodCampClasses.allowsLocal()) return;

        if (c.bloodCampAssist && c.bloodAssistPingOffset && currentWatcherEntity != null && tickCounter % 40 == 0) {
            PingMeter.requestSilent();
        }

        if (hasFinal && tickCounter >= finalTick) {
            hasFinal = false;
            nextMoveEstimateTick = -1L; // move happened -> stop the countdown
            if (c.bloodCampKillTitle) title("§cKill Mobs");
            if (c.bloodCampMoveAlert) { title("§4§lKILL BLOOD"); playAlert(c.bloodCampMoveSound, c.bloodCampMoveVolume); }
        }
    }

    private static boolean inDbg = false;

    private static void dbg(String s) {
        if (!TeslaMapsConfig.get().debugMode || inDbg) return;
        inDbg = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8[BloodDbg] " + s));
        } finally {
            inDbg = false;
        }
    }

    public static void onChatMessage(String message) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bloodCampClasses.allowsLocal()) return;
        if (!config.bloodCampMoveTimer && !config.bloodCampAssist && !config.bloodReturnTimer && !config.bloodCampMoveMessage && !config.bloodCampPartyMessage) return;

        if (message.contains("Watcher")) dbg("Watcher msg: \"" + message + "\"");

        for (Pattern p : OPEN_MESSAGES) {
            if (p.matcher(message).matches()) {
                openTimeMs = System.currentTimeMillis();
                openTick = tickCounter;
                openServerTick = serverTicks;
                nextMoveEstimateTick = tickCounter + config.bloodReturnEstimate * 20L;
                dbg("Blood open matched");
                return;
            }
        }

        if (!MOVE_MESSAGE.matcher(message).matches()) return;
        firstSpawns = false;
        if (openTimeMs <= 0) { dbg("move line but no blood-open seen -> abort"); return; }

        double bloodMove = (System.currentTimeMillis() - openTimeMs) / 1000.0 + 0.1;   // real seconds since open
        double bloodMoveTime = (serverTicks - openServerTick) * 0.05 + 0.1;            // server seconds since open
        double bloodLag = bloodMove - bloodMoveTime;
        openTimeMs = 0L;
        double base;
        if (bloodMoveTime >= 31 && bloodMoveTime <= 34) base = 36;
        else if (bloodMoveTime >= 28 && bloodMoveTime <= 31) base = 33;
        else if (bloodMoveTime >= 25 && bloodMoveTime <= 28) base = 30;
        else if (bloodMoveTime >= 22 && bloodMoveTime <= 25) base = 27;
        else if (bloodMoveTime >= 1 && bloodMoveTime <= 22) base = 24;
        else { dbg("bloodMoveTime=" + bloodMoveTime + " outside [1,34] -> invalid"); return; }

        double prediction = bloodLag + base; // total real seconds from blood open until the move

        String secs = String.format(java.util.Locale.US, "%.2f", prediction);
        Minecraft mc = Minecraft.getInstance();
        if (config.bloodCampMoveMessage && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Move Prediction: §f" + secs + " Seconds§7."));
        }
        if (config.bloodCampPartyMessage && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc Move Prediction: " + secs + " Seconds.");
        }

        double delaySec = prediction - bloodMoveTime - 0.15;
        finalTick = tickCounter + Math.round(delaySec * 20);
        hasFinal = finalTick > tickCounter;
        nextMoveEstimateTick = hasFinal ? finalTick : -1L; // refine the early countdown to the precise move
        dbg("Move Prediction " + secs + "s (serverElapsed=" + String.format(java.util.Locale.US, "%.2f", bloodMoveTime)
                + ", lag=" + String.format(java.util.Locale.US, "%.2f", bloodLag) + ", moveTick=" + finalTick + ")");
    }

    public static void onSetEquipmentPacket(ClientboundSetEquipmentPacket packet) {
        if (!TeslaMapsConfig.get().bloodCampAssist || currentWatcherEntity != null) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBoss()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Pair<EquipmentSlot, ItemStack> slot : packet.getSlots()) {
            if (slot.getSecond().isEmpty()) continue;
            if (slot.getFirst() != EquipmentSlot.HEAD) continue;
            String tex = skullTexture(slot.getSecond());
            if (tex != null && WATCHER_SKULLS.contains(tex)) {
                Entity e = level.getEntity(packet.getEntity());
                if (e instanceof Zombie z) { currentWatcherEntity = z; dbg("Watcher found @ " + z.blockPosition()); }
                return;
            }
        }
    }

    public static void onRemoveEntitiesPacket(ClientboundRemoveEntitiesPacket packet) {
        if (currentWatcherEntity == null) return;
        if (packet.getEntityIds().contains(currentWatcherEntity.getId())) currentWatcherEntity = null;
    }

    public static Zombie getWatcherEntity() {
        return currentWatcherEntity;
    }

    public static void onMoveEntityPacket(ClientboundMoveEntityPacket packet) {
        if (!TeslaMapsConfig.get().bloodCampAssist) return;
        if (packet.getXa() == 0 && packet.getYa() == 0 && packet.getZa() == 0) return;
        if (currentWatcherEntity == null) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBoss()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!(packet.getEntity(level) instanceof ArmorStand entity)) return;
        if (currentWatcherEntity.distanceTo(entity) > 20) return;
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() != Items.PLAYER_HEAD) return;
        String tex = skullTexture(head);
        if (tex == null || !ALLOWED_MOB_SKULLS.contains(tex)) return;

        Vec3 packetVector = new Vec3(entity.getX() + packet.getXa() / 4096.0,
                entity.getY() + packet.getYa() / 4096.0, entity.getZ() + packet.getZa() / 4096.0);

        EntityData data = entityDataMap.computeIfAbsent(entity, k -> new EntityData(packetVector, currentTickTime, firstSpawns));
        Vec3 delta = packetVector.subtract(data.lastPosition);
        data.lastPosition = packetVector;
        if (delta.lengthSqr() > 0) data.deltaHistory.addLast(delta);

        Vec3 totalDelta = Vec3.ZERO;
        for (Vec3 d : data.deltaHistory) totalDelta = totalDelta.add(d);
        Vec3 dir = totalDelta.lengthSqr() > 0 ? totalDelta.normalize() : Vec3.ZERO;
        Vec3 endpoint = data.startVector.add(dir.scale(data.firstSpawns ? 16.1 : 11.9));

        long timeTook = currentTickTime - data.started;
        Vec3 speed = timeTook == 0 ? Vec3.ZERO : new Vec3(
                (packetVector.x - data.startVector.x) / timeTook,
                (packetVector.y - data.startVector.y) / timeTook,
                (packetVector.z - data.startVector.z) / timeTook);

        RenderEData rd = renderDataMap.computeIfAbsent(entity, k -> new RenderEData(packetVector, endpoint, currentTickTime, speed));
        rd.lastEndVector = rd.endVector;
        rd.endVecUpdated = currentTickTime;
        rd.speedVectors = speed;
        rd.currVector = packetVector;
        rd.endVector = endpoint;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.bloodCampClasses.allowsLocal()) return;
        if (!c.bloodCampAssist || renderDataMap.isEmpty()) return;
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBoss()) return;

        double bs = c.bloodAssistBoxSize;
        Vec3 boxOffset = new Vec3(bs / -2.0, 1.5, bs / -2.0);
        int spawnColor = TeslaMapsConfig.parseColor(c.colorBloodSpawn);
        int posColor = TeslaMapsConfig.parseColor(c.colorBloodPosition);
        int finalColor = TeslaMapsConfig.parseColor(c.colorBloodFinal);
        boolean skip = !c.bloodAssistInterpolation;

        Iterator<Map.Entry<ArmorStand, RenderEData>> it = renderDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ArmorStand, RenderEData> entry = it.next();
            ArmorStand entity = entry.getKey();
            RenderEData rd = entry.getValue();
            if (!entity.isAlive()) { it.remove(); entityDataMap.remove(entity); continue; }
            EntityData data = entityDataMap.get(entity);
            if (data == null) continue;

            Vec3 endPoint = calcEndVector(rd.endVector, rd.lastEndVector, Math.min(currentTickTime - rd.endVecUpdated, 100) / 100f, false);
            long timeTook = currentTickTime - data.started;
            float time = getTime(data.firstSpawns, timeTook);
            float mobOffset = c.bloodAssistPingOffset ? PingMeter.getLastPingMs() : c.bloodAssistManualOffset;
            Vec3 pingPoint = new Vec3(entity.getX() + rd.speedVectors.x * mobOffset,
                    entity.getY() + rd.speedVectors.y * mobOffset, entity.getZ() + rd.speedVectors.z * mobOffset);

            rd.lastEndPoint = endPoint;
            rd.lastPingPoint = pingPoint;
            Vec3 pingDraw = calcEndVector(pingPoint, rd.lastPingPoint, 1f, skip);
            Vec3 endDraw = calcEndVector(endPoint, rd.lastEndPoint, 1f, skip);
            AABB pingAABB = new AABB(0, 0, 0, bs, bs, bs).move(boxOffset.add(pingDraw));
            AABB endAABB = new AABB(0, 0, 0, bs, bs, bs).move(boxOffset.add(endDraw));

            if (mobOffset < time) {
                ESPRenderer.drawBoxOutline(matrices, pingAABB, posColor, 2.0f, cameraPos, true);
                ESPRenderer.drawBoxOutline(matrices, endAABB, spawnColor, 2.0f, cameraPos, true);
            } else {
                ESPRenderer.drawBoxOutline(matrices, endAABB, finalColor, 2.0f, cameraPos, true);
            }
            if (c.bloodAssistLine) {
                ESPRenderer.drawLine(matrices, rd.currVector.add(0, 2, 0), endPoint.add(0, 2, 0), 0xFFFF5555, 2.0f, cameraPos);
            }
            if (c.bloodAssistTime) {
                float timeDisplay = (time - c.bloodAssistOffset) / 1000f;
                String col = timeDisplay > 1.5f ? "§a" : timeDisplay >= 0.5f ? "§6" : timeDisplay >= 0.0f ? "§c" : "§b";
                ESPRenderer.drawText(matrices, col + String.format("%.2f", timeDisplay) + "s", endPoint.add(0, 2, 0), 2.0f, cameraPos);
            }
        }
    }

    private static float getTime(boolean firstSpawn, long timeTook) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return (firstSpawn ? 2000 : 0) + c.bloodAssistTick * 50f - timeTook + c.bloodAssistOffset;
    }

    private static Vec3 calcEndVector(Vec3 curr, Vec3 last, float mult, boolean skip) {
        if (last == null || skip) return curr;
        return new Vec3(last.x + (curr.x - last.x) * mult, last.y + (curr.y - last.y) * mult, last.z + (curr.z - last.z) * mult);
    }

    private static String skullTexture(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResolvableProfile rp = stack.get(DataComponents.PROFILE);
        if (rp == null) return null;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return null;
        return props.iterator().next().value();
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bloodCampClasses.allowsLocal()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<String> lines = new ArrayList<>();
        if (config.bloodCampMoveTimer && hasFinal) {
            float remaining = Math.max(0, (finalTick - tickCounter)) * 0.05f;
            lines.add(String.format("§cMove Timer: §f%.2fs", remaining));
        } else if (config.bloodReturnTimer && nextMoveEstimateTick > tickCounter) {
            float rem = (nextMoveEstimateTick - tickCounter) * 0.05f;
            if (rem <= 120f) lines.add(String.format("§eReturn to Blood: §f~%.1fs", rem));
        }
        if (config.bloodCampHpBar) {
            String hp = getWatcherHpText();
            if (hp != null) lines.add(hp);
        }
        if (lines.isEmpty()) return;

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(config.bloodCampX, config.bloodCampY);
        pose.scale(config.bloodCampScale, config.bloodCampScale);
        int y = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, y, 0xFFFFFFFF);
            y += 10;
        }
        pose.popMatrix();
    }

    private static String getWatcherHpText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return null;
        var events = ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).teslamaps$getEvents();
        if (events == null || events.isEmpty()) return null;
        for (LerpingBossEvent ev : events.values()) {
            if (ev.getName() == null
                    || !ev.getName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "").contains("The Watcher")) continue;
            float progress = ev.getProgress();
            if (progress < 0.05f) return null;
            int floor = DungeonManager.getCurrentFloor() != null ? DungeonManager.getCurrentFloor().getLevel() : 0;
            int max = 12 + floor;
            int cur = Math.round(max * progress);
            return "§cThe Watcher: §f" + cur + "§7/§f" + max;
        }
        return null;
    }

    private static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 20, 5);
        mc.gui.setTitle(Component.literal(text));
    }

    private static void playAlert(String soundKey, float volume) {
        var ev = com.teslamaps.utils.SoundOptions.resolve(soundKey);
        if (ev == null) return;
        if (volume <= 1.0f) {
            com.teslamaps.utils.LoudSound.play(ev, Math.max(0f, volume), 1.0f);
        } else {
            com.teslamaps.utils.LoudSound.playStacked(ev, 1.0f, 1.0f, Math.round(volume));
        }
    }

    public static void reset() {
        hasFinal = false;
        tickCounter = 0L;
        currentTickTime = 0L;
        currentWatcherEntity = null;
        firstSpawns = true;
        openTimeMs = 0L;
        openTick = 0L;
        openServerTick = 0L;
        nextMoveEstimateTick = -1L;
        entityDataMap.clear();
        renderDataMap.clear();
    }

    private static final Set<String> WATCHER_SKULLS = new HashSet<>(Arrays.asList(
            "ewogICJ0aW1lc3RhbXAiIDogMTY5NzMwOTQxNzI1NiwKICAicHJvZmlsZUlkIiA6ICJjYjYxY2U5ODc4ZWI0NDljODA5MzliNWYxNTkwMzE1MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJWb2lkZWRUcmFzaDUxODUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY2MmI2ZmI0YjhiNTg2ZGM0Y2RmODAzYjA0NDRkOWI0MWQyNDVjZGY2NjhkYWIzOGZhNmMwNjRhZmU4ZTQ2MSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjM1MjMyMiwKICAicHJvZmlsZUlkIiA6ICI3MmY5MTdjNWQyNDU0OTk0YjlmYzQ1YjVhM2YyMjIzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGF0X0d1eV9Jc19NZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yNzM5ZDdmNGU2NmE3ZGIyZWE2Y2Q0MTRlNGM0YmE0MWRmN2E5MjQ1NWM5ZmM0MmNhYWIwMTQ2NjVjMzY3YWQ1IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjI5MjgzNiwKICAicHJvZmlsZUlkIiA6ICIzZDIxZTYyMTk2NzQ0Y2QwYjM3NjNkNTU3MWNlNGJlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcl83MUJsYWNrYmlyZCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iZjZlMWU3ZWQzNjU4NmMyZDk4MDU3MDAyYmMxYWRjOTgxZTI4ODlmN2JkN2I1YjM4NTJiYzU1Y2M3ODAyMjA0IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTY5NzIzODQ0NjgxMiwKICAicHJvZmlsZUlkIiA6ICJmMjc0YzRkNjI1MDQ0ZTQxOGVmYmYwNmM3NWIyMDIxMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJIeXBpZ3NlbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80Y2VjNDAwMDhlMWMzMWMxOTg0ZjRkNjUwYWJiMzQxMGYyMDM3MTE5ZmQ2MjRhZmM5NTM1NjNiNzM1MTVhMDc3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjAwOTg2NywKICAicHJvZmlsZUlkIiA6ICJiMGQ0YjI4YmMxZDc0ODg5YWYwZTg2NjFjZWU5NmFhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaW5lU2tpbl9vcmciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjM3ZGQxOGI1OTgzYTc2N2U1NTZkYzY0NDI0YWY0YjlhYmRiNzVkNGM5ZThiMDk3ODE4YWZiYzQzMWJmMGUwOSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNTkyNDIwNSwKICAicHJvZmlsZUlkIiA6ICIzZDIxZTYyMTk2NzQ0Y2QwYjM3NjNkNTU3MWNlNGJlZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcl83MUJsYWNrYmlyZCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mNWYwZDc4ZmUzOGQxZDdmNzVmMDhjZGNmMmExODU1ZDZkYTAzMzdlMTE0YTNjNjNlM2JmM2M2MThiYzczMmIwIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTU1MDkyNjM2MSwKICAicHJvZmlsZUlkIiA6ICI0ZDcwNDg2ZjUwOTI0ZDMzODZiYmZjOWMxMmJhYjRhZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzaXJGYWJpb3pzY2hlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzUxOTY3ZGI1ZTMxOTk5MTYyNTIwMjE5MDNjZjRlOTk1MmVmN2NlYzIyMGZhYWNhMWJhNzliYWZlNTkzOGJkODAiCiAgICB9CiAgfQp9",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjIxMjc1NSwKICAicHJvZmlsZUlkIiA6ICI2NGRiNmMwNTliOTk0OTM2YTY0M2QwODEwODE0ZmJkMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVTaWx2ZXJEcmVhbXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWZkNjFlODA1NWY2ZWU5N2FiNWI2MTk2YThkN2VjOTgwNzhhYzM3ZTAwMzc2MTU3YjZiNTIwZWFhYTJmOTNhZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTYwNjIzOTU4NiwKICAicHJvZmlsZUlkIiA6ICJhYWZmMDUwYTExOTk0NzM1YjEyNDVlNDk0MGFlZjY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXN0SW1tb3J0YWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTVjMWRjNDdhMDRjZTU3MDAxYThiNzI2ZjAxOGNkZWY0MGI3ZWE5ZDdiZDZkODM1Y2E0OTVhMGVmMTY5Zjg5MyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"
    ));

    private static final Set<String> ALLOWED_MOB_SKULLS = new HashSet<>(Arrays.asList(
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDEwNjQwNTAsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVhNzk4NjBhY2E3OTk0MDdjMGZhYTEwYjFiYmNmNDI5OThmYWQ0ZWJjZjMxZDdhMjE0MTgwODI2YjRhYzk0ZTEifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDExODY2MzYsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzQ3NzQ4NzExOTBjODc4YzlhMmM0NDk2YzFlMTAyNTdjNmM0ZWExMzgwN2Q3MmMxNWQ3YWM2YWIzYTdhOWE4ZGMifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDAyMDM1NzMsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Y0NjI0YTlhOGM2OWNhMjA0NTA0YWJiMDQzZDQ3NDU2Y2Q5YjA5NzQ5YTM2MzU3NDYyMzAzZjI3NmEyMjlkNCJ9fX0=",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDExNDUyMjIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M5MTllNWI4ZDU2ZjA2MmEyMWQyMjRkZTE0YWY3NzFlMmY1NWQwOWI1OWU3YjA5OWQwOWRhYTU3NTQwYjc5Y2YiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA1MzgzODIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E4OWY2MzAzYWY4NTg3NzYxMDkxMmRjMDRiOGIxZTg5NzI0NzUyZjBhN2VlYTA1YWI2NTQ3ZTIyODE3OWMwNmYiLCJtZXRhZGF0YSI6eyJtb2RlbCI6InNsaW0ifX19fQ==",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5ODk1NTgsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzY3MjM3ZWRkYWViZGJiZGFhY2ZhOTEyODg1NTYwY2NkYzY1ZGE5M2I0YzNkNTEzNTMyODY4ZWMyM2JiNWI0NDgifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA0OTUwMjgsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZmMTg0YzE5ZTcyNTYyM2QzMjgyOGEwYTRlNzQxZTg2ZjEzNWFjNjNkYmM4MjhmZjNjODQ2ODMzOGYzNjgzYiJ9fX0=",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDEwMzA3NjUsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVjY2NkNTNmNTE5MWMyOWE5ZGM4ZjAxNzBmYmRjNGU1OWU2NjQ3NmFhZTMzZGUyN2I0NjhmMWRlMWI3Y2YzYjIifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5MTc4NzYsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I1YmE3NmUwMmNhYjcyZmE3ZDhhYzU0Y2VlYzg0OTk3NmFiMGIwMGEwMTA2OGQ2OGMyNjY3NjZiZjcwYzM5OTcifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA3Njk2MTQsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhMjNjOGNkZTI5NDNjODQyNDlkZTgzNTFiYzM1NDBiZTVmOGFmYWFiYThiMmNiMDMyZmM1YWNhZDc4YTI2OWIifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA4MTg4MDMsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkxNzFmMzViOGY1MDgxNDJiZDhjNjU0MTdkMGYzMjQxNTNhYjkxNDc3MzllZTRkMTBkZWE3MzNjYzgwZWFhMjAifX19",
            "eyJ0aW1lc3RhbXAiOjE1ODYwNDA5NTY0MjIsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdkMTJiMmFkZTQxM2E2Y2Q3Y2NhM2M5NWU5NjFiYTlmMGFlNzE2NWZhNDFmYzdiNWQ1ZjA5NGEwMTI0MGM2MDkifX19",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZjM2UzMWNmYzY2NzMzMjc1YzQyZmNmYjVkOWE0NDM0MmQ2NDNiNTVjZDE0YzljNzdkMjczYTIzNTIifX19",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzE2OTIxMSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODQyMWJhNWI4ZTM1NzNlZjk3YmViNWI0MGUxNWQxNWIyMGYzMDYzMWM0YzUzMzBjM2RlZGEzMDQ3ZGYwZTkyIgogICAgfQogIH0KfQ==",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzExMjUwMCwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWQyMjc3MmY3NjkwNDVmZGM1YmU4MTlhZDY4YjAxYTk3YWMwNGM2MDg4NmQyY2E3YWZlZTM5YjI4MmY3YTM4MyIKICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzM4Njc5NCwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWQ2N2Y5N2Q3ZjgyMTcyOWJlYjM0YTgyYzNmMTM1OTJiNDA0MzlmZTUyNDhlNzI1NzZmZGU3YWExODBiZjc3IgogICAgfQogIH0KfQ==",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzIxNTkwNSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmIzOTczYTc1MmIyNGEyZjNhYmIwMDM0MjdmNmRiZTZjYTNhNjFkYjBhMWJjZjM1MWM2ZWFiMjdlYzI3ZTUwIgogICAgfQogIH0KfQ==",
            "eyJ0aW1lc3RhbXAiOjE1NzQ0MTkzMTAxNjQsInByb2ZpbGVJZCI6Ijc1MTQ0NDgxOTFlNjQ1NDY4Yzk3MzlhNmUzOTU3YmViIiwicHJvZmlsZU5hbWUiOiJUaGFua3NNb2phbmciLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzEyNzE2ZWNiZjViOGRhMDBiMDVmMzE2ZWM2YWY2MWU4YmQwMjgwNWIyMWViOGU0NDAxNTE0NjhkYzY1NjU0OWMifX19",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTkyMzAyODAxNSwKICAicHJvZmlsZUlkIiA6ICJhMmY4MzQ1OTVjODk0YTI3YWRkMzA0OTcxNmNhOTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiUHVuY2giLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzI2MDMyNTE3MWE3YmE4NDYwODMwYzBlZWE1MTVjNzU3YTY2NWU1YjE2YTE0MjA3YmExYTMxODI3NTJiZWU4NyIKICAgIH0KICB9Cn0=",
            "ewogICJ0aW1lc3RhbXAiIDogMTU5NTQyODIyMDAyMCwKICAicHJvZmlsZUlkIiA6ICJkYTQ5OGFjNGU5Mzc0ZTVjYjYxMjdiMzgwODU1Nzk4MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJOaXRyb2hvbGljXzIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjJkOGZkM2FhNTYxN2IxZGFjMGFhZTljODFmNmRkNzBhZDkzYTU5OTQyZjQ2MGQyN2U0ZDU1YTVjYjg5MThlOCIKICAgIH0KICB9Cn0=",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNzY5NzI5Nzk3M2UwMmI3OWJjMTA2OTg0NjBiNTFhNjM5YzYwZTVlNDE3NzM0ZTExIn19fQ==",
            "ewogICJ0aW1lc3RhbXAiIDogMTU4OTc5MzA2ODgzOSwKICAicHJvZmlsZUlkIiA6ICIyYzEwNjRmY2Q5MTc0MjgyODRlM2JmN2ZhYTdlM2UxYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOYWVtZSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83ZGU3YmJiZGYyMmJmZTE3OTgwZDRlMjA2ODdlMzg2ZjExZDU5ZWUxZGI2ZjhiNDc2MjM5MWI3OWE1YWM1MzJkIgogICAgfQogIH0KfQ==",
            "ewogICJ0aW1lc3RhbXAiIDogMTU5ODk3NzI1OTM1NywKICAicHJvZmlsZUlkIiA6ICJlNzkzYjJjYTdhMmY0MTI2YTA5ODA5MmQ3Yzk5NDE3YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVfSG9zdGVyX01hbiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMTAwN2M1YjcxMTRhYmVjNzM0MjA2ZDRmYzYxM2RhNGYzYTBlOTlmNzFmZjk0OWNlZGFkYzk5MDc5MTM1YTBiIgogICAgfQogIH0KfQ=="
    ));
}
