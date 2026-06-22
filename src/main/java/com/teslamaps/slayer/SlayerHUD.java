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
package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

public class SlayerHUD {
    private static final Pattern INFERNO_PATTERN = Pattern.compile("Inferno Demonlord (I{1,3}V?|IV|V)\\s*(ᛤ)?\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern PHASE_PATTERN = Pattern.compile("(SPIRIT|ASHEN|CRYSTAL|AURIC)\\s*♨(\\d+)");

    private static final Pattern MINIBOSS_PATTERN = Pattern.compile("♨\\s*(Burningsoul|Kindleheart)\\s*Demon\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern QUAZII_PATTERN = Pattern.compile("ⓆⓊⒶⓏⒾⒾ\\s*([\\d.]+)([kKmMbB]?)❤");
    private static final Pattern TYPHOEUS_PATTERN = Pattern.compile("ⓉⓎⓅⒽⓄⒺⓊⓈ\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern SPAWNED_BY_PATTERN = Pattern.compile("(?i)spawned\\s*by:?\\s*(\\w+)");

    private static final double[] INFERNO_MAX_HP = {
        0,          // Tier 0 (unused)
        250_000,    // Tier I
        1_000_000,  // Tier II
        5_000_000,  // Tier III
        50_000_000, // Tier IV
        200_000_000 // Tier V
    };

    private static String currentBossName = null;
    private static double currentHP = 0;
    private static double maxHP = 0;
    private static double phaseMaxHP = 0;  // Max HP for current phase
    private static int bossTier = 0;
    private static boolean hasShield = false;
    private static long lastUpdateTime = 0;
    private static Entity bossEntity = null;  // The actual boss entity for ESP

    private static String currentPhaseType = null;  // SPIRIT, ASHEN, CRYSTAL, AURIC
    private static int phaseCount = 0;

    private static final Set<Entity> minibossEntities = new HashSet<>();
    private static String miniboss1Type = null;
    private static String miniboss2Type = null;

    private static double quaziiHP = 0;
    private static double typhoeusHP = 0;
    private static long quaziiLastSeen = 0;
    private static long typhoeusLastSeen = 0;
    private static boolean quaziiDead = false;
    private static boolean typhoeusDead = false;
    private static final double QUAZII_MAX_HP = 10_000_000;  // 10M
    private static final double TYPHOEUS_MAX_HP = 10_000_000; // 10M
    private static final long MINION_TIMEOUT = 5000; // 5 seconds before hiding if not seen

    private static long questStartTime = 0;       // When the slayer quest FIRST started (persists through demon phases)
    private static long lastKillTime = 0;         // When the last boss was killed
    private static boolean questActive = false;   // Is a slayer quest currently active?
    private static final List<Long> killTimes = new ArrayList<>();    // Fight durations
    private static final List<Long> spawnTimes = new ArrayList<>();   // Time between kills
    private static int totalKills = 0;            // Total kills this session

    private static String bossOwner = null;       // Who spawned this boss
    private static boolean isOwnBoss = false;     // Is this our boss?

    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    private static String lastWorldName = null;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetQuest();
            lastWorldName = null;
            return;
        }

        String currentWorldName = mc.level.dimension().identifier().toString();
        if (lastWorldName != null && !lastWorldName.equals(currentWorldName)) {
            resetQuest();
        }
        lastWorldName = currentWorldName;

        minibossEntities.clear();
        miniboss1Type = null;
        miniboss2Type = null;

        boolean foundQuazii = false;
        boolean foundTyphoeus = false;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        AABB searchBox = new AABB(px - 30, py - 10, pz - 30, px + 30, py + 10, pz + 30);

        boolean foundBoss = false;
        currentPhaseType = null;
        phaseCount = 0;
        bossEntity = null;

        for (ArmorStand armorStand : mc.level.getEntitiesOfClass(ArmorStand.class, searchBox, as -> true)) {
            String name = armorStand.getName().getString();

            Matcher bossMatcher = INFERNO_PATTERN.matcher(name);
            if (bossMatcher.find()) {
                String tierStr = bossMatcher.group(1);
                String shield = bossMatcher.group(2);
                String hpNum = bossMatcher.group(3);
                String hpSuffix = bossMatcher.group(4);

                bossTier = parseTier(tierStr);
                currentHP = parseHP(hpNum, hpSuffix);
                hasShield = shield != null && !shield.isEmpty();

                double baseMax = bossTier > 0 && bossTier < INFERNO_MAX_HP.length ? INFERNO_MAX_HP[bossTier] : 50_000_000;
                if (hasShield) {
                    maxHP = baseMax * 3;
                    phaseMaxHP = baseMax * 3;
                } else {
                    maxHP = baseMax;
                    phaseMaxHP = baseMax;
                }

                currentBossName = "Inferno Demonlord " + tierStr;
                lastUpdateTime = System.currentTimeMillis();
                foundBoss = true;

                if (currentHP <= 0) {
                    clearBossVisuals();
                    foundBoss = false;
                    continue;
                }

                if (!questActive) {
                    questStartTime = System.currentTimeMillis();
                    questActive = true;

                    if (lastKillTime > 0) {
                        long spawnDelay = questStartTime - lastKillTime;
                        spawnTimes.add(spawnDelay);
                    }
                }

                findBossEntity(mc, armorStand);
                continue;
            }

            Matcher phaseMatcher = PHASE_PATTERN.matcher(name);
            if (phaseMatcher.find()) {
                currentPhaseType = phaseMatcher.group(1);
                phaseCount = Integer.parseInt(phaseMatcher.group(2));
                continue;
            }

            Matcher minibossMatcher = MINIBOSS_PATTERN.matcher(name);
            if (minibossMatcher.find()) {
                String type = minibossMatcher.group(1);
                if (miniboss1Type == null) {
                    miniboss1Type = type;
                } else if (miniboss2Type == null && !type.equals(miniboss1Type)) {
                    miniboss2Type = type;
                }
                findMinibossEntity(mc, armorStand, type);
            }

            Matcher quaziiMatcher = QUAZII_PATTERN.matcher(name);
            if (quaziiMatcher.find()) {
                double hp = parseHP(quaziiMatcher.group(1), quaziiMatcher.group(2));
                quaziiLastSeen = System.currentTimeMillis();
                foundQuazii = true;
                if (hp <= 0) {
                    quaziiDead = true;
                    quaziiHP = 0;
                } else {
                    quaziiHP = hp;
                }
            }

            Matcher typhoeusMatcher = TYPHOEUS_PATTERN.matcher(name);
            if (typhoeusMatcher.find()) {
                double hp = parseHP(typhoeusMatcher.group(1), typhoeusMatcher.group(2));
                typhoeusLastSeen = System.currentTimeMillis();
                foundTyphoeus = true;
                if (hp <= 0) {
                    typhoeusDead = true;
                    typhoeusHP = 0;
                } else {
                    typhoeusHP = hp;
                }
            }

            Matcher spawnedByMatcher = SPAWNED_BY_PATTERN.matcher(name);
            if (spawnedByMatcher.find()) {
                bossOwner = spawnedByMatcher.group(1);
                String playerName = mc.player.getName().getString();
                isOwnBoss = playerName.equalsIgnoreCase(bossOwner);
            }
        }

        if (TeslaMapsConfig.get().slayerOnlyOwnBoss && bossOwner != null && !isOwnBoss) {
            currentBossName = null; // Hide HUD for other players' bosses
        }

        if (!foundBoss && System.currentTimeMillis() - lastUpdateTime > 10000) {
            clearBossVisuals();
        }

        long now = System.currentTimeMillis();
        if (!foundQuazii && !quaziiDead && now - quaziiLastSeen > MINION_TIMEOUT) {
            quaziiHP = 0; // Hide after timeout
        }
        if (!foundTyphoeus && !typhoeusDead && now - typhoeusLastSeen > MINION_TIMEOUT) {
            typhoeusHP = 0; // Hide after timeout
        }
    }

    private static void findBossEntity(Minecraft mc, ArmorStand armorStand) {
        AABB searchBox = armorStand.getBoundingBox().inflate(2, 3, 2);
        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof ArmorStand))) {
            if (entity instanceof Mob || entity.getName().getString().contains("Demonlord")) {
                bossEntity = entity;
                break;
            }
        }
    }

    private static void findMinibossEntity(Minecraft mc, ArmorStand armorStand, String type) {
        AABB searchBox = armorStand.getBoundingBox().inflate(2, 3, 2);
        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof ArmorStand))) {
            if (entity instanceof Mob) {
                minibossEntities.add(entity);
                break;
            }
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().slayerHUD) return;
        boolean demonsActiveCheck = (quaziiHP > 0 && !quaziiDead) || (typhoeusHP > 0 && !typhoeusDead);
        if (currentBossName == null && !demonsActiveCheck) return;

        Minecraft mc = Minecraft.getInstance();
        Font textRenderer = mc.font;

        int x = TeslaMapsConfig.get().slayerHudX;
        int y = TeslaMapsConfig.get().slayerHudY;
        float scale = TeslaMapsConfig.get().slayerHudScale;

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);

        int bgWidth = 140;
        int padding = 6;
        int barHeight = 12;
        int smallBarHeight = 8;
        int barWidth = bgWidth - padding * 2;
        int barX = x + padding;

        boolean demonsActive = (quaziiHP > 0 && !quaziiDead) || (typhoeusHP > 0 && !typhoeusDead);
        boolean showBossSection = currentBossName != null && !demonsActive;

        int contentHeight = 0;
        if (showBossSection) {
            contentHeight += 10 + barHeight; // Boss name + main bar
            if (currentPhaseType != null) contentHeight += 12; // Phase line
        }
        if (demonsActive) {
            if (quaziiHP > 0) contentHeight += smallBarHeight + 2; // Quazii bar
            if (typhoeusHP > 0) contentHeight += smallBarHeight + 2; // Typhoeus bar
        }
        int bgHeight = contentHeight + padding * 2;

        drawRoundedRect(context, x, y, bgWidth, bgHeight, 4, 0xDD000000);

        int centerX = x + bgWidth / 2;
        int currentY = y + padding;

        if (showBossSection) {
            int nameWidth = textRenderer.width(currentBossName);
            context.text(textRenderer, currentBossName, centerX - nameWidth / 2, currentY, 0xFFFF5555, true);
            currentY += 12;

            double hpPercent = Math.min(1.0, Math.max(0, currentHP / phaseMaxHP));
            int filledWidth = (int) (barWidth * hpPercent);

            drawRoundedRect(context, barX, currentY, barWidth, barHeight, 2, 0xFF333333);

            int barColor = getPhaseColor(currentPhaseType);
            if (filledWidth > 2) {
                drawRoundedRect(context, barX, currentY, filledWidth, barHeight, 2, barColor);
            }

            String hpText = formatHP(currentHP) + " / " + formatHP(phaseMaxHP);
            int hpTextWidth = textRenderer.width(hpText);
            context.text(textRenderer, hpText, centerX - hpTextWidth / 2, currentY + 2, 0xFFFFFFFF, true);
            currentY += barHeight + 2;

            if (currentPhaseType != null) {
                int phaseColor = getPhaseColor(currentPhaseType);
                String phaseText = currentPhaseType + " x" + phaseCount;
                int phaseWidth = textRenderer.width(phaseText);
                context.text(textRenderer, phaseText, centerX - phaseWidth / 2, currentY, phaseColor, true);
                currentY += 12;
            }
        }

        if (demonsActive) {
            if (quaziiHP > 0) {
                drawMinionBar(context, textRenderer, barX, currentY, barWidth, smallBarHeight,
                    "Quazii", quaziiHP, QUAZII_MAX_HP, 0xFF55FF55); // Green
                currentY += smallBarHeight + 2;
            }

            if (typhoeusHP > 0) {
                drawMinionBar(context, textRenderer, barX, currentY, barWidth, smallBarHeight,
                    "Typhoeus", typhoeusHP, TYPHOEUS_MAX_HP, 0xFFFF55FF); // Magenta
            }
        }

        matrices.popMatrix();
    }

    private static void drawMinionBar(GuiGraphicsExtractor context, Font textRenderer,
            int x, int y, int width, int height, String name, double hp, double maxHp, int color) {
        double percent = Math.min(1.0, Math.max(0, hp / maxHp));
        int filledWidth = (int) (width * percent);

        drawRoundedRect(context, x, y, width, height, 2, 0xFF333333);

        if (filledWidth > 2) {
            drawRoundedRect(context, x, y, filledWidth, height, 2, color);
        }

        String text = name + ": " + formatHP(hp);
        context.text(textRenderer, text, x + 2, y, 0xFFFFFFFF, true);
    }

    private static void drawRoundedRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int radius, int color) {
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        context.fill(x + 1, y + 1, x + radius, y + radius, color);
        context.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        context.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        context.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    public static boolean shouldGlow(Entity entity) {
        if (bossEntity != null && entity == bossEntity && TeslaMapsConfig.get().slayerBossESP) {
            return true;
        }
        if (minibossEntities.contains(entity) && TeslaMapsConfig.get().slayerMinibossESP) {
            return true;
        }
        return false;
    }

    public static int getGlowColor(Entity entity) {
        if (bossEntity != null && entity == bossEntity) {
            return getPhaseColor(currentPhaseType) & 0x00FFFFFF;
        }
        if (minibossEntities.contains(entity)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                AABB searchBox = entity.getBoundingBox().inflate(1, 3, 1);
                for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, searchBox, a -> true)) {
                    String name = as.getName().getString();
                    if (name.contains("Burningsoul")) {
                        return 0xFF5555; // Red
                    } else if (name.contains("Kindleheart")) {
                        return 0xFFFFFF; // White
                    }
                }
            }
            return 0xFF5555; // Default red
        }
        return 0xFFFFFF;
    }

    public static boolean isSlayerMiniboss(String name) {
        return name.contains("Burningsoul Demon") || name.contains("Kindleheart Demon");
    }

    private static int parseTier(String tierStr) {
        return switch (tierStr) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 4;
        };
    }

    private static double parseHP(String num, String suffix) {
        try {
            double value = Double.parseDouble(num);
            return switch (suffix.toUpperCase()) {
                case "K" -> value * 1_000;
                case "M" -> value * 1_000_000;
                case "B" -> value * 1_000_000_000;
                default -> value;
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getPhaseColor(String phase) {
        if (phase == null) return 0xFFFFFFFF;
        return switch (phase) {
            case "SPIRIT" -> 0xFFFFFFFF;  // White
            case "ASHEN" -> 0xFFAAAAAA;   // Gray
            case "CRYSTAL" -> 0xFF55FFFF; // Light blue
            case "AURIC" -> 0xFFFFD700;   // Gold (more golden)
            default -> 0xFFFFFFFF;
        };
    }

    public static String getCurrentPhaseType() {
        return currentPhaseType;
    }

    public static Entity getBossEntity() {
        return bossEntity;
    }

    public static Set<Entity> getMinibossEntities() {
        return minibossEntities;
    }

    private static String formatHP(double hp) {
        if (hp >= 1_000_000_000) return String.format("%.1fB", hp / 1_000_000_000);
        if (hp >= 1_000_000) return String.format("%.1fM", hp / 1_000_000);
        if (hp >= 1_000) return String.format("%.1fK", hp / 1_000);
        return String.format("%.0f", hp);
    }

    public static void onSlayerComplete() {
        if (!questActive || questStartTime == 0) return;

        if (TeslaMapsConfig.get().slayerOnlyOwnBoss && bossOwner != null && !isOwnBoss) {
            resetQuest();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long killTime = System.currentTimeMillis();
        long fightDuration = killTime - questStartTime;
        killTimes.add(fightDuration);
        lastKillTime = killTime;
        totalKills++;

        sendKillMessage(mc, fightDuration);

        resetQuest();
    }

    public static void onSlayerFailed() {
        resetQuest();
    }

    private static void clearBossVisuals() {
        currentBossName = null;
        currentHP = 0;
        maxHP = 0;
        phaseMaxHP = 0;
        bossTier = 0;
        hasShield = false;
        currentPhaseType = null;
        phaseCount = 0;
        bossEntity = null;
    }

    private static void resetQuest() {
        clearBossVisuals();
        questActive = false;
        questStartTime = 0;
        bossOwner = null;
        isOwnBoss = false;
        quaziiHP = 0;
        typhoeusHP = 0;
        quaziiDead = false;
        typhoeusDead = false;
    }

    private static void sendKillMessage(Minecraft mc, long fightDuration) {
        if (mc.player == null) return;

        StringBuilder msg = new StringBuilder();
        msg.append(ChatFormatting.GOLD).append("[TeslaMaps] ").append(ChatFormatting.GREEN).append("Boss killed! ");
        msg.append(ChatFormatting.GRAY).append("(#").append(totalKills).append(") ");

        msg.append(ChatFormatting.WHITE).append("Time: ").append(ChatFormatting.YELLOW).append(formatTime(fightDuration));

        if (spawnTimes.size() > 0) {
            long lastSpawnTime = spawnTimes.get(spawnTimes.size() - 1);
            msg.append(ChatFormatting.WHITE).append(" | Spawn: ").append(ChatFormatting.AQUA).append(formatTime(lastSpawnTime));
        }

        mc.player.sendSystemMessage(Component.literal(msg.toString()));

        if (killTimes.size() >= 2) {
            long avgKill = killTimes.stream().mapToLong(Long::longValue).sum() / killTimes.size();
            StringBuilder avgMsg = new StringBuilder();
            avgMsg.append(ChatFormatting.GOLD).append("[TeslaMaps] ").append(ChatFormatting.GRAY).append("Avg (").append(totalKills).append(" kills): ");
            avgMsg.append(ChatFormatting.WHITE).append("Kill: ").append(ChatFormatting.YELLOW).append(formatTime(avgKill));

            if (spawnTimes.size() >= 2) {
                long avgSpawn = spawnTimes.stream().mapToLong(Long::longValue).sum() / spawnTimes.size();
                avgMsg.append(ChatFormatting.WHITE).append(" | Spawn: ").append(ChatFormatting.AQUA).append(formatTime(avgSpawn));
            }

            mc.player.sendSystemMessage(Component.literal(avgMsg.toString()));
        }
    }

    private static String formatTime(long ms) {
        long seconds = ms / 1000;
        long millis = (ms % 1000) / 100; // tenths of a second

        if (seconds >= 60) {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d.%d", minutes, seconds, millis);
        } else {
            return String.format("%d.%ds", seconds, millis);
        }
    }

    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!TeslaMapsConfig.get().slayerHUD || currentBossName == null) return false;

        int x = TeslaMapsConfig.get().slayerHudX;
        int y = TeslaMapsConfig.get().slayerHudY;
        int width = 148;
        int height = 50;

        if (mouseX >= x - 4 && mouseX <= x + width && mouseY >= y - 4 && mouseY <= y + height) {
            isDragging = true;
            dragOffsetX = (int) mouseX - x;
            dragOffsetY = (int) mouseY - y;
            return true;
        }
        return false;
    }

    public static boolean handleMouseDrag(double mouseX, double mouseY) {
        if (isDragging) {
            TeslaMapsConfig.get().slayerHudX = (int) mouseX - dragOffsetX;
            TeslaMapsConfig.get().slayerHudY = (int) mouseY - dragOffsetY;
            return true;
        }
        return false;
    }

    public static void handleMouseRelease() {
        if (isDragging) {
            isDragging = false;
            TeslaMapsConfig.save();
        }
    }
}
