package com.teslamaps.features.hud;

// Ported from SkyHanni's features/misc/CustomTextBox.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.screen.TextFieldsScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

public final class CustomTextBox {

    private static HudEditElement hud;
    private static String parsedFrom;
    private static List<String> lines = List.of();
    private static int width;

    private CustomTextBox() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("customTextBox", "Custom Text Box")
                .enabled(() -> cfg().customTextBox)
                .size(() -> { parse(); return Math.max(20, width); }, () -> { parse(); return TextHud.height(Math.max(1, lines.size())); })
                .preview((g, mc, x, y, s) -> { parse(); TextHud.draw(g, mc, x, y, s, lines, width); });
        Feature.of("CustomTextBox").when(() -> cfg().customTextBox)
                .hudForeground("customtextbox", CustomTextBox::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Text Box");
                    b.toggle("Custom Text Box", () -> c.customTextBox, v -> c.customTextBox = v)
                            .desc("Your own text on screen. & for color codes, \\n for a new line. Position in /tmap gui.");
                    b.button("  Edit Text", () -> Minecraft.getInstance().setScreen(new TextFieldsScreen(
                            Minecraft.getInstance().screen, "Custom Text Box", "&a for colors, \\n for a new line",
                            List.of(new TextFieldsScreen.Field("Text", () -> c.customTextBoxText, v -> c.customTextBoxText = v)),
                            1000)));
                    b.toggle("  Only In Chest Menus", () -> c.customTextBoxOnlyInGui, v -> c.customTextBoxOnlyInGui = v);
                });
    }

    private static void parse() {
        String t = cfg().customTextBoxText;
        if (t == null) t = "";
        if (t.equals(parsedFrom)) return;
        parsedFrom = t;
        lines = List.of(t.replace("&", "§").split("\\\\n"));
        width = TextHud.width(lines);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        boolean chest = mc.screen instanceof AbstractContainerScreen<?>;
        if (cfg().customTextBoxOnlyInGui ? !chest : mc.screen != null) return;
        parse();
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
