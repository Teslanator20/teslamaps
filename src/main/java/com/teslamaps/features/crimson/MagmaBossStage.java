package com.teslamaps.features.crimson;

// Ported from GanKura's EntityHealthScanner.scanMagmaBossScoreboard
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MagmaBossStage {

    private static final Pattern PERCENT = Pattern.compile("Boss: (\\d{1,3})%", Pattern.CASE_INSENSITIVE);
    private static final List<String> SAMPLE = List.of("§6Magma Boss", "§c45% §7Damage Soaked", "§a▎▎▎▎▎§7▎▎▎▎▎");

    private static HudEditElement hud;
    private static volatile String health;
    private static List<String> lines = List.of();
    private static int width;
    private static String lastStage;
    private static int ticks;

    private MagmaBossStage() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("magmaBossHud", "Magma Boss").section("Crimson Isle")
                .enabled(() -> cfg().magmaBossStage && cfg().magmaBossHud)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("MagmaBossStage").section("Crimson Isle").when(() -> cfg().magmaBossStage)
                .tick(MagmaBossStage::tick)
                .hudHiddenInInventory("magmabosshud", MagmaBossStage::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Magma Boss Stages", () -> c.magmaBossStage, v -> c.magmaBossStage = v)
                            .desc("Reads the Magma Chamber sidebar (Boss %, Kill the Magmas, Boss Health). Its HP goes onto the Mob Visuals nameplate of the Magma Boss")
                            .tags("magma boss", "magma chamber", "sidebar", "hp");
                    b.toggle("  Title On Stage Change", () -> c.magmaBossTitle, v -> c.magmaBossTitle = v);
                    b.toggle("  Stage HUD", () -> c.magmaBossHud, v -> c.magmaBossHud = v)
                            .desc("Stage and progress bar as a HUD (/tmap gui)");
                });
    }

    /** Sidebar HP line for the Mob Visuals nameplate, null when unknown. */
    public static String health() {
        TeslaMapsConfig c = cfg();
        return c.magmaBossStage && c.section("Crimson Isle") ? health : null;
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInArea("Crimson Isle")) {
            clear();
            return;
        }
        List<String> sb = sidebar(mc);
        String percent = null, soaked = null, kill = null, bossHp = null;
        boolean reforming = false;
        for (int i = 0; i < sb.size(); i++) {
            String plain = StringUtil.stripTrim(sb.get(i));
            String next = i + 1 < sb.size() ? sb.get(i + 1).trim() : null;
            if (next != null && next.isEmpty()) next = null;
            Matcher m = PERCENT.matcher(plain);
            if (m.find()) percent = m.group(1) + "%";
            if (plain.contains("Damage Soaked:")) soaked = next;
            else if (plain.contains("Kill the Magmas:")) kill = next;
            else if (plain.contains("Boss Health:")) bossHp = next;
            else if (plain.contains("The boss is reforming!")) reforming = true;
        }

        String stage, hp;
        if (reforming) { stage = "Reforming..."; hp = null; }
        else if (bossHp != null) { stage = "Final Stage"; hp = bossHp; }
        else if (kill != null) { stage = "Kill the Magmas"; hp = kill; }
        else { stage = percent; hp = soaked; }

        if (stage != null && !"Final Stage".equals(stage) && !stage.equals(lastStage) && cfg().magmaBossTitle) {
            mc.gui.setTitle(Component.literal("§c§l" + stage));
        }
        lastStage = stage;
        health = hp;

        if (stage == null) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>(3);
        out.add("§6Magma Boss");
        out.add("§c" + stage);
        if (hp != null) out.add(hp);
        lines = out;
        width = TextHud.width(out);
    }

    private static void clear() {
        health = null;
        lastStage = null;
        lines = List.of();
    }

    private static List<String> sidebar(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.level == null) return out;
        Scoreboard board = mc.level.getScoreboard();
        Objective obj = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) return out;
        List<PlayerScoreEntry> entries = new ArrayList<>(board.listPlayerScores(obj));
        entries.sort((a, b) -> Integer.compare(b.value(), a.value()));
        for (PlayerScoreEntry e : entries) {
            PlayerTeam team = board.getPlayersTeam(e.owner());
            out.add(team == null ? e.owner() : team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
        }
        return out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !cfg().magmaBossHud || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
