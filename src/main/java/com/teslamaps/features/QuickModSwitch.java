package com.teslamaps.features;

// After SkyHanni's QuickModMenuSwitch
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Clickable list of the other installed mods' config menus, shown inside config screens. */
public final class QuickModSwitch {

    private record Mod(String name, String modId, String command, String[] screens) {}

    private static final Mod[] KNOWN = {
            new Mod("TeslaMaps", "teslamaps", "tmap", new String[]{"com.teslamaps.screen.MapConfigScreen"}),
            new Mod("Skyblocker", "skyblocker", "skyblocker config", new String[]{"de.hysky.skyblocker.config", "dev.isxander.yacl3.gui"}),
            new Mod("SkyHanni", "skyhanni", "sh", new String[]{"at.hannibal2.skyhanni.config", "io.github.notenoughupdates.moulconfig"}),
            new Mod("Devonian", "devonian", "devonian", new String[]{"com.github.synnerz.devonian"}),
            new Mod("Odin", "odin", "odin", new String[]{"com.odtheking.odin.clickgui"}),
            new Mod("SkyOcean", "skyocean", "skyocean", new String[]{"me.owdding.skyocean.config", "com.teamresourceful.resourcefulconfig"}),
            new Mod("Firmament", "firmament", "firm config", new String[]{"moe.nea.firmament.gui.config"}),
    };
    private static final int ROW_H = 11;

    private static List<Mod> installed;
    private static Screen shownOn;
    private static Mod current;
    private static long lastClick;
    private static HudEditElement hud;

    private QuickModSwitch() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static List<Mod> installed() {
        if (installed == null) {
            List<Mod> out = new ArrayList<>();
            for (Mod m : KNOWN) if (FabricLoader.getInstance().isModLoaded(m.modId)) out.add(m);
            installed = out;
        }
        return installed;
    }

    public static void register() {
        hud = HudEditElement.of("quickModSwitch", "Quick Mod Menu Switch")
                .enabled(() -> cfg().quickModSwitch)
                .size(QuickModSwitch::width, () -> installed().size() * ROW_H + 4)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, -1, -1));
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!cfg().quickModSwitch) return;
            Mod m = match(screen);
            boolean pause = cfg().quickModSwitchPause && screen instanceof PauseScreen;
            if (m == null && !pause) return;
            if (installed().size() < 2) return;
            shownOn = screen;
            current = m;
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
                if (cfg().quickModSwitch && s == shownOn) draw(ctx, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (!cfg().quickModSwitch || s != shownOn || event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return true;
                Mod hit = at(event.x(), event.y());
                if (hit == null) return true;
                open(hit);
                return false;
            });
        });
    }

    /** For screens that poll the mouse themselves: a click on the list must not reach them. */
    public static boolean covers(Screen screen, int mx, int my) {
        return cfg().quickModSwitch && screen == shownOn && hud != null && at(mx, my) != null;
    }

    private static Mod match(Screen screen) {
        String cls = screen.getClass().getName();
        for (Mod m : installed()) for (String p : m.screens) if (cls.startsWith(p)) return m;
        return null;
    }

    private static int width() {
        var font = Minecraft.getInstance().font;
        int w = 40;
        for (Mod m : installed()) w = Math.max(w, font.width(m.name));
        return w + 8;
    }

    private static Mod at(double mx, double my) {
        float s = hud.scale();
        double lx = (mx - hud.x()) / s, ly = (my - hud.y()) / s - 2;
        if (lx < 0 || lx > width() || ly < 0) return null;
        int i = (int) (ly / ROW_H);
        List<Mod> list = installed();
        return i < list.size() ? list.get(i) : null;
    }

    private static void open(Mod m) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 250 || m == current) return;
        lastClick = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        mc.getConnection().sendCommand(m.command);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, int mx, int my) {
        List<Mod> list = installed();
        if (list.isEmpty()) return;
        Mod hover = mx < 0 ? null : at(mx, my);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(), list.size() * ROW_H + 4);
        for (int i = 0; i < list.size(); i++) {
            Mod m = list.get(i);
            int color = m == current ? 0xFFFF5555 : m == hover ? 0xFFFFFF55 : 0xFFFFFFFF;
            g.text(mc.font, m.name, 4, 3 + i * ROW_H, color, true);
        }
        pose.popMatrix();
    }
}
