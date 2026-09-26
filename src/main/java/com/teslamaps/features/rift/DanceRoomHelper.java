package com.teslamaps.features.rift;

// Ported from SkyHanni's rift/area/mirrorverse/DanceRoomHelper.kt (instructions: SkyHanni-REPO DanceRoomInstructions.json)
import com.google.gson.JsonElement;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.RepoCache;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class DanceRoomHelper {

    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/DanceRoomInstructions.json";
    private static final AABB ROOM = new AABB(-267, 32, -110, -260, 40, -102);
    private static final List<String> SAMPLE = List.of("§7Now: §eMove §f0:850", "§7Next: §5Sneak", "§7Later: §eStand", "§7Later: §5Sneak §bJump");

    private static HudEditElement hud;
    private static volatile List<String> instructions = List.of();
    private static boolean requested, inRoom;
    private static int index, countdown, ticks, width;
    private static List<String> lines = List.of();
    private static Object lastLevel;

    private DanceRoomHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        hud = HudEditElement.of("riftDance", "Dance Room Helper").section("Rift")
                .enabled(() -> cfg().riftDanceRoom)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("DanceRoomHelper").section("Rift").when(() -> cfg().riftDanceRoom)
                .tick(DanceRoomHelper::tick)
                .hud("riftdance", DanceRoomHelper::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Dance Room Helper", () -> c.riftDanceRoom, v -> c.riftDanceRoom = v)
                            .desc("Mirrorverse Dance Room: the moves as Now / Next / Later with a countdown, synced by the success and fail sounds (/tmap gui).");
                    b.slider("  Lines to Show", 1f, 10f, () -> (float) c.riftDanceLines, v -> c.riftDanceLines = Math.round(v));
                    b.toggle("  Hide Original Title", () -> c.riftDanceHideTitle, v -> c.riftDanceHideTitle = v);
                    b.toggle("  Hide Other Players", () -> c.riftDanceHidePlayers, v -> c.riftDanceHidePlayers = v);
                });
    }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return inRoom && c.riftDanceRoom && c.section("Rift");
    }

    public static boolean listening() {
        return on();
    }

    /** From SetTitleText, main thread. */
    public static boolean hideTitle() {
        return on() && cfg().riftDanceHideTitle;
    }

    public static boolean hidePlayers() {
        return on() && cfg().riftDanceHidePlayers;
    }

    private static void load() {
        requested = true;
        Thread t = new Thread(() -> {
            try {
                List<String> out = new ArrayList<>();
                for (JsonElement e : RepoCache.fetch("dance_room", URL, 15).getAsJsonObject().getAsJsonArray("instructions")) out.add(e.getAsString());
                instructions = List.copyOf(out);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[DanceRoom] repo load failed: {}", e.toString());
            }
        }, "TeslaMaps-DanceRoom");
        t.setDaemon(true);
        t.start();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inRoom = false;
            index = 0;
            countdown = 0;
        }
        if (ticks++ % 10 == 0) inRoom = RiftZone.inMirrorverse() && mc.player != null && ROOM.contains(mc.player.position());
        if (!inRoom) {
            index = 0;
            countdown = 0;
            lines = List.of();
            return;
        }
        if (!requested) load();
        if (countdown > 0) countdown--;
        build();
    }

    private static void build() {
        List<String> ins = instructions;
        if (ins.isEmpty()) {
            lines = List.of("§cDance Room instructions not loaded");
            width = TextHud.width(lines);
            return;
        }
        int later = Math.max(1, cfg().riftDanceLines);
        List<String> out = new ArrayList<>(later + 2);
        for (int i = index; i < ins.size() && i <= index + later; i++) {
            String move = format(ins.get(i));
            if (i == index) {
                String cd = "";
                if (countdown > 0) {
                    int ms = countdown * 50;
                    cd = " §f" + ms / 1000 + ":" + (ms % 1000 < 100 ? "0" : "") + (ms % 1000 < 10 ? "0" : "") + ms % 1000;
                }
                out.add("§7Now: " + move + cd);
            } else if (i == index + 1) {
                out.add("§7Next: " + move);
            } else {
                out.add("§7Later: " + move);
            }
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String format(String line) {
        StringBuilder sb = new StringBuilder();
        for (String w : line.split(" ")) {
            if (w.isEmpty()) continue;
            String word = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            String col = switch (word) {
                case "Move", "Stand" -> "§e";
                case "Sneak" -> "§5";
                case "Jump" -> "§b";
                case "Punch" -> "§d";
                default -> "§f";
            };
            if (sb.length() > 0) sb.append(' ');
            sb.append(col).append(word);
        }
        return sb.toString();
    }

    /** From SoundPlayMixin, main thread. */
    public static void onSound(String id, float pitch, float volume) {
        boolean success = id.equals("minecraft:block.note_block.bass") && volume == 1f
                && (Math.abs(pitch - 0.6984127f) < 0.001f || Math.abs(pitch - 0.52380955f) < 0.001f);
        boolean failure = (id.equals("minecraft:entity.player.burp") && Math.abs(volume - 0.8f) < 0.001f)
                || (id.equals("minecraft:entity.player.levelup") && Math.abs(pitch - 1.8412699f) < 0.001f && volume == 1f);
        if (success) {
            index++;
            countdown = 20;
            build();
        } else if (failure && (index > 0 || countdown > 0)) {
            index = 0;
            countdown = 0;
            build();
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
