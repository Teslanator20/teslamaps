package com.teslamaps.features.theend;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.HudVisibility;
import com.teslamaps.utils.ServerTicks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Stage, stage timer and spawn location of the End Stone Protector. */
public class ProtectorHud {

    private static final String[] SAMPLE = {
            "§6§lProtector Status", "Stage: §e4 §f(2m 30s)", "Location: §fMiddle Front"
    };

    private static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("§6§lProtector Status");
        out.add("Stage: " + stageText());
        String loc = locationText();
        if (loc != null) out.add(loc);
        return out;
    }

    private static String stageText() {
        if (EndProtector.scanning) return "§8Scanning...";

        if (EndProtector.STAGE_SUMMONED.equals(EndProtector.stage)) {
            double remaining = Math.max(0, EndProtector.stage5TargetTicks - ServerTicks.now());
            if (remaining > 0) return String.format("§c5 (%.1fs)", remaining / 20.0);
            return (!EndProtector.hasRisen && !"None".equals(EndProtector.locationName))
                    ? "§c5 §e(Soon)" : "§c5 §c(Spawned)";
        }

        String num = switch (EndProtector.stage) {
            case EndProtector.STAGE_RESTING -> "§70";
            case EndProtector.STAGE_DORMANT -> "§71";
            case EndProtector.STAGE_AGITATED -> "§72";
            case EndProtector.STAGE_DISTURBED -> "§73";
            case EndProtector.STAGE_AWAKENING -> "§e4";
            default -> "§f?";
        };
        if (EndProtector.STAGE_AWAKENING.equals(EndProtector.stage) && EndProtector.stage4StartMillis > 0) {
            long secs = (System.currentTimeMillis() - EndProtector.stage4StartMillis) / 1000;
            num += String.format(" %s(%dm %ds)", EndProtector.stageColour(secs), secs / 60, secs % 60);
        }
        return num;
    }

    private static String locationText() {
        boolean stage4 = EndProtector.STAGE_AWAKENING.equals(EndProtector.stage);
        boolean stage5 = EndProtector.STAGE_SUMMONED.equals(EndProtector.stage);
        if (!stage4 && !stage5) return null;
        if ("None".equals(EndProtector.locationName)) return "Location: §8Scanning...";
        return (stage5 ? "§cLocation: " : "Location: §f") + EndProtector.locationName;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.endProtector || !c.endProtectorStatusHud) return;
        if (HudVisibility.suppressed()) return;
        if (!EndProtector.inArea()) return;

        draw(context, Minecraft.getInstance(), c.protectorStatusX, c.protectorStatusY,
                c.protectorStatusScale, lines());
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, EndProtector.inArea() ? lines() : List.of(SAMPLE));
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale,
                             List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    public static int[] previewSize(Font font) {
        List<String> lines = EndProtector.inArea() ? lines() : List.of(SAMPLE);
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
