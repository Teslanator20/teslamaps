package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/FlowstateHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.HudVisibility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** Flowstate streak: time until it breaks, blocks x/200, mining speed bonus, personal best. */
public final class FlowstateHelper {

    private static final long STREAK_MS = 10_000L;
    private static final List<String> SAMPLE = List.of("§d§lFlowstate Helper", "§7Time Remaining: §a9.7s",
            "§7Streak: §e123§8/200", "§6+369⸕");

    private static HudEditElement hud;
    private static Level lastLevel;
    private static int level, streak, ticks;
    private static long streakEnd, lastOriginalAt;
    private static BlockPos lastOriginal;
    private static List<String> lines = List.of();
    private static int width;

    private FlowstateHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("flowstateHelper", "Flowstate Helper").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().flowstateHelper)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines),
                        () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("FlowstateHelper").section("Mining").when(() -> cfg().flowstateHelper)
                .tick(FlowstateHelper::tick)
                .hud("flowstatehelper", FlowstateHelper::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Flowstate Helper");
                    b.toggle("Flowstate Helper", () -> c.flowstateHelper, v -> c.flowstateHelper = v)
                            .desc("While holding a Flowstate tool: time until the streak breaks, streak x/200, mining speed bonus and your best streak. Position in /tmap gui.")
                            .tags("flowstate", "streak", "mining speed", "enchant");
                    b.toggle("  Compact (One Line)", () -> c.flowstateCompact, v -> c.flowstateCompact = v);
                    b.toggle("  Colored Timer", () -> c.flowstateColorTimer, v -> c.flowstateColorTimer = v);
                    b.slider("  Auto Hide After (s, -1 = never)", -1, 10, () -> (float) c.flowstateAutoHide,
                            v -> c.flowstateAutoHide = Math.round(v), 0);
                    b.toggle("  Personal Best Message", () -> c.flowstatePbMessage, v -> c.flowstatePbMessage = v)
                            .desc("Chat line when a streak beats your best (only once the best is above 200)");
                });
    }

    public static boolean tracking() {
        return level > 0 && cfg().flowstateHelper && cfg().section("Mining") && Mining.onMiningIsland();
    }

    /** Block update, main thread. The crosshair block plus whatever breaks right after it. */
    public static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState || oldState.isAir()) return;
        Block nb = newState.getBlock();
        if (nb != Blocks.AIR && nb != Blocks.BEDROCK) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || pos.distToCenterSqr(mc.player.position()) > 49) return;
        long now = System.currentTimeMillis();
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(pos)) {
            lastOriginal = pos.immutable();
            lastOriginalAt = now;
        } else if (lastOriginal == null || now - lastOriginalAt > 300 || pos.equals(lastOriginal)) {
            return;
        }
        streak++;
        streakEnd = now + STREAK_MS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            streakEnd = 0;
            endStreak();
        }
        if (++ticks % 10 == 0) level = flowstate(mc.player.getMainHandItem());
        long now = System.currentTimeMillis();
        if (streak > 0 && now > streakEnd) endStreak();
        if (!Mining.onMiningIsland() || (level == 0 && streak == 0)) {
            lines = List.of();
            return;
        }
        int hide = cfg().flowstateAutoHide;
        if (hide >= 0 && now - (streakEnd - STREAK_MS) > hide * 1000L) {
            lines = List.of();
            return;
        }
        build(now);
    }

    private static void endStreak() {
        TeslaMapsConfig c = cfg();
        if (streak > c.flowstateBest) {
            Minecraft mc = Minecraft.getInstance();
            if (c.flowstateBest > 200 && c.flowstatePbMessage && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §d§lNEW FLOWSTATE PERSONAL BEST! §fStreak: "
                        + streak + ". You beat your old best by " + (streak - c.flowstateBest) + " blocks!"));
            }
            c.flowstateBest = streak;
            TeslaMapsConfig.save();
        }
        streak = 0;
    }

    private static void build(long now) {
        long left = Math.max(0, streakEnd - now);
        String timer = timerColor(left) + (left / 1000) + "." + (left % 1000 / 100) + "s";
        String sc = streak < 200 ? "§e" : "§a";
        int bonus = Math.min(streak, 200) * Math.max(level, 1);
        List<String> out = new ArrayList<>(5);
        if (cfg().flowstateCompact) {
            out.add("§7x" + sc + streak + " §6+" + bonus + "⸕ " + timer);
        } else {
            out.add("§d§lFlowstate Helper");
            out.add("§7Time Remaining: " + timer);
            out.add("§7Streak: " + sc + streak + (streak < 200 ? "§8/200" : ""));
            out.add("§6+" + bonus + "⸕");
            int best = cfg().flowstateBest;
            out.add(streak <= best ? "§7Personal Best: " + sc + streak + "§8/§d" + best : "§d§lNew Personal Best " + sc + streak);
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String timerColor(long left) {
        if (!cfg().flowstateColorTimer) return "§b";
        if (left > 8000) return "§2";
        if (left > 6000) return "§a";
        if (left > 4000) return "§e";
        if (left > 2000) return "§6";
        return "§c";
    }

    private static int flowstate(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return 0;
        return d.copyTag().getCompoundOrEmpty("enchantments").getIntOr("ultimate_flowstate", 0);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (lines.isEmpty() || hud == null || HudVisibility.suppressed()) return;
        TextHud.draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
