package com.teslamaps.features;

// Buttons: Zen's PartyFinderMessage. Chat names -> /pv: NEU's replaceSocialOptions1.
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyFinderLinks {

    private static final Pattern JOIN = Pattern.compile("^Party Finder > (\\w{1,16}) joined the (?:dungeon )?group!");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PartyFinderLinks() {}

    /** ChatMixin, right before the line is stored. */
    public static Component rewrite(Component message) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pbOnJoin || !(c.pbOnJoinButtons || c.chatNamesOpenPv) || !c.section("Party")) return message;
        try {
            Component out = message;
            if (c.chatNamesOpenPv && hasTarget(out)) out = retarget(out);
            if (c.pbOnJoinButtons) {
                String text = StringUtil.stripTrim(out.getString());
                if (text.startsWith("Party Finder > ")) {
                    Matcher m = JOIN.matcher(text);
                    if (m.find() && !isSelf(m.group(1))) out = buttons(out, m.group(1));
                }
            }
            return out;
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static boolean isSelf(String name) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getGameProfile().name().equalsIgnoreCase(name);
    }

    private static Component buttons(Component line, String name) {
        MutableComponent out = line.copy();
        out.append(Component.literal(" §c[✖]").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/party kick " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§c/party kick " + name)))));
        out.append(Component.literal(" §b[PV]").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/pv " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§b/pv " + name)))));
        return out;
    }

    private static String pvTarget(Style style) {
        if (!(style.getClickEvent() instanceof ClickEvent.RunCommand run)) return null;
        String cmd = run.command();
        if (cmd == null) return null;
        String low = cmd.toLowerCase(Locale.ROOT);
        String arg;
        if (low.startsWith("/socialoptions ")) arg = cmd.substring(15).trim();
        else if (low.startsWith("/viewprofile ")) arg = cmd.substring(13).trim();
        else return null;
        return NAME.matcher(arg).matches() ? arg : null;
    }

    private static boolean hasTarget(Component c) {
        if (pvTarget(c.getStyle()) != null) return true;
        for (Component s : c.getSiblings()) if (hasTarget(s)) return true;
        return false;
    }

    private static Component retarget(Component c) {
        Style st = c.getStyle();
        String name = pvTarget(st);
        if (name != null) {
            st = st.withClickEvent(new ClickEvent.RunCommand("/pv " + name))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClick to open /pv " + name)));
        }
        MutableComponent out = c.plainCopy().setStyle(st);
        for (Component s : c.getSiblings()) out.append(retarget(s));
        return out;
    }
}
