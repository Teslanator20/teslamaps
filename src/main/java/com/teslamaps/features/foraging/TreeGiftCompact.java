package com.teslamaps.features.foraging;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.TickScheduler;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Squashes the whole TREE GIFT block into one line that keeps the reward hover, plus one
 * line per bonus gift drop. Patterns follow SkyHanni's ForagingTrackerLegacy.
 */
public class TreeGiftCompact {

    // "§2§l▬{64}" — the bar Hypixel wraps the gift block in
    private static final Pattern SEPARATOR = Pattern.compile("▬{64}");
    private static final Pattern GIFT_HEADER = Pattern.compile(" *TREE GIFT");
    private static final Pattern BONUS_HEADER = Pattern.compile(" *BONUS GIFT");
    private static final Pattern CONTRIBUTION = Pattern.compile(
            " *You helped cut (?<percentage>[\\d.]+)% of the (?<type>.*) Tree\\.");
    private static final Pattern REWARDS = Pattern.compile(" *\\+(?<count>[\\d,]+) rewards gained!(?: \\(hover\\))?");
    private static final Pattern BONUS_REWARD = Pattern.compile(" *(?<item>.*) \\((?<percentage>[\\d.]+)%\\)");
    private static final Pattern PHANTOM = Pattern.compile(" *A (?<phantom>[\\S ]+) fell from the Tree!");

    private static boolean inBlock = false;
    private static boolean giftBlock = false;
    private static boolean inBonusGift = false;
    private static int lines = 0;
    private static String treeType = "";
    private static String percent = "";
    private static String rewardCount = "0";
    private static Style hover = null;
    private static final List<String> rareDrops = new ArrayList<>();

    // Ported from HypixelSimpleChat's TreeGiftTotals.kt
    private static final Pattern GAIN = Pattern.compile("(.+?) x([\\d,]+)(?:-([\\d,]+))?");
    private static final Pattern LEAD_CODES = Pattern.compile("^((?:§.)*)");

    private static final class Total {
        final String label;
        long min, max;
        Total(String label, long min, long max) { this.label = label; this.min = min; this.max = max; }
    }

    private static final class Tree {
        int gifts;
        final Map<String, Total> gains = new LinkedHashMap<>();
    }

    private static final Map<String, Tree> trees = new LinkedHashMap<>();

    /** @return true when the line belongs to a gift block and must not reach chat. */
    public static boolean handle(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.foragingCompactGifts) {
            inBlock = false;
            return false;
        }

        // Hypixel wraps a lot of reward blocks in the same ▬ bar (corpse loot, catches, ...),
        // so the bar alone must not arm the block outside the places gifts drop
        if (!inBlock && !Foraging.whereTreesGrow()) return false;

        String clean = Foraging.strip(message.getString()).stripTrailing();
        String trimmed = clean.trim();

        if (SEPARATOR.matcher(trimmed).matches()) {
            if (inBlock) {
                inBlock = false;
                send();
            } else {
                start();
            }
            return true;
        }
        if (!inBlock) {
            // the opening bar can go missing when other mods eat it, arm on the header instead
            if (GIFT_HEADER.matcher(clean).matches()) {
                start();
                giftBlock = true;
                return true;
            }
            return false;
        }
        // never swallow the rest of chat if the closing bar goes missing
        if (++lines > 60) {
            inBlock = false;
            return false;
        }

        // the gift header is the first line after the bar - anything else means this is
        // some other ▬ block and the rest of it has to stay visible
        if (!giftBlock) {
            if (GIFT_HEADER.matcher(clean).matches()) giftBlock = true;
            else if (!trimmed.isEmpty()) {
                inBlock = false;
                return false;
            }
        }

        if (BONUS_HEADER.matcher(clean).matches()) {
            inBonusGift = true;
            return true;
        }

        Matcher m = CONTRIBUTION.matcher(clean);
        if (m.matches()) {
            String value = m.group("percentage");
            percent = (value.equals("100") ? "§a" : "§c") + value + "%";
            treeType = m.group("type");
            return true;
        }

        m = REWARDS.matcher(clean);
        if (m.matches()) {
            rewardCount = m.group("count");
            hover = findHover(message);
            return true;
        }

        m = PHANTOM.matcher(clean);
        if (m.matches()) {
            boolean hide = GiftDropFilter.anyHidden() && GiftDropFilter.hidden(GiftDropFilter.Cat.MOBS)
                    && GiftDropFilter.isMob(m.group("phantom").trim());
            if (!hide) rareDrops.add("A wild §d" + m.group("phantom") + " §fappeared!");
            return true;
        }

        if (inBonusGift) {
            m = BONUS_REWARD.matcher(clean);
            if (m.matches() && !(GiftDropFilter.anyHidden() && GiftDropFilter.hidden(GiftDropFilter.of(m.group("item"))))) {
                rareDrops.add(m.group("item"));
            }
        }
        return true;
    }

    public static void reset() {
        inBlock = false;
        inBonusGift = false;
    }

    private static void start() {
        if (GiftDropFilter.anyHidden()) GiftDropFilter.fetch();
        inBlock = true;
        giftBlock = false;
        inBonusGift = false;
        lines = 0;
        treeType = "";
        percent = "";
        rewardCount = "0";
        hover = null;
        rareDrops.clear();
    }

    private static void send() {
        if (treeType.isEmpty()) return;
        String text = "§9" + treeType + " Tree Gift. §7You helped cut " + percent
                + " §7and gained §e" + rewardCount + " rewards§a!";
        MutableComponent line = Component.literal(text);
        if (hover != null) line.setStyle(line.getStyle().withHoverEvent(withTotals(hover.getHoverEvent())));
        List<String> drops = List.copyOf(rareDrops);

        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.sendSystemMessage(line);
            for (String drop : drops) mc.player.sendSystemMessage(Component.literal("§f - " + drop));
        });
    }

    private static HoverEvent withTotals(HoverEvent event) {
        if (!TeslaMapsConfig.get().foragingGiftTotals || !(event instanceof HoverEvent.ShowText show)) return event;
        String summary = record(treeType, show.value().getString());
        if (summary == null) return event;
        return new HoverEvent.ShowText(show.value().copy().append(Component.literal(summary)));
    }

    private static String record(String tree, String hoverText) {
        Map<String, Total> gains = new LinkedHashMap<>();
        for (String raw : hoverText.split("\n")) {
            Matcher g = GAIN.matcher(Foraging.strip(raw).trim());
            if (!g.matches()) continue;
            Long min = num(g.group(2));
            if (min == null) continue;
            Long max = g.group(3) == null ? min : num(g.group(3));
            if (max == null) max = min;
            Matcher lead = LEAD_CODES.matcher(raw.trim());
            String codes = lead.find() && !lead.group(1).isEmpty() ? lead.group(1) : "§7";
            String what = g.group(1).trim();
            gains.put(what, new Total(codes + what, min, max));
        }
        if (gains.isEmpty()) return null;
        Tree t = trees.computeIfAbsent(tree, k -> new Tree());
        t.gifts++;
        for (Map.Entry<String, Total> e : gains.entrySet()) {
            Total total = t.gains.get(e.getKey());
            if (total == null) {
                t.gains.put(e.getKey(), e.getValue());
                continue;
            }
            total.min += e.getValue().min;
            total.max += e.getValue().max;
        }
        StringBuilder sb = new StringBuilder("\n\n").append(header(tree, t));
        for (Total total : t.gains.values()) sb.append("\n").append(line(total));
        return sb.toString();
    }

    private static String header(String name, Tree t) {
        return "§2" + name + " §8· §7" + t.gifts + " gift" + (t.gifts == 1 ? "" : "s") + " this session";
    }

    private static String line(Total t) {
        String amount = t.min == t.max ? group(t.min) : group(t.min) + "-" + group(t.max);
        return t.label + " §7x" + amount;
    }

    private static String group(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static Long num(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("treegift")
                .executes(ctx -> {
                    if (trees.isEmpty()) {
                        ctx.getSource().sendFeedback(Component.literal("§7No Tree Gift counted this session."));
                        return 1;
                    }
                    for (Map.Entry<String, Tree> e : trees.entrySet()) {
                        ctx.getSource().sendFeedback(Component.literal(header(e.getKey(), e.getValue())));
                        for (Total t : e.getValue().gains.values())
                            ctx.getSource().sendFeedback(Component.literal(" §8· " + line(t)));
                    }
                    return 1;
                })
                .then(ClientCommands.literal("reset").executes(ctx -> {
                    trees.clear();
                    ctx.getSource().sendFeedback(Component.literal("§7Tree Gift totals reset"));
                    return 1;
                }));
    }

    private static Style findHover(Component component) {
        if (component.getStyle().getHoverEvent() != null) return component.getStyle();
        for (Component sibling : component.getSiblings()) {
            Style found = findHover(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
