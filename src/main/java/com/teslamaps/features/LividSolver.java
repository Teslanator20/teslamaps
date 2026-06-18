package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.dungeon.DungeonState;
import com.teslamaps.render.ESPRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Livid Solver for F5/M5 boss fight.
 * Features:
 * - Detects the correct Livid by checking wool block color
 * - Shows invulnerability timer HUD
 * - Highlights correct Livid with ESP/glow
 * - Announces found Livid in chat
 * - Handles blindness effect (delays rendering until blindness wears off)
 */
public class LividSolver {
    // Wool indicator block position (verified working position)
    private static final BlockPos WOOL_LOCATION = new BlockPos(5, 110, 42);

    // Livid start message regex
    private static final Pattern LIVID_START_PATTERN = Pattern.compile(
            "^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$"
    );

    // Invulnerability timer (starts at 390 ticks = 19.5 seconds)
    private static int invulnTime = 0;

    // Current correct Livid
    private static Livid currentLivid = Livid.HOCKEY; // Default
    private static Entity correctLividEntity = null;

    // Wrong Livids to hide
    private static final Set<Entity> wrongLivids = Collections.newSetFromMap(new WeakHashMap<>());

    // Track if we've announced the Livid this fight
    private static boolean hasAnnounced = false;

    // Track if we've announced Livid death
    private static boolean hasAnnouncedDeath = false;

    // Pending announcement (delayed due to blindness)
    private static boolean pendingAnnouncement = false;
    private static int announcementDelay = 0;

    // Colors
    private static final int LIVID_COLOR = 0xFF00FF00; // Green for correct Livid
    private static final int TRACER_LIVID = 0xFF00FF00;

    /**
     * Livid types with their wool block and color codes.
     */
    public enum Livid {
        VENDETTA("Vendetta", 'f', 0xFFFFFFFF, Blocks.WHITE_WOOL),
        CROSSED("Crossed", 'd', 0xFFAA00AA, Blocks.MAGENTA_WOOL),
        ARCADE("Arcade", 'e', 0xFFFFFF55, Blocks.YELLOW_WOOL),
        SMILE("Smile", 'a', 0xFF55FF55, Blocks.LIME_WOOL),
        DOCTOR("Doctor", '7', 0xFFAAAAAA, Blocks.GRAY_WOOL),
        FROG("Frog", '2', 0xFF00AA00, Blocks.GREEN_WOOL),
        SCREAM("Scream", '9', 0xFF5555FF, Blocks.BLUE_WOOL),
        PURPLE("Purple", '5', 0xFFAA00AA, Blocks.PURPLE_WOOL),
        HOCKEY("Hockey", 'c', 0xFFFF5555, Blocks.RED_WOOL);

        public final String entityName;
        public final char colorCode;
        public final int color;
        public final Block wool;

        Livid(String entityName, char colorCode, int color, Block wool) {
            this.entityName = entityName;
            this.colorCode = colorCode;
            this.color = color;
            this.wool = wool;
        }

        public static Livid fromWool(Block wool) {
            for (Livid livid : values()) {
                if (livid.wool == wool) {
                    return livid;
                }
            }
            return null;
        }

        public static Livid fromName(String name) {
            // Name format: "Vendetta Livid", "Crossed Livid", etc.
            String prefix = name.replace(" Livid", "").trim();
            for (Livid livid : values()) {
                if (livid.entityName.equalsIgnoreCase(prefix)) {
                    return livid;
                }
            }
            return null;
        }
    }

    /**
     * Called when a chat message is received.
     */
    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (!isFloor5()) return;

        // Check for Livid fight start
        if (LIVID_START_PATTERN.matcher(message).matches()) {
            invulnTime = 390; // 19.5 seconds
            hasAnnounced = false;
            TeslaMaps.LOGGER.info("[LividSolver] Livid fight started! Invuln timer: {}t", invulnTime);
        }
    }

    /**
     * Called each server tick.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        if (!TeslaMapsConfig.get().lividFinder) return;

        // Check for Livid entities to detect boss arena (even if floor detection fails)
        checkForLividArena(mc);

        // Only require floor 5 OR presence of Livid entities
        if (!isFloor5()) {
            reset();
            return;
        }

        // Decrement invuln timer
        if (invulnTime > 0) {
            invulnTime--;
        }

        // Process pending announcement when blindness wears off
        if (pendingAnnouncement) {
            if (announcementDelay > 0) {
                announcementDelay--;
            } else {
                // Blindness should be gone now
                doAnnouncement();
                pendingAnnouncement = false;
            }
        }

        // Check if correct Livid has died
        if (correctLividEntity != null && !correctLividEntity.isAlive() && !hasAnnouncedDeath) {
            hasAnnouncedDeath = true;
            announceLividDeath(mc);
        }

        // Find correct Livid
        findCorrectLivid(mc);
    }

    /**
     * Find the correct Livid by checking wool block color.
     */
    private static void findCorrectLivid(Minecraft mc) {
        // Check wool block at indicator position
        Block woolBlock = mc.level.getBlockState(WOOL_LOCATION).getBlock();

        Livid detectedLivid = Livid.fromWool(woolBlock);
        if (detectedLivid == null) {
            return;
        }

        if (detectedLivid != currentLivid) {
            currentLivid = detectedLivid;
            TeslaMaps.LOGGER.info("[LividSolver] Detected Livid type: {} (wool: {})",
                    currentLivid.entityName, woolBlock.getName().getString());
        }

        // Find all Livid entities
        List<Entity> livids = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (entity instanceof ArmorStand) continue;

            String name = entity.getName().getString();
            if (name.endsWith(" Livid") || name.equals("Livid")) {
                livids.add(entity);
            }
        }

        if (livids.isEmpty()) return;

        // Find the correct one
        wrongLivids.clear();
        correctLividEntity = null;

        for (Entity livid : livids) {
            String lividName = livid.getName().getString();
            Livid type = Livid.fromName(lividName);

            if (type == currentLivid) {
                correctLividEntity = livid;
            } else {
                wrongLivids.add(livid);
            }
        }

        // Announce if we found the correct one and haven't announced yet
        if (correctLividEntity != null && !hasAnnounced) {
            scheduleAnnouncement(mc);
        }
    }

    /**
     * Schedule announcement (handles blindness delay).
     */
    private static void scheduleAnnouncement(Minecraft mc) {
        // Check for blindness effect
        var blindness = mc.player.getEffect(MobEffects.BLINDNESS);
        if (blindness != null && blindness.getDuration() > 20) {
            // Delay announcement until blindness wears off
            pendingAnnouncement = true;
            announcementDelay = blindness.getDuration() - 20; // Announce slightly before it ends
            TeslaMaps.LOGGER.info("[LividSolver] Delaying announcement by {}t due to blindness", announcementDelay);
        } else {
            // Announce now
            doAnnouncement();
        }
    }

    /**
     * Actually send the announcement to chat.
     */
    private static void doAnnouncement() {
        if (hasAnnounced) return;
        hasAnnounced = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Format: "Found Livid: §{colorCode}{name}"
        String coloredName = "§" + currentLivid.colorCode + currentLivid.entityName;
        Component message = Component.literal("[TeslaMaps] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Found Livid: ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(coloredName + " Livid"));

        mc.player.sendSystemMessage(message);
        TeslaMaps.LOGGER.info("[LividSolver] Announced: Found {} Livid", currentLivid.entityName);
    }

    /**
     * Announce Livid death to party chat.
     */
    private static void announceLividDeath(Minecraft mc) {
        if (mc.player == null) return;
        if (!TeslaMapsConfig.get().lividDeathMessage) return;

        // Send party chat message
        mc.player.connection.sendCommand("pc Livid Dead!");
        TeslaMaps.LOGGER.info("[LividSolver] Announced Livid death to party");
    }

    /**
     * Reset state (called on world change, leaving dungeon, etc.).
     */
    public static void reset() {
        invulnTime = 0;
        currentLivid = Livid.HOCKEY;
        correctLividEntity = null;
        wrongLivids.clear();
        hasAnnounced = false;
        hasAnnouncedDeath = false;
        pendingAnnouncement = false;
        announcementDelay = 0;
    }

    // Track if we're in Livid boss arena (detected by presence of Livid entities)
    private static boolean inLividArena = false;

    /**
     * Check if we're on Floor 5 (F5 or M5) OR in Livid boss arena.
     * The boss arena has positive coordinates, so floor detection may fail.
     * We detect Livid arena by presence of Livid entities.
     */
    private static boolean isFloor5() {
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == DungeonFloor.F5 || floor == DungeonFloor.M5) {
            return true;
        }
        // Also check if we're in Livid arena (detected by presence of Livid entities)
        return inLividArena;
    }

    /**
     * Check for Livid entities to detect if we're in Livid boss arena.
     */
    private static void checkForLividArena(Minecraft mc) {
        inLividArena = false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand) continue;
            String name = entity.getName().getString();
            if (name.endsWith(" Livid") || name.equals("Livid")) {
                inLividArena = true;
                return;
            }
        }
    }

    /**
     * Check if we're in boss fight.
     */
    private static boolean isInBoss() {
        return DungeonManager.getCurrentState() == DungeonState.BOSS_FIGHT;
    }

    /**
     * Get invulnerability time remaining in ticks.
     */
    public static int getInvulnTime() {
        return invulnTime;
    }

    /**
     * Get the correct Livid entity (for ESP).
     */
    public static Entity getCorrectLivid() {
        return correctLividEntity;
    }

    /**
     * Get wrong Livids (for hiding/dimming).
     */
    public static Set<Entity> getWrongLivids() {
        return wrongLivids;
    }

    /**
     * Check if entity is the correct Livid.
     */
    public static boolean isCorrectLivid(Entity entity) {
        return entity == correctLividEntity && correctLividEntity != null;
    }

    /**
     * Check if entity is a wrong Livid.
     */
    public static boolean isWrongLivid(Entity entity) {
        return wrongLivids.contains(entity);
    }

    /**
     * Check if player has blindness (for render checks).
     */
    public static boolean hasBlindness() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.hasEffect(MobEffects.BLINDNESS);
    }

    /**
     * Render HUD (invulnerability timer).
     */
    public static void renderHUD(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (!isFloor5()) return;
        if (invulnTime <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Color based on time remaining
        // > 260t (13s) = green, > 130t (6.5s) = yellow, else red
        ChatFormatting color;
        if (invulnTime > 260) {
            color = ChatFormatting.GREEN;
        } else if (invulnTime > 130) {
            color = ChatFormatting.YELLOW;
        } else {
            color = ChatFormatting.RED;
        }

        String text = "Livid: " + invulnTime + "t";
        Component displayText = Component.literal(text).withStyle(color);

        // Position - top center of screen
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;
        int y = 10;

        context.text(mc.font, displayText, x, y, 0xFFFFFFFF, true);
    }

    /**
     * Render world elements (ESP for correct Livid).
     * Only renders if blindness has worn off.
     */
    public static void renderWorld(PoseStack matrices, MultiBufferSource provider,
                                   Vec3 cameraPos, Vec3 playerEyePos) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (!isFloor5()) return;

        // Don't render ESP while blinded
        if (hasBlindness()) return;

        if (correctLividEntity != null && correctLividEntity.isAlive()) {
            AABB lividBox = correctLividEntity.getBoundingBox();
            ESPRenderer.drawESPBox(matrices, lividBox, LIVID_COLOR, cameraPos);
            if (TeslaMapsConfig.get().lividTracer) {
                Vec3 lividCenter = lividBox.getCenter();
                ESPRenderer.drawTracerFromCamera(matrices, lividCenter, TRACER_LIVID, cameraPos);
            }
        }
    }

    /**
     * Should entity glow (for mixin).
     */
    public static boolean shouldGlow(Entity entity) {
        if (!TeslaMapsConfig.get().lividFinder) return false;
        if (!isFloor5()) return false;
        if (hasBlindness()) return false;

        return entity == correctLividEntity && correctLividEntity != null;
    }

    /**
     * Get glow color for entity.
     */
    public static int getGlowColor(Entity entity) {
        if (entity == correctLividEntity) {
            return LIVID_COLOR & 0x00FFFFFF;
        }
        return 0;
    }
}
