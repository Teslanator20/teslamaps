package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.Calculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Ported from Skyblocker's SignCalculator + AbstractSignEditScreenMixin
public class SignCalculator {
    private static String lastInput = null;
    private static Double output = null;
    private static String display = "";

    public static boolean isInputSign(String[] m) {
        if (m == null || m.length < 4) return false;
        if (m[1].equals("^^^^^^^^^^^^^^^")) return !(m[2].endsWith("your") || m[2].endsWith("query"));
        if (m[1].equals("^^^^^^")) return !m[2].endsWith("your") && !m[2].equals("Enter name");
        return m[1].equals("^^Flipping^^");
    }

    private static boolean active(String[] m) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.searchbar && c.signCalculator && isInputSign(m);
    }

    private static String expr(String input) {
        if (input.startsWith("=")) return input.substring(1);
        return TeslaMapsConfig.get().signCalculatorEquals ? null : input;
    }

    private static void update(String input) {
        if (input.equals(lastInput)) return;
        lastInput = input;
        String e = expr(input);
        output = e == null || e.isBlank() ? null : Calculator.eval(e);
        if (e == null || e.isBlank()) display = "";
        else if (output == null) display = "§cInvalid equation";
        else display = "§a" + e + " = " + Calculator.format(output);
    }

    public static void render(GuiGraphicsExtractor g, String[] m) {
        if (!active(m)) return;
        update(m[0]);
        if (display.isEmpty()) return;
        g.centeredText(Minecraft.getInstance().font, display, g.guiWidth() / 2, 55, 0xFFFFFFFF);
    }

    public static void onDone(String[] m) {
        if (!active(m)) return;
        lastInput = null;
        update(m[0]);
        lastInput = null;
        if (output == null) return;
        boolean price = m[2].contains("price");
        String value = price ? String.valueOf(Math.round(output * 100d) / 100d) : Long.toString(Math.round(output));
        if (value.length() >= 15) value = value.substring(0, 15);
        m[0] = value;
    }
}
