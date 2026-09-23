package com.teslamaps.features;

// Ported from SkyHanni's StashCompact.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The three-line stash warning as one clickable line. */
public final class StashCompact {

    private static final Pattern COUNT = Pattern.compile("^\\s*You have (?<count>[\\d,]+) (?<type>item|material)s? stashed away!.*");
    private static final Pattern TYPES = Pattern.compile("^\\s*\\(This totals (?<count>[\\d,]+) types? of (?<type>item|material)s? stashed!\\).*");
    private static final Pattern PICKUP = Pattern.compile("^\\s*>>> CLICK HERE to pick (?:them|it) up! <<<.*");
    private static final Pattern ADDED = Pattern.compile(
            "^One or more (?:item|material)s? didn't fit in your inventory and were added to your (?:item|material) stash! Click here to pick them up!.*");

    private static final class Warning {
        final int count;
        final String type;
        int types = -1;

        Warning(int count, String type) {
            this.count = count;
            this.type = type;
        }

        boolean same(Warning o) {
            return o != null && o.count == count && o.type.equals(type);
        }
    }

    private static String currentType;
    private static Warning currentItem, currentMaterial, lastItem, lastMaterial;
    private static boolean hideNextBlank, emitting;
    private static Object lastLevel;

    private StashCompact() {}

    /** From the chat filter; true = drop the line. */
    public static boolean handle(String plain) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.stashCompact || emitting) return false;

        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            if (c.stashDuplicatesPerWorld) lastItem = lastMaterial = null;
        }

        if (hideNextBlank) {
            hideNextBlank = false;
            if (plain.isBlank()) return true;
        }

        Matcher m = COUNT.matcher(plain);
        if (m.matches()) {
            currentType = m.group("type");
            set(new Warning(parseInt(m.group("count")), currentType));
            dropRecentBlanks();
            return true;
        }
        m = TYPES.matcher(plain);
        if (m.matches()) {
            currentType = m.group("type");
            Warning w = current();
            if (w != null) w.types = parseInt(m.group("count"));
            return true;
        }
        if (PICKUP.matcher(plain).matches()) {
            hideNextBlank = true;
            Warning w = current();
            if (w == null) return true;
            set(null);
            if (w.count <= c.stashMinCount) return true;
            Warning last = w.type.equals("item") ? lastItem : lastMaterial;
            if (c.stashHideDuplicates && w.same(last)) return true;
            if (w.type.equals("item")) lastItem = w; else lastMaterial = w;
            emit(w, c.stashUseViewStash);
            return true;
        }
        if (c.stashHideAdded && ADDED.matcher(plain).matches()) {
            hideNextBlank = true;
            return true;
        }
        return false;
    }

    private static Warning current() {
        return "item".equals(currentType) ? currentItem : currentMaterial;
    }

    private static void set(Warning w) {
        if ("item".equals(currentType)) currentItem = w; else currentMaterial = w;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Hypixel wraps the block in empty lines; they arrive right before the count line
    private static void dropRecentBlanks() {
        Minecraft mc = Minecraft.getInstance();
        ChatComponentAccessor acc = (ChatComponentAccessor) mc.gui.getChat();
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        int now = mc.gui.getGuiTicks();
        boolean removed = false;
        for (int i = 0; i < 2 && !all.isEmpty(); i++) {
            GuiMessage first = all.get(0);
            if (now - first.addedTime() > 10 || !StringUtil.strip(first.content().getString()).isBlank()) break;
            all.remove(0);
            removed = true;
        }
        if (removed) acc.teslamaps$refreshTrimmedMessages();
    }

    private static void emit(Warning w, boolean view) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean item = w.type.equals("item");
        String main = item ? "§e" : "§b", accent = item ? "§6" : "§3";
        String noun = w.type + (w.count == 1 ? "" : "s");
        String extra = w.types > 0 ? ", " + main + "totalling " + accent + w.types + " type" + (w.types == 1 ? "" : "s") + main : "";
        String action = view ? "view" : "pickup";
        String command = view ? "/viewstash " + w.type : "/pickupstash";
        Component line = Component.literal(main + "You have " + accent + String.format("%,d", w.count) + " " + main + noun
                        + " in stash" + extra + ". " + main + "Click to " + accent + action + " " + main + "your stash!")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to " + action + " your " + w.type + " stash!"))));
        emitting = true;
        try {
            player.sendSystemMessage(line);
        } finally {
            emitting = false;
        }
    }
}
