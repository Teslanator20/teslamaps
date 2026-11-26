package com.teslamaps.debug;

import com.teslamaps.TeslaMaps;
import com.teslamaps.registry.Feature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dev client only: runs the lines of run/autopilot.txt (then deletes it), so the client can be driven
 * without window focus. cmd &lt;command&gt; | click x y [button] | scroll x y amount | type text | esc | shot name
 */
public final class DevAutopilot {

    private DevAutopilot() {}

    private static int ticks;

    public static void register() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        Feature.of("DevAutopilot").tickAlways(DevAutopilot::tick);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Path file = FabricLoader.getInstance().getGameDir().resolve("autopilot.txt");
        if (!Files.exists(file)) return;
        try {
            List<String> lines = Files.readAllLines(file);
            Files.delete(file);
            for (String l : lines) run(l.trim());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Autopilot] {}", e.toString());
        }
    }

    private static void run(String line) {
        if (line.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        String[] a = line.split(" ", 2);
        String rest = a.length > 1 ? a[1] : "";
        Screen s = mc.gui.screen();
        TeslaMaps.LOGGER.info("[Autopilot] {} (screen {})", line, s == null ? "none" : s.getClass().getSimpleName());
        switch (a[0]) {
            case "cmd" -> { if (mc.getConnection() != null) mc.getConnection().sendCommand(rest); }
            case "click" -> {
                String[] p = rest.split(" ");
                if (s == null) return;
                double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]);
                int b = p.length > 2 ? Integer.parseInt(p[2]) : 1;
                MouseButtonEvent ev = new MouseButtonEvent(x, y, new MouseButtonInfo(b, 0));
                s.mouseClicked(ev, false);
                s.mouseReleased(ev);
            }
            case "scroll" -> {
                String[] p = rest.split(" ");
                if (s != null) s.mouseScrolled(Double.parseDouble(p[0]), Double.parseDouble(p[1]), 0, Double.parseDouble(p[2]));
            }
            case "type" -> { if (s != null) for (int c : rest.codePoints().toArray()) s.charTyped(new CharacterEvent(c)); }
            case "esc" -> { if (s != null) s.onClose(); }
            case "info" -> TeslaMaps.LOGGER.info("[Autopilot] guiScale={} screen={}x{}", mc.getWindow().getGuiScale(),
                    s == null ? 0 : s.width, s == null ? 0 : s.height);
            case "set" -> {
                String[] p = rest.split(" ", 2);
                try {
                    var f = com.teslamaps.config.TeslaMapsConfig.class.getField(p[0]);
                    var c = com.teslamaps.config.TeslaMapsConfig.get();
                    Class<?> t = f.getType();
                    if (t == boolean.class) f.setBoolean(c, Boolean.parseBoolean(p[1]));
                    else if (t == int.class) f.setInt(c, Integer.parseInt(p[1]));
                    else if (t == float.class) f.setFloat(c, Float.parseFloat(p[1]));
                    else f.set(c, p[1]);
                } catch (ReflectiveOperationException e) {
                    TeslaMaps.LOGGER.warn("[Autopilot] set: {}", e.toString());
                }
            }
            case "shot" -> Screenshot.grab(mc.gameDirectory, (rest.isEmpty() ? "autopilot" : rest) + ".png",
                    mc.gameRenderer.mainRenderTarget(), 1, msg -> TeslaMaps.LOGGER.info("[Autopilot] shot {}", msg.getString()));
            default -> TeslaMaps.LOGGER.warn("[Autopilot] unknown: {}", line);
        }
    }
}
