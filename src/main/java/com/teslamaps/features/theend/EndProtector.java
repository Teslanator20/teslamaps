package com.teslamaps.features.theend;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ServerTicks;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import com.teslamaps.utils.TickScheduler;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * End Stone Protector: stage tracking, spawn location, DPS and loot quality, rare drop alerts.
 * Ported from GanKura's GolemHandler / GolemLocationScanner / RareDropScanner.
 */
public class EndProtector {

    public static final String AREA = "The End";

    public static final String STAGE_RESTING = "Resting";
    public static final String STAGE_DORMANT = "Dormant";
    public static final String STAGE_AGITATED = "Agitated";
    public static final String STAGE_DISTURBED = "Disturbed";
    public static final String STAGE_AWAKENING = "Awakening";   // stage 4
    public static final String STAGE_SUMMONED = "Summoned";     // stage 5

    private static final String NAME = "End ?Stone Protector";

    private static final Pattern TAB_PROTECTOR = Pattern.compile("Protector:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPAWN = Pattern.compile(
            "The ground begins to shake as an " + NAME + " rises from below!", Pattern.CASE_INSENSITIVE);
    private static final Pattern RISEN = Pattern.compile(
            "BEWARE - An " + NAME + " has risen!", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOWN = Pattern.compile(NAME + " DOWN!", Pattern.CASE_INSENSITIVE);
    private static final Pattern TREMOR = Pattern.compile(
            "You feel a tremor from beneath the earth!", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMAGE = Pattern.compile(
            "Your Damage: ([\\d,]+) \\(Position #([\\d,]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_DAMAGER = Pattern.compile(
            "(1st|2nd|3rd) Damager - (?:.*\\] )?([A-Za-z0-9_]+) - ([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZEALOTS = Pattern.compile(
            "Zealots Contributed: ([\\d,]+)/100", Pattern.CASE_INSENSITIVE);

    // loot quality each drop needs
    private static final int LQ_TIER_BOOST_CORE = 250;
    private static final int LQ_LEGENDARY_PET = 235;
    private static final int LQ_EPIC_PET = 220;

    private record Spot(String name, BlockPos pos) {}

    private static final List<Spot> SPOTS = List.of(
            new Spot("Middle Front", new BlockPos(-644, 5, -267)),
            new Spot("Right Front", new BlockPos(-639, 5, -326)),
            new Spot("Right Behind", new BlockPos(-678, 5, -330)),
            new Spot("Left", new BlockPos(-649, 5, -217)),
            new Spot("Middle Behind", new BlockPos(-727, 5, -282)),
            new Spot("Middle Center", new BlockPos(-689, 5, -271)));

    private static final String[] STAGES = {
            STAGE_RESTING, STAGE_DORMANT, STAGE_AGITATED, STAGE_DISTURBED, STAGE_AWAKENING, STAGE_SUMMONED};

    // --- state ---
    static String stage = STAGE_RESTING;
    static boolean scanning = true;
    static boolean hasRisen = false;
    static long stage4StartMillis = 0;
    static long stage5TargetTicks = 0;

    static String locationName = "None";
    static BlockPos locationPos = null;

    private static long fightStartTicks = 0;
    private static long fightEndTicks = 0;
    private static long firstPlaceDamage = 0;
    private static int zealotKills = 0;

    private static String top1Name, top2Name, top3Name;
    private static long top1Damage, top2Damage, top3Damage;

    private static boolean lootScanning = false;
    private static boolean dropAlerted = false;
    private static int lootScanTicks = 0;
    private static final int MAX_LOOT_SCAN_TICKS = 1200;   // 60s

    private static int tabTicks = 0;
    private static int headTicks = 0;
    private static long lastRaiseMillis = 0;
    private static long downMillis = 0;
    private static Spot headSpot = null;

    public static boolean inArea() {
        return SkyblockUtils.isInArea(AREA);
    }

    public static void reset() {
        stage = STAGE_RESTING;
        day30Warned = false;
        scanning = true;
        hasRisen = false;
        stage4StartMillis = 0;
        stage5TargetTicks = 0;
        locationName = "None";
        locationPos = null;
        fightStartTicks = 0;
        fightEndTicks = 0;
        firstPlaceDamage = 0;
        zealotKills = 0;
        top1Name = top2Name = top3Name = null;
        top1Damage = top2Damage = top3Damage = 0;
        lootScanning = false;
        dropAlerted = false;
        lastRaiseMillis = 0;
        downMillis = 0;
        headSpot = null;
    }

    // --- chat ---

    public static void onChatMessage(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.endProtector) return;
        if (!inArea()) return;

        Minecraft mc = Minecraft.getInstance();
        String msg = StringUtil.strip(raw);

        if (SPAWN.matcher(msg).find()) {
            setSummoned(mc);
            return;
        }

        if (TREMOR.matcher(msg).find()) {
            int i = stageIndex(stage);
            if (scanning || i < 0) scanHeads(mc);
            else if (i < 4) raise(mc, i + 1);
            return;
        }

        if (RISEN.matcher(msg).find()) {
            hasRisen = true;
            if (mc.level != null) {
                fightStartTicks = ServerTicks.now(mc.level.getGameTime());
                fightEndTicks = 0;
                firstPlaceDamage = 0;
                zealotKills = 0;
            }
            return;
        }

        if (DOWN.matcher(msg).find()) {
            if (mc.level != null) {
                fightEndTicks = ServerTicks.now(mc.level.getGameTime());
                lootScanning = true;
                dropAlerted = false;
                lootScanTicks = 0;
            }
            updateStage(mc, STAGE_RESTING);
            headSpot = null;
            downMillis = System.currentTimeMillis();
            return;
        }

        Matcher top = TOP_DAMAGER.matcher(msg);
        if (top.find()) {
            try {
                String rank = top.group(1);
                String name = top.group(2);
                long damage = Long.parseLong(top.group(3).replace(",", ""));
                switch (rank) {
                    case "1st" -> { top1Name = name; top1Damage = damage; firstPlaceDamage = damage; }
                    case "2nd" -> { top2Name = name; top2Damage = damage; }
                    case "3rd" -> { top3Name = name; top3Damage = damage; }
                    default -> { }
                }
            } catch (NumberFormatException ignored) {}
            return;
        }

        Matcher zealot = ZEALOTS.matcher(msg);
        if (zealot.find()) {
            try {
                zealotKills = Integer.parseInt(zealot.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {}
            return;
        }

        Matcher dmg = DAMAGE.matcher(msg);
        if (dmg.find()) reportFight(mc, dmg);
    }

    private static void reportFight(Minecraft mc, Matcher m) {
        if (mc.level == null) return;
        long endTicks = fightEndTicks;
        long nowTicks = ServerTicks.now(mc.level.getGameTime());
        // the summary belongs to the kill that just happened, not to one 20s ago
        if (endTicks == 0 || (nowTicks - endTicks) > 400) return;
        fightEndTicks = 0;

        long myDamage;
        int myPosition;
        try {
            myDamage = Long.parseLong(m.group(1).replace(",", ""));
            myPosition = Integer.parseInt(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }

        double seconds = 0;
        String dps = null;
        String duration = null;
        if (fightStartTicks > 0 && endTicks > fightStartTicks) {
            seconds = (endTicks - fightStartTicks) / 20.0;
            if (seconds > 0) {
                dps = formatDps(myDamage / seconds);
                duration = String.format("%.1fs", seconds);
            }
        }

        String finalDps = dps;
        String finalDuration = duration;
        double finalSeconds = seconds;
        // the zealot line lands after the damage line
        TickScheduler.schedule(10, () ->
                printResult(mc, finalDps, finalDuration, finalSeconds, lootQuality(myDamage, myPosition)));
    }

    private static void printResult(Minecraft mc, String dps, String duration, double seconds, int lq) {
        if (mc.player == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();

        if (c.endProtectorDpsChat && dps != null && duration != null) {
            MutableComponent msg = Component.literal(
                    String.format("§6Your Protector DPS: §l§o%s §r§7(%s) ", dps, duration));
            if (seconds > 0 && top1Damage > 0) {
                MutableComponent hover = Component.literal("§6§lTop 3 DPS\n");
                hover.append(Component.literal(String.format("§e#1 §f%s §7- §6§l§o%s",
                        top1Name, formatDps(top1Damage / seconds))));
                if (top2Damage > 0) hover.append(Component.literal(String.format("\n§6#2 §f%s §7- §6§l§o%s",
                        top2Name, formatDps(top2Damage / seconds))));
                if (top3Damage > 0) hover.append(Component.literal(String.format("\n§c#3 §f%s §7- §6§l§o%s",
                        top3Name, formatDps(top3Damage / seconds))));
                msg.append(Component.literal("§6§l§o[HOVER]")
                        .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
            }
            mc.player.sendSystemMessage(msg);
        }

        if (c.endProtectorLootQualityChat) {
            String colour = qualityColour(lq, LQ_TIER_BOOST_CORE, LQ_LEGENDARY_PET, LQ_EPIC_PET);
            MutableComponent msg = Component.literal(
                    String.format("§6Your Protector Loot Quality: %s§l§o%d §r", colour, lq));
            MutableComponent hover = Component.literal("§6§lDrops\n");
            hover.append(Component.literal(dropLine("§6Tier Boost Core", lq, LQ_TIER_BOOST_CORE)));
            hover.append(Component.literal("\n" + dropLine("§7[Lvl 1] §6Golem", lq, LQ_LEGENDARY_PET)));
            hover.append(Component.literal("\n" + dropLine("§7[Lvl 1] §5Golem", lq, LQ_EPIC_PET)));
            msg.append(Component.literal("§6§l§o[HOVER]")
                    .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
            mc.player.sendSystemMessage(msg);
        }
    }

    private static int lootQuality(long myDamage, int myPosition) {
        int placement;
        if (myPosition == 1) placement = 200;
        else if (myPosition == 2) placement = 175;
        else if (myPosition == 3) placement = 150;
        else if (myPosition == 4) placement = 125;
        else if (myPosition == 5) placement = 110;
        else if (myPosition <= 8) placement = 100;
        else if (myPosition <= 10) placement = 90;
        else if (myPosition <= 12) placement = 80;
        else placement = myDamage >= 1 ? 70 : 10;

        long first = firstPlaceDamage;
        if (first == 0 && myPosition == 1) first = myDamage;
        double damageScore = first > 0 ? 50.0 * ((double) myDamage / first) : 0;

        return (int) (placement + damageScore + Math.min(zealotKills, 100));
    }

    private static String formatDps(double dps) {
        return dps >= 1000 ? String.format("%,.1fk", dps / 1000.0) : String.format("%,.1f", dps);
    }

    private static String qualityColour(int quality, int... required) {
        for (int need : required) if (quality < need) return "§c";
        return "§a";
    }

    private static String dropLine(String name, int quality, int required) {
        return String.format("%s §7- %s §8(%d)", name, quality >= required ? "§a✔" : "§c✘", required);
    }

    // --- stage machine ---

    private static void setSummoned(Minecraft mc) {
        scanning = false;
        if (mc.level != null) stage5TargetTicks = ServerTicks.now(mc.level.getGameTime()) + 400;
        updateStage(mc, STAGE_SUMMONED);
    }

    private static void updateStage(Minecraft mc, String newStage) {
        String old = stage;
        if (old.equals(newStage)) return;
        stage = newStage;
        TeslaMapsConfig c = TeslaMapsConfig.get();

        if (STAGE_AWAKENING.equals(newStage)) {
            day30Warned = false;
            stage4StartMillis = System.currentTimeMillis();
            if (c.endProtectorStage4Title) {
                mc.gui.setTitle(Component.literal("PROTECTOR STAGE 4")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                LoudSound.play(SoundEvents.IRON_GOLEM_HURT, 1.0f, 1.0f);
            }
            return;
        }

        if (STAGE_SUMMONED.equals(newStage)) {
            if (STAGE_AWAKENING.equals(old) && stage4StartMillis > 0 && c.endProtectorStage4Duration) {
                long secs = (System.currentTimeMillis() - stage4StartMillis) / 1000;
                TickScheduler.schedule(2, () -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal(
                                String.format("§aProtector Stage 4 Duration: %dm %ds", secs / 60, secs % 60)));
                    }
                });
            }
            stage4StartMillis = 0;
            if (stage5TargetTicks == 0 && mc.level != null) {
                stage5TargetTicks = ServerTicks.now(mc.level.getGameTime()) + 400;
            }
            if (c.endProtectorStage4Title) {
                mc.gui.setTitle(Component.literal("PROTECTOR STAGE 5")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                LoudSound.play(SoundEvents.ANVIL_LAND, 1.0f, 1.0f);
            }
            return;
        }

        stage4StartMillis = 0;
        hasRisen = false;
    }

    // --- tick ---

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.endProtector) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!inArea()) {
            if (!STAGE_RESTING.equals(stage) || locationPos != null) reset();
            return;
        }

        if (++tabTicks >= 10) {
            tabTicks = 0;
            readTabList(mc);
        }
        if (++headTicks >= 20) {
            headTicks = 0;
            scanHeads(mc);
        }

        scanLocation(mc);
        scanLoot(mc, c);
        checkDay30(mc, c);
    }

    private static boolean day30Warned = false;

    // GanKura: a lobby past day 30 tends to reboot before the golem spawns
    private static void checkDay30(Minecraft mc, TeslaMapsConfig c) {
        if (!c.endProtectorDay30Warning || day30Warned || !STAGE_AWAKENING.equals(stage)) return;
        long day = Math.max(mc.level.getDefaultClockTime(), mc.level.getOverworldClockTime()) / 24000L;
        if (day < 30) return;
        day30Warned = true;
        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] §cThis server might restart before the golem spawns (Day " + day + ")"));
    }

    private static void readTabList(Minecraft mc) {
        for (String raw : TabListUtils.getTabListLines()) {
            Matcher m = TAB_PROTECTOR.matcher(StringUtil.stripTrim(raw));
            if (!m.find()) continue;

            boolean wasScanning = scanning;
            scanning = false;
            String rawState = m.group(1).trim().split("\\s+")[0];
            String name = switch (rawState) {
                case "Resting" -> STAGE_RESTING;
                case "Dormant" -> STAGE_DORMANT;
                case "Agitated" -> STAGE_AGITATED;
                case "Disturbed" -> STAGE_DISTURBED;
                case "Awakening" -> STAGE_AWAKENING;
                default -> rawState;
            };
            // joined mid-fight: the 400 tick countdown is already over
            if (wasScanning && STAGE_SUMMONED.equals(name)) stage5TargetTicks = 1;
            // the tab widget lags one stage behind the spawn message
            if (STAGE_SUMMONED.equals(stage) && STAGE_AWAKENING.equals(name)) return;
            // and behind the head / tremor
            int tabIndex = stageIndex(name);
            if (tabIndex >= 0 && tabIndex < stageIndex(stage)
                    && System.currentTimeMillis() - lastRaiseMillis < 30_000) return;
            updateStage(mc, name);
            return;
        }
    }

    private static int stageIndex(String s) {
        for (int i = 0; i < STAGES.length; i++) if (STAGES[i].equals(s)) return i;
        return -1;
    }

    private static void raise(Minecraft mc, int index) {
        lastRaiseMillis = System.currentTimeMillis();
        // joined mid-countdown: the 400 ticks are unknown
        if (index == 5 && scanning) stage5TargetTicks = 1;
        scanning = false;
        updateStage(mc, STAGES[index]);
    }

    /** Ported from Skyblocker's TheEnd.isProtectorHere: the head climbs one block per stage. */
    private static void scanHeads(Minecraft mc) {
        if (mc.level == null || hasRisen) return;
        if (System.currentTimeMillis() - downMillis < 30_000) return;
        for (Spot spot : SPOTS) {
            int x = spot.pos().getX(), z = spot.pos().getZ() - 2;
            if (!mc.level.hasChunk(x >> 4, z >> 4)) continue;
            for (int i = 0; i < 5; i++) {
                var state = mc.level.getBlockState(new BlockPos(x, 5 + i, z));
                if (!state.is(Blocks.PLAYER_HEAD) && !state.is(Blocks.PLAYER_WALL_HEAD)) continue;
                headSpot = spot;
                int index = i + 1;
                if (scanning || index > stageIndex(stage)) raise(mc, index);
                return;
            }
        }
    }

    /** The spawn site is the one whose altar block carries sandstone stairs. */
    private static void scanLocation(Minecraft mc) {
        boolean stage4 = STAGE_AWAKENING.equals(stage);
        boolean stage5 = STAGE_SUMMONED.equals(stage);

        if (!stage4 && !stage5) {
            if (!"None".equals(locationName)) {
                locationName = "None";
                locationPos = null;
            }
            return;
        }
        if (!"None".equals(locationName)) return;

        int yOffset = stage5 ? 1 : 0;
        if (headSpot != null) {
            locationName = headSpot.name();
            locationPos = headSpot.pos().above(yOffset);
            return;
        }
        for (Spot spot : SPOTS) {
            BlockPos pos = spot.pos().above(yOffset);
            if (mc.level.getBlockState(pos).is(Blocks.SANDSTONE_STAIRS)) {
                locationName = spot.name();
                locationPos = pos;
                return;
            }
        }
    }

    // --- rare drops ---

    private static void scanLoot(Minecraft mc, TeslaMapsConfig c) {
        if (!lootScanning) {
            lootScanTicks = 0;
            return;
        }
        if (++lootScanTicks > MAX_LOOT_SCAN_TICKS) {
            lootScanning = false;
            lootScanTicks = 0;
            return;
        }
        if (dropAlerted) return;

        AABB box = mc.player.getBoundingBox().inflate(30.0);
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, box, e -> true)) {
            if (!stand.hasCustomName()) continue;
            Component name = stand.getCustomName();
            if (name == null) continue;
            String text = name.getString();

            if (text.contains("Tier Boost Core") && hasColor(name, ChatFormatting.GOLD)) {
                c.protectorTierBoostCores++;
                notifyDrop(mc, c, Component.literal("Tier Boost Core").withStyle(ChatFormatting.GOLD),
                        c.protectorTierBoostCores, c.endProtectorIgnoreTbc);
                return;
            }

            if (text.contains("[Lvl 1] Golem")) {
                if (hasColor(name, ChatFormatting.GOLD)) {
                    c.protectorLegendaryPets++;
                    notifyDrop(mc, c, pet(ChatFormatting.GOLD), c.protectorLegendaryPets, c.endProtectorIgnoreLegPet);
                    return;
                }
                if (hasColor(name, ChatFormatting.DARK_PURPLE)) {
                    c.protectorEpicPets++;
                    notifyDrop(mc, c, pet(ChatFormatting.DARK_PURPLE), c.protectorEpicPets, c.endProtectorIgnoreEpicPet);
                    return;
                }
            }
        }
    }

    private static MutableComponent pet(ChatFormatting rarity) {
        return Component.literal("[Lvl 1] ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Golem").withStyle(rarity));
    }

    private static void notifyDrop(Minecraft mc, TeslaMapsConfig c, Component item, int count, boolean ignored) {
        dropAlerted = true;
        lootScanning = false;
        TeslaMapsConfig.save();

        if (!c.endProtectorDropAlert || ignored) return;

        mc.gui.setTitle(Component.literal("DROP!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.empty().append(item)
                .append(Component.literal(" x" + count).withStyle(ChatFormatting.GRAY)));

        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.empty()
                    .append(mc.player.getDisplayName())
                    .append(Component.literal(" has obtained ").withStyle(ChatFormatting.YELLOW))
                    .append(item)
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        }
        LoudSound.play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.5f);
    }

    private static boolean hasColor(Component text, ChatFormatting target) {
        TextColor want = TextColor.fromLegacyFormat(target);
        if (want == null) return false;
        if (want.equals(text.getStyle().getColor())) return true;
        for (Component sibling : text.getSiblings()) if (hasColor(sibling, target)) return true;
        return false;
    }

    // --- world render ---

    /** Block the beacon and the world label sit on, plus its colour, or null outside stage 4/5. */
    static BlockPos anchor() {
        if (locationPos == null || "None".equals(locationName)) return null;
        if (STAGE_AWAKENING.equals(stage)) return locationPos.offset(0, 1, -2);
        if (STAGE_SUMMONED.equals(stage)) return locationPos.offset(0, 0, -2);
        return null;
    }

    static int anchorColor() {
        return STAGE_AWAKENING.equals(stage) ? 0xFFFFFFFF : 0xFFFF5555;
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.endProtector) return;
        if (!c.endProtectorBeacon && !c.endProtectorWorldText && !c.endProtectorTracer) return;
        if (!inArea()) return;

        BlockPos pos = anchor();
        if (pos == null) return;
        int color = anchorColor();

        if (c.endProtectorBeacon) ESPRenderer.drawBeaconBeam(matrices, pos, color, cameraPos);
        if (c.endProtectorTracer) ESPRenderer.drawTracerFromCamera(matrices,
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), color, cameraPos);

        if (c.endProtectorWorldText) {
            Vec3 label = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
            com.teslamaps.features.waypoints.WaypointSet.drawLabel(matrices, cameraPos, worldLabel(), label, 1.0f);
        }
    }

    private static String worldLabel() {
        if (STAGE_AWAKENING.equals(stage)) {
            String text = "§f§lPROTECTOR";
            if (stage4StartMillis > 0) {
                long secs = (System.currentTimeMillis() - stage4StartMillis) / 1000;
                text += String.format(" %s(%dm %ds)", stageColour(secs), secs / 60, secs % 60);
            }
            return text;
        }
        double remaining = Math.max(0, stage5TargetTicks - ServerTicks.now());
        if (remaining > 0) return String.format("§c§lPROTECTOR §c(%.1fs)", remaining / 20.0);
        return (!hasRisen && !"None".equals(locationName))
                ? "§c§lPROTECTOR §e(Soon)" : "§c§lPROTECTOR §c(Spawned)";
    }

    static String stageColour(long seconds) {
        return seconds >= 480 ? "§c" : seconds >= 240 ? "§e" : "§f";
    }
}
