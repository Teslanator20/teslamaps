package com.teslamaps.features;

// Ported from SkyHanni's ReminderManager.kt / ReminderHudDisplay.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.Reminder;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Reminders {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final String SAMPLE = "§e2h 5m §7Forge abholen";
    private static final int HUD_MAX = 5;

    private static HudEditElement hud;
    private static List<String> hudLines = List.of();
    private static int hudWidth;
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("reminderHud", "Reminders").section("Timers")
                .enabled(() -> cfg().reminders && cfg().reminderHud)
                .size(() -> Math.max(hudWidth, Minecraft.getInstance().font.width(SAMPLE)), () -> Math.max(1, hudLines.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, List.of(SAMPLE), mc.font.width(SAMPLE)));
        Feature.of("Reminders").section("Timers").when(() -> cfg().reminders)
                .tick(Reminders::tick)
                .hud("reminderhud", Reminders::render)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Reminders");
                    b.toggle("Reminders", () -> c.reminders, v -> c.reminders = v)
                            .desc("/tmap remind <time> <text>, e.g. /tmap remind 2h30m Forge. Also list, remove, edit, move.");
                    b.toggle("  Show Title", () -> c.remindersTitle, v -> c.remindersTitle = v);
                    b.toggle("  Delete After Firing", () -> c.remindersAutoDelete, v -> c.remindersAutoDelete = v)
                            .desc("Off: repeats until you click dismiss.");
                    b.slider("  Repeat Every (min)", 1f, 60f,
                            () -> (float) c.remindersInterval, v -> c.remindersInterval = Math.round(v), 0);
                    b.toggle("  Remaining Time HUD", () -> c.reminderHud, v -> c.reminderHud = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("remind")
                        .executes(c -> help(c.getSource()))
                        .then(ClientCommands.literal("help").executes(c -> help(c.getSource())))
                        .then(ClientCommands.literal("list").executes(c -> list(c.getSource())))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "id")))))
                        .then(ClientCommands.literal("edit")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                                .executes(c -> edit(c.getSource(), StringArgumentType.getString(c, "id"),
                                                        StringArgumentType.getString(c, "text"))))))
                        .then(ClientCommands.literal("move")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .then(ClientCommands.argument("time", StringArgumentType.word())
                                                .executes(c -> move(c.getSource(), StringArgumentType.getString(c, "id"),
                                                        StringArgumentType.getString(c, "time"))))))
                        .then(ClientCommands.argument("time", StringArgumentType.word())
                                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                        .executes(c -> create(c.getSource(), StringArgumentType.getString(c, "time"),
                                                StringArgumentType.getString(c, "text"))))))));
    }

    static long parseDuration(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        Matcher m = PART.matcher(t);
        long ms = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
            end = m.end();
        }
        if (end != t.length()) {
            try { return end == 0 ? Long.parseLong(t) * 60_000L : -1; } catch (NumberFormatException e) { return -1; }
        }
        return ms > 1000 ? ms : -1;
    }

    static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static Reminder find(String id) {
        for (Reminder r : cfg().reminderList) if (r.id.equalsIgnoreCase(id)) return r;
        return null;
    }

    private static String newId() {
        String chars = "abcdefghjkmnpqrstuvwxyz23456789";
        java.util.Random rnd = new java.util.Random();
        String id;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
            id = sb.toString();
        } while (find(id) != null);
        return id;
    }

    private static void msg(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal("§e[Reminder] " + s));
    }

    private static int create(FabricClientCommandSource src, String time, String text) {
        long ms = parseDuration(time);
        if (ms < 0) { msg(src, "§cInvalid time, use e.g. 90s, 15m, 2h30m, 1d"); return 0; }
        cfg().reminderList.add(new Reminder(newId(), text, System.currentTimeMillis() + ms));
        TeslaMapsConfig.save();
        msg(src, "§6Reminder set for " + format(ms) + (cfg().reminders ? "" : " §c(Reminders are off in the settings)"));
        return 1;
    }

    private static int remove(FabricClientCommandSource src, String id) {
        Reminder r = find(id);
        if (r == null) { msg(src, "§cReminder not found!"); return 0; }
        cfg().reminderList.remove(r);
        TeslaMapsConfig.save();
        msg(src, "§cReminder deleted.");
        return 1;
    }

    private static int edit(FabricClientCommandSource src, String id, String text) {
        Reminder r = find(id);
        if (r == null) { msg(src, "§cReminder not found!"); return 0; }
        r.text = text;
        TeslaMapsConfig.save();
        msg(src, "§6Reminder edited.");
        return 1;
    }

    private static int move(FabricClientCommandSource src, String id, String time) {
        Reminder r = find(id);
        if (r == null) { msg(src, "§cReminder not found!"); return 0; }
        long ms = parseDuration(time);
        if (ms < 0) { msg(src, "§cInvalid time format!"); return 0; }
        r.at = System.currentTimeMillis() + ms;
        r.last = 0;
        TeslaMapsConfig.save();
        msg(src, "§6Reminder moved to " + format(ms));
        return 1;
    }

    private static int list(FabricClientCommandSource src) {
        List<Reminder> l = new ArrayList<>(cfg().reminderList);
        if (l.isEmpty()) { msg(src, "§7No reminders."); return 1; }
        l.sort(Comparator.comparingLong(r -> r.at));
        long now = System.currentTimeMillis();
        src.sendFeedback(Component.literal("§e§lReminders"));
        for (Reminder r : l) {
            String when = r.at <= now ? "§cdue" : "§6" + format(r.at - now);
            MutableComponent line = Component.empty()
                    .append(button("§c✕", "§7Click to remove", new ClickEvent.RunCommand("/tmap remind remove " + r.id)))
                    .append(" ")
                    .append(button("§e✎", "§7Click to edit", new ClickEvent.SuggestCommand("/tmap remind edit " + r.id + " " + r.text)))
                    .append(" ")
                    .append(button("§e⟳", "§7Click to move", new ClickEvent.SuggestCommand("/tmap remind move " + r.id + " 10m")))
                    .append(Component.literal(" §8[" + when + "§8] §7" + r.text));
            src.sendFeedback(line);
        }
        return 1;
    }

    private static int help(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("§6Reminder commands:\n"
                + "§e/tmap remind <time> <text> §7- new reminder (90s, 15m, 2h30m, 1d)\n"
                + "§e/tmap remind list §7- all reminders\n"
                + "§e/tmap remind remove <id>\n"
                + "§e/tmap remind edit <id> <text>\n"
                + "§e/tmap remind move <id> <time>"));
        return 1;
    }

    private static MutableComponent button(String label, String hover, ClickEvent click) {
        return Component.literal("§8[" + label + "§8]").withStyle(s -> s
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        long interval = Math.max(1, c.remindersInterval) * 60_000L;
        List<Reminder> fired = new ArrayList<>();
        for (Reminder r : c.reminderList) {
            if (r.at > now || now - r.last < interval) continue;
            r.last = now;
            fired.add(r);
        }
        if (!fired.isEmpty()) fire(c, fired);
        if (c.reminderHud) buildHud(c, now);
    }

    private static void fire(TeslaMapsConfig c, List<Reminder> fired) {
        Minecraft mc = Minecraft.getInstance();
        for (Reminder r : fired) {
            ExecutionLog.log("Reminder: " + r.text);
            MutableComponent line = Component.literal("§e[Reminder] ");
            if (!c.remindersAutoDelete) {
                line.append(button("§a✔", "§7Click to dismiss", new ClickEvent.RunCommand("/tmap remind remove " + r.id)))
                        .append(" ")
                        .append(button("§e§l⟳", "§7Click to move", new ClickEvent.SuggestCommand("/tmap remind move " + r.id + " 10m")))
                        .append(" ");
            }
            line.append(Component.literal("§6" + r.text));
            if (mc.player != null) mc.player.sendSystemMessage(line);
        }
        if (c.remindersAutoDelete) c.reminderList.removeAll(fired);
        TeslaMapsConfig.save();
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        com.teslamaps.utils.TickScheduler.schedule(3, () -> LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f));
        com.teslamaps.utils.TickScheduler.schedule(6, () -> LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f));
        if (c.remindersTitle) {
            mc.gui.setTimes(5, 60, 10);
            mc.gui.setTitle(Component.literal(fired.size() == 1 ? "§cReminder!" : "§c" + fired.size() + " Reminders!"));
            mc.gui.setSubtitle(Component.literal(fired.size() == 1 ? "§e" + fired.get(0).text : ""));
        }
    }

    private static void buildHud(TeslaMapsConfig c, long now) {
        List<Reminder> l = new ArrayList<>(c.reminderList);
        l.sort(Comparator.comparingLong(r -> r.at));
        List<String> out = new ArrayList<>();
        int w = 0;
        Minecraft mc = Minecraft.getInstance();
        for (Reminder r : l) {
            if (out.size() >= HUD_MAX) break;
            String s = (r.at <= now ? "§cdue" : "§e" + format(r.at - now)) + " §7" + r.text;
            out.add(s);
            w = Math.max(w, mc.font.width(s));
        }
        hudLines = out;
        hudWidth = w;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> lines = hudLines;
        if (!cfg().reminderHud || lines.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, hudWidth);
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> lines, int w) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        com.teslamaps.render.gui.HudStyle.panel(g, -2, -2, w + 4, lines.size() * 10 + 2);
        for (int i = 0; i < lines.size(); i++) g.text(mc.font, lines.get(i), 0, i * 10, 0xFFFFFFFF, true);
        pose.popMatrix();
    }
}
