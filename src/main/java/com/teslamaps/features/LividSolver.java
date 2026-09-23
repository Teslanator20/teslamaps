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

public class LividSolver {
    private static final BlockPos WOOL_LOCATION = new BlockPos(5, 110, 42);

    private static final Pattern LIVID_START_PATTERN = Pattern.compile(
            "^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$"
    );

    private static int invulnTime = 0;

    private static Livid currentLivid = Livid.HOCKEY; // Default
    private static Entity correctLividEntity = null;

    private static final Set<Entity> wrongLivids = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Entity> wrongNametags = Collections.newSetFromMap(new WeakHashMap<>());
    private static int lockedLividId = -1;
    private static long blindSeenAt = 0;

    private static boolean hasAnnounced = false;

    private static boolean hasAnnouncedDeath = false;

    private static boolean pendingAnnouncement = false;
    private static int announcementDelay = 0;

    private static final int LIVID_COLOR = 0xFF00FF00; // Green for correct Livid
    private static final int TRACER_LIVID = 0xFF00FF00;

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
            String prefix = name.replace(" Livid", "").trim();
            for (Livid livid : values()) {
                if (livid.entityName.equalsIgnoreCase(prefix)) {
                    return livid;
                }
            }
            return null;
        }
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (!isFloor5()) return;

        if (LIVID_START_PATTERN.matcher(message).matches()) {
            invulnTime = 390; // 19.5 seconds
            hasAnnounced = false;
            TeslaMaps.debug("[LividSolver] Livid fight started! Invuln timer: {}t", invulnTime);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        if (!TeslaMapsConfig.get().lividFinder) return;

        checkForLividArena(mc);

        if (!isFloor5()) {
            reset();
            return;
        }

        if (invulnTime > 0) {
            invulnTime--;
        }

        if (blindSeenAt == 0 && mc.player.hasEffect(MobEffects.BLINDNESS)) blindSeenAt = System.currentTimeMillis();

        if (pendingAnnouncement) {
            if (announcementDelay > 0) {
                announcementDelay--;
            } else {
                doAnnouncement();
                pendingAnnouncement = false;
            }
        }

        if (correctLividEntity != null && !correctLividEntity.isAlive() && !hasAnnouncedDeath) {
            hasAnnouncedDeath = true;
            announceLividDeath(mc);
        }

        findCorrectLivid(mc);
    }

    private static void findCorrectLivid(Minecraft mc) {
        Block woolBlock = mc.level.getBlockState(WOOL_LOCATION).getBlock();

        Livid detectedLivid = Livid.fromWool(woolBlock);
        if (detectedLivid == null) {
            return;
        }

        if (detectedLivid != currentLivid) {
            currentLivid = detectedLivid;
            TeslaMaps.debug("[LividSolver] Detected Livid type: {} (wool: {})",
                    currentLivid.entityName, woolBlock.getName().getString());
        }

        boolean tags = TeslaMapsConfig.get().lividHideWrongNametags;
        List<Entity> livids = new ArrayList<>();
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (entity instanceof ArmorStand stand) {
                if (!tags) continue;
                String name = entity.getName().getString();
                // §2﴾ §2§lLivid§r§r §a7M§c❤ §2﴿
                if (name.length() > 2 && name.charAt(0) == '§' && name.charAt(2) == '﴾' && name.contains("§lLivid")) {
                    stands.add(stand);
                }
                continue;
            }
            String name = entity.getName().getString();
            if (name.endsWith(" Livid") || name.equals("Livid")) {
                livids.add(entity);
            }
        }

        if (livids.isEmpty()) return;

        wrongLivids.clear();
        wrongNametags.clear();
        correctLividEntity = null;

        // Skyblocker's correctLividId: the real one keeps its entity even if M5 swaps colors
        Entity locked = lockedLividId >= 0 ? mc.level.getEntity(lockedLividId) : null;
        if (locked != null && locked.isAlive() && livids.contains(locked)) {
            correctLividEntity = locked;
        } else {
            for (Entity livid : livids) {
                if (Livid.fromName(livid.getName().getString()) == currentLivid) correctLividEntity = livid;
            }
        }

        // nothing found = hide nothing, a misread wool must never hide the real Livid
        if (correctLividEntity == null) return;

        if (lockedLividId < 0 && blindSeenAt > 0 && System.currentTimeMillis() - blindSeenAt >= 2000) {
            lockedLividId = correctLividEntity.getId();
        }

        for (Entity livid : livids) {
            if (livid != correctLividEntity) wrongLivids.add(livid);
        }

        if (tags && !stands.isEmpty()) {
            Livid type = Livid.fromName(correctLividEntity.getName().getString());
            char code = (type != null ? type : currentLivid).colorCode;
            for (ArmorStand stand : stands) {
                if (stand.getName().getString().charAt(1) != code) wrongNametags.add(stand);
            }
        }

        if (!hasAnnounced) {
            scheduleAnnouncement(mc);
        }
    }

    private static void scheduleAnnouncement(Minecraft mc) {
        var blindness = mc.player.getEffect(MobEffects.BLINDNESS);
        if (blindness != null && blindness.getDuration() > 20) {
            pendingAnnouncement = true;
            announcementDelay = blindness.getDuration() - 20; // Announce slightly before it ends
            TeslaMaps.debug("[LividSolver] Delaying announcement by {}t due to blindness", announcementDelay);
        } else {
            doAnnouncement();
        }
    }

    private static void doAnnouncement() {
        if (hasAnnounced) return;
        hasAnnounced = true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String coloredName = "§" + currentLivid.colorCode + currentLivid.entityName;
        Component message = Component.literal("[TeslaMaps] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Found Livid: ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(coloredName + " Livid"));

        mc.player.sendSystemMessage(message);
        TeslaMaps.debug("[LividSolver] Announced: Found {} Livid", currentLivid.entityName);
    }

    private static void announceLividDeath(Minecraft mc) {
        if (mc.player == null) return;
        if (!TeslaMapsConfig.get().lividDeathMessage) return;

        mc.player.connection.sendCommand("pc Livid Dead!");
        TeslaMaps.debug("[LividSolver] Announced Livid death to party");
    }

    public static void reset() {
        invulnTime = 0;
        currentLivid = Livid.HOCKEY;
        correctLividEntity = null;
        wrongLivids.clear();
        wrongNametags.clear();
        lockedLividId = -1;
        blindSeenAt = 0;
        hasAnnounced = false;
        hasAnnouncedDeath = false;
        pendingAnnouncement = false;
        announcementDelay = 0;
    }

    private static boolean inLividArena = false;

    private static boolean isFloor5() {
        DungeonFloor floor = DungeonManager.getCurrentFloor();
        if (floor == DungeonFloor.F5 || floor == DungeonFloor.M5) {
            return true;
        }
        return inLividArena;
    }

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

    private static boolean isInBoss() {
        return DungeonManager.getCurrentState() == DungeonState.BOSS_FIGHT;
    }

    public static int getInvulnTime() {
        return invulnTime;
    }

    public static Entity getCorrectLivid() {
        return correctLividEntity;
    }

    public static Set<Entity> getWrongLivids() {
        return wrongLivids;
    }

    public static boolean isCorrectLivid(Entity entity) {
        return entity == correctLividEntity && correctLividEntity != null;
    }

    public static boolean isWrongLivid(Entity entity) {
        return !TeslaMapsConfig.get().lividShowWrong && wrongLivids.contains(entity);
    }

    public static boolean shouldHideNametag(Entity entity) {
        return !wrongNametags.isEmpty() && TeslaMapsConfig.get().lividFinder && !TeslaMapsConfig.get().lividShowWrong
                && !LegitMode.blocksCheats() && wrongNametags.contains(entity);
    }

    public static boolean hasBlindness() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return mc.player.hasEffect(MobEffects.BLINDNESS);
    }

    public static void renderHUD(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (TeslaMapsConfig.get().lividTimerHud && TeslaMapsConfig.get().section("Timers")) return;
        if (!isFloor5()) return;
        if (invulnTime <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

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

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;
        int y = 10;

        context.text(mc.font, displayText, x, y, 0xFFFFFFFF, true);
    }

    public static void renderWorld(PoseStack matrices, MultiBufferSource provider,
                                   Vec3 cameraPos, Vec3 playerEyePos) {
        if (!TeslaMapsConfig.get().lividFinder) return;
        if (!isFloor5()) return;

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

    public static boolean shouldGlow(Entity entity) {
        if (!TeslaMapsConfig.get().lividFinder) return false;
        if (!isFloor5()) return false;
        if (hasBlindness()) return false;

        return entity == correctLividEntity && correctLividEntity != null;
    }

    public static int getGlowColor(Entity entity) {
        if (entity == correctLividEntity) {
            return LIVID_COLOR & 0x00FFFFFF;
        }
        return 0;
    }
}
