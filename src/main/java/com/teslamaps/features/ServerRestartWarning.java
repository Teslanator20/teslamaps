package com.teslamaps.features;

// Chat trigger + sound from GanKura's ServerRestartHandler, countdown title from SkyHanni's ServerRestartTitle
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class ServerRestartWarning {

    private static final String SAMPLE = "§cServer closing in §f3:11";
    private static final String KEY = "Server closing: ";

    private static int seconds = -1;
    private static int lastTitle = -1;
    private static String line = null;
    private static int timer;
    private static Object lastLevel;
    private static HudEditElement hud;

    private ServerRestartWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("serverRestartHud", "Server Restart")
                .enabled(() -> cfg().serverRestartWarning && cfg().serverRestartHud)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("ServerRestartWarning").when(() -> cfg().serverRestartWarning)
                .tick(ServerRestartWarning::tick)
                .serverChat(ServerRestartWarning::onChat)
                .hud("serverrestart", ServerRestartWarning::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Server Restart");
                    b.toggle("Server Restart Warning", () -> c.serverRestartWarning, v -> c.serverRestartWarning = v)
                            .desc("Warns when Hypixel announces a server restart and counts down the \"Server closing\" scoreboard line.");
                    b.toggle("  Sound On Announcement", () -> c.serverRestartSound, v -> c.serverRestartSound = v);
                    b.toggle("  Countdown Title", () -> c.serverRestartTitle, v -> c.serverRestartTitle = v);
                    b.toggle("  Countdown HUD", () -> c.serverRestartHud, v -> c.serverRestartHud = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("[Important] This server will restart soon")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (cfg().serverRestartSound) LoudSound.play(SoundEvents.IRON_GOLEM_DEATH, 1.0f, 1.0f);
            if (cfg().serverRestartTitle) title(mc, "§c§lSERVER RESTART SOON");
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seconds = -1;
            lastTitle = -1;
            line = null;
        }
        if (++timer % 5 != 0) return;
        int found = -1;
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            int i = raw.indexOf(KEY);
            if (i < 0) continue;
            found = parse(ScoreboardUtils.cleanLine(raw.substring(i + KEY.length())));
            break;
        }
        seconds = found;
        if (found < 0) {
            line = null;
            lastTitle = -1;
            return;
        }
        String mmss = found / 60 + ":" + (found % 60 < 10 ? "0" : "") + found % 60;
        line = "§cServer closing in §f" + mmss;
        if (cfg().serverRestartTitle && found != lastTitle
                && (lastTitle < 0 || found <= 10 || (found % 30 == 0 && found <= 120))) {
            lastTitle = found;
            title(mc, "§cServer Restart in §b" + mmss);
        }
    }

    private static int parse(String s) {
        int colon = s.indexOf(':');
        if (colon <= 0) return -1;
        int end = colon + 1;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        try {
            return Integer.parseInt(s.substring(0, colon).trim()) * 60 + Integer.parseInt(s.substring(colon + 1, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void title(Minecraft mc, String text) {
        mc.gui.setTimes(0, 30, 5);
        mc.gui.setTitle(Component.literal(text));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null || !cfg().serverRestartHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
