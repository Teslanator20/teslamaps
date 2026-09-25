package com.teslamaps.features;

// Ported from SkyHanni's features/event/hoppity/HoppityEggsCompactChat.kt and HypixelSimpleChat's HoppityCompact.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Egg + rabbit + NEW/DUPLICATE lines of a Hoppity find as one line. */
public final class HoppityCompactChat {

    private static final Pattern EGG = Pattern.compile("^HOPPITY'S HUNT You found an? (?:Chocolate )?(.+?) Egg\\b.*!$");
    private static final Pattern RABBIT = Pattern.compile("^HOPPITY'S HUNT You found (.+) \\((COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE)\\)!$");
    private static final Pattern NEW = Pattern.compile("^NEW RABBIT! (?:(?:\\+(.+?) Chocolate and )?\\+(.+?)x Chocolate per second!|(.*))$");
    private static final Pattern DUPE = Pattern.compile("^DUPLICATE RABBIT! \\+([\\d,]+) Chocolate");
    private static final Pattern TOWER = Pattern.compile("^TIME TOWER! .*for (\\d+)h");
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE"};
    private static final String[] RARITY_COLORS = {"§f", "§a", "§9", "§5", "§6", "§d", "§b"};

    private static final List<Component> lines = new ArrayList<>();
    private static String meal, rabbit, rarity;
    private static long startedAt;
    private static long towerUntil;
    private static boolean emitting;

    private record Find(String compact, boolean dupe, String rarity, long chocolate, List<Component> lines) {}

    private static final List<Find> hitman = new ArrayList<>();
    private static long hitmanAt;

    private HoppityCompactChat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HoppityCompactChat").section("Chat").when(() -> cfg().hoppityCompact)
                .tick(HoppityCompactChat::tick);
    }

    /** From the chat filter; true = drop the line. May queue a rewrite. */
    static boolean handle(Component message, String text, String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.chatFilterEnabled || !c.hoppityCompact || emitting) return false;
        if (com.teslamaps.utils.PlayerChat.current() != null) return false;
        String t = stripped.trim();

        Matcher m = TOWER.matcher(t);
        if (m.find()) {
            towerUntil = System.currentTimeMillis() + Long.parseLong(m.group(1)) * 3_600_000L;
            return false;
        }
        m = RABBIT.matcher(t);
        if (m.matches()) {
            if (rabbit != null) flushPending();
            rabbit = m.group(1);
            rarity = m.group(2);
            lines.add(message);
            if (startedAt == 0) startedAt = System.currentTimeMillis();
            return true;
        }
        m = EGG.matcher(t);
        if (m.matches()) {
            flushPending();
            meal = m.group(1);
            lines.add(message);
            startedAt = System.currentTimeMillis();
            return true;
        }
        Matcher n = NEW.matcher(t);
        Matcher d = DUPE.matcher(t);
        boolean isNew = n.matches();
        boolean isDupe = !isNew && d.find();
        if (!isNew && !isDupe || rabbit == null) return false;

        lines.add(message);
        long choc = 0;
        String out;
        if (isDupe) {
            choc = parse(d.group(1));
            String num = "";
            if (c.hoppityCompactDupeNumber) {
                Integer count = c.hoppityRabbits == null ? null : c.hoppityRabbits.get(rabbit);
                if (count != null && count > 0) num = " §7(§b#" + (count + 1) + "§7)";
            }
            String chocColor = c.hoppityCompactTimeTower && System.currentTimeMillis() < towerUntil ? "§d" : "§6";
            out = mealPart() + "§7Duplicate " + rarityPart() + "§f" + rabbit + num
                    + " §7(§6+" + chocColor + ChatCompactRules.shortNum(d.group(1)) + " §6Chocolate§7)";
        } else {
            String gain = n.group(2) != null ? "§6+" + n.group(2) + "x Chocolate/s" : "§f" + n.group(3);
            out = mealPart() + "§d§lNEW " + rarityPart() + "§f" + rabbit + " §7(" + gain + "§7)";
        }
        Component compact = withHover(out, lines);
        boolean isHitman = "Hitman".equalsIgnoreCase(meal);
        if (isHitman && c.hoppityCompactHitman) {
            hitman.add(new Find(out.substring(mealPart().length()), isDupe, rarity, choc, List.copyOf(lines)));
            hitmanAt = System.currentTimeMillis();
            reset();
            return true;
        }
        reset();
        ChatFilter.queueRewrite(text, compact);
        return false;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (startedAt != 0 && now - startedAt > 3000) flushPending();
        if (!hitman.isEmpty() && now - hitmanAt > 2000) flushHitman();
    }

    private static String mealPart() {
        if (meal == null) return "";
        String color = switch (meal) {
            case "Breakfast", "Brunch" -> "§6";
            case "Lunch", "Déjeuner" -> "§9";
            case "Dinner", "Supper" -> "§a";
            case "Hitman" -> "§c";
            default -> "§d";
        };
        return color + meal + " Egg! ";
    }

    private static String rarityPart() {
        if (!cfg().hoppityCompactRarity || rarity == null) return "";
        return rarityColor(rarity) + "§l" + rarity + " ";
    }

    private static String rarityColor(String r) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(r)) return RARITY_COLORS[i];
        return "§f";
    }

    private static Component withHover(String legacy, List<Component> original) {
        MutableComponent hover = Component.empty();
        for (int i = 0; i < original.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(original.get(i));
        }
        return Component.literal(legacy).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static void reset() {
        lines.clear();
        meal = rabbit = rarity = null;
        startedAt = 0;
    }

    /** The third line never came: give the held lines back. */
    private static void flushPending() {
        List<Component> held = new ArrayList<>(lines);
        reset();
        for (Component l : held) add(l);
    }

    private static void flushHitman() {
        List<Find> finds = new ArrayList<>(hitman);
        hitman.clear();
        if (finds.size() <= cfg().hoppityCompactHitmanThreshold) {
            for (Find f : finds) add(withHover("§cHitman Egg! " + f.compact, f.lines));
            return;
        }
        int dupes = 0;
        long choc = 0;
        int[] perRarity = new int[RARITIES.length];
        MutableComponent hover = Component.empty();
        for (int i = 0; i < finds.size(); i++) {
            Find f = finds.get(i);
            if (f.dupe) { dupes++; choc += f.chocolate; }
            for (int r = 0; r < RARITIES.length; r++) if (RARITIES[r].equals(f.rarity)) perRarity[r]++;
            if (i > 0) hover.append("\n");
            hover.append(Component.literal(f.compact));
        }
        StringBuilder sb = new StringBuilder("§c§lHitman Summary §7· §f").append(finds.size()).append(" rabbits");
        int fresh = finds.size() - dupes;
        if (fresh > 0) sb.append(" §8· §d").append(fresh).append(" new");
        if (dupes > 0) sb.append(" §8· §7").append(dupes).append(" dupes §6+")
                .append(ChatCompactRules.shortNum(Long.toString(choc))).append(" Chocolate");
        StringBuilder rar = new StringBuilder();
        for (int r = 0; r < RARITIES.length; r++) {
            if (perRarity[r] == 0) continue;
            rar.append(rar.isEmpty() ? " §8(" : " ").append(RARITY_COLORS[r]).append(perRarity[r])
                    .append(RARITIES[r].charAt(0));
        }
        if (!rar.isEmpty()) sb.append(rar).append("§8)");
        add(Component.literal(sb.toString()).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
    }

    private static void add(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        emitting = true;
        try {
            mc.player.sendSystemMessage(c);
        } finally {
            emitting = false;
        }
    }

    private static long parse(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; }
    }
}
