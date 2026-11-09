package com.teslamaps.features;

// Ported from SkyHanni's PestSpawn.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Title on every pest spawn (offline ones too) and a compact clickable chat line instead of Hypixel's. */
public final class PestSpawnAlerts {

    public record Spawn(int amount, List<String> plots) {}

    private static final Pattern ONE = Pattern.compile("^\\w+! A .{0,4}Pest has appeared in (?:Plot - (?<plot>.+)|(?<barn>The Barn))!");
    private static final Pattern MULTI = Pattern.compile("^\\w+! (?<amount>\\d+) .{0,4}Pests? have spawned in (?:Plot - (?<plot>.+)|(?<barn>The Barn))!");
    private static final Pattern OFFLINE = Pattern.compile("^\\w+! While you were offline, .{0,4}Pests? spawned in Plots (?<plots>.+)!");
    private static final Pattern CLICK = Pattern.compile("^\\s*CLICK HERE to teleport to the plot!\\s*$");

    private static long lastSpawn;

    private PestSpawnAlerts() {}

    /** Spawn line, colour codes already stripped; amount -1 = offline spawn. */
    public static Spawn parse(String text) {
        if (text.indexOf("Pest") < 0) return null;
        Matcher m = ONE.matcher(text);
        if (m.find()) return new Spawn(1, List.of(plot(m)));
        m = MULTI.matcher(text);
        if (m.find()) return new Spawn(Integer.parseInt(m.group("amount")), List.of(plot(m)));
        m = OFFLINE.matcher(text);
        if (m.find()) {
            List<String> plots = new java.util.ArrayList<>();
            for (String p : m.group("plots").split(", | and ")) if (!p.isBlank()) plots.add(p.trim());
            return new Spawn(-1, plots);
        }
        return null;
    }

    private static String plot(Matcher m) {
        return m.group("barn") != null ? "The Barn" : m.group("plot").trim();
    }

    /** From ChatMixin for server lines; true = drop Hypixel's line. */
    public static boolean onChat(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pestSpawnTitle && !c.pestSpawnCompact) return false;
        if (!SkyblockUtils.isInGarden()) return false;
        String text = StringUtil.strip(raw);
        if (c.pestSpawnCompact && CLICK.matcher(text).matches()) {
            return System.currentTimeMillis() - lastSpawn < 1500;
        }
        Spawn spawn = parse(text);
        if (spawn == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (spawn.amount() < 0) {
            if (c.pestSpawnTitle && mc.gui != null) {
                mc.gui.hud.setTimes(0, 100, 10);
                mc.gui.hud.setTitle(Component.literal("§ePests spawned offline"));
                mc.gui.hud.setSubtitle(Component.literal("§7in §bPlots " + String.join(", ", spawn.plots())));
            }
            return false;
        }
        lastSpawn = System.currentTimeMillis();
        String plot = spawn.plots().get(0);
        String msg = "§e" + spawn.amount() + " §a" + (spawn.amount() == 1 ? "Pest" : "Pests") + " Spawned in §b"
                + (plot.equals("The Barn") ? plot : "Plot " + plot) + "§a!";
        if (c.pestSpawnTitle && mc.gui != null) {
            mc.gui.hud.setTimes(0, 120, 10);
            mc.gui.hud.setTitle(Component.literal(msg));
            mc.gui.hud.setSubtitle(Component.empty());
        }
        if (!c.pestSpawnCompact || mc.player == null) return false;
        String tp = plot.equals("The Barn") ? "barn" : plot;
        mc.player.sendSystemMessage(Component.literal(msg).withStyle(st -> st
                .withClickEvent(new ClickEvent.RunCommand("/plottp " + tp))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /plottp " + tp)))));
        return true;
    }
}
