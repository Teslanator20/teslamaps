package com.teslamaps.adm.misc.pf;

import com.teslamaps.adm.misc.AdmCore;
import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's pf/PartyFinderStats.kt: cata / secrets / S+ PB per member row and the missing classes
public final class PartyFinderStats {
    private static final int PARTY_SIZE = 5;
    private static final String[] CLASSES = {"Archer", "Berserk", "Healer", "Mage", "Tank"};
    private static final char[] CLASS_COLORS = {'6', '4', 'd', 'b', '2'};

    private static final Pattern MEMBER = Pattern.compile("^(\\w{1,16}): (\\w+) \\((\\d+)\\)$");
    private static final Pattern FLOOR = Pattern.compile("^Floor: (.+)$");
    private static final Pattern DUNGEON = Pattern.compile("^Dungeon: (.+)$");
    private static final Pattern FORMATTING = Pattern.compile("§.");
    private static final Map<String, String> ROMAN = Map.of(
            "Entrance", "0", "Floor I", "1", "Floor II", "2", "Floor III", "3",
            "Floor IV", "4", "Floor V", "5", "Floor VI", "6", "Floor VII", "7");

    private PartyFinderStats() {}

    /** Returns {@code lines} untouched (same instance) when this is not a Party Finder party item. */
    public static List<Component> decorate(AbstractContainerScreen<?> screen, ItemStack stack, List<Component> lines) {
        if (!RandomStuff.showsPartyFinderStats()) return lines;
        if (!screen.getTitle().getString().startsWith("Party Finder")) return lines;
        if (!clean(stack.getHoverName().getString()).endsWith("'s Party")) return lines;

        String floor = null;
        boolean master = false;
        boolean inMembers = false;
        String missing = missingClasses(lines);
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            String raw = clean(line.getString());
            String text = raw.trim();
            Matcher fm = FLOOR.matcher(text);
            if (fm.find()) floor = ROMAN.get(fm.group(1).trim());
            Matcher dm = DUNGEON.matcher(text);
            if (dm.find()) master = dm.group(1).toLowerCase().contains("master");
            if (text.equals("Members:")) {
                inMembers = true;
                String pad = raw.endsWith(" ") ? "" : " ";
                out.add(missing.isEmpty() ? line : line.copy().append(Component.literal(pad + missing)));
                continue;
            }
            Matcher mm = inMembers ? MEMBER.matcher(text) : null;
            if (mm != null && mm.find()) out.add(line.copy().append(Component.literal(statsFor(mm.group(1), floor, master))));
            else out.add(line);
        }
        return out;
    }

    private static String missingClasses(List<Component> lines) {
        boolean inMembers = false;
        int members = 0;
        Set<Integer> taken = new HashSet<>();
        for (Component line : lines) {
            String text = clean(line.getString()).trim();
            if (text.equals("Members:")) { inMembers = true; continue; }
            if (!inMembers) continue;
            Matcher m = MEMBER.matcher(text);
            if (!m.find()) continue;
            members++;
            int c = classNamed(m.group(2));
            if (c >= 0) taken.add(c);
        }
        if (members == 0) return "";
        if (members >= PARTY_SIZE) return "§8· §7full";
        String mine = AdmCore.myClass();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CLASSES.length; i++) {
            if (taken.contains(i)) continue;
            if (!sb.isEmpty()) sb.append("§8, ");
            if (CLASSES[i].equalsIgnoreCase(mine)) sb.append("§l§").append(CLASS_COLORS[i]).append(CLASSES[i]).append("§r");
            else sb.append('§').append(CLASS_COLORS[i]).append(CLASSES[i]);
        }
        if (sb.isEmpty()) return "";
        return "§8· §cmissing: " + sb;
    }

    private static int classNamed(String text) {
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i].equalsIgnoreCase(text)) return i;
        return "Berserker".equalsIgnoreCase(text) ? 1 : -1;
    }

    private static String statsFor(String name, String floor, boolean master) {
        PlayerStats.Lookup entry = PlayerStats.lookup(name);
        if (entry instanceof PlayerStats.Ready r) {
            return " §8| §e" + PlayerStats.cataStr(r.stats()) + " §8| §b" + PlayerStats.secretsStr(r.stats().secrets)
                    + " §8| §d" + PlayerStats.pbStr(r.stats(), master ? "m" : "f", floor);
        }
        if (entry instanceof PlayerStats.Failed) return " §8· §c?";
        return " §8· §7…";
    }

    private static String clean(String s) {
        return FORMATTING.matcher(s).replaceAll("");
    }
}
