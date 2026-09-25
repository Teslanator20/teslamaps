package com.teslamaps.render.gui;

// Ported from Skyblocker's CaxtonCompatibility / ModernUICompatibility
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Outlined text that also looks right under Caxton / ModernUI font renderers. */
public final class OutlinedText {

    private static final boolean CAXTON = FabricLoader.getInstance().isModLoaded("caxton");
    private static final boolean MODERNUI = FabricLoader.getInstance().isModLoaded("modernui");
    private static MethodHandle caxton;
    private static boolean caxtonTried, caxtonFailed;
    private static Boolean modernUiEngine;
    // ModernTextRenderer.sOutlineOffset default
    private static final float[][] OFFSETS = {{.5f, 0}, {.5f, .5f}, {.5f, -.5f}, {-.5f, 0}, {-.5f, .5f}, {-.5f, -.5f}, {0, .5f}, {0, -.5f}};

    private OutlinedText() {}

    /** True when a font mod is present and the compat toggle is on. */
    public static boolean active() {
        return (CAXTON || MODERNUI) && TeslaMapsConfig.get().outlinedTextCompat;
    }

    /** Draws via the font mod; false = caller draws its own outline. */
    public static boolean draw(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, int outline) {
        if (!active()) return false;
        if (CAXTON && drawCaxton(g, font, Component.literal(s).getVisualOrderText(), x, y, color, outline)) return true;
        if (MODERNUI && modernUi()) {
            FormattedCharSequence outlineText = Component.literal(com.teslamaps.utils.StringUtil.strip(s)).getVisualOrderText();
            Matrix3x2fStack pose = g.pose();
            for (float[] d : OFFSETS) {
                pose.pushMatrix();
                pose.translate(d[0], d[1]);
                g.text(font, outlineText, x, y, outline, false);
                pose.popMatrix();
            }
            g.text(font, s, x, y, color, false);
            return true;
        }
        return false;
    }

    private static boolean drawCaxton(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color, int outline) {
        if (!caxtonTried) {
            caxtonTried = true;
            try {
                Class<?> c = Class.forName("xyz.flirora.caxton.render.Voepfxo");
                caxton = MethodHandles.publicLookup().findStatic(c, "drawText4Way", MethodType.methodType(void.class,
                        GuiGraphicsExtractor.class, Font.class, FormattedCharSequence.class, float.class, float.class, int.class, int.class));
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Caxton compat: drawText4Way not found", t);
            }
        }
        if (caxton == null || caxtonFailed) return false;
        try {
            caxton.invoke(g, font, text, (float) x, (float) y, color, outline);
            return true;
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Caxton compat: drawText4Way failed", t);
            caxtonFailed = true;
            return false;
        }
    }

    private static boolean modernUi() {
        if (modernUiEngine == null) {
            try {
                Class<?> c = Class.forName("icyllis.modernui.mc.ModernUIMod");
                MethodHandle h = MethodHandles.publicLookup().findStatic(c, "isTextEngineEnabled", MethodType.methodType(boolean.class));
                modernUiEngine = (boolean) h.invoke();
            } catch (Throwable t) {
                modernUiEngine = true;
            }
        }
        return modernUiEngine;
    }
}
