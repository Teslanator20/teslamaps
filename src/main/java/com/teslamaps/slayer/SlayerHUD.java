package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HUD overlay for slayer boss information.
 * Shows health, phase type for Inferno Demonlord.
 * Also provides ESP for slayer minibosses and boss.
 */
public class SlayerHUD {
    // Pattern to match slayer boss names: "♨ ☠ Inferno Demonlord IV 47.7M❤" or with shield "♨ ☠ Inferno Demonlord IV ᛤ 150M❤"
    private static final Pattern INFERNO_PATTERN = Pattern.compile("Inferno Demonlord (I{1,3}V?|IV|V)\\s*(ᛤ)?\\s*([\\d.]+)([kKmMbB]?)❤");

    // Pattern to match phase armor stands: "SPIRIT ♨2 04:14"
    private static final Pattern PHASE_PATTERN = Pattern.compile("(SPIRIT|ASHEN|CRYSTAL|AURIC)\\s*♨(\\d+)");

    // Pattern to match miniboss demons
    private static final Pattern MINIBOSS_PATTERN = Pattern.compile("♨\\s*(Burningsoul|Kindleheart)\\s*Demon\\s*([\\d.]+)([kKmMbB]?)❤");

    // Boss spawn demons use circled Unicode letters
    // 🦴 ☠ ⓆⓊⒶⓏⒾⒾ 10M❤ and ☠ ☠ ⓉⓎⓅⒽⓄⒺⓊⓈ 7.4M❤
    private static final Pattern QUAZII_PATTERN = Pattern.compile("ⓆⓊⒶⓏⒾⒾ\\s*([\\d.]+)([kKmMbB]?)❤");
    private static final Pattern TYPHOEUS_PATTERN = Pattern.compile("ⓉⓎⓅⒽⓄⒺⓊⓈ\\s*([\\d.]+)([kKmMbB]?)❤");

    // Pattern to detect boss owner: "Spawned by: PlayerName" or similar
    private static final Pattern SPAWNED_BY_PATTERN = Pattern.compile("(?i)spawned\\s*by:?\\s*(\\w+)");

    // Inferno Demonlord max HP by tier (regular phase)
    private static final double[] INFERNO_MAX_HP = {
        0,          // Tier 0 (unused)
        250_000,    // Tier I
        1_000_000,  // Tier II
        5_000_000,  // Tier III
        50_000_000, // Tier IV
        200_000_000 // Tier V
    };

    // Boss state
    private static String currentBossName = null;
    private static double currentHP = 0;
    private static double maxHP = 0;
    private static double phaseMaxHP = 0;  // Max HP for current phase
    private static int bossTier = 0;
    private static boolean hasShield = false;
    private static long lastUpdateTime = 0;
    private static Entity bossEntity = null;  // The actual boss entity for ESP

    // Phase state
    private static String currentPhaseType = null;  // SPIRIT, ASHEN, CRYSTAL, AURIC
    private static int phaseCount = 0;

    // Miniboss tracking for ESP
    private static final Set<Entity> minibossEntities = new HashSet<>();
    private static String miniboss1Type = null;
    private static String miniboss2Type = null;

    // Boss spawn demon health tracking (Quazii and Typhoeus)
    private static double quaziiHP = 0;
    private static double typhoeusHP = 0;
    private static long quaziiLastSeen = 0;
    private static long typhoeusLastSeen = 0;
    private static boolean quaziiDead = false;
    private static boolean typhoeusDead = false;
    private static final double QUAZII_MAX_HP = 10_000_000;  // 10M
    private static final double TYPHOEUS_MAX_HP = 10_000_000; // 10M
    private static final long MINION_TIMEOUT = 5000; // 5 seconds before hiding if not seen

    // Kill timing tracking
    private static long questStartTime = 0;       // When the slayer quest FIRST started (persists through demon phases)
    private static long lastKillTime = 0;         // When the last boss was killed
    private static boolean questActive = false;   // Is a slayer quest currently active?
    private static final List<Long> killTimes = new ArrayList<>();    // Fight durations
    private static final List<Long> spawnTimes = new ArrayList<>();   // Time between kills
    private static int totalKills = 0;            // Total kills this session

    // Boss ownership tracking
    private static String bossOwner = null;       // Who spawned this boss
    private static boolean isOwnBoss = false;     // Is this our boss?

    // Dragging state
    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    // Server switch detection
    private static String lastWorldName = null;

    /**
     * Called every tick to scan for slayer bosses.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            resetQuest();
            lastWorldName = null;
            return;
        }

        // Detect server/world switch and reset
        String currentWorldName = mc.world.getRegistryKey().getValue().toString();
        if (lastWorldName != null && !lastWorldName.equals(currentWorldName)) {
            resetQuest();
        }
        lastWorldName = currentWorldName;

        // Always scan for minibosses if ESP enabled
        minibossEntities.clear();
        miniboss1Type = null;
        miniboss2Type = null;

        // Track if we found the minions this tick
        boolean foundQuazii = false;
        boolean foundTyphoeus = false;

        // Scan for slayer boss armor stands nearby
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        Box searchBox = new Box(px - 30, py - 10, pz - 30, px + 30, py + 10, pz + 30);

        boolean foundBoss = false;
        currentPhaseType = null;
        phaseCount = 0;
        bossEntity = null;

        for (ArmorStandEntity armorStand : mc.world.getEntitiesByClass(ArmorStandEntity.class, searchBox, as -> true)) {
            String name = armorStand.getName().getString();

            // Check for Inferno Demonlord
            Matcher bossMatcher = INFERNO_PATTERN.matcher(name);
            if (bossMatcher.find()) {
                String tierStr = bossMatcher.group(1);
                String shield = bossMatcher.group(2);
                String hpNum = bossMatcher.group(3);
                String hpSuffix = bossMatcher.group(4);

                bossTier = parseTier(tierStr);
                currentHP = parseHP(hpNum, hpSuffix);
                hasShield = shield != null && !shield.isEmpty();

                // Calculate max HP based on shield state
                double baseMax = bossTier > 0 && bossTier < INFERNO_MAX_HP.length ? INFERNO_MAX_HP[bossTier] : 50_000_000;
                if (hasShield) {
                    // Shield phase has 3x HP (e.g., 150M for Tier IV)
                    maxHP = baseMax * 3;
                    phaseMaxHP = baseMax * 3;
                } else {
                    maxHP = baseMax;
                    phaseMaxHP = baseMax;
                }

                currentBossName = "Inferno Demonlord " + tierStr;
                lastUpdateTime = System.currentTimeMillis();
                foundBoss = true;

                // If HP is 0, immediately hide boss visuals
                if (currentHP <= 0) {
                    clearBossVisuals();
                    foundBoss = false;
                    continue;
                }

                // Track quest start time - only set ONCE when quest first starts
                // This persists through demon phases until quest complete/fail
                if (!questActive) {
                    questStartTime = System.currentTimeMillis();
                    questActive = true;

                    // Calculate spawn time (time since last kill)
                    if (lastKillTime > 0) {
                        long spawnDelay = questStartTime - lastKillTime;
                        spawnTimes.add(spawnDelay);
                    }
                }

                // Find the actual boss entity near this armor stand for ESP
                findBossEntity(mc, armorStand);
                continue;
            }

            // Check for phase type (SPIRIT, ASHEN, CRYSTAL, AURIC)
            Matcher phaseMatcher = PHASE_PATTERN.matcher(name);
            if (phaseMatcher.find()) {
                currentPhaseType = phaseMatcher.group(1);
                phaseCount = Integer.parseInt(phaseMatcher.group(2));
                continue;
            }

            // Check for miniboss demons (Burningsoul, Kindleheart)
            Matcher minibossMatcher = MINIBOSS_PATTERN.matcher(name);
            if (minibossMatcher.find()) {
                String type = minibossMatcher.group(1);
                if (miniboss1Type == null) {
                    miniboss1Type = type;
                } else if (miniboss2Type == null && !type.equals(miniboss1Type)) {
                    miniboss2Type = type;
                }
                // Find the actual miniboss entity for ESP
                findMinibossEntity(mc, armorStand, type);
            }

            // Check for Quazii
            Matcher quaziiMatcher = QUAZII_PATTERN.matcher(name);
            if (quaziiMatcher.find()) {
                double hp = parseHP(quaziiMatcher.group(1), quaziiMatcher.group(2));
                quaziiLastSeen = System.currentTimeMillis();
                foundQuazii = true;
                if (hp <= 0) {
                    // Immediately hide when HP is 0
                    quaziiDead = true;
                    quaziiHP = 0;
                } else {
                    quaziiHP = hp;
                }
            }

            // Check for Typhoeus
            Matcher typhoeusMatcher = TYPHOEUS_PATTERN.matcher(name);
            if (typhoeusMatcher.find()) {
                double hp = parseHP(typhoeusMatcher.group(1), typhoeusMatcher.group(2));
                typhoeusLastSeen = System.currentTimeMillis();
                foundTyphoeus = true;
                if (hp <= 0) {
                    // Immediately hide when HP is 0
                    typhoeusDead = true;
                    typhoeusHP = 0;
                } else {
                    typhoeusHP = hp;
                }
            }

            // Check for "Spawned by" to determine boss owner
            Matcher spawnedByMatcher = SPAWNED_BY_PATTERN.matcher(name);
            if (spawnedByMatcher.find()) {
                bossOwner = spawnedByMatcher.group(1);
                // Check if this is our boss
                String playerName = mc.player.getName().getString();
                isOwnBoss = playerName.equalsIgnoreCase(bossOwner);
            }
        }

        // If "only own boss" is enabled and this isn't our boss, hide HUD
        if (TeslaMapsConfig.get().slayerOnlyOwnBoss && bossOwner != null && !isOwnBoss) {
            currentBossName = null; // Hide HUD for other players' bosses
        }

        // Clear boss visuals if not found for 10 seconds (boss might be spawning demons)
        // This does NOT reset quest timing - that only happens on quest complete/fail
        if (!foundBoss && System.currentTimeMillis() - lastUpdateTime > 10000) {
            clearBossVisuals();
        }

        // Handle Quazii/Typhoeus persistence - keep showing if seen recently and not dead
        long now = System.currentTimeMillis();
        if (!foundQuazii && !quaziiDead && now - quaziiLastSeen > MINION_TIMEOUT) {
            quaziiHP = 0; // Hide after timeout
        }
        if (!foundTyphoeus && !typhoeusDead && now - typhoeusLastSeen > MINION_TIMEOUT) {
            typhoeusHP = 0; // Hide after timeout
        }
    }

    /**
     * Find the actual boss entity near an armor stand.
     */
    private static void findBossEntity(MinecraftClient mc, ArmorStandEntity armorStand) {
        Box searchBox = armorStand.getBoundingBox().expand(2, 3, 2);
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, searchBox, e -> !(e instanceof ArmorStandEntity))) {
            if (entity instanceof MobEntity || entity.getName().getString().contains("Demonlord")) {
                bossEntity = entity;
                break;
            }
        }
    }

    /**
     * Find miniboss entities near armor stands.
     */
    private static void findMinibossEntity(MinecraftClient mc, ArmorStandEntity armorStand, String type) {
        Box searchBox = armorStand.getBoundingBox().expand(2, 3, 2);
        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, searchBox, e -> !(e instanceof ArmorStandEntity))) {
            if (entity instanceof MobEntity) {
                minibossEntities.add(entity);
                break;
            }
        }
    }

    /**
     * Render the slayer HUD.
     */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!TeslaMapsConfig.get().slayerHUD) return;
        // Show HUD if boss is active OR if demons are active
        boolean demonsActiveCheck = (quaziiHP > 0 && !quaziiDead) || (typhoeusHP > 0 && !typhoeusDead);
        if (currentBossName == null && !demonsActiveCheck) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer textRenderer = mc.textRenderer;

        int x = TeslaMapsConfig.get().slayerHudX;
        int y = TeslaMapsConfig.get().slayerHudY;
        float scale = TeslaMapsConfig.get().slayerHudScale;

        // Apply scaling
        var matrices = context.getMatrices();
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

        // Check if demons are active - if so, hide boss HP and only show demons
        boolean demonsActive = (quaziiHP > 0 && !quaziiDead) || (typhoeusHP > 0 && !typhoeusDead);
        boolean showBossSection = currentBossName != null && !demonsActive;

        // Calculate content height based on what's visible
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

        // Draw rounded background
        drawRoundedRect(context, x, y, bgWidth, bgHeight, 4, 0xDD000000);

        int centerX = x + bgWidth / 2;
        int currentY = y + padding;

        // Boss section (only if boss is active AND demons are not active)
        if (showBossSection) {
            // Boss name (centered)
            int nameWidth = textRenderer.getWidth(currentBossName);
            context.drawText(textRenderer, currentBossName, centerX - nameWidth / 2, currentY, 0xFFFF5555, true);
            currentY += 12;

            // Health bar
            double hpPercent = Math.min(1.0, Math.max(0, currentHP / phaseMaxHP));
            int filledWidth = (int) (barWidth * hpPercent);

            // Bar background with slight rounding
            drawRoundedRect(context, barX, currentY, barWidth, barHeight, 2, 0xFF333333);

            // Bar fill
            int barColor = getPhaseColor(currentPhaseType);
            if (filledWidth > 2) {
                drawRoundedRect(context, barX, currentY, filledWidth, barHeight, 2, barColor);
            }

            // HP text on bar (centered)
            String hpText = formatHP(currentHP) + " / " + formatHP(phaseMaxHP);
            int hpTextWidth = textRenderer.getWidth(hpText);
            context.drawText(textRenderer, hpText, centerX - hpTextWidth / 2, currentY + 2, 0xFFFFFFFF, true);
            currentY += barHeight + 2;

            // Phase type with count (centered, only if phase exists)
            if (currentPhaseType != null) {
                int phaseColor = getPhaseColor(currentPhaseType);
                String phaseText = currentPhaseType + " x" + phaseCount;
                int phaseWidth = textRenderer.getWidth(phaseText);
                context.drawText(textRenderer, phaseText, centerX - phaseWidth / 2, currentY, phaseColor, true);
                currentY += 12;
            }
        }

        // Quazii and Typhoeus health bars (only when demons are active)
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

    /**
     * Draw a small health bar for minions (Quazii/Typhoeus).
     */
    private static void drawMinionBar(DrawContext context, TextRenderer textRenderer,
            int x, int y, int width, int height, String name, double hp, double maxHp, int color) {
        double percent = Math.min(1.0, Math.max(0, hp / maxHp));
        int filledWidth = (int) (width * percent);

        // Background
        drawRoundedRect(context, x, y, width, height, 2, 0xFF333333);

        // Fill
        if (filledWidth > 2) {
            drawRoundedRect(context, x, y, filledWidth, height, 2, color);
        }

        // Text: "Name: HP"
        String text = name + ": " + formatHP(hp);
        context.drawText(textRenderer, text, x + 2, y, 0xFFFFFFFF, true);
    }

    /**
     * Draw a rectangle with rounded corners.
     */
    private static void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        // Main body
        context.fill(x + radius, y, x + width - radius, y + height, color);
        // Left and right strips
        context.fill(x, y + radius, x + radius, y + height - radius, color);
        context.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        // Corners (simplified as small squares for performance)
        context.fill(x + 1, y + 1, x + radius, y + radius, color);
        context.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        context.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        context.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    /**
     * Check if entity should have slayer ESP glow.
     */
    public static boolean shouldGlow(Entity entity) {
        // Boss glow
        if (bossEntity != null && entity == bossEntity && TeslaMapsConfig.get().slayerBossESP) {
            return true;
        }
        // Miniboss glow
        if (minibossEntities.contains(entity) && TeslaMapsConfig.get().slayerMinibossESP) {
            return true;
        }
        return false;
    }

    /**
     * Get glow color for slayer entities.
     */
    public static int getGlowColor(Entity entity) {
        // Boss gets phase-based color
        if (bossEntity != null && entity == bossEntity) {
            return getPhaseColor(currentPhaseType) & 0x00FFFFFF;
        }
        // Minibosses get type-based color
        if (minibossEntities.contains(entity)) {
            // Check which type by looking at nearby armor stands
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                Box searchBox = entity.getBoundingBox().expand(1, 3, 1);
                for (ArmorStandEntity as : mc.world.getEntitiesByClass(ArmorStandEntity.class, searchBox, a -> true)) {
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

    /**
     * Check if an armor stand name indicates a slayer miniboss.
     */
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

    /**
     * Get color for phase type.
     * Spirit = white, Ashen = gray, Crystal = light blue, Auric = gold
     */
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

    /**
     * Called when "SLAYER QUEST COMPLETE" chat message is received.
     */
    public static void onSlayerComplete() {
        if (!questActive || questStartTime == 0) return;

        // Only count kills if it's our boss (or if "only own boss" is disabled)
        if (TeslaMapsConfig.get().slayerOnlyOwnBoss && bossOwner != null && !isOwnBoss) {
            resetQuest();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        long killTime = System.currentTimeMillis();
        long fightDuration = killTime - questStartTime;
        killTimes.add(fightDuration);
        lastKillTime = killTime;
        totalKills++;

        // Send chat message with times
        sendKillMessage(mc, fightDuration);

        // Reset quest state
        resetQuest();
    }

    /**
     * Called when "SLAYER QUEST FAILED" chat message is received.
     * Just resets without counting stats.
     */
    public static void onSlayerFailed() {
        resetQuest();
    }

    /**
     * Clear visual boss state (when boss disappears temporarily).
     * Does NOT reset questActive/questStartTime - those persist until quest complete/fail.
     */
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
        // Don't reset questActive, questStartTime, bossOwner, isOwnBoss!
    }

    /**
     * Full reset when quest completes or fails.
     */
    private static void resetQuest() {
        clearBossVisuals();
        questActive = false;
        questStartTime = 0;
        bossOwner = null;
        isOwnBoss = false;
        // Reset minions - quest is over
        quaziiHP = 0;
        typhoeusHP = 0;
        quaziiDead = false;
        typhoeusDead = false;
    }

    /**
     * Send kill statistics to chat.
     */
    private static void sendKillMessage(MinecraftClient mc, long fightDuration) {
        if (mc.player == null) return;

        StringBuilder msg = new StringBuilder();
        msg.append(Formatting.GOLD).append("[TeslaMaps] ").append(Formatting.GREEN).append("Boss killed! ");
        msg.append(Formatting.GRAY).append("(#").append(totalKills).append(") ");

        // Fight duration
        msg.append(Formatting.WHITE).append("Time: ").append(Formatting.YELLOW).append(formatTime(fightDuration));

        // Spawn time (if available)
        if (spawnTimes.size() > 0) {
            long lastSpawnTime = spawnTimes.get(spawnTimes.size() - 1);
            msg.append(Formatting.WHITE).append(" | Spawn: ").append(Formatting.AQUA).append(formatTime(lastSpawnTime));
        }

        mc.player.sendMessage(Text.literal(msg.toString()), false);

        // Send averages if we have enough data
        if (killTimes.size() >= 2) {
            long avgKill = killTimes.stream().mapToLong(Long::longValue).sum() / killTimes.size();
            StringBuilder avgMsg = new StringBuilder();
            avgMsg.append(Formatting.GOLD).append("[TeslaMaps] ").append(Formatting.GRAY).append("Avg (").append(totalKills).append(" kills): ");
            avgMsg.append(Formatting.WHITE).append("Kill: ").append(Formatting.YELLOW).append(formatTime(avgKill));

            if (spawnTimes.size() >= 2) {
                long avgSpawn = spawnTimes.stream().mapToLong(Long::longValue).sum() / spawnTimes.size();
                avgMsg.append(Formatting.WHITE).append(" | Spawn: ").append(Formatting.AQUA).append(formatTime(avgSpawn));
            }

            mc.player.sendMessage(Text.literal(avgMsg.toString()), false);
        }
    }

    /**
     * Format milliseconds as MM:SS.s or SS.ss
     */
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

    // Dragging support
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
